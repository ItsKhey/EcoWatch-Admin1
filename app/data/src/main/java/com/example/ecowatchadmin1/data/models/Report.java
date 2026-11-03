package com.example.ecowatchadmin1.data.models;

import android.text.TextUtils;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.example.ecowatchadmin1.utils.LongDateAdapter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity(tableName = "reports")
public class Report implements Serializable {
    @PrimaryKey(autoGenerate = true)
    private int id;

    @SerializedName(value = "_id", alternate = {"mongoId", "mongo_id"})
    private String mongoId;

    @SerializedName(value = "reportId", alternate = {"report_id"})
    private String reportId;

    @SerializedName(value = "residentId", alternate = {"resident_id"})
    private String residentId;

    /**
     * The resident's display name, joined onto the report by the server.
     *
     * <p>Null when the resident's account has been deleted or the report predates
     * the join. Callers must fall back to the resident ID rather than assume it is
     * present.
     */
    @SerializedName(value = "residentName", alternate = {"resident_name"})
    private String residentName;

    private String title;
    private String description;

    @SerializedName(value = "category", alternate = {"wasteType", "waste_type"})
    private String wasteType;

    @SerializedName(value = "imageUrl", alternate = {"image_url", "photoUrl", "photo_url"})
    private String photoUrl;

    /**
     * Every photo attached to the report, in upload order. The server sends this
     * as {@code photos}; {@link #photoUrl} only holds the cover image, so this
     * list is what lets the admin list show how many photos a report carries.
     */
    @SerializedName(value = "photos", alternate = {"photoUrls", "imageUrls"})
    private List<String> photoUrls = new ArrayList<>();

    private String localPhotoPath; // New field for local storage

    private double latitude;
    private double longitude;
    private String status;

    @SerializedName(value = "adminResponse", alternate = {"admin_response"})
    private String adminResponse;

    /**
     * The administrator's note for the assigned cleaner: assignment
     * instructions and revision reasons. Kept apart from {@link #adminResponse}
     * because the two have different audiences — this one is personnel-only and
     * never reaches the resident who filed the report.
     */
    @SerializedName(value = "personnelNote", alternate = {"personnel_note"})
    private String personnelNote;

    /**
     * True while the current assignment has been sent back for revision.
     *
     * <p>The server derives this from its audit trail rather than from
     * {@link #personnelNote}, because that column also carries ordinary
     * assignment instructions. An older server omits the field, which reads as
     * false: a report is only labelled "Revised" when the server says an
     * administrator actually requested it.
     *
     * <p>Not persisted locally, because it describes the server's current state
     * and every history screen reloads that state before binding it.
     */
    @Ignore
    @SerializedName(value = "revisionRequested", alternate = {"revision_requested"})
    private boolean revisionRequested;

    /**
     * The note the assigned personnel wrote when they finished the cleanup.
     *
     * <p>This is the mirror image of {@link #personnelNote}: that field is the
     * administrator talking to the cleaner, this one is the cleaner talking back.
     * They stay separate because only the administrator can be trusted with an
     * internal instruction, and only the cleaner knows what actually got done.
     *
     * <p>Null until the assignment is completed. It does not live on the report
     * row upstream, because approving a report releases the assignment; the
     * server joins the newest completed assignment on at read time.
     */
    @SerializedName(value = "personnelResponse", alternate = {"personnel_response",
            "completionNotes", "completion_notes"})
    private String personnelResponse;

    /**
     * The personnel account that performed the cleanup, resolved from the same
     * completed assignment as {@link #personnelResponse}.
     *
     * <p>Kept apart from {@link #assignedPersonnelName} on purpose. Approving a
     * report releases the active assignment, so the assigned fields go blank
     * exactly when the administrator wants to know who did the work; these
     * fields keep that credit after the assignment is gone.
     */
    @SerializedName(value = "completedByPersonnelId", alternate = {
            "completed_by_personnel_id", "completedPersonnelId"
    })
    private String completedByPersonnelId;

    @SerializedName(value = "completedByPersonnelName", alternate = {
            "completed_by_personnel_name", "completedPersonnelName"
    })
    private String completedByPersonnelName;

    @SerializedName(value = "completedByPersonnelAvatar", alternate = {
            "completed_by_personnel_avatar", "completedPersonnelAvatar"
    })
    private String completedByPersonnelAvatar;

    /** When the cleaner submitted the finished work. */
    @SerializedName(value = "workCompletedAt", alternate = {"work_completed_at"})
    private String workCompletedAt;

    @SerializedName(value = "createdAt", alternate = {"created_at"})
    private String createdAt;

    @SerializedName(value = "updatedAt", alternate = {"updated_at"})
    private String updatedAt;

    @SerializedName(value = "isSynced", alternate = {"is_synced"})
    private boolean isSynced;

    @SerializedName(value = "isDeleted", alternate = {"is_deleted"})
    private boolean isDeleted;

    @SerializedName(value = "deletedAt", alternate = {"deleted_at"})
    @JsonAdapter(LongDateAdapter.class)
    private Long deletedAt;

    @SerializedName(value = "isArchived", alternate = {"is_archived"})
    private boolean isArchived;

    @SerializedName(value = "isRestored", alternate = {"is_restored"})
    private boolean isRestored;

    @SerializedName(value = "assignedPersonnelIds", alternate = {"assigned_personnel_ids"})
    private List<String> assignedPersonnelIds;

    /**
     * The server-owned single assignment used by Phase 2B. The legacy
     * assignedPersonnelIds list is retained for resident and archived-report
     * compatibility, but new admin assignments are always represented by
     * this canonical field.
     */
    @SerializedName(value = "assignedPersonnelId", alternate = {"assigned_personnel_id"})
    private String assignedPersonnelId;

    @SerializedName(value = "assignedPersonnelName", alternate = {"assigned_personnel_name"})
    private String assignedPersonnelName;

    @SerializedName(value = "assignedPersonnelAvatar", alternate = {
            "assigned_personnel_avatar", "assignedPersonnelAvatarUrl",
            "assigned_personnel_avatar_url"
    })
    private String assignedPersonnelAvatar;

    @SerializedName(value = "assignedByAdminId", alternate = {"assigned_by_admin_id"})
    private String assignedByAdminId;

    @SerializedName(value = "assignedAt", alternate = {"assigned_at"})
    private String assignedAt;

    private String priority;

    @SerializedName(value = "handledById", alternate = {"handled_by_id"})
    private String handledById;

    @SerializedName(value = "resolutionImageUrl", alternate = {"resolution_image_url"})
    private String resolutionImageUrl;

    @SerializedName(value = "resolvedAt", alternate = {"resolved_at"})
    private String resolvedAt;

    @SerializedName(value = "clusterId", alternate = {"cluster_id"})
    private String clusterId;

    /**
     * How big the reported waste is.
     *
     * <p>The resident picks this when they file the report, and it is the label
     * the dispatch is bound by: one cleaner for a small job, a cleaning team
     * for a medium one, a team with a collection truck for a large one. Reports
     * filed before the field existed are small jobs, which is the only way they
     * could have been handled then.
     */
    @SerializedName(value = "wasteScale", alternate = {"waste_scale"})
    private String wasteScale;

    /** The three accepted scales, in increasing order of effort. */
    public static final String SCALE_SMALL = "Small-Scale Waste";
    public static final String SCALE_MEDIUM = "Medium-Scale Waste";
    public static final String SCALE_LARGE = "Large-Scale Waste";

    /** The cleaning crew that was dispatched for this report, if any. */
    @SerializedName(value = "assignedTeamId", alternate = {"assigned_team_id"})
    private String assignedTeamId;

    @SerializedName(value = "assignedTeamName", alternate = {"assigned_team_name"})
    private String assignedTeamName;

    @SerializedName(value = "assignedCrew", alternate = {"assigned_crew"})
    @Ignore
    private List<AssignedCrewMember> assignedCrew = new ArrayList<>();

    public static class AssignedCrewMember implements Serializable {
        @SerializedName(value = "personnelId", alternate = {"personnel_id"})
        public String personnelId;
        @SerializedName(value = "fullName", alternate = {"full_name"})
        public String fullName;
        @SerializedName(value = "leader", alternate = {"isLeader", "is_leader"})
        public boolean leader;
    }

    /** The collection truck that was dispatched for this report, if any. */
    @SerializedName(value = "assignedVehicleId", alternate = {"assigned_vehicle_id"})
    private String assignedVehicleId;

    @SerializedName(value = "assignedVehiclePlateNumber", alternate = {
            "assigned_vehicle_plate_number"
    })
    private String assignedVehiclePlateNumber;

    @SerializedName(value = "assignedVehicleType", alternate = {"assigned_vehicle_type"})
    private String assignedVehicleType;

    /** The scale the report is filed under, defaulting to the small one. */
    public String getWasteScale() {
        if (wasteScale != null && !wasteScale.trim().isEmpty()) {
            return wasteScale.trim();
        }
        return SCALE_SMALL;
    }

    /**
     * The scale exactly as the server sent it, or null when it sent none.
     *
     * <p>Needed where "no scale in this payload" and "a small report" mean
     * different things — merging a partial response must not turn a medium
     * report into a small one just because the mutation reply left the field
     * out.
     */
    public String getRawWasteScale() {
        return wasteScale;
    }

    public void setWasteScale(String wasteScale) { this.wasteScale = wasteScale; }

    /** True when the report needs a cleaning team rather than one cleaner. */
    public boolean requiresTeam() {
        return SCALE_MEDIUM.equals(getWasteScale()) || SCALE_LARGE.equals(getWasteScale());
    }

    /** True when the report needs a collection vehicle on site. */
    public boolean requiresVehicle() {
        return SCALE_LARGE.equals(getWasteScale());
    }

    /**
     * The crew that was dispatched, preferring the name the server joined on.
     *
     * <p>Falls back to the id when the name is missing, which happens for a team
     * that was deleted after it was dispatched. Showing the id is honest.
     */
    public String getAssignedTeamLabel() {
        if (assignedTeamName != null && !assignedTeamName.trim().isEmpty()) {
            return assignedTeamName.trim();
        }
        if (assignedTeamId != null && !assignedTeamId.trim().isEmpty()) {
            return assignedTeamId.trim();
        }
        return null;
    }

    /**
     * The truck that was dispatched, preferring the plate number.
     *
     * <p>The plate is what the crew and the resident actually recognise, so it
     * comes first; the type is kept as a fallback for a vehicle record that
     * never carried a plate.
     */
    public String getAssignedVehicleLabel() {
        if (assignedVehiclePlateNumber != null && !assignedVehiclePlateNumber.trim().isEmpty()) {
            String type = assignedVehicleType == null || assignedVehicleType.trim().isEmpty()
                    ? null : assignedVehicleType.trim();
            return type == null
                    ? assignedVehiclePlateNumber.trim()
                    : assignedVehiclePlateNumber.trim() + " (" + type + ")";
        }
        if (assignedVehicleType != null && !assignedVehicleType.trim().isEmpty()) {
            return assignedVehicleType.trim();
        }
        if (assignedVehicleId != null && !assignedVehicleId.trim().isEmpty()) {
            return assignedVehicleId.trim();
        }
        return null;
    }

    /**
     * Cleaning proof photos captured by the personnel when the task started
     * (BEFORE) and finished (AFTER). The server flattens these into the report
     * list so the admin review dialog can show them without a second request.
     */
    @SerializedName(value = "beforePhotoUrls", alternate = {"before_photo_urls"})
    private List<String> beforePhotoUrls = new ArrayList<>();

    @SerializedName(value = "afterPhotoUrls", alternate = {"after_photo_urls"})
    private List<String> afterPhotoUrls = new ArrayList<>();

    /**
     * The ordered cleaning events for this task, when the endpoint supplies
     * them. Only the personnel history asks for them; every other list parses
     * the field as absent and keeps its compact card.
     *
     * <p>Not persisted locally for the same reason as
     * {@link #revisionRequested}: it describes the server's audit trail at read
     * time, and every history screen reloads before binding it.
     */
    @Ignore
    @SerializedName(value = "timeline", alternate = {"taskTimeline", "task_timeline"})
    private List<ReportTimelineEvent> timeline = new ArrayList<>();

    public Report(String title, String description, String wasteType, double latitude, double longitude) {
        this.title = title;
        this.description = description;
        this.wasteType = wasteType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = "Pending";
        this.isSynced = false;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getMongoId() { return mongoId; }
    public void setMongoId(String mongoId) { this.mongoId = mongoId; }

    public String getServerReportId() { return reportId; }
    public void setServerReportId(String reportId) { this.reportId = reportId; }

    public String getReportId() { return reportId; } 
    public void setReportId(String reportId) { this.reportId = reportId; }

    public String getResidentId() { return residentId; }
    public void setResidentId(String residentId) { this.residentId = residentId; }

    public String getResidentName() { return residentName; }
    public void setResidentName(String residentName) { this.residentName = residentName; }

    /**
     * Who to credit on the report, preferring the name.
     *
     * <p>Falls back to the resident ID when the name is missing, which happens for
     * reports whose resident account has been deleted. Showing the ID is honest;
     * showing "Unknown" would hide that an account is missing.
     */
    public String getResidentLabel() {
        if (residentName != null && !residentName.trim().isEmpty()) return residentName.trim();
        if (residentId != null && !residentId.trim().isEmpty()) return residentId.trim();
        return "Unknown resident";
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getWasteType() { return wasteType; }
    public void setWasteType(String wasteType) { this.wasteType = wasteType; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public List<String> getPhotoUrls() {
        return photoUrls == null ? new ArrayList<>() : photoUrls;
    }

    /**
     * Stores the photo list, dropping blanks and repeats. A list left empty by a
     * response that omitted the field must not wipe a previously known count.
     */
    public void setPhotoUrls(List<String> urls) {
        List<String> cleaned = new ArrayList<>();
        if (urls != null) {
            for (String url : urls) {
                if (url == null) continue;
                String trimmed = url.trim();
                if (trimmed.isEmpty() || cleaned.contains(trimmed)) continue;
                cleaned.add(trimmed);
            }
        }
        this.photoUrls = cleaned;
    }

    public String getLocalPhotoPath() { return localPhotoPath; }
    public void setLocalPhotoPath(String localPhotoPath) { this.localPhotoPath = localPhotoPath; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAdminResponse() { return adminResponse; }
    public void setAdminResponse(String adminResponse) { this.adminResponse = adminResponse; }

    public String getPersonnelNote() { return personnelNote; }
    public void setPersonnelNote(String personnelNote) { this.personnelNote = personnelNote; }

    /** True only when the server recorded an administrator revision request. */
    public boolean isRevisionRequested() { return revisionRequested; }
    public void setRevisionRequested(boolean revisionRequested) { this.revisionRequested = revisionRequested; }

    public String getPersonnelResponse() { return personnelResponse; }
    public void setPersonnelResponse(String personnelResponse) { this.personnelResponse = personnelResponse; }

    public String getCompletedByPersonnelId() { return completedByPersonnelId; }
    public void setCompletedByPersonnelId(String completedByPersonnelId) { this.completedByPersonnelId = completedByPersonnelId; }

    public String getCompletedByPersonnelName() { return completedByPersonnelName; }
    public void setCompletedByPersonnelName(String completedByPersonnelName) { this.completedByPersonnelName = completedByPersonnelName; }

    public String getCompletedByPersonnelAvatar() { return completedByPersonnelAvatar; }
    public void setCompletedByPersonnelAvatar(String completedByPersonnelAvatar) { this.completedByPersonnelAvatar = completedByPersonnelAvatar; }

    public String getWorkCompletedAt() { return workCompletedAt; }
    public void setWorkCompletedAt(String workCompletedAt) { this.workCompletedAt = workCompletedAt; }

    /**
     * True once a cleaner has submitted finished work for this report, whether or
     * not they left a note.
     */
    public boolean hasCompletedWork() {
        return completedByPersonnelId != null && !completedByPersonnelId.trim().isEmpty();
    }

    /** True when the cleaner submitted a note alongside the finished work. */
    public boolean hasPersonnelResponse() {
        return personnelResponse != null && !personnelResponse.trim().isEmpty();
    }

    /**
     * Who cleaned this report, preferring the live assignment and falling back to
     * the completed one.
     *
     * <p>Approving a report releases the assignment, so the live pointer is empty
     * on exactly the reports where the administrator still needs a name. The
     * completed assignment is kept for that reason.
     */
    public String getCleanerLabel() {
        if (assignedPersonnelName != null && !assignedPersonnelName.trim().isEmpty()) {
            return assignedPersonnelName.trim();
        }
        if (completedByPersonnelName != null && !completedByPersonnelName.trim().isEmpty()) {
            return completedByPersonnelName.trim();
        }
        if (completedByPersonnelId != null && !completedByPersonnelId.trim().isEmpty()) {
            return completedByPersonnelId.trim();
        }
        List<String> ids = getAssignedPersonnelIds();
        return ids.isEmpty() ? null : TextUtils.join(", ", ids);
    }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public boolean isSynced() { return isSynced; }
    public void setSynced(boolean synced) { isSynced = synced; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public Long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }

    public boolean isArchived() { return isArchived; }
    public void setArchived(boolean archived) { isArchived = archived; }

    public boolean isRestored() { return isRestored; }
    public void setRestored(boolean restored) { isRestored = restored; }

    public List<String> getAssignedPersonnelIds() {
        return assignedPersonnelIds == null ? new ArrayList<>() : assignedPersonnelIds;
    }

    public void setAssignedPersonnelIds(List<String> assignedPersonnelIds) { this.assignedPersonnelIds = assignedPersonnelIds; }

    public String getAssignedPersonnelId() {
        if (assignedPersonnelId != null && !assignedPersonnelId.trim().isEmpty()) {
            return assignedPersonnelId;
        }
        if (assignedPersonnelIds != null && assignedPersonnelIds.size() == 1) {
            return assignedPersonnelIds.get(0);
        }
        return null;
    }

    public void setAssignedPersonnelId(String assignedPersonnelId) {
        this.assignedPersonnelId = assignedPersonnelId;
    }

    public String getAssignedPersonnelName() { return assignedPersonnelName; }
    public void setAssignedPersonnelName(String assignedPersonnelName) { this.assignedPersonnelName = assignedPersonnelName; }

    public String getAssignedPersonnelAvatar() { return assignedPersonnelAvatar; }
    public void setAssignedPersonnelAvatar(String assignedPersonnelAvatar) { this.assignedPersonnelAvatar = assignedPersonnelAvatar; }

    public String getAssignedTeamId() { return assignedTeamId; }
    public void setAssignedTeamId(String assignedTeamId) { this.assignedTeamId = assignedTeamId; }

    public String getAssignedTeamName() { return assignedTeamName; }
    public void setAssignedTeamName(String assignedTeamName) { this.assignedTeamName = assignedTeamName; }

    public List<AssignedCrewMember> getAssignedCrew() {
        return assignedCrew == null ? new ArrayList<>() : assignedCrew;
    }
    public void setAssignedCrew(List<AssignedCrewMember> assignedCrew) {
        this.assignedCrew = assignedCrew == null ? new ArrayList<>() : assignedCrew;
    }

    public String getAssignedVehicleId() { return assignedVehicleId; }
    public void setAssignedVehicleId(String assignedVehicleId) { this.assignedVehicleId = assignedVehicleId; }

    public String getAssignedVehiclePlateNumber() { return assignedVehiclePlateNumber; }
    public void setAssignedVehiclePlateNumber(String assignedVehiclePlateNumber) {
        this.assignedVehiclePlateNumber = assignedVehiclePlateNumber;
    }

    public String getAssignedVehicleType() { return assignedVehicleType; }
    public void setAssignedVehicleType(String assignedVehicleType) { this.assignedVehicleType = assignedVehicleType; }

    public String getAssignedByAdminId() { return assignedByAdminId; }
    public void setAssignedByAdminId(String assignedByAdminId) { this.assignedByAdminId = assignedByAdminId; }

    public String getAssignedAt() { return assignedAt; }
    public void setAssignedAt(String assignedAt) { this.assignedAt = assignedAt; }

    public boolean hasAssignment() {
        return getAssignedPersonnelId() != null;
    }

    /**
     * True when anybody at all is assigned to this report.
     *
     * <p>A crew assignment deliberately leaves the single-personnel pointer
     * empty, because a crew has no single owner; the whole crew lives in the
     * assignment list. Checking only the pointer would make a dispatched crew
     * look like a report nobody has picked up, which would hide the unassign
     * and approve actions.
     */
    public boolean hasAssignedPersonnel() {
        if (hasAssignment()) return true;
        return !getAssignedPersonnelIds().isEmpty();
    }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getHandledById() { return handledById; }
    public void setHandledById(String handledById) { this.handledById = handledById; }

    public String getResolutionImageUrl() { return resolutionImageUrl; }
    public void setResolutionImageUrl(String resolutionImageUrl) { this.resolutionImageUrl = resolutionImageUrl; }

    public String getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(String resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getClusterId() { return clusterId; }
    public void setClusterId(String clusterId) { this.clusterId = clusterId; }

    public List<String> getBeforePhotoUrls() {
        return beforePhotoUrls == null ? new ArrayList<>() : beforePhotoUrls;
    }

    public void setBeforePhotoUrls(List<String> urls) {
        this.beforePhotoUrls = cleanPhotoUrls(urls);
    }

    public List<String> getAfterPhotoUrls() {
        return afterPhotoUrls == null ? new ArrayList<>() : afterPhotoUrls;
    }

    public void setAfterPhotoUrls(List<String> urls) {
        this.afterPhotoUrls = cleanPhotoUrls(urls);
    }

    /** The task's ordered cleaning events, never null. */
    public List<ReportTimelineEvent> getTimeline() {
        return timeline == null ? new ArrayList<>() : timeline;
    }

    public void setTimeline(List<ReportTimelineEvent> timeline) {
        this.timeline = timeline == null ? new ArrayList<>() : timeline;
    }

    /** Drops nulls, blanks and repeats so a sloppy payload cannot break the UI. */
    private static List<String> cleanPhotoUrls(List<String> urls) {
        List<String> cleaned = new ArrayList<>();
        if (urls != null) {
            for (String url : urls) {
                if (url == null) continue;
                String trimmed = url.trim();
                if (trimmed.isEmpty() || cleaned.contains(trimmed)) continue;
                cleaned.add(trimmed);
            }
        }
        return cleaned;
    }
}
