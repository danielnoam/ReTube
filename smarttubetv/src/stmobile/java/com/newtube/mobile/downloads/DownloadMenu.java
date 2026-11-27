package com.newtube.mobile.downloads;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The context menu of a download: what the long-press on a Downloads card and the watch page's
 * "Downloaded" pill open. Same app dialog as every other card menu, so it looks native.
 */
public final class DownloadMenu {
    private DownloadMenu() {
    }

    public static void show(Context context, @Nullable DownloadItem item) {
        if (context == null || item == null) {
            return;
        }

        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        DownloadStorage storage = new DownloadStorage(context);

        if (item.isDone()) {
            dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.mobile_download_play),
                    optionItem -> {
                        dialog.closeDialog();
                        play(context, item);
                    }));
            dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.mobile_watch_share),
                    optionItem -> {
                        dialog.closeDialog();
                        share(context, item);
                    }));
        }
        if (item.isFailed()) {
            dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.mobile_download_retry),
                    optionItem -> {
                        dialog.closeDialog();
                        MobileDownloadService.retry(context, item);
                    }));
        }
        if (item.isActive()) {
            dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.mobile_download_cancel),
                    optionItem -> {
                        dialog.closeDialog();
                        MobileDownloadService.cancel(context, item);
                    }));
        } else {
            dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.mobile_download_delete),
                    optionItem -> {
                        dialog.closeDialog();
                        delete(context, item);
                    }));
        }

        String subtitle = DownloadProgressText.of(context, item);
        dialog.showDialog(item.title + "\n" + subtitle);
    }

    /**
     * Opens the file in the app's own player with the other finished downloads queued around it,
     * in the Downloads tab's order: the ones below play next, the ones above are Previous. The
     * queue plays straight from the files, so it keeps going offline.
     */
    public static void play(Context context, DownloadItem item) {
        if (!item.isDone() || item.outputUri == null) {
            return;
        }
        DownloadStorage storage = new DownloadStorage(context);
        if (!storage.exists(item.outputUri)) {
            com.newtube.mobile.ui.common.MobileSnackbar.show(context, R.string.mobile_download_file_missing);
            return;
        }

        DownloadsBridge bridge = DownloadsBridge.instance(context);
        List<Video> order = new ArrayList<>();
        Video first = null;
        for (DownloadItem download : DownloadRegistry.instance(context).itemsNewestFirst()) {
            boolean isStart = download.id.equals(item.id);
            if (!isStart && (!download.isDone() || !storage.exists(download.outputUri))) {
                continue;
            }
            Video entry = bridge.queueEntryFor(download);
            entry.fromQueue = true;
            order.add(entry);
            if (isStart) {
                first = entry;
            }
        }
        if (first == null) { // deleted from the registry between the tap and now
            first = bridge.queueEntryFor(item);
            first.fromQueue = true;
            order.clear();
            order.add(first);
        }

        Playlist queue = Playlist.instance();
        queue.clear();
        queue.addAll(order);
        queue.setCurrent(first);
        PlaybackPresenter.instance(context).openVideo(first);
    }

    public static void share(Context context, DownloadItem item) {
        Uri uri = new DownloadStorage(context).shareUri(item.outputUri);
        if (uri == null) {
            com.newtube.mobile.ui.common.MobileSnackbar.show(context, R.string.mobile_download_file_missing);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType((item.isAudioOnly() ? "audio/" : "video/") + (item.webm ? "webm" : "mp4"))
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, item.title)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(intent, context.getString(R.string.mobile_watch_share));
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(chooser);
    }

    public static void delete(Context context, DownloadItem item) {
        DownloadStorage storage = new DownloadStorage(context);
        storage.delete(item.outputUri);
        if (item.thumbPath != null) {
            //noinspection ResultOfMethodCallIgnored
            new File(item.thumbPath).delete();
        }
        File parts = DownloadRegistry.partsDir(context);
        //noinspection ResultOfMethodCallIgnored
        new File(parts, item.id + ".video").delete();
        //noinspection ResultOfMethodCallIgnored
        new File(parts, item.id + ".audio").delete();
        //noinspection ResultOfMethodCallIgnored
        DownloadOptions.audioIdFile(new File(parts, item.id + ".audio")).delete();
        DownloadRegistry.instance(context).remove(item);
    }
}
