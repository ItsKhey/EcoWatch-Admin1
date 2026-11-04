package com.example.ecowatchadmin1.ui.reports;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.remote.ServerConfig;
import com.example.ecowatchadmin1.utils.ReportSubmittedAtFormatter;
import com.example.ecowatchadmin1.utils.WasteScaleDisplay;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Full report-by-report view for one Possible Duplicate Reports group.
 *
 * <p>Opened from the review card's SEE ALL button. The inline expansion on the
 * card is a quick comparison; this dialog is the "take a closer look" step,
 * showing every report with its photo, description, submitted time, location
 * and distance from the first report, all on one scrollable page so two
 * submissions can be compared without dismissing anything and deciding
 * afterwards.
 *
 * <p>Read-only on purpose. The decision itself stays on the card, so there is
 * exactly one place where DENY and ACCEPT can be pressed and both remain behind
 * a confirmation.
 */
final class PossibleDuplicateDetailDialog {

    private static final String TAG = "PossibleDuplicateDetail";

    private PossibleDuplicateDetailDialog() {
    }

    static void show(@NonNull Context context, @NonNull PossibleDuplicateGroup group) {
        List<Report> reports = group.getReports() == null
                ? new ArrayList<>() : group.getReports();

        View content = LayoutInflater.from(context)
                .inflate(R.layout.dialog_duplicate_review_detail, null, false);

        ((TextView) content.findViewById(R.id.duplicateDetailSubtitle))
                .setText(context.getString(R.string.possible_duplicate_count, reports.size()));
        ((TextView) content.findViewById(R.id.duplicateDetailContext))
                .setText(context.getString(R.string.possible_duplicate_detail_context,
                        WasteScaleDisplay.labelFor(context, group.getWasteScale())));

        LinearLayout list = content.findViewById(R.id.duplicateDetailList);
        if (reports.isEmpty()) {
            // A group with no reports should not render an empty page; the
            // repository already filters these out, so this is a guard.
            list.addView(noReportsRow(context));
        } else {
            Report first = reports.get(0);
            for (Report report : reports) {
                View card = LayoutInflater.from(context)
                        .inflate(R.layout.item_duplicate_review_detail, list, false);
                bindReportCard(context, card, report, first);
                list.addView(card);
            }
        }

        Dialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(content)
                .create();

        content.findViewById(R.id.duplicateDetailClose)
                .setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static View noReportsRow(Context context) {
        TextView text = new TextView(context);
        text.setText("No reports to compare.");
        text.setTextSize(13f);
        int padding = Math.round(16 * context.getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, padding);
        return text;
    }

    private static void bindReportCard(Context context, View card, Report report,
                                       @Nullable Report first) {
        ((TextView) card.findViewById(R.id.detailRowTitle)).setText(
                context.getString(R.string.possible_duplicate_report_reference,
                        DuplicateReviewFormatter.referenceOf(report)));
        ((TextView) card.findViewById(R.id.detailRowStatus))
                .setText(DuplicateReviewFormatter.statusOf(report));
        ((TextView) card.findViewById(R.id.detailRowResident))
                .setText(context.getString(R.string.possible_duplicate_resident_label,
                        report.getResidentLabel()));
        ((TextView) card.findViewById(R.id.detailRowCategory)).setText(
                context.getString(R.string.review_row_category_scale,
                        DuplicateReviewFormatter.wasteTypeOf(report),
                        DuplicateReviewFormatter.scaleLabelOf(context, report)));
        ((TextView) card.findViewById(R.id.detailRowSubmitted)).setText(
                context.getString(R.string.review_row_submitted_label,
                        DuplicateReviewFormatter.submittedAt(report.getCreatedAt())));
        ((TextView) card.findViewById(R.id.detailRowLocation))
                .setText(DuplicateReviewFormatter.locationLine(context, report, first));
        ((TextView) card.findViewById(R.id.detailRowDescription))
                .setText(report.getDescription() == null || report.getDescription().trim().isEmpty()
                        ? context.getString(R.string.possible_duplicate_no_description)
                        : report.getDescription());

        ShapeableImageView photo = card.findViewById(R.id.detailRowPhoto);
        Object imageSource = DuplicateReviewFormatter.photoSourceOf(report, context);
        if (imageSource == null) {
            // A report without a photo still needs its row to look deliberate
            // rather than broken.
            photo.setVisibility(View.GONE);
        } else {
            Glide.with(context).load(imageSource).centerCrop().into(photo);
        }
    }
}