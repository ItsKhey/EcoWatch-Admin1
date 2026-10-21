package com.example.ecowatchadmin1.ui.residents;

import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.ResidentAccount;
import com.example.ecowatchadmin1.data.remote.ServerConfig;
import com.example.ecowatchadmin1.utils.AdminAccountDateFormatter;

/** Small shared presentation helpers for reporter cards and details. */
public final class ReporterAccountUi {

    private ReporterAccountUi() {
        // Utility class.
    }

    public static void loadAvatar(ImageView view, String rawUrl) {
        android.content.Context context = view.getContext();
        if (view == null) return;
        Glide.with(view)
                .load(resolveImageUrl(context, rawUrl))
                .placeholder(R.drawable.ic_person_default)
                .error(R.drawable.ic_person_default)
                .centerCrop()
                .into(view);
    }

    public static String resolveImageUrl(android.content.Context context, String rawUrl) {
        // Relative paths resolve against whichever server this build targets.
        return ServerConfig.absoluteUrl(context, rawUrl);
    }

    public static String formatLastActive(String rawTimestamp) {
        if (rawTimestamp == null || rawTimestamp.trim().isEmpty()) return "Never";
        String formatted = AdminAccountDateFormatter.format(rawTimestamp);
        return formatted == null ? rawTimestamp : formatted;
    }

    public static String formatDate(String rawTimestamp) {
        if (rawTimestamp == null || rawTimestamp.trim().isEmpty()) return "Not available";
        String formatted = AdminAccountDateFormatter.format(rawTimestamp);
        return formatted == null ? rawTimestamp : formatted;
    }

    public static void bindStatus(TextView view, ResidentAccount resident) {
        if (view == null || resident == null) return;
        boolean disabled = resident.isDisabled();
        view.setText(disabled
                ? R.string.reporter_status_disabled
                : R.string.reporter_status_active);
        view.setTextColor(view.getContext().getColor(disabled
                ? R.color.reporter_status_disabled_text
                : R.color.reporter_status_active_text));
        view.setBackgroundResource(disabled
                ? R.drawable.reporter_status_disabled_background
                : R.drawable.reporter_status_active_background);
        view.setContentDescription(view.getContext().getString(
                R.string.reporter_status_description, view.getText()));
    }

    public static String valueOrDash(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value;
    }
}
