package com.example.ecowatchadmin1.utils;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Turns a task-timeline action into the sentence shown on the personnel
 * history card.
 *
 * <p>The server sends the audit action constant, not display text, so the
 * wording lives here where it can be unit-tested and changed without touching
 * the adapter. Unknown actions are humanised rather than dropped: if the
 * backend learns a new cleaning phase, the card still shows that it happened.
 */
public final class ReportTimeline {
    private ReportTimeline() {
    }

    /** The label for one timeline event, or null when the action is blank. */
    @Nullable
    public static String labelFor(@Nullable String action) {
        if (action == null || action.trim().isEmpty()) return null;
        String normalized = action.trim().toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "CLEANING_STARTED":
                return "Cleaning started";
            case "BEFORE_EVIDENCE_SUBMITTED":
                return "Before photos submitted";
            case "AFTER_EVIDENCE_SUBMITTED":
                return "After photos submitted";
            case "CLEANING_COMPLETED":
                return "Cleaning completed";
            case "REVISION_REQUESTED":
                return "Revision requested by admin";
            case "COMPLETED":
                return "Approved by admin";
            default:
                return humanize(normalized);
        }
    }

    /** "SOME_NEW_ACTION" becomes "Some new action". */
    private static String humanize(String action) {
        String words = action.replace('_', ' ').replace('-', ' ').trim()
                .toLowerCase(Locale.ROOT);
        if (words.isEmpty()) return null;
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
