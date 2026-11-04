package com.example.ecowatchadmin1.ui.residents;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.models.ReportTimelineEvent;
import com.example.ecowatchadmin1.utils.ReportRevision;
import com.example.ecowatchadmin1.utils.ReportTimeline;
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
    private boolean showTimeline;

    public void setReports(List<Report> value) {
        reports.clear();
        if (value != null) reports.addAll(value);
        notifyDataSetChanged();
    }

    /**
     * Opts this list into the per-task timeline. Off by default so the same
     * compact card keeps serving the reporter, team, and vehicle histories,
     * whose payloads do not carry the events.
     */
    public void setShowTimeline(boolean showTimeline) {
        if (this.showTimeline == showTimeline) return;
        this.showTimeline = showTimeline;
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
        String rawStatus = report.getStatus();
        String historyStatus = ReportRevision.isRevision(report) ? "Revised" : rawStatus;
        if (historyStatus != null) {
            historyStatus = historyStatus.trim();
            String normalized = historyStatus.toLowerCase().replace('_', ' ').replace('-', ' ');
            if (normalized.contains("progress")) {
                historyStatus = "In Progress";
            } else if ("assigned".equals(normalized)) {
                historyStatus = "Assigned";
            } else if (normalized.contains("on hand") || normalized.contains("onhand")) {
                historyStatus = "On Hand";
            } else if (normalized.contains("under review")) {
                historyStatus = "Under Review";
            } else if (normalized.contains("denied") || normalized.contains("reject")) {
                historyStatus = "Denied";
            } else if (normalized.contains("revised")) {
                historyStatus = "Revised";
            } else if (normalized.contains("completed") || normalized.contains("resolved")) {
                historyStatus = "Completed";
            } else if (normalized.equals("pending") || normalized.contains("pending")) {
                historyStatus = "Pending";
            }
        }
        holder.status.setText(historyStatus == null || historyStatus.trim().isEmpty()
                ? "Pending" : historyStatus);
        holder.status.setBackgroundResource(R.drawable.reporter_report_status_badge);
        String team = report.getAssignedTeamLabel();
        if (team == null || team.trim().isEmpty()) {
            team = report.getAssignedTeamId();
        }
        boolean hasTeam = team != null && !team.trim().isEmpty() && !"none".equalsIgnoreCase(team.trim());
        if (hasTeam) {
            holder.assignedTeam.setText(holder.itemView.getContext().getString(
                    R.string.assigned_team_label) + ": " + team.trim());
            holder.assignedTeam.setVisibility(View.VISIBLE);
        } else {
            holder.assignedTeam.setVisibility(View.GONE);
        }

        String completedBy = report.getCompletedByPersonnelName();
        if (completedBy == null || completedBy.trim().isEmpty()) {
            completedBy = report.getCompletedByPersonnelId();
        }
        if (completedBy == null || completedBy.trim().isEmpty()) {
            completedBy = report.getAssignedPersonnelName();
        }
        if (completedBy == null || completedBy.trim().isEmpty()) {
            completedBy = report.getAssignedPersonnelId();
        }
        if (completedBy == null || completedBy.trim().isEmpty()) {
            List<String> ids = report.getAssignedPersonnelIds();
            if (ids != null && !ids.isEmpty()) {
                completedBy = TextUtils.join(", ", ids);
            }
        }
        boolean hasPersonnel = completedBy != null && !completedBy.trim().isEmpty()
                && !"none".equalsIgnoreCase(completedBy.trim());

        if (hasPersonnel && (!hasTeam || !report.requiresTeam())) {
            holder.assignedPersonnel.setText(holder.itemView.getContext().getString(
                    R.string.assigned_personnel) + ": " + completedBy.trim());
            holder.assignedPersonnel.setVisibility(View.VISIBLE);
        } else {
            holder.assignedPersonnel.setVisibility(View.GONE);
        }
        String finishedAt = report.getWorkCompletedAt();
        if (finishedAt == null || finishedAt.trim().isEmpty()) finishedAt = report.getResolvedAt();
        boolean hasFinishedAt = finishedAt != null && !finishedAt.trim().isEmpty();
        holder.adminResponse.setVisibility(hasFinishedAt ? View.VISIBLE : View.GONE);
        holder.adminResponse.setText("Completed On: "
                + (hasFinishedAt ? ReporterAccountUi.formatDate(finishedAt) : ""));
        holder.assignedAt.setVisibility(View.GONE);
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
        bindTimeline(holder, report);
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

    /**
     * Renders the task's cleaning events inside the card, oldest first.
     *
     * <p>The server orders them; this only paints them. A blank action is
     * skipped rather than shown as an empty row. When nothing is left to show,
     * the whole section (divider included) is hidden so an ordinary card keeps
     * its compact height.
     */
    private void bindTimeline(ReportViewHolder holder, Report report) {
        holder.timeline.removeAllViews();
        List<ReportTimelineEvent> events = showTimeline
                ? report.getTimeline()
                : new ArrayList<>();
        LayoutInflater inflater = LayoutInflater.from(holder.timeline.getContext());
        for (ReportTimelineEvent event : events) {
            if (event == null) continue;
            String label = ReportTimeline.labelFor(event.getAction());
            if (label == null) continue;
            View row = inflater.inflate(R.layout.item_report_timeline_event,
                    holder.timeline, false);
            ((TextView) row.findViewById(R.id.reporterTimelineLabel)).setText(label);
            TextView timestamp = row.findViewById(R.id.reporterTimelineTimestamp);
            String rawTimestamp = event.getTimestamp();
            if (rawTimestamp == null || rawTimestamp.trim().isEmpty()) {
                timestamp.setVisibility(View.GONE);
            } else {
                timestamp.setText(ReporterAccountUi.formatDate(rawTimestamp));
            }
            holder.timeline.addView(row);
        }

        boolean hasEvents = holder.timeline.getChildCount() > 0;
        holder.timelineDivider.setVisibility(hasEvents ? View.VISIBLE : View.GONE);
        holder.timelineTitle.setVisibility(hasEvents ? View.VISIBLE : View.GONE);
        holder.timeline.setVisibility(hasEvents ? View.VISIBLE : View.GONE);
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
        final TextView assignedTeam;
        final TextView adminResponse;
        final TextView assignedAt;
        final TextView lifecycle;
        final TextView dateTime;
        final TextView coordinates;
        final View timelineDivider;
        final TextView timelineTitle;
        final LinearLayout timeline;

        ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.reporterReportThumbnail);
            reportId = itemView.findViewById(R.id.reporterReportId);
            title = itemView.findViewById(R.id.reporterReportTitle);
            category = itemView.findViewById(R.id.reporterReportCategory);
            status = itemView.findViewById(R.id.reporterReportStatus);
            assignedPersonnel = itemView.findViewById(R.id.reporterReportAssignedPersonnel);
            assignedTeam = itemView.findViewById(R.id.reporterReportAssignedTeam);
            adminResponse = itemView.findViewById(R.id.reporterReportAdminResponse);
            assignedAt = itemView.findViewById(R.id.reporterReportAssignedAt);
            lifecycle = itemView.findViewById(R.id.reporterReportLifecycle);
            dateTime = itemView.findViewById(R.id.reporterReportDateTime);
            coordinates = itemView.findViewById(R.id.reporterReportCoordinates);
            timelineDivider = itemView.findViewById(R.id.reporterReportTimelineDivider);
            timelineTitle = itemView.findViewById(R.id.reporterReportTimelineTitle);
            timeline = itemView.findViewById(R.id.reporterReportTimeline);
        }
    }
}
