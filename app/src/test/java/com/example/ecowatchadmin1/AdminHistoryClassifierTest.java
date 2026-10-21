package com.example.ecowatchadmin1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchadmin1.utils.AdminHistoryCategory;
import com.example.ecowatchadmin1.utils.AdminHistoryClassifier;
import com.example.ecowatchadmin1.utils.AdminHistoryDateFormatter;

import org.junit.Test;

/**
 * Pins the Admin History tab rules.
 *
 * <p>There is no category column in the history table, so which tab a record
 * appears under is decided entirely by these rules. Getting one wrong does not
 * crash: it silently files an admin's decision under the wrong heading, which is
 * the failure this screen exists to prevent.
 *
 * <p>The action names below are the ones the server actually writes today. Where a
 * name is ambiguous ({@code COMPLETED}, {@code STATUS_CHANGED}) the test states
 * which reading was chosen and why, because reversing those choices is the most
 * likely way to break this later.
 */
public class AdminHistoryClassifierTest {

    // ------------------------------------------------------------- Reports

    @Test
    public void reportDecisionsLandUnderReports() {
        assertCategory(AdminHistoryCategory.REPORTS, "SUBMITTED");
        assertCategory(AdminHistoryCategory.REPORTS, "STATUS_CHANGED");
        assertCategory(AdminHistoryCategory.REPORTS, "ARCHIVED");
        assertCategory(AdminHistoryCategory.REPORTS, "RESTORED");
        assertCategory(AdminHistoryCategory.REPORTS, "UPDATED");
        assertCategory(AdminHistoryCategory.REPORTS, "PERMANENTLY_DELETED");
        assertCategory(AdminHistoryCategory.REPORTS, "MOVED_TO_TRASH");
    }

    /**
     * The duplicate review is the decision the admin most needs to be able to find
     * later, in both directions.
     */
    @Test
    public void duplicateDecisionsLandUnderReports() {
        assertCategory(AdminHistoryCategory.REPORTS, "NOT_DUPLICATE");
        assertCategory(AdminHistoryCategory.REPORTS, "DUPLICATE");
        assertCategory(AdminHistoryCategory.REPORTS, "MARKED_DUPLICATE");
    }

    // --------------------------------------------------------- Assignments

    @Test
    public void assignmentDecisionsLandUnderAssignments() {
        assertCategory(AdminHistoryCategory.ASSIGNMENTS, "ASSIGNED");
        assertCategory(AdminHistoryCategory.ASSIGNMENTS, "ASSIGNMENT_ASSIGNED");
        assertCategory(AdminHistoryCategory.ASSIGNMENTS, "REASSIGNED");
        assertCategory(AdminHistoryCategory.ASSIGNMENTS, "UNASSIGNED");
        assertCategory(AdminHistoryCategory.ASSIGNMENTS, "ASSIGNMENT_CANCELLED");
        assertCategory(AdminHistoryCategory.ASSIGNMENTS, "ASSIGNMENT_REJECTED");
    }

    /**
     * {@code COMPLETED} is written both for a finished cleaning job and for a
     * report an admin marked complete. ASSIGNMENT_COMPLETED is pinned to
     * Assignments because the assignment is what the admin changed; the cleaning
     * tab covers the distinct CLEANING_* actions instead.
     */
    @Test
    public void assignmentScopedCompletedStaysUnderAssignments() {
        assertCategory(AdminHistoryCategory.ASSIGNMENTS, "ASSIGNMENT_COMPLETED");
    }

    // ------------------------------------------------------------ Cleaning

    @Test
    public void cleaningProgressLandsUnderCleaning() {
        assertCategory(AdminHistoryCategory.CLEANING, "CLEANING_STARTED");
        assertCategory(AdminHistoryCategory.CLEANING, "CLEANING_COMPLETED");
        assertCategory(AdminHistoryCategory.CLEANING, "BEFORE_EVIDENCE_SUBMITTED");
        assertCategory(AdminHistoryCategory.CLEANING, "AFTER_EVIDENCE_SUBMITTED");
        assertCategory(AdminHistoryCategory.CLEANING, "CLEANING_INCOMPLETE");
    }

    /**
     * A bare COMPLETED or STARTED is the post-assignment event, so it reads as
     * cleaning rather than as a report status change.
     */
    @Test
    public void bareCompletionAndStartLandUnderCleaning() {
        assertCategory(AdminHistoryCategory.CLEANING, "COMPLETED");
        assertCategory(AdminHistoryCategory.CLEANING, "STARTED");
        assertCategory(AdminHistoryCategory.CLEANING, "INCOMPLETE");
    }

    /**
     * Sent back for rework after the admin reviewed the finished work. Cleaning,
     * because the cleaning is what has to be redone.
     */
    @Test
    public void revisionRequestLandsUnderCleaning() {
        assertCategory(AdminHistoryCategory.CLEANING, "REVISION_REQUESTED");
    }

    // ------------------------------------------------------ Announcements

    @Test
    public void announcementEventsLandUnderAnnouncements() {
        assertCategory(AdminHistoryCategory.ANNOUNCEMENTS, "ANNOUNCEMENT_CREATED");
        assertCategory(AdminHistoryCategory.ANNOUNCEMENTS, "ANNOUNCEMENT_PUBLISHED");
        assertCategory(AdminHistoryCategory.ANNOUNCEMENTS, "ANNOUNCEMENT_EDITED");
        assertCategory(AdminHistoryCategory.ANNOUNCEMENTS, "ANNOUNCEMENT_DELETED");
        assertCategory(AdminHistoryCategory.ANNOUNCEMENTS, "PUBLISHED");
        assertCategory(AdminHistoryCategory.ANNOUNCEMENTS, "UNPUBLISHED");
    }

    // ------------------------------------------------------------- Account

    @Test
    public void espAccountEventsLandUnderAccount() {
        assertCategory(AdminHistoryCategory.ACCOUNT, "ACCOUNT_CREATED");
        assertCategory(AdminHistoryCategory.ACCOUNT, "ACCOUNT_ACTIVATED");
        assertCategory(AdminHistoryCategory.ACCOUNT, "ACCOUNT_DEACTIVATED");
        assertCategory(AdminHistoryCategory.ACCOUNT, "ACCOUNT_REACTIVATED");
        assertCategory(AdminHistoryCategory.ACCOUNT, "ACCOUNT_UPDATED");
        assertCategory(AdminHistoryCategory.ACCOUNT, "ACCOUNT_DELETED");
        assertCategory(AdminHistoryCategory.ACCOUNT, "ESP_ACCOUNT_CREATED");
        assertCategory(AdminHistoryCategory.ACCOUNT, "PERSONNEL_CREATED");
        assertCategory(AdminHistoryCategory.ACCOUNT, "PASSWORD_RESET");
    }

    // -------------------------------------------------- ordering precedence

    /**
     * Several actions carry words that belong to a different tab. The precedence
     * is fixed in the classifier, and these cases pin it so it cannot drift:
     * cleaning beats assignment, and report beats account.
     */
    @Test
    public void moreSpecificCategoriesWinOverGenericOnes() {
        // Contains "ASSIGN", but it is a cleaning record.
        assertCategory(AdminHistoryCategory.CLEANING, "CLEANING_ASSIGNMENT_VERIFIED");
        // Contains "ACCOUNT", but the change is to a report.
        assertCategory(AdminHistoryCategory.REPORTS, "REPORT_ACCOUNT_OWNER_CHANGED");
        // Contains "PERSONNEL", but the record is about the cleaning job.
        assertCategory(AdminHistoryCategory.CLEANING, "CLEANING_PERSONNEL_SUBMITTED");
    }

    // ------------------------------------------------------------ fallback

    /**
     * A server action this build has never seen must still appear somewhere. If it
     * were dropped instead, new server-side events would silently vanish from the
     * audit trail.
     */
    @Test
    public void unknownActionFallsBackToReportsRatherThanDisappearing() {
        assertCategory(AdminHistoryCategory.REPORTS, "SOME_BRAND_NEW_ACTION");
        assertTrue(AdminHistoryClassifier.matches("SOME_BRAND_NEW_ACTION",
                AdminHistoryCategory.REPORTS));
    }

    @Test
    public void missingActionStillMatches() {
        assertCategory(AdminHistoryCategory.REPORTS, null);
        assertCategory(AdminHistoryCategory.REPORTS, "");
        assertCategory(AdminHistoryCategory.REPORTS, "   ");
    }

    @Test
    public void actionCaseAndSpacingAreNormalised() {
        assertCategory(AdminHistoryCategory.REPORTS, "archived");
        assertCategory(AdminHistoryCategory.REPORTS, "  Archived  ");
        assertCategory(AdminHistoryCategory.CLEANING, "completed");
        assertCategory(AdminHistoryCategory.ASSIGNMENTS, "  assigned ");
    }

    // ------------------------------------------------------------ filtering

    @Test
    public void allTabMatchesEveryRow() {
        for (AdminHistoryCategory category : AdminHistoryCategory.values()) {
            assertTrue("All must match " + category,
                    AdminHistoryClassifier.matches("COMPLETED", AdminHistoryCategory.ALL));
        }
    }

    @Test
    public void categoryTabMatchesOnlyItsOwnRows() {
        assertTrue(AdminHistoryClassifier.matches("ASSIGNED",
                AdminHistoryCategory.ASSIGNMENTS));
        assertFalse(AdminHistoryClassifier.matches("ASSIGNED",
                AdminHistoryCategory.ANNOUNCEMENTS));
        assertFalse(AdminHistoryClassifier.matches("ASSIGNED",
                AdminHistoryCategory.CLEANING));
    }

    @Test
    public void aRowBelongsToExactlyOneCategory() {
        // Guards against a future edit adding an action to two branches, which
        // would make it vanish from a combined All + per-tab count.
        String[] actions = {"SUBMITTED", "ASSIGNED", "COMPLETED", "ANNOUNCEMENT_PUBLISHED",
                "ACCOUNT_CREATED", "CLEANING_STARTED"};
        for (String action : actions) {
            int matches = 0;
            for (AdminHistoryCategory category : AdminHistoryCategory.values()) {
                if (category == AdminHistoryCategory.ALL) continue;
                if (AdminHistoryClassifier.matches(action, category)) matches++;
            }
            assertEquals(action + " must match exactly one category tab", 1, matches);
        }
    }

    // -------------------------------------------------------------- search

    @Test
    public void searchMatchesActionDescriptionReportAndActor() {
        // One assertion per searched field, so a failure names the field that broke.
        assertTrue("action",
                AdminHistoryClassifier.matchesQuery("denied", "DENIED", null, null, null));
        assertTrue("description",
                AdminHistoryClassifier.matchesQuery("submitted", null,
                        "Admin confirmed the report was already submitted", null, null));
        assertTrue("report id",
                AdminHistoryClassifier.matchesQuery("ew-1024", null, null, "EW-1024", null));
        assertTrue("actor",
                AdminHistoryClassifier.matchesQuery("adm-001", null, null, null, "ADM-001"));
    }

    @Test
    public void searchIsCaseInsensitiveAndTrimmed() {
        assertTrue(AdminHistoryClassifier.matchesQuery("  DENIED  ", "DENIED", null, null, null));
    }

    @Test
    public void emptyQueryMatchesEverything() {
        assertTrue(AdminHistoryClassifier.matchesQuery("", "ANY", null, null, null));
        assertTrue(AdminHistoryClassifier.matchesQuery(null, "ANY", null, null, null));
        assertTrue(AdminHistoryClassifier.matchesQuery("   ", "ANY", null, null, null));
    }

    @Test
    public void searchMissesWhenNothingMatches() {
        assertFalse(AdminHistoryClassifier.matchesQuery("announcement", "DENIED",
                "Report denied", "EW-1", "ADM-1"));
    }

    @Test
    public void searchToleratesNullFields() {
        assertFalse(AdminHistoryClassifier.matchesQuery("x", null, null, null, null));
    }

    // ------------------------------------------------------------- dates

    @Test
    public void dateRangeIncludesBothBounds() {
        long day = 1_700_000_000_000L;

        assertTrue(AdminHistoryClassifier.withinRange(day, day, day));
        assertTrue(AdminHistoryClassifier.withinRange(day, day - 1000, day + 1000));
        assertFalse(AdminHistoryClassifier.withinRange(day, day + 1, null));
        assertFalse(AdminHistoryClassifier.withinRange(day, null, day - 1));
    }

    @Test
    public void openEndedRangeAcceptsAnyTimestamp() {
        assertTrue(AdminHistoryClassifier.withinRange(1_700_000_000_000L, null, null));
    }

    /**
     * An undated row must not be claimed to sit inside a window it cannot be
     * placed in, or "Today" would show records that are not from today.
     */
    @Test
    public void undatedRowIsExcludedFromABoundedRangeButKeptInAnOpenOne() {
        assertFalse(AdminHistoryClassifier.withinRange(null, 0L, 1L));
        assertTrue(AdminHistoryClassifier.withinRange(null, null, null));
        assertFalse(AdminHistoryClassifier.withinRange(null, 0L, null));
        assertFalse(AdminHistoryClassifier.withinRange(null, null, 1L));
    }

    // ------------------------------------------------------ title and icon

    @Test
    public void titleTurnsActionConstantsIntoReadableText() {
        assertEquals("Not Duplicate", AdminHistoryClassifier.title("NOT_DUPLICATE"));
        assertEquals("Status Changed", AdminHistoryClassifier.title("STATUS_CHANGED"));
        assertEquals("Permanently Deleted",
                AdminHistoryClassifier.title("PERMANENTLY_DELETED"));
        assertEquals("Announcement Published",
                AdminHistoryClassifier.title("ANNOUNCEMENT_PUBLISHED"));
    }

    @Test
    public void titleNeverReturnsBlankForAMissingAction() {
        assertEquals("Activity", AdminHistoryClassifier.title(null));
        assertEquals("Activity", AdminHistoryClassifier.title(""));
    }

    @Test
    public void everyActionProducesAnIcon() {
        String[] actions = {"SUBMITTED", "ASSIGNED", "COMPLETED", "ACCOUNT_CREATED",
                "ANNOUNCEMENT_PUBLISHED", "PERMANENTLY_DELETED", "MADE_UP_ACTION"};
        for (String action : actions) {
            assertFalse("icon missing for " + action,
                    AdminHistoryClassifier.icon(action).isEmpty());
        }
    }

    // -------------------------------------------------------- date display

    /**
     * The card format from the design, and the reason {@code h:mm} is used instead
     * of {@code hh:mm}: a zero-padded hour disagrees with every other timestamp in
     * the app.
     */
    @Test
    public void timestampsRenderAsDesigned() {
        // 2023-11-14T22:13:20Z, rendered in Manila time (UTC+8) as the next
        // morning. Pinned so the zone and the 12-hour format cannot drift.
        assertEquals("Nov 15, 2023 • 6:13 AM",
                AdminHistoryDateFormatter.format(1_700_000_000_000L));
    }

    /**
     * The hour must not be zero-padded: {@code hh} renders 06:13 while the rest of
     * the app shows "6:13 AM", and every card would disagree with every other
     * timestamp on screen.
     */
    @Test
    public void singleDigitHourIsNotPadded() {
        String formatted = AdminHistoryDateFormatter.format(1_700_000_000_000L);
        assertTrue("expected an unpadded hour in: " + formatted,
                formatted.contains(" • 6:13 AM"));
    }

    @Test
    public void missingTimestampReadsAsUnknownRatherThanBlank() {
        assertEquals("Unknown time", AdminHistoryDateFormatter.format(null));
        assertEquals("Unknown time", AdminHistoryDateFormatter.format(0L));
        assertEquals("Unknown time", AdminHistoryDateFormatter.format(-1L));
    }

    // ------------------------------------------------------------ category

    @Test
    public void categoryLabelsRoundTrip() {
        for (AdminHistoryCategory category : AdminHistoryCategory.values()) {
            assertEquals(category,
                    AdminHistoryCategory.fromLabel(category.getLabel()));
        }
    }

    @Test
    public void unknownCategoryLabelFallsBackToAll() {
        assertEquals(AdminHistoryCategory.ALL, AdminHistoryCategory.fromLabel("nonsense"));
        assertEquals(AdminHistoryCategory.ALL, AdminHistoryCategory.fromLabel(null));
    }

    @Test
    public void accountTabIsLabelledForAdminsNotDevelopers() {
        assertEquals("Account", AdminHistoryCategory.ACCOUNT.getLabel());
    }

    // ------------------------------------------------------------- helpers

    private static void assertCategory(AdminHistoryCategory expected, String action) {
        assertEquals("action '" + action + "' should be filed under " + expected.getLabel(),
                expected, AdminHistoryClassifier.categorize(action));
    }
}
