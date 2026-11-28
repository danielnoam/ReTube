package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NEWTUBE(productivity): videoId -> YouTube category ("Education"...), learned from the /player
 * microformat of videos that were played. Productivity mode matches list cards against it, since
 * feed items carry no category. Memory only and bounded: a cleared map is a few lost matches.
 */
public final class VideoCategoryCache {
    private static final int MAX = 2000;
    private static final Map<String, String> CATEGORIES = new ConcurrentHashMap<>();

    private VideoCategoryCache() {
    }

    public static void remember(String videoId, String category) {
        if (videoId == null || category == null || category.isEmpty()) {
            return;
        }
        if (CATEGORIES.size() >= MAX) {
            CATEGORIES.clear();
        }
        CATEGORIES.put(videoId, category);
    }

    public static String get(String videoId) {
        return videoId != null ? CATEGORIES.get(videoId) : null;
    }

    /**
     * Records the category of a loaded format info. {@code getCategory()} comes with the
     * MediaServiceCore patch in docs/productivity; read by reflection so the app builds (and just
     * learns nothing) against a MediaServiceCore without it.
     */
    public static void rememberFrom(String videoId, MediaItemFormatInfo info) {
        if (info == null) {
            return;
        }
        try {
            Object category = info.getClass().getMethod("getCategory").invoke(info);
            if (category instanceof String) {
                remember(videoId, (String) category);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // no accessor in this MediaServiceCore
        }
    }
}
