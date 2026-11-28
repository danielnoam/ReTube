package com.newtube.mobile.stats;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

/**
 * Times what the player actually plays (media3's isPlaying: not paused, not buffering, not ended)
 * for {@link LocalStats}. The video is captured when playing starts, so a stretch is never
 * credited to the next video the player has already been handed. Main thread only.
 */
public final class WatchClock {
    /** Played this long, a video counts as watched (once per time it is opened). */
    static final long WATCHED_AFTER_MS = 30_000;
    /** Saved this often while playing, so a killed process loses at most a minute. */
    private static final long CHECKPOINT_MS = 60_000;
    /** A stretch longer than this is a clock problem, not viewing. */
    private static final long MAX_STRETCH_MS = 6 * 60 * 60_000L;

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mCheckpoint = this::checkpoint;

    private long mStartedAt = -1;
    private String mVideoId;
    private String mChannelId;
    private String mChannelName;
    private boolean mOffline;
    private String mKind;

    private String mCountingVideoId;
    private long mCountingMs;
    private boolean mCounted;
    /** Played time of the counting video not yet given to a category (its lookup is out). */
    private long mUncategorizedMs;

    public WatchClock(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    public void onPlayingChanged(boolean playing, @Nullable Video video) {
        long now = SystemClock.elapsedRealtime();
        flush(now);
        if (playing && video != null && !video.incognito && video.videoId != null && LocalStats.isRecording(mContext)) {
            mVideoId = video.videoId;
            mChannelId = video.channelId;
            mChannelName = video.getAuthor();
            mOffline = video.isLocal();
            mKind = kindOf(video);
            mStartedAt = now;
            mHandler.postDelayed(mCheckpoint, CHECKPOINT_MS);
        }
    }

    public void stop() {
        flush(SystemClock.elapsedRealtime());
    }

    private void checkpoint() {
        if (mStartedAt < 0) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        flush(now);
        mStartedAt = now;
        mHandler.postDelayed(mCheckpoint, CHECKPOINT_MS);
    }

    private void flush(long now) {
        mHandler.removeCallbacks(mCheckpoint);
        if (mStartedAt < 0) {
            return;
        }
        long ms = Math.min(now - mStartedAt, MAX_STRETCH_MS);
        mStartedAt = -1;
        if (ms <= 0) {
            return;
        }
        if (!mVideoId.equals(mCountingVideoId)) {
            mCountingVideoId = mVideoId;
            mCountingMs = 0;
            mCounted = false;
            mUncategorizedMs = 0;
        }
        mCountingMs += ms;
        boolean count = !mCounted && mCountingMs >= WATCHED_AFTER_MS;
        if (count) {
            mCounted = true;
        }
        LocalStats.recordWatch(mContext, mChannelId, mChannelName, mOffline, ms, count, mKind);
        categorize(ms);
    }

    /**
     * YouTube's category for the time: known, it is added at once; else the time waits, and the
     * category is asked once the video counts as watched (a skipped video costs no request).
     * Downloads play offline, so they aren't asked about.
     */
    private void categorize(long ms) {
        String category = VideoCategories.cached(mVideoId);
        if (category != null) {
            LocalStats.recordCategory(mContext, category, ms);
            return;
        }
        mUncategorizedMs += ms;
        if (!mCounted || mOffline) {
            return;
        }
        String videoId = mVideoId;
        long waiting = mUncategorizedMs;
        mUncategorizedMs = 0;
        VideoCategories.fetch(videoId, result -> {
            long more = 0;
            if (videoId.equals(mCountingVideoId)) {
                more = mUncategorizedMs; // played while the answer was on its way
                mUncategorizedMs = 0;
            }
            LocalStats.recordCategory(mContext, result, waiting + more);
        });
    }

    /** Shorts and live from the video itself; music from YouTube's auto-made "Artist - Topic" channels. */
    static String kindOf(@NonNull Video video) {
        if (video.isShorts) {
            return StatsData.KIND_SHORTS;
        }
        if (video.isLive) {
            return StatsData.KIND_LIVE;
        }
        String author = video.getAuthor();
        if (author != null && author.endsWith(" - Topic")) {
            return StatsData.KIND_MUSIC;
        }
        return StatsData.KIND_VIDEO;
    }
}
