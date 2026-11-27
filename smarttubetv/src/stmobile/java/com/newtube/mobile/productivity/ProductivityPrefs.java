package com.newtube.mobile.productivity;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Productivity mode state (Settings > Productivity): the on/off switch, the topics that stay
 * visible while it is on, and the daily time limit with today's usage.
 */
public final class ProductivityPrefs {
    private static final String PREFS_NAME = "retube_productivity_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_CATEGORIES = "categories";
    private static final String KEY_KEYWORDS = "keywords";
    private static final String KEY_CHANNELS = "channels";
    private static final String KEY_CATEGORY_LOOKUP = "category_lookup";
    private static final String KEY_LIMIT_MIN = "limit_minutes";
    private static final String KEY_COUNT_MODE = "count_mode";
    private static final String KEY_ON_LIMIT = "on_limit";
    private static final String KEY_HOLD_TO_EXIT = "hold_to_exit";
    private static final String KEY_USED_MS = "used_ms";
    private static final String KEY_USED_DAY = "used_day";

    /** The daily limit counts every minute in the app. */
    public static final int COUNT_ALL = 0;
    /** The daily limit ignores time spent in productivity mode. */
    public static final int COUNT_OUTSIDE = 1;

    /** At the limit: close the app. */
    public static final int ON_LIMIT_CLOSE = 0;
    /** At the limit: only productivity mode stays available until tomorrow. */
    public static final int ON_LIMIT_PRODUCTIVITY_ONLY = 1;

    public interface Listener {
        void onProductivityChanged();
    }

    private static final java.util.List<Listener> sListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private ProductivityPrefs() {
    }

    public static void addListener(Listener listener) {
        sListeners.add(listener);
    }

    public static void removeListener(Listener listener) {
        sListeners.remove(listener);
    }

    private static void notifyChanged() {
        for (Listener listener : sListeners) {
            listener.onProductivityChanged();
        }
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        notifyChanged();
    }

    public static Set<String> getCategories(Context context) {
        return new LinkedHashSet<>(prefs(context).getStringSet(KEY_CATEGORIES, defaultCategories()));
    }

    public static void setCategories(Context context, Set<String> categories) {
        prefs(context).edit().putStringSet(KEY_CATEGORIES, new LinkedHashSet<>(categories)).apply();
        notifyChanged();
    }

    public static Set<String> getKeywords(Context context) {
        return new LinkedHashSet<>(prefs(context).getStringSet(KEY_KEYWORDS, new LinkedHashSet<>()));
    }

    public static void setKeywords(Context context, Set<String> keywords) {
        prefs(context).edit().putStringSet(KEY_KEYWORDS, new LinkedHashSet<>(keywords)).apply();
        notifyChanged();
    }

    /** Channel ids that always show, whatever their videos are about. */
    public static Set<String> getChannels(Context context) {
        return new LinkedHashSet<>(prefs(context).getStringSet(KEY_CHANNELS, new LinkedHashSet<>()));
    }

    public static void setChannels(Context context, Set<String> channelIds) {
        prefs(context).edit().putStringSet(KEY_CHANNELS, new LinkedHashSet<>(channelIds)).apply();
        notifyChanged();
    }

    public static boolean isCategoryMatchEnabled(Context context) {
        return prefs(context).getBoolean(KEY_CATEGORY_LOOKUP, true);
    }

    public static void setCategoryMatchEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_CATEGORY_LOOKUP, enabled).apply();
        notifyChanged();
    }

    /** 0 = no limit. */
    public static int getLimitMinutes(Context context) {
        return prefs(context).getInt(KEY_LIMIT_MIN, 0);
    }

    public static void setLimitMinutes(Context context, int minutes) {
        prefs(context).edit().putInt(KEY_LIMIT_MIN, Math.max(0, minutes)).apply();
        notifyChanged();
    }

    public static int getCountMode(Context context) {
        return prefs(context).getInt(KEY_COUNT_MODE, COUNT_OUTSIDE);
    }

    public static void setCountMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_COUNT_MODE, mode).apply();
        notifyChanged();
    }

    public static int getOnLimit(Context context) {
        return prefs(context).getInt(KEY_ON_LIMIT, ON_LIMIT_PRODUCTIVITY_ONLY);
    }

    public static void setOnLimit(Context context, int action) {
        prefs(context).edit().putInt(KEY_ON_LIMIT, action).apply();
        notifyChanged();
    }

    public static boolean isHoldToExit(Context context) {
        return prefs(context).getBoolean(KEY_HOLD_TO_EXIT, false);
    }

    public static void setHoldToExit(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_HOLD_TO_EXIT, on).apply();
    }

    /** Milliseconds counted toward the limit today (0 once the day has rolled over). */
    public static long getUsedTodayMs(Context context) {
        SharedPreferences p = prefs(context);
        return p.getInt(KEY_USED_DAY, -1) == today() ? p.getLong(KEY_USED_MS, 0L) : 0L;
    }

    public static void addUsedMs(Context context, long ms) {
        SharedPreferences p = prefs(context);
        long used = p.getInt(KEY_USED_DAY, -1) == today() ? p.getLong(KEY_USED_MS, 0L) : 0L;
        p.edit().putInt(KEY_USED_DAY, today()).putLong(KEY_USED_MS, used + ms).apply();
    }

    /** Whether today's counted time has reached the limit. */
    public static boolean isLimitReached(Context context) {
        int limit = getLimitMinutes(context);
        return limit > 0 && getUsedTodayMs(context) >= limit * 60_000L;
    }

    /** Year * 1000 + day of year, in the device's time zone: the limit resets at local midnight. */
    static int today() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR);
    }

    /** YouTube's own category names (as the /player microformat reports them). */
    public static final String[] ALL_CATEGORIES = {
            "Education", "Science & Technology", "Howto & Style", "News & Politics",
            "Nonprofits & Activism", "Music", "Gaming", "Sports", "Entertainment", "Comedy",
            "Film & Animation", "People & Blogs", "Travel & Events", "Autos & Vehicles", "Pets & Animals"
    };

    private static Set<String> defaultCategories() {
        return new LinkedHashSet<>(Arrays.asList("Education", "Science & Technology"));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
