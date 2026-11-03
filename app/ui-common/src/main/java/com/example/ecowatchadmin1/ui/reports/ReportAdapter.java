package com.example.ecowatchadmin1.ui.reports;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.remote.ServerConfig;
import com.example.ecowatchadmin1.data.repository.PossibleDuplicateGrouper;
import com.example.ecowatchadmin1.ui.residents.ReporterAccountUi;
import com.example.ecowatchadmin1.utils.PhotoCountBadge;
import com.example.ecowatchadmin1.utils.ReportSubmittedAtFormatter;
import com.example.ecowatchadmin1.utils.WasteScaleDisplay;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.imageview.ShapeableImageView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

public class ReportAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<Report> reports = new ArrayList<>();
    private OnReportClickListener listener;
    private boolean isSelectionMode = false;
    private Set<Integer> selectedIds = new HashSet<>();
    private String currentView = "Inbox"; // Inbox, Archive, Trash

    /** Groups rendered above the reports. */
    private List<PossibleDuplicateGroup> possibleDuplicateGroups = new ArrayList<>();
    private final Set<String> expandedGroupIds = new HashSet<>();
    private DuplicateGroupActionListener groupActionListener;

    public interface DuplicateGroupActionListener {
        void onPossibleDuplicatesDenied(PossibleDuplicateGroup group);
        void onPossibleDuplicatesAccepted(PossibleDuplicateGroup group);
        /** SEE ALL: opens the full report-by-report comparison for the group. */
        void onPossibleDuplicatesSeeAll(PossibleDuplicateGroup group);
    }

    private static final int TYPE_GROUP = 0;
    private static final int TYPE_REPORT = 1;

    public void setOnDuplicateGroupActionListener(DuplicateGroupActionListener listener) {
        this.groupActionListener = listener;
    }

    public void setPossibleDuplicateGroups(List<PossibleDuplicateGroup> groups) {
        this.possibleDuplicateGroups = groups == null ? new ArrayList<>() : groups;
        // Drop expansion state for groups that are gone, so a later group reusing
        // the identifier cannot inherit a previous group's expanded state.
        if (!expandedGroupIds.isEmpty()) {
            Set<String> live = new HashSet<>();
            for (PossibleDuplicateGroup group : this.possibleDuplicateGroups) {
                if (group != null && group.getGroupId() != null) {
                    live.add(group.getGroupId());
                }
            }
            expandedGroupIds.retainAll(live);
        }
        notifyDataSetChanged();
    }

    public interface OnReportClickListener {
        void onReportClick(Report report);
        void onLongReportClick(Report report);
        void onDeleteClick(Report report);
        void onEditClick(Report report);
        void onRestoreClick(Report report);
        void onSelectionChanged(int count);
    }

    public void setCurrentView(String view) {
        this.currentView = view;
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean selectionMode) {
        isSelectionMode = selectionMode;
        if (!selectionMode) selectedIds.clear();
        notifyDataSetChanged();
    }

    public void selectAll() {
        selectedIds.clear();
        for (Report r : reports) selectedIds.add(r.getId());
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(selectedIds.size());
    }

    public void unselectAll() {
        selectedIds.clear();
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(0);
    }

    public List<Integer> getSelectedIds() {
        return new ArrayList<>(selectedIds);
    }

    /**
     * Resolves the current selection back into report objects, preserving the
     * order the reports are displayed in.
     */
    public List<Report> getSelectedReports() {
        List<Report> selected = new ArrayList<>();
        for (Report report : reports) {
            if (report != null && selectedIds.contains(report.getId())) {
                selected.add(report);
            }
        }
        return selected;
    }

    public void setOnReportClickListener(OnReportClickListener listener) {
        this.listener = listener;
    }

    public void setReports(List<Report> reports) {
        this.reports = reports;
        notifyDataSetChanged();
    }

    public List<Report> getReports() {
        return reports;
    }

    private void bindGroup(GroupViewHolder holder, PossibleDuplicateGroup group) {
        List<Report> groupReports = group.getReports() == null
                ? new ArrayList<>() : group.getReports();
        Context context = holder.itemView.getContext();

        holder.subtitle.setText(context.getString(R.string.possible_duplicate_count,
                groupReports.size()));

        Double maxPairwise = groupReports.size() >= 2 ? maxSpreadMeters(groupReports) : null;
        String scaleLabel = WasteScaleDisplay.labelFor(context, group.getWasteScale());
        holder.context.setText(maxPairwise == null
                ? context.getString(R.string.possible_duplicate_explain_scale, scaleLabel)
                : context.getString(R.string.possible_duplicate_explain_nearby,
                        scaleLabel, Math.round(maxPairwise)));

        holder.chipGroup.removeAllViews();
        for (Report report : groupReports) {
            Chip chip = new Chip(context);
            String id = report.getReportId() != null ? report.getReportId() : report.getMongoId();
            chip.setText(context.getString(R.string.possible_duplicate_report_reference,
                    id != null ? id : "—"));
            chip.setCheckable(false);
            holder.chipGroup.addView(chip);
        }

        boolean expanded = expandedGroupIds.contains(group.getGroupId());
        holder.details.setVisibility(expanded ? View.VISIBLE : View.GONE);
        holder.details.removeAllViews();
        if (expanded) {
            Report first = groupReports.isEmpty() ? null : groupReports.get(0);
            for (Report report : groupReports) {
                View row = LayoutInflater.from(context)
                        .inflate(R.layout.item_duplicate_review_row, holder.details, false);
                bindReviewRow(row, report, first);
                holder.details.addView(row);
            }
        }

        // Only the header toggles. Binding the listener to the whole card meant
        // a tap near the buttons could both expand the group and register on the
        // row underneath, which made the card feel like it reacted once and then
        // stopped.
        holder.header.setOnClickListener(v -> toggleGroup(group));

        holder.seeAllBtn.setOnClickListener(v -> {
            if (groupActionListener != null) groupActionListener.onPossibleDuplicatesSeeAll(group);
        });
        boolean anyDeniable = false;
        for (Report report : groupReports) {
            if (PossibleDuplicateGrouper.canBeDenied(report)) {
                anyDeniable = true;
                break;
            }
        }
        final boolean canDeny = anyDeniable;
        holder.denyBtn.setEnabled(canDeny);
        holder.denyBtn.setAlpha(canDeny ? 1f : 0.5f);
        holder.denyBtn.setOnClickListener(v -> {
            if (groupActionListener != null && canDeny) {
                groupActionListener.onPossibleDuplicatesDenied(group);
            }
        });
        holder.acceptBtn.setOnClickListener(v -> {
            if (groupActionListener != null) groupActionListener.onPossibleDuplicatesAccepted(group);
        });
    }

    private void toggleGroup(PossibleDuplicateGroup group) {
        String id = group != null ? group.getGroupId() : null;
        if (id == null) return;
        if (!expandedGroupIds.remove(id)) {
            expandedGroupIds.add(id);
        }
        notifyDataSetChanged();
    }

    private void bindReviewRow(View row, Report report, Report first) {
        Context context = row.getContext();
        ((TextView) row.findViewById(R.id.reviewRowTitle))
                .setText(context.getString(R.string.possible_duplicate_report_reference,
                        DuplicateReviewFormatter.referenceOf(report)));
        ((TextView) row.findViewById(R.id.reviewRowResident))
                .setText(context.getString(R.string.possible_duplicate_resident_label,
                        report.getResidentLabel()));
        ((TextView) row.findViewById(R.id.reviewRowMeta))
                .setText(DuplicateReviewFormatter.submittedLine(context, report));
        ((TextView) row.findViewById(R.id.reviewRowDescription))
                .setText(report.getDescription() == null || report.getDescription().trim().isEmpty()
                        ? context.getString(R.string.possible_duplicate_no_description)
                        : report.getDescription());
        ((TextView) row.findViewById(R.id.reviewRowLocation))
                .setText(DuplicateReviewFormatter.locationLine(context, report, first));
        ((TextView) row.findViewById(R.id.reviewRowStatus))
                .setText("Status: " + DuplicateReviewFormatter.statusOf(report));

        ShapeableImageView thumb = row.findViewById(R.id.reviewRowThumb);
        Object imageSource = DuplicateReviewFormatter.photoSourceOf(report, context);
        if (imageSource != null) {
            Glide.with(context).load(imageSource).centerCrop().into(thumb);
        }
    }

    private static Double maxSpreadMeters(List<Report> reports) {
        return DuplicateReviewFormatter.maxSpreadMeters(reports);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_GROUP) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_possible_duplicate_group, parent, false);
            return new GroupViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_report, parent, false);
        return new ReportViewHolder(view);
    }

    @Override
    public int getItemViewType(int position) {
        return position < possibleDuplicateGroups.size() ? TYPE_GROUP : TYPE_REPORT;
    }

    @Override
    public int getItemCount() {
        return possibleDuplicateGroups.size() + reports.size();
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        if (viewHolder instanceof GroupViewHolder) {
            bindGroup((GroupViewHolder) viewHolder, possibleDuplicateGroups.get(position));
            return;
        }
        Report report = reports.get(position - possibleDuplicateGroups.size());
        ReportViewHolder holder = (ReportViewHolder) viewHolder;
        holder.title.setText(report.getTitle() != null ? report.getTitle() : "Untitled Report");
        
        String reportId = report.getReportId();
        if (reportId == null || reportId.isEmpty()) {
            reportId = report.getMongoId();
        }
        holder.reportId.setText("ID: " + (reportId != null ? reportId : "N/A"));

        String category = report.getWasteType() != null ? report.getWasteType() : "Unknown";
        String dateStr = "Just now";
        
        if (report.getCreatedAt() != null) {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = inputFormat.parse(report.getCreatedAt());

                if (date != null) {
                    // h:mm, not hh:mm. hh is the zero-padded 12-hour clock, which rendered
                    // 09:05 as "09:05 AM" while every other screen in the app
                    // showed "9:05 AM".
                    SimpleDateFormat displayFormat = new SimpleDateFormat("MMMM dd, yyyy • h:mm a", Locale.US);
                    displayFormat.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
                    dateStr = displayFormat.format(date);
                }
            } catch (Exception e) {
                dateStr = report.getCreatedAt();
            }
        }
        
        holder.typeDate.setText(category + " • " + dateStr);
        String statusText = report.getStatus();
        holder.status.setText("Status: " + (statusText != null ? statusText : "Pending"));
        if (holder.reportScale != null) {
            String scale = report.getWasteScale();
            if (scale == null || scale.trim().isEmpty()) {
                holder.reportScale.setText(com.example.ecowatchadmin1.data.models.Report.SCALE_SMALL);
            } else {
                holder.reportScale.setText(scale);
            }
        }

        // Show one scale-appropriate dispatch label: the cleaner for small
        // jobs, or the saved crew for medium and large jobs.
        boolean completed = "Completed".equalsIgnoreCase(statusText);
        boolean hasAssignment = report.hasAssignedPersonnel();
        boolean teamTask = report.requiresTeam();
        holder.assignedPersonnel.setVisibility(
                hasAssignment && !teamTask ? View.VISIBLE : View.GONE);
        holder.assignedTeam.setVisibility(
                hasAssignment && teamTask ? View.VISIBLE : View.GONE);
        holder.adminResponse.setVisibility(completed ? View.VISIBLE : View.GONE);
        holder.assignedAt.setVisibility(completed ? View.VISIBLE : View.GONE);

        String assigned = report.getAssignedPersonnelName();
        if (assigned == null || assigned.trim().isEmpty()) {
            List<String> ids = report.getAssignedPersonnelIds();
            assigned = ids == null || ids.isEmpty()
                    ? (report.getAssignedPersonnelId() == null ? "None" : report.getAssignedPersonnelId())
                    : TextUtils.join(", ", ids);
        }
        holder.assignedPersonnel.setText(holder.itemView.getContext().getString(
                R.string.assigned_personnel) + ": " + assigned);
        String team = report.getAssignedTeamLabel();
        holder.assignedTeam.setText(holder.itemView.getContext().getString(
                R.string.assigned_team_label) + ": "
                + (team == null || team.trim().isEmpty()
                ? holder.itemView.getContext().getString(R.string.none) : team));
        String response = report.getAdminResponse();
        holder.adminResponse.setText(holder.itemView.getContext().getString(
                R.string.admin_response_label) + ": "
                + (response == null || response.trim().isEmpty() ? "None" : response));
        // An em dash only when there is no dispatch time at all. An earlier
        // version also keyed off the single-personnel pointer, which a crew
        // leaves empty, so completed crew reports showed "—" here.
        String assignmentTime = report.getAssignedAt() == null
                || report.getAssignedAt().trim().isEmpty()
                ? "—" : ReporterAccountUi.formatDate(report.getAssignedAt());
        holder.assignedAt.setText(holder.itemView.getContext().getString(
                R.string.assignment_time_label) + ": " + assignmentTime);

        // Multi-select handling
        holder.checkbox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
        holder.checkbox.setChecked(selectedIds.contains(report.getId()));
        holder.checkbox.setOnClickListener(v -> {
            if (selectedIds.contains(report.getId())) {
                selectedIds.remove(report.getId());
            } else {
                selectedIds.add(report.getId());
            }
            if (listener != null) listener.onSelectionChanged(selectedIds.size());
        });

        // UI adjustments based on the tab's canonical and compatibility names.
        // ReportsFragment uses "Trashed"/"Archived", while older screens used
        // "Trash"/"Archive"; both must expose the same lifecycle controls.
        if ("Trash".equalsIgnoreCase(currentView)
                || "Trashed".equalsIgnoreCase(currentView)) {
            holder.editBtn.setVisibility(View.GONE);
            holder.restoreBtn.setVisibility(View.VISIBLE);
            holder.deleteBtn.setVisibility(View.VISIBLE); // Permanent delete
        } else if ("Archive".equalsIgnoreCase(currentView)
                || "Archived".equalsIgnoreCase(currentView)) {
            holder.restoreBtn.setVisibility(View.VISIBLE);
            holder.deleteBtn.setVisibility(View.VISIBLE);
            holder.editBtn.setVisibility(View.GONE);
        } else {
            holder.editBtn.setVisibility(View.GONE);
            holder.restoreBtn.setVisibility(View.GONE);
            holder.deleteBtn.setVisibility(View.VISIBLE);
        }

        String photoUrl = report.getPhotoUrl();
        Object imageSource = null;

        if (report.getLocalPhotoPath() != null) {
            File localFile = new File(report.getLocalPhotoPath());
            if (localFile.exists()) {
                imageSource = localFile;
            }
        }

        if (imageSource == null && photoUrl != null && !photoUrl.isEmpty()) {
            // Relative path, expanded against whichever server this build targets.
            imageSource = ServerConfig.absoluteUrl(holder.itemView.getContext(), photoUrl);
        }

        Glide.with(holder.itemView.getContext())
                .load(imageSource)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .centerCrop()
                .into(holder.thumb);

        // Multi-photo indicator. Bound unconditionally so a recycled row cannot
        // keep the badge when it is reused for a single-photo report.
        PhotoCountBadge.bind(holder.photoCountBadge, holder.photoCountText,
                report.getPhotoUrls());

        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                holder.checkbox.performClick();
            } else if (listener != null) {
                listener.onReportClick(report);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode && listener != null) {
                listener.onLongReportClick(report);
                return true;
            }
            return false;
        });

        holder.deleteBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(report);
            }
        });

        holder.editBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditClick(report);
            }
        });

        holder.restoreBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRestoreClick(report);
            }
        });
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        TextView title, subtitle, context;
        ChipGroup chipGroup;
        LinearLayout details;
        View header, seeAllBtn, denyBtn, acceptBtn;

        GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.duplicateReviewTitle);
            subtitle = itemView.findViewById(R.id.duplicateReviewSubtitle);
            context = itemView.findViewById(R.id.duplicateReviewContext);
            chipGroup = itemView.findViewById(R.id.duplicateReviewChipGroup);
            details = itemView.findViewById(R.id.duplicateReviewDetails);
            seeAllBtn = itemView.findViewById(R.id.duplicateReviewSeeAll);
            denyBtn = itemView.findViewById(R.id.duplicateReviewDeny);
            acceptBtn = itemView.findViewById(R.id.duplicateReviewAccept);
            // The header row carries the title, the count and SEE ALL, and is the
            // tap target that expands the comparison rows.
            header = itemView.findViewById(R.id.duplicateReviewHeader);
        }
    }

    static class ReportViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView thumb;
        TextView title, typeDate, status, reportId, reportScale;
        TextView assignedPersonnel, assignedTeam, adminResponse, assignedAt, photoCountText;
        View editBtn, deleteBtn, restoreBtn, photoCountBadge;
        CheckBox checkbox;

        public ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            thumb = itemView.findViewById(R.id.reportThumb);
            title = itemView.findViewById(R.id.reportTitle);
            reportId = itemView.findViewById(R.id.reportIdText);
            typeDate = itemView.findViewById(R.id.reportTypeDate);
            status = itemView.findViewById(R.id.reportStatus);
            reportScale = itemView.findViewById(R.id.reportScale);
            assignedPersonnel = itemView.findViewById(R.id.reportAssignedPersonnel);
            assignedTeam = itemView.findViewById(R.id.reportAssignedTeam);
            adminResponse = itemView.findViewById(R.id.reportAdminResponse);
            assignedAt = itemView.findViewById(R.id.reportAssignedAt);
            photoCountBadge = itemView.findViewById(R.id.photoCountBadge);
            photoCountText = itemView.findViewById(R.id.photoCountText);
            editBtn = itemView.findViewById(R.id.editReportBtn);
            deleteBtn = itemView.findViewById(R.id.deleteReportBtn);
            restoreBtn = itemView.findViewById(R.id.restoreReportBtn);
            checkbox = itemView.findViewById(R.id.reportCheckbox);
        }
    }
}
