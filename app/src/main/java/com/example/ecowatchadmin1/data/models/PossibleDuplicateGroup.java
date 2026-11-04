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
    /**
     * The canonical waste scale every report in this group was filed under.
     * A group never mixes scales: the review is about one pile at one spot,
     * and a small pile and a large one are separate incidents even when they
     * are metres apart.
     */
    private String wasteScale;

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public List<Report> getReports() { return reports; }
    public void setReports(List<Report> reports) { this.reports = reports; }

    public Double getCenterLatitude() { return centerLatitude; }
    public void setCenterLatitude(Double centerLatitude) { this.centerLatitude = centerLatitude; }

    public Double getCenterLongitude() { return centerLongitude; }
    public void setCenterLongitude(Double centerLongitude) { this.centerLongitude = centerLongitude; }

    /** The canonical scale, falling back to the first report when absent. */
    public String getWasteScale() {
        if (wasteScale != null && !wasteScale.trim().isEmpty()) {
            return wasteScale.trim();
        }
        if (reports != null) {
            for (Report report : reports) {
                if (report != null) return report.getWasteScale();
            }
        }
        return Report.SCALE_SMALL;
    }

    public void setWasteScale(String wasteScale) { this.wasteScale = wasteScale; }

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
