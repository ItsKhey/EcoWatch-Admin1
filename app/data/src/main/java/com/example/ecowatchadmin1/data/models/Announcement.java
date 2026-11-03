package com.example.ecowatchadmin1.data.models;

import com.example.ecowatchadmin1.utils.NotificationDateFormatter;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/** A CENRO announcement as returned by {@code GET /api/announcements}. */
public class Announcement implements Serializable {

    @SerializedName("announcementId")
    private String announcementId;

    @SerializedName("title")
    private String title;

    @SerializedName("message")
    private String message;

    @SerializedName("audience")
    private String audience;

    @SerializedName("barangay")
    private String barangay;

    @SerializedName("targetResidentIds")
    private List<String> targetResidentIds;

    @SerializedName("priority")
    private String priority;

    @SerializedName("createdByAdminId")
    private String createdByAdminId;

    @SerializedName("createdAt")
    private String createdAt;

    /**
     * When the announcement stops being delivered and is permanently deleted.
     * Null means the announcement was published without a timer.
     */
    @SerializedName("expiresAt")
    private String expiresAt;

    public String getAnnouncementId() { return announcementId; }
    public void setAnnouncementId(String announcementId) { this.announcementId = announcementId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }

    public String getBarangay() { return barangay; }
    public void setBarangay(String barangay) { this.barangay = barangay; }

    public List<String> getTargetResidentIds() { return targetResidentIds; }
    public void setTargetResidentIds(List<String> targetResidentIds) { this.targetResidentIds = targetResidentIds; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getCreatedByAdminId() { return createdByAdminId; }
    public void setCreatedByAdminId(String createdByAdminId) { this.createdByAdminId = createdByAdminId; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

    /** True when this announcement was published without an expiry timer. */
    public boolean neverExpires() {
        return expiresAt == null || expiresAt.trim().isEmpty();
    }

    /**
     * True when the announcement carries a deadline that has already passed.
     *
     * <p>Kept for display only: the backend filters expired rows out of every
     * read, so one showing up here means the row was cached before it lapsed.
     */
    public boolean isExpired() {
        if (neverExpires()) return false;
        Date deadline = NotificationDateFormatter.parse(expiresAt);
        return deadline != null && deadline.before(new Date());
    }

    /**
     * True when the announcement was published with raised priority.
     *
     * <p>The backend stores {@code NORMAL} and {@code IMPORTANT}, but the
     * composer sends the title-cased labels, so both spellings are accepted.
     */
    public boolean isImportant() {
        return "IMPORTANT".equalsIgnoreCase(priority)
                || "Important".equalsIgnoreCase(priority);
    }
}
