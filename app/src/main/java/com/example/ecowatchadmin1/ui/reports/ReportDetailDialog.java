package com.example.ecowatchadmin1.ui.reports;

import android.app.Dialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.ui.residents.ReporterAccountUi;
import com.example.ecowatchadmin1.utils.ReportSubmittedAtFormatter;
import com.example.ecowatchadmin1.utils.WasteScaleDisplay;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Shared renderer for the existing admin report-details dialog. */
public final class ReportDetailDialog {
    private ReportDetailDialog() {
        // Utility class.
    }

    /** Opens the normal admin report-details presentation. */
    public static void show(@NonNull Fragment fragment, @NonNull Report report) {
        show(fragment, report, false, null);
    }

    /** Opens the admin presentation and refreshes the owning screen on change. */
    public static void show(@NonNull Fragment fragment, @NonNull Report report,
                            @Nullable Runnable onChanged) {
        show(fragment, report, false, onChanged);
    }

    /**
     * Opens a read-only presentation for reporter history. Administrative
     * assignment controls are hidden because this screen is intentionally
     * read-only and must not expose an inert action.
     */
    public static void showReadOnly(@NonNull Fragment fragment, @NonNull Report report) {
        show(fragment, report, true, null);
    }

    private static void show(@NonNull Fragment fragment, @NonNull Report report,
                             boolean readOnly, @Nullable Runnable onChanged) {
        if (!fragment.isAdded()) return;

        View detailView = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.dialog_report_detail, null, false);
        TextView detailReportId = detailView.findViewById(R.id.detailReportId);
        TextView detailTitle = detailView.findViewById(R.id.detailTitle);
        TextView detailStatus = detailView.findViewById(R.id.detailStatus);
        TextView lifecycleStatus = detailView.findViewById(R.id.detailLifecycleStatus);
        TextView detailDate = detailView.findViewById(R.id.detailDate);
        TextView detailTime = detailView.findViewById(R.id.detailTime);
        TextView detailCategory = detailView.findViewById(R.id.detailCategory);
        TextView detailDescription = detailView.findViewById(R.id.detailDescription);
        TextView detailLocation = detailView.findViewById(R.id.detailLocation);
        TextView priorityLabel = detailView.findViewById(R.id.detailPriorityLabel);
        Spinner prioritySpinner = detailView.findViewById(R.id.detailPrioritySpinner);
        TextView assignedLabel = detailView.findViewById(R.id.detailAssignedPersonnelLabel);
        TextView assignedText = detailView.findViewById(R.id.detailAssignedPersonnel);
        TextView assignmentTimeLabel = detailView.findViewById(R.id.detailAssignmentTimeLabel);
        TextView assignmentTime = detailView.findViewById(R.id.detailAssignmentTime);
        View assignButton = detailView.findViewById(R.id.assignPersonnelBtn);
        View unassignButton = detailView.findViewById(R.id.unassignPersonnelBtn);
        View updateStatusResponseButton = detailView.findViewById(R.id.updateStatusResponseBtn);
        View approveButton = detailView.findViewById(R.id.approveReportBtn);
        View reviseButton = detailView.findViewById(R.id.reviseReportBtn);
        TextView resolutionLabel = detailView.findViewById(R.id.resolutionLabel);
        ImageView resolutionImage = detailView.findViewById(R.id.resolutionImage);
        TextView resolutionDate = detailView.findViewById(R.id.resolutionDate);
        View closeButton = detailView.findViewById(R.id.closeDetailBtn);

        // Each group of related fields sits in its own outlined card, so the
        // cards are toggled alongside the rows they contain. This keeps an
        // empty card from being left behind when a whole group is absent.
        View photoCard = detailView.findViewById(R.id.photoCard);
        View adminResponseCard = detailView.findViewById(R.id.adminResponseCard);
        View personnelNoteCard = detailView.findViewById(R.id.personnelNoteCard);
        View resolutionCard = detailView.findViewById(R.id.resolutionCard);
        View actionsCard = detailView.findViewById(R.id.detailActionsCard);

        String reportId = report.getReportId();
        if (reportId == null || reportId.trim().isEmpty()) reportId = report.getMongoId();
        detailReportId.setText(ReporterAccountUi.valueOrDash(reportId));
        detailTitle.setText(report.getTitle() == null || report.getTitle().trim().isEmpty()
                ? "Untitled report" : report.getTitle());
        detailStatus.setText(report.getStatus() == null || report.getStatus().trim().isEmpty()
                ? "Pending" : report.getStatus());
        detailCategory.setText(report.getWasteType() == null || report.getWasteType().trim().isEmpty()
                ? "Uncategorized" : report.getWasteType());
        detailDescription.setText(report.getDescription() == null ? "—" : report.getDescription());
        detailLocation.setText(String.format(Locale.getDefault(),
                "Lat: %.4f, Lon: %.4f", report.getLatitude(), report.getLongitude()));

        AlertDialog[] dialogHolder = new AlertDialog[1];

        bindLifecycleStatus(lifecycleStatus, report);
        bindCreatedAt(detailDate, detailTime, report.getCreatedAt());
        bindDispatchDetails(fragment, report, detailView);
        bindAdministrativeFields(fragment, report, readOnly, detailView, priorityLabel,
                prioritySpinner, assignedLabel, assignedText, assignmentTimeLabel,
                assignmentTime, assignButton, unassignButton, updateStatusResponseButton,
                approveButton, reviseButton, actionsCard, onChanged, dialogHolder);
        bindResolution(fragment, report, resolutionLabel, resolutionImage, resolutionDate,
                resolutionCard);

        TextView responseLabel = detailView.findViewById(R.id.adminResponseLabel);
        TextView responseText = detailView.findViewById(R.id.detailAdminResponse);
        responseLabel.setVisibility(View.VISIBLE);
        responseText.setVisibility(View.VISIBLE);
        adminResponseCard.setVisibility(View.VISIBLE);
        responseText.setText(report.getAdminResponse() == null
                || report.getAdminResponse().trim().isEmpty()
                ? fragment.getString(R.string.none)
                : report.getAdminResponse());

        // The personnel-only note gets its own card rather than being merged into
        // the response above, so the admin can tell which words the resident read
        // and which ones were only for the cleaner.
        TextView personnelNoteLabel = detailView.findViewById(R.id.personnelNoteLabel);
        TextView personnelNoteText = detailView.findViewById(R.id.detailPersonnelNote);
        String personnelNote = report.getPersonnelNote();
        boolean hasPersonnelNote = personnelNote != null && !personnelNote.trim().isEmpty();
        personnelNoteCard.setVisibility(hasPersonnelNote ? View.VISIBLE : View.GONE);
        if (hasPersonnelNote) {
            personnelNoteLabel.setVisibility(View.VISIBLE);
            personnelNoteText.setVisibility(View.VISIBLE);
            personnelNoteText.setText(personnelNote.trim());
        } else {
            personnelNoteLabel.setVisibility(View.GONE);
            personnelNoteText.setVisibility(View.GONE);
        }

        bindPersonnelCompletion(fragment, report, detailView);

        bindDetailPhotos(fragment, detailView, report, photoCard);
        bindCleaningEvidence(fragment, detailView, report);

        if (readOnly) {
            // Reporter, personnel, vehicle, and team history share this compact
            // four-section read-only layout. Completion evidence remains in the
            // photo section; internal notes and dispatch controls are omitted.
            detailView.findViewById(R.id.adminResponseCard).setVisibility(View.GONE);
            detailView.findViewById(R.id.personnelNoteCard).setVisibility(View.GONE);
            detailView.findViewById(R.id.fromPersonnelNoteCard).setVisibility(View.GONE);
            detailView.findViewById(R.id.assignmentCard).setVisibility(View.GONE);
            detailView.findViewById(R.id.detailActionsCard).setVisibility(View.GONE);
        }

        AlertDialog dialog = new AlertDialog.Builder(fragment.requireContext())
                .setView(detailView)
                .create();
        dialogHolder[0] = dialog;
        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * Shows what the personnel reported back when they finished, and who finished
     * it.
     *
     * <p>This is deliberately separate from the administrator's own note above the
     * assignment card. Two people can be talking about the same report, and the
     * admin has to be able to tell their own instructions apart from the cleaner's
     * account of the work.
     *
     * <p>The card is driven by whether work was completed, not by whether a note
     * exists: a cleaner who finished the job without writing anything down still
     * has to be credited, so the card appears with an explicit "no note" line
     * rather than silently disappearing and leaving the report unattributed.
     */
    private static void bindPersonnelCompletion(Fragment fragment, Report report,
                                                View detailView) {
        View card = detailView.findViewById(R.id.fromPersonnelNoteCard);
        TextView label = detailView.findViewById(R.id.fromPersonnelNoteLabel);
        TextView author = detailView.findViewById(R.id.fromPersonnelNoteBy);
        TextView note = detailView.findViewById(R.id.detailfromPersonnelNote);
        TextView emptyNote = detailView.findViewById(R.id.fromPersonnelNoteEmpty);

        if (!report.hasCompletedWork()) {
            card.setVisibility(View.GONE);
            return;
        }

        label.setVisibility(View.VISIBLE);

        // Team completions are credited to the assigned crew's leader. Any
        // teammate can submit the completion note, but that submitter can vary
        // between refreshes and is not the team's accountable contact.
        String cleanerName = getCompletionAttributionName(report);
        String completedAt = report.getWorkCompletedAt() == null
                || report.getWorkCompletedAt().trim().isEmpty()
                ? null
                : ReporterAccountUi.formatDate(report.getWorkCompletedAt());
        String attribution = completedAt == null
                ? fragment.getString(R.string.personnel_note_authored_by_name, cleanerName)
                : fragment.getString(R.string.personnel_note_authored_by, cleanerName, completedAt);
        author.setText(attribution);
        author.setVisibility(View.VISIBLE);

        if (report.hasPersonnelResponse()) {
            note.setText(report.getPersonnelResponse().trim());
            note.setVisibility(View.VISIBLE);
            emptyNote.setVisibility(View.GONE);
        } else {
            note.setVisibility(View.GONE);
            emptyNote.setVisibility(View.VISIBLE);
        }

        card.setVisibility(View.VISIBLE);
    }

    private static void bindLifecycleStatus(TextView view, Report report) {
        if (report.isArchived() && report.isDeleted()) {
            view.setText(R.string.report_lifecycle_archived_and_trashed);
            view.setVisibility(View.VISIBLE);
        } else if (report.isArchived()) {
            view.setText(R.string.report_lifecycle_archived);
            view.setVisibility(View.VISIBLE);
        } else if (report.isDeleted()) {
            view.setText(R.string.report_lifecycle_trashed);
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    /**
     * Shows who performed the cleanup once the active assignment is gone.
     *
     * <p>Kept as a separate row from the assigned personnel rather than a fallback
     * value in it: the two mean different things. "Assigned" is who is accountable
     * for the work right now, "Completed by" is who did it. Overwriting one with
     * the other would claim someone is still on the hook after approval released
     * them.
     */
    private static void bindCompletedByPersonnel(Fragment fragment, Report report,
                                                 View detailView,
                                                 boolean hasLiveAssignmentName) {
        TextView label = detailView.findViewById(R.id.detailAssignedPersonnelFallbackLabel);
        TextView value = detailView.findViewById(R.id.detailCompletedByPersonnel);

        // Only worth its own row when the assigned field could not answer the
        // question; otherwise it would just repeat the name above it.
        boolean show = !hasLiveAssignmentName && report.hasCompletedWork();
        label.setVisibility(show ? View.VISIBLE : View.GONE);
        value.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;

        value.setText(ReporterAccountUi.valueOrDash(getCompletionAttributionName(report)));
    }

    /**
     * Resolves completion credit consistently for both the note and the
     * completed-by row. Team reports use the leader from the dispatched roster;
     * small reports use the individual recorded on the completed assignment.
     */
    private static String getCompletionAttributionName(Report report) {
        if (report.requiresTeam()) {
            for (Report.AssignedCrewMember member : report.getAssignedCrew()) {
                if (member == null || !member.leader) continue;
                String leaderName = member.fullName;
                if (leaderName == null || leaderName.trim().isEmpty()) {
                    leaderName = member.personnelId;
                }
                if (leaderName != null && !leaderName.trim().isEmpty()) {
                    return leaderName.trim();
                }
            }
        }

        String personnelName = report.getCompletedByPersonnelName();
        if (personnelName != null && !personnelName.trim().isEmpty()) {
            return personnelName.trim();
        }
        return report.getCompletedByPersonnelId();
    }

    /**
     * The scale of the report and what went out to deal with it.
     *
     * <p>The crew and the truck rows only appear when there is one. A small
     * report is handled by a single cleaner, so it has neither, and showing
     * empty rows for them would only be noise.
     */
    private static void bindDispatchDetails(Fragment fragment, Report report, View detailView) {
        String scaleText = WasteScaleDisplay.labelFor(fragment.requireContext(),
                report.getWasteScale());

        // Two rows state the scale: the summary card at the top and the
        // assignment card next to the dispatch controls. They used to share one
        // id, so only the first was ever found and the other stayed blank --
        // both are bound explicitly now.
        TextView scale = detailView.findViewById(R.id.detailWasteScale);
        if (scale != null) {
            scale.setText(scaleText);
        }
        TextView assignmentScale = detailView.findViewById(R.id.detailAssignmentWasteScale);
        if (assignmentScale != null) {
            assignmentScale.setText(scaleText);
        }

        TextView teamLabel = detailView.findViewById(R.id.detailAssignedTeamLabel);
        TextView teamValue = detailView.findViewById(R.id.detailAssignedTeam);
        TextView teamRoster = detailView.findViewById(R.id.detailAssignedTeamRoster);
        boolean teamTask = report.requiresTeam();
        String team = teamTask ? report.getAssignedTeamLabel() : null;
        if (teamLabel != null) {
            teamLabel.setVisibility(team == null ? View.GONE : View.VISIBLE);
        }
        if (teamValue != null) {
            teamValue.setVisibility(team == null ? View.GONE : View.VISIBLE);
            if (team != null) teamValue.setText(team);
        }
        if (teamRoster != null) {
            String rosterText = teamTask ? formatTeamRoster(fragment, report) : null;
            teamRoster.setVisibility(rosterText == null ? View.GONE : View.VISIBLE);
            if (rosterText != null) teamRoster.setText(rosterText);
        }

        TextView vehicleLabel = detailView.findViewById(R.id.detailAssignedVehicleLabel);
        TextView vehicleValue = detailView.findViewById(R.id.detailAssignedVehicle);
        String vehicle = report.getAssignedVehicleLabel();
        if (vehicleLabel != null) {
            vehicleLabel.setVisibility(vehicle == null ? View.GONE : View.VISIBLE);
        }
        if (vehicleValue != null) {
            vehicleValue.setVisibility(vehicle == null ? View.GONE : View.VISIBLE);
            if (vehicle != null) vehicleValue.setText(vehicle);
        }
    }

    private static String formatTeamRoster(Fragment fragment, Report report) {
        List<String> members = new ArrayList<>();
        String leader = null;
        for (Report.AssignedCrewMember member : report.getAssignedCrew()) {
            if (member == null) continue;
            String name = member.fullName == null || member.fullName.trim().isEmpty()
                    ? member.personnelId : member.fullName.trim();
            if (name == null || name.trim().isEmpty()) continue;
            if (member.leader) leader = name.trim();
            members.add(name.trim());
        }
        Collections.sort(members, String.CASE_INSENSITIVE_ORDER);
        if (leader == null && members.isEmpty()) return null;

        String leaderLine = fragment.getString(R.string.team_leader_label) + ": "
                + (leader == null ? fragment.getString(R.string.none) : leader);
        String membersLine = fragment.getString(R.string.team_members_label) + ": "
                + (members.isEmpty() ? fragment.getString(R.string.none)
                : TextUtils.join(", ", members));
        return leaderLine + "\n" + membersLine;
    }

    private static void bindAdministrativeFields(
            Fragment fragment,
            Report report,
            boolean readOnly,
            View detailView,
            TextView priorityLabel,
            Spinner prioritySpinner,
            TextView assignedLabel,
            TextView assignedText,
            TextView assignmentTimeLabel,
            TextView assignmentTime,
            View assignButton,
            View unassignButton,
            View updateStatusResponseButton,
            View approveButton,
            View reviseButton,
            View actionsCard,
            @Nullable Runnable onChanged,
            AlertDialog[] dialogHolder) {
        View reviewActionsDivider = detailView.findViewById(R.id.reviewActionsDivider);

        if (priorityLabel != null) {
            priorityLabel.setVisibility(readOnly ? View.GONE : View.VISIBLE);
        }
        if (prioritySpinner != null) {
            prioritySpinner.setVisibility(readOnly ? View.GONE : View.VISIBLE);
            String priority = report.getPriority() == null || report.getPriority().trim().isEmpty()
                    ? "Normal" : report.getPriority();
            ArrayAdapter<String> priorityAdapter = new ArrayAdapter<>(
                    fragment.requireContext(),
                    android.R.layout.simple_spinner_item,
                    new String[]{priority});
            priorityAdapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item);
            prioritySpinner.setAdapter(priorityAdapter);
            prioritySpinner.setEnabled(false);
        }

        List<String> personnel = report.getAssignedPersonnelIds();
        String assignedName = report.requiresTeam() ? null : report.getAssignedPersonnelName();
        if (!report.requiresTeam() && (assignedName == null || assignedName.trim().isEmpty())) {
            assignedName = personnel == null || personnel.isEmpty()
                    ? report.getAssignedPersonnelId() : TextUtils.join(", ", personnel);
        }
        boolean hasLiveAssignmentName =
                assignedName != null && !assignedName.trim().isEmpty();
        assignedLabel.setVisibility(report.requiresTeam() ? View.GONE : View.VISIBLE);
        assignedText.setVisibility(report.requiresTeam() ? View.GONE : View.VISIBLE);
        assignedText.setText(hasLiveAssignmentName
                ? assignedName.trim() : fragment.getString(R.string.none));

        // A crew assignment leaves the single-personnel pointer empty, so the
        // assignment list is what says whether anybody has been sent yet.
        boolean hasAssignment = report.hasAssignedPersonnel();
        boolean hasAssignmentTime = hasAssignment
                && report.getAssignedAt() != null
                && !report.getAssignedAt().trim().isEmpty();
        assignmentTimeLabel.setVisibility(hasAssignmentTime ? View.VISIBLE : View.GONE);
        assignmentTime.setVisibility(hasAssignmentTime ? View.VISIBLE : View.GONE);
        if (hasAssignmentTime) {
            assignmentTime.setText(ReporterAccountUi.formatDate(report.getAssignedAt()));
        }

        // Approving a report releases the assignment, which is why the assigned
        // name above reads "None" on a finished report. Naming the person who
        // actually did the work is what the admin is really looking for there, so
        // it gets its own row rather than being folded into the assigned field.
        bindCompletedByPersonnel(fragment, report, detailView, hasLiveAssignmentName);

        boolean lifecycleAllowsAssignment = !report.isArchived() && !report.isDeleted()
                && !"Completed".equalsIgnoreCase(report.getStatus())
                && !"Denied".equalsIgnoreCase(report.getStatus());
        boolean underReview = report.getStatus() != null
                && "Under Review".equalsIgnoreCase(report.getStatus().trim());
        reviewActionsDivider.setVisibility(underReview ? View.VISIBLE : View.GONE);

        // A report that is still Pending has nobody assigned to it yet, so there
        // is no one for a status or a response to be written to. That action
        // only unlocks once personnel have actually been assigned.
        boolean awaitingAssignment = "Pending".equalsIgnoreCase(report.getStatus())
                && !hasAssignment;

        Runnable dismissAndRefresh = () -> {
            if (dialogHolder != null && dialogHolder[0] != null && dialogHolder[0].isShowing()) {
                dialogHolder[0].dismiss();
            }
            if (onChanged != null) onChanged.run();
        };

        updateStatusResponseButton.setVisibility(!readOnly && lifecycleAllowsAssignment
                && !awaitingAssignment ? View.VISIBLE : View.GONE);
        updateStatusResponseButton.setOnClickListener(v -> {
            if (!readOnly) showStatusResponseDialog(fragment, report, dismissAndRefresh);
        });

        assignButton.setVisibility(!readOnly && lifecycleAllowsAssignment && !underReview
                ? View.VISIBLE : View.GONE);
        if (assignButton instanceof TextView) {
            ((TextView) assignButton).setText(hasAssignment
                    ? R.string.reassign_personnel : R.string.assign_personnel);
        }
        assignButton.setOnClickListener(v ->
                AssignmentPersonnelBottomSheet.show(fragment, report, dismissAndRefresh));

        unassignButton.setVisibility(!readOnly && hasAssignment && lifecycleAllowsAssignment
                ? View.VISIBLE : View.GONE);
        unassignButton.setOnClickListener(v -> {
            if (!readOnly) confirmUnassign(fragment, report, dismissAndRefresh);
        });

        // Approval means "the cleaner says this is done and I agree". That only
        // becomes true once the cleaner has submitted the work and the report has
        // moved to Under Review. Offering it while the report is merely In
        // Progress would let an admin close a report the crew had not finished,
        // and the server would accept it.
        boolean approvalAvailable = !readOnly
                && lifecycleAllowsAssignment
                && hasAssignment
                && underReview;
        approveButton.setVisibility(approvalAvailable ? View.VISIBLE : View.GONE);
        approveButton.setOnClickListener(v -> {
            if (!readOnly) confirmApprove(fragment, report, dismissAndRefresh);
        });

        // Revising is only meaningful while the work is waiting for review: the
        // report still has its cleaner and the administrator is looking at the
        // submitted after photos.
        boolean revisionAvailable = !readOnly
                && lifecycleAllowsAssignment
                && hasAssignment
                && underReview;
        reviseButton.setVisibility(revisionAvailable ? View.VISIBLE : View.GONE);
        reviseButton.setOnClickListener(v -> {
            if (!readOnly) confirmRevise(fragment, report, dismissAndRefresh);
        });

        // The actions card only earns its space when at least one control
        // inside it is actually available.
        boolean hasAvailableAction = !readOnly && lifecycleAllowsAssignment;
        actionsCard.setVisibility(hasAvailableAction ? View.VISIBLE : View.GONE);
    }

    private static void showStatusResponseDialog(Fragment fragment, Report report,
                                                  @Nullable Runnable onChanged) {
        if (!fragment.isAdded()) return;
        View content = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.dialog_admin_report_status, null, false);
        Spinner statusSpinner = content.findViewById(R.id.statusSpinner);
        TextInputEditText responseInput = content.findViewById(R.id.statusAdminResponse);
        // "Under Review" has to be in this list: the cleaner moves the report
        // there, and a report opened in that state would otherwise pre-select
        // "Pending" and silently downgrade it the moment the admin saves.
        String[] statuses = {
            "Pending", "In Progress", "Under Review", "Denied", "Completed"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                fragment.requireContext(),
                android.R.layout.simple_spinner_item,
                statuses
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusSpinner.setAdapter(adapter);
        String currentStatus = report.getStatus() == null ? "Pending" : report.getStatus();
        for (int index = 0; index < statuses.length; index++) {
            if (statuses[index].equalsIgnoreCase(currentStatus)) {
                statusSpinner.setSelection(index);
                break;
            }
        }
        responseInput.setText(report.getAdminResponse() == null
                ? "" : report.getAdminResponse());

        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.report_status_title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save_status_response, null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String selectedStatus = String.valueOf(statusSpinner.getSelectedItem());
                    String response = responseInput.getText() == null
                            ? "" : responseInput.getText().toString().trim();
                    if (("In Progress".equals(selectedStatus)
                            || "Denied".equals(selectedStatus))
                            && response.isEmpty()) {
                        Toast.makeText(
                                fragment.requireContext(),
                                R.string.status_response_required,
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    ReportsViewModel viewModel = new ViewModelProvider(fragment)
                            .get(ReportsViewModel.class);
                    viewModel.updateReportStatus(report, selectedStatus, response,
                            new com.example.ecowatchadmin1.data.repository.ReportRepository.ApiCallback<Report>() {
                                @Override
                                public void onSuccess(Report result) {
                                    if (!fragment.isAdded()) return;
                                    Toast.makeText(
                                            fragment.requireContext(),
                                            R.string.status_response_updated,
                                            Toast.LENGTH_SHORT
                                    ).show();
                                    dialog.dismiss();
                                    if (onChanged != null) onChanged.run();
                                }

                                @Override
                                public void onError(String error) {
                                    if (fragment.isAdded()) {
                                        Toast.makeText(
                                                fragment.requireContext(),
                                                error,
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                                }
                            });
                }));
        dialog.show();
    }

    private static void confirmUnassign(Fragment fragment, Report report,
                                        @Nullable Runnable onChanged) {
        if (!fragment.isAdded()) return;
        new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.unassign_confirmation_title)
                .setMessage(R.string.unassign_confirmation_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.unassign_personnel, (dialog, which) -> {
                    ReportsViewModel viewModel = new ViewModelProvider(fragment)
                            .get(ReportsViewModel.class);
                    String stableReportId = report.getReportId();
                    if (stableReportId == null || stableReportId.trim().isEmpty()) {
                        stableReportId = report.getMongoId();
                    }
                    viewModel.unassignPersonnel(stableReportId,
                            new com.example.ecowatchadmin1.data.repository.ReportRepository.ApiCallback<Report>() {
                                @Override
                                public void onSuccess(Report result) {
                                    if (!fragment.isAdded()) return;
                                    Toast.makeText(fragment.requireContext(),
                                            R.string.assignment_removed, Toast.LENGTH_SHORT).show();
                                    if (onChanged != null) onChanged.run();
                                }

                                @Override
                                public void onError(String error) {
                                    if (fragment.isAdded()) {
                                        Toast.makeText(fragment.requireContext(), error,
                                                Toast.LENGTH_LONG).show();
                                    }
                                }
                            });
                })
                .show();
    }

    private static void confirmApprove(Fragment fragment, Report report,
                                       @Nullable Runnable onChanged) {
        if (!fragment.isAdded()) return;
        new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.approve_completion_title)
                .setMessage(R.string.approve_completion_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.approve_completion, (dialog, which) -> {
                    ReportsViewModel viewModel = new ViewModelProvider(fragment)
                            .get(ReportsViewModel.class);
                    String stableReportId = report.getReportId();
                    if (stableReportId == null || stableReportId.trim().isEmpty()) {
                        stableReportId = report.getMongoId();
                    }
                    viewModel.approveReport(stableReportId,
                            new com.example.ecowatchadmin1.data.repository.ReportRepository.ApiCallback<Report>() {
                                @Override
                                public void onSuccess(Report result) {
                                    if (!fragment.isAdded()) return;
                                    Toast.makeText(fragment.requireContext(),
                                            R.string.completion_approved, Toast.LENGTH_SHORT).show();
                                    if (onChanged != null) onChanged.run();
                                }

                                @Override
                                public void onError(String error) {
                                    if (fragment.isAdded()) {
                                        Toast.makeText(fragment.requireContext(), error,
                                                Toast.LENGTH_LONG).show();
                                    }
                                }
                            });
                })
                .show();
    }

    /**
     * Asks for the reason the finished work is being sent back, then reopens the
     * task for the cleaner. The note is required: it is the only thing the
     * cleaner sees explaining what to redo on their next attempt.
     */
    private static void confirmRevise(Fragment fragment, Report report,
                                      @Nullable Runnable onChanged) {
        if (!fragment.isAdded()) return;

        View content = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.dialog_revise_report, null, false);
        TextInputEditText noteInput = content.findViewById(R.id.reviseNoteInput);

        AlertDialog dialog = new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.revise_completion_title)
                .setMessage(R.string.revise_completion_message)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.revise_completion, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String note = noteInput.getText() == null
                            ? "" : noteInput.getText().toString().trim();
                    if (note.isEmpty()) {
                        Toast.makeText(fragment.requireContext(),
                                R.string.revision_note_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ReportsViewModel viewModel = new ViewModelProvider(fragment)
                            .get(ReportsViewModel.class);
                    viewModel.reviseReport(report, note,
                            new com.example.ecowatchadmin1.data.repository.ReportRepository.ApiCallback<Report>() {
                                @Override
                                public void onSuccess(Report result) {
                                    if (!fragment.isAdded()) return;
                                    Toast.makeText(fragment.requireContext(),
                                            R.string.revision_requested, Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                    if (onChanged != null) onChanged.run();
                                }

                                @Override
                                public void onError(String error) {
                                    if (fragment.isAdded()) {
                                        Toast.makeText(fragment.requireContext(), error,
                                                Toast.LENGTH_LONG).show();
                                    }
                                }
                            });
                }));
        dialog.show();
    }

    private static void bindResolution(Fragment fragment, Report report,
                                       TextView resolutionLabel,
                                       ImageView resolutionImage,
                                       TextView resolutionDate,
                                       View resolutionCard) {
        String imageUrl = ReporterAccountUi.resolveImageUrl(fragment.getContext(), report.getResolutionImageUrl());
        boolean hasImage = imageUrl != null;
        boolean hasDate = report.getResolvedAt() != null
                && !report.getResolvedAt().trim().isEmpty();

        resolutionLabel.setVisibility(hasImage || hasDate ? View.VISIBLE : View.GONE);
        resolutionImage.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        resolutionDate.setVisibility(hasDate ? View.VISIBLE : View.GONE);
        resolutionCard.setVisibility(hasImage || hasDate ? View.VISIBLE : View.GONE);
        if (hasImage) {
            Glide.with(fragment)
                    .load(imageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .centerCrop()
                    .into(resolutionImage);
        }
        if (hasDate) {
            resolutionDate.setText(ReporterAccountUi.formatDate(report.getResolvedAt()));
        }
    }

    private static void bindCreatedAt(TextView dateView, TextView timeView, String raw) {
        String formattedDate = ReportSubmittedAtFormatter.formatDate(raw);
        String formattedTime = ReportSubmittedAtFormatter.formatTime(raw);
        if (formattedDate == null || formattedTime == null) {
            dateView.setText(raw == null || raw.trim().isEmpty() ? "Not available" : raw);
            timeView.setText("Not available");
            return;
        }
        dateView.setText(formattedDate);
        timeView.setText(formattedTime);
    }

    /**
     * Fills the dialog's photo strip with every image the report carries.
     *
     * <p>A local copy wins over the remote URL for the cover image, because a
     * report that has not finished uploading only exists on the device. The
     * remaining photos come from the server list, resolved to absolute URLs.
     */
    private static void bindDetailPhotos(Fragment fragment, View detailView, Report report,
                                      View photoCard) {
        RecyclerView photoStrip = detailView.findViewById(R.id.detailPhotoStrip);
        TextView photoHint = detailView.findViewById(R.id.detailPhotoHint);

        List<Object> sources = new ArrayList<>();

        // The cover image: prefer the on-device copy when it still exists.
        String coverUrl = report.getPhotoUrl();
        Object cover = null;
        String localPath = report.getLocalPhotoPath();
        if (localPath != null && !localPath.trim().isEmpty()) {
            File localFile = new File(localPath);
            if (localFile.exists()) cover = localFile;
        }
        if (cover == null) {
            cover = ReporterAccountUi.resolveImageUrl(fragment.getContext(), coverUrl);
        }
        if (cover != null) sources.add(cover);

        // The remaining photos come from the server's ordered list. The cover is
        // already shown, so it is skipped rather than repeated.
        for (String url : report.getPhotoUrls()) {
            if (url == null || url.trim().isEmpty()) continue;
            String resolved = ReporterAccountUi.resolveImageUrl(fragment.getContext(), url.trim());
            if (resolved == null) continue;
            if (cover instanceof String && resolved.equals(cover)) continue;
            sources.add(resolved);
        }

        if (sources.isEmpty()) {
            photoStrip.setVisibility(View.GONE);
            photoHint.setVisibility(View.GONE);
            photoCard.setVisibility(View.GONE);
            return;
        }

        ReportDetailPhotoAdapter adapter = new ReportDetailPhotoAdapter();
        adapter.setOnPhotoClickListener((position, source) -> showFullScreenImage(fragment, source));
        photoStrip.setAdapter(adapter);
        adapter.setPhotos(sources);
        photoStrip.setVisibility(View.VISIBLE);
        photoHint.setVisibility(sources.size() > 1 ? View.VISIBLE : View.GONE);
        photoCard.setVisibility(View.VISIBLE);
    }

    /**
     * Shows the personnel's BEFORE and AFTER cleaning photos, one labelled strip
     * each. The whole card is hidden when there is nothing to show; a report that
     * has no photos yet simply omits the section.
     */
    private static void bindCleaningEvidence(Fragment fragment, View detailView, Report report) {
        View card = detailView.findViewById(R.id.cleaningEvidenceCard);
        TextView beforeLabel = detailView.findViewById(R.id.beforePhotosLabel);
        TextView afterLabel = detailView.findViewById(R.id.afterPhotosLabel);
        RecyclerView beforeStrip = detailView.findViewById(R.id.beforePhotoStrip);
        RecyclerView afterStrip = detailView.findViewById(R.id.afterPhotoStrip);
        TextView emptyText = detailView.findViewById(R.id.noCleaningEvidenceText);

        List<Object> before = resolveEvidenceSources(fragment, report.getBeforePhotoUrls());
        List<Object> after = resolveEvidenceSources(fragment, report.getAfterPhotoUrls());

        if (before.isEmpty() && after.isEmpty()) {
            card.setVisibility(View.GONE);
            return;
        }

        bindEvidenceStrip(fragment, beforeLabel, beforeStrip, before);
        bindEvidenceStrip(fragment, afterLabel, afterStrip, after);
        emptyText.setVisibility(View.GONE);
        card.setVisibility(View.VISIBLE);
    }

    private static void bindEvidenceStrip(Fragment fragment, TextView label,
                                          RecyclerView strip, List<Object> sources) {
        if (sources.isEmpty()) {
            label.setVisibility(View.GONE);
            strip.setVisibility(View.GONE);
            return;
        }
        ReportDetailPhotoAdapter adapter = new ReportDetailPhotoAdapter();
        adapter.setOnPhotoClickListener((position, source) -> showFullScreenImage(fragment, source));
        strip.setAdapter(adapter);
        adapter.setPhotos(sources);
        label.setVisibility(View.VISIBLE);
        strip.setVisibility(View.VISIBLE);
    }

    private static List<Object> resolveEvidenceSources(Fragment fragment, List<String> urls) {
        List<Object> sources = new ArrayList<>();
        if (urls == null) return sources;
        for (String url : urls) {
            if (url == null || url.trim().isEmpty()) continue;
            String resolved = ReporterAccountUi.resolveImageUrl(fragment.getContext(), url.trim());
            if (resolved == null) continue;
            sources.add(resolved);
        }
        return sources;
    }

    private static void showFullScreenImage(Fragment fragment, Object imageSource) {
        if (!fragment.isAdded()) return;
        Dialog dialog = new Dialog(fragment.requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView imageView = new ImageView(fragment.requireContext());
        imageView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setBackgroundColor(fragment.getResources().getColor(android.R.color.black));
        dialog.setContentView(imageView);
        Glide.with(fragment).load(imageSource).into(imageView);
        imageView.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
