package com.newtube.mobile.productivity;

import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.misc.VideoCategoryCache;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * While productivity mode is on, a list keeps a video only when it is about one of the user's
 * topics: its channel is always allowed, its title or channel name starts a word with one of the
 * keywords, or its YouTube category (learned by {@link VideoCategoryCache} once a video has played) is
 * one of the chosen categories. Channels, playlists and other non-video cards are left alone.
 * With the mode off nothing is dropped.
 */
public final class ProductivityFilter {
    private static Context sContext;
    private static boolean sEnabled;
    private static Set<String> sKeywords;
    private static Set<String> sCategorySet;
    private static Set<String> sChannels;
    private static boolean sCategoryMatch;

    private ProductivityFilter() {
    }

    /** Call once at startup; keeps a snapshot so list code stays off SharedPreferences. */
    public static void init(Context context) {
        sContext = context.getApplicationContext();
        refresh();
        ProductivityPrefs.addListener(ProductivityFilter::refresh);
    }

    private static void refresh() {
        Context c = sContext;
        if (c == null) {
            return;
        }
        sEnabled = ProductivityPrefs.isEnabled(c);
        Set<String> keywords = new java.util.LinkedHashSet<>();
        for (String keyword : ProductivityPrefs.getKeywords(c)) {
            keywords.add(normalize(keyword));
        }
        sKeywords = keywords;
        sCategorySet = ProductivityPrefs.getCategories(c);
        sChannels = ProductivityPrefs.getChannels(c);
        sCategoryMatch = ProductivityPrefs.isCategoryMatchEnabled(c);
    }

    /** Test seam: sets the snapshot directly instead of reading preferences. */
    static void configure(boolean enabled, Set<String> keywords, Set<String> categories,
                          Set<String> channels, boolean categoryMatch) {
        Set<String> normalized = new java.util.LinkedHashSet<>();
        for (String keyword : keywords) {
            normalized.add(normalize(keyword));
        }
        sEnabled = enabled;
        sKeywords = normalized;
        sCategorySet = categories;
        sChannels = channels;
        sCategoryMatch = categoryMatch;
    }

    public static boolean isActive() {
        return sEnabled;
    }

    /** Whether the card may be listed right now. */
    public static boolean isAllowed(@Nullable Video video) {
        if (!sEnabled || video == null) {
            return true;
        }
        if (video.videoId == null) {
            return true; // channel, playlist and section cards are not videos
        }
        if (video.channelId != null && sChannels.contains(video.channelId)) {
            return true;
        }
        if (matchesKeyword(video.title) || matchesKeyword(video.author)
                || matchesKeyword(video.unlocalizedTitle)) {
            return true;
        }
        if (sCategoryMatch) {
            String category = VideoCategoryCache.get(video.videoId);
            if (category == null) {
                category = video.category;
            }
            return category != null && sCategorySet.contains(category);
        }
        return false;
    }

    /** {@code videos} without what productivity mode hides (the same list when it hides nothing). */
    public static List<Video> filter(List<Video> videos) {
        if (!sEnabled || videos == null) {
            return videos;
        }
        List<Video> result = new ArrayList<>(videos.size());
        for (Video video : videos) {
            if (isAllowed(video)) {
                result.add(video);
            }
        }
        return result;
    }

    /** Whether {@code text} has a word starting with one of the keywords (case- and accent-blind). */
    private static boolean matchesKeyword(@Nullable String text) {
        if (text == null || sKeywords == null || sKeywords.isEmpty()) {
            return false;
        }
        String haystack = " " + normalize(text);
        for (String keyword : sKeywords) {
            if (!keyword.isEmpty() && haystack.contains(" " + keyword)) {
                return true;
            }
        }
        return false;
    }

    static String normalize(String s) {
        String decomposed = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").trim();
    }
}
