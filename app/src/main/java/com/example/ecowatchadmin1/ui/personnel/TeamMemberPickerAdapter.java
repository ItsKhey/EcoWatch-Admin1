package com.example.ecowatchadmin1.ui.personnel;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Personnel;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TeamMemberPickerAdapter extends RecyclerView.Adapter<TeamMemberPickerAdapter.ViewHolder> {

    public interface Listener {
        void onSelectionChanged();
    }

    private final List<Personnel> personnelList = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private String leaderId;
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setPersonnel(List<Personnel> items) {
        personnelList.clear();
        if (items != null) {
            for (Personnel p : items) {
                if (p != null && p.isEnabled()) {
                    personnelList.add(p);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void setSelection(List<String> memberIds, String leader) {
        selected.clear();
        if (memberIds != null) {
            for (String id : memberIds) {
                if (id != null && !id.trim().isEmpty()) {
                    selected.add(id.trim().toUpperCase(java.util.Locale.US));
                }
            }
        }
        leaderId = leader == null ? null : leader.trim().toUpperCase(java.util.Locale.US);
        if (leaderId != null && !leaderId.isEmpty()) {
            selected.add(leaderId);
        }
        notifyDataSetChanged();
    }

    public Set<String> getSelectedIds() {
        Set<String> result = new HashSet<>();
        for (String id : selected) {
            result.add(id);
        }
        return result;
    }

    public String getLeaderId() {
        return leaderId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_team_member_picker, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Personnel person = personnelList.get(position);
        String pid = person.getPersonnelId() == null ? "" : person.getPersonnelId().trim();
        String pidUpper = pid.toUpperCase(java.util.Locale.US);

        holder.name.setText(person.getFullName() == null || person.getFullName().trim().isEmpty()
                ? pid : person.getFullName().trim());
        holder.id.setText(pid.isEmpty() ? "" : holder.itemView.getContext()
                .getString(R.string.personnel_id_format, pid));

        boolean checked = selected.contains(pidUpper);
        holder.check.setOnCheckedChangeListener(null);
        holder.check.setChecked(checked);
        holder.check.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selected.add(pidUpper);
            } else {
                selected.remove(pidUpper);
                if (pidUpper.equals(leaderId)) {
                    leaderId = null;
                    notifyDataSetChanged();
                }
            }
            // The leader choice belongs to a crew member, so it only becomes
            // available once this row is ticked. Updating it here keeps that
            // true without waiting for the next full rebind.
            holder.leader.setEnabled(isChecked);
            if (listener != null) listener.onSelectionChanged();
        });

        boolean isLeader = pidUpper.equals(leaderId);
        holder.leader.setOnCheckedChangeListener(null);
        holder.leader.setChecked(isLeader);
        holder.leader.setEnabled(checked);
        holder.leader.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!selected.contains(pidUpper)) {
                    selected.add(pidUpper);
                    holder.check.setChecked(true);
                }
                leaderId = pidUpper;
                notifyDataSetChanged();
            } else if (isLeader) {
                leaderId = null;
                notifyDataSetChanged();
            }
            if (listener != null) listener.onSelectionChanged();
        });
        PersonnelUi.loadAvatar(holder.avatar, person.getProfileImageUrl());
    }

    @Override
    public int getItemCount() {
        return personnelList.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCheckBox check;
        final ShapeableImageView avatar;
        final TextView name;
        final TextView id;
        final RadioButton leader;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            check = itemView.findViewById(R.id.memberCheck);
            avatar = itemView.findViewById(R.id.memberAvatar);
            name = itemView.findViewById(R.id.memberName);
            id = itemView.findViewById(R.id.memberId);
            leader = itemView.findViewById(R.id.memberLeader);
        }
    }
}
