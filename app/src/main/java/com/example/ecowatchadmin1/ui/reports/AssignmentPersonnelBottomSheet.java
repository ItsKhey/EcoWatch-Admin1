package com.example.ecowatchadmin1.ui.reports;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
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
import com.example.ecowatchadmin1.data.models.Vehicle;
import com.example.ecowatchadmin1.data.repository.CleaningTeamRepository;
import com.example.ecowatchadmin1.data.repository.PersonnelRepository;
import com.example.ecowatchadmin1.data.repository.VehicleRepository;
import com.example.ecowatchadmin1.utils.WasteScaleDisplay;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Searchable, server-backed personnel picker used by the existing report detail
 * dialog. Only active accounts returned by the admin endpoint are rendered;
 * disabled/deleted accounts are filtered again defensively in the client.
 *
 * <p>The sheet is shaped by the report's waste scale, because that is the rule
 * the dispatch follows: a small job goes to one cleaner, a medium one to a
 * cleaning team, and a large one to a cleaning team with a collection vehicle.
 * The scale was chosen by the resident when the report was filed, so the sheet
 * states it and adapts rather than letting the administrator pick it here.
 */
public final class AssignmentPersonnelBottomSheet {

    /** The smallest crew the server accepts for a team dispatch. */
    private static final int MINIMUM_CREW_SIZE = 2;

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
        RecyclerView list = content.findViewById(R.id.assignmentPersonnelList);
        View loading = content.findViewById(R.id.assignmentLoading);
        TextView empty = content.findViewById(R.id.assignmentEmpty);
        MaterialButton confirm = content.findViewById(R.id.assignmentConfirm);
        TextView title = content.findViewById(R.id.assignmentSheetTitle);
        TextView subtitle = content.findViewById(R.id.assignmentSheetSubtitle);
        TextView scaleBanner = content.findViewById(R.id.assignmentScaleBanner);
        TextView teamLabel = content.findViewById(R.id.assignmentTeamLabel);
        Spinner teamSpinner = content.findViewById(R.id.assignmentTeamSpinner);
        TextView vehicleLabel = content.findViewById(R.id.assignmentVehicleLabel);
        Spinner vehicleSpinner = content.findViewById(R.id.assignmentVehicleSpinner);

        // The scale decides the shape of the dispatch, so it is read once and
        // drives everything below.
        final String wasteScale = report.getWasteScale();
        final boolean crewMode = report.requiresTeam();
        final boolean vehicleMode = report.requiresVehicle();

        String reportId = report.getReportId();
        if (reportId == null || reportId.trim().isEmpty()) {
            reportId = report.getMongoId();
        }
        final String stableReportId = reportId;
        // A crew leaves the single-personnel pointer empty, so the assignment
        // list is what says whether anybody has been sent yet.
        boolean reassignment = report.hasAssignedPersonnel();
        title.setText(reassignment ? R.string.reassign_personnel : R.string.assign_personnel);
        subtitle.setText(R.string.assignment_sheet_subtitle);
        scaleBanner.setText(fragment.getString(
                R.string.assignment_scale_banner,
                WasteScaleDisplay.labelFor(fragment.requireContext(), wasteScale),
                fragment.getString(scaleRuleFor(wasteScale))));
        if (report.getAdminResponse() != null) {
            response.setText(report.getAdminResponse());
        }
        // Prefill the note the admin gave the cleaner previously so a
        // reassignment round-trips it instead of quietly dropping it.
        if (report.getPersonnelNote() != null) {
            personnelNote.setText(report.getPersonnelNote());
        }

        teamLabel.setVisibility(crewMode ? View.VISIBLE : View.GONE);
        teamSpinner.setVisibility(crewMode ? View.VISIBLE : View.GONE);
        vehicleLabel.setVisibility(vehicleMode ? View.VISIBLE : View.GONE);
        vehicleSpinner.setVisibility(vehicleMode ? View.VISIBLE : View.GONE);

        AssignmentPersonnelAdapter adapter =
                new AssignmentPersonnelAdapter(stableReportId, null, crewMode);
        list.setLayoutManager(new LinearLayoutManager(fragment.requireContext()));
        list.setAdapter(adapter);
        if (!crewMode && reassignment) {
            adapter.setSelectedId(report.getAssignedPersonnelId());
        }

        final List<Personnel> source = new ArrayList<>();
        final List<CleaningTeam> teams = new ArrayList<>();
        final List<Vehicle> vehicles = new ArrayList<>();
        // The people on the chosen team. A crew has to be made of them, so the
        // list is narrowed to exactly these before anybody can be picked.
        final Set<String> teamMemberIds = new LinkedHashSet<>();
        final String[] query = {""};
        final boolean[] requestFinished = {false};

        Runnable render = () -> {
            String normalized = query[0] == null
                    ? "" : query[0].trim().toLowerCase(Locale.getDefault());
            List<Personnel> filtered = new ArrayList<>();
            for (Personnel person : source) {
                if (person == null || !person.isEnabled()) continue;
                if (crewMode && !teamMemberIds.isEmpty()
                        && !teamMemberIds.contains(normalize(person.getPersonnelId()))) {
                    continue;
                }
                if (normalized.isEmpty() || matches(person, normalized)) filtered.add(person);
            }
            adapter.submit(filtered);

            List<String> selected = adapter.getSelectedIds();
            boolean ready = true;
            if (crewMode) {
                ready = !teamMemberIds.isEmpty()
                        && selected.size() >= MINIMUM_CREW_SIZE
                        && allAssignable(selected, source, stableReportId);
            } else {
                ready = selected.size() == 1
                        && allAssignable(selected, source, stableReportId);
            }
            if (vehicleMode) {
                ready = ready && selectedVehicle(vehicleSpinner, vehicles) != null;
            }
            confirm.setEnabled(ready);
            if (!ready) searchLayout.setError(null);

            boolean waitingForTeam = crewMode && teamMemberIds.isEmpty();
            if (waitingForTeam) {
                empty.setVisibility(View.VISIBLE);
                empty.setText(fragment.getString(R.string.assignment_choose_team));
                list.setVisibility(View.GONE);
            } else {
                empty.setVisibility(filtered.isEmpty() && requestFinished[0]
                        ? View.VISIBLE : View.GONE);
                list.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
            }
        };
        adapter.setListener(person -> render.run());

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

        // The people who can be sent. Loaded first so the crew can be checked
        // against them before the administrator picks anybody.
        loading.setVisibility(View.VISIBLE);
        new PersonnelRepository(fragment.requireActivity().getApplication())
                .getPersonnel(new PersonnelRepository.PersonnelListCallback() {
                    @Override
                    public void onSuccess(List<Personnel> people) {
                        if (!dialog.isShowing() || !fragment.isAdded()) return;
                        source.clear();
                        if (people != null) source.addAll(people);
                        requestFinished[0] = true;
                        loading.setVisibility(View.GONE);
                        render.run();
                    }

                    @Override
                    public void onError(String message) {
                        if (!dialog.isShowing() || !fragment.isAdded()) return;
                        loading.setVisibility(View.GONE);
                        empty.setVisibility(View.VISIBLE);
                        empty.setText(message == null || message.trim().isEmpty()
                                ? fragment.getString(R.string.assignment_no_personnel) : message);
                    }
                });

        // The crews that can be sent.
        if (crewMode) {
            new CleaningTeamRepository(fragment.requireActivity().getApplication())
                    .getTeams(new CleaningTeamRepository.TeamListCallback() {
                        @Override
                        public void onSuccess(List<CleaningTeam> result) {
                            if (!dialog.isShowing() || !fragment.isAdded()) return;
                            teams.clear();
                            if (result != null) teams.addAll(result);
                            fillTeamSpinner(fragment, teamSpinner, teams);
                        }

                        @Override
                        public void onError(String message) {
                            if (!dialog.isShowing() || !fragment.isAdded()) return;
                            empty.setVisibility(View.VISIBLE);
                            empty.setText(message);
                        }
                    });

            teamSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position,
                                           long id) {
                    if (position <= 0 || position > teams.size()) {
                        // The first row is the "choose a team" placeholder.
                        teamMemberIds.clear();
                        adapter.setSelectedIds(new ArrayList<>());
                        render.run();
                        return;
                    }
                    loadTeamMembers(fragment, dialog, teams.get(position - 1),
                            teamMemberIds, adapter, render);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }

        // The trucks that can be sent.
        if (vehicleMode) {
            new VehicleRepository(fragment.requireActivity().getApplication())
                    .getFleet(new VehicleRepository.FleetCallback() {
                        @Override
                        public void onSuccess(VehicleRepository.FleetSnapshot snapshot) {
                            if (!dialog.isShowing() || !fragment.isAdded()) return;
                            vehicles.clear();
                            if (snapshot != null && snapshot.getVehicles() != null) {
                                vehicles.addAll(snapshot.getVehicles());
                            }
                            fillVehicleSpinner(fragment, vehicleSpinner, vehicles, stableReportId);
                            render.run();
                        }

                        @Override
                        public void onError(String message) {
                            if (!dialog.isShowing() || !fragment.isAdded()) return;
                            empty.setVisibility(View.VISIBLE);
                            empty.setText(message);
                        }
                    });
        }

        confirm.setOnClickListener(v -> {
            List<String> selected = adapter.getSelectedIds();
            if (crewMode && selected.size() < MINIMUM_CREW_SIZE) {
                searchLayout.setError(fragment.getString(R.string.assignment_minimum_crew));
                return;
            }
            if (!crewMode && selected.isEmpty()) {
                searchLayout.setError(fragment.getString(R.string.assignment_select_personnel));
                return;
            }
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

            String teamId = crewMode ? selectedTeamId(teamSpinner, teams) : null;
            String vehicleId = vehicleMode
                    ? selectedVehicleId(vehicleSpinner, vehicles) : null;

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
     * Reads the crew the administrator picked and loads the people on it.
     *
     * <p>The people who go out have to be members of that crew, so the list is
     * narrowed to them and they are pre-ticked: the administrator is confirming
     * a dispatch, not assembling one name at a time.
     */
    private static void loadTeamMembers(@NonNull Fragment fragment,
                                         @NonNull BottomSheetDialog dialog,
                                         @NonNull CleaningTeam team,
                                         @NonNull Set<String> teamMemberIds,
                                         @NonNull AssignmentPersonnelAdapter adapter,
                                         @NonNull Runnable render) {
        new CleaningTeamRepository(fragment.requireActivity().getApplication())
                .getTeam(team.getTeamId(), new CleaningTeamRepository.TeamCallback() {
                    @Override
                    public void onSuccess(CleaningTeam loaded) {
                        if (!dialog.isShowing() || !fragment.isAdded()) return;
                        List<String> memberIds = new ArrayList<>();
                        if (loaded != null) {
                            for (CleaningTeam.Member member : loaded.getMembers()) {
                                String personnelId = member.getPersonnelId();
                                if (personnelId == null || personnelId.trim().isEmpty()) continue;
                                memberIds.add(personnelId.trim());
                            }
                        }
                        teamMemberIds.clear();
                        teamMemberIds.addAll(normalizeAll(memberIds));
                        adapter.setSelectedIds(memberIds);
                        render.run();
                    }

                    @Override
                    public void onError(String message) {
                        if (!dialog.isShowing() || !fragment.isAdded()) return;
                        teamMemberIds.clear();
                        adapter.setSelectedIds(new ArrayList<>());
                        render.run();
                        Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** Fills the crew picker. The first row is a placeholder, not a team. */
    private static void fillTeamSpinner(@NonNull Fragment fragment, @NonNull Spinner spinner,
                                        @NonNull List<CleaningTeam> teams) {
        List<String> labels = new ArrayList<>();
        labels.add(fragment.getString(R.string.assignment_team_placeholder));
        for (CleaningTeam team : teams) {
            labels.add(team.getDisplayName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                fragment.requireContext(),
                android.R.layout.simple_spinner_item,
                labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    /**
     * Fills the truck picker.
     *
     * <p>A truck under maintenance cannot go out, and one already on another
     * collection cannot be sent to a second one, so both are left out. A truck
     * already on this report stays, because that is the one being replaced.
     */
    private static void fillVehicleSpinner(@NonNull Fragment fragment, @NonNull Spinner spinner,
                                            @NonNull List<Vehicle> vehicles,
                                            @NonNull String reportId) {
        List<String> labels = new ArrayList<>();
        labels.add(fragment.getString(R.string.assignment_vehicle_placeholder));
        for (Vehicle vehicle : vehicles) {
            if (vehicle == null) continue;
            if (vehicle.isInMaintenance()) continue;
            boolean onAnotherReport = vehicle.getCurrentReportId() != null
                    && !vehicle.getCurrentReportId().trim().isEmpty()
                    && !vehicle.getCurrentReportId().trim().equalsIgnoreCase(reportId);
            if (onAnotherReport) continue;
            labels.add(vehicleLabel(vehicle));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                fragment.requireContext(),
                android.R.layout.simple_spinner_item,
                labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private static String vehicleLabel(Vehicle vehicle) {
        String plate = vehicle.getPlateNumber();
        String type = vehicle.getVehicleType();
        StringBuilder label = new StringBuilder();
        if (plate != null && !plate.trim().isEmpty()) {
            label.append(plate.trim());
        } else if (vehicle.getVehicleId() != null) {
            label.append(vehicle.getVehicleId().trim());
        }
        if (type != null && !type.trim().isEmpty()) {
            if (label.length() > 0) label.append(" · ");
            label.append(type.trim());
        }
        return label.toString();
    }

    /** The crew the administrator picked, or null when none was chosen. */
    private static String selectedTeamId(Spinner spinner, List<CleaningTeam> teams) {
        int position = spinner.getSelectedItemPosition();
        if (position <= 0 || position > teams.size()) return null;
        return teams.get(position - 1).getTeamId();
    }

    /** The truck the administrator picked, or null when none was chosen. */
    private static String selectedVehicleId(Spinner spinner, List<Vehicle> vehicles) {
        Vehicle vehicle = selectedVehicle(spinner, vehicles);
        return vehicle == null ? null : vehicle.getVehicleId();
    }

    /**
     * The truck behind the chosen row.
     *
     * <p>The spinner rows are built by skipping the trucks that cannot go out,
     * so the rows and the vehicle list do not line up one for one; the labels
     * are matched instead.
     */
    private static Vehicle selectedVehicle(Spinner spinner, List<Vehicle> vehicles) {
        Object selected = spinner.getSelectedItem();
        if (selected == null) return null;
        String label = selected.toString();
        for (Vehicle vehicle : vehicles) {
            if (vehicle == null) continue;
            if (vehicleLabel(vehicle).equals(label)) return vehicle;
        }
        return null;
    }

    /**
     * True when every picked person can actually be sent.
     *
     * <p>Somebody who is already cleaning elsewhere cannot be picked up for a
     * second job, so the dispatch is only offered when the whole crew is free.
     */
    private static boolean allAssignable(List<String> selectedIds, List<Personnel> people,
                                         String reportId) {
        if (selectedIds == null || selectedIds.isEmpty()) return false;
        for (String selectedId : selectedIds) {
            Personnel person = find(people, selectedId);
            if (person == null) return false;
            if (!person.isEnabled() || person.isCleaning()) return false;
            String assignedTo = person.getAssignmentReportId();
            boolean sameReport = assignedTo != null && !assignedTo.trim().isEmpty()
                    && reportId != null && reportId.equalsIgnoreCase(assignedTo.trim());
            if (assignedTo != null && !assignedTo.trim().isEmpty() && !sameReport) {
                return false;
            }
            // A cleaning flag or a task without a report pointer still means
            // the person is busy; it may be work started by an older client
            // whose report reference was lost.
            if (!sameReport && person.getCurrentTask() != null
                    && !person.getCurrentTask().trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

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

    private static String normalize(String personnelId) {
        return personnelId == null ? "" : personnelId.trim().toUpperCase(Locale.getDefault());
    }

    private static List<String> normalizeAll(List<String> personnelIds) {
        List<String> normalized = new ArrayList<>();
        if (personnelIds == null) return normalized;
        for (String personnelId : personnelIds) {
            if (personnelId == null) continue;
            String trimmed = personnelId.trim();
            if (!trimmed.isEmpty()) normalized.add(normalize(trimmed));
        }
        return normalized;
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
