package com.newtube.mobile.downloads;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.utils.IntentExtractor;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.MetaSeparator;

import io.reactivex.rxjava3.disposables.Disposable;

/**
 * "Download" in Android's share sheet: the quality picker over the app the link was shared from,
 * without opening the player, and back to that app once a row is picked. The download itself
 * runs in {@link MobileDownloadService} like any other.
 */
public final class ShareDownloadActivity extends AppCompatActivity {
    @Nullable private Disposable mFetch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            // Rotation: the dialog went with the old window; the share is simply over.
            finish();
            return;
        }
        String videoId = videoIdOf(getIntent());
        if (videoId == null) {
            toastAndFinish(getString(R.string.mobile_download_unavailable));
            return;
        }
        Toast.makeText(this, R.string.mobile_download_preparing, Toast.LENGTH_SHORT).show();
        mFetch = DownloadPicker.resolve(getApplicationContext(), Video.from(videoId), (choice, reason) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (choice == null) {
                toastAndFinish(reason != null ? reason : getString(R.string.mobile_download_unavailable));
                return;
            }
            showChoice(choice);
        });
    }

    private void showChoice(DownloadPicker.Choice choice) {
        CharSequence[] rows = new CharSequence[choice.options.size()];
        for (int i = 0; i < rows.length; i++) {
            DownloadOption option = choice.options.get(i);
            rows[i] = choice.label(option) + MetaSeparator.DOT + choice.description(option);
        }
        new MaterialAlertDialogBuilder(this, R.style.MobileAlertDialog)
                .setTitle(choice.title())
                .setItems(rows, (dialog, which) -> {
                    DownloadOption option = choice.options.get(which);
                    if (choice.alreadyHere(option)) {
                        toastAndFinish(getString(R.string.mobile_download_already_here));
                        return;
                    }
                    choice.enqueue(option);
                    String size = DownloadOptions.formatBytes(option.totalBytes);
                    toastAndFinish(size.isEmpty() ? getString(R.string.mobile_download_started)
                            : getString(R.string.mobile_download_started_size, size));
                })
                .setOnDismissListener(dialog -> finish())
                .show();
    }

    private void toastAndFinish(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        RxHelper.disposeActions(mFetch);
        super.onDestroy();
    }

    @Nullable
    private static String videoIdOf(@Nullable Intent intent) {
        if (intent == null) {
            return null;
        }
        String link = DownloadOptions.firstLink(intent.getStringExtra(Intent.EXTRA_TEXT));
        if (link == null && intent.getData() != null) {
            link = intent.getData().toString();
        }
        if (link == null) {
            return null;
        }
        try {
            return IntentExtractor.extractVideoId(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
