package com.newtube.mobile.ui.browse;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.LocalWatchLater;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeSignInService;
import com.newtube.mobile.ui.common.MobileSnackbar;

/**
 * The Watch later button on feed thumbnails. Signed out it is the phone's own Watch later; signed
 * in it is the account's, whose contents the app only learns per video: a card shows "saved" once
 * this session has saved it or asked, and the first tap on an unknown card asks before it adds.
 */
final class WatchLaterToggle {
    interface OnChanged {
        void onChanged(Video video);
    }

    private WatchLaterToggle() {
    }

    static boolean canToggle(Video video) {
        return LocalWatchLater.canSave(video);
    }

    static boolean isSaved(Context context, Video video) {
        if (!YouTubeSignInService.instance().isSigned()) {
            return LocalWatchLater.instance(context).contains(video.videoId);
        }
        return Boolean.TRUE.equals(MediaServiceManager.instance().getWatchLaterMark(video.videoId));
    }

    static void toggle(Context context, Video video, OnChanged onChanged) {
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

    private static void say(Context context, String message) {
        MobileSnackbar.show(context, message, null, null);
    }
}
