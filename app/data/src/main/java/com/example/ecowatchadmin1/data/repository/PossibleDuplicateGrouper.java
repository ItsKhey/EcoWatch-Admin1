package com.example.ecowatchadmin1.data.repository;

import com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.utils.ReportSubmittedAtFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns the server's location groups into review groups.
 *
 * <p>The backend knows which reports sit at the same spot, but a spot can hold
 * a small pile and a large one a few metres apart. Those are different
 * incidents, so this class splits each location by waste scale before the UI
 * sees it. Submission time is deliberately unused: two reports filed days
 * apart are still duplicates when the location and scale agree.
 *
 * <p>Only open reports belong in the queue: Pending, In Progress and Under
 * Review. Completed work has already been cleared and a Denied report has
 * already been rejected, so neither can still receive a duplicate decision.
 * The resident's nearby scan applies the same exclusion, so a pair only ever
 * reaches review when both sides are still open. Filtering here also keeps the
 * app correct against an older server that ignores the rule.
 *
 * <p>The group id carries the scale as a suffix ({@code CL-123456::medium}) so
 * a decision applies only to the reports the administrator actually reviewed.
 * The backend understands the same suffix.
 */
public final class PossibleDuplicateGrouper {

    public static final String GROUP_ID_SEPARATOR = "::";

    public static final String SCALE_KEY_SMALL = "small";
    public static final String SCALE_KEY_MEDIUM = "medium";
    public static final String SCALE_KEY_LARGE = "large";

    /** The only report statuses that can still receive a duplicate decision. */
    public static final String STATUS_PENDING = "Pending";
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_UNDER_REVIEW = "Under Review";
    public static final String STATUS_COMPLETED = "Completed";
    public static final String STATUS_DENIED = "Denied";

    private static final String[] SCALE_ORDER = new String[]{
            SCALE_KEY_SMALL, SCALE_KEY_MEDIUM, SCALE_KEY_LARGE
    };

    private PossibleDuplicateGrouper() {
        // Utility class.
    }

    /**
     * Regroups server-provided location groups into scale-scoped review groups.
     * A group needs at least two distinct reports; anything smaller is not a
     * duplicate pair.
     */
    public static List<PossibleDuplicateGroup> regroup(
            List<PossibleDuplicateGroup> serverGroups) {
        List<PossibleDuplicateGroup> result = new ArrayList<>();
        if (serverGroups == null || serverGroups.isEmpty()) return result;

        Set<String> seenGroupIds = new HashSet<>();
        for (PossibleDuplicateGroup serverGroup : serverGroups) {
            if (serverGroup == null) continue;

            String baseGroupId = baseGroupId(serverGroup.getGroupId());
            if (baseGroupId == null) continue;

            Map<String, List<Report>> byScale = new LinkedHashMap<>();
            Set<String> seenReports = new HashSet<>();
            for (Report report : nonNullReports(serverGroup.getReports())) {
                if (report == null) continue;

                // Only open reports can still be reviewed. Completed work has
                // already been cleared, and a Denied report was already
                // rejected; the backend applies the same rule to its queue.
                if (!isEligibleForReview(report)) continue;

                // The same report can appear twice in a sloppy payload. Counting
                // it twice would turn one report into a fake duplicate pair.
                String reportId = reportIdentity(report);
                if (reportId != null && !seenReports.add(reportId)) continue;

                String scaleKey = scaleKeyOf(report);
                List<Report> reports = byScale.get(scaleKey);
                if (reports == null) {
                    reports = new ArrayList<>();
                    byScale.put(scaleKey, reports);
                }
                reports.add(report);
            }

            for (String scaleKey : SCALE_ORDER) {
                List<Report> reports = byScale.get(scaleKey);
                if (reports == null || reports.size() < 2) continue;

                List<Report> sorted = new ArrayList<>(reports);
                Collections.sort(sorted, PossibleDuplicateGrouper::compareOldestFirst);

                String groupId = baseGroupId + GROUP_ID_SEPARATOR + scaleKey;
                // If a server payload repeats the same group, keep the first one
                // rather than showing two identical review cards.
                if (!seenGroupIds.add(groupId)) continue;

                PossibleDuplicateGroup group = new PossibleDuplicateGroup();
                group.setGroupId(groupId);
                group.setWasteScale(canonicalScaleOfKey(scaleKey));
                group.setReports(sorted);
                group.setCenterLatitude(centroidLatitude(
                        sorted, serverGroup.getCenterLatitude()));
                group.setCenterLongitude(centroidLongitude(
                        sorted, serverGroup.getCenterLongitude()));
                result.add(group);
            }
        }
        return result;
    }

    /** The server group id without a scale suffix, or null when there is none. */
    public static String baseGroupId(String groupId) {
        if (groupId == null || groupId.trim().isEmpty()) return null;
        String trimmed = groupId.trim();
        int separator = trimmed.lastIndexOf(GROUP_ID_SEPARATOR);
        if (separator <= 0) return trimmed;

        String suffix = trimmed.substring(separator + GROUP_ID_SEPARATOR.length())
                .trim().toLowerCase(Locale.ROOT);
        for (String scaleKey : SCALE_ORDER) {
            if (scaleKey.equals(suffix)) {
                return trimmed.substring(0, separator).trim();
            }
        }
        return trimmed;
    }

    /** The scale key carried by a group id, or null when it carries none. */
    public static String scaleKeyFromGroupId(String groupId) {
        if (groupId == null) return null;
        String trimmed = groupId.trim();
        int separator = trimmed.lastIndexOf(GROUP_ID_SEPARATOR);
        if (separator <= 0) return null;

        String suffix = trimmed.substring(separator + GROUP_ID_SEPARATOR.length())
                .trim().toLowerCase(Locale.ROOT);
        for (String scaleKey : SCALE_ORDER) {
            if (scaleKey.equals(suffix)) return scaleKey;
        }
        return null;
    }

    /** Normalizes any spelling the server or a legacy report might carry. */
    public static String scaleKeyOf(String rawScale) {
        String value = rawScale == null
                ? "" : rawScale.trim().toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace(' ', '-');
        if (value.startsWith(SCALE_KEY_LARGE)) return SCALE_KEY_LARGE;
        if (value.startsWith(SCALE_KEY_MEDIUM)) return SCALE_KEY_MEDIUM;
        // Missing scales predate the field and were handled as small jobs.
        return SCALE_KEY_SMALL;
    }

    /** The scale key of a report, using the same default as the rest of the app. */
    public static String scaleKeyOf(Report report) {
        return report == null ? SCALE_KEY_SMALL : scaleKeyOf(report.getWasteScale());
    }

    /** True when a report was reviewed and rejected as a duplicate. */
    public static boolean isDenied(Report report) {
        return statusOf(report).equalsIgnoreCase(STATUS_DENIED);
    }

    /**
     * Whether a report can still appear in the possible-duplicate queue.
     *
     * <p>Pending, In Progress and Under Review are open. Completed and Denied
     * are terminal and are excluded, even when an older server still sends them.
     */
    public static boolean isEligibleForReview(Report report) {
        String status = statusOf(report);
        return status.equalsIgnoreCase(STATUS_PENDING)
                || status.equalsIgnoreCase(STATUS_IN_PROGRESS)
                || status.equalsIgnoreCase(STATUS_UNDER_REVIEW);
    }

    /**
     * Whether the deny action can still change this report's status.
     *
     * <p>Only statuses the queue allows can be denied. Completed and Denied
     * reports are filtered out earlier, so this stays aligned with
     * {@link #isEligibleForReview(Report)}.
     */
    public static boolean canBeDenied(Report report) {
        return isEligibleForReview(report);
    }

    private static String statusOf(Report report) {
        if (report == null || report.getStatus() == null) return "";
        return report.getStatus().trim();
    }

    /** The canonical scale text for a key. */
    public static String canonicalScaleOfKey(String scaleKey) {
        if (SCALE_KEY_LARGE.equals(scaleKey)) return Report.SCALE_LARGE;
        if (SCALE_KEY_MEDIUM.equals(scaleKey)) return Report.SCALE_MEDIUM;
        return Report.SCALE_SMALL;
    }

    private static List<Report> nonNullReports(List<Report> reports) {
        return reports == null ? Collections.emptyList() : reports;
    }

    private static String reportIdentity(Report report) {
        String id = report.getReportId();
        if (id != null && !id.trim().isEmpty()) return id.trim();
        id = report.getMongoId();
        if (id != null && !id.trim().isEmpty()) return id.trim();
        return null;
    }

    private static int compareOldestFirst(Report left, Report right) {
        long leftTime = submittedAtMillis(left);
        long rightTime = submittedAtMillis(right);
        int byTime = Long.compare(leftTime, rightTime);
        if (byTime != 0) return byTime;

        // A stable tie-break keeps the "first report" from jumping between
        // refreshes when two reports share a timestamp.
        String leftId = reportIdentity(left);
        String rightId = reportIdentity(right);
        if (leftId == null && rightId == null) return 0;
        if (leftId == null) return 1;
        if (rightId == null) return -1;
        return leftId.compareToIgnoreCase(rightId);
    }

    private static long submittedAtMillis(Report report) {
        Date submittedAt = ReportSubmittedAtFormatter.parse(report.getCreatedAt());
        return submittedAt == null ? Long.MAX_VALUE : submittedAt.getTime();
    }

    private static Double centroidLatitude(List<Report> reports, Double fallback) {
        double sum = 0;
        int count = 0;
        for (Report report : reports) {
            double value = report.getLatitude();
            if (isValidLatitude(value)) {
                sum += value;
                count++;
            }
        }
        if (count == 0) return fallback;
        return sum / count;
    }

    private static Double centroidLongitude(List<Report> reports, Double fallback) {
        double sum = 0;
        int count = 0;
        for (Report report : reports) {
            double value = report.getLongitude();
            if (isValidLongitude(value)) {
                sum += value;
                count++;
            }
        }
        if (count == 0) return fallback;
        return sum / count;
    }

    private static boolean isValidLatitude(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value)
                && value >= -90 && value <= 90;
    }

    private static boolean isValidLongitude(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value)
                && value >= -180 && value <= 180;
    }
}
