package com.example.ecowatchadmin1.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchadmin1.data.models.Report;

import org.junit.Test;

/**
 * The rule that decides when a report shows "Revised" in the reporter history.
 *
 * <p>The server has no "Revised" status: requesting a revision sets the report
 * back to <b>In Progress</b> and records the decision in its audit trail, which
 * the report payload exposes as {@code revisionRequested}. A personnel note is
 * not proof of a revision because the same field carries ordinary assignment
 * instructions, so these cases pin that the explicit server flag is required and
 * that terminal and other statuses keep their real label.
 */
public class ReportRevisionTest {

    private static Report report(String status, boolean revisionRequested,
                                 String personnelNote) {
        Report report = new Report("Illegal dumping", "Bags by the corner",
                "Plastic", 14.5995, 120.9842);
        report.setStatus(status);
        report.setRevisionRequested(revisionRequested);
        report.setPersonnelNote(personnelNote);
        return report;
    }

    /** A missing report is never a revision. */
    @Test
    public void nullReportIsNotARevision() {
        assertFalse(ReportRevision.isRevision(null));
    }

    /** The server flag marks the current assignment as sent back for revision. */
    @Test
    public void flaggedInProgressReportIsARevision() {
        assertTrue(ReportRevision.isRevision(
                report("In Progress", true, "Please recapture the after photo.")));
    }

    /** The flag, not the note, is what counts. */
    @Test
    public void flaggedInProgressReportIsARevisionEvenWithoutNote() {
        assertTrue(ReportRevision.isRevision(report("In Progress", true, null)));
    }

    /** Assignment instructions on an assigned report are not a revision. */
    @Test
    public void inProgressWithOnlyANoteIsNotARevision() {
        assertFalse(ReportRevision.isRevision(
                report("In Progress", false, "Meet the resident at the west gate.")));
    }

    /** The server and older payloads spell the status in several ways. */
    @Test
    public void statusSpellingVariantsAreRecognised() {
        assertTrue(ReportRevision.isRevision(report("in_progress", true, null)));
        assertTrue(ReportRevision.isRevision(report("in-progress", true, null)));
        assertTrue(ReportRevision.isRevision(report("  IN PROGRESS  ", true, null)));
    }

    /** Completed reports must keep "Completed" even if they were revised before. */
    @Test
    public void completedWithFlagIsNotARevision() {
        assertFalse(ReportRevision.isRevision(
                report("Completed", true, "Good job, but check the back alley.")));
    }

    /** Denied reports must keep "Denied" even if they were revised before. */
    @Test
    public void deniedWithFlagIsNotARevision() {
        assertFalse(ReportRevision.isRevision(report("Denied", true, "Not enough evidence.")));
    }

    /** A stale flag does not override a status that is not an active revision. */
    @Test
    public void otherStatusesWithFlagAreNotRevisions() {
        assertFalse(ReportRevision.isRevision(report("Pending", true, null)));
        assertFalse(ReportRevision.isRevision(report("Under Review", true, null)));
        assertFalse(ReportRevision.isRevision(report("Assigned", true, null)));
        assertFalse(ReportRevision.isRevision(report("On Hand", true, null)));
    }

    /** A report with no status can never be a revision. */
    @Test
    public void missingStatusIsNotARevision() {
        assertFalse(ReportRevision.isRevision(report(null, true, "Please redo this.")));
    }

    /** The status normaliser trims, lower-cases and flattens separators. */
    @Test
    public void normalizeStatusFlattensServerSpelling() {
        assertEquals("in progress", ReportRevision.normalizeStatus("  In_Progress "));
        assertEquals("in progress", ReportRevision.normalizeStatus("in-progress"));
        assertEquals("under review", ReportRevision.normalizeStatus("UNDER   REVIEW"));
        assertNull(ReportRevision.normalizeStatus(null));
    }
}
