package com.newtube.mobile.downloads;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * The folders downloads go to (Settings > General > Downloads). Unset means the default:
 * {@code Movies/ReTube} and {@code Music/ReTube} through MediaStore. A chosen folder is a
 * Storage Access Framework tree the app holds a persistable grant for; it needs Android 8, where
 * MediaMuxer can write to a file descriptor. A folder whose grant is gone (deleted, or a restored
 * backup on another phone) counts as unset.
 */
public final class DownloadFolders {
    private static final String PREFS_NAME = "retube_download_folders";
    private static final String KEY_VIDEO = "video_tree";
    private static final String KEY_AUDIO = "audio_tree";
    private static final int GRANT = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    private DownloadFolders() {
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    /** The chosen folder, or null for the default. */
    @Nullable
    public static Uri get(@NonNull Context context, boolean audio) {
        if (!isSupported()) {
            return null;
        }
        String value = prefs(context).getString(key(audio), null);
        if (value == null) {
            return null;
        }
        Uri tree = Uri.parse(value);
        return hasGrant(context, tree) ? tree : null;
    }

    public static void set(@NonNull Context context, boolean audio, @NonNull Uri tree) {
        ContentResolver resolver = context.getContentResolver();
        try {
            resolver.takePersistableUriPermission(tree, GRANT);
        } catch (SecurityException e) {
            return;
        }
        Uri old = get(context, audio);
        prefs(context).edit().putString(key(audio), tree.toString()).apply();
        if (old != null && !old.equals(tree)) {
            releaseIfUnused(context, old);
        }
    }

    public static boolean hasCustom(@NonNull Context context) {
        return get(context, false) != null || get(context, true) != null;
    }

    public static void reset(@NonNull Context context) {
        Uri video = get(context, false);
        Uri audio = get(context, true);
        prefs(context).edit().remove(KEY_VIDEO).remove(KEY_AUDIO).apply();
        if (video != null) {
            releaseIfUnused(context, video);
        }
        if (audio != null) {
            releaseIfUnused(context, audio);
        }
    }

    /** What the settings row shows: "Movies/ReTube (default)", or the chosen folder's path. */
    @NonNull
    public static CharSequence label(@NonNull Context context, boolean audio) {
        Uri tree = get(context, audio);
        if (tree == null) {
            return context.getString(R.string.mobile_settings_download_folder_default,
                    (audio ? "Music/" : "Movies/") + DownloadStorage.FOLDER);
        }
        String docId = DocumentsContract.getTreeDocumentId(tree);
        int colon = docId.indexOf(':');
        if (colon < 0) {
            return docId;
        }
        String volume = docId.substring(0, colon);
        String path = docId.substring(colon + 1);
        if ("primary".equalsIgnoreCase(volume)) {
            return path.isEmpty() ? context.getString(R.string.mobile_settings_download_folder_internal) : path;
        }
        return path.isEmpty() ? volume : volume + "/" + path;
    }

    private static boolean hasGrant(Context context, Uri tree) {
        for (UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
            if (permission.getUri().equals(tree) && permission.isWritePermission()) {
                return true;
            }
        }
        return false;
    }

    private static void releaseIfUnused(Context context, Uri tree) {
        String value = tree.toString();
        SharedPreferences prefs = prefs(context);
        if (value.equals(prefs.getString(KEY_VIDEO, null)) || value.equals(prefs.getString(KEY_AUDIO, null))) {
            return;
        }
        try {
            context.getContentResolver().releasePersistableUriPermission(tree, GRANT);
        } catch (SecurityException ignored) {
        }
    }

    private static String key(boolean audio) {
        return audio ? KEY_AUDIO : KEY_VIDEO;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
