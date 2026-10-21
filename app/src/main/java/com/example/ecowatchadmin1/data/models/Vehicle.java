package com.example.ecowatchadmin1.data.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * One vehicle in the fleet, shown by the Vehicles half of the Personnel screen.
 *
 * <p>The status is a single value rather than a status plus a maintenance flag,
 * matching the server's own column and the three cards the screen shows. Keeping
 * "Maintenance" out of "Assigned" is the point: a truck under servicing must not
 * also be counted as out on a collection.
 */
public class Vehicle {

    public static final String STATUS_AVAILABLE = "Available";
    public static final String STATUS_ASSIGNED = "Assigned";
    public static final String STATUS_MAINTENANCE = "Maintenance";

    private String vehicleId;
    private String plateNumber;
    private String vehicleType;
    private String ratedLoadCapacity;
    private String status = STATUS_AVAILABLE;
    private String currentReportId;
    private String currentClusterId;
    private String assignedAt;
    private String lastServiceAt;
    private String lastActivityAt;
    private String notes;
    private String currentReportTitle;
    private String currentReportStatus;
    private Double currentReportLatitude;
    private Double currentReportLongitude;
    private String createdAt;
    private String updatedAt;

    /**
     * True when the vehicle is out on a collection.
     *
     * <p>Driven by the status alone rather than by the presence of a report id,
     * so a vehicle parked in a state the server and the app disagree about still
     * reports the state the card is coloured by.
     */
    public boolean isAssigned() {
        return STATUS_ASSIGNED.equalsIgnoreCase(status);
    }

    public boolean isInMaintenance() {
        return STATUS_MAINTENANCE.equalsIgnoreCase(status);
    }

    public boolean isAvailable() {
        return STATUS_AVAILABLE.equalsIgnoreCase(status);
    }

    /** What the card shows as the vehicle's current task. */
    @Nullable
    public String getCurrentCollection() {
        if (currentReportTitle != null && !currentReportTitle.trim().isEmpty()) {
            return currentReportTitle.trim();
        }
        if (currentReportId != null && !currentReportId.trim().isEmpty()) {
            return currentReportId.trim();
        }
        return null;
    }

    /**
     * The status in the screen's own vocabulary, never null.
     *
     * <p>An unrecognised status is reported as Available rather than swallowed, so
     * a server that grows a fourth state shows up as a wrong colour instead of an
     * empty card.
     */
    @NonNull
    public String getStatusLabel() {
        if (isAssigned()) return STATUS_ASSIGNED;
        if (isInMaintenance()) return STATUS_MAINTENANCE;
        return STATUS_AVAILABLE;
    }

    /** The card's secondary line: the rated load, when one was recorded. */
    @Nullable
    public String getRatedLoadLabel() {
        if (ratedLoadCapacity == null || ratedLoadCapacity.trim().isEmpty()) return null;
        return ratedLoadCapacity.trim();
    }

    public boolean matchesQuery(String query) {
        if (query == null || query.trim().isEmpty()) return true;
        String needle = query.trim().toLowerCase(Locale.getDefault());
        return contains(vehicleId, needle)
                || contains(plateNumber, needle)
                || contains(vehicleType, needle)
                || contains(status, needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(needle);
    }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public String getRatedLoadCapacity() { return ratedLoadCapacity; }
    public void setRatedLoadCapacity(String ratedLoadCapacity) { this.ratedLoadCapacity = ratedLoadCapacity; }

    public String getStatus() { return status; }
    public void setStatus(String status) {
        this.status = status == null || status.trim().isEmpty() ? STATUS_AVAILABLE : status.trim();
    }

    public String getCurrentReportId() { return currentReportId; }
    public void setCurrentReportId(String currentReportId) { this.currentReportId = currentReportId; }

    public String getCurrentClusterId() { return currentClusterId; }
    public void setCurrentClusterId(String currentClusterId) { this.currentClusterId = currentClusterId; }

    public String getAssignedAt() { return assignedAt; }
    public void setAssignedAt(String assignedAt) { this.assignedAt = assignedAt; }

    public String getLastServiceAt() { return lastServiceAt; }
    public void setLastServiceAt(String lastServiceAt) { this.lastServiceAt = lastServiceAt; }

    public String getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(String lastActivityAt) { this.lastActivityAt = lastActivityAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCurrentReportTitle() { return currentReportTitle; }
    public void setCurrentReportTitle(String currentReportTitle) { this.currentReportTitle = currentReportTitle; }

    public String getCurrentReportStatus() { return currentReportStatus; }
    public void setCurrentReportStatus(String currentReportStatus) { this.currentReportStatus = currentReportStatus; }

    public Double getCurrentReportLatitude() { return currentReportLatitude; }
    public void setCurrentReportLatitude(Double currentReportLatitude) { this.currentReportLatitude = currentReportLatitude; }

    public Double getCurrentReportLongitude() { return currentReportLongitude; }
    public void setCurrentReportLongitude(Double currentReportLongitude) { this.currentReportLongitude = currentReportLongitude; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
