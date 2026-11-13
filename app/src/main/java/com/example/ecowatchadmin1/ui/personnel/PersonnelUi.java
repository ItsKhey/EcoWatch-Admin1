package com.example.ecowatchadmin1.ui.personnel;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.utils.AdminAccountDateFormatter;
import com.example.ecowatchadmin1.utils.ReportSubmittedAtFormatter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Presentation helpers shared by the personnel list and the personnel details
 * screen, so a status pill looks identical in both places.
 */
public final class PersonnelUi {

    private PersonnelUi() {
        // Utility class.
    }

    /** Human label for the four states the account pill can show. */
    public static String statusLabel(Context context, Personnel person) {
        if (!person.isEnabled()) return context.getString(R.string.personnel_status_disabled);
        if (person.isCleaning()) return context.getString(R.string.personnel_cleaning);
        if (person.isOnline()) return context.getString(R.string.personnel_online);
        return context.getString(R.string.personnel_filter_offline);
    }

    /** Applies the status text plus its coloured pill background. */
    public static void bindStatus(TextView view, Personnel person) {
        if (view == null || person == null) return;
        Context context = view.getContext();

        int background;
        int foreground;
        if (!person.isEnabled()) {
            background = R.color.personnel_disabled_background;
            foreground = R.color.personnel_disabled;
        } else if (person.isCleaning()) {
            background = R.color.personnel_cleaning_background;
            foreground = R.color.personnel_cleaning;
        } else if (person.isOnline()) {
            background = R.color.personnel_online_background;
            foreground = R.color.personnel_online;
        } else {
            background = R.color.personnel_offline_background;
            foreground = R.color.personnel_offline;
        }

        view.setText(statusLabel(context, person));
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(ContextCompat.getColor(context, background));
        drawable.setCornerRadius(24 * context.getResources().getDisplayMetrics().density);
        view.setBackground(drawable);
        view.setTextColor(ContextCompat.getColor(context, foreground));
    }

    public static void loadAvatar(ImageView view, String rawUrl) {
        if (view == null) return;
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            view.setImageResource(R.drawable.ic_person_default);
            return;
        }
        Glide.with(view)
                .load(com.example.ecowatchadmin1.ui.residents.ReporterAccountUi
                        .resolveImageUrl(view.getContext(), rawUrl))
                .placeholder(R.drawable.ic_person_default)
                .error(R.drawable.ic_person_default)
                .centerCrop()
                .into(view);
    }

    public static String formatDate(String rawTimestamp) {
        if (rawTimestamp == null || rawTimestamp.trim().isEmpty()) return "Not available";
        String formatted = AdminAccountDateFormatter.format(rawTimestamp);
        return formatted == null ? rawTimestamp : formatted;
    }

    public static String formatLastActive(String rawTimestamp) {
        if (rawTimestamp == null || rawTimestamp.trim().isEmpty()) return "Never";
        String formatted = AdminAccountDateFormatter.format(rawTimestamp);
        return formatted == null ? rawTimestamp : formatted;
    }

    /**
     * Relative text for the "Last Collection" line, matching the vehicle card.
     *
     * <p>Minutes, hours and days are counted from now; after that the exact
     * date is more useful than a count. An absent stamp reads "No Collection
     * Yet" rather than a dash so the column explains itself.
     */
    public static String formatLastCollection(String rawTimestamp) {
        if (rawTimestamp == null || rawTimestamp.trim().isEmpty()) return "No Collection Yet";
        String value = rawTimestamp.trim();
        Date date = ReportSubmittedAtFormatter.parse(value);
        if (date == null) return value;

        long diff = System.currentTimeMillis() - date.getTime();
        if (diff < 0) diff = 0;

        long minutes = diff / (60 * 1000L);
        if (minutes < 1) return "Just Now";
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " Minute Ago" : " Minutes Ago");
        }

        long hours = diff / (60 * 60 * 1000L);
        if (hours < 24) {
            return hours + (hours == 1 ? " Hour Ago" : " Hours Ago");
        }

        long days = diff / (24 * 60 * 60 * 1000L);
        if (days < 100) {
            return days + (days == 1 ? " Day Ago" : " Days Ago");
        }

        SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yy", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
        return formatter.format(date);
    }

    /**
     * Date-and-time text for the details screens' "Last Collection" row,
     * matching the vehicle details page.
     *
     * <p>The list uses the relative form because rows sit next to each other
     * and a count of minutes is easy to scan. A single details page has room
     * for the exact moment, which is what an administrator needs when
     * reconciling a collection against a report.
     */
    public static String formatCollectionDateTime(String rawTimestamp) {
        if (rawTimestamp == null || rawTimestamp.trim().isEmpty()) return "No Collection Yet";
        String value = rawTimestamp.trim();
        String formatted = AdminAccountDateFormatter.format(value);
        return formatted == null ? value : formatted;
    }

    /**
     * The newest completed collection in a history page, or null when there is
     * no finished task yet.
     *
     * <p>The work-completed stamp is preferred, then the resolution stamp, then
     * the report's last update: the first two describe the cleanup itself,
     * while the third is only a safe stand-in for older rows.
     */
    public static String latestCollectionTimestamp(List<Report> reports) {
        if (reports == null) return null;
        Date latest = null;
        String latestRaw = null;
        for (Report report : reports) {
            if (report == null || !isCompletedCollection(report)) continue;
            String[] candidates = new String[]{
                    report.getWorkCompletedAt(), report.getResolvedAt(), report.getUpdatedAt()
            };
            for (String candidate : candidates) {
                Date parsed = ReportSubmittedAtFormatter.parse(candidate);
                if (parsed == null) continue;
                if (latest == null || parsed.after(latest)) {
                    latest = parsed;
                    latestRaw = candidate;
                }
                // The first parseable stamp is the most specific for this row.
                break;
            }
        }
        return latestRaw;
    }

    private static boolean isCompletedCollection(Report report) {
        String status = report.getStatus() == null
                ? "" : report.getStatus().trim().toLowerCase(Locale.ROOT);
        return report.hasCompletedWork()
                || status.contains("complete")
                || status.contains("resolved")
                || status.contains("approved")
                || status.contains("done");
    }

    public static String valueOrDash(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value;
    }
}
