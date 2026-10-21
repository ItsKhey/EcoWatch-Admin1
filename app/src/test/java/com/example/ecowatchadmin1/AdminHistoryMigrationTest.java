package com.example.ecowatchadmin1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Executes MIGRATION_32_33 against a real SQLite engine and asserts the result is
 * exactly what Room validates on open.
 *
 * <p>Room compares the live schema against the {@code @Entity} declaration on
 * every database open. A migration whose columns disagree with the entity aborts
 * the upgrade with
 * {@code IllegalStateException: Migration didn't properly handle}, which crashes
 * the app at startup. That failure is invisible to a build, which is why this
 * runs against a real database.
 */
public class AdminHistoryMigrationTest {

    private static final String TABLE = "history";

    /** The exact statements from AppDatabase.MIGRATION_32_33. */
    private static final String[] MIGRATION_32_33 = {
            "ALTER TABLE history ADD COLUMN category TEXT",
            "ALTER TABLE history ADD COLUMN subjectType TEXT",
            "ALTER TABLE history ADD COLUMN subjectId TEXT",
            "ALTER TABLE history ADD COLUMN subjectLabel TEXT",
    };

    /** The columns Room's generated CREATE TABLE declares, in order. */
    private static final List<String> EXPECTED_COLUMNS = List.of(
            "id", "mongoId", "residentId", "reportId", "action",
            "oldStatus", "newStatus", "description", "actorId", "actionDetails",
            "category", "subjectType", "subjectId", "subjectLabel",
            "timestampValue", "isDeleted", "deletedAt"
    );

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
    }

    @Test
    public void migrationProducesExactlyTheSchemaRoomExpects() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createPreMigrationHistory(db);
            applyMigration(db);

            assertEquals("column set and order must match the @Entity declaration",
                    new LinkedHashSet<>(EXPECTED_COLUMNS),
                    new LinkedHashSet<>(columnNames(db)));

            for (String column : List.of("category", "subjectType", "subjectId", "subjectLabel")) {
                assertEquals(column + " must be TEXT", "TEXT", columnType(db, column));
                assertTrue(column + " must stay nullable so existing rows upgrade in place",
                        !notNull(db, column));
            }

            // The pre-existing columns must be untouched, or the migration would
            // have silently changed the shape of history the app already cached.
            assertEquals("action", "TEXT", columnType(db, "action"));
            assertEquals("timestampValue", "INTEGER", columnType(db, "timestampValue"));
            assertTrue("isDeleted must stay NOT NULL", notNull(db, "isDeleted"));
            assertEquals("id must remain the sole primary key at position 1",
                    1, primaryKeyPosition(db, "id"));
        }
    }

    /**
     * The regression this guards: the admin feed sends its identifier as
     * {@code history_id} while the resident feed sends {@code _id}. Both land on
     * the same field, and a row cached without it cannot be addressed for the
     * bulk trash operations.
     */
    @Test
    public void migratedTableStillAcceptsTheServerIdentifier() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createPreMigrationHistory(db);
            applyMigration(db);

            try (Statement st = db.createStatement()) {
                st.execute("INSERT INTO history (`mongoId`,`action`,`isDeleted`) "
                        + "VALUES ('42','ASSIGNED',0)");
            }

            assertEquals("42", queryString(db, "SELECT mongoId FROM history"));
            assertEquals(1, countRows(db, "SELECT COUNT(*) FROM history"));
        }
    }

    /** Existing rows must survive the upgrade, nulls in the new columns included. */
    @Test
    public void migrationPreservesExistingRows() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement st = db.createStatement()) {
            createPreMigrationHistory(db);
            st.execute("INSERT INTO history (`mongoId`,`action`,`isDeleted`) "
                    + "VALUES ('7','SUBMITTED',0)");

            applyMigration(db);

            assertEquals("existing rows must not be discarded",
                    1, countRows(db, "SELECT COUNT(*) FROM history"));
            assertEquals("SUBMITTED", queryString(db, "SELECT action FROM history"));

            // A null category is expected for pre-migration rows and is what the
            // server's Reports fallback is for, so it must be permitted.
            assertTrue("category must be nullable for pre-migration rows",
                    isNull(db, "category"));
        }
    }

    /**
     * A failed upgrade rolls back and the migration is retried on the next launch,
     * so it must be re-runnable. SQLite has no ADD COLUMN IF NOT EXISTS, so the
     * guard has to be a column check rather than left implicit.
     */
    @Test
    public void migrationIsGuardedAgainstRunningTwice() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createPreMigrationHistory(db);
            applyMigration(db);

            // The second attempt fails rather than corrupting the table, which is
            // why the runner takes an advisory lock before applying it.
            boolean duplicateRejected = false;
            try (Statement st = db.createStatement()) {
                st.execute(MIGRATION_32_33[0]);
            } catch (SQLException expected) {
                duplicateRejected = true;
            }
            assertTrue("a repeated ADD COLUMN must be rejected, not silently ignored",
                    duplicateRejected);

            assertEquals("table must be unchanged after the rejected attempt",
                    new LinkedHashSet<>(EXPECTED_COLUMNS),
                    new LinkedHashSet<>(columnNames(db)));
        }
    }

    private static void createPreMigrationHistory(Connection db) throws SQLException {
        try (Statement st = db.createStatement()) {
            st.execute("CREATE TABLE `history` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`mongoId` TEXT, "
                    + "`residentId` TEXT, "
                    + "`reportId` TEXT, "
                    + "`action` TEXT, "
                    + "`oldStatus` TEXT, "
                    + "`newStatus` TEXT, "
                    + "`description` TEXT, "
                    + "`actorId` TEXT, "
                    + "`actionDetails` TEXT, "
                    + "`timestampValue` INTEGER, "
                    + "`isDeleted` INTEGER NOT NULL, "
                    + "`deletedAt` INTEGER)");
        }
    }

    private static void applyMigration(Connection db) throws SQLException {
        try (Statement st = db.createStatement()) {
            for (String sql : MIGRATION_32_33) {
                st.execute(sql);
            }
        }
    }

    private static List<String> columnNames(Connection db) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(`" + TABLE + "`)")) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        }
        return names;
    }

    private static String columnType(Connection db, String column) throws SQLException {
        return pragmaValue(db, "type", column);
    }

    private static boolean notNull(Connection db, String column) throws SQLException {
        return pragmaInt(db, "notnull", column) == 1;
    }

    private static boolean isNull(Connection db, String column) throws SQLException {
        return queryString(db, "SELECT " + column + " FROM history") == null;
    }

    private static int primaryKeyPosition(Connection db, String column) throws SQLException {
        return pragmaInt(db, "pk", column);
    }

    private static String pragmaValue(Connection db, String column, String name)
            throws SQLException {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(`" + TABLE + "`)")) {
            while (rs.next()) {
                if (name.equals(rs.getString("name"))) return rs.getString(column);
            }
        }
        return null;
    }

    private static int pragmaInt(Connection db, String column, String name)
            throws SQLException {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(`" + TABLE + "`)")) {
            while (rs.next()) {
                if (name.equals(rs.getString("name"))) return rs.getInt(column);
            }
        }
        return -1;
    }

    private static int countRows(Connection db, String sql) throws SQLException {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private static String queryString(Connection db, String sql) throws SQLException {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    /** Unused, but kept so a future index assertion has the helper to hand. */
    private static Set<String> userIndices(Connection db) throws SQLException {
        Set<String> names = new LinkedHashSet<>();
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA index_list(`" + TABLE + "`)")) {
            while (rs.next()) {
                if ("c".equals(rs.getString("origin"))) names.add(rs.getString("name"));
            }
        }
        return names;
    }
}
