package com.example.ecowatchadmin1;

import static org.junit.Assert.assertEquals;

import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.remote.AdminJsonParser;
import com.example.ecowatchadmin1.data.remote.ReportUpdateMerger;
import com.google.gson.JsonParser;

import org.junit.Test;

public class ReportUpdateMergerTest {

    @Test
    public void partialAssignmentCannotEraseBackendCreatedAt() {
        Report existing = new Report("Illegal dumping", "Original details",
                "Bulk waste", 14.5995, 120.9842);
        existing.setReportId("REP-001");
        existing.setMongoId("REP-001");
        existing.setResidentId("RES-001");
        existing.setPhotoUrl("https://example.com/photo.jpg");
        existing.setCreatedAt("2026-09-25T05:53:12.123+00:00");

        Report partial = AdminJsonParser.parseReport(new JsonParser().parse(
                "{\"report_id\":\"REP-001\",\"status\":\"In Progress\","
                        + "\"admin_response\":\"Personnel assigned.\"}"));
        Report merged = ReportUpdateMerger.merge(existing, partial);

        assertEquals("2026-09-25T05:53:12.123+00:00", merged.getCreatedAt());
        assertEquals("Illegal dumping", merged.getTitle());
        assertEquals("Bulk waste", merged.getWasteType());
        assertEquals("Original details", merged.getDescription());
        assertEquals("https://example.com/photo.jpg", merged.getPhotoUrl());
        assertEquals(14.5995, merged.getLatitude(), 0.00001);
        assertEquals(120.9842, merged.getLongitude(), 0.00001);
        assertEquals("In Progress", merged.getStatus());
        assertEquals("Personnel assigned.", merged.getAdminResponse());
    }
}
