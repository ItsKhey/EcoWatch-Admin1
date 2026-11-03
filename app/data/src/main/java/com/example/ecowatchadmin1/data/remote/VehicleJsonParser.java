package com.example.ecowatchadmin1.data.remote;

import androidx.annotation.Nullable;

import com.example.ecowatchadmin1.data.models.Vehicle;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Response adapter for the vehicle fleet API.
 *
 * <p>The backend sends camelCase, but the snake_case names are accepted too, the
 * same way {@link PersonnelJsonParser} does, so the admin client keeps painting
 * the fleet during a rolling deployment where the two spellings briefly coexist.
 */
public final class VehicleJsonParser {
    private VehicleJsonParser() {
    }

    /**
     * Reads the list response.
     *
     * <p>Accepts a bare array, {@code {vehicles: []}}, or {@code {data: {vehicles:
     * []}}}, because a fleet endpoint reached through a proxy envelope should not
     * blank the screen.
     */
    public static List<Vehicle> parseList(JsonElement payload) {
        JsonElement collection = findCollection(payload);
        if (collection == null || !collection.isJsonArray()) {
            if (collection != null && collection.isJsonObject()) {
                Vehicle single = parseVehicle(collection);
                return single == null ? Collections.emptyList() : Collections.singletonList(single);
            }
            return Collections.emptyList();
        }

        List<Vehicle> vehicles = new ArrayList<>();
        for (JsonElement element : collection.getAsJsonArray()) {
            if (element == null || !element.isJsonObject()) continue;
            Vehicle vehicle = parseVehicle(element.getAsJsonObject());
            if (vehicle != null) vehicles.add(vehicle);
        }
        return vehicles;
    }

    @Nullable
    public static Vehicle parseVehicle(JsonElement payload) {
        if (payload == null || !payload.isJsonObject()) return null;
        return parseVehicle(findVehicleObject(payload.getAsJsonObject()));
    }

    /**
     * Reads the summary counts behind the Total / Available / Assigned cards.
     *
     * <p>Returns zeroes for anything missing rather than nulls, so a partial
     * payload paints a plausible screen instead of crashing the cards.
     */
    public static VehicleCounts parseCounts(JsonElement payload) {
        VehicleCounts counts = new VehicleCounts();
        if (payload == null || !payload.isJsonObject()) return counts;

        JsonObject object = payload.getAsJsonObject();
        for (String key : new String[]{"counts", "vehicles", "statistics", "stats", "data"}) {
            JsonObject nested = object.get(key) != null && object.get(key).isJsonObject()
                    ? object.getAsJsonObject(key) : null;
            if (nested != null) {
                counts.setTotal(readInt(nested, "total", "totalVehicles", "total_vehicles"));
                counts.setAvailable(readInt(nested, "available", "availableCount", "available_count"));
                counts.setAssigned(readInt(nested, "assigned", "assignedCount", "assigned_count"));
                counts.setMaintenance(readInt(nested, "maintenance", "maintenanceCount", "maintenance_count"));
                if (counts.getTotal() > 0 || counts.getAvailable() > 0
                        || counts.getAssigned() > 0 || counts.getMaintenance() > 0) {
                    return counts;
                }
            }
        }

        counts.setTotal(readInt(object, "total", "totalVehicles", "total_vehicles"));
        counts.setAvailable(readInt(object, "available", "availableCount", "available_count"));
        counts.setAssigned(readInt(object, "assigned", "assignedCount", "assigned_count"));
        counts.setMaintenance(readInt(object, "maintenance", "maintenanceCount", "maintenance_count"));
        return counts;
    }

    @Nullable
    private static Vehicle parseVehicle(JsonObject object) {
        if (object == null) return null;

        String vehicleId = readString(object, "vehicleId", "vehicle_id", "id");
        String plateNumber = readString(object, "plateNumber", "plate_number");
        if (vehicleId == null && plateNumber == null) return null;

        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(vehicleId);
        vehicle.setPlateNumber(plateNumber);
        vehicle.setVehicleType(readString(object, "vehicleType", "vehicle_type", "type"));
        vehicle.setRatedLoadCapacity(readString(object,
                "ratedLoadCapacity", "rated_load_capacity", "loadCapacity"));
        vehicle.setStatus(readString(object, "status"));
        vehicle.setCurrentReportId(readString(object,
                "currentReportId", "current_report_id"));
        vehicle.setCurrentClusterId(readString(object,
                "currentClusterId", "current_cluster_id"));
        vehicle.setAssignedAt(readString(object, "assignedAt", "assigned_at"));
        vehicle.setLastServiceAt(readString(object,
                "lastServiceAt", "last_service_at"));
        vehicle.setLastActivityAt(readString(object,
                "lastActivityAt", "last_activity_at"));
        vehicle.setNotes(readString(object, "notes"));
        vehicle.setCreatedAt(readString(object, "createdAt", "created_at"));
        vehicle.setUpdatedAt(readString(object, "updatedAt", "updated_at"));

        // The collection the vehicle is on arrives as a nested object, which is
        // what lets the card say what the truck is actually doing without a
        // second request per row.
        JsonObject report = readObject(object, "currentReport", "current_report");
        if (report != null) {
            vehicle.setCurrentReportTitle(readString(report, "title"));
            vehicle.setCurrentReportStatus(readString(report, "status"));
            vehicle.setCurrentReportLatitude(readDouble(report, "latitude"));
            vehicle.setCurrentReportLongitude(readDouble(report, "longitude"));
        }

        return vehicle;
    }

    @Nullable
    private static JsonElement findCollection(JsonElement payload) {
        if (payload == null) return null;
        if (payload.isJsonArray()) return payload;
        if (!payload.isJsonObject()) return null;

        JsonObject object = payload.getAsJsonObject();
        for (String key : new String[]{"vehicles", "data", "result", "items"}) {
            JsonElement nested = object.get(key);
            if (nested == null) continue;
            if (nested.isJsonArray()) return nested;
            if (nested.isJsonObject()) {
                JsonObject inner = nested.getAsJsonObject();
                for (String innerKey : new String[]{"vehicles", "items"}) {
                    JsonElement innerArray = inner.get(innerKey);
                    if (innerArray != null && innerArray.isJsonArray()) return innerArray;
                }
                // A single-vehicle envelope, which the create and update routes
                // return.
                if (looksLikeVehicle(inner)) return inner;
            }
        }
        if (looksLikeVehicle(object)) return object;
        return null;
    }

    @Nullable
    private static JsonObject findVehicleObject(JsonObject object) {
        for (String key : new String[]{"vehicle", "data", "result"}) {
            JsonObject nested = readObject(object, key);
            if (nested != null && looksLikeVehicle(nested)) return nested;
        }
        return looksLikeVehicle(object) ? object : null;
    }

    private static boolean looksLikeVehicle(JsonObject object) {
        return object != null
                && (object.has("vehicleId") || object.has("vehicle_id")
                || object.has("plateNumber") || object.has("plate_number"));
    }

    private static int readInt(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element == null || element.isJsonNull()) continue;
            try {
                return element.getAsInt();
            } catch (RuntimeException ignored) {
                // Not a number; try the next spelling.
            }
        }
        return 0;
    }

    @Nullable
    private static String readString(JsonObject object, String... keys) {
        if (object == null) return null;
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) continue;
            String value = element.getAsString();
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    @Nullable
    private static Double readDouble(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return null;
        try {
            return element.getAsDouble();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static JsonObject readObject(JsonObject object, String... keys) {
        if (object == null) return null;
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && element.isJsonObject()) return element.getAsJsonObject();
        }
        return null;
    }

    /** The three card numbers, plus Maintenance for the fleet's own completeness. */
    public static final class VehicleCounts {
        private int total;
        private int available;
        private int assigned;
        private int maintenance;

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }

        public int getAvailable() { return available; }
        public void setAvailable(int available) { this.available = available; }

        public int getAssigned() { return assigned; }
        public void setAssigned(int assigned) { this.assigned = assigned; }

        public int getMaintenance() { return maintenance; }
        public void setMaintenance(int maintenance) { this.maintenance = maintenance; }
    }
}
