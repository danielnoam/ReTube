package com.newtube.mobile.ui.settings;

import android.content.Context;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.stats.LocalStats;
import com.newtube.mobile.stats.StatsCalendar;
import com.newtube.mobile.stats.StatsConsent;
import com.newtube.mobile.stats.StatsData;
import com.newtube.mobile.ui.common.MobileSnackbar;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Settings > Stats: watch time by day, week, month, year and all time, any earlier period by the
 * arrows or a tap on its bar, downloads against streaming, counts and the most watched channels,
 * all from {@link LocalStats}. Until stats have a yes ({@link StatsConsent}) nothing is recorded.
 */
public final class StatsFragment extends Fragment implements SettingsAdapter.Listener {
    private static final int MODE_DAY = 0;
    private static final int MODE_WEEK = 1;
    private static final int MODE_MONTH = 2;
    private static final int MODE_YEAR = 3;
    private static final int MODE_ALL = 4;
    /** Bars per tab: 7 days, 8 weeks, 6 months, 12 months (Year: its months), 5 years. */
    private static final int[] BARS = {7, 8, 6, 12, 5};
    private static final int TOP_CHANNELS = 5;
    private static final String STATE_MODE = "mode";
    private static final String STATE_OFFSET = "offset";

    private int mMode = MODE_WEEK;
    /** Which period of the tab: 0 the current one, 1 the one before, ... */
    private int mOffset;
    private View mOff;
    private View mContent;
    private TextView[] mTabs;
    private View mPrev;
    private View mNext;
    private SettingsAdapter mControls;
    @Nullable private SettingsRow mClearRow;
    @Nullable private AlertDialog mDialog;
    /** What a tap on each bar shows: its tab and period. */
    private int[] mBarModes = new int[0];
    private int[] mBarOffsets = new int[0];

    /** One period: its first and last day. */
    private static final class Range {
        final long from;
        final long to;

        Range(long from, long to) {
            this.from = from;
            this.to = to;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mMode = savedInstanceState.getInt(STATE_MODE, MODE_WEEK);
            mOffset = savedInstanceState.getInt(STATE_OFFSET, 0);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_MODE, mMode);
        outState.putInt(STATE_OFFSET, mOffset);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mobile_stats, container, false);
        view.findViewById(R.id.stats_back).setOnClickListener(
                v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        mOff = view.findViewById(R.id.stats_off);
        mContent = view.findViewById(R.id.stats_content);
        view.findViewById(R.id.stats_turn_on).setOnClickListener(v -> setRecording(true));

        mTabs = new TextView[] {view.findViewById(R.id.stats_tab_day), view.findViewById(R.id.stats_tab_week),
                view.findViewById(R.id.stats_tab_month), view.findViewById(R.id.stats_tab_year),
                view.findViewById(R.id.stats_tab_all)};
        for (int i = 0; i < mTabs.length; i++) {
            int mode = i;
            mTabs[i].setOnClickListener(v -> {
                if (mMode != mode) {
                    mMode = mode;
                    mOffset = 0;
                    render();
                }
            });
        }
        mPrev = view.findViewById(R.id.stats_prev);
        mNext = view.findViewById(R.id.stats_next);
        mPrev.setOnClickListener(v -> {
            mOffset++;
            render();
        });
        mNext.setOnClickListener(v -> {
            mOffset = Math.max(0, mOffset - 1);
            render();
        });
        StatsBarChart chart = view.findViewById(R.id.stats_chart);
        chart.setOnBarClickListener(index -> {
            if (index < mBarModes.length && mBarOffsets[index] >= 0) {
                mMode = mBarModes[index];
                mOffset = mBarOffsets[index];
                render();
            }
        });

        RecyclerView controls = view.findViewById(R.id.stats_controls);
        controls.setLayoutManager(new LinearLayoutManager(requireContext()));
        controls.setItemAnimator(null);
        mControls = new SettingsAdapter(this);
        controls.setAdapter(mControls);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        render();
        // Not answered on the home screen (backed out of it): ask here.
        StatsConsent.ask(requireActivity(), this::render);
    }

    @Override
    public void onDestroyView() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        super.onDestroyView();
    }

    private void setRecording(boolean on) {
        LocalStats.setRecording(requireContext(), on);
        render();
    }

    @Override
    public void onRowClicked(@NonNull SettingsRow row) {
        if (row == mClearRow) {
            confirmClear();
        } else if (row.kind == SettingsRow.KIND_SWITCH && row.checked != null && row.toggle != null) {
            row.toggle.set(!row.checked.getAsBoolean());
            render();
        }
    }

    private void confirmClear() {
        Context context = requireContext().getApplicationContext();
        mDialog = new MaterialAlertDialogBuilder(requireContext(), R.style.MobileAlertDialog)
                .setTitle(R.string.mobile_stats_clear_confirm)
                .setMessage(R.string.mobile_stats_clear_body)
                .setPositiveButton(R.string.mobile_settings_clear, (dialog, which) -> {
                    LocalStats.clear(context);
                    MobileSnackbar.show(context, R.string.mobile_settings_cleared);
                    mOffset = 0;
                    render();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void render() {
        Context context = getContext();
        if (context == null || mContent == null) {
            return;
        }
        boolean on = LocalStats.isRecording(context);
        StatsData data = LocalStats.snapshot(context);
        // Stats kept from before a "turn off" stay visible until cleared.
        boolean show = on || !data.isEmpty();
        mOff.setVisibility(on ? View.GONE : View.VISIBLE);
        mContent.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            renderStats(context, data);
        }
        renderControls(context, data);
    }

    private void renderControls(Context context, StatsData data) {
        List<SettingsRow> rows = new ArrayList<>();
        // Off with nothing kept: the card's "Turn on stats" is the only control needed.
        if (LocalStats.isRecording(context) || !data.isEmpty()) {
            rows.add(SettingsRow.toggle(context.getString(R.string.mobile_stats_keep),
                    context.getString(R.string.mobile_stats_keep_summary),
                    () -> LocalStats.isRecording(context), on -> LocalStats.setRecording(context, on)));
        }
        mClearRow = SettingsRow.action(context.getString(R.string.mobile_stats_clear),
                context.getString(R.string.mobile_stats_clear_summary), page -> { }).destructive();
        if (!data.isEmpty()) {
            rows.add(mClearRow);
        }
        mControls.submit(rows);
    }

    private Range range(int mode, int offset, long today) {
        switch (mode) {
            case MODE_DAY:
                return new Range(today - offset, today - offset);
            case MODE_MONTH:
                return new Range(StatsCalendar.addMonths(today, -offset), StatsCalendar.addMonths(today, -offset + 1) - 1);
            case MODE_YEAR:
                return new Range(StatsCalendar.addYears(today, -offset), StatsCalendar.addYears(today, -offset + 1) - 1);
            case MODE_ALL:
                // Only used for the bars: one per year.
                return new Range(StatsCalendar.addYears(today, -offset), StatsCalendar.addYears(today, -offset + 1) - 1);
            case MODE_WEEK:
            default:
                long week = StatsCalendar.weekStart(today, Calendar.getInstance().getFirstDayOfWeek()) - 7L * offset;
                return new Range(week, week + 6);
        }
    }

    private void renderStats(Context context, StatsData data) {
        View root = requireView();
        for (int i = 0; i < mTabs.length; i++) {
            mTabs[i].setSelected(i == mMode);
        }

        long today = StatsCalendar.today();
        long sinceDay = data.isEmpty() ? today : StatsCalendar.dayOf(data.getSinceMs(), TimeZone.getDefault());
        boolean all = mMode == MODE_ALL;
        if (all) {
            mOffset = 0;
        }
        // Nothing before the first day counted: the arrows stop at its period.
        while (mOffset > 0 && range(mMode, mOffset, today).to < sinceDay) {
            mOffset--;
        }
        Range range = range(mMode, mOffset, today);

        StatsData.Totals period = all ? data.allTime() : data.sum(range.from, range.to);
        mPrev.setVisibility(all ? View.GONE : View.VISIBLE);
        mNext.setVisibility(all ? View.GONE : View.VISIBLE);
        mPrev.setEnabled(range.from > sinceDay);
        mPrev.setAlpha(range.from > sinceDay ? 1f : 0.3f);
        mNext.setEnabled(mOffset > 0);
        mNext.setAlpha(mOffset > 0 ? 1f : 0.3f);

        ((TextView) root.findViewById(R.id.stats_period_label)).setText(all
                ? context.getString(R.string.mobile_stats_all_time) : periodName(context, mMode, mOffset, range));
        ((TextView) root.findViewById(R.id.stats_period_time)).setText(duration(context, period.watchMs));
        TextView compare = root.findViewById(R.id.stats_period_compare);
        if (all) {
            compare.setText(data.isEmpty() ? null : context.getString(R.string.mobile_stats_since, formatDate(data.getSinceMs())));
        } else {
            Range previous = range(mMode, mOffset + 1, today);
            compare.setText(context.getString(R.string.mobile_stats_compare, periodName(context, mMode, mOffset + 1, previous),
                    duration(context, data.sum(previous.from, previous.to).watchMs)));
        }

        renderChart(context, data, root, today);

        StatsSplitBar split = root.findViewById(R.id.stats_split);
        split.setData(period.streamedMs(), period.offlineMs);
        ((TextView) root.findViewById(R.id.stats_split_text)).setText(splitText(context, period));

        NumberFormat numbers = NumberFormat.getIntegerInstance();
        ((TextView) root.findViewById(R.id.stats_videos)).setText(numbers.format(period.videos));
        ((TextView) root.findViewById(R.id.stats_downloads)).setText(numbers.format(period.downloads));
        ((TextView) root.findViewById(R.id.stats_average)).setText(duration(context, dailyAverage(data, range, today, sinceDay, all)));

        LinearLayout channels = root.findViewById(R.id.stats_channels);
        channels.removeAllViews();
        List<StatsData.Channel> top = data.topChannels(TOP_CHANNELS);
        root.findViewById(R.id.stats_channels_card).setVisibility(top.isEmpty() ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(context);
        for (int i = 0; i < top.size(); i++) {
            StatsData.Channel channel = top.get(i);
            View row = inflater.inflate(R.layout.item_mobile_stats_channel, channels, false);
            ((TextView) row.findViewById(R.id.stats_channel_rank)).setText(String.valueOf(i + 1));
            ((TextView) row.findViewById(R.id.stats_channel_name)).setText(channel.name);
            ((TextView) row.findViewById(R.id.stats_channel_time)).setText(duration(context, channel.watchMs));
            channels.addView(row);
        }

        renderKinds(context, data, root, inflater);
        renderCategories(context, data, root, inflater);
        View recap = root.findViewById(R.id.stats_recap);
        recap.setVisibility(data.isEmpty() ? View.GONE : View.VISIBLE);
        recap.setOnClickListener(v -> {
            if (getActivity() instanceof MobileSettingsActivity) {
                ((MobileSettingsActivity) getActivity()).openStatsRecap(this);
            }
        });

        StatsData.Totals allTime = data.allTime();
        String allText = context.getString(R.string.mobile_stats_all_time_watch, duration(context, allTime.watchMs))
                + "\n" + context.getResources().getQuantityString(R.plurals.mobile_stats_all_time_videos, allTime.videos, allTime.videos)
                + "\n" + context.getString(R.string.mobile_stats_all_time_downloads, allTime.downloads,
                data.getVideoDownloads(), data.getAudioDownloads());
        ((TextView) root.findViewById(R.id.stats_all_time)).setText(allText);
        TextView since = root.findViewById(R.id.stats_since);
        since.setVisibility(data.isEmpty() ? View.GONE : View.VISIBLE);
        if (!data.isEmpty()) {
            since.setText(context.getString(R.string.mobile_stats_since, formatDate(data.getSinceMs())));
        }
        // All time repeats the card at the top: leave it out there.
        root.findViewById(R.id.stats_all_time_card).setVisibility(all ? View.GONE : View.VISIBLE);
    }

    /** Videos, Shorts, live and music: all-time watch time and share, most watched first. */
    private static void renderKinds(Context context, StatsData data, View root, LayoutInflater inflater) {
        LinearLayout kinds = root.findViewById(R.id.stats_kinds);
        kinds.removeAllViews();
        long total = 0;
        for (String kind : StatsData.KINDS) {
            total += data.kindMs(kind);
        }
        root.findViewById(R.id.stats_kinds_card).setVisibility(total > 0 ? View.VISIBLE : View.GONE);
        if (total <= 0) {
            return;
        }
        List<String> order = new ArrayList<>(java.util.Arrays.asList(StatsData.KINDS));
        java.util.Collections.sort(order, (a, b) -> Long.compare(data.kindMs(b), data.kindMs(a)));
        for (String kind : order) {
            long ms = data.kindMs(kind);
            if (ms <= 0) {
                continue;
            }
            int name = StatsData.KIND_SHORTS.equals(kind) ? R.string.mobile_stats_kind_shorts
                    : StatsData.KIND_LIVE.equals(kind) ? R.string.mobile_stats_kind_live
                    : StatsData.KIND_MUSIC.equals(kind) ? R.string.mobile_stats_kind_music : R.string.mobile_stats_kind_video;
            View row = inflater.inflate(R.layout.item_mobile_stats_channel, kinds, false);
            TextView share = row.findViewById(R.id.stats_channel_rank);
            share.setMinWidth(Math.round(48 * context.getResources().getDisplayMetrics().density));
            share.setText(Math.round(100f * ms / total) + "%");
            ((TextView) row.findViewById(R.id.stats_channel_name)).setText(name);
            ((TextView) row.findViewById(R.id.stats_channel_time)).setText(duration(context, ms));
            kinds.addView(row);
        }
    }

    /** YouTube's categories of what was watched, top six, all time. */
    private static void renderCategories(Context context, StatsData data, View root, LayoutInflater inflater) {
        LinearLayout list = root.findViewById(R.id.stats_categories);
        list.removeAllViews();
        List<java.util.Map.Entry<String, Long>> categories = data.categories();
        long total = 0;
        for (java.util.Map.Entry<String, Long> entry : categories) {
            total += entry.getValue();
        }
        root.findViewById(R.id.stats_categories_card).setVisibility(total > 0 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < categories.size() && i < 6 && total > 0; i++) {
            java.util.Map.Entry<String, Long> entry = categories.get(i);
            View row = inflater.inflate(R.layout.item_mobile_stats_channel, list, false);
            TextView share = row.findViewById(R.id.stats_channel_rank);
            share.setMinWidth(Math.round(48 * context.getResources().getDisplayMetrics().density));
            share.setText(Math.round(100f * entry.getValue() / total) + "%");
            ((TextView) row.findViewById(R.id.stats_channel_name)).setText(entry.getKey());
            ((TextView) row.findViewById(R.id.stats_channel_time)).setText(duration(context, entry.getValue()));
            list.addView(row);
        }
    }

    /** Day: the 7 days up to the shown one. Otherwise: the days of the period counted so far. */
    private long dailyAverage(StatsData data, Range range, long today, long sinceDay, boolean all) {
        if (mMode == MODE_DAY) {
            return data.sum(range.from - 6, range.to).watchMs / 7;
        }
        long first = all ? sinceDay : Math.max(range.from, sinceDay);
        long last = Math.min(range.to, today);
        long days = Math.max(1, last - first + 1);
        long watched = all ? data.allTime().watchMs : data.sum(range.from, range.to).watchMs;
        return watched / days;
    }

    /**
     * The bars: the latest periods while the shown one is among them (it is lit), else the ones
     * ending at it. Year shows its own twelve months, All time one bar per year (five years). A
     * tap on a bar shows that period; on a month of Year or a year of All time it opens that tab.
     */
    private void renderChart(Context context, StatsData data, View root, long today) {
        int bars = BARS[mMode];
        Range[] ranges = new Range[bars];
        mBarModes = new int[bars];
        mBarOffsets = new int[bars];
        int highlight = -1;
        String skeleton;
        if (mMode == MODE_YEAR) {
            long january = StatsCalendar.addYears(today, -mOffset);
            for (int i = 0; i < bars; i++) {
                long month = StatsCalendar.addMonths(january, i);
                ranges[i] = new Range(month, StatsCalendar.addMonths(january, i + 1) - 1);
                mBarModes[i] = MODE_MONTH;
                mBarOffsets[i] = StatsCalendar.monthIndex(today) - StatsCalendar.monthIndex(month);
            }
            if (mOffset == 0) {
                highlight = StatsCalendar.monthIndex(today) - StatsCalendar.monthIndex(january);
            }
            skeleton = "MMMMM";
        } else {
            int barMode = mMode == MODE_ALL ? MODE_YEAR : mMode;
            int endOffset = mOffset < bars ? 0 : mOffset;
            for (int i = 0; i < bars; i++) {
                int offset = endOffset + bars - 1 - i;
                ranges[i] = range(barMode, offset, today);
                mBarModes[i] = barMode;
                mBarOffsets[i] = offset;
            }
            if (mMode != MODE_ALL) {
                highlight = bars - 1 - (mOffset - endOffset);
            }
            skeleton = barMode == MODE_DAY ? "EEE" : barMode == MODE_WEEK ? "MMMd" : barMode == MODE_MONTH ? "MMM" : "yyyy";
        }

        Locale locale = Locale.getDefault();
        SimpleDateFormat format = new SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        long[] streamed = new long[bars];
        long[] offline = new long[bars];
        String[] labels = new String[bars];
        long max = 0;
        for (int i = 0; i < bars; i++) {
            StatsData.Totals totals = data.sum(ranges[i].from, ranges[i].to);
            streamed[i] = totals.streamedMs();
            offline[i] = totals.offlineMs;
            labels[i] = format.format(new Date(StatsCalendar.utcMs(ranges[i].from)));
            max = Math.max(max, totals.watchMs);
        }
        StatsBarChart chart = root.findViewById(R.id.stats_chart);
        chart.setData(streamed, offline, labels, highlight, max > 0 ? duration(context, max) : null);
    }

    /** "This week", "Last week", "Week of Nov 9"; the same for days, months and years. */
    private static String periodName(Context context, int mode, int offset, Range range) {
        if (offset <= 1) {
            int[][] names = {
                    {R.string.mobile_stats_today, R.string.mobile_stats_yesterday},
                    {R.string.mobile_stats_this_week, R.string.mobile_stats_last_week},
                    {R.string.mobile_stats_this_month, R.string.mobile_stats_last_month},
                    {R.string.mobile_stats_this_year, R.string.mobile_stats_last_year}};
            return context.getString(names[mode][offset]);
        }
        Locale locale = Locale.getDefault();
        String skeleton = mode == MODE_DAY ? "EEEMMMd" : mode == MODE_WEEK ? "MMMd" : mode == MODE_MONTH ? "MMMMyyyy" : "yyyy";
        SimpleDateFormat format = new SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String date = format.format(new Date(StatsCalendar.utcMs(range.from)));
        return mode == MODE_WEEK ? context.getString(R.string.mobile_stats_week_of, date) : date;
    }

    private static String formatDate(long wallMs) {
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(new Date(wallMs));
    }

    private static String splitText(Context context, StatsData.Totals period) {
        if (period.watchMs <= 0) {
            return context.getString(R.string.mobile_stats_split_none);
        }
        int offline = Math.round(100f * period.offlineMs / period.watchMs);
        int streamed = 100 - offline;
        int res = Math.abs(streamed - offline) <= 10 ? R.string.mobile_stats_split_even
                : streamed > offline ? R.string.mobile_stats_split_more_streamed : R.string.mobile_stats_split_more_offline;
        return context.getString(res, streamed, offline);
    }

    /** "2 h 14 min", or "45 min". */
    static String duration(Context context, long ms) {
        long minutes = ms / 60_000;
        if (minutes < 60) {
            return context.getString(R.string.mobile_stats_minutes, (int) minutes);
        }
        return context.getString(R.string.mobile_stats_hours_minutes, (int) (minutes / 60), (int) (minutes % 60));
    }
}
