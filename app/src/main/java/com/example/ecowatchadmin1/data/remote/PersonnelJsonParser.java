package com.example.ecowatchadmin1.data.remote;

import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.data.models.PersonnelTaskCounts;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Response adapter for the personnel API. The backend uses camelCase JSON, but
 * accepting the legacy snake_case names here keeps the admin client resilient
 * during a rolling Render deployment.
 */
public final class PersonnelJsonParser {
    private PersonnelJsonParser() {
    }

    public static List<Personnel> parseList(JsonElement payload) {
        JsonElement collection = findCollection(payload);
        if (collection == null || !collection.isJsonArray()) {
            if (collection != null && collection.isJsonObject() && looksLikePersonnel(collection.getAsJsonObject())) {
                Personnel person = parsePersonnel(collection.getAsJsonObject());
                return person == null ? Collections.emptyList() : Collections.singletonList(person);
            }
            return Collections.emptyList();
        }

        List<Personnel> people = new ArrayList<>();
        for (JsonElement element : collection.getAsJsonArray()) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            Personnel person = parsePersonnel(element.getAsJsonObject());
            if (person != null) {
                people.add(person);
            }
        }
        return people;
    }

    public static Personnel parsePersonnel(JsonElement payload) {
        JsonObject object = findPersonnelObject(payload);
        return object == null ? null : parsePersonnel(object);
    }

    /**
     * Reads the assignment-derived task counts.
     *
     * <p>Accepts the {@code counts}/{@code taskCounts}/{@code statistics} envelope
     * keys and both camelCase and snake_case field names, so a deployment that
     * only exposes one of the two spellings still paints the cards.
     */
    public static PersonnelTaskCounts parseTaskCounts(JsonElement payload) {
        PersonnelTaskCounts counts = new PersonnelTaskCounts();
        JsonObject source = findCountsObject(payload);
        if (source == null) return counts;

        counts.setTotalAssigned(readInt(source,
                "totalAssigned", "total_assigned", "total", "allTime", "all_time"));
        counts.setOnHand(readInt(source, "onHand", "on_hand", "active"));
        counts.setInProgress(readInt(source, "inProgress", "in_progress"));
        counts.setCompleted(readInt(source, "completed", "completedCount", "completed_count"));
        counts.setAssigned(readInt(source, "assigned", "assignedCount", "assigned_count"));
        counts.setAccepted(readInt(source, "accepted", "acceptedCount", "accepted_count"));
        counts.setCancelled(readInt(source, "cancelled", "canceled", "cancelledCount"));
        return counts;
    }

    private static JsonObject findCountsObject(JsonElement payload) {
        if (payload == null || !payload.isJsonObject()) return null;
        JsonObject object = payload.getAsJsonObject();
        for (String key : new String[]{"counts", "taskCounts", "task_counts", "statistics", "stats"}) {
            JsonObject nested = findObject(object, key);
            if (nested != null) return nested;
        }
        if (object.has("totalAssigned") || object.has("total_assigned")
                || object.has("onHand") || object.has("on_hand")
                || object.has("inProgress") || object.has("in_progress")
                || object.has("completed")) {
            return object;
        }
        for (String key : new String[]{"data", "result"}) {
            JsonElement nested = object.get(key);
            if (nested != null && nested.isJsonObject()) {
                JsonObject found = findCountsObject(nested);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int readInt(JsonObject object, String... keys) {
        if (object == null) return 0;
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value == null || !value.isJsonPrimitive()) continue;
            try {
                return Math.max(0, value.getAsInt());
            } catch (RuntimeException ignored) {
                try {
                    return Math.max(0, (int) Double.parseDouble(value.getAsString()));
                } catch (RuntimeException ignoredAgain) {
                    // Try the next spelling.
                }
            }
        }
        return 0;
    }

    public static MutationResult parseMutation(JsonElement payload, String fallbackPassword) {
        if (payload == null || payload.isJsonNull()) {
            return new MutationResult(null, fallbackPassword);
        }

        Personnel person = parsePersonnel(payload);
        String password = readStringRecursively(payload,
                "temporaryPassword", "temporary_password", "plainPassword", "plain_password",
                "password", "newPassword", "new_password");
        if (password == null || password.trim().isEmpty()) {
            password = fallbackPassword;
        }
        return new MutationResult(person, password);
    }

    private static Personnel parsePersonnel(JsonObject source) {
        if (source == null || !looksLikePersonnel(source)) {
            return null;
        }

        Personnel person = new Personnel();
        person.setPersonnelId(firstNonBlank(
                readString(source, "personnelId", "personnel_id", "id", "accountId", "account_id"),
                readString(source, "personnelID")));
        person.setFirstName(readString(source, "firstName", "first_name"));
        person.setLastName(readString(source, "lastName", "last_name"));
        person.setFullName(readString(source, "fullName", "full_name", "name"));
        person.setGmail(readString(source, "gmail", "email"));
        person.setUsername(readString(source, "username", "userName", "user_name"));
        person.setAddress(readString(source, "address"));
        person.setPhoneNumber(readString(source, "phoneNumber", "phone_number"));
        person.setMessengerLink(readString(source, "messengerLink", "messenger_link"));
        person.setProfileImageUrl(readString(source,
                "profileImage", "profile_image", "profileImageUrl", "profile_image_url",
                "avatarUrl", "avatar_url"));
        readOccupationAndVehicle(person, source);

        String accountStatus = readString(source,
                "accountStatus", "account_status", "account_state", "accountState");
        String rawStatus = readString(source, "status", "operationalStatus", "operational_status");
        boolean hasEnabledField = hasAny(source, "enabled", "isEnabled", "is_enabled", "active", "isActive", "is_active");
        boolean explicitlyEnabled = readBoolean(source, "enabled", "isEnabled", "is_enabled", "active", "isActive", "is_active");
        boolean explicitlyDisabled = readBoolean(source, "disabled", "isDisabled", "is_disabled");
        boolean explicitlyDeleted = readBoolean(source, "deleted", "isDeleted", "is_deleted");
        boolean disabledStatus = "DISABLED".equalsIgnoreCase(accountStatus)
                || "DELETED".equalsIgnoreCase(accountStatus)
                || "INACTIVE".equalsIgnoreCase(accountStatus)
                || "BLOCKED".equalsIgnoreCase(accountStatus)
                || "SUSPENDED".equalsIgnoreCase(accountStatus)
                || "DISABLED".equalsIgnoreCase(rawStatus)
                || "DELETED".equalsIgnoreCase(rawStatus)
                || "INACTIVE".equalsIgnoreCase(rawStatus)
                || "BLOCKED".equalsIgnoreCase(rawStatus)
                || "SUSPENDED".equalsIgnoreCase(rawStatus);
        person.setEnabled(!explicitlyDeleted && !explicitlyDisabled && !disabledStatus
                && (!hasEnabledField || explicitlyEnabled));
        person.setStatus(rawStatus);

        boolean explicitOnline = readBoolean(source, "online", "isOnline", "is_online");
        boolean explicitCleaning = readBoolean(source, "cleaning", "isCleaning", "is_cleaning");
        String normalizedStatus = rawStatus == null ? "" : rawStatus.toUpperCase(Locale.US);
        person.setOnline(person.isEnabled()
                && (explicitOnline || "ONLINE".equals(normalizedStatus)
                || "ACTIVE".equals(normalizedStatus) || "AVAILABLE".equals(normalizedStatus)));
        person.setCleaning(person.isEnabled()
                && (explicitCleaning || "CLEANING".equals(normalizedStatus)
                || "IN_PROGRESS".equals(normalizedStatus) || "IN PROGRESS".equals(normalizedStatus)));

        person.setLastActive(readString(source, "lastActive", "last_active", "lastSeen", "last_seen"));

        JsonObject assignment = findObject(source,
                "currentAssignment", "current_assignment", "assignment", "currentTask", "current_task");
        String assignmentText = null;
        String reportId = null;
        Double latitude = readDoubleObject(source, "assignmentLatitude", "assignment_latitude", "latitude", "lat");
        Double longitude = readDoubleObject(source, "assignmentLongitude", "assignment_longitude", "longitude", "lng", "lon", "long");

        if (assignment != null) {
            assignmentText = firstNonBlank(
                    readString(assignment, "title", "task", "taskTitle", "name", "description", "currentTask", "current_task"),
                    readString(assignment, "reportId", "report_id", "id"));
            reportId = readString(assignment, "reportId", "report_id", "id");
            if (reportId == null || reportId.trim().isEmpty()) {
                reportId = readString(source,
                        "assignmentReportId", "assignment_report_id", "currentReportId", "current_report_id");
            }
            Double nestedLat = readDoubleObject(assignment, "latitude", "lat");
            Double nestedLng = readDoubleObject(assignment, "longitude", "lng", "lon", "long");
            if (nestedLat != null) latitude = nestedLat;
            if (nestedLng != null) longitude = nestedLng;
            JsonObject location = findObject(assignment, "location", "coordinates");
            if (location != null) {
                Double locationLat = readDoubleObject(location, "latitude", "lat");
                Double locationLng = readDoubleObject(location, "longitude", "lng", "lon", "long");
                if (locationLat != null) latitude = locationLat;
                if (locationLng != null) longitude = locationLng;
            }
            JsonElement coordinates = readElement(assignment, "coordinates");
            if (coordinates != null && coordinates.isJsonArray() && coordinates.getAsJsonArray().size() >= 2) {
                JsonArray values = coordinates.getAsJsonArray();
                longitude = readDoubleElement(values.get(0));
                latitude = readDoubleElement(values.get(1));
            }
        } else {
            assignmentText = readString(source, "currentTask", "current_task", "assignmentTitle", "assignment_title");
            reportId = readString(source, "assignmentReportId", "assignment_report_id", "currentReportId", "current_report_id");
        }
        if (assignmentText == null || assignmentText.trim().isEmpty()) {
            assignmentText = readString(source, "currentAssignment", "current_assignment");
        }
        if (assignmentText == null || assignmentText.trim().isEmpty()) {
            assignmentText = readString(source, "currentTask", "current_task");
        }
        person.setCurrentAssignment(assignmentText);
        person.setCurrentTask(assignmentText);
        person.setAssignmentReportId(reportId);
        person.setAssignmentLatitude(latitude);
        person.setAssignmentLongitude(longitude);

        person.setDateCreated(readString(source, "dateCreated", "date_created", "createdAt", "created_at"));
        person.setUpdatedAt(readString(source, "updatedAt", "updated_at"));
        return person;
    }

    /**
     * Reads the occupation and the truck a driver is assigned to.
     *
     * <p>The vehicle arrives either as two flat columns or as one nested object,
     * so both are read: a deployment that only returns the id is still enough for
     * the card to name a truck, and one that nests the vehicle is not blanked by
     * an empty top-level read.
     *
     * <p>An account carrying a vehicle but no occupation is treated as a driver.
     * The two facts cannot disagree in practice, and preferring the vehicle means
     * a server that only stores the assignment still shows the truck instead of
     * dropping the driver back to a regular cleaner.
     */
    private static void readOccupationAndVehicle(Personnel person, JsonObject source) {
        String rawOccupation = readString(source,
                "occupation", "occupationType", "occupation_type",
                "jobTitle", "job_title");
        person.setOccupation(rawOccupation);

        JsonObject vehicle = findObject(source, "assignedVehicle", "assigned_vehicle", "vehicle");
        String vehicleId = firstNonBlank(
                readString(source, "assignedVehicleId", "assigned_vehicle_id",
                        "vehicleId", "vehicle_id"),
                vehicle == null ? null : readString(vehicle, "vehicleId", "vehicle_id", "id"));
        String plateNumber = firstNonBlank(
                readString(source, "assignedVehiclePlateNumber", "assigned_vehicle_plate_number",
                        "vehiclePlateNumber", "vehicle_plate_number", "plateNumber", "plate_number"),
                vehicle == null ? null : readString(vehicle, "plateNumber", "plate_number"));

        person.setAssignedVehicleId(vehicleId);
        person.setAssignedVehiclePlateNumber(plateNumber);

        if (person.getOccupation() == null && vehicleId != null) {
            person.setOccupation(Personnel.OCCUPATION_DRIVER);
        }
    }

    private static JsonElement findCollection(JsonElement payload) {
        if (payload == null || payload.isJsonNull()) {
            return null;
        }
        if (payload.isJsonArray()) {
            return payload;
        }
        if (!payload.isJsonObject()) {
            return null;
        }
        JsonObject object = payload.getAsJsonObject();
        for (String key : new String[]{"personnel", "personnelAccounts", "personnel_accounts", "accounts", "items", "results", "data"}) {
            JsonElement candidate = object.get(key);
            if (candidate == null || candidate.isJsonNull()) {
                continue;
            }
            if (candidate.isJsonArray()) {
                return candidate;
            }
            JsonElement nested = findCollection(candidate);
            if (nested != null && (nested.isJsonArray() || looksLikePersonnel(nested.getAsJsonObject()))) {
                return nested;
            }
        }
        return looksLikePersonnel(object) ? object : null;
    }

    private static JsonObject findPersonnelObject(JsonElement payload) {
        if (payload == null || payload.isJsonNull()) {
            return null;
        }
        if (payload.isJsonArray()) {
            JsonArray array = payload.getAsJsonArray();
            if (array.size() == 0) return null;
            JsonElement first = array.get(0);
            return first != null && first.isJsonObject() ? first.getAsJsonObject() : null;
        }
        if (!payload.isJsonObject()) {
            return null;
        }
        JsonObject object = payload.getAsJsonObject();
        if (looksLikePersonnel(object)) {
            return object;
        }
        for (String key : new String[]{"personnel", "account", "data", "result", "user"}) {
            JsonElement nested = object.get(key);
            JsonObject found = findPersonnelObject(nested);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static JsonObject findObject(JsonObject source, String... keys) {
        if (source == null) return null;
        for (String key : keys) {
            JsonElement value = source.get(key);
            if (value != null && value.isJsonObject()) {
                return value.getAsJsonObject();
            }
        }
        return null;
    }

    private static boolean looksLikePersonnel(JsonObject object) {
        return hasAny(object, "personnelId", "personnel_id", "firstName", "first_name",
                "lastName", "last_name", "fullName", "full_name", "username", "gmail");
    }

    private static boolean hasAny(JsonObject object, String... keys) {
        if (object == null) return false;
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) return true;
        }
        return false;
    }

    private static JsonElement readElement(JsonObject object, String... keys) {
        if (object == null) return null;
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) return value;
        }
        return null;
    }

    private static String readString(JsonObject object, String... keys) {
        JsonElement value = readElement(object, keys);
        if (value == null || !value.isJsonPrimitive()) return null;
        try {
            return value.getAsString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Double readDoubleObject(JsonObject object, String... keys) {
        JsonElement value = readElement(object, keys);
        if (value == null || !value.isJsonPrimitive()) return null;
        try {
            return value.getAsDouble();
        } catch (RuntimeException ignored) {
            try {
                return Double.parseDouble(value.getAsString());
            } catch (RuntimeException ignoredAgain) {
                return null;
            }
        }
    }

    private static Double readDoubleElement(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) return null;
        try {
            return value.getAsDouble();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean readBoolean(JsonObject object, String... keys) {
        JsonElement value = readElement(object, keys);
        if (value == null || !value.isJsonPrimitive()) return false;
        if (value.getAsJsonPrimitive().isBoolean()) return value.getAsBoolean();
        String raw = value.getAsString();
        return "true".equalsIgnoreCase(raw) || "1".equals(raw) || "yes".equalsIgnoreCase(raw);
    }

    private static String readStringRecursively(JsonElement element, String... keys) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String direct = readString(object, keys);
            if (direct != null && !direct.trim().isEmpty() && !direct.toLowerCase(Locale.US).contains("$2")) {
                return direct;
            }
            for (JsonElement child : object.entrySet().stream()
                    .map(entry -> entry.getValue())
                    .toArray(JsonElement[]::new)) {
                String nested = readStringRecursively(child, keys);
                if (nested != null && !nested.trim().isEmpty()) return nested;
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                String nested = readStringRecursively(child, keys);
                if (nested != null && !nested.trim().isEmpty()) return nested;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    public static final class MutationResult {
        private final Personnel personnel;
        private final String password;

        public MutationResult(Personnel personnel, String password) {
            this.personnel = personnel;
            this.password = password;
        }

        public Personnel getPersonnel() {
            return personnel;
        }

        public String getPassword() {
            return password;
        }
    }
}
