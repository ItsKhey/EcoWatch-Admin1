package com.example.ecowatchadmin1.ui.personnel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchadmin1.data.models.Vehicle;
import com.example.ecowatchadmin1.data.remote.VehicleCreateRequest;
import com.google.gson.Gson;

import org.junit.Test;

/**
 * Registering a vehicle asks what the truck is, not how it is doing.
 *
 * <p>The add form deliberately offers only plate number, vehicle type and rated
 * load capacity. Status and notes are lifecycle facts, and a vehicle joining the
 * fleet has neither yet: it arrives Available with nothing written up. Offering
 * those two fields here could only ever produce the one answer the server
 * accepts, so they belong on the edit path instead.
 *
 * <p>Gson omits null fields, which is what makes "send nothing" and "send
 * nothing" mean the same thing on both sides of the wire.
 */
public class VehicleRegistrationPayloadTest {

    private final Gson gson = new Gson();

    /** Mirrors the registration call the add form makes. */
    private VehicleCreateRequest registration(String plate, String type, String ratedLoad) {
        return new VehicleCreateRequest(plate, type, ratedLoad, Vehicle.STATUS_AVAILABLE, null);
    }

    @Test
    public void registrationSendsOnlyIdentityFields() {
        String json = gson.toJson(registration("ABC-123", "Mini dump truck", "1.5 tons"));

        assertTrue(json.contains("\"plateNumber\":\"ABC-123\""));
        assertTrue(json.contains("\"vehicleType\":\"Mini dump truck\""));
        assertTrue(json.contains("\"ratedLoadCapacity\":\"1.5 tons\""));
    }

    /**
     * Notes are not part of registration. A null notes field must vanish from the
     * wire entirely rather than travel as an empty string the server would store
     * as a real (if empty) value.
     */
    @Test
    public void registrationOmitsNotesEntirely() {
        String json = gson.toJson(registration("ABC-123", "Mini dump truck", null));

        assertFalse("notes must not be sent on registration", json.contains("notes"));
    }

    /**
     * A new vehicle is Available. The server refuses anything else on a create, so
     * the form sends the one accepted value rather than leaving it unset.
     */
    @Test
    public void registrationAlwaysArrivesAvailable() {
        String json = gson.toJson(registration("ABC-123", "Garbage truck", null));

        assertTrue(json.contains("\"status\":\"" + Vehicle.STATUS_AVAILABLE + "\""));
    }

    /** Rated load stays optional: a truck with no recorded capacity is valid. */
    @Test
    public void anAbsentRatedLoadIsOmittedRatherThanBlank() {
        String json = gson.toJson(registration("ABC-123", "Garbage truck", null));

        assertFalse(json.contains("ratedLoadCapacity"));
    }

    @Test
    public void aFullyDescribedRegistrationCarriesEveryField() {
        String json = gson.toJson(registration("XYZ-789", "Garbage truck", "5 tons"));

        assertTrue(json.contains("\"plateNumber\":\"XYZ-789\""));
        assertTrue(json.contains("\"vehicleType\":\"Garbage truck\""));
        assertTrue(json.contains("\"ratedLoadCapacity\":\"5 tons\""));
        assertTrue(json.contains("\"status\":\"Available\""));
        assertFalse(json.contains("notes"));
    }

    /**
     * The edit path is where a truck picks up a status and notes, so it has to be
     * able to send values the add form never collects.
     */
    @Test
    public void anEditCanStillCarryStatusAndNotes() {
        Vehicle truck = new Vehicle();
        truck.setVehicleId("VEH-0001");
        truck.setPlateNumber("ABC-123");
        truck.setStatus(Vehicle.STATUS_MAINTENANCE);
        truck.setNotes("Brake pads replaced");

        assertTrue(truck.isInMaintenance());
        assertEquals("Brake pads replaced", truck.getNotes());
        assertEquals("VEH-0001", truck.getVehicleId());
    }

    /** A truck that has never been written up shows no notes rather than a blank. */
    @Test
    public void aFreshVehicleHasNoNotesToShow() {
        Vehicle truck = new Vehicle();

        assertNull(truck.getNotes());
        assertTrue(truck.isAvailable());
    }
}
