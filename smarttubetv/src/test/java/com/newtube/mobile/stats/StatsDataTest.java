package com.newtube.mobile.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class StatsDataTest {
    private static final long MIN = 60_000;

    @Before
    public void reset() {
        LocalStats.resetForTest();
        context().getSharedPreferences(LocalStats.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void sumsByDayRangeAndSplitsDownloads() {
        StatsData data = new StatsData();
        data.addWatch(100, 1, "UC1", "One", false, 30 * MIN, true);
        data.addWatch(101, 2, "UC1", "One", true, 10 * MIN, false);
        data.addWatch(105, 3, "UC2", "Two", false, 5 * MIN, true);
        data.addDownload(101, 4, false);
        data.addDownload(105, 5, true);

        StatsData.Totals firstTwo = data.sum(100, 101);
        assertEquals(40 * MIN, firstTwo.watchMs);
        assertEquals(10 * MIN, firstTwo.offlineMs);
        assertEquals(30 * MIN, firstTwo.streamedMs());
        assertEquals(1, firstTwo.videos);
        assertEquals(1, firstTwo.downloads);

        StatsData.Totals all = data.allTime();
        assertEquals(45 * MIN, all.watchMs);
        assertEquals(2, all.videos);
        assertEquals(2, all.downloads);
        assertEquals(1, data.getVideoDownloads());
        assertEquals(1, data.getAudioDownloads());
        assertEquals(1, data.getSinceMs());

        List<StatsData.Channel> top = data.topChannels(5);
        assertEquals("One", top.get(0).name);
        assertEquals(40 * MIN, top.get(0).watchMs);
    }

    @Test
    public void oldDaysArePrunedButAllTimeKeepsThem() {
        StatsData data = new StatsData();
        data.addWatch(10, 1, null, null, false, MIN, false);
        data.addWatch(10 + StatsData.KEEP_DAYS + 1, 2, null, null, false, MIN, false);
        assertEquals(0, data.sum(0, 20).watchMs);
        assertEquals(2 * MIN, data.allTime().watchMs);
    }

    @Test
    public void survivesJson() throws Exception {
        StatsData data = new StatsData();
        data.addWatch(200, 7, "UC1", "One", true, 3 * MIN, true);
        data.addDownload(200, 8, false);
        StatsData back = StatsData.fromJson(data.toJson());
        assertEquals(3 * MIN, back.sum(200, 200).offlineMs);
        assertEquals(1, back.sum(200, 200).downloads);
        assertEquals("One", back.topChannels(1).get(0).name);
        assertEquals(7, back.getSinceMs());
    }

    @Test
    public void kindsHoursAndBiggestDay() throws Exception {
        StatsData data = new StatsData();
        data.addWatch(300, 1, "UC1", "One", false, 10 * MIN, false, StatsData.KIND_SHORTS, 23);
        data.addWatch(300, 2, "UC1", "One", false, 5 * MIN, false, StatsData.KIND_VIDEO, 23);
        data.addWatch(301, 3, "UC2", "Two", false, 4 * MIN, false, StatsData.KIND_MUSIC, 9);
        assertEquals(10 * MIN, data.kindMs(StatsData.KIND_SHORTS));
        assertEquals(0, data.kindMs(StatsData.KIND_LIVE));
        assertEquals(23, data.peakHour());
        assertEquals(300, data.biggestDay());
        assertEquals(15 * MIN, data.dayWatchMs(300));
        assertEquals(2, data.activeDays());

        data.addCategory("Gaming", 3 * MIN);
        data.addCategory("Education", 5 * MIN);
        data.addCategory("Gaming", 4 * MIN);
        data.addCategory("", 9 * MIN);
        assertEquals("Gaming", data.categories().get(0).getKey());
        assertEquals(2, data.categories().size());

        StatsData back = StatsData.fromJson(data.toJson());
        assertEquals(4 * MIN, back.kindMs(StatsData.KIND_MUSIC));
        assertEquals(7 * MIN, (long) back.categories().get(0).getValue());
        assertEquals(23, back.peakHour());
    }

    @Test
    public void nothingIsRecordedWithoutConsent() {
        Context context = context();
        assertEquals(LocalStats.CONSENT_UNASKED, LocalStats.getConsent(context));
        LocalStats.recordWatch(context, "UC1", "One", false, MIN, true);
        LocalStats.recordDownload(context, false);
        assertTrue(LocalStats.snapshot(context).isEmpty());

        LocalStats.setRecording(context, false);
        LocalStats.recordWatch(context, "UC1", "One", false, MIN, true);
        assertTrue(LocalStats.snapshot(context).isEmpty());

        LocalStats.setRecording(context, true);
        LocalStats.recordWatch(context, "UC1", "One", false, MIN, true);
        assertFalse(LocalStats.snapshot(context).isEmpty());

        LocalStats.resetForTest(); // a new process reads it back
        assertEquals(LocalStats.CONSENT_ON, LocalStats.getConsent(context));
        assertEquals(MIN, LocalStats.snapshot(context).allTime().watchMs);

        LocalStats.clear(context);
        assertTrue(LocalStats.snapshot(context).isEmpty());
    }

    @Test
    public void calendarWeeksAndMonths() {
        long day = StatsCalendar.of(2026, Calendar.NOVEMBER, 27); // a Friday
        assertEquals(StatsCalendar.of(2026, Calendar.NOVEMBER, 23), StatsCalendar.weekStart(day, Calendar.MONDAY));
        assertEquals(StatsCalendar.of(2026, Calendar.NOVEMBER, 22), StatsCalendar.weekStart(day, Calendar.SUNDAY));
        assertEquals(day, StatsCalendar.weekStart(day, Calendar.FRIDAY));
        assertEquals(StatsCalendar.of(2026, Calendar.NOVEMBER, 1), StatsCalendar.monthStart(day));
        assertEquals(StatsCalendar.of(2026, Calendar.OCTOBER, 1), StatsCalendar.addMonths(day, -1));
        assertEquals(StatsCalendar.of(2025, Calendar.DECEMBER, 1), StatsCalendar.addMonths(day, -11));
        assertEquals(StatsCalendar.of(2026, Calendar.JANUARY, 1), StatsCalendar.addYears(day, 0));
        assertEquals(StatsCalendar.of(2024, Calendar.JANUARY, 1), StatsCalendar.addYears(day, -2));
        assertEquals(10, StatsCalendar.monthIndex(day) - StatsCalendar.monthIndex(StatsCalendar.of(2026, Calendar.JANUARY, 5)));

        // 01:30 in Jerusalem is still the day before in UTC; the local date wins.
        TimeZone zone = TimeZone.getTimeZone("Asia/Jerusalem");
        Calendar local = Calendar.getInstance(zone);
        local.clear();
        local.set(2026, Calendar.NOVEMBER, 27, 1, 30);
        assertEquals(day, StatsCalendar.dayOf(local.getTimeInMillis(), zone));
    }

    private static Context context() {
        return RuntimeEnvironment.getApplication();
    }
}
