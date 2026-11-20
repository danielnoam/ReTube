package com.newtube.mobile.downloads;

import android.content.Context;
import android.content.SharedPreferences;

/** Download behaviour switches (Settings > General > Downloads). */
public final class DownloadPrefs {
    private static final String PREFS_NAME = "retube_download_prefs";
    private static final String KEY_CUT_SPONSORS = "cut_sponsor_segments";

    private DownloadPrefs() {
    }

    /** Leave out the segments SponsorBlock skips in the player. Off by default. */
    public static boolean isCutSponsorsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_CUT_SPONSORS, false);
    }

    public static void setCutSponsorsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_CUT_SPONSORS, enabled).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
