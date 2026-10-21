package com.example.ecowatchadmin1.ui.personnel;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.utils.AdminAccountDateFormatter;

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

    public static String valueOrDash(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value;
    }
}
