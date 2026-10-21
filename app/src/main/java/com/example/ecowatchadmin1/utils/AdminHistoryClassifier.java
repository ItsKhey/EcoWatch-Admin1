package com.example.ecowatchadmin1.utils;

import java.util.Locale;

/**
 * Maps a history row's {@code action} onto the tab it belongs under, and derives
 * the compact title and icon shown on its card.
 *
 * <p>Deliberately pure and Android-free so the rules can be unit tested. The
 * server writes a flat, lowercase-free {@code SCREAMING_SNAKE} action and nothing
 * else: there is no category column in the history table and no admin-wide
 * endpoint to add one to. Classification therefore has to be derived here, and
 * the whole screen is only as good as this table.
 *
 * <p>Two rules govern the mapping, both of them about not lying to the admin:
 *
 * <ul>
 *   <li><b>Order matters.</b> {@code COMPLETED} is written both for a finished
 *       cleaning job and for a report an admin marked complete, and the two
 *       belong in different tabs. Assignment-scoped actions are checked before
 *       cleaning, and cleaning before reports, so the most specific reading wins.
 *       Reversing any pair of these blocks changes which tab a row lands in.</li>
 *   <li><b>An unknown action falls back to Reports, never to All-only.</b> Any row
 *       the table has not seen before is still shown, so a new server action
 *       cannot make history silently vanish from the screen.</li>
 * </ul>
 */
public final class AdminHistoryClassifier {

    private AdminHistoryClassifier() {
    }

    /**
     * The tab a history row belongs under.
     *
     * @param action the row's {@code action} as stored; may be null
     */
    public static AdminHistoryCategory categorize(String action) {
        String a = normalize(action);
        if (a.isEmpty()) return AdminHistoryCategory.REPORTS;

        // --- Cleaning: checked before Assignments, because it is more specific ---
        //
        // CLEANING_ASSIGNMENT_VERIFIED names an assignment and is still a cleaning
        // record. Testing ASSIGN first would file it under Assignments purely
        // because that keyword appears earlier in the string.
        if (contains(a, "CLEANING") || contains(a, "CLEANUP")
                || contains(a, "CLEANED") || contains(a, "EVIDENCE")
                || contains(a, "REVISION") || contains(a, "REVERIFY")) {
            return AdminHistoryCategory.CLEANING;
        }

        // --- Assignments: personnel management ---
        if (contains(a, "ASSIGN") || contains(a, "REASSIGN") || contains(a, "UNASSIGN")) {
            // "ASSIGNMENT_COMPLETED" reads as both an assignment event and a
            // finished cleaning job. The assignment is what the admin changed, so
            // it is reported as an assignment; the cleaning tab gets the distinct
            // CLEANING_* actions instead.
            return AdminHistoryCategory.ASSIGNMENTS;
        }

        // --- Cleaning: a bare completion or start is the post-assignment event ---
        if (a.equals("COMPLETED") || a.startsWith("COMPLETED ")
                || a.equals("STARTED") || a.startsWith("STARTED ")
                || a.equals("INCOMPLETE") || a.startsWith("INCOMPLETE ")) {
            return AdminHistoryCategory.CLEANING;
        }

        // --- Announcements ---
        if (contains(a, "ANNOUNCEMENT") || contains(a, "ANNOUNCED")
                || contains(a, "PUBLISH") || a.equals("PUBLISHED")
                || contains(a, "BROADCAST")) {
            return AdminHistoryCategory.ANNOUNCEMENTS;
        }

        // --- Report-scoped, checked before Account because "ACCOUNT" appears in
        // report actions too (a change of the account a report belongs to) ---
        if (contains(a, "REPORT")) {
            return AdminHistoryCategory.REPORTS;
        }

        // --- ESP / admin account management ---
        if (contains(a, "ACCOUNT") || contains(a, "PERSONNEL_") || contains(a, "ESP")
                || contains(a, "ACTIVATED") || contains(a, "DEACTIVATED")
                || contains(a, "REACTIVATED") || contains(a, "ROLE")
                || contains(a, "CREDENTIAL") || contains(a, "PASSWORD_RESET")) {
            return AdminHistoryCategory.ACCOUNT;
        }

        // --- Reports: the fallback, so an unrecognised action is never lost ---
        return AdminHistoryCategory.REPORTS;
    }

    /**
     * The tab a cached row actually belongs under.
     *
     * <p>The server stores the category on the row and that value wins. Deriving it
     * from the action again on the client is what produced two visible bugs: a row
     * the server filed under Reports could be labelled Cleaning, and a tab could
     * show nothing because the client's keyword reading disagreed with the
     * category the rows had been filtered by.
     *
     * <p>A row with no stored category predates the column, so it falls back to the
     * action and then to Reports, which is the same order the server uses. The two
     * therefore agree on every row.
     *
     * @param storedCategory the row's {@code category} column; may be null or blank
     * @param action         the row's {@code action} as stored; may be null
     */
    public static AdminHistoryCategory effectiveCategory(String storedCategory, String action) {
        if (storedCategory == null || storedCategory.trim().isEmpty()) {
            return categorize(action);
        }
        try {
            return AdminHistoryCategory.valueOf(storedCategory.trim().toUpperCase(Locale.US));
        } catch (IllegalArgumentException notAKnownCategory) {
            // A category this build does not recognise is still the server's
            // decision to make; fall back rather than dropping the row out of
            // every tab.
            return categorize(action);
        }
    }

    /**
     * A concise, human title for a card.
     *
     * <p>Turns {@code NOT_DUPLICATE} into "Not Duplicate" rather than the raw
     * constant, and prefers the server's own description when it carries a more
     * specific sentence than the action name.
     */
    public static String title(String action) {
        String a = normalize(action);
        if (a.isEmpty()) return "Activity";

        // Underscores are word separators, so they are replaced before splitting.
        // Without this, NOT_DUPLICATE renders as "Not_duplicate".
        String[] words = a.replace('_', ' ').replace('-', ' ').split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.US));
        }
        return out.length() == 0 ? "Activity" : out.toString();
    }

    /** The glyph shown on a card, chosen by the same action rules as the tab. */
    public static String icon(String action) {
        String a = normalize(action);
        if (a.isEmpty()) return "•";

        // Checked in the same precedence as categorize(), so the glyph always
        // agrees with the tab the row is filed under.
        if (contains(a, "CLEANING") || contains(a, "STARTED")) return "🧹";
        if (contains(a, "EVIDENCE")) return "📷";
        if (contains(a, "COMPLETED") || contains(a, "CLEANED")) return "✓";
        if (contains(a, "ASSIGN") || contains(a, "UNASSIGN")) return "👤";
        if (contains(a, "ANNOUNCEMENT") || contains(a, "PUBLISH")) return "📢";
        if (contains(a, "ACCOUNT") || contains(a, "ACTIVATED")
                || contains(a, "DEACTIVATED") || contains(a, "PASSWORD")) return "🔑";
        if (contains(a, "PERMANENTLY_DELETED") || contains(a, "DELETED")) return "🗑";
        if (contains(a, "NOT_DUPLICATE") || contains(a, "ACCEPTED")) return "✓";
        if (contains(a, "DUPLICATE")) return "🗑";
        if (contains(a, "DENIED") || contains(a, "DENY")) return "✕";
        if (contains(a, "ARCHIVED")) return "📦";
        if (contains(a, "RESTORED")) return "📤";
        if (contains(a, "MOVED_TO_TRASH") || contains(a, "TRASH")) return "🗑";
        if (contains(a, "STATUS_CHANGED") || contains(a, "UPDATED")) return "⚙";
        if (contains(a, "SUBMITTED")) return "📝";
        return "•";
    }

    /**
     * Whether a row should appear under the given tab.
     *
     * <p>All and Trash are view states rather than categories: {@code ALL} matches
     * every row, and the caller handles trash separately because trashed rows are
     * selected by {@code isDeleted} rather than by action.
     */
    public static boolean matches(String action, AdminHistoryCategory tab) {
        if (tab == null || tab == AdminHistoryCategory.ALL) return true;
        return categorize(action) == tab;
    }

    /**
     * Whether a free-text query matches this row.
     *
     * <p>Searches the action, description, report id and actor, because an admin
     * looking for a decision usually knows the report number or their own id
     * rather than the exact wording of the action.
     */
    public static boolean matchesQuery(String query, String action, String description,
                                       String reportId, String actorId) {
        if (query == null) return true;
        String q = query.trim().toLowerCase(Locale.US);
        if (q.isEmpty()) return true;

        return containsIgnoreCase(action, q)
                || containsIgnoreCase(description, q)
                || containsIgnoreCase(reportId, q)
                || containsIgnoreCase(actorId, q);
    }

    /**
     * Whether a row's timestamp falls inside an inclusive {@code [from, to]} range.
     *
     * <p>Either bound may be null for an open-ended range, which is how the custom
     * picker leaves a side unset. A null timestamp is included only when the range
     * is open on both sides, so an undated row is never shown to be inside a
     * window it cannot be placed in.
     */
    public static boolean withinRange(Long timestamp, Long fromInclusive, Long toInclusive) {
        if (timestamp == null) return fromInclusive == null && toInclusive == null;
        if (fromInclusive != null && timestamp < fromInclusive) return false;
        if (toInclusive != null && timestamp > toInclusive) return false;
        return true;
    }

    private static String normalize(String action) {
        return action == null ? "" : action.trim().toUpperCase(Locale.US);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack.contains(needle);
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null) return false;
        return haystack.toLowerCase(Locale.US).contains(needle);
    }
}
