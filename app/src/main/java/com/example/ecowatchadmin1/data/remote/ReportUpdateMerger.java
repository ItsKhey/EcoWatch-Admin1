package com.example.ecowatchadmin1.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.ecowatchadmin1.data.models.Report;

/**
 * Prevents a partial assignment/status response from erasing immutable report
 * fields that were already returned by the backend.
 */
public final class ReportUpdateMerger {
    private ReportUpdateMerger() {
        // Utility class.
    }

    @NonNull
    public static Report merge(@Nullable Report existing, @NonNull Report update) {
        if (existing == null) return update;

        if (isBlank(update.getMongoId())) update.setMongoId(existing.getMongoId());
        if (isBlank(update.getReportId())) update.setReportId(existing.getReportId());
        if (isBlank(update.getResidentId())) update.setResidentId(existing.getResidentId());

        // AdminJsonParser uses these values when a mutation response omits
        // immutable fields. Do not let those parser defaults replace a full
        // report already received from the server.
        if (isBlank(update.getTitle()) || "Untitled report".equals(update.getTitle())) {
            update.setTitle(existing.getTitle());
        }
        if (isBlank(update.getWasteType()) || "Uncategorized".equals(update.getWasteType())) {
            update.setWasteType(existing.getWasteType());
        }
        if (isBlank(update.getDescription())) update.setDescription(existing.getDescription());
        if (isBlank(update.getPhotoUrl())) update.setPhotoUrl(existing.getPhotoUrl());
        if (update.getLatitude() == 0.0d && update.getLongitude() == 0.0d
                && (existing.getLatitude() != 0.0d || existing.getLongitude() != 0.0d)) {
            update.setLatitude(existing.getLatitude());
            update.setLongitude(existing.getLongitude());
        }

        // created_at is immutable and backend-owned. In particular, an older
        // assignment endpoint may return only the fields it changed.
        if (isBlank(update.getCreatedAt())) {
            update.setCreatedAt(existing.getCreatedAt());
        }
        return update;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
