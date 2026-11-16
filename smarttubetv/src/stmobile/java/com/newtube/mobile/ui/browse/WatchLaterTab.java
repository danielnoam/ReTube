package com.newtube.mobile.ui.browse;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.misc.LocalWatchLater;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeSignInService;

import java.util.List;

/**
 * Settings > Tabs > "Watch later" in the bottom bar (off by default). Signed out the tab is the
 * phone's own Watch later ({@link LocalWatchLater#SECTION_ID}); signed in it is the account's
 * Watch later playlist ("WL") pinned to You, which turning the switch on pins if it isn't yet.
 */
public final class WatchLaterTab {
    private static final String PREFS_NAME = "newtube_tabs";
    private static final String KEY_WATCH_LATER_TAB = "watch_later_tab";
    private static final String WATCH_LATER_PLAYLIST_ID = "WL";

    private WatchLaterTab() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isOn(Context context) {
        return prefs(context).getBoolean(KEY_WATCH_LATER_TAB, false);
    }

    public static void setOn(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_WATCH_LATER_TAB, on).apply();

        if (on && YouTubeSignInService.instance().isSigned()) {
            pinAccountWatchLater(context);
        }
    }

    public static boolean isWatchLaterSection(BrowseSection section) {
        if (section == null) {
            return false;
        }
        if (section.getId() == LocalWatchLater.SECTION_ID) {
            return true;
        }
        Object data = section.getData();
        return data instanceof Video && WATCH_LATER_PLAYLIST_ID.equals(((Video) data).playlistId);
    }

    /**
     * The section the tab shows: the phone's own Watch later when it is listed (signed out only),
     * else the account's pinned one. A pinned "WL" left over from a signed-in session can't load
     * signed out, so it never wins over the local one.
     */
    public static BrowseSection pick(List<BrowseSection> sections) {
        BrowseSection pinned = null;
        for (BrowseSection section : sections) {
            if (section.getId() == LocalWatchLater.SECTION_ID) {
                return section;
            }
            if (pinned == null && isWatchLaterSection(section)) {
                pinned = section;
            }
        }
        return pinned;
    }

    private static void pinAccountWatchLater(Context context) {
        BrowsePresenter presenter = BrowsePresenter.instance(context);
        for (Video pinned : SidebarService.instance(context).getPinnedItems()) {
            if (WATCH_LATER_PLAYLIST_ID.equals(pinned.playlistId)) {
                return;
            }
        }

        Video watchLater = new Video();
        watchLater.playlistId = WATCH_LATER_PLAYLIST_ID;
        watchLater.title = context.getString(R.string.header_watch_later);

        presenter.pinItem(watchLater);
        if (!presenter.isItemPinned(watchLater)) {
            // No browse screen attached (pinItem needs one): store the pin, it loads next start.
            SidebarService.instance(context).addPinnedItem(watchLater);
        }
    }
}
