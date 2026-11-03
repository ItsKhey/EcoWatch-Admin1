package com.example.ecowatchadmin1.utils;

import androidx.annotation.Nullable;

import com.example.ecowatchadmin1.data.models.Report;

import java.util.Locale;

/**
 * Decides whether a report is currently back with the cleaner for another
 * attempt.
 *
 * <p>The server has no "Revised" status. Requesting a revision moves the report
 * back to <b>In Progress</b> and the administrator writes an audit trail entry
 * for it; the cleaner also sees the reason in their personnel-only note. The
 * authoritative flag is the server's {@link Report#isRevisionRequested()} field,
 * which is derived from that audit trail.
 *
 * <p>The personnel note is deliberately ignored. The same field holds ordinary
 * assignment instructions, so treating a note as proof of a revision relabels
 * assigned reports as "Revised". A report only counts here while the server says
 * an administrator requested a revision for the current assignment and the
 * report is still back in progress.
 */
public final class ReportRevision {
    private ReportRevision() {
    }

    public static boolean isRevision(@Nullable Report report) {
        if (report == null || !report.isRevisionRequested()) return false;
        return "in progress".equals(normalizeStatus(report.getStatus()));
    }

    /** Lower-cases a server status and flattens its separators for comparison. */
    @Nullable
    public static String normalizeStatus(@Nullable String status) {
        if (status == null) return null;
        return status.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ");
    }
}
