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
 * Settings > Stats: watch time by day, week and month, downloads against streaming, counts and the
 * most watched channels, all from {@link LocalStats} (on this phone only). The first open asks
 * whether to keep stats at all; until the answer is yes nothing is recorded.
 */
public final class StatsFragment extends Fragment implements SettingsAdapter.Listener {
    private static final int MODE_DAY = 0;
    private static final int MODE_WEEK = 1;
    private static final int MODE_MONTH = 2;
    private static final int DAY_BARS = 7;
    private static final int WEEK_BARS = 8;
    private static final int MONTH_BARS = 6;
    private static final int TOP_CHANNELS = 5;
    private static final String STATE_MODE = "mode";

    private int mMode = MODE_WEEK;
    private View mOff;
    private View mContent;
    private TextView[] mTabs;
    private SettingsAdapter mControls;
    @Nullable private SettingsRow mClearRow;
    @Nullable private AlertDialog mDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mMode = savedInstanceState.getInt(STATE_MODE, MODE_WEEK);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_MODE, mMode);
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
                view.findViewById(R.id.stats_tab_month)};
        for (int i = 0; i < mTabs.length; i++) {
            int mode = i;
            mTabs[i].setOnClickListener(v -> {
                mMode = mode;
                render();
            });
        }

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
        if (LocalStats.getConsent(requireContext()) == LocalStats.CONSENT_UNASKED && (mDialog == null || !mDialog.isShowing())) {
            askConsent();
        }
    }

    @Override
    public void onDestroyView() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        super.onDestroyView();
    }

    /** The first open: keep stats or not. Back or a tap outside leaves it undecided (asked again next time). */
    private void askConsent() {
        mDialog = new MaterialAlertDialogBuilder(requireContext(), R.style.MobileAlertDialog)
                .setTitle(R.string.mobile_stats_consent_title)
                .setMessage(R.string.mobile_stats_consent_body)
                .setPositiveButton(R.string.mobile_stats_consent_yes, (dialog, which) -> setRecording(true))
                .setNegativeButton(R.string.mobile_stats_consent_no, (dialog, which) -> setRecording(false))
                .show();
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

    private void renderStats(Context context, StatsData data) {
        View root = requireView();
        for (int i = 0; i < mTabs.length; i++) {
            mTabs[i].setSelected(i == mMode);
        }

        long today = StatsCalendar.today();
        int firstDayOfWeek = Calendar.getInstance().getFirstDayOfWeek();
        long from;
        long prevFrom;
        long prevTo;
        int label;
        int compare;
        int bars;
        switch (mMode) {
            case MODE_DAY:
                from = today;
                prevFrom = prevTo = today - 1;
                label = R.string.mobile_stats_today;
                compare = R.string.mobile_stats_compare_day;
                bars = DAY_BARS;
                break;
            case MODE_MONTH:
                from = StatsCalendar.monthStart(today);
                prevFrom = StatsCalendar.addMonths(today, -1);
                prevTo = from - 1;
                label = R.string.mobile_stats_this_month;
                compare = R.string.mobile_stats_compare_month;
                bars = MONTH_BARS;
                break;
            case MODE_WEEK:
            default:
                from = StatsCalendar.weekStart(today, firstDayOfWeek);
                prevFrom = from - 7;
                prevTo = from - 1;
                label = R.string.mobile_stats_this_week;
                compare = R.string.mobile_stats_compare_week;
                bars = WEEK_BARS;
                break;
        }

        StatsData.Totals period = data.sum(from, today);
        StatsData.Totals previous = data.sum(prevFrom, prevTo);
        ((TextView) root.findViewById(R.id.stats_period_label)).setText(label);
        ((TextView) root.findViewById(R.id.stats_period_time)).setText(duration(context, period.watchMs));
        ((TextView) root.findViewById(R.id.stats_period_compare)).setText(
                context.getString(compare, duration(context, previous.watchMs)));

        renderChart(context, data, root, today, firstDayOfWeek, bars);

        StatsSplitBar split = root.findViewById(R.id.stats_split);
        split.setData(period.streamedMs(), period.offlineMs);
        ((TextView) root.findViewById(R.id.stats_split_text)).setText(splitText(context, period));

        NumberFormat numbers = NumberFormat.getIntegerInstance();
        ((TextView) root.findViewById(R.id.stats_videos)).setText(numbers.format(period.videos));
        ((TextView) root.findViewById(R.id.stats_downloads)).setText(numbers.format(period.downloads));
        // Day: the last 7 days' average. Week and month: the days of it so far.
        long averageFrom = mMode == MODE_DAY ? today - (DAY_BARS - 1) : from;
        long averageDays = today - averageFrom + 1;
        long averageMs = (mMode == MODE_DAY ? data.sum(averageFrom, today).watchMs : period.watchMs) / averageDays;
        ((TextView) root.findViewById(R.id.stats_average)).setText(duration(context, averageMs));

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

        StatsData.Totals all = data.allTime();
        String allTime = context.getString(R.string.mobile_stats_all_time_watch, duration(context, all.watchMs))
                + "\n" + context.getResources().getQuantityString(R.plurals.mobile_stats_all_time_videos, all.videos, all.videos)
                + "\n" + context.getString(R.string.mobile_stats_all_time_downloads, all.downloads,
                data.getVideoDownloads(), data.getAudioDownloads());
        ((TextView) root.findViewById(R.id.stats_all_time)).setText(allTime);
        TextView since = root.findViewById(R.id.stats_since);
        since.setVisibility(data.isEmpty() ? View.GONE : View.VISIBLE);
        if (!data.isEmpty()) {
            since.setText(context.getString(R.string.mobile_stats_since,
                    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(new Date(data.getSinceMs()))));
        }
    }

    private void renderChart(Context context, StatsData data, View root, long today, int firstDayOfWeek, int bars) {
        long[] streamed = new long[bars];
        long[] offline = new long[bars];
        String[] labels = new String[bars];
        Locale locale = Locale.getDefault();
        SimpleDateFormat format = new SimpleDateFormat(DateFormat.getBestDateTimePattern(locale,
                mMode == MODE_DAY ? "EEE" : mMode == MODE_WEEK ? "MMMd" : "MMM"), locale);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        long thisWeek = StatsCalendar.weekStart(today, firstDayOfWeek);
        long max = 0;
        for (int i = 0; i < bars; i++) {
            int back = bars - 1 - i;
            long from;
            long to;
            if (mMode == MODE_DAY) {
                from = to = today - back;
            } else if (mMode == MODE_WEEK) {
                from = thisWeek - 7L * back;
                to = from + 6;
            } else {
                from = StatsCalendar.addMonths(today, -back);
                to = StatsCalendar.addMonths(today, -back + 1) - 1;
            }
            StatsData.Totals totals = data.sum(from, to);
            streamed[i] = totals.streamedMs();
            offline[i] = totals.offlineMs;
            labels[i] = format.format(new Date(StatsCalendar.utcMs(from)));
            max = Math.max(max, totals.watchMs);
        }
        StatsBarChart chart = root.findViewById(R.id.stats_chart);
        chart.setData(streamed, offline, labels, max > 0 ? duration(context, max) : null);
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
