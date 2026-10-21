package com.example.ecowatchadmin1.ui.announcements;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Announcement;
import com.example.ecowatchadmin1.utils.NotificationDateFormatter;

/**
 * Binds one announcement to a row inflated from {@code item_announcement}.
 *
 * <p>Shared by the dashboard's "Your Announcements" preview and the full
 * announcements list so a row can never look different depending on which
 * screen the administrator is looking at.
 */
public final class AnnouncementRowBinder {

    private AnnouncementRowBinder() {
    }

    /**
     * Fills {@code itemView} with the announcement's title, message, publish
     * time, and importance badge.
     *
     * <p>Missing text falls back to a placeholder rather than rendering a blank
     * or literal "null" row, so a partly populated announcement is still
     * recognizable.
     *
     * <p>Deliberately leaves the row's delete control alone. The dashboard preview
     * and the full list share this layout, and a destructive action belongs only
     * on the screen that offers a confirmation; callers hide or wire it.
     */
    public static void bind(@NonNull View itemView, @NonNull Announcement announcement) {
        TextView titleView = itemView.findViewById(R.id.announcementItemTitle);
        TextView bodyView = itemView.findViewById(R.id.announcementItemBody);
        TextView timeView = itemView.findViewById(R.id.announcementItemTime);
        TextView badgeView = itemView.findViewById(R.id.announcementPriorityBadge);

        String title = announcement.getTitle();
        titleView.setText(isBlank(title)
                ? itemView.getContext().getString(R.string.announcement_untitled)
                : title);

        String message = announcement.getMessage();
        bodyView.setText(isBlank(message)
                ? itemView.getContext().getString(R.string.announcement_no_body)
                : message);

        timeView.setText(NotificationDateFormatter.format(announcement.getCreatedAt()));

        badgeView.setVisibility(announcement.isImportant() ? View.VISIBLE : View.GONE);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
