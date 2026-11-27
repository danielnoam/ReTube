package com.newtube.mobile.ui.settings;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.stats.LocalStats;
import com.newtube.mobile.stats.StatsCalendar;
import com.newtube.mobile.stats.StatsData;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.function.LongFunction;

/**
 * Stats > "Get disappointed in your life": the biggest all-time numbers as full-screen cards, one
 * after another like stories. A number counts up as its card comes in; a tap on the right goes on,
 * on the left goes back; the last card closes it. Cards with nothing to say are left out.
 */
public final class StatsRecapFragment extends Fragment {
    private static final long CARD_IN_MS = 450;
    private static final long COUNT_MS = 1400;
    private static final int[] BACKGROUNDS = {
            0xFF140A0A, 0xFF2A0610, 0xFF0A1A2E, 0xFF2E0B24, 0xFF0B2A1E, 0xFF2E1F0B, 0xFF1C0B2E, 0xFF0B2228, 0xFF2A0A0A, 0xFF101010};

    /** One card: small line, big number (counted up from 0 to {@code value}), caption, aside. */
    private static final class Card {
        final CharSequence kicker;
        final long value;
        final LongFunction<String> big;
        final CharSequence caption;
        @Nullable final CharSequence aside;
        final float bigSp;

        Card(CharSequence kicker, long value, LongFunction<String> big, CharSequence caption, @Nullable CharSequence aside, float bigSp) {
            this.kicker = kicker;
            this.value = value;
            this.big = big;
            this.caption = caption;
            this.aside = aside;
            this.bigSp = bigSp;
        }
    }

    private final List<Card> mCards = new ArrayList<>();
    private int mIndex = -1;
    private FrameLayout mRoot;
    private LinearLayout mProgress;
    private TextView mKicker;
    private TextView mBig;
    private TextView mCaption;
    private TextView mAside;
    @Nullable private ValueAnimator mCount;
    @Nullable private ValueAnimator mTint;
    private int mBackground = BACKGROUNDS[0];

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        buildCards(context, LocalStats.snapshot(context));

        mRoot = new FrameLayout(context);
        mRoot.setBackgroundColor(mBackground);
        mRoot.setClickable(true);

        mProgress = new LinearLayout(context);
        mProgress.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        progressParams.setMargins(dp(12), dp(12), dp(12), 0);
        for (int i = 0; i < mCards.size(); i++) {
            View segment = new View(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            params.setMargins(dp(2), 0, dp(2), 0);
            segment.setBackgroundColor(Color.WHITE);
            mProgress.addView(segment, params);
        }
        mRoot.addView(mProgress, progressParams);

        ImageButton close = new ImageButton(context);
        close.setImageResource(R.drawable.ic_mobile_back_arrow);
        close.setColorFilter(Color.WHITE);
        close.setBackgroundResource(android.R.color.transparent);
        close.setContentDescription(getString(R.string.mobile_dialog_back));
        close.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.START);
        closeParams.setMargins(dp(4), dp(20), 0, 0);
        mRoot.addView(close, closeParams);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(28), 0, dp(28), 0);
        mKicker = text(context, 20, false, 0xCCFFFFFF);
        mBig = text(context, 64, true, Color.WHITE);
        mCaption = text(context, 24, true, Color.WHITE);
        mAside = text(context, 17, false, 0xB3FFFFFF);
        column.addView(mKicker);
        column.addView(mBig);
        column.addView(mCaption);
        column.addView(mAside);
        ((LinearLayout.LayoutParams) mBig.getLayoutParams()).topMargin = dp(8);
        ((LinearLayout.LayoutParams) mCaption.getLayoutParams()).topMargin = dp(4);
        ((LinearLayout.LayoutParams) mAside.getLayoutParams()).topMargin = dp(24);
        mRoot.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        mRoot.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (event.getX() < v.getWidth() / 3f) {
                    show(mIndex - 1);
                } else {
                    show(mIndex + 1);
                }
                v.performClick();
            }
            return true;
        });

        show(0);
        return mRoot;
    }

    @Override
    public void onDestroyView() {
        if (mCount != null) {
            mCount.cancel();
        }
        if (mTint != null) {
            mTint.cancel();
        }
        super.onDestroyView();
    }

    private void show(int index) {
        if (index < 0) {
            return;
        }
        if (index >= mCards.size()) {
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
            return;
        }
        boolean forward = index > mIndex;
        mIndex = index;
        Card card = mCards.get(index);

        for (int i = 0; i < mProgress.getChildCount(); i++) {
            mProgress.getChildAt(i).setAlpha(i <= index ? 1f : 0.3f);
        }

        int target = BACKGROUNDS[index % BACKGROUNDS.length];
        if (mTint != null) {
            mTint.cancel();
        }
        mTint = ValueAnimator.ofObject(new ArgbEvaluator(), mBackground, target);
        mTint.setDuration(CARD_IN_MS);
        mTint.addUpdateListener(a -> {
            mBackground = (int) a.getAnimatedValue();
            mRoot.setBackgroundColor(mBackground);
        });
        mTint.start();

        mKicker.setText(card.kicker);
        mBig.setTextSize(TypedValue.COMPLEX_UNIT_SP, card.bigSp);
        mBig.setText(card.big.apply(0));
        mCaption.setText(card.caption);
        mAside.setText(card.aside);
        mAside.setVisibility(card.aside != null ? View.VISIBLE : View.GONE);

        View[] parts = {mKicker, mBig, mCaption, mAside};
        float shift = dp(forward ? 32 : -32);
        for (int i = 0; i < parts.length; i++) {
            View part = parts[i];
            part.animate().cancel();
            part.setAlpha(0f);
            part.setTranslationY(shift);
            part.animate().alpha(1f).translationY(0f).setStartDelay(90L * i).setDuration(CARD_IN_MS)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }

        if (mCount != null) {
            mCount.cancel();
        }
        if (card.value > 0) {
            mCount = ValueAnimator.ofFloat(0f, 1f);
            mCount.setStartDelay(150);
            mCount.setDuration(COUNT_MS);
            mCount.setInterpolator(new DecelerateInterpolator(2f));
            mCount.addUpdateListener(a -> mBig.setText(card.big.apply(Math.round(card.value * (float) a.getAnimatedValue()))));
            mCount.start();
        } else {
            mBig.setText(card.big.apply(card.value));
        }
    }

    private void buildCards(Context context, StatsData data) {
        StatsData.Totals all = data.allTime();
        NumberFormat numbers = NumberFormat.getIntegerInstance();
        long hours = all.watchMs / 3_600_000L;

        mCards.add(new Card(getString(R.string.mobile_recap_intro_kicker), 0, v -> getString(R.string.mobile_recap_intro_big),
                getString(R.string.mobile_recap_intro_caption), getString(R.string.mobile_recap_tap_hint), 56));

        if (hours >= 1) {
            mCards.add(new Card(getString(R.string.mobile_recap_total_kicker), hours,
                    v -> getResources().getQuantityString(R.plurals.mobile_recap_hours, (int) v, numbers.format(v)),
                    getString(R.string.mobile_recap_total_caption),
                    getString(R.string.mobile_recap_total_aside, oneDecimal(all.watchMs / 86_400_000d), oneDecimal(all.watchMs / 57_600_000d)), 64));
            long books = all.watchMs / (8 * 3_600_000L);
            long movies = all.watchMs / (2 * 3_600_000L);
            long nights = all.watchMs / (8 * 3_600_000L);
            if (movies >= 1) {
                mCards.add(new Card(getString(R.string.mobile_recap_instead_kicker), movies,
                        v -> getResources().getQuantityString(R.plurals.mobile_recap_movies, (int) v, numbers.format(v)),
                        getString(R.string.mobile_recap_instead_caption),
                        books >= 1 ? getString(R.string.mobile_recap_instead_aside, numbers.format(books), numbers.format(nights)) : null, 56));
            }
        } else {
            mCards.add(new Card(getString(R.string.mobile_recap_total_kicker), all.watchMs / 60_000,
                    v -> StatsFragment.duration(context, v * 60_000), getString(R.string.mobile_recap_total_caption),
                    getString(R.string.mobile_recap_little), 64));
        }

        long worst = data.biggestDay();
        if (worst >= 0) {
            long ms = data.dayWatchMs(worst);
            Locale locale = Locale.getDefault();
            SimpleDateFormat format = new SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), locale);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            mCards.add(new Card(getString(R.string.mobile_recap_worst_kicker), ms / 60_000, v -> StatsFragment.duration(context, v * 60_000),
                    format.format(new Date(StatsCalendar.utcMs(worst))),
                    getString(R.string.mobile_recap_worst_aside, Math.round(100f * ms / 86_400_000f)), 52));
        }

        int peak = data.peakHour();
        if (peak >= 0) {
            int aside = peak < 5 ? R.string.mobile_recap_hour_night : peak < 12 ? R.string.mobile_recap_hour_morning
                    : peak < 18 ? R.string.mobile_recap_hour_day : R.string.mobile_recap_hour_evening;
            mCards.add(new Card(getString(R.string.mobile_recap_hour_kicker), 0, v -> String.format(Locale.getDefault(), "%02d:00", peak),
                    getString(R.string.mobile_recap_hour_caption), getString(aside), 72));
        }

        List<StatsData.Channel> top = data.topChannels(1);
        if (!top.isEmpty() && top.get(0).watchMs >= 60_000) {
            StatsData.Channel channel = top.get(0);
            mCards.add(new Card(getString(R.string.mobile_recap_channel_kicker), 0, v -> channel.name,
                    getString(R.string.mobile_recap_channel_caption, StatsFragment.duration(context, channel.watchMs)),
                    getString(R.string.mobile_recap_channel_aside), 40));
        }

        if (all.videos > 0) {
            mCards.add(new Card(getString(R.string.mobile_recap_videos_kicker), all.videos, numbers::format,
                    getResources().getQuantityString(R.plurals.mobile_recap_videos_caption, all.videos),
                    getString(R.string.mobile_recap_videos_aside, StatsFragment.duration(context, all.watchMs / all.videos)), 72));
        }

        String biggestKind = null;
        long biggestKindMs = 0;
        for (String kind : StatsData.KINDS) {
            if (data.kindMs(kind) > biggestKindMs) {
                biggestKindMs = data.kindMs(kind);
                biggestKind = kind;
            }
        }
        if (biggestKind != null && all.watchMs > 0) {
            int percent = Math.round(100f * biggestKindMs / all.watchMs);
            int caption;
            int aside;
            switch (biggestKind) {
                case StatsData.KIND_SHORTS:
                    caption = R.string.mobile_recap_kind_shorts;
                    aside = R.string.mobile_recap_kind_shorts_aside;
                    break;
                case StatsData.KIND_LIVE:
                    caption = R.string.mobile_recap_kind_live;
                    aside = R.string.mobile_recap_kind_live_aside;
                    break;
                case StatsData.KIND_MUSIC:
                    caption = R.string.mobile_recap_kind_music;
                    aside = R.string.mobile_recap_kind_music_aside;
                    break;
                default:
                    caption = R.string.mobile_recap_kind_video;
                    aside = R.string.mobile_recap_kind_video_aside;
                    break;
            }
            mCards.add(new Card(getString(R.string.mobile_recap_kind_kicker), percent, v -> v + "%",
                    getString(caption), getString(aside), 80));
        }

        List<java.util.Map.Entry<String, Long>> categories = data.categories();
        if (!categories.isEmpty() && categories.get(0).getValue() >= 60_000) {
            java.util.Map.Entry<String, Long> genre = categories.get(0);
            mCards.add(new Card(getString(R.string.mobile_recap_category_kicker), 0, v -> genre.getKey(),
                    getString(R.string.mobile_recap_category_caption, StatsFragment.duration(context, genre.getValue())),
                    getString(R.string.mobile_recap_category_aside), 48));
        }

        if (all.downloads > 0) {
            int offline = all.watchMs > 0 ? Math.round(100f * all.offlineMs / all.watchMs) : 0;
            mCards.add(new Card(getString(R.string.mobile_recap_downloads_kicker), all.downloads, numbers::format,
                    getResources().getQuantityString(R.plurals.mobile_recap_downloads_caption, all.downloads),
                    getString(offline >= 30 ? R.string.mobile_recap_downloads_used : R.string.mobile_recap_downloads_unused, offline), 72));
        }

        mCards.add(new Card(getString(R.string.mobile_recap_outro_kicker), 0, v -> getString(R.string.mobile_recap_outro_big),
                getString(R.string.mobile_recap_outro_caption), getString(R.string.mobile_recap_outro_aside), 56));
    }

    private static String oneDecimal(double value) {
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(1);
        return format.format(value);
    }

    private TextView text(Context context, float sp, boolean bold, int color) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        }
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
