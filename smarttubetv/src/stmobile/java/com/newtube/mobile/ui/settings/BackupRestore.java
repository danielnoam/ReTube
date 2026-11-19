package com.newtube.mobile.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.BackupSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.BackupAndRestoreHelper;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.MobileSnackbar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * NEWTUBE(backup): Settings > Backup > Restore on Android 11+. "Back up now" saves a zip to
 * Documents/SmartTubeBackup through MediaStore, but the engine's restore only lists what is in the
 * app's own Android/media folder, which a reinstall (or a new app id) empties and no file manager
 * can write to. So the phone restores from a zip picked with Android's file picker: it is checked to
 * be a backup, unpacked where the engine looks, and the engine's restore list takes it from there.
 */
final class BackupRestore {
    private static final String TAG = BackupRestore.class.getSimpleName();
    private static final String BACKUP_PREFS_ENTRY = "/Backup/shared_prefs/";

    private BackupRestore() {
    }

    /** Android 11+: no shared folder to read backups from (BackupAndRestoreManager's own test). */
    static boolean needsPicker(Context context) {
        return AppInfoHelpers.getRealSdkVersion(context) > 29;
    }

    static void restoreFrom(@NonNull Context context, @NonNull Uri uri) {
        File temp = new File(FileHelpers.getExternalMediaDirectory(context), "restore_" + System.currentTimeMillis() + ".zip");

        if (!copy(context, uri, temp) || !isBackup(temp)) {
            temp.delete();
            MobileSnackbar.show(context, R.string.mobile_settings_restore_not_backup);
            return;
        }

        new BackupAndRestoreHelper(context).unpackTempZip(temp);
        BackupSettingsPresenter.instance(context).showLocalRestoreDialogApi30();
        BackupSettingsPresenter.unhold();
    }

    private static boolean copy(Context context, Uri uri, File target) {
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) {
                return false;
            }
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Can't read the picked backup: %s", e.getMessage());
            return false;
        }
    }

    /** Checked before anything is unpacked: unpacking first deletes the backups already there. */
    private static boolean isBackup(File zip) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().contains(BACKUP_PREFS_ENTRY)) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Picked file isn't a zip: %s", e.getMessage());
        }
        return false;
    }

    /**
     * Any type: a zip shows up as application/zip, application/x-zip-compressed or
     * application/octet-stream depending on the provider. Opens in Documents/SmartTubeBackup, where
     * "Back up now" saves, when the picker honours it.
     */
    static final class PickBackup extends ActivityResultContracts.OpenDocument {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @NonNull String[] input) {
            Intent intent = super.createIntent(context, input);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            if (Build.VERSION.SDK_INT >= 26) {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents",
                        "primary:Documents/" + BackupAndRestoreHelper.BACKUP_FOLDER_NAME));
            }
            return intent;
        }
    }
}
