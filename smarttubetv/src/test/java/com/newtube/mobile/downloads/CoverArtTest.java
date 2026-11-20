package com.newtube.mobile.downloads;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class CoverArtTest {
    @Test
    public void aWideThumbnailIsKeptWholeForVideo() {
        assertArrayEquals(new int[]{0, 0, 1280, 720}, CoverArt.cropRect(1280, 720, false));
    }

    @Test
    public void aWideThumbnailIsCenterCroppedForAudio() {
        assertArrayEquals(new int[]{280, 0, 720, 720}, CoverArt.cropRect(1280, 720, true));
    }

    /** hqdefault: 480x360 with the 480x270 frame letterboxed in the middle. */
    @Test
    public void letterboxBarsAreCutOff() {
        assertArrayEquals(new int[]{0, 45, 480, 270}, CoverArt.cropRect(480, 360, false));
        assertArrayEquals(new int[]{105, 45, 270, 270}, CoverArt.cropRect(480, 360, true));
    }
}
