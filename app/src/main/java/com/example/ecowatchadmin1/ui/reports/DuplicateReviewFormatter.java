package com.example.ecowatchadmin1.ui.reports;

import android.content.Context;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.remote.ServerConfig;
import com.example.ecowatchadmin1.utils.ReportSubmittedAtFormatter;
import com.example.ecowatchadmin1.utils.WasteScaleDisplay;

import java.io.File;

/**
 * Shared formatting for the Possible Duplicate Reports review surfaces.
 *
 * <p>The inline card rows, the SEE ALL dialog and the DENY picker all describe
 * the same reports to the administrator, so they must agree. Keeping the
 * distance, timestamp and photo lookups in one place is what stops the three
 * surfaces from quietly drifting apart.
 */
final class DuplicateReviewFormatter {

    private DuplicateReviewFormatter() {
    }

    /** Report reference for display, falling back to the mongo id. */
    static String referenceOf(Report report) {
        if (report == null) return "—";
        String id = report.getReportId() != null ? report.getReportId() : report.getMongoId();
        return id != null ? id : "—";
    }

    /** The stable id to send back to the server, or null when there is none. */
    static String serverIdOf(Report report) {
        if (report == null) return null;
        String id = report.getReportId() != null ? report.getReportId() : report.getMongoId();
        return id != null && !id.trim().isEmpty() ? id : null;
    }

    static String statusOf(Report report) {
        if (report == null || report.getStatus() == null || report.getStatus().trim().isEmpty()) {
            return "Pending";
        }
        return report.getStatus();
    }

    static String wasteTypeOf(Report report) {
        return report == null || report.getWasteType() == null
                ? "Unknown type" : report.getWasteType();
    }

    /**
     * Great-circle distance in meters.
     *
     * <p>Used to describe how close two submissions are. The comparison an
     * administrator actually makes is "how far apart are these", never a raw
     * coordinate, which is why this feeds a sentence rather than a coordinate
     * pair.
     */
    static double distanceMeters(Report a, Report b) {
        double r = 6371000;
        double dLat = Math.toRadians(b.getLatitude() - a.getLatitude());
        double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.getLatitude()))
                * Math.cos(Math.toRadians(b.getLatitude()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    /**
     * The widest gap inside the group, or null when there is nothing to compare.
     * Used for the card's "all within N m of each other" line.
     */
    static Double maxSpreadMeters(java.util.List<Report> reports) {
        Double max = null;
        if (reports == null) return max;
        for (int i = 0; i < reports.size(); i++) {
            for (int j = i + 1; j < reports.size(); j++) {
                double d = distanceMeters(reports.get(i), reports.get(j));
                max = max == null ? d : Math.max(max, d);
            }
        }
        return max;
    }

    /** Distance line for one report, relative to the group's first report. */
    static String locationLine(Context context, Report report, Report first) {
        if (first == null || report == first) {
            return context.getString(R.string.possible_duplicate_first_report);
        }
        return context.getString(R.string.possible_duplicate_distance,
                Math.round(distanceMeters(report, first)));
    }

    /**
     * A report's submitted time as readable text.
     *
     * <p>Falls back to the raw value rather than showing nothing, because an
     * unfamiliar timestamp is still information while a blank field reads as
     * missing data.
     */
    static String submittedAt(String rawTimestamp) {
        String date = ReportSubmittedAtFormatter.formatDate(rawTimestamp);
        String time = ReportSubmittedAtFormatter.formatTime(rawTimestamp);
        if (date == null || time == null) {
            return rawTimestamp == null || rawTimestamp.trim().isEmpty()
                    ? "unknown time" : rawTimestamp.trim();
        }
        return date + " • " + time;
    }

    /** The translated waste scale for a report. */
    static String scaleLabelOf(Context context, Report report) {
        return WasteScaleDisplay.labelFor(context,
                report == null ? null : report.getWasteScale());
    }

    /**
     * "Scale • Category • Submitted <time>", shared by every review surface.
     *
     * <p>The scale is part of the line because it is part of why these reports
     * were grouped at all. Submission time stays visible for context, but it no
     * longer decides whether two reports are compared.
     */
    static String submittedLine(Context context, Report report) {
        return scaleLabelOf(context, report) + " • " + wasteTypeOf(report)
                + " • Submitted "
                + submittedAt(report == null ? null : report.getCreatedAt());
    }

    /**
     * Best available image for a report: the local copy when the report was
     * created on this device, otherwise the server URL. Null when neither
     * exists, so callers can hide the thumbnail instead of showing a gap.
     */
    static Object photoSourceOf(Report report, Context context) {
        if (report == null) return null;
        if (report.getLocalPhotoPath() != null) {
            File local = new File(report.getLocalPhotoPath());
            if (local.exists()) return local;
        }
        if (report.getPhotoUrl() != null && !report.getPhotoUrl().trim().isEmpty()) {
            return ServerConfig.absoluteUrl(context, report.getPhotoUrl());
        }
        return null;
    }
}