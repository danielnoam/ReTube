package com.newtube.mobile.downloads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class SponsorCutsTest {
    private static final long S = 1_000_000;

    @Test
    public void overlappingSegmentsBecomeOneCut() {
        SponsorCuts cuts = SponsorCuts.of(Arrays.asList(new long[]{10 * S, 20 * S}, new long[]{15 * S, 25 * S},
                new long[]{40 * S, 50 * S}));

        assertEquals(2, cuts.count());
        assertEquals(25 * S, cuts.totalUs());
    }

    @Test
    public void emptyAndInvertedSegmentsAreIgnored() {
        SponsorCuts cuts = SponsorCuts.of(Arrays.asList(new long[]{5 * S, 5 * S}, new long[]{9 * S, 3 * S}));

        assertTrue(cuts.isEmpty());
        assertEquals(0, SponsorCuts.of(Collections.emptyList()).totalUs());
    }

    @Test
    public void keptSamplesMoveBackByEveryEarlierCut() {
        SponsorCuts cuts = SponsorCuts.of(Arrays.asList(new long[]{10 * S, 20 * S}, new long[]{30 * S, 35 * S}));

        assertEquals(0, cuts.shiftAt(9 * S));
        assertTrue(cuts.contains(10 * S));
        assertTrue(cuts.contains(20 * S - 1));
        assertFalse(cuts.contains(20 * S));
        assertEquals(10 * S, cuts.shiftAt(20 * S));
        assertEquals(10 * S, cuts.shiftAt(29 * S));
        assertEquals(15 * S, cuts.shiftAt(36 * S));
    }

    /** Keyframes every 2 s: a 13.5-24.2 s segment is cut from 14 s to 24 s, never into content. */
    @Test
    public void videoCutsShrinkToTheKeyframesInsideTheSegment() {
        long[] keyframes = new long[30];
        for (int i = 0; i < keyframes.length; i++) {
            keyframes[i] = i * 2 * S;
        }
        SponsorCuts cuts = SponsorCuts.of(Collections.singletonList(new long[]{13_500_000, 24_200_000}))
                .snappedTo(keyframes);

        assertEquals(1, cuts.count());
        assertFalse(cuts.contains(13_900_000));
        assertTrue(cuts.contains(14 * S));
        assertTrue(cuts.contains(24 * S - 1));
        assertFalse(cuts.contains(24 * S));
        assertEquals(10 * S, cuts.totalUs());
    }

    @Test
    public void aSegmentShorterThanAKeyframeIntervalIsNotCut() {
        long[] keyframes = {0, 5 * S, 10 * S, 15 * S};
        SponsorCuts cuts = SponsorCuts.of(Collections.singletonList(new long[]{6 * S, 9 * S})).snappedTo(keyframes);

        assertTrue(cuts.isEmpty());
    }

    @Test
    public void aSegmentOnExactKeyframesIsCutExactly() {
        long[] keyframes = {0, 5 * S, 10 * S, 15 * S};
        SponsorCuts cuts = SponsorCuts.of(Collections.singletonList(new long[]{5 * S, 15 * S})).snappedTo(keyframes);

        assertEquals(10 * S, cuts.totalUs());
        assertTrue(cuts.contains(5 * S));
        assertFalse(cuts.contains(15 * S));
    }
}
