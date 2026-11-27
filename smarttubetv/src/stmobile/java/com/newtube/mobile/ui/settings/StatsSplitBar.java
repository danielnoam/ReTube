package com.newtube.mobile.ui.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/** One rounded bar: streamed time from the left, time played from downloads from the right. */
public final class StatsSplitBar extends View {
    private final Paint mStreamed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mOffline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Path mClip = new Path();
    private long mStreamedMs;
    private long mOfflineMs;

    public StatsSplitBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mStreamed.setColor(ContextCompat.getColor(context, R.color.mobile_color_progress));
        mOffline.setColor(ContextCompat.getColor(context, R.color.mobile_color_link));
        mTrack.setColor(ContextCompat.getColor(context, R.color.mobile_color_pill));
    }

    public void setData(long streamedMs, long offlineMs) {
        mStreamedMs = streamedMs;
        mOfflineMs = offlineMs;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float radius = getHeight() / 2f;
        mRect.set(0, 0, getWidth(), getHeight());
        long total = mStreamedMs + mOfflineMs;
        if (total <= 0) {
            canvas.drawRoundRect(mRect, radius, radius, mTrack);
            return;
        }
        mClip.reset();
        mClip.addRoundRect(mRect, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(mClip);
        float split = getWidth() * mStreamedMs / (float) total;
        canvas.drawRect(0, 0, split, getHeight(), mStreamed);
        canvas.drawRect(split, 0, getWidth(), getHeight(), mOffline);
        canvas.restore();
    }
}
