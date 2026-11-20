package com.newtube.mobile.downloads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SharedLinkTest {
    @Test
    public void theLinkIsFoundInsideSharedText() {
        assertEquals("https://youtu.be/dQw4w9WgXcQ?si=abc",
                DownloadOptions.firstLink("Watch this! https://youtu.be/dQw4w9WgXcQ?si=abc\nso good"));
    }

    @Test
    public void aBareLinkIsTakenAsIs() {
        assertEquals("https://www.youtube.com/shorts/dQw4w9WgXcQ",
                DownloadOptions.firstLink("https://www.youtube.com/shorts/dQw4w9WgXcQ"));
    }

    @Test
    public void textWithoutALinkHasNone() {
        assertNull(DownloadOptions.firstLink("no link here"));
        assertNull(DownloadOptions.firstLink(null));
    }
}
