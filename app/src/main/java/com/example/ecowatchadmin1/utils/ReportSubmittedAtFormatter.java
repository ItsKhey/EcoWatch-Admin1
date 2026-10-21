package com.example.ecowatchadmin1.utils;

import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats the backend-owned report submission timestamp.
 *
 * <p>The report API exposes PostgreSQL {@code created_at} as an ISO-8601
 * string. Keeping parsing and presentation here prevents individual report
 * screens from inventing alternate submission-time fields.</p>
 */
public final class ReportSubmittedAtFormatter {
    private static final TimeZone DISPLAY_TIME_ZONE = TimeZone.getTimeZone("Asia/Manila");
    private static final Pattern FRACTIONAL_SECONDS = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2})(\\.\\d+)(.*)$");

    private ReportSubmittedAtFormatter() {
        // Utility class.
    }

    /** Formats the submission date as {@code Sep 25, 2026}. */
    @Nullable
    public static String formatDate(@Nullable String raw) {
        Date submittedAt = parse(raw);
        if (submittedAt == null) return null;
        SimpleDateFormat formatter = new SimpleDateFormat("MMM d, yyyy", Locale.US);
        formatter.setTimeZone(DISPLAY_TIME_ZONE);
        return formatter.format(submittedAt);
    }

    /** Formats the submission time as {@code 1:53 PM}. */
    @Nullable
    public static String formatTime(@Nullable String raw) {
        Date submittedAt = parse(raw);
        if (submittedAt == null) return null;
        SimpleDateFormat formatter = new SimpleDateFormat("h:mm a", Locale.US);
        formatter.setTimeZone(DISPLAY_TIME_ZONE);
        return formatter.format(submittedAt);
    }

    /**
     * Supports the timestamp forms emitted by PostgreSQL/Supabase and the
     * legacy epoch values retained during the MongoDB-to-PostgreSQL migration.
     */
    @Nullable
    public static Date parse(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String value = normalizeFractionalSeconds(raw.trim());

        try {
            long numeric = Long.parseLong(value);
            boolean milliseconds = value.length() >= 12;
            return new Date(milliseconds ? numeric : numeric * 1000L);
        } catch (NumberFormatException ignored) {
            // Continue with ISO/PostgreSQL formats.
        }

        Date explicitOffset = parseWithExplicitOffset(value);
        if (explicitOffset != null) return explicitOffset;

        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXX",
                "yyyy-MM-dd'T'HH:mm:ssXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss.SSSSSSXXX",
                "yyyy-MM-dd HH:mm:ss.SSSXXX",
                "yyyy-MM-dd HH:mm:ssXXX",
                "yyyy-MM-dd HH:mm:ss.SSSSSS",
                "yyyy-MM-dd HH:mm:ss.SSS",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
                parser.setLenient(false);
                parser.setTimeZone(hasExplicitOffset(pattern) ? TimeZone.getTimeZone("UTC")
                        : DISPLAY_TIME_ZONE);
                return parser.parse(value);
            } catch (ParseException ignored) {
                // Try the next supported representation.
            }
        }
        return null;
    }

    /**
     * SimpleDateFormat's {@code XXX} implementation is not consistent across
     * Android/JVM releases for six-digit fractional seconds. Parse the local
     * date fields first, then apply the explicit offset arithmetically.
     */
    @Nullable
    private static Date parseWithExplicitOffset(String value) {
        String iso = value.replace(' ', 'T');
        int offsetStart = -1;
        char offsetSign = 0;

        if (iso.endsWith("Z") || iso.endsWith("z")) {
            offsetStart = iso.length() - 1;
            offsetSign = '+';
        } else {
            int plus = iso.lastIndexOf('+');
            int minus = iso.lastIndexOf('-');
            int candidate = plus > minus ? plus : minus;
            if (candidate > 10) {
                offsetStart = candidate;
                offsetSign = iso.charAt(candidate);
            }
        }

        if (offsetStart < 0) return null;

        int offsetMinutes;
        if (offsetStart == iso.length() - 1) {
            offsetMinutes = 0;
        } else {
            String offset = iso.substring(offsetStart + 1);
            if (offset.startsWith(":")) offset = offset.substring(1);
            if (offset.length() == 4 && offset.indexOf(':') < 0) {
                offset = offset.substring(0, 2) + ":" + offset.substring(2);
            }
            if (offset.length() != 5 || offset.charAt(2) != ':') return null;
            try {
                int hours = Integer.parseInt(offset.substring(0, 2));
                int minutes = Integer.parseInt(offset.substring(3, 5));
                if (hours > 23 || minutes > 59) return null;
                offsetMinutes = (hours * 60) + minutes;
                if (offsetSign == '-') offsetMinutes = -offsetMinutes;
            } catch (NumberFormatException error) {
                return null;
            }
        }

        String localValue = iso.substring(0, offsetStart);
        int fractionStart = localValue.indexOf('.');
        long fractionMillis = 0L;
        if (fractionStart >= 0) {
            String digits = localValue.substring(fractionStart + 1);
            localValue = localValue.substring(0, fractionStart);
            if (digits.length() > 3) digits = digits.substring(0, 3);
            while (digits.length() < 3) digits = digits + "0";
            try {
                fractionMillis = Long.parseLong(digits);
            } catch (NumberFormatException error) {
                return null;
            }
        }

        try {
            SimpleDateFormat parser = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            parser.setLenient(false);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date local = parser.parse(localValue);
            if (local == null) return null;
            return new Date(local.getTime() + fractionMillis
                    - (offsetMinutes * 60_000L));
        } catch (ParseException error) {
            return null;
        }
    }

    private static String normalizeFractionalSeconds(String value) {
        Matcher matcher = FRACTIONAL_SECONDS.matcher(value);
        if (!matcher.matches()) return value;

        String digits = matcher.group(2).substring(1);
        if (digits.length() > 6) digits = digits.substring(0, 6);
        while (digits.length() < 6) digits = digits + "0";
        String suffix = matcher.group(3);
        if ("Z".equalsIgnoreCase(suffix)) suffix = "+00:00";
        return matcher.group(1) + "." + digits + suffix;
    }

    private static boolean hasExplicitOffset(String pattern) {
        return pattern.endsWith("X");
    }
}
