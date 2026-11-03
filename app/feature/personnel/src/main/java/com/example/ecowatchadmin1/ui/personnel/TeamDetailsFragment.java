package com.example.ecowatchadmin1.ui.personnel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.CleaningTeam;
import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.models.ReportPage;
import com.example.ecowatchadmin1.data.repository.CleaningTeamRepository;
import com.example.ecowatchadmin1.data.repository.PersonnelRepository;
import com.example.ecowatchadmin1.ui.reports.ReportDetailDialog;
import com.example.ecowatchadmin1.ui.residents.ReporterReportAdapter;
import com.example.ecowatchadmin1.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Full-screen team details, opened from the Teams list. */
public final class TeamDetailsFragment extends Fragment {
    private static final String ARG_TEAM_ID = "team_id";

    private TeamViewModel viewModel;
    private CleaningTeam team;
    private final List<Personnel> personnel = new ArrayList<>();
    private TeamMemberListAdapter memberAdapter;
    private View loading;
    private TextView error;
    private TextView leader;
    private TextView leaderId;
    private TextView description;
    private TextView crewCount;
    private TextView status;
    private TextView teamCleaningStatus;
    private TextView totalAssignedTasks;
    private TextView tasksOnHand;
    private TextView tasksInProgress;
    private TextView tasksCompleted;
    private TextView noMembers;
    private RecyclerView members;
    private RecyclerView historyRecyclerView;
    private TextView historyEmpty;
    private MaterialButton addMember;
    private MaterialButton edit;
    private MaterialButton delete;
    private MaterialButton historyLoadMore;
    private ReporterReportAdapter historyAdapter;
    private final List<Report> teamHistory = new ArrayList<>();
    private int teamHistoryTotal;
    private int historyPage;
    private boolean historyHasMore;
    private boolean historyLoading;

    public static TeamDetailsFragment newInstance(String teamId) {
        TeamDetailsFragment fragment = new TeamDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TEAM_ID, teamId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.screen_team_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.teamDetailsBack).setOnClickListener(v ->
                getParentFragmentManager().popBackStack());

        FrameLayout content = view.findViewById(R.id.teamDetailsContent);
        View details = LayoutInflater.from(requireContext())
                .inflate(R.layout.fragment_team_details, content, false);
        content.addView(details, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        details.findViewById(R.id.teamDetailClose).setVisibility(View.GONE);

        loading = new android.widget.ProgressBar(requireContext());
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER);
        content.addView(loading, progressParams);
        error = new TextView(requireContext());
        error.setTextColor(requireContext().getColor(R.color.personnel_offline));
        error.setGravity(android.view.Gravity.CENTER);
        error.setVisibility(View.GONE);
        content.addView(error, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = details.findViewById(R.id.teamDetailTitle);
        leader = details.findViewById(R.id.teamDetailLeader);
        leaderId = details.findViewById(R.id.teamDetailLeaderId);
        description = details.findViewById(R.id.teamDetailDescription);
        crewCount = details.findViewById(R.id.teamDetailCrewCount);
        status = details.findViewById(R.id.teamDetailStatus);
        teamCleaningStatus = details.findViewById(R.id.teamCleaningStatus);
        totalAssignedTasks = details.findViewById(R.id.totalAssignedReport);
        tasksOnHand = details.findViewById(R.id.reporterStatOnhand);
        tasksInProgress = details.findViewById(R.id.reporterStatInProgress);
        tasksCompleted = details.findViewById(R.id.reporterStatCompleted);
        members = details.findViewById(R.id.teamDetailMembers);
        historyRecyclerView = details.findViewById(R.id.teamHistoryRecyclerView);
        historyEmpty = details.findViewById(R.id.teamHistoryEmpty);
        historyLoadMore = details.findViewById(R.id.teamHistoryLoadMore);
        noMembers = details.findViewById(R.id.teamDetailNoMembers);
        addMember = details.findViewById(R.id.teamDetailAddMember);
        edit = details.findViewById(R.id.teamDetailEdit);
        delete = details.findViewById(R.id.teamDetailDelete);

        memberAdapter = new TeamMemberListAdapter();
        members.setLayoutManager(new LinearLayoutManager(requireContext()));
        members.setAdapter(memberAdapter);
        members.setNestedScrollingEnabled(false);
        memberAdapter.setListener(this::confirmRemoveMember);
        historyAdapter = new ReporterReportAdapter();
        // The team's work log gets the same per-task timeline as the personnel
        // history; the shared adapter's compact mode remains the default.
        historyAdapter.setShowTimeline(true);
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        historyRecyclerView.setAdapter(historyAdapter);
        historyRecyclerView.setNestedScrollingEnabled(false);
        historyAdapter.setListener(report -> ReportDetailDialog.showReadOnly(this, report));
        historyLoadMore.setOnClickListener(v -> loadTeamTaskHistory(true));

        viewModel = new ViewModelProvider(this).get(TeamViewModel.class);
        String teamId = getArguments() == null ? null : getArguments().getString(ARG_TEAM_ID);
        if (!new SessionManager(requireContext()).isAdmin() || isBlank(teamId)) {
            showError(getString(R.string.personnel_error_admin_only));
            return;
        }
        addMember.setOnClickListener(v -> openAddMember());
        edit.setOnClickListener(v -> {
            if (team != null) TeamDialogs.showEditor(requireContext(), team, viewModel,
                    personnel, this::loadTeam);
        });
        delete.setOnClickListener(v -> confirmDelete());

        new PersonnelRepository(requireActivity().getApplication())
                .getPersonnel(new PersonnelRepository.PersonnelListCallback() {
                    @Override public void onSuccess(List<Personnel> result) {
                        if (!isAdded()) return;
                        personnel.clear();
                        if (result != null) personnel.addAll(result);
                        if (memberAdapter != null) memberAdapter.setPersonnel(personnel);
                        updateCleaningStatus();
                    }

                    @Override public void onError(String message) {
                        // Team data remains useful if avatars cannot be fetched.
                    }
                });
        loadTeam();
        loadTeamTaskHistory(false);
    }

    private void loadTeamTaskHistory(boolean append) {
        if (!isAdded() || viewModel == null || historyLoading
                || (append && !historyHasMore)) return;
        String teamId = getArguments() == null ? null : getArguments().getString(ARG_TEAM_ID);
        if (isBlank(teamId)) return;

        historyLoading = true;
        historyLoadMore.setEnabled(false);
        int requestedPage = append ? historyPage + 1 : 1;
        viewModel.loadTeamTaskHistory(teamId, requestedPage, 30,
                new CleaningTeamRepository.TeamTaskHistoryCallback() {
                    @Override
                    public void onSuccess(ReportPage result) {
                        if (!isAdded()) return;
                        ReportPage page = result == null ? new ReportPage() : result;
                        if (!append) teamHistory.clear();
                        if (page.getReports() != null) {
                            for (Report report : page.getReports()) {
                                if (report == null) continue;
                                if (isBlank(report.getAssignedTeamName()) && team != null) {
                                    report.setAssignedTeamName(team.getTeamName());
                                }
                                if (isBlank(report.getAssignedTeamId())) {
                                    report.setAssignedTeamId(teamId);
                                }
                                teamHistory.add(report);
                            }
                        }
                        teamHistoryTotal = Math.max(page.getTotal(), teamHistory.size());
                        historyPage = Math.max(1, page.getPage());
                        historyHasMore = page.hasMore();
                        historyLoading = false;
                        historyAdapter.setReports(teamHistory);
                        historyEmpty.setVisibility(teamHistory.isEmpty() ? View.VISIBLE : View.GONE);
                        historyLoadMore.setVisibility(historyHasMore ? View.VISIBLE : View.GONE);
                        historyLoadMore.setEnabled(true);
                        updateTeamMonitoringStats();
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        historyLoading = false;
                        historyLoadMore.setEnabled(true);
                        historyLoadMore.setVisibility(historyHasMore ? View.VISIBLE : View.GONE);
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void updateTeamMonitoringStats() {
        if (totalAssignedTasks == null || tasksOnHand == null
                || tasksInProgress == null || tasksCompleted == null) {
            return;
        }

        int onHand = 0;
        int inProgress = 0;
        int completed = 0;

        for (Report r : teamHistory) {
            if (r == null) continue;
            String statusStr = r.getStatus() != null ? r.getStatus().trim().toLowerCase(Locale.ROOT) : "";
            if (statusStr.contains("complete") || statusStr.contains("resolved")
                    || statusStr.contains("approved") || statusStr.contains("done")) {
                completed++;
            } else if (statusStr.contains("progress") || statusStr.contains("cleaning")
                    || statusStr.contains("dispatched") || statusStr.contains("accepted")
                    || statusStr.contains("active")) {
                inProgress++;
            } else {
                onHand++;
            }
        }

        int total = Math.max(teamHistoryTotal, onHand + inProgress + completed);

        totalAssignedTasks.setText(String.valueOf(total));
        tasksOnHand.setText(String.valueOf(onHand));
        tasksInProgress.setText(String.valueOf(inProgress));
        tasksCompleted.setText(String.valueOf(completed));
        updateCleaningStatus();
    }

    private void loadTeam() {
        if (!isAdded() || viewModel == null) return;
        loading.setVisibility(View.VISIBLE);
        error.setVisibility(View.GONE);
        String id = getArguments() == null ? null : getArguments().getString(ARG_TEAM_ID);
        viewModel.loadTeam(id, new CleaningTeamRepository.TeamCallback() {
            @Override public void onSuccess(CleaningTeam result) {
                if (!isAdded()) return;
                loading.setVisibility(View.GONE);
                team = result;
                renderTeam();
            }

            @Override public void onError(String message) {
                if (!isAdded()) return;
                loading.setVisibility(View.GONE);
                showError(message);
            }
        });
    }

    private void renderTeam() {
        if (team == null) return;
        for (Report report : teamHistory) {
            if (report != null && isBlank(report.getAssignedTeamName())) {
                report.setAssignedTeamName(team.getTeamName());
            }
        }
        if (historyAdapter != null) historyAdapter.setReports(teamHistory);
        View content = getView().findViewById(R.id.teamDetailsContent);
        TextView title = content.findViewById(R.id.teamDetailTitle);
        TextView subtitle = content.findViewById(R.id.teamDetailSubtitle);
        title.setText(isBlank(team.getTeamName()) ? team.getTeamId() : team.getTeamName());
        subtitle.setText(isBlank(team.getTeamId()) ? ""
                : getString(R.string.team_crew_id, team.getTeamId()));

        String leaderName = team.getResolvedLeaderName();
        boolean hasLeader = !isBlank(leaderName);
        leader.setText(hasLeader ? leaderName.trim() : getString(R.string.team_no_leader));
        leaderId.setText(hasLeader && !isBlank(team.getLeaderId())
                ? getString(R.string.personnel_id_format, team.getLeaderId()) : "");
        leaderId.setVisibility(hasLeader && !isBlank(team.getLeaderId())
                ? View.VISIBLE : View.GONE);
        if (isBlank(team.getDescription())) {
            description.setVisibility(View.GONE);
        } else {
            description.setText(team.getDescription().trim());
            description.setVisibility(View.VISIBLE);
        }
        crewCount.setText(getString(R.string.team_crew_count, team.getCrewSize()));
        boolean ready = team.isDispatchReady();
        status.setText(ready ? R.string.team_status_ready : R.string.team_status_needs_attention);
        status.setTextColor(requireContext().getColor(ready
                ? R.color.personnel_online : R.color.personnel_cleaning));
        updateCleaningStatus();
        memberAdapter.setMembers(team.getMembers(), team.getLeaderId());
        memberAdapter.setPersonnel(personnel);
        noMembers.setVisibility(team.getMembers().isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void updateCleaningStatus() {
        if (teamCleaningStatus == null || team == null || !isAdded()) return;
        boolean cleaning = isTeamCleaning(team);
        if (cleaning) {
            teamCleaningStatus.setText(R.string.personnel_cleaning);
            teamCleaningStatus.setTextColor(requireContext().getColor(R.color.personnel_cleaning));
        } else {
            teamCleaningStatus.setText(R.string.team_status_idle);
            teamCleaningStatus.setTextColor(requireContext().getColor(R.color.personnel_online));
        }
    }

    private boolean isTeamCleaning(CleaningTeam team) {
        if (team == null) return false;

        String teamName = team.getTeamName() != null ? team.getTeamName().trim() : "";
        String teamId = team.getTeamId() != null ? team.getTeamId().trim() : "";

        for (Personnel person : personnel) {
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

        for (Report r : teamHistory) {
            if (r == null) continue;
            String statusStr = r.getStatus() != null ? r.getStatus().trim().toLowerCase(Locale.ROOT) : "";
            if (statusStr.contains("progress") || statusStr.contains("cleaning")
                    || statusStr.contains("dispatched") || statusStr.contains("accepted")
                    || statusStr.contains("active")) {
                return true;
            }
        }

        return false;
    }

    private void openAddMember() {
        if (team == null) return;
        TeamDialogs.showAddMember(requireContext(), team, viewModel, personnel, this::loadTeam);
    }

    private void confirmRemoveMember(CleaningTeam.Member member) {
        if (team == null || member == null || isBlank(member.getPersonnelId())) return;
        String name = isBlank(member.getFullName())
                ? member.getPersonnelId() : member.getFullName();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.team_details_remove_member)
                .setMessage(getString(R.string.team_details_remove_member_confirm, name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.team_details_remove_member, (dialog, which) ->
                        viewModel.removeMember(team.getTeamId(), member.getPersonnelId(),
                                new CleaningTeamRepository.TeamCallback() {
                                    @Override public void onSuccess(CleaningTeam updated) {
                                        if (!isAdded()) return;
                                        Toast.makeText(requireContext(), R.string.team_member_removed,
                                                Toast.LENGTH_SHORT).show();
                                        loadTeam();
                                    }

                                    @Override public void onError(String message) {
                                        if (isAdded()) Toast.makeText(requireContext(), message,
                                                Toast.LENGTH_LONG).show();
                                    }
                                }))
                .show();
    }

    private void confirmDelete() {
        if (team == null) return;
        String name = isBlank(team.getTeamName()) ? team.getTeamId() : team.getTeamName();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.team_delete_confirm_title)
                .setMessage(getString(R.string.team_delete_confirm_message, name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.team_delete, (dialog, which) ->
                        viewModel.deleteTeam(team.getTeamId(), new CleaningTeamRepository.TeamCallback() {
                            @Override public void onSuccess(CleaningTeam ignored) {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), R.string.team_deleted,
                                        Toast.LENGTH_SHORT).show();
                                getParentFragmentManager().popBackStack();
                            }

                            @Override public void onError(String message) {
                                if (isAdded()) Toast.makeText(requireContext(), message,
                                        Toast.LENGTH_LONG).show();
                            }
                        }))
                .show();
    }

    private void showError(String message) {
        error.setText(isBlank(message) ? getString(R.string.team_no_members) : message);
        error.setVisibility(View.VISIBLE);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
