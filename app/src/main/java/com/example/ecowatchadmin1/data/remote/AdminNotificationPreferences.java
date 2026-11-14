package com.example.ecowatchadmin1.data.remote;

import com.google.gson.annotations.SerializedName;

/**
 * The administrator's notification toggles as the server stores them.
 *
 * <p>Every field defaults to {@code true} so a client that has never spoken to
 * the backend behaves like an administrator who has never touched their
 * settings, and an absent value can never be read as "off".
 *
 * <p>Covers all thirteen Admin app categories, including ones the backend does
 * not yet produce an event for. An unsyncable toggle is the same defect as an
 * unenforced one.
 */
public class AdminNotificationPreferences {

    @SerializedName("masterEnabled")
    private boolean masterEnabled = true;

    @SerializedName("reportStatusUpdates")
    private boolean reportStatusUpdates = true;

    @SerializedName("reportCompleted")
    private boolean reportCompleted = true;

    @SerializedName("reportDenied")
    private boolean reportDenied = true;

    @SerializedName("securityAlerts")
    private boolean securityAlerts = true;

    @SerializedName("highPriorityReports")
    private boolean highPriorityReports = true;

    @SerializedName("personnelAccepted")
    private boolean personnelAccepted = true;

    @SerializedName("personnelCleaning")
    private boolean personnelCleaning = true;

    @SerializedName("photoUpdates")
    private boolean photoUpdates = true;

    @SerializedName("awaitingApproval")
    private boolean awaitingApproval = true;

    @SerializedName("archivedReports")
    private boolean archivedReports = true;

    @SerializedName("maintenanceAlerts")
    private boolean maintenanceAlerts = true;

    @SerializedName("soundEnabled")
    private boolean soundEnabled = true;

    @SerializedName("vibrationEnabled")
    private boolean vibrationEnabled = true;

    @SerializedName("soundUri")
    private String soundUri;

    public boolean isMasterEnabled() { return masterEnabled; }
    public void setMasterEnabled(boolean value) { this.masterEnabled = value; }

    public boolean isReportStatusUpdates() { return reportStatusUpdates; }
    public void setReportStatusUpdates(boolean value) { this.reportStatusUpdates = value; }

    public boolean isReportCompleted() { return reportCompleted; }
    public void setReportCompleted(boolean value) { this.reportCompleted = value; }

    public boolean isReportDenied() { return reportDenied; }
    public void setReportDenied(boolean value) { this.reportDenied = value; }

    public boolean isSecurityAlerts() { return securityAlerts; }
    public void setSecurityAlerts(boolean value) { this.securityAlerts = value; }

    public boolean isHighPriorityReports() { return highPriorityReports; }
    public void setHighPriorityReports(boolean value) { this.highPriorityReports = value; }

    public boolean isPersonnelAccepted() { return personnelAccepted; }
    public void setPersonnelAccepted(boolean value) { this.personnelAccepted = value; }

    public boolean isPersonnelCleaning() { return personnelCleaning; }
    public void setPersonnelCleaning(boolean value) { this.personnelCleaning = value; }

    public boolean isPhotoUpdates() { return photoUpdates; }
    public void setPhotoUpdates(boolean value) { this.photoUpdates = value; }

    public boolean isAwaitingApproval() { return awaitingApproval; }
    public void setAwaitingApproval(boolean value) { this.awaitingApproval = value; }

    public boolean isArchivedReports() { return archivedReports; }
    public void setArchivedReports(boolean value) { this.archivedReports = value; }

    public boolean isMaintenanceAlerts() { return maintenanceAlerts; }
    public void setMaintenanceAlerts(boolean value) { this.maintenanceAlerts = value; }

    public boolean isSoundEnabled() { return soundEnabled; }
    public void setSoundEnabled(boolean value) { this.soundEnabled = value; }

    public boolean isVibrationEnabled() { return vibrationEnabled; }
    public void setVibrationEnabled(boolean value) { this.vibrationEnabled = value; }

    public String getSoundUri() { return soundUri; }
    public void setSoundUri(String value) { this.soundUri = value; }
}
