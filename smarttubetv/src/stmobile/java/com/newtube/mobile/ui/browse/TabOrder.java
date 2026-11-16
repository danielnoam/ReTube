package com.newtube.mobile.ui.browse;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.misc.LocalWatchLater;
import com.liskovsoft.smartyoutubetv2.common.misc.VideoDownloads;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The order of the bottom bar's tabs (Settings > Tabs > Order). {@link #WATCH_LATER} stands for the
 * Watch later tab whichever section backs it (the phone's own or the account's pinned "WL").
 */
public final class TabOrder {
    public static final int WATCH_LATER = LocalWatchLater.SECTION_ID;
    private static final String KEY_ORDER = "tab_order";
    private static final int[] DEFAULT = {
            MediaGroup.TYPE_HOME,
            MediaGroup.TYPE_SUBSCRIPTIONS,
            WATCH_LATER,
            MediaGroup.TYPE_HISTORY,
            VideoDownloads.SECTION_ID,
    };

    private TabOrder() {
    }

    /** Every bottom-bar tab, on or off, in the user's order. */
    public static List<Integer> get(Context context) {
        List<Integer> order = new ArrayList<>();
        String stored = WatchLaterTab.prefs(context).getString(KEY_ORDER, "");
        for (String part : stored.split(",")) {
            try {
                int id = Integer.parseInt(part.trim());
                if (isTab(id) && !order.contains(id)) {
                    order.add(id);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        for (int id : DEFAULT) {
            if (!order.contains(id)) {
                order.add(id);
            }
        }
        return order;
    }

    /** The tabs that are on, in order. */
    public static List<Integer> enabled(Context context) {
        List<Integer> enabled = new ArrayList<>();
        for (int id : get(context)) {
            if (isOn(context, id)) {
                enabled.add(id);
            }
        }
        return enabled;
    }

    /** Puts the tabs that are on in this order; the ones that are off keep theirs, after them. */
    public static void setEnabled(Context context, List<Integer> enabledOrder) {
        StringBuilder value = new StringBuilder();
        for (int id : enabledOrder) {
            append(value, id);
        }
        for (int id : get(context)) {
            if (!enabledOrder.contains(id)) {
                append(value, id);
            }
        }
        WatchLaterTab.prefs(context).edit().putString(KEY_ORDER, value.toString()).apply();
    }

    /** Changes whenever the order or the set of tabs that are on changes. */
    static String signature(Context context) {
        return enabled(context).toString();
    }

    public static boolean isOn(Context context, int id) {
        return id == WATCH_LATER ? WatchLaterTab.isOn(context) : SidebarService.instance(context).isSectionPinned(id);
    }

    public static int titleRes(int id) {
        if (id == MediaGroup.TYPE_HOME) {
            return R.string.header_home;
        }
        if (id == MediaGroup.TYPE_SUBSCRIPTIONS) {
            return R.string.header_subscriptions;
        }
        if (id == MediaGroup.TYPE_HISTORY) {
            return R.string.header_history;
        }
        if (id == VideoDownloads.SECTION_ID) {
            return R.string.header_downloads;
        }
        return R.string.header_watch_later;
    }

    private static boolean isTab(int id) {
        for (int tab : DEFAULT) {
            if (tab == id) {
                return true;
            }
        }
        return false;
    }

    private static void append(StringBuilder value, int id) {
        if (value.length() > 0) {
            value.append(',');
        }
        value.append(id);
    }
}
