package com.example.ecowatchadmin1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.remote.AdminJsonParser;
import com.example.ecowatchadmin1.utils.PhotoCountBadge;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.List;

/**
 * The admin list badges a report by photo count, so the parser has to carry the
 * server's {@code photos} array through to the model, and the badge rule has to
 * agree with the resident app's copy of it.
 */
public class AdminPhotoCountTest {

    private static Report parseSingle(String extraFields) {
        String json = "[{"
                + "\"report_id\":\"RPT-PHOTO-1\","
                + "\"resident_id\":\"RES-001\","
                + "\"title\":\"Multi photo report\","
                + "\"category\":\"Biodegradable\","
                + "\"image_url\":\"https://example.com/a.jpg\"" + extraFields
                + "}]";
        List<Report> reports = AdminJsonParser.parseReports(new JsonParser().parse(json));
        assertEquals(1, reports.size());
        return reports.get(0);
    }

    @Test
    public void parsesTheServerPhotoArray() {
        Report report = parseSingle(",\"photos\":["
                + "\"https://example.com/a.jpg\","
                + "\"https://example.com/b.jpg\","
                + "\"https://example.com/c.jpg\","
                + "\"https://example.com/d.jpg\","
                + "\"https://example.com/e.jpg\"]");

        assertEquals(5, report.getPhotoUrls().size());
        assertTrue(PhotoCountBadge.shouldShow(report.getPhotoUrls()));
        assertEquals("5", PhotoCountBadge.format(report.getPhotoUrls()));
    }

    @Test
    public void leavesTheBadgeHiddenWhenNoPhotoArrayIsSent() {
        // An older backend, or a report predating the per-photo rows.
        Report report = parseSingle("");

        assertTrue(report.getPhotoUrls().isEmpty());
        assertFalse(PhotoCountBadge.shouldShow(report.getPhotoUrls()));
    }

    @Test
    public void treatsASinglePhotoArrayAsNoBadge() {
        Report report = parseSingle(",\"photos\":[\"https://example.com/a.jpg\"]");

        assertEquals(1, report.getPhotoUrls().size());
        assertFalse(PhotoCountBadge.shouldShow(report.getPhotoUrls()));
    }

    @Test
    public void dropsBlankAndRepeatedUrls() {
        Report report = parseSingle(",\"photos\":["
                + "\"https://example.com/a.jpg\","
                + "\"  \","
                + "\"https://example.com/a.jpg\","
                + "\"https://example.com/b.jpg\"]");

        assertEquals(2, report.getPhotoUrls().size());
        assertTrue(PhotoCountBadge.shouldShow(report.getPhotoUrls()));
        assertEquals("2", PhotoCountBadge.format(report.getPhotoUrls()));
    }

    @Test
    public void badgeRuleMatchesTheResidentApp() {
        assertEquals(2, PhotoCountBadge.MIN_PHOTOS_FOR_BADGE);
    }
}
