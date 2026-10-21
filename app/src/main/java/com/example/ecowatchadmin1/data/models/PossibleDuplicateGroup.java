package com.example.ecowatchadmin1.data.models;

import java.util.ArrayList;
import java.util.List;

/**
 * One review group from the "Possible Duplicate Reports" endpoint: reports
 * the backend believes may describe the same waste incident. The stored
 * server-side grouping key is never shown to administrators.
 */
public class PossibleDuplicateGroup {
    private String groupId;
    private List<Report> reports = new ArrayList<>();
    private Double centerLatitude;
    private Double centerLongitude;

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public List<Report> getReports() { return reports; }
    public void setReports(List<Report> reports) { this.reports = reports; }

    public Double getCenterLatitude() { return centerLatitude; }
    public void setCenterLatitude(Double centerLatitude) { this.centerLatitude = centerLatitude; }

    public Double getCenterLongitude() { return centerLongitude; }
    public void setCenterLongitude(Double centerLongitude) { this.centerLongitude = centerLongitude; }

    /** Reports whose status is Pending right now. */
    public List<Report> getPendingReports() {
        List<Report> pending = new ArrayList<>();
        if (reports == null) return pending;
        for (Report report : reports) {
            if (report != null && "Pending".equalsIgnoreCase(report.getStatus())) {
                pending.add(report);
            }
        }
        return pending;
    }
}
