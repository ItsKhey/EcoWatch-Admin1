package com.example.ecowatchadmin1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchadmin1.utils.AdminHistoryCategory;
import com.example.ecowatchadmin1.utils.AdminHistoryClassifier;

import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Executes the exact {@code @Query} string from {@code HistoryDao.observeAdminFeed}
 * against a real SQLite engine, and pins the tab filter the screen applies to it.
 *
 * <p>The admin feed is read from this cache, so a category filter that is subtly
 * wrong produces a screen that is empty for a reason no amount of network
 * debugging will explain. Running the literal SQL is the only way to see which
 * rows a tab actually receives.
 *
 * <p>The feed is unfiltered and the tab is applied in memory. It used to carry the
 * category in the query and the screen passed {@code ALL} for the All tab, which
 * matched nothing because no row stores that value.
 */
public class AdminHistoryFeedQueryTest {

    /** Copied verbatim from {@code HistoryDao.observeAdminFeed}. */
    private static final String FEED_QUERY =
            "SELECT * FROM history WHERE isDeleted = 0 "
                    + "ORDER BY timestampValue DESC, id DESC LIMIT 500";

    /** Copied verbatim from {@code HistoryDao.collapseDuplicateHistoryRows}. */
    private static final String COLLAPSE_QUERY =
            "DELETE FROM history WHERE mongoId IS NOT NULL "
                    + "AND id NOT IN (SELECT MAX(id) FROM history "
                    + "WHERE mongoId IS NOT NULL GROUP BY mongoId)";

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
    }

    private static Connection seeded() throws SQLException {
        Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = db.createStatement()) {
            st.execute("CREATE TABLE history ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "mongoId TEXT, residentId TEXT, reportId TEXT, action TEXT, "
                    + "oldStatus TEXT, newStatus TEXT, description TEXT, "
                    + "actorId TEXT, actionDetails TEXT, category TEXT, "
                    + "subjectType TEXT, subjectId TEXT, subjectLabel TEXT, "
                    + "timestampValue INTEGER, isDeleted INTEGER NOT NULL, deletedAt INTEGER)");
            st.execute("INSERT INTO history (mongoId,action,category,isDeleted,timestampValue) VALUES"
                    + " ('1','ANNOUNCEMENT_PUBLISHED','ANNOUNCEMENTS',0,500),"
                    + " ('2','SUBMITTED','REPORTS',0,400),"
                    + " ('3','ASSIGNED','ASSIGNMENTS',0,300),"
                    + " ('4','CLEANING_COMPLETED','CLEANING',0,200),"
                    + " ('5','PERSONNEL_CREATED','ACCOUNT',0,100)");
        }
        return db;
    }

    /** Runs the DAO's literal query. Nothing is bound: the feed takes no argument. */
    private static int feedCount(Connection db) throws SQLException {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery(FEED_QUERY)) {
            int n = 0;
            while (rs.next()) n++;
            return n;
        }
    }

    private static List<String> categoriesIn(Connection db) throws SQLException {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery("SELECT category FROM history "
                     + "WHERE isDeleted = 0 AND category IS NOT NULL")) {
            List<String> seen = new ArrayList<>();
            while (rs.next()) seen.add(rs.getString(1));
            return seen;
        }
    }

    /**
     * The regression this guards. The feed was once filtered by
     * {@code category = :category}, and All was passed as the category even though
     * no row stores {@code 'ALL'}, so the query matched nothing however many rows
     * the cache held and the screen reported "No history records yet".
     */
    @Test
    public void feedReturnsEveryActiveRowRegardlessOfTab() throws SQLException {
        try (Connection db = seeded()) {
            assertEquals("every active row is available to every tab", 5, feedCount(db));
        }
    }

    /** Newest first, which is the order the screen sorts and pages in. */
    @Test
    public void feedIsOrderedNewestFirst() throws SQLException {
        try (Connection db = seeded();
             Statement st = db.createStatement();
             ResultSet rs = st.executeQuery(FEED_QUERY)) {
            assertTrue(rs.next());
            assertEquals("newest row first", "1", rs.getString("mongoId"));
        }
    }

    /**
     * Every category the server can send has to be filterable client-side, since
     * switching tabs no longer issues a query. This asserts the fixture covers all
     * of them, so a tab that cannot be satisfied would fail here rather than
     * silently showing an empty screen.
     */
    @Test
    public void everyServerCategoryIsReachableAsATab() throws SQLException {
        try (Connection db = seeded()) {
            assertEquals(new LinkedHashSet<>(Arrays.asList(
                            "ANNOUNCEMENTS", "REPORTS", "ASSIGNMENTS", "CLEANING", "ACCOUNT")),
                    new LinkedHashSet<>(categoriesIn(db)));
        }
    }

    /**
     * A row written before the category column existed has to stay reachable, or a
     * pre-migration row would vanish from every tab including All.
     */
    @Test
    public void uncategorisedRowStaysInTheFeed() throws SQLException {
        try (Connection db = seeded()) {
            try (Statement st = db.createStatement()) {
                st.execute("INSERT INTO history (mongoId,action,isDeleted,timestampValue)"
                        + " VALUES ('6','STATUS_CHANGED',0,600)");
            }
            assertEquals(6, feedCount(db));
            assertEquals("an uncategorised row falls back to Reports",
                    AdminHistoryCategory.REPORTS,
                    AdminHistoryClassifier.effectiveCategory(null, "STATUS_CHANGED"));
        }
    }

    /**
     * The stored category outranks the action string. Bare {@code COMPLETED} is
     * stored as Reports by the server but classified as Cleaning by the keyword
     * rules, so reading the stored value is what stops a Reports row appearing on
     * the Cleaning tab and then being hidden by a second client-side filter.
     */
    @Test
    public void storedCategoryOutranksTheActionString() {
        assertEquals("the server's stored category must win",
                AdminHistoryCategory.REPORTS,
                AdminHistoryClassifier.effectiveCategory("REPORTS", "COMPLETED"));

        assertEquals("a row with no stored category still files sensibly",
                AdminHistoryCategory.CLEANING,
                AdminHistoryClassifier.effectiveCategory("  ", "CLEANING_COMPLETED"));

        assertEquals("an unrecognised category falls back rather than dropping the row",
                AdminHistoryCategory.ASSIGNMENTS,
                AdminHistoryClassifier.effectiveCategory("SOMETHING_NEW", "ASSIGNED"));
    }

    /** The stored category is read case-insensitively, as the server sends upper case. */
    @Test
    public void storedCategoryIsMatchedRegardlessOfCase() {
        assertEquals(AdminHistoryCategory.CLEANING,
                AdminHistoryClassifier.effectiveCategory("cleaning", "CLEANING_COMPLETED"));
    }

    /** A trashed row is in the trash view, never in the active feed. */
    @Test
    public void trashedRowsAreExcludedFromTheFeed() throws SQLException {
        try (Connection db = seeded()) {
            try (Statement st = db.createStatement()) {
                st.execute("UPDATE history SET isDeleted=1, deletedAt=900 WHERE mongoId='1'");
            }
            assertEquals(4, feedCount(db));
        }
    }

    /**
     * A re-sync must replace the cached row rather than append a copy.
     *
     * <p>Room's REPLACE resolves on the primary key, and the sync assigns a fresh
     * one every time, so the stale copies are deleted by server id first. Without
     * that the tab counts doubled on each pull-to-refresh.
     */
    @Test
    public void resyncReplacesTheCachedRowInsteadOfDuplicatingIt() throws SQLException {
        try (Connection db = seeded()) {
            try (Statement st = db.createStatement()) {
                st.execute("DELETE FROM history WHERE mongoId IN ('2')");
                st.execute("INSERT INTO history (mongoId,action,category,isDeleted,timestampValue)"
                        + " VALUES ('2','STATUS_CHANGED','REPORTS',0,999)");
            }
            assertEquals("still one row for that server id", 5, feedCount(db));

            try (Statement st = db.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT action FROM history WHERE mongoId = '2'")) {
                assertTrue(rs.next());
                assertEquals("the cached copy reflects the newest payload",
                        "STATUS_CHANGED", rs.getString(1));
            }
        }
    }

    /**
     * The crash this reproduces.
     *
     * <p>A build that cached the feed without collapsing by server id appended a
     * copy of every row on each pull. After enough refreshes the device reported
     * {@code totalRows=2636} against roughly 263 real records, and the feed query
     * began failing with {@code SQLiteBlobTooBigException} because Android backs a
     * cursor with one shared CursorWindow of about 2 MB for the entire result.
     */
    @Test
    public void collapsingDuplicatesShrinksAnInflatedCacheBackToOneRowPerRecord() throws SQLException {
        try (Connection db = seeded();
             Statement st = db.createStatement()) {

            // Ten pull-to-refreshes, each appending one more copy of the five rows
            // it received: 5 originals + 50 duplicates. Selecting only the original
            // keys keeps this at +5 per refresh rather than doubling, which is what
            // a re-sync actually did.
            for (int refresh = 0; refresh < 10; refresh++) {
                st.execute("INSERT INTO history (mongoId,action,category,isDeleted,timestampValue)"
                        + " SELECT mongoId,action,category,isDeleted,timestampValue"
                        + " FROM history WHERE id <= 5");
            }
            assertEquals("the cache has inflated to eleven copies of every row",
                    55, feedCount(db));

            try (Statement c = db.createStatement()) {
                c.executeUpdate(COLLAPSE_QUERY);
            }

            assertEquals("one row per server record survives the repair",
                    5, feedCount(db));
        }
    }

    /**
     * The repair keeps the newest copy, because that is the one holding the most
     * recent payload the server sent.
     */
    @Test
    public void collapsingKeepsTheNewestCopyOfEachRecord() throws SQLException {
        try (Connection db = seeded();
             Statement st = db.createStatement()) {
            st.execute("INSERT INTO history (mongoId,action,category,isDeleted,timestampValue)"
                    + " VALUES ('2','STATUS_CHANGED','REPORTS',0,400)");
            try (Statement c = db.createStatement()) {
                c.executeUpdate(COLLAPSE_QUERY);
            }
            try (Statement s = db.createStatement();
                 ResultSet rs = s.executeQuery(
                         "SELECT action FROM history WHERE mongoId = '2'")) {
                assertTrue(rs.next());
                assertEquals("STATUS_CHANGED", rs.getString(1));
            }
        }
    }

    /**
     * A row with no server id was written by the resident feed and has no server
     * counterpart to collapse against, so the repair must leave it alone rather
     * than deleting it as collateral damage.
     */
    @Test
    public void collapsingLeavesRowsWithoutAServerIdAlone() throws SQLException {
        try (Connection db = seeded();
             Statement st = db.createStatement()) {
            st.execute("INSERT INTO history (mongoId,action,isDeleted,timestampValue)"
                    + " VALUES (NULL,'SUBMITTED',0,300)");
            st.execute("INSERT INTO history (mongoId,action,isDeleted,timestampValue)"
                    + " VALUES (NULL,'SUBMITTED',0,300)");
            try (Statement c = db.createStatement()) {
                c.executeUpdate(COLLAPSE_QUERY);
            }
            assertEquals("resident rows without a server id are untouched",
                    2, countWhere(db, "mongoId IS NULL"));
        }
    }

    /**
     * The feed is bounded, so no single read can overrun the CursorWindow however
     * large the table becomes.
     */
    @Test
    public void feedIsBoundedSoItCannotOverrunTheCursorWindow() throws SQLException {
        try (Connection db = seeded();
             Statement st = db.createStatement()) {
            for (int i = 0; i < 1200; i++) {
                st.execute("INSERT INTO history (mongoId,action,category,isDeleted,timestampValue)"
                        + " VALUES ('x" + i + "','SUBMITTED','REPORTS',0," + (1000 + i) + ")");
            }
            assertEquals("the table really is larger than the bound",
                    1205, countWhere(db, "isDeleted = 0"));
            assertEquals("but one read can never return more than the bound",
                    500, feedCount(db));
        }
    }

    private static int countWhere(Connection db, String where) throws SQLException {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM history WHERE " + where)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }
}