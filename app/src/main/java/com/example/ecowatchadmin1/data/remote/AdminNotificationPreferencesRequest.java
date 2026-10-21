package com.example.ecowatchadmin1.data.remote;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

/**
 * Body for {@code PUT /api/notifications/admin-preferences}.
 *
 * <p>Carries only the toggle that changed, boxed so an untouched field is
 * omitted from the JSON entirely. Sending the whole set would let a stale local
 * value for another category overwrite a change made on another device.
 */
public class AdminNotificationPreferencesRequest {

    @SerializedName("masterEnabled")
    private Boolean masterEnabled;

    @SerializedName("reportStatusUpdates")
    private Boolean reportStatusUpdates;

    @SerializedName("reportCompleted")
    private Boolean reportCompleted;

    @SerializedName("reportDenied")
    private Boolean reportDenied;

    @SerializedName("cenroAnnouncements")
    private Boolean cenroAnnouncements;

    @SerializedName("securityAlerts")
    private Boolean securityAlerts;

    @SerializedName("highPriorityReports")
    private Boolean highPriorityReports;

    @SerializedName("personnelAccepted")
    private Boolean personnelAccepted;

    @SerializedName("personnelCleaning")
    private Boolean personnelCleaning;

    @SerializedName("photoUpdates")
    private Boolean photoUpdates;

    @SerializedName("awaitingApproval")
    private Boolean awaitingApproval;

    @SerializedName("archivedReports")
    private Boolean archivedReports;

    @SerializedName("maintenanceAlerts")
    private Boolean maintenanceAlerts;

    @SerializedName("soundEnabled")
    private Boolean soundEnabled;

    @SerializedName("vibrationEnabled")
    private Boolean vibrationEnabled;

    @SerializedName("soundUri")
    private String soundUri;

    private AdminNotificationPreferencesRequest() {
    }

    /**
     * Builds a request for whichever field the caller names.
     *
     * <p>A single factory rather than fifteen: the fragment binds categories
     * generically, so it has no compile-time list of fields to call named
     * builders for. The field name is validated here so a typo fails at the call
     * site instead of as a 400 from the server.
     */
    public static AdminNotificationPreferencesRequest forField(
            @NonNull String field,
            boolean enabled) {
        AdminNotificationPreferencesRequest request = new AdminNotificationPreferencesRequest();
        switch (field) {
            case "masterEnabled": request.masterEnabled = enabled; break;
            case "reportStatusUpdates": request.reportStatusUpdates = enabled; break;
            case "reportCompleted": request.reportCompleted = enabled; break;
            case "reportDenied": request.reportDenied = enabled; break;
            case "cenroAnnouncements": request.cenroAnnouncements = enabled; break;
            case "securityAlerts": request.securityAlerts = enabled; break;
            case "highPriorityReports": request.highPriorityReports = enabled; break;
            case "personnelAccepted": request.personnelAccepted = enabled; break;
            case "personnelCleaning": request.personnelCleaning = enabled; break;
            case "photoUpdates": request.photoUpdates = enabled; break;
            case "awaitingApproval": request.awaitingApproval = enabled; break;
            case "archivedReports": request.archivedReports = enabled; break;
            case "maintenanceAlerts": request.maintenanceAlerts = enabled; break;
            case "soundEnabled": request.soundEnabled = enabled; break;
            case "vibrationEnabled": request.vibrationEnabled = enabled; break;
            default:
                throw new IllegalArgumentException("Unknown preference field: " + field);
        }
        return request;
    }

    /** Builds a request carrying only the chosen ringtone. */
    public static AdminNotificationPreferencesRequest forSoundUri(String uri) {
        AdminNotificationPreferencesRequest request = new AdminNotificationPreferencesRequest();
        request.soundUri = (uri == null || uri.trim().isEmpty()) ? null : uri.trim();
        return request;
    }
}
