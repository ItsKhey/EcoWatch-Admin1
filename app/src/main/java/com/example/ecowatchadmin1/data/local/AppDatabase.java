package com.example.ecowatchadmin1.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.models.User;
import com.example.ecowatchadmin1.data.models.History;
import com.example.ecowatchadmin1.data.models.Notification;
import com.example.ecowatchadmin1.data.models.BiometricSetting;
import com.example.ecowatchadmin1.data.models.NotificationSetting;
import com.example.ecowatchadmin1.data.models.WorkforceGroup;

@Database(entities = {User.class, Report.class, History.class, Notification.class, BiometricSetting.class, NotificationSetting.class, WorkforceGroup.class}, version = 35, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    /**
     * Adds how big the waste is, and what went out to deal with it.
     *
     * <p>The scale is chosen by the resident when the report is filed and is
     * what the dispatch is bound by: one cleaner for a small job, a crew for a
     * medium one, a crew and a truck for a large one. The crew and the truck
     * are cached alongside the report so the detail dialog can name them
     * without a second request.
     *
     * <p>All nullable, so existing rows upgrade in place and are backfilled by
     * the server on the next sync.
     */
    private static final Migration MIGRATION_33_34 = new Migration(33, 34) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE reports ADD COLUMN wasteScale TEXT");
            database.execSQL("ALTER TABLE reports ADD COLUMN assignedTeamId TEXT");
            database.execSQL("ALTER TABLE reports ADD COLUMN assignedTeamName TEXT");
            database.execSQL("ALTER TABLE reports ADD COLUMN assignedVehicleId TEXT");
            database.execSQL("ALTER TABLE reports ADD COLUMN assignedVehiclePlateNumber TEXT");
            database.execSQL("ALTER TABLE reports ADD COLUMN assignedVehicleType TEXT");
        }
    };

    private static final Migration MIGRATION_30_31 = new Migration(30, 31) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Migration for schema update in version 31.
        }
    };

    /**
     * Adds the personnel's finished work: the note they submitted and who did it.
     *
     * Kept out of the assignment columns on purpose. Approving a report releases
     * the assignment, so the assigned personnel fields go blank on exactly the
     * reports the administrator still needs to attribute, and the cleaner's note
     * never lived on the report row at all.
     */
    private static final Migration MIGRATION_29_30 = new Migration(29, 30) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE reports ADD COLUMN personnelResponse TEXT");
            database.execSQL("ALTER TABLE reports ADD COLUMN completedByPersonnelId TEXT");
            database.execSQL("ALTER TABLE reports ADD COLUMN completedByPersonnelName TEXT");
            database.execSQL("ALTER TABLE reports ADD COLUMN completedByPersonnelAvatar TEXT");
            database.execSQL("ALTER TABLE reports ADD COLUMN workCompletedAt TEXT");
        }
    };

    private static final Migration MIGRATION_28_29 = new Migration(28, 29) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE reports ADD COLUMN personnelNote TEXT");
        }
    };

    /**
     * Adds photo arrays for before/after cleaning proof and creates table
     * for workforce groups.
     */
    private static final Migration MIGRATION_27_28 = new Migration(27, 28) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE reports ADD COLUMN beforePhotoUrls TEXT");
            database.execSQL("ALTER TABLE reports ADD COLUMN afterPhotoUrls TEXT");
            database.execSQL("CREATE TABLE IF NOT EXISTS workforce_groups ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`mongoId` TEXT, "
                    + "`groupName` TEXT, "
                    + "`memberIds` TEXT)");
        }
    };

    /**
     * Adds the resident's display name alongside the report.
     *
     * A report row only ever stored resident_id, so every list that shows who
     * filed a report could only show the ID. The server now joins the name onto
     * the report payload, so it is cached alongside the rest rather than
     * refetched.
     *
     * Nullable on purpose: existing rows have no name until the next sync, and a
     * report whose resident account was deleted never gets one.
     */
    private static final Migration MIGRATION_26_27 = new Migration(26, 27) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE reports ADD COLUMN residentName TEXT");
        }
    };

    /**
     * Adds the per-report photo list used by the multi-photo badge. Existing
     * rows keep only the cover image and are repopulated on the next sync, so
     * the upgrade never discards data.
     */
    private static final Migration MIGRATION_25_26 = new Migration(25, 26) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE reports ADD COLUMN photoUrls TEXT");
        }
    };

    /**
     * Keeps the server's own grouping key on the cached report row. The admin UI
     * never shows it; it exists so a report keeps its nearby-detection identity
     * after a cache round trip.
     */
    private static final Migration MIGRATION_24_25 = new Migration(24, 25) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE reports ADD COLUMN clusterId TEXT");
        }
    };

    private static final Migration MIGRATION_23_24 = new Migration(23, 24) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Admin-specific notification categories. Existing columns are
            // reused by the new rows so an installed app keeps its choices;
            // only genuinely new categories are added, each defaulting to on.
            database.execSQL("ALTER TABLE notification_settings ADD COLUMN highPriorityReports INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE notification_settings ADD COLUMN personnelAccepted INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE notification_settings ADD COLUMN personnelCleaning INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE notification_settings ADD COLUMN photoUpdates INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE notification_settings ADD COLUMN awaitingApproval INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE notification_settings ADD COLUMN archivedReports INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE notification_settings ADD COLUMN maintenanceAlerts INTEGER NOT NULL DEFAULT 1");
        }
    };

    private static final Migration MIGRATION_22_23 = new Migration(22, 23) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE notifications ADD COLUMN reportId TEXT");
            database.execSQL("ALTER TABLE notifications ADD COLUMN deletedAt TEXT");

            // Older polling used the local auto-generated Room ID as a fallback
            // server key, which can create duplicate-looking rows. Preserve the
            // newest copy before adding the account-scoped unique index.
            database.execSQL(
                    "DELETE FROM notifications WHERE mongoId IS NOT NULL "
                            + "AND residentId IS NOT NULL AND id NOT IN ("
                            + "SELECT MAX(id) FROM notifications "
                            + "WHERE mongoId IS NOT NULL AND residentId IS NOT NULL "
                            + "GROUP BY residentId, mongoId)"
            );
            database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_notifications_residentId_mongoId "
                            + "ON notifications(residentId, mongoId)"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notifications_residentId_reportId "
                            + "ON notifications(residentId, reportId)"
            );
        }
    };

    private static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE reports ADD COLUMN assignedPersonnelId TEXT");
            database.execSQL("ALTER TABLE reports ADD COLUMN assignedPersonnelName TEXT");
            database.execSQL("ALTER TABLE reports ADD COLUMN assignedPersonnelAvatar TEXT");
            database.execSQL("ALTER TABLE reports ADD COLUMN assignedByAdminId TEXT");
            database.execSQL("ALTER TABLE reports ADD COLUMN assignedAt TEXT");
        }
    };

    /**
     * Adds the columns the Admin History screen needs.
     *
     * <p>The server now decides which tab a record belongs under and sends the
     * actor and the subject, so the client no longer re-derives any of it from
     * the action string. Without these columns the cached copy would drop the
     * category on every sync and every tab would show the same rows.
     *
     * <p>All nullable, so existing history rows upgrade in place and are
     * backfilled by the server on the next sync.
     */
    private static final Migration MIGRATION_32_33 = new Migration(32, 33) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE history ADD COLUMN category TEXT");
            database.execSQL("ALTER TABLE history ADD COLUMN subjectType TEXT");
            database.execSQL("ALTER TABLE history ADD COLUMN subjectId TEXT");
            database.execSQL("ALTER TABLE history ADD COLUMN subjectLabel TEXT");
        }
    };

    private static volatile AppDatabase instance;

    public abstract UserDao userDao();
    public abstract ReportDao reportDao();
    public abstract HistoryDao historyDao();
    public abstract NotificationDao notificationDao();
    public abstract BiometricDao biometricDao();
    public abstract NotificationSettingDao notificationSettingDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "ecowatch_db")
                            .addMigrations(MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_32_33, MIGRATION_33_34)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
