package com.example.ecowatchadmin1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.data.remote.PersonnelCreateRequest;
import com.example.ecowatchadmin1.data.remote.PersonnelJsonParser;
import com.google.gson.Gson;
import com.google.gson.JsonParser;

import org.junit.Test;

/**
 * A cleaner either walks the route or drives one of the trucks.
 *
 * <p>Two occupations rather than a free-text role: a role nobody can filter on is
 * a role the fleet cannot be matched against. Driver then has to carry a vehicle,
 * because a driver without a truck is an account the dispatch list cannot use.
 *
 * <p>These cover the two halves of that rule: the response side, where a driver
 * has to arrive with an occupation the card can read and a truck to name, and the
 * request side, where the assignment has to travel with the create instead of
 * following it.
 */
public class PersonnelOccupationTest {

    private final Gson gson = new Gson();

    /** Mirrors the registration call the dialog makes for a regular cleaner. */
    private PersonnelCreateRequest regularRegistration() {
        return new PersonnelCreateRequest("Ana", "Reyes", "Quezon City",
                "ana@gmail.com", "ana.reyes", "password123",
                "09171234567", "https://m.me/ana", Personnel.OCCUPATION_REGULAR, null);
    }

    /** Mirrors the registration call the dialog makes for a driver. */
    private PersonnelCreateRequest driverRegistration(String vehicleId) {
        return new PersonnelCreateRequest("Mara", "Santos", "Caloocan",
                "mara@gmail.com", "mara.santos", "password123",
                "09181234567", "https://m.me/mara", Personnel.OCCUPATION_DRIVER, vehicleId);
    }

    @Test
    public void aRegularCleanerIsNotADriver() {
        Personnel person = PersonnelJsonParser.parsePersonnel(new JsonParser().parse(
                "{\"personnelId\":\"PERS-0001\",\"firstName\":\"Ana\",\"lastName\":\"Reyes\","
                        + "\"username\":\"ana.reyes\",\"occupation\":\"Regular\"}"));

        assertFalse(person.isDriver());
        assertEquals(Personnel.OCCUPATION_REGULAR, person.getOccupationLabel());
        assertFalse(person.hasAssignedVehicle());
    }

    @Test
    public void aDriverKeepsTheTruckTheyWereGiven() {
        Personnel person = PersonnelJsonParser.parsePersonnel(new JsonParser().parse(
                "{\"personnelId\":\"PERS-0002\",\"firstName\":\"Mara\",\"lastName\":\"Santos\","
                        + "\"username\":\"mara.santos\",\"occupation\":\"Driver\","
                        + "\"assignedVehicleId\":\"VEH-0007\","
                        + "\"assignedVehiclePlateNumber\":\"ABC-123\"}"));

        assertTrue(person.isDriver());
        assertTrue(person.hasAssignedVehicle());
        assertEquals("VEH-0007", person.getAssignedVehicleId());
        assertEquals("ABC-123", person.getAssignedVehiclePlateNumber());
    }

    /**
     * A deployment that nests the vehicle rather than flattening it must still
     * produce a card, or the same account would read differently depending on
     * which server answered.
     */
    @Test
    public void aNestedVehicleObjectIsReadToo() {
        Personnel person = PersonnelJsonParser.parsePersonnel(new JsonParser().parse(
                "{\"personnelId\":\"PERS-0003\",\"firstName\":\"Ben\",\"lastName\":\"Cruz\","
                        + "\"username\":\"ben.cruz\",\"occupation\":\"Driver\","
                        + "\"assignedVehicle\":{\"vehicleId\":\"VEH-0009\","
                        + "\"plateNumber\":\"XYZ-789\"}}"));

        assertTrue(person.isDriver());
        assertEquals("VEH-0009", person.getAssignedVehicleId());
        assertEquals("XYZ-789", person.getAssignedVehiclePlateNumber());
    }

    /**
     * An account carrying a truck is a driver whatever the occupation column says.
     * Preferring the vehicle is what stops a server that stores only the
     * assignment from painting the truck on a card that claims nobody drives.
     */
    @Test
    public void aVehicleAloneImpliesTheDriverOccupation() {
        Personnel person = PersonnelJsonParser.parsePersonnel(new JsonParser().parse(
                "{\"personnelId\":\"PERS-0004\",\"firstName\":\"Liza\",\"lastName\":\"Reyes\","
                        + "\"username\":\"liza.reyes\",\"vehicleId\":\"VEH-0011\"}"));

        assertTrue(person.isDriver());
        assertTrue(person.hasAssignedVehicle());
    }

    /** An account with no occupation at all is a cleaner, not an unknown state. */
    @Test
    public void anAbsentOccupationReadsAsRegular() {
        Personnel person = PersonnelJsonParser.parsePersonnel(new JsonParser().parse(
                "{\"personnelId\":\"PERS-0005\",\"firstName\":\"Ana\",\"lastName\":\"Reyes\","
                        + "\"username\":\"ana.reyes\"}"));

        assertFalse(person.isDriver());
        assertNull(person.getOccupation());
        assertFalse(person.hasAssignedVehicle());
    }

    @Test
    public void registrationCarriesTheOccupation() {
        String json = gson.toJson(regularRegistration());

        assertTrue(json.contains("\"occupation\":\"Regular\""));
    }

    /**
     * A cleaner has no truck, and Gson drops null fields, so the wire carries an
     * assignment only when the administrator actually made one rather than an
     * empty value the server would store as a real assignment.
     */
    @Test
    public void aRegularCleanerSendsNoVehicle() {
        String json = gson.toJson(regularRegistration());

        assertFalse(json.contains("assignedVehicleId"));
        assertFalse(json.contains("vehicleId"));
    }

    /** A driver is registered with the truck, on the same create rather than after. */
    @Test
    public void aDriverSendsTheVehicleOnTheSameCreate() {
        String json = gson.toJson(driverRegistration("VEH-0007"));

        assertTrue(json.contains("\"occupation\":\"Driver\""));
        assertTrue(json.contains("\"assignedVehicleId\":\"VEH-0007\""));
    }
}