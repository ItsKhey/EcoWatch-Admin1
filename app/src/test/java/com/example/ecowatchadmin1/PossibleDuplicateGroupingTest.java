package com.example.ecowatchadmin1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.repository.PossibleDuplicateGrouper;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Guards the identity of a Possible Duplicate Reports group.
 *
 * <p>A clone group is the same location plus the same waste scale. Submission
 * time is not part of it: two residents can report the same pile days apart and
 * still be duplicates. A small pile and a large one metres apart are not
 * duplicates, even though the location matches.
 */
public class PossibleDuplicateGroupingTest {

    private static Report report(String reportId, String wasteScale, long submittedAt,
                                 double latitude, double longitude) {
        Report report = new Report("title", "description", "Waste", latitude, longitude);
        report.setReportId(reportId);
        report.setWasteScale(wasteScale);
        report.setCreatedAt(String.valueOf(submittedAt));
        return report;
    }

    private static Report reportWithStatus(String reportId, String wasteScale, String status) {
        Report report = report(reportId, wasteScale, 1000000000000L, 14.61, 120.98);
        report.setStatus(status);
        return report;
    }

    private static PossibleDuplicateGroup serverGroup(String groupId, Report... reports) {
        PossibleDuplicateGroup group = new PossibleDuplicateGroup();
        group.setGroupId(groupId);
        group.setReports(new ArrayList<>(Arrays.asList(reports)));
        group.setCenterLatitude(14.61);
        group.setCenterLongitude(120.98);
        return group;
    }

    /** Same spot and scale, submitted days apart: still one clone group. */
    @Test
    public void matchingLocationAndScaleGroupRegardlessOfSubmittedTime() {
        PossibleDuplicateGroup server = serverGroup("CL-123456",
                report("R-1", Report.SCALE_SMALL, 1000000000000L, 14.61, 120.98),
                report("R-2", Report.SCALE_SMALL, 1000600000000L, 14.61, 120.98),
                report("R-3", Report.SCALE_SMALL, 1001200000000L, 14.61, 120.98));

        List<PossibleDuplicateGroup> groups = PossibleDuplicateGrouper.regroup(
                new ArrayList<>(Arrays.asList(server)));

        assertEquals(1, groups.size());
        PossibleDuplicateGroup group = groups.get(0);
        assertEquals("CL-123456::small", group.getGroupId());
        assertEquals(Report.SCALE_SMALL, group.getWasteScale());
        assertEquals(3, group.getReports().size());
        // The oldest report is the original, even though the newest arrived last.
        assertEquals("R-1", group.getReports().get(0).getReportId());
    }

    /** Same spot, different scales: two separate review groups. */
    @Test
    public void matchingLocationWithDifferentScalesIsSplit() {
        PossibleDuplicateGroup server = serverGroup("CL-123456",
                report("S-1", Report.SCALE_SMALL, 1000000000000L, 14.61, 120.98),
                report("M-1", Report.SCALE_MEDIUM, 1000000001000L, 14.61, 120.98),
                report("S-2", Report.SCALE_SMALL, 1000000002000L, 14.61, 120.98),
                report("M-2", Report.SCALE_MEDIUM, 1000000003000L, 14.61, 120.98));

        List<PossibleDuplicateGroup> groups = PossibleDuplicateGrouper.regroup(
                new ArrayList<>(Arrays.asList(server)));

        assertEquals(2, groups.size());
        assertEquals("CL-123456::small", groups.get(0).getGroupId());
        assertEquals(2, groups.get(0).getReports().size());
        assertTrue(containsReport(groups.get(0), "S-1"));
        assertTrue(containsReport(groups.get(0), "S-2"));

        assertEquals("CL-123456::medium", groups.get(1).getGroupId());
        assertEquals(2, groups.get(1).getReports().size());
        assertTrue(containsReport(groups.get(1), "M-1"));
        assertTrue(containsReport(groups.get(1), "M-2"));
    }

    /** A scale with only one report is not a duplicate pair. */
    @Test
    public void aScaleWithOneReportIsNotReviewable() {
        PossibleDuplicateGroup server = serverGroup("CL-123456",
                report("S-1", Report.SCALE_SMALL, 1000000000000L, 14.61, 120.98),
                report("S-2", Report.SCALE_SMALL, 1000000001000L, 14.61, 120.98),
                report("L-1", Report.SCALE_LARGE, 1000000002000L, 14.61, 120.98));

        List<PossibleDuplicateGroup> groups = PossibleDuplicateGrouper.regroup(
                new ArrayList<>(Arrays.asList(server)));

        assertEquals(1, groups.size());
        assertEquals("CL-123456::small", groups.get(0).getGroupId());
    }

    /** A repeated report must not count as two duplicates of itself. */
    @Test
    public void duplicateReportIdsAreCountedOnce() {
        Report repeated = report("R-1", Report.SCALE_SMALL,
                1000000000000L, 14.61, 120.98);
        PossibleDuplicateGroup server = serverGroup("CL-123456",
                repeated,
                repeated,
                report("R-2", Report.SCALE_SMALL, 1000000001000L, 14.61, 120.98));

        List<PossibleDuplicateGroup> groups = PossibleDuplicateGrouper.regroup(
                new ArrayList<>(Arrays.asList(server)));

        assertEquals(1, groups.size());
        assertEquals(2, groups.get(0).getReports().size());
    }

    /** A server id already scoped to a scale keeps that scope. */
    @Test
    public void compositeServerGroupIdKeepsItsScale() {
        PossibleDuplicateGroup server = serverGroup("CL-123456::large",
                report("L-1", Report.SCALE_LARGE, 1000000000000L, 14.61, 120.98),
                report("L-2", Report.SCALE_LARGE, 1000000001000L, 14.61, 120.98));

        List<PossibleDuplicateGroup> groups = PossibleDuplicateGrouper.regroup(
                new ArrayList<>(Arrays.asList(server)));

        assertEquals(1, groups.size());
        assertEquals("CL-123456::large", groups.get(0).getGroupId());
        assertEquals(Report.SCALE_LARGE, groups.get(0).getWasteScale());
    }

    /** Unknown spellings normalize to the canonical scale vocabulary. */
    @Test
    public void scaleAliasesNormalizeToTheCanonicalVocabulary() {
        assertEquals(PossibleDuplicateGrouper.SCALE_KEY_SMALL,
                PossibleDuplicateGrouper.scaleKeyOf("small"));
        assertEquals(PossibleDuplicateGrouper.SCALE_KEY_MEDIUM,
                PossibleDuplicateGrouper.scaleKeyOf("Medium-Scale Waste"));
        assertEquals(PossibleDuplicateGrouper.SCALE_KEY_LARGE,
                PossibleDuplicateGrouper.scaleKeyOf("large_scale_waste"));
        assertEquals(PossibleDuplicateGrouper.SCALE_KEY_SMALL,
                PossibleDuplicateGrouper.scaleKeyOf((String) null));
    }

    /** Bad coordinates must not break the card; distance is display-only. */
    @Test
    public void invalidCoordinatesDoNotPreventGrouping() {
        PossibleDuplicateGroup server = serverGroup("CL-123456",
                report("R-1", Report.SCALE_SMALL, 1000000000000L,
                        Double.NaN, Double.POSITIVE_INFINITY),
                report("R-2", Report.SCALE_SMALL, 1000000001000L,
                        Double.NaN, Double.POSITIVE_INFINITY));
        server.setCenterLatitude(null);
        server.setCenterLongitude(null);

        List<PossibleDuplicateGroup> groups = PossibleDuplicateGrouper.regroup(
                new ArrayList<>(Arrays.asList(server)));

        assertEquals(1, groups.size());
        assertEquals(2, groups.get(0).getReports().size());
        assertNull(groups.get(0).getCenterLatitude());
        assertNull(groups.get(0).getCenterLongitude());
    }

    /** Completed and Denied are excluded; every open status is included. */
    @Test
    public void completedAndDeniedAreExcludedButOpenStatusesAreNot() {
        PossibleDuplicateGroup server = serverGroup("CL-123456",
                reportWithStatus("D-1", Report.SCALE_SMALL, "Denied"),
                reportWithStatus("C-1", Report.SCALE_SMALL, "Completed"),
                reportWithStatus("P-1", Report.SCALE_SMALL, "Pending"),
                reportWithStatus("I-1", Report.SCALE_SMALL, "In Progress"),
                reportWithStatus("U-1", Report.SCALE_SMALL, "Under Review"));

        List<PossibleDuplicateGroup> groups = PossibleDuplicateGrouper.regroup(
                new ArrayList<>(Arrays.asList(server)));

        assertEquals(1, groups.size());
        PossibleDuplicateGroup group = groups.get(0);
        assertEquals(3, group.getReports().size());
        assertFalse(containsReport(group, "D-1"));
        assertFalse(containsReport(group, "C-1"));
        assertTrue(containsReport(group, "P-1"));
        assertTrue(containsReport(group, "I-1"));
        assertTrue(containsReport(group, "U-1"));
    }

    /** A Completed or Denied row cannot stand in for a live duplicate. */
    @Test
    public void terminalReportCannotCompleteThePair() {
        PossibleDuplicateGroup server = serverGroup("CL-123456",
                reportWithStatus("C-1", Report.SCALE_SMALL, "Completed"),
                reportWithStatus("D-1", Report.SCALE_SMALL, "Denied"),
                reportWithStatus("P-1", Report.SCALE_SMALL, "Pending"));

        List<PossibleDuplicateGroup> groups = PossibleDuplicateGrouper.regroup(
                new ArrayList<>(Arrays.asList(server)));

        assertTrue(groups.isEmpty());
    }

    /** Only open statuses can receive a deny decision. */
    @Test
    public void onlyOpenReportsAreDeniable() {
        assertFalse(PossibleDuplicateGrouper.canBeDenied(
                reportWithStatus("C-1", Report.SCALE_SMALL, "Completed")));
        assertFalse(PossibleDuplicateGrouper.canBeDenied(
                reportWithStatus("D-1", Report.SCALE_SMALL, "Denied")));
        assertTrue(PossibleDuplicateGrouper.canBeDenied(
                reportWithStatus("U-1", Report.SCALE_SMALL, "Under Review")));
        assertTrue(PossibleDuplicateGrouper.canBeDenied(
                reportWithStatus("I-1", Report.SCALE_SMALL, "In Progress")));
        assertTrue(PossibleDuplicateGrouper.canBeDenied(
                reportWithStatus("P-1", Report.SCALE_SMALL, "Pending")));
    }

    private static boolean containsReport(PossibleDuplicateGroup group, String reportId) {
        for (Report report : group.getReports()) {
            if (reportId.equals(report.getReportId())) return true;
        }
        return false;
    }
}
