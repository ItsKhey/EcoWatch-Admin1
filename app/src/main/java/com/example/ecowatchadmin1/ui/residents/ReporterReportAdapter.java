package com.example.ecowatchadmin1.ui.residents;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Report;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

/** Compact report-history cards used inside reporter details. */
public class ReporterReportAdapter extends RecyclerView.Adapter<ReporterReportAdapter.ReportViewHolder> {
    public interface Listener {
        void onReportClick(Report report);
    }

    private final List<Report> reports = new ArrayList<>();
    private Listener listener;

    public void setReports(List<Report> value) {
        reports.clear();
        if (value != null) reports.addAll(value);
        notifyDataSetChanged();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ReportViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reporter_report, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        Report report = reports.get(position);
        String reportId = report.getReportId();
        if (reportId == null || reportId.trim().isEmpty()) reportId = report.getMongoId();

        holder.reportId.setText(ReporterAccountUi.valueOrDash(reportId));
        holder.reportId.setContentDescription(holder.itemView.getContext().getString(
                R.string.reporter_report_id_description));
        holder.title.setText(report.getTitle() == null || report.getTitle().trim().isEmpty()
                ? "Untitled report" : report.getTitle());
        holder.category.setText(report.getWasteType() == null || report.getWasteType().trim().isEmpty()
                ? "Uncategorized" : report.getWasteType());
        holder.status.setText(report.getStatus() == null || report.getStatus().trim().isEmpty()
                ? "Pending" : report.getStatus());
        holder.status.setBackgroundResource(R.drawable.reporter_report_status_badge);
        String assigned = report.getAssignedPersonnelName();
        if (assigned == null || assigned.trim().isEmpty()) {
            List<String> ids = report.getAssignedPersonnelIds();
            assigned = ids == null || ids.isEmpty()
                    ? (report.getAssignedPersonnelId() == null ? "None" : report.getAssignedPersonnelId())
                    : String.join(", ", ids);
        }
        holder.assignedPersonnel.setText(holder.itemView.getContext().getString(
                R.string.assigned_personnel) + ": " + assigned);
        String response = report.getAdminResponse();
        holder.adminResponse.setText(holder.itemView.getContext().getString(
                R.string.admin_response_label) + ": "
                + (response == null || response.trim().isEmpty() ? "None" : response));
        holder.assignedAt.setText(holder.itemView.getContext().getString(
                R.string.assignment_time_label) + ": "
                + (report.getAssignedAt() == null || report.getAssignedAt().trim().isEmpty()
                ? "—" : ReporterAccountUi.formatDate(report.getAssignedAt())));
        if (report.isArchived() && report.isDeleted()) {
            holder.lifecycle.setText(R.string.report_lifecycle_archived_and_trashed);
            holder.lifecycle.setVisibility(View.VISIBLE);
        } else if (report.isArchived()) {
            holder.lifecycle.setText(R.string.report_lifecycle_archived);
            holder.lifecycle.setVisibility(View.VISIBLE);
        } else if (report.isDeleted()) {
            holder.lifecycle.setText(R.string.report_lifecycle_trashed);
            holder.lifecycle.setVisibility(View.VISIBLE);
        } else {
            holder.lifecycle.setVisibility(View.GONE);
        }
        holder.dateTime.setText(ReporterAccountUi.formatDate(report.getCreatedAt()));
        holder.coordinates.setText(holder.itemView.getContext().getString(
                R.string.reporter_coordinates_format,
                report.getLatitude(), report.getLongitude()));

        Glide.with(holder.itemView.getContext())
                .load(ReporterAccountUi.resolveImageUrl(holder.itemView.getContext(), report.getPhotoUrl()))
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .centerCrop()
                .into(holder.thumbnail);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onReportClick(report);
        });
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    static final class ReportViewHolder extends RecyclerView.ViewHolder {
        final ShapeableImageView thumbnail;
        final TextView reportId;
        final TextView title;
        final TextView category;
        final TextView status;
        final TextView assignedPersonnel;
        final TextView adminResponse;
        final TextView assignedAt;
        final TextView lifecycle;
        final TextView dateTime;
        final TextView coordinates;

        ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.reporterReportThumbnail);
            reportId = itemView.findViewById(R.id.reporterReportId);
            title = itemView.findViewById(R.id.reporterReportTitle);
            category = itemView.findViewById(R.id.reporterReportCategory);
            status = itemView.findViewById(R.id.reporterReportStatus);
            assignedPersonnel = itemView.findViewById(R.id.reporterReportAssignedPersonnel);
            adminResponse = itemView.findViewById(R.id.reporterReportAdminResponse);
            assignedAt = itemView.findViewById(R.id.reporterReportAssignedAt);
            lifecycle = itemView.findViewById(R.id.reporterReportLifecycle);
            dateTime = itemView.findViewById(R.id.reporterReportDateTime);
            coordinates = itemView.findViewById(R.id.reporterReportCoordinates);
        }
    }
}
