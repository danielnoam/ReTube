package com.newtube.mobile.downloads;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The time ranges a download leaves out (SponsorBlock segments), in microseconds, and how the
 * samples around them move. Without re-encoding a video can only restart on a keyframe, so a
 * video's cuts are first shrunk to the keyframes inside each segment: a cut never removes real
 * content, and may leave up to one keyframe interval of the segment at either end. Audio frames
 * stand alone, so an audio-only download is cut exactly.
 *
 * <p>Pure Java so the timing rules are unit-tested.
 */
final class SponsorCuts {
    static final SponsorCuts NONE = new SponsorCuts(Collections.emptyList());

    /** Sorted, disjoint {start, end} pairs, end exclusive. */
    private final List<long[]> mCuts;

    private SponsorCuts(List<long[]> cuts) {
        mCuts = cuts;
    }

    /** Overlapping or touching segments become one cut; empty and inverted ones are dropped. */
    static SponsorCuts of(List<long[]> segmentsUs) {
        List<long[]> sorted = new ArrayList<>();
        for (long[] segment : segmentsUs) {
            long start = Math.max(0, segment[0]);
            if (segment[1] > start) {
                sorted.add(new long[]{start, segment[1]});
            }
        }
        Collections.sort(sorted, (a, b) -> Long.compare(a[0], b[0]));
        List<long[]> merged = new ArrayList<>();
        for (long[] cut : sorted) {
            long[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && cut[0] <= last[1]) {
                last[1] = Math.max(last[1], cut[1]);
            } else {
                merged.add(cut);
            }
        }
        return new SponsorCuts(merged);
    }

    /**
     * Each cut shrunk to [first keyframe at or after its start, last keyframe at or before its
     * end); a segment with fewer than two keyframes in it is not cut at all.
     *
     * @param keyframesUs presentation times of the video's sync samples
     */
    SponsorCuts snappedTo(long[] keyframesUs) {
        long[] sorted = keyframesUs.clone();
        Arrays.sort(sorted);
        List<long[]> snapped = new ArrayList<>();
        for (long[] cut : mCuts) {
            int from = Arrays.binarySearch(sorted, cut[0]);
            if (from < 0) {
                from = -from - 1;
            }
            int to = Arrays.binarySearch(sorted, cut[1]);
            if (to < 0) {
                to = -to - 2;
            }
            if (from < sorted.length && to >= 0 && sorted[to] > sorted[from]) {
                snapped.add(new long[]{sorted[from], sorted[to]});
            }
        }
        return new SponsorCuts(snapped);
    }

    boolean isEmpty() {
        return mCuts.isEmpty();
    }

    boolean contains(long timeUs) {
        for (long[] cut : mCuts) {
            if (timeUs < cut[0]) {
                return false;
            }
            if (timeUs < cut[1]) {
                return true;
            }
        }
        return false;
    }

    /** How far a kept sample at this time moves back: the length of every cut before it. */
    long shiftAt(long timeUs) {
        long shift = 0;
        for (long[] cut : mCuts) {
            if (cut[1] > timeUs) {
                break;
            }
            shift += cut[1] - cut[0];
        }
        return shift;
    }

    long totalUs() {
        return shiftAt(Long.MAX_VALUE);
    }

    int count() {
        return mCuts.size();
    }
}
