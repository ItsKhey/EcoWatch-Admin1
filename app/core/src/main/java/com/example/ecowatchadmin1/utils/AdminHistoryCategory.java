package com.example.ecowatchadmin1.utils;

/**
 * The five tabs the Admin History screen filters by.
 *
 * <p>Each tab is a bucket of administrative decisions, not a data source: an
 * announcement edit and an account deactivation are both admin actions, but they
 * answer different questions when someone is auditing what happened.
 */
public enum AdminHistoryCategory {

    /** Everything recorded. */
    ALL("All"),

    /**
     * Report status, actions and decisions: accepted/reviewed, denied, marked
     * duplicate, duplicate decision reversed, status changed, moved to In
     * Progress, marked Completed, restored/archived, permanently deleted, and
     * report information edited by an admin.
     */
    REPORTS("Reports"),

    /**
     * The administrative side of personnel management: assigned, assignment
     * changed, cancelled, reassigned, completed, rejected.
     */
    ASSIGNMENTS("Assignments"),

    /**
     * What happened after assignment: cleaning started, completed, before and
     * after evidence submitted, marked incomplete/failed, verified.
     */
    CLEANING("Cleaning"),

    /** Created, edited, published, unpublished and deleted announcements. */
    ANNOUNCEMENTS("Announcements"),

    /** ESP account created, activated, deactivated, restored, updated, deleted. */
    ACCOUNT("Account");

    private final String label;

    AdminHistoryCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Resolves a persisted tab name back to a category, defaulting to {@link #ALL}. */
    public static AdminHistoryCategory fromLabel(String label) {
        if (label == null) return ALL;
        for (AdminHistoryCategory category : values()) {
            if (category.label.equalsIgnoreCase(label.trim())) return category;
        }
        return ALL;
    }
}
