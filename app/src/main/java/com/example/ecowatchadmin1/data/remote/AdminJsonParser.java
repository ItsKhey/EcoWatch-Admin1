package com.example.ecowatchadmin1.data.remote;

import com.example.ecowatchadmin1.data.models.AdminAccount;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.models.ReportTimelineEvent;
import com.example.ecowatchadmin1.data.models.User;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Converts the response shapes used by the Render service into the existing
 * Android model objects. The admin account contract is read from the
 * canonical "admin" object; resident identifiers are never used as an
 * administrator identity.
 */
public final class AdminJsonParser {
    private static final String[] DATE_FORMATS = new String[]{
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd HH:mm:ss.SSSXXX",
            "yyyy-MM-dd HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
    };

    private AdminJsonParser() {
        // Utility class.
    }

    public static String findToken(JsonObject response) {
        if (response == null) {
            return null;
        }

        String token = readString(response, "token", "accessToken", "access_token", "jwt");
        if (!isBlank(token)) {
            return token;
        }

        JsonObject data = findObject(response, "data", "auth", "result");
        if (data != null && data != response) {
            return findToken(data);
        }
        return null;
    }

    /**
     * Parses the authenticated admin and rejects non-admin accounts.
     *
     * <p>Admin identity is intentionally read only from the canonical
     * {@code adminId}/{@code admin_id} fields. The legacy resident identifier
     * is never used as a fallback because doing so can put a resident account
     * identifier on the administrator screen.</p>
     */
    public static User parseLoginUser(JsonObject response, String fallbackIdentifier) {
        return parseLoginUser(response, fallbackIdentifier, null);
    }

    /**
     * Variant used during a rolling deployment. If an older response has only
     * the legacy resident-shaped profile, the ID may come from the already
     * verified JWT; the legacy residentId field itself is never read.
     */
    public static User parseLoginUser(JsonObject response, String fallbackIdentifier,
                                      String authenticatedAdminId) {
        JsonObject source = findAdminObject(response);
        if (source == null) {
            source = response == null ? new JsonObject() : response;
        }

        String id = readString(source, "adminId", "admin_id");
        if (isBlank(id) && response != null && response != source) {
            id = readString(response, "adminId", "admin_id");
        }
        if (isBlank(id)) {
            id = authenticatedAdminId;
        }
        if (isBlank(id) || !id.regionMatches(true, 0, "ADM-", 0, 4)) {
            throw new IllegalArgumentException("The server did not return a valid admin identifier.");
        }

        String role = readString(source, "role", "accountType", "account_type");
        if (isBlank(role) && response != null) {
            // Read only the role from the compatibility profile wrapper. The
            // wrapper's residentId is intentionally not considered an ID.
            JsonObject legacyProfile = findObject(response, "resident");
            role = readString(legacyProfile, "role", "accountType", "account_type");
        }
        if (isBlank(role)) {
            throw new IllegalArgumentException("The server did not identify this account as an administrator.");
        }
        if (!isAdminRole(role.trim())) {
            throw new IllegalArgumentException("This account is not authorized as an administrator.");
        }

        JsonObject profileSource = source;
        if (!looksLikeAdmin(source)) {
            JsonObject legacyProfile = findObject(response, "resident");
            if (legacyProfile != null) {
                profileSource = legacyProfile;
            }
        }
        String username = readString(profileSource, "username", "userName");
        if (isBlank(username)) {
            username = fallbackIdentifier;
        }
        if (isBlank(username)) {
            username = "admin";
        }

        String firstName = readString(profileSource, "firstName", "first_name");
        String lastName = readString(profileSource, "lastName", "last_name");
        String email = readString(profileSource, "email");
        String address = readString(profileSource, "address");
        String profileImage = readString(profileSource,
                "profileImage", "profile_image", "profileImageUrl", "profile_image_url");

        User user = new User(
                id,
                isBlank(firstName) ? "Admin" : firstName,
                isBlank(lastName) ? "User" : lastName,
                username,
                isBlank(email) ? "" : email,
                address == null ? "" : address,
                profileImage == null ? "" : profileImage,
                "ADMIN"
        );

        user.setStatus(readString(profileSource, "status"));
        user.setPhoneNumber(readString(profileSource, "phoneNumber", "phone_number"));
        user.setCreatedAt(readString(profileSource, "createdAt", "created_at", "dateCreated", "date_created"));
        user.setUpdatedAt(readString(profileSource, "updatedAt", "updated_at"));
        user.setCreatedById(readString(profileSource, "createdById", "created_by_id"));
        return user;
    }

    /**
     * Parses the authenticated administrator account returned by the admin
     * API. This method never falls back to {@code residentId}; a response that
     * only contains a resident identifier is rejected.
     */
    public static AdminAccount parseAccountDetails(JsonObject response) {
        return parseAccountDetails(response, null, false);
    }

    /**
     * Transitional parser for an older protected /auth/me response. The
     * administrator ID must come from the verified session/JWT; the legacy
     * residentId field is never read.
     */
    public static AdminAccount parseAccountDetails(JsonObject response,
                                                    String authenticatedAdminId) {
        return parseAccountDetails(response, authenticatedAdminId, true);
    }

    private static AdminAccount parseAccountDetails(JsonObject response,
                                                     String authenticatedAdminId,
                                                     boolean allowLegacyProfile) {
        JsonObject source = findAdminObject(response);
        boolean legacyProfile = false;
        if (source == null && allowLegacyProfile && response != null) {
            source = findObject(response, "resident");
            legacyProfile = source != null;
        }
        if (source == null) {
            throw new IllegalArgumentException("The server did not return an admin account.");
        }

        String id = readString(source, "adminId", "admin_id");
        if (isBlank(id)) {
            id = authenticatedAdminId;
        }
        if (isBlank(id) || !id.regionMatches(true, 0, "ADM-", 0, 4)) {
            throw new IllegalArgumentException("The server did not return a valid admin identifier.");
        }

        String role = readString(source, "role", "accountType", "account_type");
        if (isBlank(role) && legacyProfile && !isBlank(authenticatedAdminId)) {
            // The caller has an authenticated admin JWT/session. The legacy
            // endpoint simply omitted the role from its compatibility shape.
            role = "ADMIN";
        }
        if (isBlank(role) || !isAdminRole(role.trim())) {
            throw new IllegalArgumentException("This account is not authorized as an administrator.");
        }

        String lastActive = readString(source, "lastActive", "last_active", "lastSeen", "last_seen");
        if (isBlank(lastActive)) {
            // Some rolling-deployment responses expose only the server's
            // updated_at field. It is still backend-owned activity metadata;
            // never substitute the device-local SessionManager timestamp.
            lastActive = readString(source, "updatedAt", "updated_at");
        }

        AdminAccount account = new AdminAccount(
                id,
                "ADMIN",
                readString(source, "createdAt", "created_at", "dateCreated", "date_created"),
                lastActive
        );
        account.setFirstName(readString(source, "firstName", "first_name"));
        account.setLastName(readString(source, "lastName", "last_name"));
        account.setUsername(readString(source, "username", "userName"));
        account.setEmail(readString(source, "email"));
        account.setAddress(readString(source, "address"));
        account.setProfileImageUrl(readString(source,
                "profileImage", "profile_image", "profileImageUrl", "profile_image_url"));
        return account;
    }

    /** Parses one report from raw, wrapped, or assignment response JSON. */
    public static Report parseReport(JsonElement payload) {
        if (payload == null || payload.isJsonNull()) {
            throw new IllegalArgumentException("Report response is empty.");
        }
        if (payload.isJsonArray()) {
            JsonArray array = payload.getAsJsonArray();
            if (array.size() == 0 || !array.get(0).isJsonObject()) {
                throw new IllegalArgumentException("Report response did not contain a report.");
            }
            return parseReport(array.get(0).getAsJsonObject());
        }
        if (!payload.isJsonObject()) {
            throw new IllegalArgumentException("Report response is not a JSON object.");
        }
        JsonObject object = payload.getAsJsonObject();
        if (looksLikeReport(object)) {
            return parseReport(object);
        }
        for (String key : new String[]{"report", "data", "result", "response"}) {
            JsonElement nested = object.get(key);
            if (nested != null && !nested.isJsonNull()) {
                try {
                    return parseReport(nested);
                } catch (IllegalArgumentException ignored) {
                    // Try the next supported response wrapper.
                }
            }
        }
        throw new IllegalArgumentException("Report response did not contain a report.");
    }

    /**
     * Accepts either a raw JSON array or an object containing reports under
     * reports/items/results/data.
     */
    public static List<Report> parseReports(JsonElement payload) {
        JsonElement reportsElement = findReportsElement(payload);
        if (reportsElement == null || reportsElement.isJsonNull()) {
            return Collections.emptyList();
        }

        if (reportsElement.isJsonArray()) {
            JsonArray array = reportsElement.getAsJsonArray();
            List<Report> reports = new ArrayList<>();
            for (JsonElement element : array) {
                if (element != null && element.isJsonObject()) {
                    try {
                        reports.add(parseReport(element.getAsJsonObject()));
                    } catch (RuntimeException ignored) {
                        // Ignore one malformed row and retain all valid reports.
                    }
                }
            }
            return reports;
        }

        if (reportsElement.isJsonObject() && looksLikeReport(reportsElement.getAsJsonObject())) {
            try {
                return Collections.singletonList(parseReport(reportsElement.getAsJsonObject()));
            } catch (RuntimeException ignored) {
                return Collections.emptyList();
            }
        }

        return Collections.emptyList();
    }

    private static Report parseReport(JsonObject source) {
        String reportId = readString(source, "reportId", "report_id");
        String mongoId = readString(source, "mongoId", "mongo_id", "_id");
        if (isBlank(reportId)) {
            reportId = readString(source, "id");
        }
        if (isBlank(mongoId)) {
            mongoId = reportId;
        }
        if (isBlank(reportId)) {
            reportId = mongoId;
        }
        if (isBlank(reportId)) {
            throw new IllegalArgumentException("Report is missing a stable report ID.");
        }

        String title = readString(source, "title");
        String description = readString(source, "description");
        String wasteType = readString(source, "category", "wasteType", "waste_type");
        double latitude = readDouble(source, "latitude", "lat");
        double longitude = readDouble(source, "longitude", "lng", "lon", "long");

        JsonElement coordinatesElement = readElement(source, "coordinates");
        if (coordinatesElement != null && coordinatesElement.isJsonArray()
                && coordinatesElement.getAsJsonArray().size() >= 2) {
            // GeoJSON-style coordinates are [longitude, latitude].
            JsonArray coordinateArray = coordinatesElement.getAsJsonArray();
            if (!hasAny(source, "longitude", "lng", "lon", "long")) {
                longitude = readDoubleFromElement(coordinateArray.get(0));
            }
            if (!hasAny(source, "latitude", "lat")) {
                latitude = readDoubleFromElement(coordinateArray.get(1));
            }
        } else {
            JsonObject coordinates = findObject(source, "coordinates", "location");
            if (coordinates != null) {
                if (!hasAny(source, "latitude", "lat")) {
                    latitude = readDouble(coordinates, "latitude", "lat");
                }
                if (!hasAny(source, "longitude", "lng", "lon", "long")) {
                    longitude = readDouble(coordinates, "longitude", "lng", "lon", "long");
                }
            }
        }

        Report report = new Report(
                isBlank(title) ? "Untitled report" : title,
                description == null ? "" : description,
                isBlank(wasteType) ? "Uncategorized" : wasteType,
                latitude,
                longitude
        );

        report.setMongoId(mongoId);
        report.setReportId(reportId);
        report.setResidentId(readString(source, "residentId", "resident_id"));
        // Who filed it. The server joins this onto the report payload, so report
        // lists can credit a resident by name without a lookup per report. The
        // nested form is accepted too, in case the join is ever reshaped to embed
        // the resident object.
        //
        // May legitimately be absent, for a report whose resident account was
        // deleted; getResidentLabel() falls back to the ID.
        JsonObject resident = findObject(source, "resident", "reporter");
        String residentName = readString(source, "residentName", "resident_name");
        if (isBlank(residentName) && resident != null) {
            residentName = joinResidentName(readString(resident, "fullName", "full_name", "name"),
                    readString(resident, "firstName", "first_name"),
                    readString(resident, "lastName", "last_name"));
        }
        report.setResidentName(residentName);
        report.setPhotoUrl(readString(source,
                "imageUrl", "image_url", "photoUrl", "photo_url"));
        // The full photo set drives the multi-photo badge in the report list.
        report.setPhotoUrls(readStringList(source, "photos", "photoUrls", "imageUrls"));
        // Cleaning evidence, sent flat on the report list so the review dialog
        // needs no extra request. The single-report endpoint nests the same data
        // under assignment.beforePhotos/afterPhotos, handled below.
        report.setBeforePhotoUrls(readStringList(source,
                "beforePhotoUrls", "before_photo_urls"));
        report.setAfterPhotoUrls(readStringList(source,
                "afterPhotoUrls", "after_photo_urls"));
        report.setStatus(defaultIfBlank(readString(source, "status"), "Pending"));
        // Backend grouping key, kept on the model because the server still uses
        // it for nearby detection. No admin screen reads or displays it.
        report.setClusterId(readString(source, "clusterId", "cluster_id"));
        report.setAdminResponse(readString(source, "adminResponse", "admin_response"));
        // Personnel-only channel. Read here so the assignment sheet can prefill
        // the note the admin gave the cleaner instead of silently discarding it.
        report.setPersonnelNote(readString(source, "personnelNote", "personnel_note"));
        // Server-derived revision marker. Kept apart from personnelNote because
        // that column also carries ordinary assignment instructions.
        report.setRevisionRequested(readBoolean(source,
                "revisionRequested", "revision_requested"));
        // The cleaning lifecycle for one task. Present only on the personnel
        // history, which is the one screen that expands a card into a timeline.
        report.setTimeline(readTimeline(source));
        // The cleaner talking back. Read separately from personnelNote because the
        // two have different authors and different audiences.
        JsonObject completedAssignment = findObject(source,
                "completedAssignment", "completed_assignment", "completion",
                "latestCompletedAssignment", "latest_completed_assignment");
        if (completedAssignment == null) {
            // Some report responses place completion details on the joined
            // assignment object rather than flattening them onto the report.
            completedAssignment = findObject(source, "assignment");
        }
        JsonObject completedPersonnel = findObject(completedAssignment,
                "personnel", "completedBy", "completed_by", "completedByPersonnel",
                "completed_by_personnel", "cleaner", "assignedPersonnel");
        report.setPersonnelResponse(firstNonBlank(
                readString(source, "personnelResponse", "personnel_response",
                        "completionNotes", "completion_notes", "completionNote",
                        "completion_note"),
                readString(completedAssignment, "personnelResponse", "personnel_response",
                        "completionNotes", "completion_notes", "completionNote",
                        "completion_note")));
        report.setCompletedByPersonnelId(firstNonBlank(
                readString(source, "completedByPersonnelId", "completed_by_personnel_id",
                        "completedPersonnelId"),
                readString(completedAssignment, "completedByPersonnelId", "completed_by_personnel_id",
                        "completedPersonnelId", "personnelId", "personnel_id"),
                readString(completedPersonnel, "personnelId", "personnel_id", "id", "_id")));
        report.setCompletedByPersonnelName(firstNonBlank(
                readString(source, "completedByPersonnelName", "completed_by_personnel_name",
                        "completedPersonnelName"),
                readString(completedAssignment, "completedByPersonnelName", "completed_by_personnel_name",
                        "completedPersonnelName", "personnelName", "personnel_name", "fullName", "full_name"),
                readString(completedPersonnel, "fullName", "full_name", "name"),
                joinName(readString(completedPersonnel, "firstName", "first_name"),
                        readString(completedPersonnel, "lastName", "last_name"))));
        report.setCompletedByPersonnelAvatar(readString(source,
                "completedByPersonnelAvatar", "completed_by_personnel_avatar",
                "completedPersonnelAvatar"));
        report.setWorkCompletedAt(firstNonBlank(
                readString(source, "workCompletedAt", "work_completed_at", "completedAt"),
                readString(completedAssignment, "workCompletedAt", "work_completed_at",
                        "completedAt", "completed_at", "finishedAt", "finished_at")));
        report.setCreatedAt(readString(source, "createdAt", "created_at"));
        report.setUpdatedAt(readString(source, "updatedAt", "updated_at"));
        report.setSynced(true);
        report.setDeleted(readBoolean(source, "isDeleted", "is_deleted"));
        report.setArchived(readBoolean(source, "isArchived", "is_archived"));
        report.setRestored(readBoolean(source, "isRestored", "is_restored"));
        report.setDeletedAt(parseTimestamp(readElement(source, "deletedAt", "deleted_at")));
        List<String> assignedPersonnelIds = readStringList(source,
                "assignedPersonnelIds", "assigned_personnel_ids");
        report.setAssignedPersonnelIds(assignedPersonnelIds);

        String assignedPersonnelId = readString(source,
                "assignedPersonnelId", "assigned_personnel_id");
        JsonObject assignedPersonnel = findObject(source,
                "assignedPersonnel", "assigned_personnel", "assignment");
        if (assignedPersonnel != null) {
            // Evidence may also arrive nested under the assignment object. Read
            // it before descending into the nested personnel object, and only
            // when the flat top-level arrays were absent.
            if (report.getBeforePhotoUrls().isEmpty()) {
                report.setBeforePhotoUrls(readEvidenceUrls(assignedPersonnel,
                        "beforePhotos", "before_photos", "beforePhotoUrls"));
            }
            if (report.getAfterPhotoUrls().isEmpty()) {
                report.setAfterPhotoUrls(readEvidenceUrls(assignedPersonnel,
                        "afterPhotos", "after_photos", "afterPhotoUrls"));
            }
            JsonObject nestedPersonnel = findObject(assignedPersonnel,
                    "personnel", "assignedPersonnel", "assigned_personnel");
            if (nestedPersonnel != null) assignedPersonnel = nestedPersonnel;
        }
        String assignedPersonnelName = readString(source,
                "assignedPersonnelName", "assigned_personnel_name");
        String assignedPersonnelAvatar = readString(source,
                "assignedPersonnelAvatar", "assigned_personnel_avatar",
                "assignedPersonnelAvatarUrl", "assigned_personnel_avatar_url");
        if (assignedPersonnel != null) {
            if (isBlank(assignedPersonnelId)) {
                assignedPersonnelId = readString(assignedPersonnel,
                        "personnelId", "personnel_id", "id");
            }
            if (isBlank(assignedPersonnelName)) {
                assignedPersonnelName = firstNonBlank(
                        readString(assignedPersonnel, "fullName", "full_name", "name"),
                        joinName(readString(assignedPersonnel, "firstName", "first_name"),
                                readString(assignedPersonnel, "lastName", "last_name")));
            }
            if (isBlank(assignedPersonnelAvatar)) {
                assignedPersonnelAvatar = readString(assignedPersonnel,
                        "avatarUrl", "avatar_url", "profileImage", "profile_image");
            }
        }
        if (isBlank(assignedPersonnelId) && assignedPersonnelIds.size() == 1) {
            assignedPersonnelId = assignedPersonnelIds.get(0);
        }
        report.setAssignedPersonnelId(assignedPersonnelId);
        report.setAssignedPersonnelName(assignedPersonnelName);
        report.setAssignedPersonnelAvatar(assignedPersonnelAvatar);
        report.setAssignedByAdminId(readString(source,
                "assignedByAdminId", "assigned_by_admin_id"));
        report.setAssignedAt(readString(source, "assignedAt", "assigned_at"));
        report.setPriority(readString(source, "priority"));
        report.setHandledById(readString(source, "handledById", "handled_by_id"));
        report.setResolutionImageUrl(readString(source,
                "resolutionImageUrl", "resolution_image_url"));
        report.setResolvedAt(readString(source, "resolvedAt", "resolved_at"));

        // How big the waste is, and what went out to deal with it. The scale is
        // chosen by the resident when the report is filed; the crew and the
        // truck are what the administrator sent to match it.
        report.setWasteScale(readString(source, "wasteScale", "waste_scale"));
        JsonObject assignment = findObject(source, "assignment");
        JsonObject assignedTeam = findObject(source, "assignedTeam", "assigned_team", "team");
        if (assignedTeam == null && assignment != null) {
            assignedTeam = findObject(assignment, "assignedTeam", "assigned_team", "team");
        }
        report.setAssignedTeamId(firstNonBlank(
                readString(source, "assignedTeamId", "assigned_team_id", "teamId", "team_id"),
                readString(assignment, "assignedTeamId", "assigned_team_id", "teamId", "team_id"),
                readString(assignedTeam, "teamId", "team_id", "id", "_id")));
        report.setAssignedTeamName(firstNonBlank(
                readString(source, "assignedTeamName", "assigned_team_name", "teamName", "team_name"),
                readString(assignment, "assignedTeamName", "assigned_team_name", "teamName", "team_name"),
                readString(assignedTeam, "teamName", "team_name", "name", "title"),
                readString(source, "assignedTeam", "assigned_team", "team"),
                readString(assignment, "assignedTeam", "assigned_team", "team")));
        report.setAssignedCrew(readAssignedCrew(source, assignment));
        JsonObject assignedVehicle = findObject(source, "assignedVehicle", "assigned_vehicle", "vehicle");
        if (assignedVehicle == null && assignment != null) {
            assignedVehicle = findObject(assignment, "assignedVehicle", "assigned_vehicle", "vehicle");
        }
        report.setAssignedVehicleId(firstNonBlank(
                readString(source, "assignedVehicleId", "assigned_vehicle_id", "vehicleId", "vehicle_id"),
                readString(assignment, "assignedVehicleId", "assigned_vehicle_id", "vehicleId", "vehicle_id"),
                readString(assignedVehicle, "vehicleId", "vehicle_id", "id", "_id")));
        report.setAssignedVehiclePlateNumber(firstNonBlank(
                readString(source, "assignedVehiclePlateNumber", "assigned_vehicle_plate_number", "plateNumber", "plate_number"),
                readString(assignment, "assignedVehiclePlateNumber", "assigned_vehicle_plate_number", "plateNumber", "plate_number"),
                readString(assignedVehicle, "plateNumber", "plate_number", "plate")));
        report.setAssignedVehicleType(firstNonBlank(
                readString(source, "assignedVehicleType", "assigned_vehicle_type", "vehicleType", "vehicle_type"),
                readString(assignment, "assignedVehicleType", "assigned_vehicle_type", "vehicleType", "vehicle_type"),
                readString(assignedVehicle, "vehicleType", "vehicle_type", "type")));
        return report;
    }

    private static List<ReportTimelineEvent> readTimeline(JsonObject source) {
        List<ReportTimelineEvent> events = new ArrayList<>();
        JsonElement element = readElement(source, "timeline", "taskTimeline", "task_timeline");
        if (element == null || !element.isJsonArray()) return events;
        for (JsonElement item : element.getAsJsonArray()) {
            if (item == null || !item.isJsonObject()) continue;
            JsonObject event = item.getAsJsonObject();
            ReportTimelineEvent parsed = new ReportTimelineEvent();
            parsed.setAction(readString(event, "action", "type"));
            parsed.setDescription(readString(event, "description"));
            parsed.setAdminId(readString(event, "adminId", "admin_id"));
            parsed.setTimestamp(readString(event, "timestamp", "createdAt", "created_at"));
            events.add(parsed);
        }
        return events;
    }

    private static List<Report.AssignedCrewMember> readAssignedCrew(JsonObject source, JsonObject assignment) {
        JsonElement element = readElement(source, "assignedCrew", "assigned_crew");
        if (element == null && assignment != null) {
            element = readElement(assignment, "assignedCrew", "assigned_crew");
        }
        List<Report.AssignedCrewMember> crew = new ArrayList<>();
        if (element == null || !element.isJsonArray()) return crew;
        for (JsonElement item : element.getAsJsonArray()) {
            if (item == null || !item.isJsonObject()) continue;
            JsonObject member = item.getAsJsonObject();
            Report.AssignedCrewMember parsed = new Report.AssignedCrewMember();
            parsed.personnelId = readString(member, "personnelId", "personnel_id");
            parsed.fullName = readString(member, "fullName", "full_name", "name");
            parsed.leader = readBoolean(member, "leader", "isLeader", "is_leader");
            crew.add(parsed);
        }
        return crew;
    }

    private static JsonObject findAdminObject(JsonObject root) {
        if (root == null) {
            return null;
        }

        JsonObject direct = findObject(root, "admin", "account");
        if (direct != null && looksLikeAdmin(direct)) {
            return direct;
        }

        JsonObject data = findObject(root, "data", "auth", "result");
        if (data != null && data != root) {
            JsonObject nested = findAdminObject(data);
            if (nested != null) {
                return nested;
            }
        }

        return looksLikeAdmin(root) ? root : null;
    }

    private static JsonObject findObject(JsonObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        }
        return null;
    }

    private static JsonElement findReportsElement(JsonElement payload) {
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
        for (String key : new String[]{"reports", "items", "results", "data", "report"}) {
            JsonElement candidate = object.get(key);
            if (candidate == null || candidate.isJsonNull()) {
                continue;
            }
            if (candidate.isJsonArray()) {
                return candidate;
            }
            if (candidate.isJsonObject()) {
                JsonElement nested = findReportsElement(candidate);
                if (nested != null) {
                    return nested;
                }
            }
        }

        return looksLikeReport(object) ? object : null;
    }

    private static boolean looksLikeReport(JsonObject object) {
        return hasAny(object, "reportId", "report_id", "title", "category", "latitude", "longitude");
    }

    private static boolean looksLikeAdmin(JsonObject object) {
        return hasAny(object,
                "adminId", "admin_id", "firstName", "first_name", "lastName", "last_name",
                "username", "userName", "email", "role", "accountType");
    }

    private static boolean hasAny(JsonObject object, String... keys) {
        if (object == null) {
            return false;
        }
        for (String key : keys) {
            if (object.has(key) && !object.get(key).isJsonNull()) {
                return true;
            }
        }
        return false;
    }

    private static JsonElement readElement(JsonObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && !element.isJsonNull()) {
                return element;
            }
        }
        return null;
    }

    private static String readString(JsonObject object, String... keys) {
        JsonElement element = readElement(object, keys);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static double readDouble(JsonObject object, String... keys) {
        JsonElement element = readElement(object, keys);
        if (element == null || !element.isJsonPrimitive()) {
            return 0.0;
        }
        try {
            return element.getAsDouble();
        } catch (RuntimeException ignored) {
            try {
                return Double.parseDouble(element.getAsString());
            } catch (RuntimeException ignoredAgain) {
                return 0.0;
            }
        }
    }

    private static double readDoubleFromElement(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return 0.0;
        }
        try {
            return element.getAsDouble();
        } catch (RuntimeException ignored) {
            try {
                return Double.parseDouble(element.getAsString());
            } catch (RuntimeException ignoredAgain) {
                return 0.0;
            }
        }
    }

    private static boolean readBoolean(JsonObject object, String... keys) {
        JsonElement element = readElement(object, keys);
        if (element == null || !element.isJsonPrimitive()) {
            return false;
        }
        if (element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        String value;
        try {
            value = element.getAsString();
        } catch (RuntimeException ignored) {
            return false;
        }
        return "true".equalsIgnoreCase(value)
                || "1".equals(value)
                || "yes".equalsIgnoreCase(value);
    }

    private static List<String> readStringList(JsonObject object, String... keys) {
        JsonElement element = readElement(object, keys);
        if (element == null) {
            return new ArrayList<>();
        }

        if (element.isJsonArray()) {
            List<String> values = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                if (item != null && item.isJsonPrimitive()) {
                    values.add(item.getAsString());
                }
            }
            return values;
        }

        if (element.isJsonPrimitive()) {
            String raw = element.getAsString();
            if (raw == null || raw.trim().isEmpty()) {
                return new ArrayList<>();
            }
            try {
                JsonElement parsed = new JsonParser().parse(raw);
                if (parsed.isJsonArray()) {
                    return readStringListFromArray(parsed.getAsJsonArray());
                }
            } catch (RuntimeException ignored) {
                // It may be a comma-separated value instead of JSON.
            }
            List<String> values = new ArrayList<>();
            for (String value : raw.split(",")) {
                if (!value.trim().isEmpty()) {
                    values.add(value.trim());
                }
            }
            return values;
        }

        return new ArrayList<>();
    }

    private static List<String> readStringListFromArray(JsonArray array) {
        List<String> values = new ArrayList<>();
        for (JsonElement item : array) {
            if (item != null && item.isJsonPrimitive()) {
                values.add(item.getAsString());
            }
        }
        return values;
    }

    /**
     * Reads a photo URL list that may hold plain strings or evidence objects of
     * the shape {@code {evidenceId, imageUrl, phase, ...}}. Used for the cleaning
     * evidence nested under a report assignment.
     */
    private static List<String> readEvidenceUrls(JsonObject object, String... keys) {
        JsonElement element = readElement(object, keys);
        if (element == null || !element.isJsonArray()) {
            return new ArrayList<>();
        }

        List<String> values = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (item == null) continue;
            if (item.isJsonPrimitive()) {
                String value = item.getAsString();
                if (!isBlank(value)) values.add(value.trim());
            } else if (item.isJsonObject()) {
                String value = readString(item.getAsJsonObject(),
                        "imageUrl", "image_url", "url", "photoUrl", "photo_url");
                if (!isBlank(value)) values.add(value.trim());
            }
        }
        return values;
    }

    private static Long parseTimestamp(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }

        if (element.getAsJsonPrimitive().isNumber()) {
            return element.getAsLong();
        }

        String value;
        try {
            value = element.getAsString();
        } catch (RuntimeException ignored) {
            return null;
        }
        if (isBlank(value)) {
            return null;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            // Continue with ISO/PostgreSQL timestamp parsing.
        }

        for (String pattern : DATE_FORMATS) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = format.parse(value);
                if (date != null) {
                    return date.getTime();
                }
            } catch (ParseException ignored) {
                // Try the next supported format.
            }
        }
        return null;
    }

    private static boolean isAdminRole(String role) {
        return "ADMIN".equalsIgnoreCase(role)
                || "ADMINISTRATOR".equalsIgnoreCase(role)
                || "SUPERADMIN".equalsIgnoreCase(role);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (!isBlank(value)) return value;
        }
        return null;
    }

    private static String joinName(String first, String last) {
        return firstNonBlank(
                isBlank(first) ? null : first.trim() + " " + (last == null ? "" : last.trim()),
                first,
                last
        );
    }

    /**
     * A resident's display name from whichever parts are present.
     *
     * <p>Prefers an explicit full name over assembling first and last, because a
     * server that supplies both has usually already decided how to render the
     * name. Returns null when nothing is available, so the caller can fall back
     * to the resident ID rather than showing an empty credit.
     */
    private static String joinResidentName(String fullName, String first, String last) {
        return firstNonBlank(fullName, joinName(first, last));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
