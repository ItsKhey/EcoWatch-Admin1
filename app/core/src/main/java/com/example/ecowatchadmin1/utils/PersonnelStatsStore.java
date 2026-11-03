package com.example.ecowatchadmin1.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The last known value of the three task cards on the personnel details screen.
 *
 * <p>The server owns these numbers: {@code GET /admin/personnel/{id}/task-counts}
 * answers them, counted from the cleaner's assignment rows, and its answer
 * always repaints the cards. This store exists so
 * the screen does not open on the layout's placeholder zeros while that request
 * is in flight, and so an administrator who already looked at a cleaner keeps
 * seeing real numbers when the connection drops.
 *
 * <p>Saved per personnel id, because unlike the personnel app this screen is
 * visited for every cleaner on the roster: a single shared value would show one
 * cleaner's totals while another cleaner's page is loading.
 *
 * <p>Kept in preferences rather than a Room table for the same reason
 * {@code SavedAccounts} is in the personnel app: a handful of ints per cleaner,
 * written and read by key, never joined or queried. A database version bump and
 * a migration would be a lot of risk for a cache.
 */
public final class PersonnelStatsStore {

    private static final String PREFS = "ecowatch_personnel_stats";

    private PersonnelStatsStore() {
        // Utility class.
    }

    /** Immutable, so the screen and the store always agree on one set of numbers. */
    public static final class Stats {
        /** Every task this cleaner was ever assigned, matching the card label. */
        public final int totalAssigned;
        /** Work still in the cleaner's hands, matching the personnel app's onHand. */
        public final int onHand;
        public final int inProgress;
        public final int completed;

        public Stats(int totalAssigned, int onHand, int inProgress, int completed) {
            this.totalAssigned = Math.max(0, totalAssigned);
            this.onHand = Math.max(0, onHand);
            this.inProgress = Math.max(0, inProgress);
            this.completed = Math.max(0, completed);
        }

        /** Counts for a cleaner whose cards have never been answered. */
        public static Stats empty() {
            return new Stats(0, 0, 0, 0);
        }
    }

    /**
     * The counts recorded the last time this personnel's cards were answered,
     * or zeros for a cleaner this device has never looked at.
     *
     * @param personnelId the page's subject; a blank id reads as "nothing saved"
     */
    public static Stats load(Context context, String personnelId) {
        if (isBlank(personnelId)) {
            return Stats.empty();
        }
        SharedPreferences prefs = prefs(context);
        String prefix = personnelId.trim() + ":";
        return new Stats(
                prefs.getInt(prefix + "total_assigned", 0),
                prefs.getInt(prefix + "on_hand", 0),
                prefs.getInt(prefix + "in_progress", 0),
                prefs.getInt(prefix + "completed", 0));
    }

    /**
     * Records the counts the server just returned, replacing the previous ones.
     *
     * <p>Replaces rather than accumulates: these are point-in-time totals of a
     * list the server can add to or drop from, so the latest answer is the only
     * one that is true.
     *
     * @param personnelId the page's subject; a blank id is not recorded, because
     *                    there would be no way to recognise it as this cleaner's
     */
    public static void save(Context context, String personnelId, Stats stats) {
        if (isBlank(personnelId)) {
            return;
        }
        Stats value = stats == null ? Stats.empty() : stats;
        String prefix = personnelId.trim() + ":";
        prefs(context).edit()
                .putInt(prefix + "total_assigned", value.totalAssigned)
                .putInt(prefix + "on_hand", value.onHand)
                .putInt(prefix + "in_progress", value.inProgress)
                .putInt(prefix + "completed", value.completed)
                .apply();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
