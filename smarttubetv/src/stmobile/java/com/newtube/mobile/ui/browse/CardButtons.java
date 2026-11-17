package com.newtube.mobile.ui.browse;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.LocalWatchLater;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeSignInService;
import com.newtube.mobile.ui.common.MobileSnackbar;

/**
 * The buttons on feed thumbnails (Home, Subscriptions, Search), each switchable in Settings > Feeds:
 * Watch later (top left) and Watched (top right).
 *
 * <p>Watch later signed out is the phone's own list. Signed in the app only learns per video
 * whether it is in the account's Watch later: a card shows "saved" once this session has saved
 * it or asked, and the first tap on an unknown card asks before it adds.</p>
 */
public final class CardButtons {
    interface OnChanged {
        void onChanged(Video video);
    }

    /** Watched far enough to count as seen: end credits are often skipped. */
    private static final int WATCHED_PERCENT = 90;
    private static final String PREFS_NAME = "newtube_cards";
    private static final String KEY_WATCH_LATER = "watch_later_button";
    private static final String KEY_WATCHED = "watched_button";

    private CardButtons() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isWatchLaterOn(Context context) {
        return prefs(context).getBoolean(KEY_WATCH_LATER, true);
    }

    public static void setWatchLaterOn(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_WATCH_LATER, on).apply();
    }

    public static boolean isWatchedOn(Context context) {
        return prefs(context).getBoolean(KEY_WATCHED, true);
    }

    public static void setWatchedOn(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_WATCHED, on).apply();
    }

    /** Changes whenever a button is switched on or off. */
    static String signature(Context context) {
        return isWatchLaterOn(context) + "," + isWatchedOn(context);
    }

    static boolean canSaveForLater(Video video) {
        return LocalWatchLater.canSave(video);
    }

    static boolean canMarkWatched(Video video) {
        return LocalWatchLater.canSave(video) && !video.isLive && !video.isUpcoming;
    }

    // Watch later

    static boolean isSaved(Context context, Video video) {
        if (!YouTubeSignInService.instance().isSigned()) {
            return LocalWatchLater.instance(context).contains(video.videoId);
        }
        return Boolean.TRUE.equals(MediaServiceManager.instance().getWatchLaterMark(video.videoId));
    }

    static void toggleWatchLater(Context context, Video video, OnChanged onChanged) {
        Context app = context.getApplicationContext();
        if (!YouTubeSignInService.instance().isSigned()) {
            LocalWatchLater watchLater = LocalWatchLater.instance(app);
            if (watchLater.contains(video.videoId)) {
                watchLater.remove(video.videoId);
                say(app, app.getString(R.string.removed_from_watch_later));
            } else {
                say(app, LocalWatchLater.savedMessage(app, watchLater.add(video)));
            }
            onChanged.onChanged(video);
            return;
        }

        MediaServiceManager service = MediaServiceManager.instance();
        Boolean saved = service.getWatchLaterMark(video.videoId);
        if (saved == null) {
            service.checkWatchLater(video, isSaved -> {
                if (isSaved) {
                    say(app, app.getString(R.string.mobile_card_watch_later_already));
                } else {
                    setSaved(app, video, true);
                }
                onChanged.onChanged(video);
            }, error -> say(app, app.getString(R.string.mobile_card_watch_later_error)));
            return;
        }
        setSaved(app, video, !saved);
        onChanged.onChanged(video);
    }

    private static void setSaved(Context context, Video video, boolean saved) {
        MediaServiceManager.instance().addRemoveFromWatchLaterPlaylist(video, saved);
        BrowsePresenter.instance(context).onPlaylistEdited();
        say(context, context.getString(saved ? R.string.saved_to_watch_later : R.string.removed_from_watch_later));
    }

    // Watched

    static boolean isWatched(Video video) {
        return video.percentWatched >= WATCHED_PERCENT;
    }

    /**
     * Marks the video watched (as the menu's "Mark as watched" does), or not watched: its progress
     * goes on the phone, and signed in the account's resume point goes back to the start (YouTube
     * keeps it in the account's history).
     */
    static void toggleWatched(Context context, Video video, OnChanged onChanged) {
        Context app = context.getApplicationContext();
        if (isWatched(video)) {
            if (YouTubeSignInService.instance().isSigned()) {
                MediaServiceManager.instance().updateHistory(video, 0);
            }
            video.markNotViewed();
            VideoStateService.instance(app).removeByVideoId(video.videoId);
            say(app, app.getString(R.string.mobile_card_marked_unwatched));
        } else {
            MediaServiceManager.instance().updateHistory(video, video.getDurationMs());
            video.markFullyViewed();
            VideoStateService.instance(app).save(new State(video, video.getDurationMs()));
            say(app, app.getString(R.string.mobile_card_marked_watched));
        }
        Playlist.instance().sync(video);
        onChanged.onChanged(video);
    }

    private static void say(Context context, String message) {
        MobileSnackbar.show(context, message, null, null);
    }
}
