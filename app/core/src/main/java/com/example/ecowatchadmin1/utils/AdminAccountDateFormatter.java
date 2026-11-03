package com.example.ecowatchadmin1.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Formats server-owned ISO-8601 account timestamps for the profile and reporter
 * screens.
 *
 * <p>Parsing is delegated to {@link ReportSubmittedAtFormatter}. This class used
 * to carry its own copy of the pattern list, and the two copies drifted: the one
 * here was missing {@code ReportSubmittedAtFormatter}'s workaround for
 * {@code SimpleDateFormat} mis-parsing six-digit fractional seconds with an
 * {@code XXX} offset, so a timestamp such as {@code 2026-09-25T05:53:12.123456Z}
 * fell through every pattern and matched the bare {@code yyyy-MM-dd} entry,
 * rendering as midnight instead of the actual time. It also forced UTC onto
 * zone-less timestamps, shifting Philippine wall-clock values eight hours
 * forward.
 *
 * <p>Both are fixed by having one parser. Adding a timestamp form should now mean
 * changing {@link ReportSubmittedAtFormatter} once, not keeping two lists in step.
 */
public final class AdminAccountDateFormatter {

    private static final TimeZone DISPLAY_TIME_ZONE = TimeZone.getTimeZone("Asia/Manila");

    private AdminAccountDateFormatter() {
        // Utility class.
    }

    /**
     * Returns a localized date/time, or {@code null} when the server did not
     * provide a parseable timestamp. Callers decide how to render the empty
     * state so the value is never fabricated on the client.
     */
    public static String format(String rawTimestamp) {
        if (rawTimestamp == null || rawTimestamp.trim().isEmpty()) {
            return null;
        }

        Date date = ReportSubmittedAtFormatter.parse(rawTimestamp.trim());
        if (date == null) {
            return null;
        }

        // Locale.US rather than Locale.getDefault(): the user can switch the app
        // language, and a non-English locale would rename the month and switch the
        // AM/PM marker to its own script. The month name and the 12-hour marker
        // should not change with the interface language.
        //
        // h:mm is deliberately unpadded, so 09:05 reads "9:05 AM".
        SimpleDateFormat output = new SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US);
        output.setTimeZone(DISPLAY_TIME_ZONE);
        return output.format(date);
    }
}