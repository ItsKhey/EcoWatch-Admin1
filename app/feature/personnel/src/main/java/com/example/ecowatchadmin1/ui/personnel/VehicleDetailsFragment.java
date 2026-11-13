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
import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.models.ReportPage;
import com.example.ecowatchadmin1.data.models.Vehicle;
import com.example.ecowatchadmin1.data.repository.PersonnelRepository;
import com.example.ecowatchadmin1.data.repository.VehicleRepository;
import com.example.ecowatchadmin1.ui.reports.ReportDetailDialog;
import com.example.ecowatchadmin1.ui.residents.ReporterReportAdapter;
import com.example.ecowatchadmin1.utils.AdminAccountDateFormatter;
import com.example.ecowatchadmin1.utils.SessionManager;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/** Full-screen vehicle details, opened from the fleet list. */
public final class VehicleDetailsFragment extends Fragment {
    private static final String ARG_VEHICLE_ID = "vehicle_id";

    private VehicleViewModel viewModel;
    private String vehicleId;
    private Vehicle current;
    private View details;
    private View loading;
    private TextView error;
    private TextView title;
    private TextView subtitle;
    private TextView assignedPersonnel;
    private TextView assignedPersonnelId;
    private TextView status;
    private TextView totalAssignedTasks;
    private TextView tasksOnHand;
    private TextView tasksInProgress;
    private TextView tasksCompleted;
    private RecyclerView historyRecyclerView;
    private TextView historyEmpty;
    private MaterialButton historyLoadMore;
    private TextView type;
    private TextView ratedLoad;
    private TextView lastActivity;
    private TextView created;
    private View collectionCard;
    private TextView collection;
    private TextView collectionMeta;
    private View notesCard;
    private TextView notes;
    private MaterialButton edit;
    private MaterialButton remove;
    private MaterialButton markUnderMaintenance;
    private MaterialButton markFunctional;
    private ReporterReportAdapter historyAdapter;
    private final List<Report> vehicleHistory = new ArrayList<>();
    private int historyPage;
    private boolean historyHasMore;
    private boolean historyLoading;
    private boolean fleetRequestStarted;
    private boolean personnelRosterLoaded;
    private boolean personnelRosterFailed;
    private List<Personnel> personnelRoster;

    public static VehicleDetailsFragment newInstance(String vehicleId) {
        VehicleDetailsFragment fragment = new VehicleDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_VEHICLE_ID, vehicleId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.screen_vehicle_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.vehicleDetailsBack).setOnClickListener(v ->
                getParentFragmentManager().popBackStack());
        FrameLayout content = view.findViewById(R.id.vehicleDetailsContent);
        details = LayoutInflater.from(requireContext())
                .inflate(R.layout.fragment_vehicle_details, content, false);
        content.addView(details, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        details.findViewById(R.id.vehicleDetailClose).setVisibility(View.GONE);

        loading = new android.widget.ProgressBar(requireContext());
        content.addView(loading, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER));
        error = new TextView(requireContext());
        error.setGravity(android.view.Gravity.CENTER);
        error.setTextColor(requireContext().getColor(R.color.personnel_offline));
        error.setVisibility(View.GONE);
        content.addView(error, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        title = details.findViewById(R.id.vehicleDetailTitle);
        title.setTextSize(18);
        subtitle = details.findViewById(R.id.vehicleDetailSubtitle);
        assignedPersonnel = details.findViewById(R.id.vehicleDetailAssignedPersonnel);
        assignedPersonnelId = details.findViewById(R.id.vehicleDetailAssignedPersonnelId);
        status = details.findViewById(R.id.vehicleDetailStatus);
        totalAssignedTasks = details.findViewById(R.id.totalAssignedReport);
        tasksOnHand = details.findViewById(R.id.reporterStatOnhand);
        tasksInProgress = details.findViewById(R.id.reporterStatInProgress);
        tasksCompleted = details.findViewById(R.id.reporterStatCompleted);
        type = details.findViewById(R.id.vehicleDetailType);
        ratedLoad = details.findViewById(R.id.vehicleDetailRatedLoad);
        lastActivity = details.findViewById(R.id.vehicleDetailLastService);
        created = details.findViewById(R.id.vehicleDetailCreated);
        collectionCard = details.findViewById(R.id.vehicleDetailCollectionCard);
        collection = details.findViewById(R.id.vehicleDetailCollection);
        collectionMeta = details.findViewById(R.id.vehicleDetailCollectionMeta);
        notesCard = details.findViewById(R.id.vehicleDetailNotesCard);
        notes = details.findViewById(R.id.vehicleDetailNotes);
        edit = details.findViewById(R.id.vehicleDetailEdit);
        remove = details.findViewById(R.id.vehicleDetailRemove);
        markUnderMaintenance = details.findViewById(R.id.vehicleMarkUnderMaintenance);
        markFunctional = details.findViewById(R.id.vehicleMarkFunctional);
        historyRecyclerView = details.findViewById(R.id.vehicleHistoryRecyclerView);
        historyEmpty = details.findViewById(R.id.vehicleHistoryEmpty);
        historyLoadMore = details.findViewById(R.id.vehicleHistoryLoadMore);

        vehicleId = getArguments() == null ? null : getArguments().getString(ARG_VEHICLE_ID);
        if (!new SessionManager(requireContext()).isAdmin() || isBlank(vehicleId)) {
            showError(getString(R.string.personnel_error_admin_only));
            return;
        }
        viewModel = new ViewModelProvider(this).get(VehicleViewModel.class);
        loadVehicleTaskCounts();
        setupTaskHistory();
        loadVehicleTaskHistory(false);
        edit.setOnClickListener(v -> {
            if (current == null) return;
            VehicleDialogs.showEditor(requireContext(), current, viewModel,
                    (updated, failure) -> {
                        if (failure != null) {
                            Toast.makeText(requireContext(), failure, Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (updated != null) bindVehicle(updated);
                        viewModel.refreshFleet();
                    });
        });
        remove.setOnClickListener(v -> {
            if (current == null) return;
            VehicleDialogs.confirmRemoval(requireContext(), current, viewModel,
                    (removed, failure) -> {
                        if (failure == null && removed == null && isAdded()) {
                            getParentFragmentManager().popBackStack();
                        }
                    });
        });
        markUnderMaintenance.setOnClickListener(v -> updateStatus(Vehicle.STATUS_MAINTENANCE));
        markFunctional.setOnClickListener(v -> updateStatus(Vehicle.STATUS_AVAILABLE));
        viewModel.getVehicles().observe(getViewLifecycleOwner(), vehicles -> {
            if (vehicles == null) return;
            for (Vehicle vehicle : vehicles) {
                if (vehicle != null && vehicleId.equalsIgnoreCase(vehicle.getVehicleId())) {
                    bindVehicle(vehicle);
                    return;
                }
            }
            if (current == null && !vehicles.isEmpty()) {
                loading.setVisibility(View.GONE);
                showError(getString(R.string.vehicle_not_found));
            }
        });
        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message != null && isAdded()) {
                loading.setVisibility(View.GONE);
                if (current == null) showError(message);
                else Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (fleetRequestStarted && !Boolean.TRUE.equals(isLoading) && current == null
                    && isAdded()) {
                loading.setVisibility(View.GONE);
                showError(getString(R.string.vehicle_not_found));
            }
        });
        assignedPersonnel.setText(R.string.vehicle_assignee_loading);
        new PersonnelRepository(requireActivity().getApplication())
                .getPersonnel(new PersonnelRepository.PersonnelListCallback() {
                    @Override public void onSuccess(List<Personnel> people) {
                        if (!isAdded()) return;
                        personnelRoster = people;
                        personnelRosterLoaded = true;
                        personnelRosterFailed = false;
                        if (current != null) bindAssignedPersonnel(current);
                        backfillVehicleHistoryTeam();
                    }

                    @Override public void onError(String message) {
                        if (!isAdded()) return;
                        personnelRosterLoaded = true;
                        personnelRosterFailed = true;
                        if (current != null) bindAssignedPersonnel(current);
                    }
                });
        fleetRequestStarted = true;
        viewModel.loadFleet();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null && current != null) {
            viewModel.refreshFleet();
            loadVehicleTaskCounts();
            loadVehicleTaskHistory(false);
        }
    }

    private void setupTaskHistory() {
        historyAdapter = new ReporterReportAdapter();
        // This log expands each job into its cleaning events, like the
        // personnel history; the other shared lists stay compact.
        historyAdapter.setShowTimeline(true);
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        historyRecyclerView.setAdapter(historyAdapter);
        historyAdapter.setListener(report -> ReportDetailDialog.showReadOnly(this, report));
        historyLoadMore.setOnClickListener(v -> loadVehicleTaskHistory(true));
    }

    private void loadVehicleTaskHistory(boolean append) {
        if (viewModel == null || isBlank(vehicleId) || historyLoading
                || (append && !historyHasMore)) return;
        historyLoading = true;
        historyLoadMore.setEnabled(false);
        int requestedPage = append ? historyPage + 1 : 1;
        viewModel.loadVehicleTaskHistory(vehicleId, requestedPage, 30,
                new VehicleRepository.VehicleTaskHistoryCallback() {
                    @Override
                    public void onSuccess(ReportPage result) {
                        if (!isAdded()) return;
                        ReportPage page = result == null ? new ReportPage() : result;
                        if (!append) vehicleHistory.clear();
                        Personnel driver = findDriver();
                        if (page.getReports() != null) {
                            for (Report report : page.getReports()) {
                                if (report == null) continue;
                                if (driver != null && !isBlank(driver.getTeamName())
                                        && isBlank(report.getAssignedTeamLabel())) {
                                    report.setAssignedTeamName(driver.getTeamName());
                                }
                                vehicleHistory.add(report);
                            }
                        }
                        historyPage = Math.max(1, page.getPage());
                        historyHasMore = page.hasMore();
                        historyLoading = false;
                        historyAdapter.setReports(vehicleHistory);
                        historyEmpty.setVisibility(vehicleHistory.isEmpty() ? View.VISIBLE : View.GONE);
                        historyLoadMore.setVisibility(historyHasMore ? View.VISIBLE : View.GONE);
                        historyLoadMore.setEnabled(true);
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

    private void loadVehicleTaskCounts() {
        if (viewModel == null || isBlank(vehicleId)) return;
        viewModel.loadVehicleTaskCounts(vehicleId,
                new VehicleRepository.VehicleTaskCountsCallback() {
                    @Override
                    public void onSuccess(com.example.ecowatchadmin1.data.models.PersonnelTaskCounts counts) {
                        if (!isAdded() || counts == null) return;
                        totalAssignedTasks.setText(String.valueOf(counts.getTotalAssigned()));
                        tasksOnHand.setText(String.valueOf(counts.getOnHand()));
                        tasksInProgress.setText(String.valueOf(counts.getInProgress()));
                        tasksCompleted.setText(String.valueOf(counts.getCompleted()));
                    }

                    @Override
                    public void onError(String message) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void bindVehicle(Vehicle vehicle) {
        current = vehicle;
        loading.setVisibility(View.GONE);
        error.setVisibility(View.GONE);
        String plateNumber = isBlank(vehicle.getPlateNumber())
                ? getString(R.string.vehicle_plate_unknown) : vehicle.getPlateNumber();
        title.setText(plateNumber);
        subtitle.setText(isBlank(vehicle.getVehicleId()) ? "" : vehicle.getVehicleId());
        status.setText(vehicle.getStatusLabel());
        status.setTextColor(requireContext().getColor(vehicle.isAssigned()
                ? R.color.vehicle_assigned : vehicle.isInMaintenance()
                ? R.color.vehicle_maintenance : R.color.vehicle_available));
        // MaterialButton applies its disabled color state automatically. An
        // assigned vehicle cannot enter maintenance until its active job ends.
        boolean canMarkUnderRepair = !vehicle.isInMaintenance() && !vehicle.isAssigned();
        boolean canMarkFunctional = vehicle.isInMaintenance();
        markUnderMaintenance.setEnabled(canMarkUnderRepair);
        markUnderMaintenance.setAlpha(canMarkUnderRepair ? 1f : 0.45f);
        markFunctional.setEnabled(canMarkFunctional);
        markFunctional.setAlpha(canMarkFunctional ? 1f : 0.45f);
        type.setText(isBlank(vehicle.getVehicleType())
                ? getString(R.string.vehicle_type_unknown) : vehicle.getVehicleType());
        bindAssignedPersonnel(vehicle);
        String load = vehicle.getRatedLoadLabel();
        ratedLoad.setText(load == null ? "—" : load);
        lastActivity.setText(formatTimestamp(vehicle.getLastActivityAt(),
                getString(R.string.vehicle_no_activity)));
        created.setText(formatTimestamp(vehicle.getCreatedAt(), "—"));

        String collectionName = vehicle.getCurrentCollection();
        collectionCard.setVisibility(collectionName == null ? View.GONE : View.VISIBLE);
        if (collectionName != null) {
            collection.setText(collectionName);
            StringBuilder meta = new StringBuilder();
            if (!isBlank(vehicle.getCurrentReportId())) meta.append(vehicle.getCurrentReportId());
            if (!isBlank(vehicle.getCurrentReportStatus())) {
                if (meta.length() > 0) meta.append(" · ");
                meta.append(vehicle.getCurrentReportStatus());
            }
            collectionMeta.setText(meta.toString());
            collectionMeta.setVisibility(meta.length() == 0 ? View.GONE : View.VISIBLE);
        }
        String vehicleNotes = vehicle.getNotes();
        notesCard.setVisibility(isBlank(vehicleNotes) ? View.GONE : View.VISIBLE);
        if (!isBlank(vehicleNotes)) notes.setText(vehicleNotes);
    }

    private void updateStatus(String nextStatus) {
        if (current == null || viewModel == null) return;
        boolean toMaintenance = Vehicle.STATUS_MAINTENANCE.equalsIgnoreCase(nextStatus);
        if (toMaintenance && (current.isInMaintenance() || current.isAssigned())) return;
        if (!toMaintenance && current.isAvailable()) return;

        markUnderMaintenance.setEnabled(false);
        markFunctional.setEnabled(false);
        viewModel.updateVehicle(current.getVehicleId(), current.getPlateNumber(),
                current.getVehicleType(), current.getRatedLoadCapacity(), nextStatus,
                current.getCurrentReportId(), current.getNotes(), (updated, failure) -> {
                    if (!isAdded()) return;
                    if (failure != null) {
                        Toast.makeText(requireContext(), failure, Toast.LENGTH_LONG).show();
                        bindVehicle(current);
                        return;
                    }
                    if (updated != null) bindVehicle(updated);
                    Toast.makeText(requireContext(), toMaintenance
                            ? R.string.vehicle_marked_under_repair
                            : R.string.vehicle_marked_functional, Toast.LENGTH_SHORT).show();
                    viewModel.refreshFleet();
                });
    }

    private void bindAssignedPersonnel(Vehicle vehicle) {
        if (!personnelRosterLoaded) {
            assignedPersonnel.setText(R.string.vehicle_assignee_loading);
            assignedPersonnelId.setVisibility(View.GONE);
            return;
        }
        if (personnelRosterFailed) {
            assignedPersonnel.setText(R.string.vehicle_assignee_unavailable);
            assignedPersonnelId.setVisibility(View.GONE);
            return;
        }

        Personnel driver = findDriver();
        if (driver == null) {
            assignedPersonnel.setText(R.string.vehicle_lacks_driver);
            assignedPersonnel.setTextColor(requireContext().getColor(R.color.vehicle_lacks_driver));
            assignedPersonnelId.setVisibility(View.GONE);
            if (current != null && current.isAvailable()) {
                status.setText(R.string.vehicle_lacks_driver);
                status.setTextColor(requireContext().getColor(R.color.vehicle_lacks_driver));
            }
            return;
        }
        assignedPersonnel.setText(driver.getFullName());
        assignedPersonnel.setTextColor(requireContext().getColor(R.color.green));
        if (isBlank(driver.getPersonnelId())) {
            assignedPersonnelId.setVisibility(View.GONE);
        } else {
            assignedPersonnelId.setText(getString(R.string.personnel_id_format,
                    driver.getPersonnelId()));
            assignedPersonnelId.setVisibility(View.VISIBLE);
        }
        backfillVehicleHistoryTeam();
    }

    private Personnel findDriver() {
        String vid = current != null ? current.getVehicleId() : vehicleId;
        if (isBlank(vid) || personnelRoster == null) return null;
        for (Personnel person : personnelRoster) {
            if (person == null || !person.isEnabled() || !person.isDriver()
                    || isBlank(person.getAssignedVehicleId())) continue;
            if (vid.trim().equalsIgnoreCase(person.getAssignedVehicleId().trim())) {
                return person;
            }
        }
        return null;
    }

    private void backfillVehicleHistoryTeam() {
        Personnel driver = findDriver();
        if (driver == null || isBlank(driver.getTeamName()) || vehicleHistory.isEmpty()) return;
        boolean changed = false;
        for (Report report : vehicleHistory) {
            if (report != null && report.requiresTeam() && isBlank(report.getAssignedTeamLabel())) {
                report.setAssignedTeamName(driver.getTeamName());
                changed = true;
            }
        }
        if (changed && historyAdapter != null) {
            historyAdapter.notifyDataSetChanged();
        }
    }

    private String formatTimestamp(String raw, String emptyValue) {
        if (isBlank(raw)) return emptyValue;
        String value = AdminAccountDateFormatter.format(raw);
        return value == null ? raw : value;
    }

    private void showError(String message) {
        error.setText(isBlank(message) ? getString(R.string.vehicle_not_found) : message);
        error.setVisibility(View.VISIBLE);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
