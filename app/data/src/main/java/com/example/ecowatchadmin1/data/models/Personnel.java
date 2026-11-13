package com.example.ecowatchadmin1.data.models;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.Locale;

/**
 * Server-owned personnel account returned by the admin personnel endpoints.
 *
 * This is intentionally not a Room entity. Personnel data is never replaced by
 * local sample records; the API response is the source of truth for the screen.
 */
public class Personnel implements Serializable {
    /**
     * The two occupations a cleanup team member can have.
     *
     * <p>A Regular cleaner walks the route; a Driver also drives one of the
     * trucks, which is what makes an assigned vehicle part of the account rather
     * than a separate mapping. Stored as the server's own words so the two sides
     * of the wire agree, and read through {@link #isDriver()} so a server that
     * spells it differently still paints the right card.
     */
    public static final String OCCUPATION_REGULAR = "Regular";
    public static final String OCCUPATION_DRIVER = "Driver";

    @SerializedName(value = "personnelId", alternate = {"personnel_id", "id", "accountId"})
    private String personnelId;

    @SerializedName(value = "occupation", alternate = {"occupationType", "occupation_type"})
    private String occupation;

    @SerializedName(value = "teamName", alternate = {"team_name"})
    private String teamName;

    @SerializedName(value = "teamRole", alternate = {"team_role"})
    private String teamRole;

    /** The truck this person drives. Server-owned; set during registration. */
    @SerializedName(value = "assignedVehicleId", alternate = {
            "assigned_vehicle_id", "vehicleId", "vehicle_id"
    })
    private String assignedVehicleId;

    /** Carried alongside the id so the card can name the truck without a request. */
    @SerializedName(value = "assignedVehiclePlateNumber", alternate = {
            "assigned_vehicle_plate_number", "vehiclePlateNumber", "vehicle_plate_number",
            "plateNumber", "plate_number"
    })
    private String assignedVehiclePlateNumber;

    private String firstName;
    private String lastName;
    private String fullName;

    @SerializedName(value = "gmail", alternate = {"email"})
    private String gmail;

    private String username;
    private String address;

    @SerializedName(value = "phoneNumber", alternate = {"phone_number"})
    private String phoneNumber;

    @SerializedName(value = "messengerLink", alternate = {"messenger_link"})
    private String messengerLink;

    @SerializedName(value = "profileImage", alternate = {
            "profile_image", "avatarUrl", "avatar_url", "profileImageUrl",
            "profile_image_url"
    })
    private String profileImageUrl;

    private boolean online;
    private boolean cleaning;
    private boolean enabled = true;
    private String status;
    private String lastActive;

    @SerializedName(value = "currentAssignment", alternate = {"current_assignment"})
    private String currentAssignment;

    private String currentTask;
    private String assignmentReportId;
    private Double assignmentLatitude;
    private Double assignmentLongitude;

    @SerializedName(value = "dateCreated", alternate = {"createdAt", "created_at"})
    private String dateCreated;

    @SerializedName(value = "updatedAt", alternate = {"updated_at"})
    private String updatedAt;

    /**
     * The last collection this person completed.
     *
     * <p>Read from the server when it exposes the timestamp, and kept under
     * alternate names so a deployment that spells it differently still fills
     * the card. {@link #getLastCollection()} supplies the display fallback.
     */
    @SerializedName(value = "lastCollectionAt", alternate = {
            "last_collection_at", "lastCollection", "last_collection",
            "lastCompletedAt", "last_completed_at"
    })
    private String lastCollectionAt;

    public Personnel() {
        // Gson and the response mapper use this constructor.
    }

    public String getPersonnelId() {
        return personnelId;
    }

    public void setPersonnelId(String personnelId) {
        this.personnelId = personnelId;
    }

    public String getOccupation() {
        return occupation;
    }

    public void setOccupation(String occupation) {
        this.occupation = occupation;
    }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getTeamRole() { return teamRole; }
    public void setTeamRole(String teamRole) { this.teamRole = teamRole; }

    /**
     * True when this person drives one of the trucks.
     *
     * <p>Read from the occupation alone rather than from the presence of a
     * vehicle, so a driver who has not been given a truck yet still reads as a
     * driver rather than silently demoted to a regular cleaner.
     */
    public boolean isDriver() {
        return occupation != null && occupation.trim().toLowerCase(Locale.ROOT).contains("driver");
    }

    /** The occupation in the screen's own vocabulary, never null. */
    public String getOccupationLabel() {
        return isDriver() ? OCCUPATION_DRIVER : OCCUPATION_REGULAR;
    }

    public String getAssignedVehicleId() {
        return assignedVehicleId;
    }

    public void setAssignedVehicleId(String assignedVehicleId) {
        this.assignedVehicleId = assignedVehicleId;
    }

    public String getAssignedVehiclePlateNumber() {
        return assignedVehiclePlateNumber;
    }

    public void setAssignedVehiclePlateNumber(String assignedVehiclePlateNumber) {
        this.assignedVehiclePlateNumber = assignedVehiclePlateNumber;
    }

    /**
     * True when there is a truck to name on this account's card.
     *
     * <p>The id is enough: the plate is carried for display, but a server that
     * returns only the id still leaves the card something real to show.
     */
    public boolean hasAssignedVehicle() {
        return assignedVehicleId != null && !assignedVehicleId.trim().isEmpty();
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFullName() {
        String resolved = fullName;
        if (resolved == null || resolved.trim().isEmpty()) {
            String first = firstName == null ? "" : firstName.trim();
            String last = lastName == null ? "" : lastName.trim();
            resolved = (first + " " + last).trim();
        }
        return resolved.trim().isEmpty() ? "Unnamed personnel" : resolved.trim();
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getGmail() {
        return gmail;
    }

    public void setGmail(String gmail) {
        this.gmail = gmail;
    }

    public String getEmail() {
        return gmail;
    }

    public void setEmail(String email) {
        this.gmail = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getMessengerLink() {
        return messengerLink;
    }

    public void setMessengerLink(String messengerLink) {
        this.messengerLink = messengerLink;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public boolean isAvailable() {
        return enabled && !cleaning
                && (currentAssignment == null || currentAssignment.trim().isEmpty())
                && (currentTask == null || currentTask.trim().isEmpty())
                && (assignmentReportId == null || assignmentReportId.trim().isEmpty());
    }

    public boolean isOnline() {
        return online && !cleaning;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public boolean isCleaning() {
        return cleaning;
    }

    public void setCleaning(boolean cleaning) {
        this.cleaning = cleaning;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStatus() {
        if (!enabled) {
            return "Disabled";
        }
        if (cleaning) {
            return "Cleaning";
        }
        if (online) {
            return "Online";
        }
        return "Offline";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRawStatus() {
        return status;
    }

    public String getLastActive() {
        return lastActive;
    }

    public void setLastActive(String lastActive) {
        this.lastActive = lastActive;
    }

    public String getCurrentAssignment() {
        if (currentAssignment != null && !currentAssignment.trim().isEmpty()) {
            return currentAssignment;
        }
        return currentTask;
    }

    public void setCurrentAssignment(String currentAssignment) {
        this.currentAssignment = currentAssignment;
    }

    public String getCurrentTask() {
        if (currentTask != null && !currentTask.trim().isEmpty()) return currentTask;
        return currentAssignment;
    }

    public void setCurrentTask(String currentTask) {
        this.currentTask = currentTask;
    }

    public String getAssignmentReportId() {
        return assignmentReportId;
    }

    public void setAssignmentReportId(String assignmentReportId) {
        this.assignmentReportId = assignmentReportId;
    }

    public Double getAssignmentLatitude() {
        return assignmentLatitude;
    }

    public void setAssignmentLatitude(Double assignmentLatitude) {
        this.assignmentLatitude = assignmentLatitude;
    }

    public Double getAssignmentLongitude() {
        return assignmentLongitude;
    }

    public void setAssignmentLongitude(Double assignmentLongitude) {
        this.assignmentLongitude = assignmentLongitude;
    }

    public boolean hasAssignmentLocation() {
        return assignmentLatitude != null && assignmentLongitude != null
                && !Double.isNaN(assignmentLatitude) && !Double.isInfinite(assignmentLatitude)
                && !Double.isNaN(assignmentLongitude) && !Double.isInfinite(assignmentLongitude)
                && assignmentLatitude >= -90 && assignmentLatitude <= 90
                && assignmentLongitude >= -180 && assignmentLongitude <= 180
                && !(assignmentLatitude == 0 && assignmentLongitude == 0);
    }

    public String getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(String dateCreated) {
        this.dateCreated = dateCreated;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getLastCollectionAt() {
        return lastCollectionAt;
    }

    public void setLastCollectionAt(String lastCollectionAt) {
        this.lastCollectionAt = lastCollectionAt;
    }

    /**
     * The stamp the card labels "Last Collection".
     *
     * <p>Only a completed collection counts. Returning {@code lastActive} here
     * would make a sign-in look like a cleanup, which is exactly the
     * inaccuracy this column used to show. A blank value means the person has
     * not finished a collection and the card says so.
     */
    public String getLastCollection() {
        if (lastCollectionAt != null && !lastCollectionAt.trim().isEmpty()) {
            return lastCollectionAt;
        }
        return null;
    }
}
