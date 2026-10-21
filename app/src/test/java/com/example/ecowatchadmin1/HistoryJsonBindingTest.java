package com.example.ecowatchadmin1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchadmin1.data.models.History;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.junit.Test;

/**
 * Parses a real {@code GET /api/admin/history} row and asserts every field the
 * screen reads actually binds.
 *
 * <p>The admin feed and the resident feed both return PostgreSQL column names, so
 * they are snake_case. Retrofit is built with a plain
 * {@code GsonConverterFactory.create()} and no field naming policy, which means
 * Gson matches the exact Java field name and nothing else. A camelCase field with
 * no {@code @SerializedName} therefore stays null for a row the server actually
 * sent, and the failure is silent: the screen renders a blank field rather than
 * reporting an error.
 *
 * <p>The JSON below is copied verbatim from the live endpoint, including the
 * {@code null}s, so a column being renamed or dropped upstream shows up here
 * instead of on the device.
 */
public class HistoryJsonBindingTest {

    private static final Gson GSON = new GsonBuilder().create();

    /** A real announcement row, exactly as the admin feed returns it. */
    private static final String ANNOUNCEMENT_ROW = "{"
            + "\"history_id\":\"4239\","
            + "\"resident_id\":null,"
            + "\"report_id\":null,"
            + "\"action\":\"ANNOUNCEMENT_PUBLISHED\","
            + "\"old_status\":null,"
            + "\"new_status\":null,"
            + "\"description\":\"Announcement \\\"Race guard\\\" published to residents.\","
            + "\"admin_id\":\"ADM-ANNOUNCE-EXPIRY-TEST\","
            + "\"category\":\"ANNOUNCEMENTS\","
            + "\"subject_type\":\"ANNOUNCEMENT\","
            + "\"subject_id\":\"4526\","
            + "\"subject_label\":\"Race guard\","
            + "\"is_deleted\":false,"
            + "\"deleted_at\":null,"
            + "\"timestamp\":\"2026-10-08T10:26:03.197Z\""
            + "}";

    /** A real report-scoped row: resident, report and status columns are populated. */
    private static final String REPORT_ROW = "{"
            + "\"history_id\":\"4152\","
            + "\"resident_id\":\"RES-P1X0RAJG\","
            + "\"report_id\":\"REP-8FE859F3\","
            + "\"action\":\"STATUS_CHANGED\","
            + "\"old_status\":\"Pending\","
            + "\"new_status\":\"In Progress\","
            + "\"description\":\"Status changed from Pending to In Progress.\","
            + "\"admin_id\":\"ADM-92A9EDDB\","
            + "\"category\":\"REPORTS\","
            + "\"subject_type\":null,"
            + "\"subject_id\":null,"
            + "\"subject_label\":null,"
            + "\"is_deleted\":false,"
            + "\"deleted_at\":null,"
            + "\"timestamp\":\"2026-10-08T10:04:53.334Z\""
            + "}";

    /** A trashed row, which is what the Trash view has to be able to see. */
    private static final String TRASHED_ROW = "{"
            + "\"history_id\":\"9001\","
            + "\"resident_id\":\"RES-P1X0RAJG\","
            + "\"report_id\":\"REP-8FE859F3\","
            + "\"action\":\"MOVED_TO_TRASH\","
            + "\"old_status\":null,"
            + "\"new_status\":null,"
            + "\"description\":\"Report moved to trash.\","
            + "\"admin_id\":\"ADM-92A9EDDB\","
            + "\"category\":\"REPORTS\","
            + "\"subject_type\":null,"
            + "\"subject_id\":null,"
            + "\"subject_label\":null,"
            + "\"is_deleted\":true,"
            + "\"deleted_at\":\"2026-10-08T11:00:00.000Z\","
            + "\"timestamp\":\"2026-10-08T10:59:00.000Z\""
            + "}";

    private static History parse(String json) {
        return GSON.fromJson(json, History.class);
    }

    /**
     * The server's identifier. Already bound through an alternate name; this guards
     * it against being lost while the other names are corrected.
     */
    @Test
    public void serverHistoryIdBindsToTheLocalKey() {
        assertEquals("4239", parse(ANNOUNCEMENT_ROW).getMongoId());
    }

    /**
     * The fields that name what the record is about.
     *
     * <p>These drive the report reference line and the card subtitle. Bound as null,
     * a card silently loses its subject and shows only a description.
     */
    @Test
    public void subjectFieldsBind() {
        History announcement = parse(ANNOUNCEMENT_ROW);

        assertEquals("ANNOUNCEMENT", announcement.getSubjectType());
        assertEquals("4526", announcement.getSubjectId());
        assertEquals("Race guard", announcement.getSubjectLabel());

        History report = parse(REPORT_ROW);
        assertEquals("RES-P1X0RAJG", report.getResidentId());
        assertEquals("REP-8FE859F3", report.getReportId());
    }

    /**
     * The actor and the status transition.
     *
     * <p>{@code View Details} prints both, so an unbound field shows as a missing
     * line in the dialog rather than as an error.
     */
    @Test
    public void actorAndStatusFieldsBind() {
        History row = parse(REPORT_ROW);

        assertEquals("ADM-92A9EDDB", row.getActorId());
        assertEquals("Pending", row.getOldStatus());
        assertEquals("In Progress", row.getNewStatus());
    }

    /**
     * The regression that made the Trash view permanently empty.
     *
     * <p>{@code is_deleted} never reached {@code isDeleted}, so every parsed row
     * reported itself as active. Nothing could ever satisfy the trash query's
     * {@code WHERE isDeleted = 1}, and a trashed record rendered as a live one on
     * the All tab instead.
     */
    @Test
    public void trashStateBindsFromTheServerFlag() {
        assertFalse("an active row must not report itself as trashed",
                parse(REPORT_ROW).isDeleted());
        assertTrue("a trashed row must report itself as trashed",
                parse(TRASHED_ROW).isDeleted());
    }

    /** The removal time, which the trash list sorts by. */
    @Test
    public void deletionTimestampBinds() {
        Long deletedAt = parse(TRASHED_ROW).getDeletedAt();

        assertNotNull("deleted_at must not be dropped", deletedAt);
        assertTrue("an ISO-8601 instant should land in the modern epoch, not 1970",
                deletedAt > 1_700_000_000_000L);
    }

    /** The published timestamp, which every card renders. */
    @Test
    public void publicationTimestampBinds() {
        Long timestamp = parse(REPORT_ROW).getTimestampValue();

        assertNotNull(timestamp);
        assertTrue("2026 must not decode as 1970", timestamp > 1_700_000_000_000L);
    }

    /** The category the server filed the row under, which drives the tab. */
    @Test
    public void categoryBinds() {
        assertEquals("ANNOUNCEMENTS", parse(ANNOUNCEMENT_ROW).getCategory());
        assertEquals("REPORTS", parse(REPORT_ROW).getCategory());
    }

    /** Action and description are camelCase-safe already; this guards them. */
    @Test
    public void actionAndDescriptionBind() {
        History row = parse(REPORT_ROW);

        assertEquals("STATUS_CHANGED", row.getAction());
        assertEquals("Status changed from Pending to In Progress.", row.getDescription());
    }

    /**
     * The resident feed addresses its rows by {@code _id} while the admin feed uses
     * {@code history_id}. Both must land on the same local key, because a row that
     * cached without one cannot be addressed by the bulk trash operations.
     */
    @Test
    public void residentFeedIdentifierAlsoBinds() {
        String residentRow = "{\"_id\":\"777\",\"action\":\"SUBMITTED\",\"resident_id\":\"RES-1\"}";

        assertEquals("777", parse(residentRow).getMongoId());
    }
}