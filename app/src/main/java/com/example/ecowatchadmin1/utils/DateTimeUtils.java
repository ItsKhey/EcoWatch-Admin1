package com.example.ecowatchadmin1.utils;

import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Shared handling for the ISO-ish timestamps the EcoWatch backend returns.
 *
 * <p>The API is not consistent about the shape of a date field: some endpoints
 * send {@code 2024-05-06T09:15:00.000Z}, some send a plain
 * {@code 2024-05-06 09:15:00}, and a couple send epoch milliseconds as a
 * string. Every screen that needs to reason about "today" needs the same
 * tolerant parsing, otherwise an unrecognized shape silently reads as a
 * non-match and quietly empties a filter.
 *
 * <p>Calendar comparisons use {@link #DISPLAY_TIME_ZONE} so a report submitted
 * at 23:50 in Manila does not get filed under the previous day.
 */
public final class DateTimeUtils {

    /** Timezone the app presents dates in, and therefore compares "days" in. */
    public static final String DISPLAY_TIME_ZONE = "Asia/Manila";

    /** Accepted timestamp shapes, most specific first. */
    private static final String[] TIMESTAMP_PATTERNS = new String[]{
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
    };

    private DateTimeUtils() {
    }

    /**
     * Parses any of the accepted timestamp shapes.
     *
     * <p>Patterns ending in {@code 'Z'} are read as UTC; the {@code XXX}
     * patterns honor whatever offset they carry; bare numeric values are
     * treated as epoch values.
     *
     * @return the parsed date, or {@code null} when the value is missing or
     *         in a shape we do not recognise.
     */
    @Nullable
    public static Date parseTimestamp(@Nullable String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;

        // Epoch value: 10-digit values are seconds, anything longer is millis.
        if (trimmed.matches("\\d{10,17}")) {
            try {
                long raw = Long.parseLong(trimmed);
                return new Date(trimmed.length() <= 11 ? raw * 1000L : raw);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        for (String pattern : TIMESTAMP_PATTERNS) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
                if (pattern.endsWith("'Z'")) {
                    parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                return parser.parse(trimmed);
            } catch (ParseException ignored) {
                // Try the next known shape.
            }
        }
        return null;
    }

    /** True when the timestamp falls on the current calendar day. */
    public static boolean isToday(@Nullable String timestamp) {
        Date date = parseTimestamp(timestamp);
        if (date == null) return false;
        return isSameDay(date, new Date());
    }

    /**
     * True when the timestamp falls inside the last {@code days} days, counting
     * today as the first day. {@code days = 7} therefore includes today and the
     * six days before it.
     */
    public static boolean isWithinLastDays(@Nullable String timestamp, int days) {
        Date date = parseTimestamp(timestamp);
        if (date == null || days <= 0) return false;
        return !date.before(startOfDayDaysAgo(days - 1));
    }

    /**
     * Formats a timestamp the way the dashboard rows do: a time for anything
     * from today, prefixed with "Today", otherwise prefixed with a short date.
     *
     * @return the formatted value, or the original text when it cannot be
     *         parsed, so an odd payload is still visible rather than hidden.
     */
    public static String formatRelativeTime(@Nullable String timestamp) {
        Date date = parseTimestamp(timestamp);
        if (date == null) return timestamp == null ? "" : timestamp;

        TimeZone zone = TimeZone.getTimeZone(DISPLAY_TIME_ZONE);
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.US);
        timeFormat.setTimeZone(zone);
        String time = timeFormat.format(date);

        if (isSameDay(date, new Date())) {
            return "Today · " + time;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d", Locale.US);
        dateFormat.setTimeZone(zone);
        return dateFormat.format(date) + " · " + time;
    }

    private static boolean isSameDay(Date first, Date second) {
        TimeZone zone = TimeZone.getTimeZone(DISPLAY_TIME_ZONE);
        Calendar a = Calendar.getInstance(zone);
        Calendar b = Calendar.getInstance(zone);
        a.setTime(first);
        b.setTime(second);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private static Date startOfDayDaysAgo(int days) {
        TimeZone zone = TimeZone.getTimeZone(DISPLAY_TIME_ZONE);
        Calendar calendar = Calendar.getInstance(zone);
        calendar.add(Calendar.DAY_OF_YEAR, -days);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
}
