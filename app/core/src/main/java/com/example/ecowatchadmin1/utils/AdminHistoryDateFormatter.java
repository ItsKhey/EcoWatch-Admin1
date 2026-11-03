package com.example.ecowatchadmin1.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Formats an Admin History card's timestamp as {@code Oct 8, 2026 • 2:35 PM}.
 *
 * <p>Uses {@code h:mm} rather than {@code hh:mm}: {@code hh} is the zero-padded
 * 12-hour clock, which renders 09:05 as "09:05 AM" while the rest of the app shows
 * "9:05 AM". Every history card would then disagree with every other timestamp on
 * screen. Kept in Manila time for the same reason the rest of the app pins it:
 * an admin reviewing decisions reads them in their own local zone, not UTC.
 */
public final class AdminHistoryDateFormatter {

    private static final String PATTERN = "MMM dd, yyyy • h:mm a";
    private static final TimeZone ZONE = TimeZone.getTimeZone("Asia/Manila");

    private AdminHistoryDateFormatter() {
    }

    /**
     * Formats a millisecond timestamp.
     *
     * @return the formatted date and time, or "Unknown time" when the row carries
     *         no timestamp rather than an empty line
     */
    public static String format(Long timestamp) {
        if (timestamp == null || timestamp <= 0L) return "Unknown time";

        SimpleDateFormat formatter = new SimpleDateFormat(PATTERN, Locale.US);
        formatter.setTimeZone(ZONE);
        return formatter.format(new Date(timestamp));
    }
}
