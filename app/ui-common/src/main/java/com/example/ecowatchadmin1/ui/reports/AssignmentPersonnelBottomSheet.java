package com.example.ecowatchadmin1.ui.reports;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.CleaningTeam;
import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.models.Vehicle;
import com.example.ecowatchadmin1.data.repository.CleaningTeamRepository;
import com.example.ecowatchadmin1.data.repository.PersonnelRepository;
import com.example.ecowatchadmin1.data.repository.VehicleRepository;
import com.example.ecowatchadmin1.ui.residents.ReporterAccountUi;
import com.example.ecowatchadmin1.utils.WasteScaleDisplay;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Searchable, server-backed picker used by the existing report detail dialog.
 *
 * <p>The scale of the report decides what shape the dispatch takes. A medium or
 * large report is sent to one of the named cleaning teams, so the sheet offers
 * the teams the administrator already maintains instead of asking for a crew to
 * be built tap by tap. A small report still goes to one person, and that person
 * must not belong to any team: their crew already answers for team dispatches,
 * so a team member is never pulled out individually.
 */
public final class AssignmentPersonnelBottomSheet {

    private AssignmentPersonnelBottomSheet() {
    }

    public static void show(@NonNull Fragment fragment, @NonNull Report report,
                            @Nullable Runnable onChanged) {
        if (!fragment.isAdded()) return;

        View content = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.dialog_assign_personnel, null, false);
        BottomSheetDialog dialog = new BottomSheetDialog(fragment.requireContext());
        dialog.setContentView(content);

        TextInputLayout searchLayout = content.findViewById(R.id.assignmentSearchLayout);
        TextInputEditText search = content.findViewById(R.id.assignmentSearch);
        TextInputLayout responseLayout = content.findViewById(R.id.assignmentResponseLayout);
        TextInputEditText response = content.findViewById(R.id.assignmentResponse);
        TextInputLayout personnelNoteLayout =
                content.findViewById(R.id.assignmentResponseLayoutforPersonnel);
        TextInputEditText personnelNote =
                content.findViewById(R.id.assignmentResponseforPersonnel);
        View loading = content.findViewById(R.id.assignmentLoading);
        MaterialButton confirm = content.findViewById(R.id.assignmentConfirm);
        TextView title = content.findViewById(R.id.assignmentSheetTitle);
        TextView subtitle = content.findViewById(R.id.assignmentSheetSubtitle);
        TextView scaleBanner = content.findViewById(R.id.assignmentScaleBanner);
        TextView crewLabel = content.findViewById(R.id.assignmentTeamLabel);
        TextView crewHint = content.findViewById(R.id.assignmentCrewHint);
        LinearLayout crewCards = content.findViewById(R.id.assignmentCrewCards);
        TextView crewEmpty = content.findViewById(R.id.assignmentCrewEmpty);
        TextView vehicleLabel = content.findViewById(R.id.assignmentVehicleLabel);
        TextView vehicleHint = content.findViewById(R.id.assignmentVehicleHint);
        View vehicleScroll = content.findViewById(R.id.assignmentVehicleScroll);
        LinearLayout vehicleCards = content.findViewById(R.id.assignmentVehicleCards);
        TextView vehicleEmpty = content.findViewById(R.id.assignmentVehicleEmpty);

        // The scale decides the shape of the dispatch, so it is read once and
        // drives everything below.
        final String wasteScale = report.getWasteScale();
        final boolean teamMode = report.requiresTeam();
        final boolean vehicleMode = report.requiresVehicle();
        vehicleLabel.setVisibility(vehicleMode ? View.VISIBLE : View.GONE);
        vehicleHint.setVisibility(vehicleMode ? View.VISIBLE : View.GONE);
        vehicleScroll.setVisibility(vehicleMode ? View.VISIBLE : View.GONE);

        String reportId = report.getReportId();
        if (reportId == null || reportId.trim().isEmpty()) {
            reportId = report.getMongoId();
        }
        final String stableReportId = reportId;
        // A crew leaves the single-personnel pointer empty, so the assignment
        // list is what says whether anybody has been sent yet.
        final boolean reassignment = report.hasAssignedPersonnel();
        title.setText(reassignment ? R.string.reassign_personnel : R.string.assign_personnel);
        subtitle.setText(R.string.assignment_sheet_subtitle);
        bindScaleBanner(scaleBanner, fragment.requireContext(),
                WasteScaleDisplay.labelFor(fragment.requireContext(), wasteScale),
                fragment.getString(scaleRuleFor(wasteScale)));
        if (report.getAdminResponse() != null) {
            response.setText(report.getAdminResponse());
        }
        // Prefill the note the admin gave the cleaner previously so a
        // reassignment round-trips it instead of quietly dropping it.
        if (report.getPersonnelNote() != null) {
            personnelNote.setText(report.getPersonnelNote());
        }

        // The card row is the one picker for every scale: a team row for a crew
        // scale, a single-personnel row for a small job. The search box only
        // filters people, so it is not shown when the row holds teams.
        crewLabel.setText(teamMode
                ? R.string.assignment_team_label : R.string.assignment_personnel_label);
        crewHint.setText(teamMode
                ? R.string.assignment_team_hint : R.string.assignment_single_hint);
        searchLayout.setVisibility(teamMode ? View.GONE : View.VISIBLE);

        // Who is going out. A small job starts from the single person already
        // on the report; a crew report starts from its named team below.
        final Set<String> selectedIds = new LinkedHashSet<>();
        if (!teamMode && reassignment) {
            List<String> assigned = report.getAssignedPersonnelIds();
            if (assigned != null) {
                for (String personnelId : assigned) {
                    if (personnelId != null && !personnelId.trim().isEmpty()) {
                        selectedIds.add(normalize(personnelId));
                        break;
                    }
                }
            }
            // A crew leaves the single pointer empty; an older single dispatch
            // is only visible through it.
            if (selectedIds.isEmpty()) {
                String single = report.getAssignedPersonnelId();
                if (single != null && !single.trim().isEmpty()) {
                    selectedIds.add(normalize(single));
                }
            }
        }

        // The chosen crew, when the scale calls for one.
        final CleaningTeam[] selectedTeam = {null};
        final Vehicle[] selectedVehicle = {null};

        final List<Personnel> source = new ArrayList<>();
        final List<CleaningTeam> allTeamsList = new ArrayList<>();
        final List<Vehicle> availableVehicles = new ArrayList<>();
        final List<Vehicle> allFleetVehicles = new ArrayList<>();
        final String[] query = {""};
        final boolean[] personnelFinished = {false};
        final boolean[] teamsFinished = {false};
        final String[] teamsError = {null};
        // Reassignment selects the assigned team once, after its crew is known;
        // later renders must not undo a selection the administrator just made.
        final boolean[] teamPrefilled = {false};
        final boolean[] vehiclesFinished = {!vehicleMode};
        final String[] vehiclesError = {null};

        Runnable render = new Runnable() {
            @Override
            public void run() {
                if (!fragment.isAdded()) return;

                if (teamMode) {
                    // The assigned team can only be restored once its member
                    // list and the personnel availability are both known.
                    if (!teamPrefilled[0] && teamsFinished[0] && personnelFinished[0]) {
                        teamPrefilled[0] = true;
                        if (reassignment) {
                            CleaningTeam assigned = findByTeamId(allTeamsList,
                                    report.getAssignedTeamId());
                            if (assigned != null && isTeamAvailable(
                                    assigned, source, stableReportId)) {
                                selectedTeam[0] = assigned;
                            }
                        }
                    }

                    buildTeamCards(fragment.requireContext(), crewCards, allTeamsList,
                            selectedTeam, source, stableReportId, this);
                    if (teamsError[0] != null) {
                        crewEmpty.setText(teamsError[0]);
                    } else {
                        crewEmpty.setText(R.string.assignment_no_teams);
                    }
                    crewEmpty.setVisibility(teamsFinished[0] && allTeamsList.isEmpty()
                            ? View.VISIBLE : View.GONE);

                    boolean ready = selectedTeam[0] != null
                            && isTeamAvailable(selectedTeam[0], source, stableReportId)
                            && (!vehicleMode || (selectedVehicle[0] != null && hasAssignedDriver(selectedVehicle[0], source)));
                    confirm.setEnabled(ready);
                } else {
                    // A small job goes to one person who is not part of a team.
                    // The team list is what identifies those people, so the
                    // cards wait for it before offering anybody.
                    HashSet<String> teamMemberIds = new HashSet<>();
                    for (CleaningTeam team : allTeamsList) {
                        if (team == null) continue;
                        for (String memberId : teamMemberIdsOf(team)) {
                            teamMemberIds.add(normalize(memberId));
                        }
                    }

                    if (!teamsFinished[0] || teamsError[0] != null) {
                        // Team membership is unknown while the list is loading
                        // or after it failed, so nobody can be ruled in or out
                        // safely. Offer nothing rather than a team member.
                        crewCards.removeAllViews();
                        confirm.setEnabled(false);
                        if (teamsError[0] != null) {
                            crewEmpty.setText(teamsError[0]);
                            crewEmpty.setVisibility(View.VISIBLE);
                        } else {
                            crewEmpty.setVisibility(View.GONE);
                        }
                    } else {
                        String normalized = query[0] == null
                                ? "" : query[0].trim().toLowerCase(Locale.getDefault());
                        List<Personnel> filtered = new ArrayList<>();
                        for (Personnel person : source) {
                            if (person == null || !person.isEnabled() || person.isDriver()) continue;
                            String pid = person.getPersonnelId();
                            if (pid != null && (teamMemberIds.contains(normalize(pid))
                                    || hasTeamAssignment(person))) continue;
                            if (normalized.isEmpty() || matches(person, normalized)) {
                                filtered.add(person);
                            }
                        }
                        // A reassignment can prefill somebody who has since
                        // joined a team, been disabled, or become a driver.
                        // Drop that stale choice so it cannot keep Confirm enabled.
                        Set<String> eligibleIds = new HashSet<>();
                        for (Personnel person : filtered) {
                            if (person.getPersonnelId() != null) {
                                eligibleIds.add(normalize(person.getPersonnelId()));
                            }
                        }
                        selectedIds.retainAll(eligibleIds);
                        // The cards are rebuilt every pass, so an unselected
                        // card gets its default look back from the layout it
                        // was inflated from.
                        buildCrewCards(fragment.requireContext(), crewCards, filtered,
                                selectedIds, stableReportId, this);

                        boolean ready = selectedIds.size() == 1
                                && allAssignable(new ArrayList<>(selectedIds), source,
                                stableReportId);
                        confirm.setEnabled(ready);
                        crewEmpty.setText(R.string.assignment_no_personnel);
                        crewEmpty.setVisibility(filtered.isEmpty() && personnelFinished[0]
                                ? View.VISIBLE : View.GONE);
                    }
                }

                if (vehicleMode) {
                    availableVehicles.clear();
                    if (vehiclesFinished[0] && personnelFinished[0]) {
                        for (Vehicle vehicle : allFleetVehicles) {
                            if (vehicle == null || isBlank(vehicle.getVehicleId())) continue;
                            // Excluded from operations/tasks since it has no driver
                            if (!hasAssignedDriver(vehicle, source)) continue;

                            boolean onThisReport = vehicle.getCurrentReportId() != null
                                    && stableReportId.equalsIgnoreCase(vehicle.getCurrentReportId());
                            boolean alreadyAssigned = reassignment
                                    && report.getAssignedVehicleId() != null
                                    && !isBlank(vehicle.getVehicleId())
                                    && report.getAssignedVehicleId().equalsIgnoreCase(
                                    vehicle.getVehicleId());
                            if (vehicle.isAvailable() || onThisReport || alreadyAssigned) {
                                availableVehicles.add(vehicle);
                            }
                            if (reassignment && report.getAssignedVehicleId() != null
                                    && report.getAssignedVehicleId().equalsIgnoreCase(vehicle.getVehicleId())
                                    && selectedVehicle[0] == null) {
                                selectedVehicle[0] = vehicle;
                            }
                        }
                    }

                    if (selectedVehicle[0] != null && !hasAssignedDriver(selectedVehicle[0], source)) {
                        selectedVehicle[0] = null;
                    }

                    buildVehicleCards(fragment.requireContext(), vehicleCards, availableVehicles,
                            selectedVehicle, stableReportId, this);
                    if (vehiclesError[0] == null || vehiclesError[0].trim().isEmpty()) {
                        vehicleEmpty.setText(R.string.assignment_no_vehicles);
                    } else {
                        vehicleEmpty.setText(vehiclesError[0]);
                    }
                    vehicleEmpty.setVisibility(vehiclesFinished[0] && personnelFinished[0]
                            && availableVehicles.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }
        };

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query[0] = s == null ? "" : s.toString();
                render.run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // The people who can be sent. Always loaded: a small job picks one of
        // them, and a crew dispatch checks its members against them.
        loading.setVisibility(View.VISIBLE);
        new PersonnelRepository(fragment.requireActivity().getApplication())
                .getPersonnel(new PersonnelRepository.PersonnelListCallback() {
                    @Override
                    public void onSuccess(List<Personnel> people) {
                        if (!dialog.isShowing() || !fragment.isAdded()) return;
                        source.clear();
                        if (people != null) source.addAll(people);
                        personnelFinished[0] = true;
                        if (teamsFinished[0] && (!vehicleMode || vehiclesFinished[0])) {
                            loading.setVisibility(View.GONE);
                        }
                        render.run();
                    }

                    @Override
                    public void onError(String message) {
                        if (!dialog.isShowing() || !fragment.isAdded()) return;
                        personnelFinished[0] = true;
                        if (teamsFinished[0]) {
                            loading.setVisibility(View.GONE);
                        }
                        if (!teamMode) {
                            crewEmpty.setVisibility(View.VISIBLE);
                            crewEmpty.setText(message == null || message.trim().isEmpty()
                                    ? fragment.getString(R.string.assignment_no_personnel) : message);
                        }
                    }
                });

        if (vehicleMode) {
            new VehicleRepository(fragment.requireActivity().getApplication())
                    .getFleet(new VehicleRepository.FleetCallback() {
                        @Override public void onSuccess(VehicleRepository.FleetSnapshot snapshot) {
                            if (!dialog.isShowing() || !fragment.isAdded()) return;
                            allFleetVehicles.clear();
                            if (snapshot != null && snapshot.getVehicles() != null) {
                                allFleetVehicles.addAll(snapshot.getVehicles());
                            }
                            vehiclesFinished[0] = true;
                            if (teamsFinished[0] && personnelFinished[0]) {
                                loading.setVisibility(View.GONE);
                            }
                            render.run();
                        }

                        @Override public void onError(String message) {
                            if (!dialog.isShowing() || !fragment.isAdded()) return;
                            vehiclesError[0] = message;
                            vehiclesFinished[0] = true;
                            if (teamsFinished[0] && personnelFinished[0]) {
                                loading.setVisibility(View.GONE);
                            }
                            render.run();
                        }
                    });
        }

        // Load full team rosters for both dispatch modes. The team-list endpoint
        // can return counts without member ids; relying on that response alone
        // let existing team members leak into the small-job personnel picker.
        final CleaningTeamRepository teamRepository =
                new CleaningTeamRepository(fragment.requireActivity().getApplication());
        teamRepository.getTeams(new CleaningTeamRepository.TeamListCallback() {
            @Override
            public void onSuccess(List<CleaningTeam> teams) {
                if (!dialog.isShowing() || !fragment.isAdded()) return;
                allTeamsList.clear();
                if (teams != null) allTeamsList.addAll(teams);

                // A team row does not reliably carry its crew. Read each full
                // team once, both to check crew availability and to rule its
                // members out of individual assignments.
                int pending = 0;
                List<CleaningTeam> teamsToLoad = new ArrayList<>();
                for (CleaningTeam team : allTeamsList) {
                    if (team != null && !isBlank(team.getTeamId())) {
                        pending++;
                        teamsToLoad.add(team);
                    }
                }
                if (pending == 0) {
                    teamsFinished[0] = true;
                    if (personnelFinished[0] && (!vehicleMode || vehiclesFinished[0])) {
                        loading.setVisibility(View.GONE);
                    }
                    render.run();
                    return;
                }

                final int[] remaining = {pending};
                final Runnable onTeamLoaded = () -> {
                    if (!dialog.isShowing() || !fragment.isAdded()) return;
                    remaining[0]--;
                    if (remaining[0] > 0) return;
                    teamsFinished[0] = true;
                    if (personnelFinished[0] && (!vehicleMode || vehiclesFinished[0])) {
                        loading.setVisibility(View.GONE);
                    }
                    render.run();
                };

                for (CleaningTeam listed : teamsToLoad) {
                    final CleaningTeam listedTeam = listed;
                    teamRepository.getTeam(listed.getTeamId(),
                            new CleaningTeamRepository.TeamCallback() {
                                @Override
                                public void onSuccess(CleaningTeam team) {
                                    if (!dialog.isShowing() || !fragment.isAdded()) return;
                                    if (team != null) {
                                        for (int i = 0; i < allTeamsList.size(); i++) {
                                            if (sameTeam(allTeamsList.get(i), listedTeam)) {
                                                allTeamsList.set(i, team);
                                                break;
                                            }
                                        }
                                    }
                                    onTeamLoaded.run();
                                }

                                @Override
                                public void onError(String message) {
                                    onTeamLoaded.run();
                                }
                            });
                }
            }

            @Override
            public void onError(String message) {
                if (!dialog.isShowing() || !fragment.isAdded()) return;
                teamsFinished[0] = true;
                teamsError[0] = message == null || message.trim().isEmpty()
                        ? fragment.getString(R.string.assignment_no_teams) : message;
                if (personnelFinished[0]) {
                    loading.setVisibility(View.GONE);
                }
                if (teamMode) {
                    crewEmpty.setText(teamsError[0]);
                    crewEmpty.setVisibility(View.VISIBLE);
                }
                render.run();
            }
        });

        confirm.setOnClickListener(v -> {
            List<String> selected;
            String teamId = null;
            if (teamMode) {
                if (selectedTeam[0] == null) {
                    Toast.makeText(fragment.requireContext(),
                            R.string.assignment_select_team, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!isTeamAvailable(selectedTeam[0], source, stableReportId)) {
                    Toast.makeText(fragment.requireContext(),
                            R.string.assignment_team_busy, Toast.LENGTH_SHORT).show();
                    return;
                }
                teamId = selectedTeam[0].getTeamId();
                selected = teamMemberIdsOf(selectedTeam[0]);
            } else {
                selected = new ArrayList<>(selectedIds);
                if (selected.size() != 1) {
                    searchLayout.setError(fragment.getString(
                            R.string.assignment_select_personnel));
                    return;
                }
            }
            searchLayout.setError(null);
            String adminResponse = response.getText() == null
                    ? "" : response.getText().toString().trim();
            if (adminResponse.isEmpty()) {
                responseLayout.setError(fragment.getString(R.string.assignment_response_required));
                return;
            }
            responseLayout.setError(null);
            // The personnel note is optional: the resident still needs an answer,
            // but instructions for the cleaner are only useful when the admin has
            // something specific to add.
            String note = personnelNote.getText() == null
                    ? "" : personnelNote.getText().toString().trim();
            if (personnelNoteLayout != null) personnelNoteLayout.setError(null);
            confirm.setEnabled(false);
            confirm.setText(fragment.getString(R.string.assigning_personnel));

            if (vehicleMode && (selectedVehicle[0] == null || !hasAssignedDriver(selectedVehicle[0], source))) {
                confirm.setEnabled(true);
                confirm.setText(fragment.getString(
                        reassignment ? R.string.reassign_personnel : R.string.assign_personnel));
                Toast.makeText(fragment.requireContext(), R.string.assignment_no_vehicles,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            String vehicleId = selectedVehicle[0] == null ? null : selectedVehicle[0].getVehicleId();

            ReportsViewModel viewModel = new ViewModelProvider(fragment).get(ReportsViewModel.class);
            ReportRepositoryCallback callback = new ReportRepositoryCallback() {
                @Override
                public void onSuccess(Report result) {
                    if (!fragment.isAdded()) return;
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                    Toast.makeText(fragment.requireContext(),
                            R.string.assignment_saved, Toast.LENGTH_SHORT).show();
                    if (onChanged != null) onChanged.run();
                }

                @Override
                public void onError(String message) {
                    if (!fragment.isAdded()) return;
                    confirm.setEnabled(true);
                    confirm.setText(fragment.getString(
                            reassignment ? R.string.reassign_personnel : R.string.assign_personnel));
                    Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_LONG).show();
                }
            };
            if (reassignment) {
                viewModel.reassignPersonnel(stableReportId, selected, wasteScale, teamId,
                        vehicleId, adminResponse, note, report.getPriority(), callback);
            } else {
                viewModel.assignPersonnel(stableReportId, selected, wasteScale, teamId,
                        vehicleId, adminResponse, note, report.getPriority(), callback);
            }
        });

        dialog.setOnShowListener(ignored -> {
            if (dialog.getBehavior() != null) {
                BottomSheetBehavior<?> behavior = (BottomSheetBehavior<?>) dialog.getBehavior();
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        dialog.show();
    }

    /**
     * Builds the row of team cards for a crew dispatch.
     *
     * <p>A team is offered when it is dispatch-ready and its whole crew is free.
     * An unavailable team stays visible with a grey dot and a dimmed card so the
     * administrator can see why a crew cannot be sent, without being able to
     * dispatch it.
     */
    private static void buildTeamCards(@NonNull Context context, @NonNull LinearLayout row,
                                       @NonNull List<CleaningTeam> teams,
                                       @NonNull CleaningTeam[] selectedTeam,
                                       @NonNull List<Personnel> people,
                                       @NonNull String reportId,
                                       @NonNull Runnable render) {
        row.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        for (CleaningTeam team : teams) {
            if (team == null) continue;
            View card = inflater.inflate(R.layout.item_assignment_team_card, row, false);
            TextView name = card.findViewById(R.id.teamCardName);
            TextView meta = card.findViewById(R.id.teamCardMeta);
            TextView leader = card.findViewById(R.id.teamCardLeader);
            View dot = card.findViewById(R.id.teamCardDot);
            MaterialCardView cardView = (MaterialCardView) card;

            name.setText(team.getTeamName() != null && !team.getTeamName().trim().isEmpty()
                    ? team.getTeamName().trim()
                    : (team.getTeamId() == null ? "" : team.getTeamId()));
            meta.setText(context.getString(R.string.team_crew_count, team.getCrewSize()));

            String leaderName = team.getResolvedLeaderName();
            if (leaderName != null && !leaderName.trim().isEmpty()) {
                leader.setText(context.getString(R.string.assignment_team_leader, leaderName.trim()));
            } else {
                leader.setText(context.getString(R.string.assignment_team_no_leader));
            }

            boolean available = isTeamAvailable(team, people, reportId);
            dot.setBackgroundResource(available
                    ? R.drawable.bg_dot_available : R.drawable.bg_dot_busy);
            cardView.setAlpha(available ? 1f : 0.55f);

            boolean selected = selectedTeam[0] != null
                    && sameTeam(selectedTeam[0], team);
            applySelection(cardView, selected, context);

            final CleaningTeam tapped = team;
            cardView.setOnClickListener(v -> {
                if (!isTeamAvailable(tapped, people, reportId)) {
                    Toast.makeText(context, R.string.assignment_team_busy,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (selectedTeam[0] != null && sameTeam(selectedTeam[0], tapped)) {
                    selectedTeam[0] = null;
                } else {
                    // One crew answers for the dispatch, so a new tap replaces
                    // the previous team rather than adding to it.
                    selectedTeam[0] = tapped;
                }
                render.run();
            });

            row.addView(card);
        }
    }

    /** Builds the available collection vehicle picker used by large reports. */
    private static void buildVehicleCards(@NonNull Context context, @NonNull LinearLayout row,
                                          @NonNull List<Vehicle> vehicles,
                                          @NonNull Vehicle[] selectedVehicle,
                                          @NonNull String reportId,
                                          @NonNull Runnable render) {
        row.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        for (Vehicle vehicle : vehicles) {
            if (vehicle == null || isBlank(vehicle.getVehicleId())) continue;
            View card = inflater.inflate(R.layout.item_assignment_vehicle_card, row, false);
            TextView plate = card.findViewById(R.id.vehicleCardPlate);
            View dot = card.findViewById(R.id.vehicleCardDot);
            MaterialCardView cardView = (MaterialCardView) card;
            String plateNumber = vehicle.getPlateNumber();
            plate.setText(isBlank(plateNumber) ? vehicle.getVehicleType() : plateNumber.trim());

            boolean available = vehicle.isAvailable() || (vehicle.getCurrentReportId() != null
                    && reportId.equalsIgnoreCase(vehicle.getCurrentReportId()));
            dot.setBackgroundResource(available
                    ? R.drawable.bg_dot_available : R.drawable.bg_dot_busy);
            cardView.setAlpha(available ? 1f : 0.55f);
            boolean selected = selectedVehicle[0] != null
                    && vehicle.getVehicleId().equalsIgnoreCase(selectedVehicle[0].getVehicleId());
            applySelection(cardView, selected, context);
            cardView.setOnClickListener(v -> {
                if (!available) return;
                selectedVehicle[0] = selected ? null : vehicle;
                render.run();
            });
            row.addView(card);
        }
    }

    /**
     * Builds the row of personnel cards for a small job.
     *
     * <p>Every card is inflated fresh on each pass, so a card that lost its
     * selection simply gets the default surface background and hairline back
     * from the layout. A busy person stays visible with a grey dot and a
     * dimmed card so the administrator can see who is out there without
     * being able to send them twice.
     */
    private static void buildCrewCards(@NonNull Context context, @NonNull LinearLayout row,
                                       @NonNull List<Personnel> people,
                                       @NonNull Set<String> selectedIds,
                                       @NonNull String reportId,
                                       @NonNull Runnable render) {
        row.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        for (Personnel person : people) {
            View card = inflater.inflate(R.layout.item_assignment_crew_card, row, false);
            ShapeableImageView avatar = card.findViewById(R.id.crewCardAvatar);
            TextView name = card.findViewById(R.id.crewCardName);
            View dot = card.findViewById(R.id.crewCardDot);
            MaterialCardView cardView = (MaterialCardView) card;

            name.setText(person.getFullName());

            boolean assignable = isAssignable(person, reportId);
            dot.setBackgroundResource(assignable
                    ? R.drawable.bg_dot_available : R.drawable.bg_dot_busy);
            cardView.setAlpha(assignable ? 1f : 0.55f);

            String profileImage = person.getProfileImageUrl();
            if (profileImage == null || profileImage.trim().isEmpty()) {
                avatar.setImageResource(R.drawable.ic_person_default);
            } else {
                Glide.with(avatar)
                        .load(ReporterAccountUi.resolveImageUrl(context, profileImage))
                        .placeholder(R.drawable.ic_person_default)
                        .error(R.drawable.ic_person_default)
                        .centerCrop()
                        .into(avatar);
            }

            final String personnelId = normalize(person.getPersonnelId());
            boolean selected = selectedIds.contains(personnelId);
            applySelection(cardView, selected, context);

            cardView.setOnClickListener(v -> {
                if (!assignable) {
                    Toast.makeText(context, R.string.assignment_personnel_busy,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!selectedIds.remove(personnelId)) {
                    // A small job goes to exactly one person: the new pick
                    // replaces whatever was there.
                    selectedIds.clear();
                    selectedIds.add(personnelId);
                }
                render.run();
            });

            row.addView(card);
        }
    }

    /** Ticks a card in: the app green, a thicker outline, a tinted face. */
    private static void applySelection(@NonNull MaterialCardView card, boolean selected,
                                       @NonNull Context context) {
        if (!selected) return;
        float density = context.getResources().getDisplayMetrics().density;
        card.setStrokeWidth(Math.round(2 * density));
        card.setStrokeColor(ContextCompat.getColor(context, R.color.green));
        card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.chip_background));
    }

    /**
     * The banner that states the scale, with the scale itself highlighted.
     *
     * <p>The first line reads as a bold headline and the scale name within it
     * is picked out in the app green and set larger, so the eye lands on the
     * fact the dispatch has to obey before it reads the rule below.
     */
    private static void bindScaleBanner(@NonNull TextView banner, @NonNull Context context,
                                        @NonNull String scaleLabel, @NonNull String rule) {
        String text = context.getString(R.string.assignment_scale_banner, scaleLabel, rule);
        SpannableStringBuilder styled = new SpannableStringBuilder(text);
        int lineEnd = text.indexOf('\n');
        if (lineEnd < 0) lineEnd = text.length();
        styled.setSpan(new StyleSpan(Typeface.BOLD), 0, lineEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int scaleStart = text.indexOf(scaleLabel);
        if (scaleStart >= 0) {
            int scaleEnd = scaleStart + scaleLabel.length();
            styled.setSpan(new ForegroundColorSpan(
                            ContextCompat.getColor(context, R.color.green)),
                    scaleStart, scaleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new AbsoluteSizeSpan(16, true), scaleStart, scaleEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        banner.setText(styled);
    }

    /**
     * True when the crew can go out on this report.
     *
     * <p>A team is only sent when it has a leader, a crew of at least two and
     * every member is free. A team already on this report stays available: that
     * is the crew being replaced.
     */
    private static boolean isTeamAvailable(@Nullable CleaningTeam team,
                                            @NonNull List<Personnel> people,
                                            @NonNull String reportId) {
        if (team == null || !team.isDispatchReady()) return false;
        List<CleaningTeam.Member> members = team.getMembers();
        // A list row without the crew cannot be checked, so it is not offered.
        if (members.isEmpty()) return false;

        Set<String> checked = new HashSet<>();
        for (CleaningTeam.Member member : members) {
            if (member == null || member.getPersonnelId() == null) return false;
            String id = normalize(member.getPersonnelId());
            if (id.isEmpty() || !checked.add(id)) continue;
            if (!member.isEnabled()) return false;
            Personnel person = find(people, id);
            if (person == null || !isAssignable(person, reportId)) return false;
        }
        // The leader walks the route too; a payload that only names the leader
        // on the team row still has to be checked.
        String leaderId = team.getLeaderId();
        if (leaderId != null && !leaderId.trim().isEmpty()) {
            String id = normalize(leaderId);
            if (checked.add(id)) {
                Personnel leader = find(people, id);
                if (leader == null || !isAssignable(leader, reportId)) return false;
            }
        }
        return true;
    }

    /** The ids of everyone on the crew, leader included, ready to send. */
    @NonNull
    private static List<String> teamMemberIdsOf(@Nullable CleaningTeam team) {
        Set<String> ids = new LinkedHashSet<>();
        if (team != null) {
            for (CleaningTeam.Member member : team.getMembers()) {
                if (member == null || member.getPersonnelId() == null) continue;
                String id = member.getPersonnelId().trim();
                if (!id.isEmpty()) ids.add(id);
            }
            String leaderId = team.getLeaderId();
            if (leaderId != null && !leaderId.trim().isEmpty()) {
                ids.add(leaderId.trim());
            }
        }
        return new ArrayList<>(ids);
    }

    @Nullable
    private static CleaningTeam findByTeamId(@NonNull List<CleaningTeam> teams,
                                             @Nullable String teamId) {
        if (isBlank(teamId)) return null;
        String wanted = teamId.trim();
        for (CleaningTeam team : teams) {
            if (team == null || isBlank(team.getTeamId())) continue;
            if (wanted.equalsIgnoreCase(team.getTeamId().trim())) return team;
        }
        return null;
    }

    private static boolean sameTeam(@Nullable CleaningTeam first, @Nullable CleaningTeam second) {
        if (first == null || second == null) return false;
        if (isBlank(first.getTeamId()) || isBlank(second.getTeamId())) return false;
        return first.getTeamId().trim().equalsIgnoreCase(second.getTeamId().trim());
    }

    /**
     * True when this person can actually be sent.
     *
     * <p>Somebody who is already cleaning elsewhere cannot be picked up for a
     * second job. Somebody already on this report is free: that is the
     * assignment being replaced.
     */
    private static boolean isAssignable(@Nullable Personnel person, @NonNull String reportId) {
        if (person == null || !person.isEnabled() || person.isCleaning()) return false;
        String assignedTo = person.getAssignmentReportId();
        boolean sameReport = assignedTo != null && !assignedTo.trim().isEmpty()
                && reportId.equalsIgnoreCase(assignedTo.trim());
        if (assignedTo != null && !assignedTo.trim().isEmpty() && !sameReport) return false;
        // A cleaning flag or a task without a report pointer still means the
        // person is busy; it may be work started by an older client whose
        // report reference was lost.
        if (!sameReport && person.getCurrentTask() != null
                && !person.getCurrentTask().trim().isEmpty()) return false;
        return true;
    }

    /** Team links can be present on personnel even when a team roster is stale. */
    private static boolean hasTeamAssignment(@Nullable Personnel person) {
        if (person == null) return false;
        if (!isBlank(person.getTeamName())) return true;
        String role = person.getTeamRole();
        if (isBlank(role)) return false;
        String normalizedRole = role.trim().toLowerCase(Locale.ROOT);
        return normalizedRole.contains("team")
                && (normalizedRole.contains("leader") || normalizedRole.contains("member")
                || normalizedRole.contains("driver"));
    }

    /**
     * True when every picked person can actually be sent.
     *
     * <p>The dispatch is only offered when the whole crew is free.
     */
    private static boolean allAssignable(List<String> selectedIds, List<Personnel> people,
                                         String reportId) {
        if (selectedIds == null || selectedIds.isEmpty()) return false;
        for (String selectedId : selectedIds) {
            if (!isAssignable(find(people, selectedId), reportId)) return false;
        }
        return true;
    }

    @Nullable
    private static Personnel find(List<Personnel> people, String personnelId) {
        if (people == null || personnelId == null) return null;
        String wanted = normalize(personnelId);
        for (Personnel person : people) {
            if (person == null) continue;
            if (normalize(person.getPersonnelId()).equals(wanted)) return person;
        }
        return null;
    }

    /** The rule that goes with a scale, as a string resource. */
    private static int scaleRuleFor(String wasteScale) {
        if (Report.SCALE_LARGE.equals(wasteScale)) return R.string.assignment_scale_rule_large;
        if (Report.SCALE_MEDIUM.equals(wasteScale)) return R.string.assignment_scale_rule_medium;
        return R.string.assignment_scale_rule_small;
    }

    /**
     * True when an enabled driver has been assigned to this vehicle by an admin.
     * Vehicles without an assigned driver are excluded from operations and tasks.
     */
    private static boolean hasAssignedDriver(@Nullable Vehicle vehicle,
                                             @Nullable List<Personnel> people) {
        if (vehicle == null || isBlank(vehicle.getVehicleId()) || people == null) {
            return false;
        }
        String wantedId = vehicle.getVehicleId().trim();
        for (Personnel person : people) {
            if (person == null || !person.isEnabled() || !person.isDriver()) continue;
            if (isBlank(person.getAssignedVehicleId())) continue;
            if (wantedId.equalsIgnoreCase(person.getAssignedVehicleId().trim())) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String personnelId) {
        return personnelId == null ? "" : personnelId.trim().toUpperCase(Locale.getDefault());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean matches(Personnel person, String query) {
        return contains(person.getFullName(), query)
                || contains(person.getPersonnelId(), query)
                || contains(person.getUsername(), query)
                || contains(person.getCurrentTask(), query);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(query);
    }

    /** Small alias keeps the network callback readable in this UI class. */
    private interface ReportRepositoryCallback
            extends com.example.ecowatchadmin1.data.repository.ReportRepository.ApiCallback<Report> {
    }
}
