package com.example.ecowatchadmin1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchadmin1.data.models.ReportStatistics;
import com.example.ecowatchadmin1.data.models.ResidentAccount;
import com.example.ecowatchadmin1.data.remote.ResidentJsonParser;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.List;

public class ResidentJsonParserTest {

    @Test
    public void parsesWrappedReporterListAndServerCounts() {
        String json = "{\"success\":true,\"residents\":[{"
                + "\"residentId\":\"RES-ABC12345\","
                + "\"firstName\":\"Juan\","
                + "\"lastName\":\"Dela Cruz\","
                + "\"username\":\"juan\","
                + "\"email\":\"juan@example.com\","
                + "\"accountStatus\":\"Active\","
                + "\"isActive\":true,"
                + "\"isDisabled\":false,"
                + "\"totalReports\":7"
                + "}]}";

        List<ResidentAccount> residents = ResidentJsonParser.parseResidents(
                new JsonParser().parse(json));

        assertEquals(1, residents.size());
        ResidentAccount resident = residents.get(0);
        assertEquals("RES-ABC12345", resident.getResidentId());
        assertEquals("Juan Dela Cruz", resident.getFullName());
        assertEquals(7, resident.getTotalReports());
        assertTrue(resident.isActive());
        assertFalse(resident.isDisabled());
    }

    @Test
    public void parsesSnakeCaseResidentDetails() {
        String json = "{\"success\":true,\"resident\":{"
                + "\"resident_id\":\"RES-ABC12345\","
                + "\"first_name\":\"Juan\","
                + "\"last_name\":\"Dela Cruz\","
                + "\"username\":\"juan\","
                + "\"email\":\"juan@example.com\","
                + "\"status\":\"DISABLED\","
                + "\"last_active\":\"2026-09-25T12:30:00.000Z\""
                + "}}";

        ResidentAccount resident = ResidentJsonParser.parseResident(
                new JsonParser().parse(json));

        assertNotNull(resident);
        assertTrue(resident.isDisabled());
        assertEquals("Disabled", resident.getAccountStatus());
        assertEquals("2026-09-25T12:30:00.000Z", resident.getLastActive());
    }

    @Test
    public void parsesLiveStatisticsWithoutCalculatingOnClient() {
        String json = "{\"success\":true,\"statistics\":{"
                + "\"total\":9,\"pending\":2,\"in_progress\":3,"
                + "\"completed\":3,\"denied\":1}}";

        ReportStatistics statistics = ResidentJsonParser.parseStatistics(
                new JsonParser().parse(json));

        assertEquals(9, statistics.getTotal());
        assertEquals(2, statistics.getPending());
        assertEquals(3, statistics.getInProgress());
        assertEquals(3, statistics.getCompleted());
        assertEquals(1, statistics.getDenied());
    }

    @Test
    public void parsesResidentPaginationMetadata() {
        String json = "{\"residents\":[{\"residentId\":\"RES-ABC12345\","
                + "\"firstName\":\"A\",\"lastName\":\"B\"}],"
                + "\"pagination\":{\"page\":2,\"limit\":1,\"total\":3,\"totalPages\":3}}";

        com.example.ecowatchadmin1.data.models.ResidentPage page =
                ResidentJsonParser.parsePage(new JsonParser().parse(json));

        assertEquals(2, page.getPage());
        assertEquals(1, page.getLimit());
        assertEquals(3, page.getTotal());
        assertEquals(3, page.getTotalPages());
        assertTrue(page.hasMore());
    }

    @Test
    public void parsesReportPaginationMetadata() {
        String json = "{\"reports\":[{\"reportId\":\"REP-1\",\"title\":\"Test\","
                + "\"latitude\":1,\"longitude\":2}],"
                + "\"pagination\":{\"page\":1,\"limit\":50,\"total\":51,\"totalPages\":2}}";

        com.example.ecowatchadmin1.data.models.ReportPage page =
                ResidentJsonParser.parseReportPage(new JsonParser().parse(json));

        assertEquals(1, page.getPage());
        assertEquals(51, page.getTotal());
        assertTrue(page.hasMore());
    }

    @Test
    public void parsesContactFieldsFromBothWireSpellings() {
        String camel = "{\"residentId\":\"RES-CONTACT1\",\"firstName\":\"Juan\",\"lastName\":\"Dela Cruz\","
                + "\"phoneNumber\":\"09170000001\",\"messengerLink\":\"https://m.me/juan\"}";
        String snake = "{\"resident_id\":\"RES-CONTACT2\",\"full_name\":\"Pedro Reyes\","
                + "\"phone_number\":\"09170000002\",\"messenger_link\":\"https://m.me/pedro\"}";

        ResidentAccount fromCamel =
                ResidentJsonParser.parseResident(new JsonParser().parse(camel));
        ResidentAccount fromSnake =
                ResidentJsonParser.parseResident(new JsonParser().parse(snake));

        assertNotNull(fromCamel);
        assertNotNull(fromSnake);
        assertEquals("09170000001", fromCamel.getPhoneNumber());
        assertEquals("https://m.me/juan", fromCamel.getMessengerLink());
        assertEquals("09170000002", fromSnake.getPhoneNumber());
        assertEquals("https://m.me/pedro", fromSnake.getMessengerLink());
    }

    @Test
    public void absentContactFieldsReadAsBlankSoRowsStayDisabled() {
        String json = "{\"residentId\":\"RES-NOCONTACT\",\"firstName\":\"Ana\",\"lastName\":\"Reyes\"}";

        ResidentAccount resident = ResidentJsonParser.parseResident(new JsonParser().parse(json));

        assertNotNull(resident);
        // The API sends "" for these, but an older payload can omit them. Either
        // way the contact rows must treat the value as absent rather than dial
        // or mail a literal "null".
        assertTrue(isBlank(resident.getPhoneNumber()));
        assertTrue(isBlank(resident.getMessengerLink()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
