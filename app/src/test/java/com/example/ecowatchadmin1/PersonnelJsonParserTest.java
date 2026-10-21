package com.example.ecowatchadmin1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.data.remote.PersonnelJsonParser;
import com.example.ecowatchadmin1.data.remote.PersonnelPasswordRequest;
import com.google.gson.Gson;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.List;

public class PersonnelJsonParserTest {
    @Test
    public void parsesNestedAssignmentAndFlags() {
        String json = "{\"personnel\":[{\"personnelId\":\"PERS-0001\","
                + "\"firstName\":\"Ana\",\"lastName\":\"Reyes\",\"gmail\":\"ana@gmail.com\","
                + "\"username\":\"ana.reyes\",\"online\":true,\"cleaning\":true,\"enabled\":true,"
                + "\"currentAssignment\":{\"reportId\":\"REP-42\",\"title\":\"North Park cleanup\","
                + "\"latitude\":14.6,\"longitude\":120.9}}]}";

        List<Personnel> people = PersonnelJsonParser.parseList(
                new JsonParser().parse(json));

        assertEquals(1, people.size());
        Personnel person = people.get(0);
        assertEquals("PERS-0001", person.getPersonnelId());
        assertEquals("Ana Reyes", person.getFullName());
        assertFalse(person.isOnline());
        assertTrue(person.isCleaning());
        assertEquals("REP-42", person.getAssignmentReportId());
        assertEquals("North Park cleanup", person.getCurrentTask());
        assertTrue(person.hasAssignmentLocation());
    }

    @Test
    public void parsesBackendMapShape() {
        String json = "{\"success\":true,\"personnel\":[{\"personnelId\":\"PERS-0010\","
                + "\"firstName\":\"Mara\",\"lastName\":\"Santos\",\"email\":\"mara@gmail.com\","
                + "\"username\":\"mara.santos\",\"status\":\"CLEANING\","
                + "\"isOnline\":false,\"isCleaning\":true,\"isActive\":true,\"enabled\":true,"
                + "\"currentAssignment\":{\"reportId\":\"REP-99\",\"title\":\"Canal cleanup\","
                + "\"latitude\":14.7,\"longitude\":121.0}}]}";

        Personnel person = PersonnelJsonParser.parsePersonnel(new JsonParser().parse(json));

        assertTrue(person.isEnabled());
        assertTrue(person.isCleaning());
        assertFalse(person.isOnline());
        assertEquals("Canal cleanup", person.getCurrentTask());
        assertEquals(14.7, person.getAssignmentLatitude(), 0.0001);
    }

    @Test
    public void respectsExplicitDisabledFlag() {
        String json = "{\"data\":{\"account\":{\"id\":\"PERS-0002\","
                + "\"fullName\":\"Disabled User\",\"username\":\"disabled\","
                + "\"email\":\"disabled@gmail.com\",\"enabled\":false,"
                + "\"status\":\"DISABLED\"}}}";

        Personnel person = PersonnelJsonParser.parsePersonnel(new JsonParser().parse(json));

        assertFalse(person.isEnabled());
        assertEquals("Disabled", person.getStatus());
    }

    @Test
    public void resetRequestDoesNotTreatNewPasswordAsAdminPassword() {
        com.google.gson.JsonObject body = new Gson().toJsonTree(
                new PersonnelPasswordRequest("StrongPass123")).getAsJsonObject();

        assertTrue(body.has("newPassword"));
        assertFalse(body.has("password"));
    }

    @Test
    public void parsesProfileImageAndBlankTaskFallback() {
        String json = "{\"personnelId\":\"PERS-0011\","
                + "\"firstName\":\"Lee\",\"lastName\":\"Cruz\","
                + "\"profileImageUrl\":\"https://example.com/lee.png\","
                + "\"currentTask\":\"\",\"currentAssignment\":\"REP-77\"}";

        Personnel person = PersonnelJsonParser.parsePersonnel(new JsonParser().parse(json));

        assertEquals("https://example.com/lee.png", person.getProfileImageUrl());
        assertEquals("REP-77", person.getCurrentTask());
    }

    @Test
    public void disabledFlagWinsOverOperationalStatus() {
        String json = "{\"personnelId\":\"PERS-0012\","
                + "\"firstName\":\"Disabled\",\"lastName\":\"Worker\","
                + "\"status\":\"CLEANING\",\"isDisabled\":true}";

        Personnel person = PersonnelJsonParser.parsePersonnel(new JsonParser().parse(json));

        assertFalse(person.isEnabled());
        assertFalse(person.isCleaning());
    }

    @Test
    public void parsesTemporaryPasswordWithoutReturningHash() {
        String json = "{\"success\":true,\"personnel\":{\"personnelId\":\"PERS-0003\","
                + "\"firstName\":\"Lee\",\"lastName\":\"Cruz\",\"username\":\"lee\","
                + "\"gmail\":\"lee@gmail.com\"},\"temporaryPassword\":\"StrongPass123\"}";

        PersonnelJsonParser.MutationResult result = PersonnelJsonParser.parseMutation(
                new JsonParser().parse(json), "fallback");

        assertEquals("PERS-0003", result.getPersonnel().getPersonnelId());
        assertEquals("StrongPass123", result.getPassword());
    }

    @Test
    public void parsesContactFieldsFromCamelCasePayload() {
        String json = "{\"personnelId\":\"PERS-0020\","
                + "\"firstName\":\"Ana\",\"lastName\":\"Reyes\","
                + "\"phoneNumber\":\"+63 917 000 0000\","
                + "\"messengerLink\":\"https://m.me/anareyes\"}";

        Personnel person = PersonnelJsonParser.parsePersonnel(new JsonParser().parse(json));

        assertEquals("+63 917 000 0000", person.getPhoneNumber());
        assertEquals("https://m.me/anareyes", person.getMessengerLink());
    }

    @Test
    public void parsesContactFieldsFromSnakeCasePayload() {
        String json = "{\"personnel_id\":\"PERS-0021\","
                + "\"phone_number\":\"09170000001\",\"messenger_link\":\"https://m.me/bob\"}";

        Personnel person = PersonnelJsonParser.parsePersonnel(new JsonParser().parse(json));

        assertEquals("09170000001", person.getPhoneNumber());
        assertEquals("https://m.me/bob", person.getMessengerLink());
    }

    @Test
    public void absentContactFieldsReadAsBlankSoRowsStayDisabled() {
        String json = "{\"personnelId\":\"PERS-0022\","
                + "\"firstName\":\"No\",\"lastName\":\"Contact\"}";

        Personnel person = PersonnelJsonParser.parsePersonnel(new JsonParser().parse(json));

        // The API sends "" for these, but an older payload can omit them. Either
        // way the contact rows must treat the value as absent rather than dial
        // or mail a literal "null".
        assertTrue(isBlank(person.getPhoneNumber()));
        assertTrue(isBlank(person.getMessengerLink()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
