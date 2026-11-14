package com.example.ecowatchadmin1.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.models.Vehicle;
import com.example.ecowatchadmin1.data.repository.AuthRepository;
import com.example.ecowatchadmin1.data.repository.PersonnelRepository;
import com.example.ecowatchadmin1.data.repository.PossibleDuplicateRepository;
import com.example.ecowatchadmin1.data.repository.VehicleRepository;
import com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup;
import com.example.ecowatchadmin1.ui.reports.ReportDetailDialog;
import com.example.ecowatchadmin1.ui.reports.ReportsViewModel;
import com.example.ecowatchadmin1.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final int MAX_PENDING_PREVIEW = 5;
    private static final int MAX_ONGOING_PREVIEW = 5;
    private static final int MAX_UNDER_REVIEW_PREVIEW = 5;
    private static final String PENDING_ROW_ICON = "📝";
    private static final String PENDING_ROW_TITLE = "Report Submitted";
    private static final String UNDER_REVIEW_ROW_ICON = "🔍";
    private static final String UNDER_REVIEW_ROW_TITLE = "Waiting for Review";

    private SwipeRefreshLayout swipeRefreshLayout;
    private ReportsViewModel reportsViewModel;
    private AuthRepository authRepository;
    private PossibleDuplicateRepository possibleDuplicateRepository;
    private PersonnelRepository personnelRepository;
    private VehicleRepository vehicleRepository;
    private LinearLayout ongoingCleaningList;
    private LinearLayout pendingReportsList;
    private LinearLayout underReviewList;
    private TextView pendingEmptyText, ongoingCleaningEmptyText, underReviewEmptyText;

    // Report statistics (counted per report)
    private TextView pendingReportsCount, inProgressReportsCount, completedTodayCount;
    private TextView completedReportsCount, totalReportsCount;
    private TextView deniedReportsCount, busyPersonnelsCount, totalPersonnelsCount;
    private TextView availablePersonnelsCount;
    // Waste collection fleet cards, counted from the vehicles endpoint.
    private TextView totalVehiclesCount, availableVehiclesCount, assignedVehiclesCount;
    private TextView underMaintenanceCount, lacksDriverCount;
    // Rollup cards; counts come from the report feed or the duplicate review.
    private TextView resolvedPercentageCount, possibleDuplicatesCount;

    /**
     * Last report list handed to us by the ViewModel. Kept so the periodic tick
     * can re-evaluate the day-sensitive cards, which must roll over at midnight
     * even when the underlying data never changes.
     */
    private List<Report> latestReports;

    /**
     * Last fleet and roster handed to us by the repositories. Both lists are
     * kept because the "Lacks Driver" card pairs a truck with its driver, and
     * the two requests finish in either order.
     */
    private List<Vehicle> latestVehicles;
    private List<Personnel> latestPersonnel;

    // Real-time refresh handler
    private final android.os.Handler statsRefreshHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable statsRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAdded() && getView() != null) {
                // Recount the report cards so "Today" resets when the day ends.
                updateReportStatistics(latestReports);
                loadPossibleDuplicateCount();
                loadPersonnelCount();
                loadVehicleCounts();
            }
            statsRefreshHandler.postDelayed(this, 10000); // Refresh every 10 seconds
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        ongoingCleaningList = view.findViewById(R.id.ongoingCleaningList);
        ongoingCleaningEmptyText = view.findViewById(R.id.ongoingCleaningEmptyText);
        pendingReportsList = view.findViewById(R.id.pendingReportsList);
        pendingEmptyText = view.findViewById(R.id.pendingEmptyText);
        underReviewList = view.findViewById(R.id.underReviewList);
        underReviewEmptyText = view.findViewById(R.id.underReviewEmptyText);

        // Report statistics views
        pendingReportsCount = view.findViewById(R.id.pendingReportsCount);
        inProgressReportsCount = view.findViewById(R.id.inProgressReportsCount);
        completedTodayCount = view.findViewById(R.id.completedTodayCount);
        completedReportsCount = view.findViewById(R.id.completedReportsCount);
        totalReportsCount = view.findViewById(R.id.totalReportsCount);
        deniedReportsCount = view.findViewById(R.id.deniedReportsCount);
        busyPersonnelsCount = view.findViewById(R.id.busyPersonnelsCount);
        totalPersonnelsCount = view.findViewById(R.id.totalPersonnelsCount);
        availablePersonnelsCount = view.findViewById(R.id.availablePersonnelsCount);

        // Waste collection fleet statistics views
        totalVehiclesCount = view.findViewById(R.id.totalVehiclesCount);
        availableVehiclesCount = view.findViewById(R.id.availableVehiclesCount);
        assignedVehiclesCount = view.findViewById(R.id.assignedVehiclesCount);
        underMaintenanceCount = view.findViewById(R.id.underMaintenanceCount);
        lacksDriverCount = view.findViewById(R.id.lacksDriverCount);

        // Rollup views
        resolvedPercentageCount = view.findViewById(R.id.resolvedPercentageCount);
        possibleDuplicatesCount = view.findViewById(R.id.possibleDuplicatesCount);

        // Card click listeners
        view.findViewById(R.id.cardPendingReports).setOnClickListener(v -> navigateToReportsWithFilter("Pending"));
        view.findViewById(R.id.cardInProgressReports).setOnClickListener(v -> navigateToReportsWithFilter("In Progress"));
        view.findViewById(R.id.cardCompletedToday).setOnClickListener(v -> navigateToReportsWithFilter("Completed"));
        view.findViewById(R.id.cardCompletedReports).setOnClickListener(v -> navigateToReportsWithFilter("Completed"));
        view.findViewById(R.id.cardTotalReports).setOnClickListener(v -> navigateToReportsWithFilter(null));
        view.findViewById(R.id.cardDeniedReports).setOnClickListener(v -> navigateToReportsWithFilter("Denied"));
        view.findViewById(R.id.cardBusyPersonnels).setOnClickListener(v -> navigateToPersonnel());
        view.findViewById(R.id.cardTotalPersonnels).setOnClickListener(v -> navigateToPersonnel());
        view.findViewById(R.id.cardAvailablePersonnels).setOnClickListener(v -> navigateToPersonnel());
        view.findViewById(R.id.cardResolvedPercentage).setOnClickListener(v -> navigateToReportsWithFilter("Completed"));
        // Possible Duplicates card: the reports tabs decide what gets reviewed.
        view.findViewById(R.id.cardPossibleDuplicates).setOnClickListener(v -> navigateToReportsWithFilter(null));
        // Fleet cards open the Vehicles section of the management screen.
        view.findViewById(R.id.cardTotalVehicles).setOnClickListener(v -> navigateToVehicles());
        view.findViewById(R.id.cardAvailableVehicles).setOnClickListener(v -> navigateToVehicles());
        view.findViewById(R.id.cardAssignedVehicles).setOnClickListener(v -> navigateToVehicles());
        view.findViewById(R.id.cardUnderMaintenanceVehicles).setOnClickListener(v -> navigateToVehicles());
        view.findViewById(R.id.cardLacksDriverVehicles).setOnClickListener(v -> navigateToVehicles());

        setupQuickActions(view);

        view.findViewById(R.id.btnViewPending).setOnClickListener(v -> {
            if (getActivity() instanceof DashboardActivity) {
                ((DashboardActivity) getActivity()).navigateToPendingReports();
            }
        });

        View btnViewOngoing = view.findViewById(R.id.btnViewOngoingCleaningOperation);
        if (btnViewOngoing != null) {
            btnViewOngoing.setOnClickListener(v -> {
                if (getActivity() instanceof DashboardActivity) {
                    ((DashboardActivity) getActivity()).navigateToInProgressReports();
                }
            });
        }

        View btnViewUnderReview = view.findViewById(R.id.btnViewUnderReview);
        if (btnViewUnderReview != null) {
            btnViewUnderReview.setOnClickListener(v -> navigateToReportsWithFilter("Under Review"));
        }

        reportsViewModel = new ViewModelProvider(this).get(ReportsViewModel.class);
        authRepository = new AuthRepository(requireActivity().getApplication());
        possibleDuplicateRepository = new PossibleDuplicateRepository(requireActivity().getApplication());
        personnelRepository = new PersonnelRepository(requireActivity().getApplication());
        vehicleRepository = new VehicleRepository(requireActivity().getApplication());

        reportsViewModel.getAllReports().observe(getViewLifecycleOwner(), this::updateUI);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            reportsViewModel.refreshReports();
            authRepository.fetchMe(null);
            loadPossibleDuplicateCount();
            loadPersonnelCount();
            loadVehicleCounts();
            swipeRefreshLayout.postDelayed(() -> swipeRefreshLayout.setRefreshing(false), 3000);
        });

        reportsViewModel.refreshReports();
        loadPossibleDuplicateCount();
        loadPersonnelCount();
        loadVehicleCounts();

        // Start real-time refresh
        statsRefreshHandler.removeCallbacks(statsRefreshRunnable);
        statsRefreshHandler.postDelayed(statsRefreshRunnable, 10000);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        statsRefreshHandler.removeCallbacks(statsRefreshRunnable);
    }

    private void setupQuickActions(@NonNull View view) {
        bindQuickAction(
                view.findViewById(R.id.quickActionReportsandAnalytics),
                "📊",
                getString(R.string.quick_action_reports_analytics),
                getString(R.string.quick_action_reports_analytics_desc),
                v -> {
                    if (getActivity() instanceof DashboardActivity) {
                        ((DashboardActivity) getActivity()).openReportsAnalytics();
                    }
                });

        bindQuickAction(
                view.findViewById(R.id.quickActionManagePersonnelAccounts),
                "👷",
                getString(R.string.quick_action_manage_personnel),
                getString(R.string.quick_action_manage_personnel_desc),
                v -> {
                    if (getActivity() instanceof DashboardActivity) {
                        ((DashboardActivity) getActivity()).navigateToPersonnel();
                    }
                });

        bindQuickAction(
                view.findViewById(R.id.quickActionMap),
                "🗺️",
                getString(R.string.quick_action_open_map),
                getString(R.string.quick_action_open_map_desc),
                v -> {
                    if (getActivity() instanceof DashboardActivity) {
                        ((DashboardActivity) getActivity()).openMap();
                    }
                });

        // Checked against DashboardActivity rather than assumed, because the
        // fragment can be torn down before the tap is delivered. A cast that fails
        // here would crash the dashboard rather than ignore a dead tap.
        bindQuickAction(
                view.findViewById(R.id.quickActionManageReporterAccounts),
                "👥",
                getString(R.string.quick_action_manage_reporters),
                getString(R.string.quick_action_manage_reporters_desc),
                v -> {
                    if (getActivity() instanceof DashboardActivity) {
                        ((DashboardActivity) getActivity()).openReporterAccounts();
                    }
                });

        bindQuickAction(
                view.findViewById(R.id.quickActionRegisterNewESPAccount),
                "➕",
                getString(R.string.quick_action_register_personnel),
                getString(R.string.quick_action_register_personnel_desc),
                v -> {
                    if (getActivity() instanceof DashboardActivity) {
                        ((DashboardActivity) getActivity()).registerNewPersonnel();
                    }
                });
    }

    private void bindQuickAction(@Nullable View row, @NonNull String icon,
                                 @NonNull String title, @NonNull String description,
                                 @NonNull View.OnClickListener listener) {
        if (row == null) return;

        TextView iconView = row.findViewById(R.id.quickActionIcon);
        if (iconView != null) iconView.setText(icon);

        TextView titleView = row.findViewById(R.id.quickActionTitle);
        if (titleView != null) titleView.setText(title);

        TextView descriptionView = row.findViewById(R.id.quickActionDescription);
        if (descriptionView != null) descriptionView.setText(description);

        row.setOnClickListener(listener);
    }

    /**
     * Counts the dashboard cards straight from the individual reports.
     * <p>
     * Every card here is a report counter, so a report is counted once and only
     * against the status it actually has:
     * <ul>
     *     <li>Pending &amp; In Progress &mdash; not archived, not deleted</li>
     *     <li>Today &mdash; completed and resolved during the current day</li>
     *     <li>Completed &mdash; every completed report, all time</li>
     *     <li>Reports &mdash; every report that has not been trashed</li>
     *     <li>Denied &mdash; denied reports, not deleted</li>
     * </ul>
     * Every card counts individual reports, so no grouping can inflate more than
     * one of them with the same underlying report.
     */
    private void updateReportStatistics(List<Report> reports) {
        int pending = 0;
        int inProgress = 0;
        int completedToday = 0;
        int completedTotal = 0;
        int totalReports = 0;
        int denied = 0;

        if (reports != null) {
            for (Report report : reports) {
                if (report == null) continue;
                if (report.isDeleted()) continue;

                // Every report that is still in the system counts as a report.
                totalReports++;

                String status = report.getStatus();

                if ("Pending".equalsIgnoreCase(status)) {
                    if (!report.isArchived()) pending++;
                } else if ("In Progress".equalsIgnoreCase(status)) {
                    // Matches the Reports screen "In Progress" tab this card opens.
                    if (!report.isArchived()) inProgress++;
                } else if ("Completed".equalsIgnoreCase(status)) {
                    completedTotal++;
                    if (isCompletedToday(report)) completedToday++;
                } else if ("Denied".equalsIgnoreCase(status)) {
                    denied++;
                }
            }
        }

        if (pendingReportsCount != null) {
            pendingReportsCount.setText(String.valueOf(pending));
        }
        if (inProgressReportsCount != null) {
            inProgressReportsCount.setText(String.valueOf(inProgress));
        }
        if (completedTodayCount != null) {
            completedTodayCount.setText(String.valueOf(completedToday));
        }
        if (completedReportsCount != null) {
            completedReportsCount.setText(String.valueOf(completedTotal));
        }
        if (totalReportsCount != null) {
            totalReportsCount.setText(String.valueOf(totalReports));
        }
        if (deniedReportsCount != null) {
            deniedReportsCount.setText(String.valueOf(denied));
        }
    }

    /**
     * A report counts towards "Today" when it was resolved during the current
     * day. {@code resolvedAt} is the canonical marker, with {@code updatedAt}
     * used as a fallback for records where the server omitted it.
     */
    private boolean isCompletedToday(Report report) {
        String resolvedAt = report.getResolvedAt();
        if (resolvedAt == null || resolvedAt.trim().isEmpty()) {
            resolvedAt = report.getUpdatedAt();
        }
        return isToday(resolvedAt);
    }

    /**
     * Counts reports the administrator still needs to review as possibly
     * describing the same incident. Keeps the dashboard rollup honest without
     * exposing the cluster concept.
     */
    private void loadPossibleDuplicateCount() {
        if (possibleDuplicateRepository == null) return;

        possibleDuplicateRepository.getPossibleDuplicates(
                new PossibleDuplicateRepository.GroupsCallback() {
                    @Override
                    public void onSuccess(List<PossibleDuplicateGroup> groups) {
                        if (getView() == null) return;
                        if (possibleDuplicatesCount != null) {
                            possibleDuplicatesCount.setText(String.valueOf(
                                    groups == null ? 0 : groups.size()));
                        }
                    }

                    @Override
                    public void onError(String error) {
                        if (getView() == null) return;
                        Log.w(TAG, "Possible duplicates could not load: " + error);
                        if (possibleDuplicatesCount != null) {
                            possibleDuplicatesCount.setText("0");
                        }
                    }
                });
    }

    /** Resolved percentage is completed reports over all active reports. */
    private void updateResolvedPercentage() {
        if (resolvedPercentageCount == null || latestReports == null) return;
        long total = latestReports.stream().filter(r -> !r.isDeleted()).count();
        long completed = latestReports.stream()
                .filter(r -> !r.isDeleted() && "Completed".equalsIgnoreCase(r.getStatus()))
                .count();
        int pct = total > 0 ? (int) Math.round(completed * 100.0 / total) : 0;
        resolvedPercentageCount.setText(pct + "%");
    }


    private boolean isToday(String timestamp) {
        return DateTimeUtils.isToday(timestamp);
    }

    private void navigateToReportsWithFilter(String status) {
        if (getActivity() instanceof DashboardActivity) {
            ((DashboardActivity) getActivity()).navigateToReportsWithFilter(status);
        }
    }

    private void navigateToPersonnel() {
        if (getActivity() instanceof DashboardActivity) {
            ((DashboardActivity) getActivity()).navigateToPersonnel();
        }
    }

    private void navigateToVehicles() {
        if (getActivity() instanceof DashboardActivity) {
            ((DashboardActivity) getActivity()).openVehicles();
        }
    }

    private void updateUI(List<Report> reports) {
        latestReports = reports;
        updatePendingAssignment(reports);
        updateOngoingCleaning(reports);
        updateUnderReview(reports);
        updateReportStatistics(reports);
        updateResolvedPercentage();
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
    }

    /**
     * Loads the ESP cards from the personnel endpoint.
     *
     * <p>"Available" is the rest of the active accounts: a cleaner on a report
     * counts as assigned, everyone else is available. Keeping all three numbers
     * in one callback means the cards cannot disagree with each other.
     */
    private void loadPersonnelCount() {
        if (personnelRepository == null) return;
        personnelRepository.getPersonnel(new PersonnelRepository.PersonnelListCallback() {
            @Override
            public void onSuccess(List<Personnel> personnel) {
                if (getView() == null) return;
                latestPersonnel = personnel;
                int total = personnel == null ? 0 : personnel.size();
                int assigned = 0;
                if (personnel != null) {
                    for (Personnel person : personnel) {
                        if (person != null && person.isCleaning()) assigned++;
                    }
                }
                int available = Math.max(0, total - assigned);

                if (totalPersonnelsCount != null) {
                    totalPersonnelsCount.setText(String.valueOf(total));
                }
                if (busyPersonnelsCount != null) {
                    busyPersonnelsCount.setText(String.valueOf(assigned));
                }
                if (availablePersonnelsCount != null) {
                    availablePersonnelsCount.setText(String.valueOf(available));
                }

                // The "Lacks Driver" card needs the roster as well as the fleet,
                // so repaint the fleet cards now that this half has arrived.
                renderFleetCounts();
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Personnel count could not load: " + error);
            }
        });
    }

    /**
     * Loads the waste collection fleet cards.
     *
     * <p>Counted from the fleet list rather than the server's summary object so
     * total is always the same set of trucks the Vehicles screen lists. A truck
     * in maintenance is included in the total but is neither available nor
     * assigned, matching the fleet screen's own cards.
     */
    private void loadVehicleCounts() {
        if (vehicleRepository == null) return;
        vehicleRepository.getFleet(new VehicleRepository.FleetCallback() {
            @Override
            public void onSuccess(VehicleRepository.FleetSnapshot snapshot) {
                if (getView() == null) return;
                latestVehicles = snapshot == null ? null : snapshot.getVehicles();
                renderFleetCounts();
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Vehicle fleet could not load: " + error);
            }
        });
    }

    /**
     * Paints the fleet cards from the two lists the dashboard holds.
     *
     * <p>Each truck is counted once: a truck under maintenance is in the total
     * but neither available nor assigned. "Lacks Driver" counts the trucks no
     * enabled driver account is assigned to, which is the same pairing the
     * Vehicles screen uses for its badge, so the number matches the rows an
     * administrator sees after tapping through.
     */
    private void renderFleetCounts() {
        List<Vehicle> vehicles = latestVehicles;
        if (vehicles != null) {
            int total = 0;
            int available = 0;
            int assigned = 0;
            int maintenance = 0;
            for (Vehicle vehicle : vehicles) {
                if (vehicle == null) continue;
                total++;
                if (vehicle.isAssigned()) assigned++;
                else if (vehicle.isAvailable()) available++;
                else if (vehicle.isInMaintenance()) maintenance++;
            }

            if (totalVehiclesCount != null) {
                totalVehiclesCount.setText(String.valueOf(total));
            }
            if (availableVehiclesCount != null) {
                availableVehiclesCount.setText(String.valueOf(available));
            }
            if (assignedVehiclesCount != null) {
                assignedVehiclesCount.setText(String.valueOf(assigned));
            }
            if (underMaintenanceCount != null) {
                underMaintenanceCount.setText(String.valueOf(maintenance));
            }
        }

        // Both halves are needed to pair a truck with a driver. Until they have
        // both arrived the card keeps its previous number rather than claiming
        // every truck is driverless.
        if (vehicles != null && latestPersonnel != null && lacksDriverCount != null) {
            lacksDriverCount.setText(String.valueOf(
                    countVehiclesWithoutDriver(vehicles, latestPersonnel)));
        }
    }

    /**
     * Counts trucks with no enabled driver assigned to them.
     *
     * <p>Mirrors {@code VehicleAdapter.setDrivers}: only an enabled driver with
     * a vehicle on the account holds that truck, and the match ignores case
     * because the two endpoints do not guarantee the same spelling.
     */
    private int countVehiclesWithoutDriver(@NonNull List<Vehicle> vehicles,
                                           @NonNull List<Personnel> personnel) {
        Set<String> drivenVehicleIds = new HashSet<>();
        for (Personnel person : personnel) {
            if (person == null || !person.isEnabled()) continue;
            if (!person.isDriver() || !person.hasAssignedVehicle()) continue;
            String vehicleId = person.getAssignedVehicleId();
            if (vehicleId == null || vehicleId.trim().isEmpty()) continue;
            drivenVehicleIds.add(vehicleId.trim().toUpperCase(Locale.ROOT));
        }

        int withoutDriver = 0;
        for (Vehicle vehicle : vehicles) {
            if (vehicle == null) continue;
            String vehicleId = vehicle.getVehicleId();
            String key = vehicleId == null ? "" : vehicleId.trim().toUpperCase(Locale.ROOT);
            if (!drivenVehicleIds.contains(key)) withoutDriver++;
        }
        return withoutDriver;
    }

    private void updatePendingAssignment(List<Report> reports) {
        if (pendingReportsList == null) return;
        pendingReportsList.removeAllViews();

        List<Report> waiting = new ArrayList<>();
        if (reports != null) {
            for (Report report : reports) {
                if (report == null) continue;
                if (report.isDeleted() || report.isArchived()) continue;
                if ("Pending".equalsIgnoreCase(report.getStatus())) {
                    waiting.add(report);
                }
            }
        }

        Collections.sort(waiting, (r1, r2) -> {
            String c1 = r1.getCreatedAt();
            String c2 = r2.getCreatedAt();
            if (c1 == null && c2 == null) return 0;
            if (c1 == null) return 1;
            if (c2 == null) return -1;
            return c1.compareTo(c2);
        });

        if (pendingEmptyText != null) {
            pendingEmptyText.setVisibility(waiting.isEmpty() ? View.VISIBLE : View.GONE);
        }

        int count = Math.min(waiting.size(), MAX_PENDING_PREVIEW);
        for (int i = 0; i < count; i++) {
            Report report = waiting.get(i);
            View row = addReportRow(pendingReportsList,
                    PENDING_ROW_ICON,
                    PENDING_ROW_TITLE,
                    reportTitle(report) + " report was pending.",
                    formatDateTime(report.getCreatedAt()));

            row.setOnClickListener(v -> {
                if (!isAdded()) return;
                ReportDetailDialog.show(this, report,
                        () -> reportsViewModel.refreshReports());
            });
        }
    }

    private View addReportRow(LinearLayout container, String icon, String title,
                              String description, String time) {
        View itemView = getLayoutInflater().inflate(R.layout.item_recent_activity,
                container, false);

        ((TextView) itemView.findViewById(R.id.activityStatusIcon)).setText(icon);
        ((TextView) itemView.findViewById(R.id.activityTitle)).setText(title);
        ((TextView) itemView.findViewById(R.id.activityDescription)).setText(description);
        ((TextView) itemView.findViewById(R.id.activityTime)).setText(time);

        container.addView(itemView);
        return itemView;
    }

    private String reportTitle(Report report) {
        String title = report == null ? null : report.getTitle();
        return title == null || title.trim().isEmpty() ? "Untitled report" : title;
    }

    private void updateOngoingCleaning(List<Report> reports) {
        if (ongoingCleaningList == null) return;
        ongoingCleaningList.removeAllViews();

        List<Report> ongoing = new ArrayList<>();
        if (reports != null) {
            for (Report report : reports) {
                if (report == null) continue;
                if (report.isDeleted() || report.isArchived()) continue;
                if (isOngoingCleaning(report.getStatus())) ongoing.add(report);
            }
        }

        Collections.sort(ongoing, (r1, r2) -> {
            String s1 = r1.getAssignedAt();
            String s2 = r2.getAssignedAt();
            if (s1 == null && s2 == null) return 0;
            if (s1 == null) return 1;
            if (s2 == null) return -1;
            return s1.compareTo(s2);
        });

        if (ongoingCleaningEmptyText != null) {
            ongoingCleaningEmptyText.setVisibility(ongoing.isEmpty() ? View.VISIBLE : View.GONE);
        }

        int count = Math.min(ongoing.size(), MAX_ONGOING_PREVIEW);
        for (int i = 0; i < count; i++) {
            Report report = ongoing.get(i);
            View card = getLayoutInflater().inflate(R.layout.item_ongoing_cleaning,
                    ongoingCleaningList, false);

            ((TextView) card.findViewById(R.id.ongoingTitle))
                    .setText(reportTitle(report));
            ((TextView) card.findViewById(R.id.ongoingReportId))
                    .setText(reportIdentifier(report));
            ((TextView) card.findViewById(R.id.ongoingStatus))
                    .setText(ongoingStatusLabel(report.getStatus()));
            ((TextView) card.findViewById(R.id.ongoingAssignedTo))
                    .setText(assignedCleanerLabel(report));
            ((TextView) card.findViewById(R.id.ongoingStartedAt))
                    .setText(formatCleaningStart(report.getAssignedAt()));
            ((TextView) card.findViewById(R.id.ongoingLocation))
                    .setText(formatCoordinates(report));

            card.setOnClickListener(v -> {
                if (!isAdded() || !(getActivity() instanceof DashboardActivity)) return;
                ((DashboardActivity) getActivity()).openMapForReport(report);
            });

            ongoingCleaningList.addView(card);
        }
    }

    private void updateUnderReview(List<Report> reports) {
        if (underReviewList == null) return;
        underReviewList.removeAllViews();

        List<Report> waiting = new ArrayList<>();
        if (reports != null) {
            for (Report report : reports) {
                if (report == null) continue;
                if (report.isDeleted() || report.isArchived()) continue;
                if ("Under Review".equalsIgnoreCase(report.getStatus())) {
                    waiting.add(report);
                }
            }
        }

        Collections.sort(waiting, (r1, r2) -> {
            String c1 = r1.getResolvedAt() != null ? r1.getResolvedAt() : r1.getUpdatedAt();
            String c2 = r2.getResolvedAt() != null ? r2.getResolvedAt() : r2.getUpdatedAt();
            if (c1 == null && c2 == null) return 0;
            if (c1 == null) return 1;
            if (c2 == null) return -1;
            return c1.compareTo(c2);
        });

        if (underReviewEmptyText != null) {
            underReviewEmptyText.setVisibility(waiting.isEmpty() ? View.VISIBLE : View.GONE);
        }

        int count = Math.min(waiting.size(), MAX_UNDER_REVIEW_PREVIEW);
        for (int i = 0; i < count; i++) {
            Report report = waiting.get(i);
            String timestamp = report.getResolvedAt() != null
                    ? report.getResolvedAt() : report.getUpdatedAt();
            View row = addReportRow(underReviewList,
                    UNDER_REVIEW_ROW_ICON,
                    UNDER_REVIEW_ROW_TITLE,
                    reportTitle(report) + " needs verification.",
                    formatDateTime(timestamp));

            row.setOnClickListener(v -> {
                if (!isAdded()) return;
                ReportDetailDialog.show(this, report,
                        () -> reportsViewModel.refreshReports());
            });
        }
    }

    private static boolean isOngoingCleaning(String status) {
        return "In Progress".equalsIgnoreCase(status)
                || "Assigned".equalsIgnoreCase(status);
    }

    private static String ongoingStatusLabel(String status) {
        return status == null || status.trim().isEmpty() ? "In Progress" : status.trim();
    }

    private String reportIdentifier(Report report) {
        String reportId = report == null ? null : report.getReportId();
        if (reportId == null || reportId.trim().isEmpty()) {
            reportId = report == null ? null : report.getMongoId();
        }
        return reportId == null || reportId.trim().isEmpty()
                ? "Unknown" : reportId.trim();
    }

    private String assignedCleanerLabel(Report report) {
        if (report == null) return "Unassigned";

        String name = report.getAssignedPersonnelName();
        if (name != null && !name.trim().isEmpty()) return name.trim();

        String personnelId = report.getAssignedPersonnelId();
        if (personnelId != null && !personnelId.trim().isEmpty()) return personnelId.trim();

        return "Unassigned";
    }

    private String formatCoordinates(Report report) {
        if (report == null) return "No coordinates";
        if (!isValidCoordinate(report.getLatitude(), report.getLongitude())) {
            return "No coordinates";
        }
        return String.format(Locale.US, "%.4f, %.4f",
                report.getLatitude(), report.getLongitude());
    }

    private static boolean isValidCoordinate(double latitude, double longitude) {
        return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180
                && !(latitude == 0 && longitude == 0);
    }

    private String formatDateTime(String timestamp) {
        if (timestamp == null) return "Just now";
        return DateTimeUtils.formatRelativeTime(timestamp);
    }

    private String formatCleaningStart(String assignedAt) {
        if (assignedAt == null || assignedAt.trim().isEmpty()) {
            return "Not yet recorded";
        }
        String formatted = formatDateTime(assignedAt.trim());
        return "Unknown".equals(formatted) ? assignedAt.trim() : formatted;
    }
}
