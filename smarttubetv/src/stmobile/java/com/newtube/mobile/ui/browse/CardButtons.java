package com.newtube.mobile.ui.browse;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.LocalWatchLater;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeSignInService;
import com.newtube.mobile.ui.common.MobileSnackbar;

/**
 * The Watch later button on feed thumbnails (Home, Subscriptions, Search), top right; switchable in
 * Settings > Feeds. In Watch later itself it shows every card saved and takes the video out.
 *
 * <p>Watch later signed out is the phone's own list. Signed in the app only learns per video
 * whether it is in the account's Watch later: a card shows "saved" once this session has saved
 * it or asked, and the first tap on an unknown card asks before it adds.</p>
 */
public final class CardButtons {
    interface OnChanged {
        void onChanged(Video video);
    }

    /**
     * A screen listing Watch later itself: there the button shows each card saved, and a tap takes
     * the video out, which the screen answers by dropping the card (and putting it back on Undo).
     */
    public interface WatchLaterList {
        void onRemoved(Video video);

        void onRestored(Video video);
    }

    private static final String PREFS_NAME = "newtube_cards";
    private static final String KEY_WATCH_LATER = "watch_later_button";

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

    /** Changes whenever a button is switched on or off. */
    static String signature(Context context) {
        return String.valueOf(isWatchLaterOn(context));
    }

    static boolean canSaveForLater(Video video) {
        return LocalWatchLater.canSave(video);
    }

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

    static void removeFromWatchLater(Context context, Video video, WatchLaterList list) {
        Context app = context.getApplicationContext();
        boolean signed = YouTubeSignInService.instance().isSigned();
        if (signed) {
            editAccountWatchLater(app, video, false);
        } else {
            LocalWatchLater.instance(app).remove(video.videoId);
        }
        list.onRemoved(video);
        MobileSnackbar.show(app, app.getString(R.string.removed_from_watch_later), app.getString(R.string.action_undo), () -> {
            if (signed) {
                editAccountWatchLater(app, video, true);
            } else {
                LocalWatchLater.instance(app).add(video);
            }
            list.onRestored(video);
        });
    }

    private static void setSaved(Context context, Video video, boolean saved) {
        editAccountWatchLater(context, video, saved);
        say(context, context.getString(saved ? R.string.saved_to_watch_later : R.string.removed_from_watch_later));
    }

    private static void editAccountWatchLater(Context context, Video video, boolean saved) {
        MediaServiceManager.instance().addRemoveFromWatchLaterPlaylist(video, saved);
        BrowsePresenter.instance(context).onPlaylistEdited();
    }

    private static void say(Context context, String message) {
        MobileSnackbar.show(context, message, null, null);
    }
}
