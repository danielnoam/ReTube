package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import java.io.File;
import java.io.IOException;

/**
 * RETUBE(privacy): a restored install starts with a NEW anonymous YouTube identity.
 *
 * <p>The visitor cookie, cached visitorData and device id live in {@code global_prefs/media_service_data}
 * next to settings the user wants back (hidden content), so the file stays in Android's backup and
 * the in-app zip. Restoring it would carry the old signed-out identity to the new device and let
 * YouTube join the two, so the identity part is dropped on the first start after a restore.</p>
 *
 * <p>Restores are told apart by a marker in {@code noBackupFilesDir}, which no backup carries:
 * no marker on an install that was never updated means Android restored the data at install time,
 * and the in-app restore writes {@link #PENDING} before it kills the process. An existing install
 * that updates to this version only gets the marker.</p>
 */
public final class RestoredIdentity {
    private static final String TAG = RestoredIdentity.class.getSimpleName();
    private static final String MARKER = "identity_owned";
    private static final String PENDING = "identity_reset_pending";
    /** Player-route caches that hold visitors (bot walls, embed and kept playback identities). */
    private static final String PLAYER_ROUTES_PREFS = "newtube_player_routes";

    private RestoredIdentity() {
    }

    /** Call after GlobalPreferences is initialised and before anything uses the media service. */
    public static void resetIfRestored(Context context) {
        File dir = context.getNoBackupFilesDir();
        File marker = new File(dir, MARKER);
        File pending = new File(dir, PENDING);

        boolean reset = pending.exists() || (!marker.exists() && isNeverUpdatedInstall(context));

        if (reset) {
            Log.d(TAG, "Restored data: starting with a new anonymous identity");
            MediaServiceData data = MediaServiceData.instance();
            data.setVisitorCookie(null);
            data.setAppInfo(null);
            data.setFailedAppInfo(null);
            data.setPoToken(null);
            data.setDeviceId(null);
            context.getSharedPreferences(PLAYER_ROUTES_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        }

        touch(marker);
        pending.delete();
    }

    /** The in-app restore calls this just before it kills the process. */
    public static void markRestored(Context context) {
        touch(new File(context.getNoBackupFilesDir(), PENDING));
    }

    private static boolean isNeverUpdatedInstall(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.firstInstallTime == info.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static void touch(File file) {
        try {
            if (!file.exists() && !file.createNewFile()) {
                Log.e(TAG, "Can't create " + file);
            }
        } catch (IOException e) {
            Log.e(TAG, "Can't create " + file + ": " + e.getMessage());
        }
    }
}
