package com.example.ecowatchadmin1.data.models;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * One step in a task's cleaning timeline.
 *
 * <p>The server sends these in the order they happened, read from the report's
 * audit trail. Only the phases that describe the work itself are included, so
 * the list reads as a story rather than as a status log. {@code adminId} is set
 * only on the administrator's events, which is how the UI tells an approval or
 * revision request apart from something the cleaner did.
 */
public class ReportTimelineEvent implements Serializable {

    @SerializedName(value = "action", alternate = {"type"})
    private String action;

    /** The server's human-readable sentence for the event, used as a fallback. */
    @SerializedName("description")
    private String description;

    /** Set when an administrator caused the event; null for cleaner actions. */
    @SerializedName(value = "adminId", alternate = {"admin_id"})
    private String adminId;

    @SerializedName(value = "timestamp", alternate = {"createdAt", "created_at"})
    private String timestamp;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    /** True when an administrator caused this event. */
    public boolean isAdminEvent() {
        return adminId != null && !adminId.trim().isEmpty();
    }
}
