package com.example.ecowatchadmin1.ui.personnel;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.CleaningTeam;
import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.data.repository.CleaningTeamRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class TeamDialogs {

    private TeamDialogs() {
    }

    interface Callback {
        void onChanged();
    }

    static void showEditor(@NonNull Context context, @Nullable CleaningTeam team,
                           @NonNull TeamViewModel viewModel,
                           @NonNull List<Personnel> personnelList,
                           @NonNull Callback callback) {
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_team_edit, null, false);
        TextView title = content.findViewById(R.id.teamDialogTitle);
        TextView subtitle = content.findViewById(R.id.teamDialogSubtitle);
        TextInputLayout nameLayout = content.findViewById(R.id.teamNameLayout);
        TextInputEditText nameInput = content.findViewById(R.id.teamNameInput);
        TextInputEditText descriptionInput = content.findViewById(R.id.teamDescriptionInput);
        TextInputEditText labelInput = content.findViewById(R.id.teamLabelInput);
        TextView leaderError = content.findViewById(R.id.teamLeaderError);
        TextView membersError = content.findViewById(R.id.teamMembersError);
        RecyclerView picker = content.findViewById(R.id.teamMemberPicker);
        MaterialButton saveButton = content.findViewById(R.id.teamSaveBtn);
        MaterialButton cancelButton = content.findViewById(R.id.teamCancelBtn);

        boolean editing = team != null;
        title.setText(editing ? R.string.team_edit_title : R.string.team_create_title);
        subtitle.setText(editing ? R.string.team_edit_subtitle : R.string.team_create_subtitle);

        if (editing) {
            nameInput.setText(team.getTeamName());
            descriptionInput.setText(team.getDescription());
            labelInput.setText(team.getLabel());
        }

        TeamMemberPickerAdapter adapter = new TeamMemberPickerAdapter();
        adapter.setPersonnel(personnelList);
        picker.setLayoutManager(new LinearLayoutManager(context));
        picker.setAdapter(adapter);

        if (editing) {
            List<String> memberIds = new ArrayList<>();
            for (CleaningTeam.Member m : team.getMembers()) {
                if (m != null && m.getPersonnelId() != null) {
                    memberIds.add(m.getPersonnelId());
                }
            }
            adapter.setSelection(memberIds, team.getLeaderId());
        }

        adapter.setListener(() -> {
            leaderError.setVisibility(View.GONE);
            membersError.setVisibility(View.GONE);
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(content)
                .create();

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        saveButton.setOnClickListener(v -> {
            leaderError.setVisibility(View.GONE);
            membersError.setVisibility(View.GONE);
            String teamName = textOf(nameInput);
            if (teamName.isEmpty()) {
                nameLayout.setError(context.getString(R.string.team_name_required));
                return;
            }
            nameLayout.setError(null);

            String leaderId = adapter.getLeaderId();
            if (leaderId == null || leaderId.trim().isEmpty()) {
                leaderError.setText(R.string.team_leader_required);
                leaderError.setVisibility(View.VISIBLE);
                return;
            }

            Set<String> selected = adapter.getSelectedIds();
            List<String> memberIdsList = new ArrayList<>(selected);
            if (memberIdsList.size() < 2) {
                membersError.setText(R.string.team_members_required);
                membersError.setVisibility(View.VISIBLE);
                return;
            }

            String description = textOf(descriptionInput);
            if (description.isEmpty()) description = null;
            String label = textOf(labelInput);
            if (label.isEmpty()) label = null;

            CleaningTeamRepository.TeamCallback cb = new CleaningTeamRepository.TeamCallback() {
                @Override
                public void onSuccess(CleaningTeam t) {
                    dialog.dismiss();
                    callback.onChanged();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                }
            };

            if (editing && team.getTeamId() != null) {
                viewModel.updateTeam(team.getTeamId(), teamName, description, label, leaderId, memberIdsList, cb);
            } else {
                viewModel.createTeam(teamName, description, label, leaderId, memberIdsList, cb);
            }
        });

        dialog.show();
    }

    static void showAddMember(@NonNull Context context, @NonNull CleaningTeam team,
                              @NonNull TeamViewModel viewModel,
                              @NonNull List<Personnel> personnelList,
                              @NonNull Callback callback) {
        List<Personnel> candidates = new ArrayList<>();
        Set<String> existing = new HashSet<>();
        for (CleaningTeam.Member m : team.getMembers()) {
            if (m != null && m.getPersonnelId() != null) {
                existing.add(m.getPersonnelId().trim().toUpperCase(java.util.Locale.US));
            }
        }
        for (Personnel p : personnelList) {
            if (p == null || !p.isEnabled()) continue;
            String pid = p.getPersonnelId() == null ? "" : p.getPersonnelId().trim().toUpperCase(java.util.Locale.US);
            if (!existing.contains(pid)) {
                candidates.add(p);
            }
        }

        String[] names = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            Personnel p = candidates.get(i);
            names[i] = (p.getFullName() == null || p.getFullName().trim().isEmpty())
                    ? (p.getPersonnelId() == null ? "" : p.getPersonnelId())
                    : p.getFullName().trim() + " (" + p.getPersonnelId() + ")";
        }

        if (candidates.isEmpty()) {
            // An item dialog with no items looks like a broken control; say why
            // there is nobody left to add instead.
            Toast.makeText(context, R.string.team_no_available_members, Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.team_details_add_member)
                .setItems(names, (d, which) -> {
                    Personnel selected = candidates.get(which);
                    if (selected == null || selected.getPersonnelId() == null) return;
                    viewModel.addMember(team.getTeamId(), selected.getPersonnelId(),
                            new CleaningTeamRepository.TeamCallback() {
                                @Override
                                public void onSuccess(CleaningTeam t) {
                                    Toast.makeText(context, R.string.team_member_added, Toast.LENGTH_SHORT).show();
                                    callback.onChanged();
                                }

                                @Override
                                public void onError(String message) {
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static String textOf(TextInputEditText input) {
        CharSequence value = input.getText();
        return value == null ? "" : value.toString().trim();
    }
}
