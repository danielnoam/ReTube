package com.newtube.mobile.stats;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The Stats page's numbers (Settings > Stats): watch time, videos, channels and downloads, kept
 * only in this app's private storage. Nothing here is sent anywhere, and the file is left out of
 * Android's cloud backup and device transfer (backup_rules.xml, data_extraction_rules.xml) and of
 * the app's own backup zip (its name matches none of {@code Utils.BACKUP_PREFS}).
 *
 * <p>Nothing is recorded until the person says yes on the Stats page's first open; a no, or the
 * switch turned off later, stops recording and keeps nothing new.</p>
 */
public final class LocalStats {
    private static final String TAG = LocalStats.class.getSimpleName();
    static final String PREFS_NAME = "retube_local_stats";
    private static final String KEY_CONSENT = "consent";
    private static final String KEY_DATA = "data";

    public static final int CONSENT_UNASKED = 0;
    public static final int CONSENT_ON = 1;
    public static final int CONSENT_OFF = 2;

    private static final Object LOCK = new Object();
    @Nullable private static StatsData sData;
    private static int sConsent = -1;

    private LocalStats() {
    }

    public static int getConsent(@NonNull Context context) {
        synchronized (LOCK) {
            if (sConsent < 0) {
                sConsent = prefs(context).getInt(KEY_CONSENT, CONSENT_UNASKED);
            }
            return sConsent;
        }
    }

    public static boolean isRecording(@NonNull Context context) {
        return getConsent(context) == CONSENT_ON;
    }

    public static void setRecording(@NonNull Context context, boolean on) {
        synchronized (LOCK) {
            sConsent = on ? CONSENT_ON : CONSENT_OFF;
            prefs(context).edit().putInt(KEY_CONSENT, sConsent).apply();
        }
    }

    /** A copy, safe to read on any thread while recording goes on. */
    @NonNull
    public static StatsData snapshot(@NonNull Context context) {
        synchronized (LOCK) {
            return data(context).copy();
        }
    }

    /** Played time of one stretch of a video; {@code countVideo} once it has played long enough. */
    static void recordWatch(@NonNull Context context, @Nullable String channelId, @Nullable String channelName,
                            boolean offline, long ms, boolean countVideo) {
        if (!isRecording(context)) {
            return;
        }
        synchronized (LOCK) {
            data(context).addWatch(StatsCalendar.today(), System.currentTimeMillis(), channelId, channelName,
                    offline, ms, countVideo);
            save(context);
        }
    }

    /** A download finished. Called on the download thread. */
    public static void recordDownload(@NonNull Context context, boolean audio) {
        if (!isRecording(context)) {
            return;
        }
        synchronized (LOCK) {
            data(context).addDownload(StatsCalendar.today(), System.currentTimeMillis(), audio);
            save(context);
        }
    }

    public static void clear(@NonNull Context context) {
        synchronized (LOCK) {
            sData = new StatsData();
            prefs(context).edit().remove(KEY_DATA).apply();
        }
    }

    private static StatsData data(Context context) {
        if (sData == null) {
            String raw = prefs(context).getString(KEY_DATA, null);
            StatsData loaded = null;
            if (raw != null) {
                try {
                    loaded = StatsData.fromJson(new JSONObject(raw));
                } catch (JSONException e) {
                    Log.e(TAG, "Unreadable stats, starting over: %s", e.getMessage());
                }
            }
            sData = loaded != null ? loaded : new StatsData();
        }
        return sData;
    }

    private static void save(Context context) {
        try {
            prefs(context).edit().putString(KEY_DATA, data(context).toJson().toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Stats not saved: %s", e.getMessage());
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Tests only. */
    static void resetForTest() {
        synchronized (LOCK) {
            sData = null;
            sConsent = -1;
        }
    }
}
