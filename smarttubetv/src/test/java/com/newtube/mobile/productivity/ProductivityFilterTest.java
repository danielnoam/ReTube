package com.newtube.mobile.productivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.misc.VideoCategoryCache;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Productivity mode keeps a video when a keyword, an allowed channel or a known category says so. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = android.app.Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ProductivityFilterTest {
    @After
    public void reset() {
        ProductivityFilter.configure(false, set(), set(), set(), true);
    }

    @Test
    public void modeOffKeepsEverythingAndTheSameList() {
        ProductivityFilter.configure(false, set("python"), set(), set(), true);
        List<Video> list = Arrays.asList(video("a", "Cute cats", null, null), video("b", "Python intro", null, null));

        assertSame(list, ProductivityFilter.filter(list));
    }

    @Test
    public void keywordMatchesWordStartsCaseAndAccentBlind() {
        ProductivityFilter.configure(true, set("Café", "calc"), set(), set(), false);

        assertTrue(ProductivityFilter.isAllowed(video("a", "My CAFE tour", null, null)));
        assertTrue(ProductivityFilter.isAllowed(video("b", "Calculus in 20 minutes", null, null)));
        assertFalse(ProductivityFilter.isAllowed(video("c", "Decalcify your kettle", null, null)));
        assertFalse(ProductivityFilter.isAllowed(video("d", "Cute cats", null, null)));
    }

    @Test
    public void keywordAlsoMatchesTheChannelName() {
        ProductivityFilter.configure(true, set("khan"), set(), set(), false);

        assertTrue(ProductivityFilter.isAllowed(video("a", "Fractions", "Khan Academy", null)));
    }

    @Test
    public void allowedChannelAlwaysShows() {
        ProductivityFilter.configure(true, set(), set(), set("UC1"), false);

        assertTrue(ProductivityFilter.isAllowed(video("a", "Anything", "Anyone", "UC1")));
        assertFalse(ProductivityFilter.isAllowed(video("b", "Anything", "Anyone", "UC2")));
    }

    @Test
    public void categoryComesFromTheCacheAndCanBeSwitchedOff() {
        VideoCategoryCache.remember("cat1", "Education");
        VideoCategoryCache.remember("cat2", "Gaming");

        ProductivityFilter.configure(true, set(), set("Education"), set(), true);
        assertTrue(ProductivityFilter.isAllowed(video("cat1", "Untitled", null, null)));
        assertFalse(ProductivityFilter.isAllowed(video("cat2", "Untitled", null, null)));
        assertFalse(ProductivityFilter.isAllowed(video("unknown", "Untitled", null, null)));

        ProductivityFilter.configure(true, set(), set("Education"), set(), false);
        assertFalse(ProductivityFilter.isAllowed(video("cat1", "Untitled", null, null)));
    }

    @Test
    public void nonVideoCardsAreNeverHidden() {
        ProductivityFilter.configure(true, set(), set(), set(), true);
        Video channel = new Video();
        channel.title = "Some channel";

        assertTrue(ProductivityFilter.isAllowed(channel));
    }

    @Test
    public void filterKeepsOrder() {
        ProductivityFilter.configure(true, set("math"), set(), set(), false);
        Video a = video("a", "Math 1", null, null);
        Video b = video("b", "Cats", null, null);
        Video c = video("c", "Math 2", null, null);

        assertEquals(Arrays.asList(a, c), ProductivityFilter.filter(new ArrayList<>(Arrays.asList(a, b, c))));
    }

    private static Video video(String id, String title, String author, String channelId) {
        Video video = new Video();
        video.videoId = id;
        video.title = title;
        video.author = author;
        video.channelId = channelId;
        return video;
    }

    private static Set<String> set(String... values) {
        return values.length == 0 ? Collections.emptySet() : new HashSet<>(Arrays.asList(values));
    }
}
