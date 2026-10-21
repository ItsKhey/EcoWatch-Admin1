package com.example.ecowatchadmin1.utils;

import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Shared formatter for notification-center timestamps. Cloned from the
 * Resident app so backend {@code created_at} values render identically in both
 * applications.
 */
public final class NotificationDateFormatter {
    private static final TimeZone DISPLAY_TIME_ZONE = TimeZone.getTimeZone("Asia/Manila");

    private NotificationDateFormatter() {
    }

    @Nullable
    public static Date parse(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String value = raw.trim().replace("Z", "+00:00");
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss.SSSXXX",
                "yyyy-MM-dd HH:mm:ssXXX",
                "yyyy-MM-dd HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
                parser.setLenient(false);
                parser.setTimeZone(pattern.endsWith("X")
                        ? TimeZone.getTimeZone("UTC")
                        : DISPLAY_TIME_ZONE);
                return parser.parse(value);
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    public static String format(@Nullable String raw) {
        Date date = parse(raw);
        if (date == null) return raw == null || raw.trim().isEmpty() ? "—" : raw;
        SimpleDateFormat formatter = new SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US);
        formatter.setTimeZone(DISPLAY_TIME_ZONE);
        return formatter.format(date);
    }
}
