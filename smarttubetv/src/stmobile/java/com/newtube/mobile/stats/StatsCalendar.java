package com.newtube.mobile.stats;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Local calendar days as plain numbers (days since 1970-01-01, counted in the phone's time zone), so
 * a day, a week and a month are ranges of longs. The UTC calendar does the date math: it has no
 * daylight-saving gaps.
 */
public final class StatsCalendar {
    private static final long DAY_MS = 86_400_000L;
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private StatsCalendar() {
    }

    public static long today() {
        return dayOf(System.currentTimeMillis(), TimeZone.getDefault());
    }

    public static long dayOf(long wallMs, TimeZone zone) {
        Calendar local = Calendar.getInstance(zone);
        local.setTimeInMillis(wallMs);
        return of(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
    }

    static long of(int year, int month, int dayOfMonth) {
        Calendar utc = Calendar.getInstance(UTC);
        utc.clear();
        utc.set(year, month, dayOfMonth);
        return utc.getTimeInMillis() / DAY_MS;
    }

    /** Midnight UTC of the day: format it with a UTC formatter to get the local date back. */
    public static long utcMs(long day) {
        return day * DAY_MS;
    }

    /** {@code firstDayOfWeek}: a {@link Calendar#SUNDAY}..{@link Calendar#SATURDAY} constant. */
    public static long weekStart(long day, int firstDayOfWeek) {
        // 1970-01-01 was a Thursday.
        int dayOfWeek = (int) Math.floorMod(day + 4, 7L) + 1; // Calendar.SUNDAY == 1
        int back = Math.floorMod(dayOfWeek - firstDayOfWeek, 7);
        return day - back;
    }

    public static long monthStart(long day) {
        Calendar utc = calendar(day);
        utc.set(Calendar.DAY_OF_MONTH, 1);
        return utc.getTimeInMillis() / DAY_MS;
    }

    /** The first day of the month {@code delta} months from the one {@code day} is in. */
    public static long addMonths(long day, int delta) {
        Calendar utc = calendar(monthStart(day));
        utc.add(Calendar.MONTH, delta);
        return utc.getTimeInMillis() / DAY_MS;
    }

    /** January 1st of the year {@code delta} years from the one {@code day} is in. */
    public static long addYears(long day, int delta) {
        Calendar utc = calendar(day);
        utc.set(Calendar.MONTH, Calendar.JANUARY);
        utc.set(Calendar.DAY_OF_MONTH, 1);
        utc.add(Calendar.YEAR, delta);
        return utc.getTimeInMillis() / DAY_MS;
    }

    /** Months since year 0, for counting the months between two days. */
    public static int monthIndex(long day) {
        Calendar utc = calendar(day);
        return utc.get(Calendar.YEAR) * 12 + utc.get(Calendar.MONTH);
    }

    private static Calendar calendar(long day) {
        Calendar utc = Calendar.getInstance(UTC);
        utc.setTimeInMillis(day * DAY_MS);
        return utc;
    }
}
