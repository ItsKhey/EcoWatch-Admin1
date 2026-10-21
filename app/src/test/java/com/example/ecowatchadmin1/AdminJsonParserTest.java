package com.example.ecowatchadmin1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.ecowatchadmin1.data.models.AdminAccount;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.models.User;
import com.example.ecowatchadmin1.data.remote.AdminJsonParser;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.List;

public class AdminJsonParserTest {

    @Test
    public void parsesPostgresSnakeCaseReports() {
        String json = "[{"
                + "\"report_id\":\"RPT-001\","
                + "\"resident_id\":\"RES-001\","
                + "\"title\":\"Overflowing bin\","
                + "\"category\":\"Household waste\","
                + "\"description\":\"Bin is full\","
                + "\"latitude\":14.5995,"
                + "\"longitude\":120.9842,"
                + "\"image_url\":\"https://example.com/report.jpg\","
                + "\"status\":\"Pending\","
                + "\"created_at\":\"2026-09-24T10:00:00.000Z\","
                + "\"is_deleted\":false,"
                + "\"is_archived\":false"
                + "}]";

        List<Report> reports = AdminJsonParser.parseReports(new JsonParser().parse(json));

        assertEquals(1, reports.size());
        Report report = reports.get(0);
        assertEquals("RPT-001", report.getReportId());
        assertEquals("RES-001", report.getResidentId());
        assertEquals("Household waste", report.getWasteType());
        assertEquals("https://example.com/report.jpg", report.getPhotoUrl());
        assertEquals(14.5995, report.getLatitude(), 0.00001);
        assertEquals(120.9842, report.getLongitude(), 0.00001);
        assertEquals("2026-09-24T10:00:00.000Z", report.getCreatedAt());
        assertTrue(report.isSynced());
    }

    @Test
    public void acceptsWrappedReportsResponse() {
        String json = "{\"success\":true,\"data\":{\"reports\":[{"
                + "\"report_id\":\"RPT-002\","
                + "\"title\":\"Illegal dumping\","
                + "\"category\":\"Bulk waste\","
                + "\"latitude\":14.6,"
                + "\"longitude\":120.98"
                + "}]}}";

        List<Report> reports = AdminJsonParser.parseReports(new JsonParser().parse(json));

        assertEquals(1, reports.size());
        assertEquals("RPT-002", reports.get(0).getReportId());
    }

    @Test
    public void parsesCanonicalAdminLogin() {
        String json = "{\"token\":\"token\",\"admin\":{"
                + "\"admin_id\":\"ADM-92A9EDDB\","
                + "\"first_name\":\"Cenro\","
                + "\"last_name\":\"Admin\","
                + "\"username\":\"cenro\","
                + "\"email\":\"cenro@example.com\","
                + "\"role\":\"ADMIN\","
                + "\"created_at\":\"2026-09-20T10:00:00.000Z\""
                + "}}";

        User user = AdminJsonParser.parseLoginUser(
                new JsonParser().parse(json).getAsJsonObject(),
                "cenro"
        );

        assertEquals("ADM-92A9EDDB", user.getServerId());
        assertEquals("ADMIN", user.getRole());
        assertEquals("2026-09-20T10:00:00.000Z", user.getCreatedAt());
    }

    @Test
    public void parsesCanonicalAdminAccountDetails() {
        String json = "{\"success\":true,\"admin\":{"
                + "\"admin_id\":\"ADM-92A9EDDB\","
                + "\"role\":\"ADMIN\","
                + "\"created_at\":\"2026-09-20T10:00:00.000Z\","
                + "\"last_active\":\"2026-09-25T12:30:00.000Z\""
                + "}}";

        AdminAccount account = AdminJsonParser.parseAccountDetails(
                new JsonParser().parse(json).getAsJsonObject());

        assertEquals("ADM-92A9EDDB", account.getAdminId());
        assertEquals("ADMIN", account.getRole());
        assertEquals("2026-09-20T10:00:00.000Z", account.getCreatedAt());
        assertEquals("2026-09-25T12:30:00.000Z", account.getLastActive());
    }

    @Test
    public void canUseVerifiedAdminIdDuringLegacyResponseRollout() {
        String json = "{\"token\":\"token\",\"resident\":{"
                + "\"residentId\":\"RES-LEGACY\","
                + "\"username\":\"cenro\","
                + "\"role\":\"ADMIN\""
                + "}}";

        User user = AdminJsonParser.parseLoginUser(
                new JsonParser().parse(json).getAsJsonObject(),
                "cenro",
                "ADM-92A9EDDB"
        );

        assertEquals("ADM-92A9EDDB", user.getServerId());
    }

    @Test
    public void canUseJwtAdminIdForLegacyAccountEndpoint() {
        String json = "{\"resident\":{"
                + "\"residentId\":\"RES-LEGACY\","
                + "\"firstName\":\"Cenro\","
                + "\"lastName\":\"Admin\","
                + "\"username\":\"cenro\""
                + "}}";

        AdminAccount account = AdminJsonParser.parseAccountDetails(
                new JsonParser().parse(json).getAsJsonObject(),
                "ADM-92A9EDDB"
        );

        assertEquals("ADM-92A9EDDB", account.getAdminId());
        assertEquals("ADMIN", account.getRole());
    }

    @Test
    public void rejectsAdminPayloadThatOnlyContainsResidentId() {
        String json = "{\"admin\":{\"residentId\":\"ADM-92A9EDDB\","
                + "\"role\":\"ADMIN\"}}";

        try {
            AdminJsonParser.parseAccountDetails(
                    new JsonParser().parse(json).getAsJsonObject());
            fail("A resident identifier must not be accepted as an admin ID.");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    @Test
    public void parsesCanonicalAssignmentFields() {
        String json = "{\"report_id\":\"REP-003\",\"title\":\"Bin overflow\","
                + "\"created_at\":\"2026-09-25T05:53:12.123+00:00\","
                + "\"assigned_personnel_id\":\"PERS-0007\","
                + "\"assigned_personnel_name\":\"Juan Dela Cruz\","
                + "\"assigned_personnel_avatar\":\"https://example.com/avatar.jpg\","
                + "\"assigned_by_admin_id\":\"ADM-0001\","
                + "\"assigned_at\":\"2026-09-25T10:05:00.000Z\","
                + "\"admin_response\":\"Please clean this area.\"}";

        Report report = AdminJsonParser.parseReport(new JsonParser().parse(json));

        assertEquals("PERS-0007", report.getAssignedPersonnelId());
        assertEquals("Juan Dela Cruz", report.getAssignedPersonnelName());
        assertEquals("https://example.com/avatar.jpg", report.getAssignedPersonnelAvatar());
        assertEquals("ADM-0001", report.getAssignedByAdminId());
        assertEquals("2026-09-25T05:53:12.123+00:00", report.getCreatedAt());
        assertEquals("2026-09-25T10:05:00.000Z", report.getAssignedAt());
        assertEquals("Please clean this area.", report.getAdminResponse());
    }

    @Test
    public void parsesNestedAssignmentAndLegacyListFallback() {
        String nested = "{\"data\":{\"report\":{\"reportId\":\"REP-004\","
                + "\"title\":\"Dumping\",\"assignedPersonnel\":{\"personnelId\":\"PERS-0008\","
                + "\"firstName\":\"Ana\",\"lastName\":\"Santos\",\"avatarUrl\":\"avatar.png\"}}}}";
        Report report = AdminJsonParser.parseReport(new JsonParser().parse(nested));
        assertEquals("PERS-0008", report.getAssignedPersonnelId());
        assertEquals("Ana Santos", report.getAssignedPersonnelName());
        assertEquals("avatar.png", report.getAssignedPersonnelAvatar());

        String legacy = "{\"report_id\":\"REP-005\",\"assigned_personnel_ids\":[\"PERS-0009\"]}";
        Report fallback = AdminJsonParser.parseReport(new JsonParser().parse(legacy));
        assertEquals("PERS-0009", fallback.getAssignedPersonnelId());
    }
}
