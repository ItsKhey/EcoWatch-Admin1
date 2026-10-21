package com.example.ecowatchadmin1.data.remote;

import com.google.gson.annotations.SerializedName;

/** Envelope for {@code GET/PUT /api/notifications/admin-preferences}. */
public class AdminNotificationPreferencesResponse {

    @SerializedName("success")
    private boolean success;

    @SerializedName("preferences")
    private AdminNotificationPreferences preferences;

    public boolean isSuccess() {
        return success;
    }

    /**
     * The stored choices. Never null: a response that omitted the field falls
     * back to an all-enabled set, matching the backend's fail-open default.
     */
    public AdminNotificationPreferences getPreferences() {
        return preferences == null ? new AdminNotificationPreferences() : preferences;
    }
}
