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

import java.util.ArrayList;
import java.util.List;

public class TeamAdapter extends RecyclerView.Adapter<TeamAdapter.TeamViewHolder> {

    public interface Listener {
        void onViewDetails(CleaningTeam team);
    }

    private final List<CleaningTeam> teams = new ArrayList<>();
    private final List<Personnel> personnelList = new ArrayList<>();
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setTeams(List<CleaningTeam> items) {
        teams.clear();
        if (items != null) {
            teams.addAll(items);
        }
        notifyDataSetChanged();
    }

    public void setPersonnel(List<Personnel> items) {
        personnelList.clear();
        if (items != null) {
            personnelList.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TeamViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_team, parent, false);
        return new TeamViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TeamViewHolder holder, int position) {
        CleaningTeam team = teams.get(position);

        holder.name.setText(team.getTeamName() != null && !team.getTeamName().trim().isEmpty()
                ? team.getTeamName().trim()
                : (team.getTeamId() == null ? "" : team.getTeamId()));

        String subtitle = team.getTeamId() == null ? "" : holder.itemView.getContext()
                .getString(R.string.team_crew_id, team.getTeamId());
        holder.subtitle.setText(subtitle);

        String label = team.getLabel();
        holder.label.setText(label == null ? "" : label.trim());
        holder.label.setVisibility(label == null || label.trim().isEmpty() ? View.GONE : View.VISIBLE);

        String leaderName = team.getResolvedLeaderName();
        if (leaderName != null && !leaderName.trim().isEmpty()) {
            holder.leader.setText(holder.itemView.getContext()
                    .getString(R.string.team_leader_label) + ": " + leaderName.trim());
        } else {
            holder.leader.setText(holder.itemView.getContext().getString(R.string.team_leader_label)
                    + ": " + holder.itemView.getContext().getString(R.string.team_no_leader));
        }

        int crewSize = team.getCrewSize();
        List<CleaningTeam.Member> members = team.getMembers();
        if (members.isEmpty()) {
            // The list endpoint omits the member list, so the description is the
            // only useful second line. The crew size is already on the status row.
            String description = team.getDescription();
            boolean hasDescription = description != null && !description.trim().isEmpty();
            holder.membersPreview.setText(hasDescription ? description.trim() : "");
            holder.membersPreview.setVisibility(hasDescription ? View.VISIBLE : View.GONE);
        } else {
            holder.membersPreview.setVisibility(View.VISIBLE);
            StringBuilder preview = new StringBuilder();
            int shown = 0;
            for (CleaningTeam.Member member : members) {
                if (member == null) continue;
                if (shown == 3) break;
                if (preview.length() > 0) preview.append(", ");
                preview.append(member.getDisplayName());
                shown++;
            }
            if (members.size() > shown) {
                preview.append(" +").append(members.size() - shown).append(" more");
            }
            holder.membersPreview.setText(preview);
        }

        boolean cleaning = isTeamCleaning(team);
        if (holder.cleaningStatus != null) {
            if (cleaning) {
                holder.cleaningStatus.setText(R.string.personnel_cleaning);
                holder.cleaningStatus.setTextColor(holder.itemView.getContext().getColor(R.color.personnel_cleaning));
            } else {
                holder.cleaningStatus.setText(R.string.team_status_idle);
                holder.cleaningStatus.setTextColor(holder.itemView.getContext().getColor(R.color.personnel_online));
            }
        }

        boolean ready = team.isDispatchReady();
        if (holder.availability != null) {
            if (cleaning) {
                holder.availability.setText(R.string.personnel_availability_busy);
                holder.availability.setTextColor(holder.itemView.getContext().getColor(R.color.personnel_cleaning));
            } else if (ready) {
                holder.availability.setText(R.string.personnel_availability_available);
                holder.availability.setTextColor(holder.itemView.getContext().getColor(R.color.personnel_online));
            } else {
                holder.availability.setText(R.string.personnel_availability_unavailable);
                holder.availability.setTextColor(holder.itemView.getContext().getColor(R.color.personnel_offline));
            }
        }

        holder.crewCount.setText(holder.itemView.getContext()
                .getString(R.string.team_crew_count, crewSize));
        holder.status.setText(ready ? R.string.team_status_ready : R.string.team_status_needs_attention);
        holder.status.setTextColor(holder.itemView.getContext().getColor(
                ready ? R.color.personnel_online : R.color.personnel_cleaning));

        holder.details.setOnClickListener(v -> {
            if (listener != null) listener.onViewDetails(team);
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onViewDetails(team);
        });
    }

    private boolean isTeamCleaning(CleaningTeam team) {
        if (team == null) return false;

        String teamName = team.getTeamName() != null ? team.getTeamName().trim() : "";
        String teamId = team.getTeamId() != null ? team.getTeamId().trim() : "";

        for (Personnel person : personnelList) {
            if (person == null || !person.isEnabled() || !person.isCleaning()) continue;

            String pTeamName = person.getTeamName() != null ? person.getTeamName().trim() : "";

            if (!teamName.isEmpty() && teamName.equalsIgnoreCase(pTeamName)) {
                return true;
            }
            if (!teamId.isEmpty() && teamId.equalsIgnoreCase(pTeamName)) {
                return true;
            }

            if (person.getPersonnelId() != null) {
                for (CleaningTeam.Member member : team.getMembers()) {
                    if (member != null && person.getPersonnelId().equalsIgnoreCase(member.getPersonnelId())) {
                        return true;
                    }
                }
            }
        }

        for (CleaningTeam.Member member : team.getMembers()) {
            if (member != null && member.getStatus() != null) {
                String s = member.getStatus().trim();
                if (s.equalsIgnoreCase("cleaning") || s.equalsIgnoreCase("busy")
                        || s.equalsIgnoreCase("in_progress") || s.equalsIgnoreCase("active")) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public int getItemCount() {
        return teams.size();
    }

    static final class TeamViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView subtitle;
        final TextView label;
        final TextView leader;
        final TextView membersPreview;
        final TextView cleaningStatus;
        final TextView availability;
        final TextView crewCount;
        final TextView status;
        final View details;

        TeamViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.teamName);
            subtitle = itemView.findViewById(R.id.teamSubtitle);
            label = itemView.findViewById(R.id.teamWasteScaleLabel);
            leader = itemView.findViewById(R.id.teamLeader);
            membersPreview = itemView.findViewById(R.id.teamMembersPreview);
            cleaningStatus = itemView.findViewById(R.id.teamCleaningStatus);
            availability = itemView.findViewById(R.id.teamAvailability);
            crewCount = itemView.findViewById(R.id.teamCrewCount);
            status = itemView.findViewById(R.id.teamStatus);
            details = itemView.findViewById(R.id.btnTeamDetails);
        }
    }
}
