package com.example.ecowatchadmin1.ui.reports;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Personnel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Search-result adapter for the admin assignment bottom sheet. */
public class AssignmentPersonnelAdapter
        extends RecyclerView.Adapter<AssignmentPersonnelAdapter.PersonnelViewHolder> {

    public interface Listener {
        void onPersonnelSelected(Personnel personnel);
    }

    private final List<Personnel> personnel = new ArrayList<>();
    private Listener listener;
    private final String reportId;
    /** Kept as a set so a multi-personnel pick stays possible; a single report cannot. */
    private final LinkedHashSet<String> selectedIds = new LinkedHashSet<>();
    private final boolean multiSelect;

    public AssignmentPersonnelAdapter(String reportId, Listener listener) {
        this(reportId, listener, false);
    }

    public AssignmentPersonnelAdapter(String reportId, Listener listener, boolean multiSelect) {
        this.reportId = reportId;
        this.listener = listener;
        this.multiSelect = multiSelect;
    }

    /**
     * Attaches the sheet's refresh callback after construction.
     *
     * <p>The sheet builds its list of people after the adapter exists, so the
     * callback can only be handed over once everything it reads is ready.
     */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Personnel> people) {
        personnel.clear();
        if (people != null) personnel.addAll(people);
        notifyDataSetChanged();
    }

    public void setSelectedId(String personnelId) {
        selectedIds.clear();
        if (personnelId != null && !personnelId.trim().isEmpty()) {
            selectedIds.add(personnelId.trim().toUpperCase(Locale.getDefault()));
        }
        notifyDataSetChanged();
    }

    /**
     * Pre-ticks a whole crew.
     *
     * <p>Used when a cleaning team is chosen: the people who go out have to be
     * members of that team, so the crew is ticked rather than left for the
     * administrator to pick one name at a time.
     */
    public void setSelectedIds(List<String> personnelIds) {
        selectedIds.clear();
        if (personnelIds != null) {
            for (String personnelId : personnelIds) {
                if (personnelId == null) continue;
                String trimmed = personnelId.trim();
                if (!trimmed.isEmpty()) {
                    selectedIds.add(trimmed.toUpperCase(Locale.getDefault()));
                }
            }
        }
        notifyDataSetChanged();
    }

    /** The one selected person, for the single-assignment path. */
    public String getSelectedId() {
        return selectedIds.isEmpty() ? null : selectedIds.iterator().next();
    }

    /** Everyone currently ticked, in the order they were picked. */
    public List<String> getSelectedIds() {
        return new ArrayList<>(selectedIds);
    }

    public boolean isMultiSelect() {
        return multiSelect;
    }

    private boolean isSelected(String personnelId) {
        return personnelId != null
                && selectedIds.contains(personnelId.trim().toUpperCase(Locale.getDefault()));
    }

    @NonNull
    @Override
    public PersonnelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_assignment_personnel, parent, false);
        return new PersonnelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PersonnelViewHolder holder, int position) {
        Personnel person = personnel.get(position);
        if (person == null) return;
        boolean selected = isSelected(person.getPersonnelId());

        holder.name.setText(person.getFullName());
        String status = !person.isEnabled()
                ? holder.itemView.getContext().getString(R.string.personnel_status_disabled)
                : person.isCleaning()
                ? holder.itemView.getContext().getString(R.string.personnel_cleaning)
                : person.isOnline()
                ? holder.itemView.getContext().getString(R.string.personnel_online)
                : holder.itemView.getContext().getString(R.string.personnel_filter_offline);
        holder.meta.setText(String.format(Locale.getDefault(), "%s · %s",
                safe(person.getPersonnelId()), status));
        String task = person.getCurrentTask();
        holder.task.setText(holder.itemView.getContext().getString(
                R.string.personnel_task_format,
                task == null || task.trim().isEmpty()
                        ? holder.itemView.getContext().getString(R.string.no_current_task)
                        : task));

        boolean unavailable = !person.isEnabled();
        boolean busy = person.isCleaning() || hasCurrentAssignment(person);
        boolean assignedToAnotherReport = hasCurrentAssignment(person)
                && reportId != null
                && !reportId.equalsIgnoreCase(person.getAssignmentReportId());
        boolean sameReport = hasCurrentAssignment(person)
                && reportId != null
                && reportId.equalsIgnoreCase(person.getAssignmentReportId());
        boolean assignable = !unavailable && (!busy || sameReport)
                && !assignedToAnotherReport;
        if (assignedToAnotherReport) busy = true;
        int background;
        int foreground;
        String availability;
        if (unavailable) {
            background = R.color.personnel_disabled_background;
            foreground = R.color.personnel_disabled;
            availability = holder.itemView.getContext().getString(R.string.personnel_availability_unavailable);
        } else if (busy) {
            background = R.color.personnel_cleaning_background;
            foreground = R.color.personnel_cleaning;
            availability = holder.itemView.getContext().getString(R.string.personnel_availability_busy);
        } else if (person.isOnline()) {
            background = R.color.personnel_online_background;
            foreground = R.color.personnel_online;
            availability = holder.itemView.getContext().getString(R.string.personnel_availability_available);
        } else {
            background = R.color.personnel_offline_background;
            foreground = R.color.personnel_offline;
            availability = holder.itemView.getContext().getString(R.string.personnel_availability_offline);
        }
        GradientDrawable badge = new GradientDrawable();
        badge.setColor(holder.itemView.getContext().getColor(background));
        badge.setCornerRadius(18 * holder.itemView.getResources().getDisplayMetrics().density);
        holder.availability.setBackground(badge);
        holder.availability.setTextColor(holder.itemView.getContext().getColor(foreground));
        holder.availability.setText(availability);

        String avatar = person.getProfileImageUrl();
        if (avatar == null || avatar.trim().isEmpty()) {
            holder.avatar.setImageResource(R.drawable.ic_person_default);
        } else {
            Glide.with(holder.avatar)
                    .load(com.example.ecowatchadmin1.ui.residents.ReporterAccountUi
                            .resolveImageUrl(holder.itemView.getContext(), avatar))
                    .placeholder(R.drawable.ic_person_default)
                    .error(R.drawable.ic_person_default)
                    .centerCrop()
                    .into(holder.avatar);
        }

        holder.select.setText(selected
                ? R.string.selected : R.string.select);
        holder.select.setEnabled(assignable);
        holder.select.setAlpha(assignable ? 1f : 0.45f);
        holder.card.setStrokeWidth(selected ? 2 : 1);
        holder.card.setStrokeColor(holder.itemView.getContext().getColor(
                selected ? R.color.green : R.color.personnel_divider));

        View.OnClickListener click = v -> {
            if (!assignable) return;
            String personnelId = person.getPersonnelId();
            if (personnelId == null) return;

            String key = personnelId.trim().toUpperCase(Locale.getDefault());
            if (multiSelect) {
                // Tapping a picked person puts them back, so a mis-tap is
                // reversible instead of forcing a fresh selection.
                if (!selectedIds.remove(key)) selectedIds.add(key);
            } else {
                selectedIds.clear();
                selectedIds.add(key);
            }

            notifyDataSetChanged();
            if (listener != null) listener.onPersonnelSelected(person);
        };
        holder.card.setOnClickListener(click);
        holder.select.setOnClickListener(click);
    }

    private boolean hasCurrentAssignment(Personnel person) {
        return (person.getAssignmentReportId() != null
                && !person.getAssignmentReportId().trim().isEmpty())
                || (person.getCurrentAssignment() != null
                && !person.getCurrentAssignment().trim().isEmpty())
                || (person.getCurrentTask() != null
                && !person.getCurrentTask().trim().isEmpty());
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value;
    }

    @Override
    public int getItemCount() {
        return personnel.size();
    }

    static final class PersonnelViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ShapeableImageView avatar;
        final TextView name;
        final TextView meta;
        final TextView task;
        final TextView availability;
        final MaterialButton select;

        PersonnelViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.assignmentPersonnelCard);
            avatar = itemView.findViewById(R.id.assignmentPersonnelAvatar);
            name = itemView.findViewById(R.id.assignmentPersonnelName);
            meta = itemView.findViewById(R.id.assignmentPersonnelMeta);
            task = itemView.findViewById(R.id.assignmentPersonnelTask);
            availability = itemView.findViewById(R.id.assignmentPersonnelAvailability);
            select = itemView.findViewById(R.id.assignmentPersonnelSelect);
        }
    }
}
