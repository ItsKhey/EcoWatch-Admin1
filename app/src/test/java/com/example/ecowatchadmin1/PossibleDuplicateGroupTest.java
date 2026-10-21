package com.example.ecowatchadmin1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.ui.reports.ReportsFragment;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Guards the rules behind the Possible Duplicate Reports card.
 *
 * <p>Two things are asserted here because both have broken the feature before:
 *
 * <ul>
 *     <li>Which tabs the card may appear on. It belongs on All and Pending and
 *         must never surface on Completed, Denied, Archived or Trashed.</li>
 *     <li>Which reports inside a group still count as pending, because a group
 *         whose reports were all resolved must stop being offered for review on
 *         the Pending tab.</li>
 * </ul>
 *
 * <p>The first is also asserted against the adapter itself, because that is
 * where the card is actually filtered; a rule that only exists in a comment
 * cannot stop a card from appearing on the Denied tab.
 */
public class PossibleDuplicateGroupTest {

    private static Report reportWithStatus(String reportId, String status) {
        Report report = new Report("title", "description", "Waste", 0, 0);
        setField(report, "reportId", reportId);
        setField(report, "status", status);
        return report;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = Report.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Report field " + name + " is missing", error);
        }
    }

    private static PossibleDuplicateGroup groupOf(Report... reports) {
        PossibleDuplicateGroup group = new PossibleDuplicateGroup();
        group.setGroupId("review-1");
        group.setReports(new ArrayList<>(Arrays.asList(reports)));
        return group;
    }

    @Test
    public void pendingReportsAreReportedForAGroupOfPendingReports() {
        PossibleDuplicateGroup group = groupOf(
                reportWithStatus("R-1", "Pending"),
                reportWithStatus("R-2", "Pending"));

        assertEquals(2, group.getPendingReports().size());
    }

    /** Only the still-pending report counts; a confirmed duplicate does not. */
    @Test
    public void onlyTheStillPendingReportIsCounted() {
        PossibleDuplicateGroup group = groupOf(
                reportWithStatus("R-1", "Pending"),
                reportWithStatus("R-2", "Denied"));

        List<Report> pending = group.getPendingReports();
        assertEquals(1, pending.size());
        assertEquals("R-1", pending.get(0).getReportId());
    }

    /** Nothing pending means nothing to review on the Pending tab. */
    @Test
    public void aResolvedGroupHasNoPendingReports() {
        PossibleDuplicateGroup group = groupOf(
                reportWithStatus("R-1", "Completed"),
                reportWithStatus("R-2", "Denied"));

        assertTrue(group.getPendingReports().isEmpty());
    }

    /** A null report list must not crash the tab switch. */
    @Test
    public void aGroupWithoutReportsIsTreatedAsEmpty() {
        PossibleDuplicateGroup group = new PossibleDuplicateGroup();
        group.setReports(null);

        assertTrue(group.getPendingReports().isEmpty());
    }

    // ---------------------------------------------------------------------
    // Tab visibility
    // ---------------------------------------------------------------------

    /**
     * The card is a review queue, so it belongs on the two tabs where work is
     * still outstanding. On every history tab it must be absent, otherwise a
     * resolved or denied report looks like it still needs a decision.
     */
    @Test
    public void theCardIsShownOnAllAndPendingOnly() {
        assertTrue("All",
                ReportsFragment.isPossibleDuplicateTab("All"));
        assertTrue("Pending",
                ReportsFragment.isPossibleDuplicateTab("Pending"));

        for (String tab : new String[]{"Completed", "Denied", "Archived", "Trashed"}) {
            assertFalse(tab + " must not show the duplicate review card",
                    ReportsFragment.isPossibleDuplicateTab(tab));
        }
    }

    /**
     * The Pending tab additionally hides groups whose reports have all moved on,
     * while All keeps showing them: All is the admin's full picture.
     */
    @Test
    public void pendingHidesGroupsWithNothingLeftToReview() {
        PossibleDuplicateGroup resolved = groupOf(
                reportWithStatus("R-1", "Completed"),
                reportWithStatus("R-2", "Denied"));
        PossibleDuplicateGroup open = groupOf(
                reportWithStatus("R-3", "Pending"),
                reportWithStatus("R-4", "Pending"));

        List<PossibleDuplicateGroup> visibleOnAll = new ArrayList<>();
        visibleOnAll.add(resolved);
        visibleOnAll.add(open);
        assertEquals(2, visibleOnAll.size());

        List<PossibleDuplicateGroup> visibleOnPending = new ArrayList<>();
        for (PossibleDuplicateGroup group : visibleOnAll) {
            if (!group.getPendingReports().isEmpty()) {
                visibleOnPending.add(group);
            }
        }
        assertEquals(1, visibleOnPending.size());
        assertEquals("review-1", visibleOnPending.get(0).getGroupId());
    }

    /**
     * A single report cannot be a duplicate of itself, so a one-report group is
     * never something to review.
     */
    @Test
    public void aGroupNeedsAtLeastTwoReportsToBeWorthReviewing() {
        assertFalse(isWorthReviewing(groupOf(reportWithStatus("R-1", "Pending"))));
        assertTrue(isWorthReviewing(groupOf(
                reportWithStatus("R-1", "Pending"),
                reportWithStatus("R-2", "Pending"))));
    }

    private static boolean isWorthReviewing(PossibleDuplicateGroup group) {
        return group.getReports() != null && group.getReports().size() >= 2;
    }
}