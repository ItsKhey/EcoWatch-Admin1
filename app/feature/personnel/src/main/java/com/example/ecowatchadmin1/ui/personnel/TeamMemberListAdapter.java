package com.example.ecowatchadmin1.ui.personnel;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.CleaningTeam;
import com.example.ecowatchadmin1.data.models.Personnel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TeamMemberListAdapter extends RecyclerView.Adapter<TeamMemberListAdapter.ViewHolder> {

    public interface Listener {
        void onRemove(CleaningTeam.Member member);
    }

    private final List<CleaningTeam.Member> members = new ArrayList<>();
    private final Map<String, String> profileImageUrls = new HashMap<>();
    private String leaderId;
    private Listener listener;
    private boolean allowRemove = true;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setAllowRemove(boolean allowRemove) {
        this.allowRemove = allowRemove;
        notifyDataSetChanged();
    }

    public void setMembers(List<CleaningTeam.Member> items, String leader) {
        members.clear();
        if (items != null) {
            members.addAll(items);
        }
        leaderId = leader == null ? null : leader.trim().toUpperCase(java.util.Locale.US);
        notifyDataSetChanged();
    }

    public void setPersonnel(List<Personnel> personnelList) {
        profileImageUrls.clear();
        if (personnelList != null) {
            for (Personnel p : personnelList) {
                if (p == null) continue;
                String pid = p.getPersonnelId();
                if (pid != null && p.getProfileImageUrl() != null) {
                    profileImageUrls.put(pid.trim().toUpperCase(Locale.US), p.getProfileImageUrl());
                }
            }
        }
        if (!members.isEmpty()) notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_team_member, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CleaningTeam.Member member = members.get(position);
        String pid = member.getPersonnelId() == null ? "" : member.getPersonnelId().trim();
        String pidUpper = pid.toUpperCase(java.util.Locale.US);
        boolean isLeader = member.isLeader() || (leaderId != null && !leaderId.isEmpty() && leaderId.equals(pidUpper));

        holder.name.setText(member.getFullName() == null || member.getFullName().trim().isEmpty()
                ? pid : member.getFullName().trim());
        holder.id.setText(pid.isEmpty() ? "" : holder.itemView.getContext()
                .getString(R.string.personnel_id_format, pid));
        holder.leaderBadge.setVisibility(isLeader ? View.VISIBLE : View.GONE);
        holder.remove.setVisibility(allowRemove && !isLeader ? View.VISIBLE : (allowRemove ? View.INVISIBLE : View.GONE));
        holder.remove.setOnClickListener(v -> {
            if (listener != null) listener.onRemove(member);
        });
        String img = profileImageUrls.get(pidUpper);
        if (img == null || img.trim().isEmpty()) {
            holder.avatar.setImageResource(R.drawable.ic_person_default);
        } else {
            com.bumptech.glide.Glide.with(holder.avatar)
                    .load(com.example.ecowatchadmin1.ui.residents.ReporterAccountUi
                            .resolveImageUrl(holder.itemView.getContext(), img))
                    .placeholder(R.drawable.ic_person_default)
                    .error(R.drawable.ic_person_default)
                    .centerCrop()
                    .into(holder.avatar);
        }
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final ShapeableImageView avatar;
        final TextView name;
        final TextView id;
        final TextView leaderBadge;
        final MaterialButton remove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.memberAvatar);
            name = itemView.findViewById(R.id.memberName);
            id = itemView.findViewById(R.id.memberId);
            leaderBadge = itemView.findViewById(R.id.memberLeaderBadge);
            remove = itemView.findViewById(R.id.memberRemove);
        }
    }
}
