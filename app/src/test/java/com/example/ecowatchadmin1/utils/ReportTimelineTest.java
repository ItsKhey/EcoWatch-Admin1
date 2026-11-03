package com.example.ecowatchadmin1.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * The labels shown for each cleaning event on the personnel task timeline.
 *
 * <p>The server sends action constants, so the client owns the wording. These
 * cases pin the four phases an administrator reads on the card and the fallback
 * that keeps an unknown future phase visible instead of silently dropping it.
 */
public class ReportTimelineTest {

    @Test
    public void labelsEveryKnownCleaningPhase() {
        assertEquals("Cleaning started", ReportTimeline.labelFor("CLEANING_STARTED"));
        assertEquals("Before photos submitted",
                ReportTimeline.labelFor("BEFORE_EVIDENCE_SUBMITTED"));
        assertEquals("After photos submitted",
                ReportTimeline.labelFor("AFTER_EVIDENCE_SUBMITTED"));
        assertEquals("Cleaning completed", ReportTimeline.labelFor("CLEANING_COMPLETED"));
        assertEquals("Revision requested by admin",
                ReportTimeline.labelFor("REVISION_REQUESTED"));
        assertEquals("Approved by admin", ReportTimeline.labelFor("COMPLETED"));
    }

    /** Server spelling may vary in case, so matching is case-insensitive. */
    @Test
    public void actionMatchingIgnoresCaseAndPadding() {
        assertEquals("Cleaning started", ReportTimeline.labelFor("  cleaning_started "));
    }

    /** An action this build has never seen is still rendered, not dropped. */
    @Test
    public void unknownActionIsHumanized() {
        assertEquals("Some new action", ReportTimeline.labelFor("SOME_NEW_ACTION"));
    }

    @Test
    public void blankActionHasNoLabel() {
        assertNull(ReportTimeline.labelFor(null));
        assertNull(ReportTimeline.labelFor("   "));
    }
}
