package com.example.ecowatchadmin1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchadmin1.data.models.Vehicle;
import com.example.ecowatchadmin1.data.remote.VehicleJsonParser;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.List;

/**
 * The vehicle fleet as the Admin screen reads it.
 *
 * <p>Two things are pinned here. The first is the list envelope: the endpoint
 * answers {@code {vehicles: [], counts: {}}} but the create and update routes
 * answer a bare object, and the same screen reads all three. The second is the
 * status vocabulary, because Maintenance is a state of its own rather than a flag
 * alongside Assigned, and a card that could show a truck as both would make the
 * Available count a lie.
 */
public class VehicleFleetTest {

    private static final String ONE_VEHICLE = "{"
            + "\"vehicleId\":\"VEH-0001\","
            + "\"plateNumber\":\"ABC-123\","
            + "\"vehicleType\":\"Mini dump truck\","
            + "\"ratedLoadCapacity\":\"1.5 tons\","
            + "\"status\":\"Available\","
            + "\"currentReportId\":null,"
            + "\"lastActivityAt\":\"2026-10-09T03:00:00.000Z\""
            + "}";

    private static VehicleJsonParser.VehicleCounts countsOf(String json) {
        return VehicleJsonParser.parseCounts(new JsonParser().parse(json));
    }

    @Test
    public void theListEnvelopeYieldsEveryVehicle() {
        String json = "{\"vehicles\":[" + ONE_VEHICLE + "],"
                + "\"counts\":{\"total\":1,\"available\":1,\"assigned\":0,\"maintenance\":0},"
                + "\"filter\":\"All\"}";

        List<Vehicle> vehicles = VehicleJsonParser.parseList(new JsonParser().parse(json));

        assertEquals(1, vehicles.size());
        assertEquals("VEH-0001", vehicles.get(0).getVehicleId());
        assertEquals("ABC-123", vehicles.get(0).getPlateNumber());
        assertEquals("Mini dump truck", vehicles.get(0).getVehicleType());
        assertEquals("1.5 tons", vehicles.get(0).getRatedLoadCapacity());
        assertEquals(Vehicle.STATUS_AVAILABLE, vehicles.get(0).getStatus());
    }

    @Test
    public void theCardCountsRideAlongWithTheList() {
        String json = "{\"vehicles\":[],"
                + "\"counts\":{\"total\":9,\"available\":5,\"assigned\":3,\"maintenance\":1}}";

        VehicleJsonParser.VehicleCounts counts = countsOf(json);

        assertEquals(9, counts.getTotal());
        assertEquals(5, counts.getAvailable());
        assertEquals(3, counts.getAssigned());
        assertEquals(1, counts.getMaintenance());
        assertEquals("Total is the sum of the three states",
                counts.getTotal(), counts.getAvailable() + counts.getAssigned() + counts.getMaintenance());
    }

    /** A create or update answers a bare object, not the list envelope. */
    @Test
    public void aSingleVehicleIsReadFromTheMutationRoutes() {
        Vehicle vehicle = VehicleJsonParser.parseVehicle(new JsonParser().parse(ONE_VEHICLE));

        assertNotNull(vehicle);
        assertEquals("VEH-0001", vehicle.getVehicleId());
    }

    @Test
    public void aWrappedSingleVehicleIsAlsoRead() {
        Vehicle vehicle = VehicleJsonParser.parseVehicle(
                new JsonParser().parse("{\"vehicle\":" + ONE_VEHICLE + "}"));

        assertNotNull(vehicle);
        assertEquals("VEH-0001", vehicle.getVehicleId());
    }

    /** The snake_case spelling is what older deployments answer. */
    @Test
    public void snakeCaseIsAcceptedAlongsideCamelCase() {
        String json = "{\"vehicles\":[{\"vehicle_id\":\"VEH-0002\","
                + "\"plate_number\":\"XYZ-789\",\"vehicle_type\":\"Garbage truck\","
                + "\"rated_load_capacity\":\"5 tons\",\"status\":\"Maintenance\"}]}";

        List<Vehicle> vehicles = VehicleJsonParser.parseList(new JsonParser().parse(json));

        assertEquals(1, vehicles.size());
        assertEquals("VEH-0002", vehicles.get(0).getVehicleId());
        assertEquals("XYZ-789", vehicles.get(0).getPlateNumber());
        assertEquals("5 tons", vehicles.get(0).getRatedLoadCapacity());
        assertEquals(Vehicle.STATUS_MAINTENANCE, vehicles.get(0).getStatus());
    }

    /**
     * The nested collection is what lets the card say what the truck is doing
     * without a second request per row.
     */
    @Test
    public void theCurrentCollectionComesAlongWithTheVehicle() {
        String json = "{\"vehicles\":[{"
                + "\"vehicleId\":\"VEH-0003\",\"plateNumber\":\"COL-1\","
                + "\"status\":\"Assigned\",\"currentReportId\":\"REP-ABC\","
                + "\"currentReport\":{\"title\":\"Corner at Mabini\",\"status\":\"In Progress\","
                + "\"latitude\":14.5995,\"longitude\":120.9842}}]}";

        Vehicle vehicle = VehicleJsonParser.parseList(new JsonParser().parse(json)).get(0);

        assertTrue(vehicle.isAssigned());
        assertEquals("Corner at Mabini", vehicle.getCurrentCollection());
        assertEquals("REP-ABC", vehicle.getCurrentReportId());
        assertEquals("In Progress", vehicle.getCurrentReportStatus());
        assertEquals(14.5995, vehicle.getCurrentReportLatitude(), 0.0001);
    }

    @Test
    public void anUnassignedVehicleHasNoCollection() {
        Vehicle vehicle = VehicleJsonParser.parseVehicle(new JsonParser().parse(ONE_VEHICLE));

        assertNull(vehicle.getCurrentCollection());
        assertFalse(vehicle.isAssigned());
    }

    /**
     * Maintenance is its own status, so a truck under servicing is never also
     * counted as out on a collection.
     */
    @Test
    public void maintenanceIsNotAlsoAssigned() {
        Vehicle vehicle = new Vehicle();
        vehicle.setStatus(Vehicle.STATUS_MAINTENANCE);

        assertTrue(vehicle.isInMaintenance());
        assertFalse(vehicle.isAssigned());
        assertFalse(vehicle.isAvailable());
    }

    @Test
    public void theThreeStatesAreMutuallyExclusive() {
        Vehicle vehicle = new Vehicle();

        vehicle.setStatus(Vehicle.STATUS_AVAILABLE);
        assertTrue(vehicle.isAvailable());
        assertFalse(vehicle.isInMaintenance());
        assertFalse(vehicle.isAssigned());

        vehicle.setStatus(Vehicle.STATUS_ASSIGNED);
        assertTrue(vehicle.isAssigned());
        assertFalse(vehicle.isAvailable());
        assertFalse(vehicle.isInMaintenance());
    }

    /**
     * An unrecognised status reads as Available rather than blowing up. The card
     * then shows a wrong colour instead of the list failing to paint at all.
     */
    @Test
    public void anUnknownStatusStillProducesAReadableVehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setStatus("Retired");

        assertEquals(Vehicle.STATUS_AVAILABLE, vehicle.getStatusLabel());
    }

    @Test
    public void aBlankStatusDefaultsToAvailable() {
        Vehicle vehicle = new Vehicle();
        vehicle.setStatus("   ");

        assertEquals(Vehicle.STATUS_AVAILABLE, vehicle.getStatusLabel());
    }

    @Test
    public void theSearchMatchesIdPlateTypeAndStatus() {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId("VEH-0042");
        vehicle.setPlateNumber("ABC-123");
        vehicle.setVehicleType("Mini dump truck");
        vehicle.setStatus(Vehicle.STATUS_ASSIGNED);

        assertTrue(vehicle.matchesQuery("VEH-0042"));
        assertTrue(vehicle.matchesQuery("abc-123"));
        assertTrue(vehicle.matchesQuery("dump"));
        assertTrue(vehicle.matchesQuery("assigned"));
        assertFalse(vehicle.matchesQuery("ZZZ"));
        assertTrue("an empty query matches everything", vehicle.matchesQuery(""));
    }

    @Test
    public void aRatedLoadIsOptionalAndBlankBecomesAbsent() {
        Vehicle vehicle = new Vehicle();
        vehicle.setRatedLoadCapacity("  ");

        assertNull(vehicle.getRatedLoadLabel());
    }

    @Test
    public void anEmptyPayloadIsNotMistakenForAVehicle() {
        assertNull(VehicleJsonParser.parseVehicle(new JsonParser().parse("{}")));
        assertTrue(VehicleJsonParser.parseList(new JsonParser().parse("{}")).isEmpty());
        assertTrue(VehicleJsonParser.parseList(new JsonParser().parse("null")).isEmpty());
    }

    /** Missing counts paint zeroes rather than crashing the cards. */
    @Test
    public void missingCountsReadAsZero() {
        VehicleJsonParser.VehicleCounts counts = countsOf("{}");

        assertEquals(0, counts.getTotal());
        assertEquals(0, counts.getAvailable());
        assertEquals(0, counts.getAssigned());
        assertEquals(0, counts.getMaintenance());
    }
}
