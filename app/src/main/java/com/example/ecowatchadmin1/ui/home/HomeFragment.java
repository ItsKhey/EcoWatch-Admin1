package com.example.ecowatchadmin1.ui.home;

import android.content.Intent;
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
import com.example.ecowatchadmin1.data.models.Announcement;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.repository.AnnouncementRepository;
import com.example.ecowatchadmin1.data.repository.AuthRepository;
import com.example.ecowatchadmin1.data.repository.PersonnelRepository;
import com.example.ecowatchadmin1.data.repository.PossibleDuplicateRepository;
import com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup;
import com.example.ecowatchadmin1.ui.announcements.AnnouncementRowBinder;
import com.example.ecowatchadmin1.ui.announcements.CreateAnnouncementActivity;
import com.example.ecowatchadmin1.ui.reports.ReportDetailDialog;
import com.example.ecowatchadmin1.ui.reports.ReportsViewModel;
import com.example.ecowatchadmin1.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final int MAX_PENDING_PREVIEW = 5;
    private static final int MAX_ONGOING_PREVIEW = 5;
    private static final int MAX_UNDER_REVIEW_PREVIEW = 5;
    private static final int MAX_ANNOUNCEMENT_PREVIEW = 3;
    private static final String PENDING_ROW_ICON = "📝";
    private static final String PENDING_ROW_TITLE = "Report Submitted";
    private static final String UNDER_REVIEW_ROW_ICON = "🔍";
    private static final String UNDER_REVIEW_ROW_TITLE = "Waiting for Review";

    private SwipeRefreshLayout swipeRefreshLayout;
    private ReportsViewModel reportsViewModel;
    private AuthRepository authRepository;
    private PossibleDuplicateRepository possibleDuplicateRepository;
    private PersonnelRepository personnelRepository;
    private LinearLayout ongoingCleaningList;
    private LinearLayout pendingReportsList;
    private LinearLayout underReviewList;
    private TextView pendingEmptyText, ongoingCleaningEmptyText, underReviewEmptyText;
    private LinearLayout announcementList;
    private TextView announcementEmptyText;
    private AnnouncementRepository announcementRepository;

    // Report statistics (counted per report)
    private TextView pendingReportsCount, inProgressReportsCount, completedTodayCount;
    private TextView completedReportsCount, totalReportsCount;
    private TextView deniedReportsCount, busyPersonnelsCount, totalPersonnelsCount;
    // Rollup cards; counts come from the report feed or the duplicate review.
    private TextView resolvedPercentageCount, possibleDuplicatesCount;

    /**
     * Last report list handed to us by the ViewModel. Kept so the periodic tick
     * can re-evaluate the day-sensitive cards, which must roll over at midnight
     * even when the underlying data never changes.
     */
    private List<Report> latestReports;

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
        announcementList = view.findViewById(R.id.announcementList);
        announcementEmptyText = view.findViewById(R.id.announcementEmptyText);

        // Report statistics views
        pendingReportsCount = view.findViewById(R.id.pendingReportsCount);
        inProgressReportsCount = view.findViewById(R.id.inProgressReportsCount);
        completedTodayCount = view.findViewById(R.id.completedTodayCount);
        completedReportsCount = view.findViewById(R.id.completedReportsCount);
        totalReportsCount = view.findViewById(R.id.totalReportsCount);
        deniedReportsCount = view.findViewById(R.id.deniedReportsCount);
        busyPersonnelsCount = view.findViewById(R.id.busyPersonnelsCount);
        totalPersonnelsCount = view.findViewById(R.id.totalPersonnelsCount);

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
        view.findViewById(R.id.cardResolvedPercentage).setOnClickListener(v -> navigateToReportsWithFilter("Completed"));
        // Possible Duplicates card: the reports tabs decide what gets reviewed.
        view.findViewById(R.id.cardPossibleDuplicates).setOnClickListener(v -> navigateToReportsWithFilter(null));

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
        announcementRepository =
                new AnnouncementRepository(requireActivity().getApplication());

        reportsViewModel.getAllReports().observe(getViewLifecycleOwner(), this::updateUI);

        View btnReadMore = view.findViewById(R.id.btnReadMore);
        if (btnReadMore != null) {
            btnReadMore.setOnClickListener(v -> {
                if (getActivity() instanceof DashboardActivity) {
                    ((DashboardActivity) getActivity()).navigateToAnnouncements();
                }
            });
        }

        swipeRefreshLayout.setOnRefreshListener(() -> {
            reportsViewModel.refreshReports();
            authRepository.fetchMe(null);
            loadAnnouncements();
            loadPossibleDuplicateCount();
            swipeRefreshLayout.postDelayed(() -> swipeRefreshLayout.setRefreshing(false), 3000);
        });

        reportsViewModel.refreshReports();
        loadAnnouncements();
        loadPossibleDuplicateCount();
        loadPersonnelCount();

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
                view.findViewById(R.id.quickActionAnnouncement),
                "📢",
                getString(R.string.quick_action_create_announcement),
                getString(R.string.quick_action_create_announcement_desc),
                v -> {
                    if (!isAdded()) return;
                    startActivity(new Intent(requireContext(), CreateAnnouncementActivity.class));
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

    private void updateUI(List<Report> reports) {
        latestReports = reports;
        updatePendingAssignment(reports);
        updateOngoingCleaning(reports);
        updateUnderReview(reports);
        updateReportStatistics(reports);
        updatePersonnelCounts(reports);
        updateResolvedPercentage();
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
    }

    private void updatePersonnelCounts(List<Report> reports) {
        int busy = 0;
        if (reports != null) {
            for (Report report : reports) {
                if (report != null && report.hasAssignment()) {
                    busy++;
                }
            }
        }
        if (busyPersonnelsCount != null) {
            busyPersonnelsCount.setText(String.valueOf(busy));
        }
        // totalPersonnelsCount is set by loadPersonnelCount() from the server
    }

    /**
     * Loads the total personnel count from the server.
     */
    private void loadPersonnelCount() {
        if (personnelRepository == null) return;
        personnelRepository.getPersonnel(new PersonnelRepository.PersonnelListCallback() {
            @Override
            public void onSuccess(List<com.example.ecowatchadmin1.data.models.Personnel> personnel) {
                if (getView() == null) return;
                int total = personnel == null ? 0 : personnel.size();
                if (totalPersonnelsCount != null) {
                    totalPersonnelsCount.setText(String.valueOf(total));
                }
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Personnel count could not load: " + error);
            }
        });
    }

    private void loadAnnouncements() {
        if (announcementRepository == null) return;

        announcementRepository.getAnnouncements(
                new AnnouncementRepository.AnnouncementListCallback() {
                    @Override
                    public void onSuccess(List<Announcement> announcements) {
                        if (getView() == null) return;
                        renderAnnouncements(announcements);
                    }

                    @Override
                    public void onError(String error) {
                        if (getView() == null) return;
                        Log.w(TAG, "Your Announcements card could not load: " + error);
                        renderAnnouncements(null);
                    }
                });
    }

    private void renderAnnouncements(List<Announcement> announcements) {
        if (announcementList == null) return;
        announcementList.removeAllViews();

        int total = announcements == null ? 0 : announcements.size();
        if (announcementEmptyText != null) {
            announcementEmptyText.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
        }
        if (total == 0) return;

        int count = Math.min(total, MAX_ANNOUNCEMENT_PREVIEW);
        for (int i = 0; i < count; i++) {
            Announcement announcement = announcements.get(i);
            if (announcement == null) continue;

            View row = getLayoutInflater().inflate(
                    R.layout.item_announcement, announcementList, false);
            AnnouncementRowBinder.bind(row, announcement);

            View deleteView = row.findViewById(R.id.announcementDelete);
            if (deleteView != null) deleteView.setVisibility(View.GONE);

            announcementList.addView(row);
        }
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
