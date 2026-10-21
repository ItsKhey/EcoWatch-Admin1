package com.example.ecowatchadmin1.ui.profile;

import com.example.ecowatchadmin1.utils.AdminHistoryClassifier;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.ecowatchadmin1.data.models.History;

/**
 * One card on the Admin History screen, flattened for display.
 *
 * <p>Exists to keep the card compact, as the design calls for: a title, the
 * report it concerns, who acted, and the timestamp. The full record stays in
 * {@link History} and is reached through View Details, so no card has to carry a
 * report description, coordinates or photos.
 */
public final class AdminHistoryRow {

    private final History history;
    private final String title;
    private final String icon;
    private final String subtitle;

    private AdminHistoryRow(History history, String title, String icon, String subtitle) {
        this.history = history;
        this.title = title;
        this.icon = icon;
        this.subtitle = subtitle;
    }

    /**
     * Builds the card for a history row.
     *
     * <p>The subtitle is the single most useful secondary line, chosen in order:
     * the report reference, then the actor, then the server's description. A row
     * with none of them yields an empty subtitle and the card simply shows title
     * and time.
     */
    @NonNull
    public static AdminHistoryRow from(@NonNull History history) {
        String title = AdminHistoryClassifier.title(history.getAction());
        String icon = AdminHistoryClassifier.icon(history.getAction());
        return new AdminHistoryRow(history, title, icon, subtitleFor(history));
    }

    private static String subtitleFor(History history) {
        String reportId = trimToNull(history.getReportId());
        if (reportId != null) return "Report #" + reportId;

        String actor = trimToNull(history.getActorId());
        if (actor != null) return actor;

        return trimToNull(history.getDescription());
    }

    public History getHistory() {
        return history;
    }

    public int getId() {
        return history.getId();
    }

    public String getTitle() {
        return title;
    }

    public String getIcon() {
        return icon;
    }

    /** The secondary line, or an empty string when the row carries nothing extra. */
    public String getSubtitle() {
        return subtitle;
    }

    @Nullable
    public Long getTimestamp() {
        return history.getTimestampValue();
    }

    private static String trimToNull(@Nullable String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
