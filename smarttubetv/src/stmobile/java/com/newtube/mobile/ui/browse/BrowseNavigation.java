package com.newtube.mobile.ui.browse;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.misc.VideoDownloads;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The same enabled tabs, in the user's {@link TabOrder}, on Browse and Search; other sections
 * stay under You. No backfill from the other delivered sections.
 */
public final class BrowseNavigation {
    /** BottomNavigationView item ids must be non-zero; BrowseSection ids start at 0. */
    public static final int ITEM_ID_OFFSET = 1_000_000;
    /** The synthetic "You" tab, far above {@link #ITEM_ID_OFFSET} + any real section id. */
    public static final int YOU_ITEM_ID = 2_000_000;
    /** BottomNavigationView hard-caps at this many items. */
    private static final int MAX_NAV_ITEMS = 5;

    private BrowseNavigation() {
    }

    public static List<BrowseSection> sections(Context context, List<BrowseSection> sections) {
        List<BrowseSection> enabled = new ArrayList<>();
        for (BrowseSection section : sections) {
            if (section.isEnabled() && section.getId() != MediaGroup.TYPE_SETTINGS) {
                enabled.add(section);
            }
        }

        List<BrowseSection> chosen = new ArrayList<>();
        // The You tab always takes one of the bar's slots.
        int maxSections = MAX_NAV_ITEMS - 1;
        boolean watchLaterTab = WatchLaterTab.isOn(context);

        // Settings > Tabs > Order; when the bar is full, the last tab moves to You.
        for (int id : TabOrder.get(context)) {
            if (chosen.size() >= maxSections) {
                break;
            }
            BrowseSection match;
            if (id == TabOrder.WATCH_LATER) {
                match = watchLaterTab ? WatchLaterTab.pick(enabled) : null;
            } else {
                match = find(enabled, chosen, id, context.getString(TabOrder.titleRes(id)));
            }
            if (match != null && !chosen.contains(match)) {
                chosen.add(match);
            }
        }
        return chosen;
    }

    /** Matches primarily by section id; falls back to a title-string match. */
    private static BrowseSection find(List<BrowseSection> candidates, List<BrowseSection> chosen,
                                      int id, String fallbackTitle) {
        for (BrowseSection section : candidates) {
            if (!chosen.contains(section)
                    && (section.getId() == id || fallbackTitle.equals(section.getTitle()))) {
                return section;
            }
        }
        return null;
    }

    /** The outline/filled icon pair for a curated tab, else the section's stock icon. */
    public static int iconFor(BrowseSection section) {
        if (WatchLaterTab.isWatchLaterSection(section)) {
            return R.drawable.ic_nav_watch_later;
        }
        switch (section.getId()) {
            case MediaGroup.TYPE_HOME:
                return R.drawable.ic_nav_home;
            case MediaGroup.TYPE_SUBSCRIPTIONS:
                return R.drawable.ic_nav_subscriptions;
            case MediaGroup.TYPE_HISTORY:
                return R.drawable.ic_nav_history;
            case VideoDownloads.SECTION_ID:
                return R.drawable.ic_nav_downloads;
            default:
                return Math.max(section.getResId(), 0);
        }
    }
}
