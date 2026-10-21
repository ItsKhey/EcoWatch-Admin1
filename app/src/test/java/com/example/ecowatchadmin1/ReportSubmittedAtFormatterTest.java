package com.example.ecowatchadmin1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.ecowatchadmin1.utils.ReportSubmittedAtFormatter;

import org.junit.Test;

import java.time.Instant;

public class ReportSubmittedAtFormatterTest {

    @Test
    public void formatsUtcCreatedAtInManilaTime() {
        String createdAt = "2026-09-25T05:53:12.123456+00:00";

        assertEquals("Sep 25, 2026", ReportSubmittedAtFormatter.formatDate(createdAt));
        assertEquals("1:53 PM", ReportSubmittedAtFormatter.formatTime(createdAt));
    }

    @Test
    public void acceptsZuluAndNoZonePostgresTimestamps() {
        assertEquals("1:53 PM", ReportSubmittedAtFormatter.formatTime(
                "2026-09-25T05:53:12Z"));
        assertEquals("1:53 PM", ReportSubmittedAtFormatter.formatTime(
                "2026-09-25T13:53:12"));
    }

    @Test
    public void acceptsEpochMillisecondsFromMigratedReports() {
        long epochMillis = Instant.parse("2026-09-25T05:53:12Z").toEpochMilli();

        assertEquals("Sep 25, 2026",
                ReportSubmittedAtFormatter.formatDate(Long.toString(epochMillis)));
        assertEquals("1:53 PM",
                ReportSubmittedAtFormatter.formatTime(Long.toString(epochMillis)));
    }

    @Test
    public void rejectsMissingOrMalformedTimestamps() {
        assertNull(ReportSubmittedAtFormatter.formatDate(null));
        assertNull(ReportSubmittedAtFormatter.formatTime(" "));
        assertNull(ReportSubmittedAtFormatter.formatTime("not-a-date"));
    }
}
