package com.newtube.mobile.ui.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * The Stats page's bars: one per day, week, month or year, each split into streamed time (bottom)
 * and time played from downloads (on top). The period shown above the chart is lit, the rest
 * dimmed (all lit when none is). A tap on a bar reports which.
 */
public final class StatsBarChart extends View {
    public interface OnBarClickListener {
        void onBarClick(int index);
    }

    private final Paint mStreamed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mOffline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Path mPath = new Path();
    private final float mRadius;
    private final float mLabelGap;

    private long[] mStreamedMs = new long[0];
    private long[] mOfflineMs = new long[0];
    private String[] mLabels = new String[0];
    @Nullable private String mMaxLabel;
    private int mHighlight = -1;
    @Nullable private OnBarClickListener mListener;

    public StatsBarChart(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mStreamed.setColor(ContextCompat.getColor(context, R.color.mobile_color_progress));
        mOffline.setColor(ContextCompat.getColor(context, R.color.mobile_color_link));
        mTrack.setColor(ContextCompat.getColor(context, R.color.mobile_color_pill));
        mGrid.setColor(ContextCompat.getColor(context, R.color.mobile_color_divider));
        mGrid.setStrokeWidth(dp(1));
        mLabel.setColor(ContextCompat.getColor(context, R.color.mobile_color_on_surface_secondary));
        mLabel.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics()));
        mLabel.setTextAlign(Paint.Align.CENTER);
        mRadius = dp(4);
        mLabelGap = dp(6);
    }

    /**
     * {@code highlight}: the lit bar, or -1 for all. {@code maxLabel}: the tallest bar's value,
     * written on the top gridline.
     */
    public void setData(@NonNull long[] streamedMs, @NonNull long[] offlineMs, @NonNull String[] labels, int highlight,
                        @Nullable String maxLabel) {
        mStreamedMs = streamedMs;
        mOfflineMs = offlineMs;
        mLabels = labels;
        mHighlight = highlight;
        mMaxLabel = maxLabel;
        invalidate();
    }

    public void setOnBarClickListener(@Nullable OnBarClickListener listener) {
        mListener = listener;
        setClickable(listener != null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mListener == null || mLabels.length == 0) {
            return super.onTouchEvent(event);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            int index = (int) (event.getX() / (getWidth() / (float) mLabels.length));
            if (index >= 0 && index < mLabels.length) {
                playSoundEffect(android.view.SoundEffectConstants.CLICK);
                mListener.onBarClick(index);
            }
        }
        return true;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int count = mLabels.length;
        if (count == 0) {
            return;
        }
        float labelHeight = mLabel.getTextSize() + mLabelGap;
        float top = labelHeight;
        float bottom = getHeight() - labelHeight;
        float width = getWidth();
        float slot = width / count;
        float barWidth = Math.min(slot * 0.56f, dp(28));

        long max = 0;
        for (int i = 0; i < count; i++) {
            max = Math.max(max, mStreamedMs[i] + mOfflineMs[i]);
        }

        canvas.drawLine(0, top, width, top, mGrid);
        canvas.drawLine(0, bottom, width, bottom, mGrid);
        if (mMaxLabel != null && max > 0) {
            mLabel.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(mMaxLabel, 0, top - mLabelGap, mLabel);
            mLabel.setTextAlign(Paint.Align.CENTER);
        }

        for (int i = 0; i < count; i++) {
            float center = slot * i + slot / 2;
            float left = center - barWidth / 2;
            float right = center + barWidth / 2;
            boolean current = mHighlight < 0 || i == mHighlight;
            int alpha = current ? 255 : 140;

            long total = mStreamedMs[i] + mOfflineMs[i];
            if (total <= 0 || max <= 0) {
                mRect.set(left, bottom - dp(3), right, bottom);
                canvas.drawRoundRect(mRect, dp(1.5f), dp(1.5f), mTrack);
            } else {
                float height = Math.max(dp(3), (bottom - top) * total / (float) max);
                float streamedHeight = height * mStreamedMs[i] / (float) total;
                float barTop = bottom - height;
                // Streamed at the bottom, downloads on top; only the top corners are rounded.
                canvas.save();
                mRect.set(left, barTop, right, bottom);
                mPath.reset();
                mPath.addRoundRect(mRect, new float[] {mRadius, mRadius, mRadius, mRadius, 0, 0, 0, 0}, Path.Direction.CW);
                canvas.clipPath(mPath);
                mStreamed.setAlpha(alpha);
                mOffline.setAlpha(alpha);
                canvas.drawRect(left, bottom - streamedHeight, right, bottom, mStreamed);
                canvas.drawRect(left, barTop, right, bottom - streamedHeight, mOffline);
                canvas.restore();
            }

            mLabel.setFakeBoldText(i == mHighlight);
            canvas.drawText(mLabels[i], center, getHeight() - dp(2), mLabel);
        }
        mLabel.setFakeBoldText(false);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
