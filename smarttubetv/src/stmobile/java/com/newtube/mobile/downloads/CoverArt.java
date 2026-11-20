package com.newtube.mobile.downloads;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * The picture embedded in a finished download: the largest YouTube thumbnail, with the black
 * letterbox bars of the 4:3 variants cut off, and center-cropped to a square for audio so music
 * players don't show a squashed or barred cover.
 */
final class CoverArt {
    private static final int MAX_SIDE = 1280;

    private CoverArt() {
    }

    static String maxResUrl(String videoId) {
        return "https://i.ytimg.com/vi/" + videoId + "/maxresdefault.jpg";
    }

    /** JPEG bytes, or null when no picture could be decoded. */
    @Nullable
    static byte[] encode(@Nullable File picture, boolean square) {
        if (picture == null || !picture.exists()) {
            return null;
        }
        Bitmap source = BitmapFactory.decodeFile(picture.getAbsolutePath());
        if (source == null) {
            return null;
        }
        Bitmap cropped = null;
        Bitmap scaled = null;
        try {
            int[] rect = cropRect(source.getWidth(), source.getHeight(), square);
            cropped = Bitmap.createBitmap(source, rect[0], rect[1], rect[2], rect[3]);
            int longest = Math.max(cropped.getWidth(), cropped.getHeight());
            scaled = cropped;
            if (longest > MAX_SIDE) {
                float ratio = (float) MAX_SIDE / longest;
                scaled = Bitmap.createScaledBitmap(cropped, Math.round(cropped.getWidth() * ratio),
                        Math.round(cropped.getHeight() * ratio), true);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)) {
                return null;
            }
            return out.toByteArray();
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        } finally {
            recycle(scaled, source);
            recycle(cropped, source);
            source.recycle();
        }
    }

    /**
     * {x, y, width, height}. YouTube's hq/sd thumbnails are 4:3 with the 16:9 frame letterboxed
     * in the middle; the frame is what's kept.
     */
    static int[] cropRect(int width, int height, boolean square) {
        int x = 0;
        int y = 0;
        int w = width;
        int h = height;
        if (width * 3 == height * 4) {
            h = width * 9 / 16;
            y = (height - h) / 2;
        }
        if (square) {
            int side = Math.min(w, h);
            x += (w - side) / 2;
            y += (h - side) / 2;
            w = side;
            h = side;
        }
        return new int[]{x, y, w, h};
    }

    private static void recycle(@Nullable Bitmap bitmap, Bitmap source) {
        if (bitmap != null && bitmap != source && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
