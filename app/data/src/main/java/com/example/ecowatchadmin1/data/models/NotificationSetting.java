package com.example.ecowatchadmin1.data.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notification_settings")
public class NotificationSetting {
    @PrimaryKey
    @NonNull
    private String residentId;

    private boolean reportStatusUpdates = true;
    private boolean reportCompleted = true;
    private boolean reportDenied = true;
    private boolean cenroAnnouncements = true;
    private boolean securityAlerts = true;
    private boolean soundEnabled = true;
    private String soundUri = ""; // Path to custom sound
    private boolean vibrationEnabled = true;
    private boolean masterEnabled = false;

    // Admin-specific categories (Phase 3A). The legacy columns above are reused
    // by the rows that map to them, so existing installations keep their
    // choices instead of silently reverting to defaults.
    private boolean highPriorityReports = true;
    private boolean personnelAccepted = true;
    private boolean personnelCleaning = true;
    private boolean photoUpdates = true;
    private boolean awaitingApproval = true;
    private boolean archivedReports = true;
    private boolean maintenanceAlerts = true;

    public NotificationSetting(@NonNull String residentId) {
        this.residentId = residentId;
    }

    @NonNull
    public String getResidentId() { return residentId; }
    public void setResidentId(@NonNull String residentId) { this.residentId = residentId; }

    public boolean isMasterEnabled() { return masterEnabled; }
    public void setMasterEnabled(boolean masterEnabled) { this.masterEnabled = masterEnabled; }

    /** "New Resident Reports" — reuses the legacy report-status column. */
    public boolean isNewReports() { return isReportStatusUpdates(); }
    public void setNewReports(boolean newReports) { setReportStatusUpdates(newReports); }

    public boolean isReportStatusUpdates() { return reportStatusUpdates; }
    public void setReportStatusUpdates(boolean reportStatusUpdates) { this.reportStatusUpdates = reportStatusUpdates; }

    /** "Emergency Reports" — reuses the legacy security-alert column. */
    public boolean isEmergencyReports() { return isSecurityAlerts(); }
    public void setEmergencyReports(boolean emergencyReports) { setSecurityAlerts(emergencyReports); }

    public boolean isSecurityAlerts() { return securityAlerts; }
    public void setSecurityAlerts(boolean securityAlerts) { this.securityAlerts = securityAlerts; }

    public boolean isHighPriorityReports() { return highPriorityReports; }
    public void setHighPriorityReports(boolean highPriorityReports) { this.highPriorityReports = highPriorityReports; }

    public boolean isPersonnelAccepted() { return personnelAccepted; }
    public void setPersonnelAccepted(boolean personnelAccepted) { this.personnelAccepted = personnelAccepted; }

    public boolean isPersonnelCleaning() { return personnelCleaning; }
    public void setPersonnelCleaning(boolean personnelCleaning) { this.personnelCleaning = personnelCleaning; }

    /** "Cleaning Completed" — reuses the legacy report-completed column. */
    public boolean isCleaningCompleted() { return isReportCompleted(); }
    public void setCleaningCompleted(boolean cleaningCompleted) { setReportCompleted(cleaningCompleted); }

    public boolean isReportCompleted() { return reportCompleted; }
    public void setReportCompleted(boolean reportCompleted) { this.reportCompleted = reportCompleted; }

    public boolean isPhotoUpdates() { return photoUpdates; }
    public void setPhotoUpdates(boolean photoUpdates) { this.photoUpdates = photoUpdates; }

    public boolean isAwaitingApproval() { return awaitingApproval; }
    public void setAwaitingApproval(boolean awaitingApproval) { this.awaitingApproval = awaitingApproval; }

    public boolean isReportDenied() { return reportDenied; }
    public void setReportDenied(boolean reportDenied) { this.reportDenied = reportDenied; }

    public boolean isArchivedReports() { return archivedReports; }
    public void setArchivedReports(boolean archivedReports) { this.archivedReports = archivedReports; }

    /** "Announcements" — reuses the legacy CENRO announcements column. */
    public boolean isAnnouncements() { return isCenroAnnouncements(); }
    public void setAnnouncements(boolean announcements) { setCenroAnnouncements(announcements); }

    public boolean isCenroAnnouncements() { return cenroAnnouncements; }
    public void setCenroAnnouncements(boolean cenroAnnouncements) { this.cenroAnnouncements = cenroAnnouncements; }

    public boolean isMaintenanceAlerts() { return maintenanceAlerts; }
    public void setMaintenanceAlerts(boolean maintenanceAlerts) { this.maintenanceAlerts = maintenanceAlerts; }

    public boolean isSoundEnabled() { return soundEnabled; }
    public void setSoundEnabled(boolean soundEnabled) { this.soundEnabled = soundEnabled; }

    public String getSoundUri() { return soundUri; }
    public void setSoundUri(String soundUri) { this.soundUri = soundUri; }

    public boolean isVibrationEnabled() { return vibrationEnabled; }
    public void setVibrationEnabled(boolean vibrationEnabled) { this.vibrationEnabled = vibrationEnabled; }
}
