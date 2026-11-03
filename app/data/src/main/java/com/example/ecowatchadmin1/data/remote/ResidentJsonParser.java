package com.example.ecowatchadmin1.data.remote;

import com.example.ecowatchadmin1.data.models.ReportPage;
import com.example.ecowatchadmin1.data.models.ReportStatistics;
import com.example.ecowatchadmin1.data.models.ResidentAccount;
import com.example.ecowatchadmin1.data.models.ResidentPage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Defensive mapping for the protected reporter-account response shapes. */
public final class ResidentJsonParser {
    private ResidentJsonParser() {
        // Utility class.
    }

    public static List<ResidentAccount> parseResidents(JsonElement payload) {
        JsonElement source = findResidentsElement(payload);
        if (source == null || source.isJsonNull()) {
            return Collections.emptyList();
        }

        List<ResidentAccount> residents = new ArrayList<>();
        if (source.isJsonArray()) {
            JsonArray array = source.getAsJsonArray();
            for (JsonElement element : array) {
                if (element == null || !element.isJsonObject()) continue;
                try {
                    ResidentAccount resident = parseResidentObject(element.getAsJsonObject());
                    if (resident != null) residents.add(resident);
                } catch (RuntimeException ignored) {
                    // Keep valid rows when one legacy row has an unexpected shape.
                }
            }
        } else if (source.isJsonObject()) {
            ResidentAccount resident = parseResidentObject(source.getAsJsonObject());
            if (resident != null) residents.add(resident);
        }
        return residents;
    }

    public static ResidentPage parsePage(JsonElement payload) {
        ResidentPage page = new ResidentPage();
        page.setResidents(parseResidents(payload));
        page.setPage(1);
        page.setLimit(page.getResidents().size());
        page.setTotal(page.getResidents().size());
        page.setTotalPages(page.getResidents().isEmpty() ? 0 : 1);

        JsonObject pagination = findPaginationObject(payload);
        if (pagination == null) return page;
        page.setPage(Math.max(1, readInt(pagination, "page")));
        page.setLimit(Math.max(0, readInt(pagination, "limit")));
        page.setTotal(Math.max(0, readInt(pagination, "total")));
        page.setTotalPages(Math.max(0, readInt(pagination, "totalPages", "total_pages")));
        return page;
    }

    public static ReportPage parseReportPage(JsonElement payload) {
        ReportPage page = new ReportPage();
        page.setReports(AdminJsonParser.parseReports(payload));
        page.setPage(1);
        page.setLimit(page.getReports().size());
        page.setTotal(page.getReports().size());
        page.setTotalPages(page.getReports().isEmpty() ? 0 : 1);

        JsonObject pagination = findPaginationObject(payload);
        if (pagination == null) return page;
        page.setPage(Math.max(1, readInt(pagination, "page")));
        page.setLimit(Math.max(0, readInt(pagination, "limit")));
        page.setTotal(Math.max(0, readInt(pagination, "total")));
        page.setTotalPages(Math.max(0, readInt(pagination, "totalPages", "total_pages")));
        return page;
    }

    public static ResidentAccount parseResident(JsonElement payload) {
        JsonObject source = findResidentObject(payload);
        return source == null ? null : parseResidentObject(source);
    }

    /**
     * Reads the outcome of an account-management call.
     *
     * <p>The server echoes {@code temporaryPassword} so the administrator can
     * relay it to the reporter. The password this client sent is used as the
     * fallback: the administrator chose it, so if the echo is ever dropped the
     * handoff dialog still shows the password that was actually applied rather
     * than going blank.
     */
    public static MutationResult parseResidentMutation(JsonElement payload,
                                                       String fallbackPassword) {
        if (payload == null || payload.isJsonNull()) {
            return new MutationResult(null, fallbackPassword);
        }

        String password = readStringRecursively(payload,
                "temporaryPassword", "temporary_password", "plainPassword",
                "plain_password", "password", "newPassword", "new_password");
        if (password == null || password.trim().isEmpty()) {
            password = fallbackPassword;
        }
        return new MutationResult(parseResident(payload), password);
    }

    /**
     * A reporter plus the temporary password from a reset.
     *
     * <p>Shaped like the personnel equivalent so both detail screens read the
     * same way, but kept as its own type: {@link com.example.ecowatchadmin1.data.models.ResidentAccount}
     * and the personnel model are unrelated projections, and sharing one result
     * class would tie the two screens together.
     */
    public static final class MutationResult {
        private final ResidentAccount resident;
        private final String password;

        MutationResult(ResidentAccount resident, String password) {
            this.resident = resident;
            this.password = password;
        }

        public ResidentAccount getResident() {
            return resident;
        }

        public String getPassword() {
            return password;
        }
    }

    private static String readStringRecursively(JsonElement element, String... keys) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (String key : keys) {
                String value = readString(object, key);
                if (value != null && !value.trim().isEmpty()) return value;
            }
            for (java.util.Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String nested = readStringRecursively(entry.getValue(), keys);
                if (nested != null) return nested;
            }
        } else if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                String nested = readStringRecursively(item, keys);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    public static ReportStatistics parseStatistics(JsonElement payload) {
        JsonObject source = findStatisticsObject(payload);
        ReportStatistics statistics = new ReportStatistics();
        if (source == null) return statistics;

        statistics.setTotal(readInt(source, "total", "totalReports", "total_reports"));
        statistics.setPending(readInt(source, "pending", "pendingCount", "pending_count"));
        statistics.setInProgress(readInt(source, "inProgress", "in_progress", "inProgressCount"));
        statistics.setCompleted(readInt(source, "completed", "completedCount", "completed_count"));
        statistics.setDenied(readInt(source, "denied", "deniedCount", "denied_count"));
        return statistics;
    }

    private static ResidentAccount parseResidentObject(JsonObject source) {
        JsonObject object = source;
        JsonObject nestedResident = readObject(source, "resident", "reporter", "account");
        if (nestedResident != null) object = nestedResident;

        String residentId = readString(object, "residentId", "resident_id", "serverId", "server_id", "id");
        if (isBlank(residentId)) return null;

        ResidentAccount resident = new ResidentAccount();
        resident.setResidentId(residentId);
        resident.setServerId(readString(object, "serverId", "server_id", "residentId", "resident_id"));
        resident.setFirstName(readString(object, "firstName", "first_name"));
        resident.setLastName(readString(object, "lastName", "last_name"));
        resident.setFullName(readString(object, "fullName", "full_name", "name"));
        resident.setUsername(readString(object, "username", "userName", "user_name"));
        resident.setEmail(readString(object, "email", "gmail"));
        resident.setAddress(readString(object, "address", "homeAddress", "home_address"));
        resident.setPhoneNumber(readString(object, "phoneNumber", "phone_number"));
        resident.setMessengerLink(readString(object, "messengerLink", "messenger_link"));
        resident.setProfileImage(readString(object, "profileImage", "profile_image"));
        resident.setProfileImageUrl(readString(object,
                "profileImageUrl", "profile_image_url", "profileImage", "profile_image"));
        resident.setRole(readString(object, "role", "accountType", "account_type"));
        resident.setStatus(readString(object, "status", "accountStatus", "account_status"));
        resident.setAccountStatus(readString(object, "accountStatus", "account_status"));

        String normalizedStatus = resident.getStatus() == null
                ? "" : resident.getStatus().trim().toUpperCase(Locale.US);
        boolean disabled = readBoolean(object, "isDisabled", "is_disabled", "disabled")
                || "DISABLED".equals(normalizedStatus)
                || "INACTIVE".equals(normalizedStatus)
                || "BLOCKED".equals(normalizedStatus)
                || "SUSPENDED".equals(normalizedStatus)
                || "DELETED".equals(normalizedStatus);
        boolean activeValue = readBoolean(object, "isActive", "is_active", "active");
        if (!hasAny(object, "isActive", "is_active", "active")) {
            activeValue = !disabled;
        }
        resident.setDisabled(disabled);
        resident.setActive(activeValue && !disabled);
        resident.setVerified(readBoolean(object, "isVerified", "is_verified", "verified"));
        resident.setLastActive(readString(object,
                "lastActive", "last_active", "lastSeen", "last_seen", "updatedAt", "updated_at"));
        resident.setDateCreated(readString(object,
                "dateCreated", "date_created", "createdAt", "created_at"));
        resident.setCreatedAt(readString(object, "createdAt", "created_at", "dateCreated", "date_created"));
        resident.setUpdatedAt(readString(object, "updatedAt", "updated_at"));
        resident.setTotalReports(readTotalReports(object));
        return resident;
    }

    private static int readTotalReports(JsonObject source) {
        JsonObject counts = readObject(source, "reportCounts", "report_counts", "statistics", "stats");
        if (counts != null) {
            int value = readInt(counts, "total", "totalReports", "total_reports");
            if (value > 0) return value;
        }
        return readInt(source, "totalReports", "total_reports", "reportCount", "report_count");
    }

    private static JsonElement findResidentsElement(JsonElement payload) {
        if (payload == null || payload.isJsonNull()) return null;
        if (payload.isJsonArray()) return payload;
        if (!payload.isJsonObject()) return null;

        JsonObject object = payload.getAsJsonObject();
        for (String key : new String[]{"residents", "reporters", "items", "results"}) {
            JsonElement candidate = object.get(key);
            if (candidate != null && candidate.isJsonArray()) return candidate;
            if (candidate != null && candidate.isJsonObject()) {
                JsonElement nested = findResidentsElement(candidate);
                if (nested != null) return nested;
            }
        }
        if (object.has("data") && !object.has("residentId") && !object.has("resident_id")) {
            JsonElement nested = findResidentsElement(object.get("data"));
            if (nested != null) return nested;
        }
        return looksLikeResident(object) ? object : null;
    }

    private static JsonObject findResidentObject(JsonElement payload) {
        if (payload == null || !payload.isJsonObject()) return null;
        JsonObject object = payload.getAsJsonObject();
        for (String key : new String[]{"resident", "reporter", "account"}) {
            JsonObject nested = readObject(object, key);
            if (nested != null && looksLikeResident(nested)) return nested;
        }
        if (looksLikeResident(object)) return object;
        for (String key : new String[]{"data", "result"}) {
            JsonElement nested = object.get(key);
            if (nested != null && nested.isJsonObject()) {
                JsonObject found = findResidentObject(nested);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JsonObject findStatisticsObject(JsonElement payload) {
        if (payload == null || !payload.isJsonObject()) return null;
        JsonObject object = payload.getAsJsonObject();
        for (String key : new String[]{"statistics", "stats", "counts", "reportCounts", "report_counts"}) {
            JsonObject nested = readObject(object, key);
            if (nested != null) return nested;
        }
        if (object.has("total") || object.has("pending") || object.has("inProgress")
                || object.has("in_progress") || object.has("completed") || object.has("denied")) {
            return object;
        }
        for (String key : new String[]{"data", "result"}) {
            JsonElement nested = object.get(key);
            if (nested != null && nested.isJsonObject()) {
                JsonObject found = findStatisticsObject(nested);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JsonObject findPaginationObject(JsonElement payload) {
        if (payload == null || !payload.isJsonObject()) return null;
        JsonObject object = payload.getAsJsonObject();
        JsonObject pagination = readObject(object, "pagination");
        if (pagination != null) return pagination;
        for (String key : new String[]{"data", "result"}) {
            JsonElement nested = object.get(key);
            if (nested != null && nested.isJsonObject()) {
                JsonObject found = findPaginationObject(nested);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean looksLikeResident(JsonObject object) {
        return hasAny(object, "residentId", "resident_id", "serverId", "server_id", "username", "email")
                && hasAny(object, "firstName", "first_name", "lastName", "last_name", "fullName", "full_name");
    }

    private static JsonObject readObject(JsonObject object, String... keys) {
        if (object == null) return null;
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && element.isJsonObject()) return element.getAsJsonObject();
        }
        return null;
    }

    private static String readString(JsonObject object, String... keys) {
        if (object == null) return null;
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && element.isJsonPrimitive()) {
                try {
                    return element.getAsString();
                } catch (RuntimeException ignored) {
                    // Try the next compatible field.
                }
            }
        }
        return null;
    }

    private static int readInt(JsonObject object, String... keys) {
        if (object == null) return 0;
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element == null || !element.isJsonPrimitive()) continue;
            try {
                return Math.max(0, element.getAsInt());
            } catch (RuntimeException ignored) {
                try {
                    return Math.max(0, (int) Double.parseDouble(element.getAsString()));
                } catch (RuntimeException ignoredAgain) {
                    // Try the next field.
                }
            }
        }
        return 0;
    }

    private static boolean readBoolean(JsonObject object, String... keys) {
        if (object == null) return false;
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element == null || !element.isJsonPrimitive()) continue;
            try {
                if (element.getAsJsonPrimitive().isBoolean()) return element.getAsBoolean();
                String value = element.getAsString();
                return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
            } catch (RuntimeException ignored) {
                // Try the next field.
            }
        }
        return false;
    }

    private static boolean hasAny(JsonObject object, String... keys) {
        if (object == null) return false;
        for (String key : keys) {
            if (object.has(key) && !object.get(key).isJsonNull()) return true;
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
