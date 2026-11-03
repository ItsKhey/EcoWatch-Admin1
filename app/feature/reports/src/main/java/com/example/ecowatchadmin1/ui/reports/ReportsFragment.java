package com.example.ecowatchadmin1.ui.reports;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.repository.PossibleDuplicateGrouper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Collectors;

public class ReportsFragment extends Fragment {
    private static final String ARG_OPEN_REPORT_ID = "open_report_id";
    private static final String ARG_INITIAL_TAB = "initial_tab";

    private ReportsViewModel viewModel;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView reportsRecyclerView;
    private ReportAdapter adapter;
    
    private TextView tabAll, tabPending, tabInProgress, tabUnderReview, tabCompleted, tabDenied, tabArchived, tabTrashed;
    private EditText searchEditText;
    private ImageView filterBtn, selectModeBtn;
    private LinearLayout selectionControlLayout, bulkActionsBar;
    private CheckBox selectAllCheckbox;
    private TextView selectionCountText, noReportsText, filterSummaryText;
    private MaterialButton bulkAssignBtn, bulkArchiveBtn, bulkRestoreBtn, bulkDeleteBtn;
    private HorizontalScrollView chipScrollView;
    private ChipGroup activeChipGroup;

    private List<Report> allCurrentReports = new ArrayList<>();
    private String currentView = "All";
    private String selectedStatusFilter = "All";
    private String currentCategoryFilter = "All";
    private String lastReportError;
    private int currentDateFilterId = -1;
    private int currentSortId = R.id.radioNewest;
    private Calendar customFromDate, customToDate;
    /** Report targeted by a notification deep link, opened once the list loads. */
    private String pendingNotificationReportId;
    private boolean pendingReportShown;
    /** Tab requested by the caller, applied once after the first list load. */
    private String requestedInitialTab;

    /** Groups of reports the backend flagged as possibly duplicates. */
    private List<com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup>
            possibleDuplicateGroups = new ArrayList<>();
    private com.example.ecowatchadmin1.data.repository.PossibleDuplicateRepository
            possibleDuplicateRepository;
    /** True while a review-group request is in flight, to avoid stacking them. */
    private boolean duplicateFetchInFlight;
    /** Set while a decision is being applied, to keep the refresh out of the way. */
    private boolean duplicateDecisionInFlight;

    /**
     * Cloned from the Resident app so a tapped NEW_REPORT notification lands
     * directly on the matching Report Details screen.
     */
    public static ReportsFragment forReportId(String reportId) {
        ReportsFragment fragment = new ReportsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_OPEN_REPORT_ID, reportId);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Opens the reports list already filtered to the Pending tab. Used by the
     * "View All" action on the dashboard's Needs Assignment card, so the
     * administrator lands on the full unassigned queue instead of the
     * unfiltered list.
     */
    public static ReportsFragment forPendingList() {
        ReportsFragment fragment = new ReportsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_INITIAL_TAB, "Pending");
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Opens the reports list already filtered to the In Progress tab. Used by the
     * "View All" action on the dashboard's Ongoing Cleaning Operation card.
     */
    public static ReportsFragment forInProgressList() {
        ReportsFragment fragment = new ReportsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_INITIAL_TAB, "In Progress");
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Opens the reports list already filtered to the given status tab. Used by
     * the dashboard statistic cards (Pending, In Progress, Completed, Denied).
     */
    public static ReportsFragment forStatus(String status) {
        ReportsFragment fragment = new ReportsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_INITIAL_TAB, status);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_report, container, false);

        if (getArguments() != null) {
            pendingNotificationReportId = getArguments().getString(ARG_OPEN_REPORT_ID);
        }
        viewModel = new ViewModelProvider(this).get(ReportsViewModel.class);

        // An explicit landing tab is applied after the views are bound, so the
        // tab pills, the adapter and the list all agree on the same view.
        String initialTab = getArguments() == null
                ? null : getArguments().getString(ARG_INITIAL_TAB);
        if (initialTab != null && !initialTab.trim().isEmpty()) {
            requestedInitialTab = initialTab.trim();
        }

        initViews(view);
        setupRecyclerView();
        setupTabs();
        setupSearch();
        setupBulkActions();

        observeViewModel();

        swipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.green, null));
        swipeRefreshLayout.setOnRefreshListener(() -> {
            viewModel.refreshReports();
            Toast.makeText(getContext(), "Syncing...", Toast.LENGTH_SHORT).show();
            // Force hide after 2 seconds if no data change triggered it
            swipeRefreshLayout.postDelayed(() -> {
                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }, 2000);
        });

        viewModel.refreshReports();
        fetchPossibleDuplicates();
        viewModel.cleanupTrash(); // Auto-cleanup on load

        return view;
    }

    private final android.os.Handler autoRefreshHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (viewModel != null) {
                viewModel.refreshReports();
            }
            autoRefreshHandler.postDelayed(this, 10000); // 10 seconds auto-sync
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.refreshReports();
        }
        autoRefreshHandler.postDelayed(autoRefreshRunnable, 10000);
    }

    @Override
    public void onPause() {
        super.onPause();
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }

    private void initViews(View view) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        reportsRecyclerView = view.findViewById(R.id.reportsRecyclerView);
        
        tabAll = view.findViewById(R.id.tabAll);
        tabPending = view.findViewById(R.id.tabPending);
        tabInProgress = view.findViewById(R.id.tabInProgress);
        tabUnderReview = view.findViewById(R.id.tabUnderReview);
        tabCompleted = view.findViewById(R.id.tabCompleted);
        tabDenied = view.findViewById(R.id.tabDenied);
        tabArchived = view.findViewById(R.id.tabArchived);
        tabTrashed = view.findViewById(R.id.tabTrashed);
        
        searchEditText = view.findViewById(R.id.searchEditText);
        filterBtn = view.findViewById(R.id.filterBtn);
        selectModeBtn = view.findViewById(R.id.selectModeBtn);
        selectionControlLayout = view.findViewById(R.id.selectionControlLayout);
        bulkActionsBar = view.findViewById(R.id.bulkActionsBar);
        selectAllCheckbox = view.findViewById(R.id.selectAllCheckbox);
        selectionCountText = view.findViewById(R.id.selectionCountText);
        noReportsText = view.findViewById(R.id.noReportsText);
        filterSummaryText = view.findViewById(R.id.filterSummaryText);
        chipScrollView = view.findViewById(R.id.chipScrollView);
        activeChipGroup = view.findViewById(R.id.activeChipGroup);

        bulkAssignBtn = view.findViewById(R.id.bulkAssignBtn);
        bulkArchiveBtn = view.findViewById(R.id.bulkArchiveBtn);
        bulkRestoreBtn = view.findViewById(R.id.bulkRestoreBtn);
        bulkDeleteBtn = view.findViewById(R.id.bulkDeleteBtn);

        selectModeBtn.setOnClickListener(v -> toggleSelectionMode());
    }

    private void toggleSelectionMode() {
        boolean isNow = !selectionControlLayout.isShown();
        adapter.setSelectionMode(isNow);
        selectionControlLayout.setVisibility(isNow ? View.VISIBLE : View.GONE);
        if (!isNow) {
            updateSelectionUI(0);
        } else {
            selectAllCheckbox.setChecked(false);
            selectionCountText.setText("0 selected");
        }
    }

    private void setupRecyclerView() {
        adapter = new ReportAdapter();
        adapter.setCurrentView("Reports");
        reportsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        reportsRecyclerView.setAdapter(adapter);
        possibleDuplicateRepository = new com.example.ecowatchadmin1.data.repository
                .PossibleDuplicateRepository(requireActivity().getApplication());

        adapter.setOnDuplicateGroupActionListener(new ReportAdapter.DuplicateGroupActionListener() {
            @Override
            public void onPossibleDuplicatesDenied(
                    com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup group) {
                showDenyDuplicateReview(group);
            }

            @Override
            public void onPossibleDuplicatesAccepted(
                    com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup group) {
                showAcceptDuplicateReview(group);
            }

            @Override
            public void onPossibleDuplicatesSeeAll(
                    com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup group) {
                // SEE ALL opens the full comparison without disturbing the card
                // state, so the administrator can look, close it, and still act
                // on the same card.
                PossibleDuplicateDetailDialog.show(requireContext(), group);
            }
        });

        adapter.setOnReportClickListener(new ReportAdapter.OnReportClickListener() {
            @Override
            public void onReportClick(Report report) {
                showReportDetail(report);
            }

            @Override
            public void onLongReportClick(Report report) {
                toggleSelectionMode();
            }

            @Override
            public void onDeleteClick(Report report) {
                boolean isTrash = "Trashed".equalsIgnoreCase(currentView);
                boolean isArchived = "Archived".equalsIgnoreCase(currentView)
                        || "Archive".equalsIgnoreCase(currentView);
                boolean isPermanent = isTrash || isArchived;
                String title = isPermanent ? "Permanent Delete" : "Delete Report";
                String msg = isPermanent
                        ? "Delete this report permanently? This action cannot be undone."
                        : "Move this report to Trash?";

                new AlertDialog.Builder(requireContext())
                        .setTitle(title)
                        .setMessage(msg)
                        .setPositiveButton("Confirm", (d, w) -> {
                            if (isPermanent) {
                                viewModel.deleteReport(report);
                                Toast.makeText(getContext(), "Report permanently removed", Toast.LENGTH_SHORT).show();
                            } else {
                                viewModel.softDeleteReport(report);
                                Toast.makeText(getContext(), "Moved to Trash", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            @Override
            public void onEditClick(Report report) {
                Toast.makeText(getContext(), "Editing is disabled", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onRestoreClick(Report report) {
                viewModel.restoreReport(report);
                Toast.makeText(getContext(), "Report restored", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSelectionChanged(int count) {
                updateSelectionUI(count);
            }
        });
    }

    private void setupTabs() {
        tabAll.setOnClickListener(v -> selectTab("All"));
        tabPending.setOnClickListener(v -> selectTab("Pending"));
        tabInProgress.setOnClickListener(v -> selectTab("In Progress"));
        tabUnderReview.setOnClickListener(v -> selectTab("Under Review"));
        tabCompleted.setOnClickListener(v -> selectTab("Completed"));
        tabDenied.setOnClickListener(v -> selectTab("Denied"));
        tabArchived.setOnClickListener(v -> selectTab("Archived"));
        tabTrashed.setOnClickListener(v -> selectTab("Trashed"));
    }

    private void selectTab(String tabName) {
        currentView = tabName;
        
        // Update UI states
        updateTabUI(tabAll, "All".equals(tabName));
        updateTabUI(tabPending, "Pending".equals(tabName));
        updateTabUI(tabInProgress, "In Progress".equals(tabName));
        updateTabUI(tabUnderReview, "Under Review".equals(tabName));
        updateTabUI(tabCompleted, "Completed".equals(tabName));
        updateTabUI(tabDenied, "Denied".equals(tabName));
        updateTabUI(tabArchived, "Archived".equals(tabName));
        updateTabUI(tabTrashed, "Trashed".equals(tabName));
        
        adapter.setCurrentView(currentView);
        adapter.setSelectionMode(false);
        updateSelectionUI(0);
        updatePossibleDuplicateGroups();
        loadViewData();
    }

    /**
     * The Possible Duplicate Reports card is a review queue, so it belongs on
     * the two tabs where work is still outstanding: All and Pending. On
     * Completed, Denied, Archived and Trashed it must not appear at all,
     * otherwise a resolved or denied report looks like it still needs a
     * decision.
     */
    public static boolean isPossibleDuplicateTab(String tab) {
        return "All".equalsIgnoreCase(tab) || "Pending".equalsIgnoreCase(tab);
    }

    /** Returns how many review cards are now on screen. */
    private int updatePossibleDuplicateGroups() {
        if (adapter == null) return 0;
        List<com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup> visible =
                new ArrayList<>();
        if (!isPossibleDuplicateTab(currentView)) {
            adapter.setPossibleDuplicateGroups(visible);
            return 0;
        }
        for (com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup group
                : possibleDuplicateGroups) {
            // Pending narrows further: a group whose reports were all resolved
            // or denied has nothing left to decide.
            if ("Pending".equalsIgnoreCase(currentView) && group.getPendingReports().isEmpty()) {
                continue;
            }
            visible.add(group);
        }
        adapter.setPossibleDuplicateGroups(visible);
        return visible.size();
    }

    /**
     * Refreshes the review groups.
     *
     * <p>Also re-run after a decision, because the whole point of the feature is
     * that it keeps working: a report submitted afterwards at the same spot
     * must appear again as a fresh review.
     */
    private void fetchPossibleDuplicates() {
        if (possibleDuplicateRepository == null) return;
        // The reports list already polls every ten seconds, so without this the
        // review endpoint would be hit just as often. A decision always forces
        // its own refresh, so skipping during a decision cannot leave stale
        // cards behind.
        if (duplicateDecisionInFlight || duplicateFetchInFlight) return;
        duplicateFetchInFlight = true;
        possibleDuplicateRepository.getPossibleDuplicates(
                new com.example.ecowatchadmin1.data.repository
                        .PossibleDuplicateRepository.GroupsCallback() {
                    @Override
                    public void onSuccess(List<
                            com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup> groups) {
                        duplicateFetchInFlight = false;
                        possibleDuplicateGroups = groups != null
                                ? groups : new ArrayList<>();
                        updatePossibleDuplicateGroups();
                    }

                    @Override
                    public void onError(String error) {
                        duplicateFetchInFlight = false;
                        // Left as-is rather than cleared: a transient failure must
                        // not make reviews disappear, and the next refresh retries.
                        updatePossibleDuplicateGroups();
                    }
                });
    }

    /**
     * Asks which report in the group is the duplicate.
     *
     * <p>Built by hand rather than with {@code setMultiChoiceItems}: that helper
     * draws its tick from the platform's {@code listChoiceIndicatorSingle}, which
     * resolves to an invisible drawable under the app's Material3 DayNight
     * theme, so the dialog opened as a plain list with nothing to tick. It also
     * only accepts text, which meant the administrator had to leave the dialog,
     * press SEE ALL, read, and start over. Each option here is a full row with a
     * real checkbox, the photo, the reporter, the category, the submitted time,
     * the description and the distance from the first report.
     *
     * <p>Selection alone changes nothing. The confirmation dialog still stands
     * between this and the decision.
     */
    private void showDenyDuplicateReview(
            com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup group) {
        final List<Report> reports = group.getReports() == null
                ? new ArrayList<>() : group.getReports();
        if (reports.isEmpty()) return;

        boolean anyDeniable = false;
        for (Report report : reports) {
            if (PossibleDuplicateGrouper.canBeDenied(report)) {
                anyDeniable = true;
                break;
            }
        }
        if (!anyDeniable) {
            Toast.makeText(requireContext(), R.string.possible_duplicate_no_actionable,
                    Toast.LENGTH_LONG).show();
            return;
        }

        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_duplicate_choose, null, false);
        final LinearLayout list = content.findViewById(R.id.duplicateChoiceList);

        // Ticking a row selects that report as a duplicate. Kept in a set keyed
        // by the server id so the confirm step never re-reads the views.
        final List<CheckBox> checkBoxes = new ArrayList<>();
        final Report first = reports.get(0);
        final android.content.Context context = requireContext();

        for (final Report report : reports) {
            View row = LayoutInflater.from(context)
                    .inflate(R.layout.item_duplicate_choice, list, false);

            CheckBox checkBox = row.findViewById(R.id.duplicateChoiceCheck);
            ((TextView) row.findViewById(R.id.duplicateChoiceTitle)).setText(
                    getString(R.string.possible_duplicate_report_reference,
                            DuplicateReviewFormatter.referenceOf(report)));
            ((TextView) row.findViewById(R.id.duplicateChoiceResident))
                    .setText(getString(R.string.possible_duplicate_resident_label,
                            report.getResidentLabel()));
            ((TextView) row.findViewById(R.id.duplicateChoiceMeta))
                    .setText(DuplicateReviewFormatter.submittedLine(context, report));
            ((TextView) row.findViewById(R.id.duplicateChoiceDescription))
                    .setText(report.getDescription() == null
                            || report.getDescription().trim().isEmpty()
                            ? getString(R.string.possible_duplicate_no_description)
                            : report.getDescription());
            ((TextView) row.findViewById(R.id.duplicateChoiceDistance))
                    .setText(DuplicateReviewFormatter.locationLine(context, report, first));

            android.widget.ImageView thumb = row.findViewById(R.id.duplicateChoiceThumb);
            Object imageSource = DuplicateReviewFormatter.photoSourceOf(report, context);
            if (imageSource == null) {
                thumb.setVisibility(View.GONE);
            } else {
                com.bumptech.glide.Glide.with(context)
                        .load(imageSource)
                        .centerCrop()
                        .into(thumb);
            }

            // The whole row is the tap target; the checkbox reflects it. Makes
            // a selection on a small screen possible without hitting a tick.
            // Completed reports stay visible as the evidence behind the group,
            // but their status can no longer change, so they are not tickable.
            boolean deniable = PossibleDuplicateGrouper.canBeDenied(report);
            checkBox.setEnabled(deniable);
            row.setEnabled(deniable);
            row.setAlpha(deniable ? 1f : 0.55f);
            if (deniable) {
                row.setOnClickListener(v -> checkBox.setChecked(!checkBox.isChecked()));
            }

            checkBoxes.add(checkBox);
            list.addView(row);
        }

        final androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(context)
                        .setView(content)
                        .create();

        content.findViewById(R.id.duplicateChoiceCancel)
                .setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.duplicateChoiceContinue)
                .setOnClickListener(v -> {
                    List<String> selectedIds = new ArrayList<>();
                    for (int i = 0; i < reports.size(); i++) {
                        if (checkBoxes.get(i).isChecked()) {
                            String id = DuplicateReviewFormatter.serverIdOf(reports.get(i));
                            if (id != null) selectedIds.add(id);
                        }
                    }
                    if (selectedIds.isEmpty()) {
                        Toast.makeText(context, R.string.possible_duplicate_need_one,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    dialog.dismiss();
                    confirmDuplicateDecision(group, selectedIds);
                });

        dialog.show();
    }

    private void confirmDuplicateDecision(
            com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup group,
            List<String> selectedIds) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.possible_duplicate_deny_title)
                .setMessage(R.string.possible_duplicate_deny_message)
                .setPositiveButton("Confirm", (dialog, which) -> applyDuplicateDecision(
                        group,
                        callback -> possibleDuplicateRepository.markAsDuplicates(
                                group.getGroupId(),
                                selectedIds,
                                callback),
                        R.string.possible_duplicate_denied_done))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Runs a decision and refreshes both lists when it lands.
     *
     * <p>The refresh is deliberately unconditional and ordered: the review list
     * first, so the decided card leaves the screen, then the reports list, so a
     * confirmed duplicate disappears from the active tabs. Both happen in place,
     * with no restart and no manual pull to refresh.
     */
    private void applyDuplicateDecision(
            com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup group,
            java.util.function.Consumer<com.example.ecowatchadmin1.data.repository
                    .PossibleDuplicateRepository.ActionCallback> call,
            int successMessage) {
        duplicateDecisionInFlight = true;
        call.accept(new com.example.ecowatchadmin1.data.repository
                .PossibleDuplicateRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                duplicateDecisionInFlight = false;
                if (isAdded()) {
                    Toast.makeText(getContext(), successMessage, Toast.LENGTH_LONG).show();
                }
                refreshAfterDecision();
            }

            @Override
            public void onError(String error) {
                duplicateDecisionInFlight = false;
                if (isAdded()) {
                    Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /**
     * Pulls the review groups again, bypassing the in-flight guard.
     *
     * <p>The guard exists to stop the ten-second reports poll from stacking
     * duplicate requests. After a decision the refresh is mandatory, so this
     * path resets the flag first and always goes out.
     */
    private void refreshAfterDecision() {
        duplicateFetchInFlight = false;
        fetchPossibleDuplicates();
        if (viewModel != null) viewModel.refreshReports();
        if (adapter != null && searchEditText != null) {
            filterReports(searchEditText.getText().toString());
        }
    }

    private void showAcceptDuplicateReview(
            com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup group) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.possible_duplicate_accept_title)
                .setMessage(R.string.possible_duplicate_accept_message)
                .setPositiveButton("Confirm", (dialog, which) -> applyDuplicateDecision(
                        group,
                        callback -> possibleDuplicateRepository.markAsNotDuplicates(
                                group.getGroupId(),
                                callback),
                        R.string.possible_duplicate_accepted_done))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateTabUI(TextView tab, boolean isActive) {
        if (isActive) {
            tab.setBackgroundResource(R.drawable.active_pill_background);
            tab.setTextColor(getResources().getColor(R.color.white, null));
        } else {
            tab.setBackground(null);
            tab.setTextColor(getResources().getColor(R.color.green, null));
        }
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterReports(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        
        filterBtn.setOnClickListener(v -> showFilterDialog());
    }

    private void setupBulkActions() {
        selectAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) adapter.selectAll();
            else adapter.unselectAll();
        });

        bulkAssignBtn.setOnClickListener(v -> assignSelectedReports());

        bulkArchiveBtn.setOnClickListener(v -> {
            viewModel.bulkArchive(adapter.getSelectedIds());
            exitSelectionMode();
        });

        bulkRestoreBtn.setOnClickListener(v -> {
            viewModel.bulkRestore(adapter.getSelectedIds());
            exitSelectionMode();
        });

        bulkDeleteBtn.setOnClickListener(v -> {
            boolean isTrash = "Trashed".equalsIgnoreCase(currentView);
            String title = isTrash ? "Bulk Permanent Delete" : "Bulk Delete";
            String msg = isTrash ? "Permanently delete selected reports? This cannot be undone." : "Move selected reports to Trash?";
            
            new AlertDialog.Builder(requireContext())
                    .setTitle(title)
                    .setMessage(msg)
                    .setPositiveButton("Confirm", (d, w) -> {
                        if (isTrash) {
                            viewModel.bulkPermanentDelete(adapter.getSelectedIds());
                        } else {
                            viewModel.bulkSoftDelete(adapter.getSelectedIds());
                        }
                        exitSelectionMode();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    /**
     * Opens the personnel picker for the selected report.
     *
     * <p>Assignment is a one-report-at-a-time operation: the backend only
     * exposes {@code POST /reports/:id/assign}, and a cleaner holds a single
     * active assignment, so a multi-selection cannot be dispatched in one go.
     */
    private void assignSelectedReports() {
        List<Report> selected = adapter.getSelectedReports();
        if (selected.isEmpty()) {
            Toast.makeText(getContext(), "Select a report to assign", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selected.size() > 1) {
            Toast.makeText(getContext(),
                    "Assign one report at a time", Toast.LENGTH_SHORT).show();
            return;
        }

        Report target = selected.get(0);
        AssignmentPersonnelBottomSheet.show(this, target, () -> {
            if (isAdded()) {
                Toast.makeText(getContext(), "Personnel assigned", Toast.LENGTH_SHORT).show();
                exitSelectionMode();
            }
        });
    }

    private void exitSelectionMode() {
        adapter.setSelectionMode(false);
        updateSelectionUI(0);
        selectAllCheckbox.setChecked(false);
        selectionControlLayout.setVisibility(View.GONE);
    }

    private void updateSelectionUI(int count) {
        selectionCountText.setText(count + " selected");
        if (count > 0) {
            bulkActionsBar.setVisibility(View.VISIBLE);

            // Assigning is the only administrative step that makes sense before
            // a report has personnel, so it is offered on the Pending tab alone.
            boolean pendingTab = "Pending".equalsIgnoreCase(currentView);
            bulkAssignBtn.setVisibility(pendingTab ? View.VISIBLE : View.GONE);

            if ("Trashed".equalsIgnoreCase(currentView)) {
                bulkArchiveBtn.setVisibility(View.GONE);
                bulkRestoreBtn.setVisibility(View.VISIBLE);
                bulkDeleteBtn.setVisibility(View.VISIBLE); // Permanent delete
            } else if ("Archived".equalsIgnoreCase(currentView)) {
                bulkArchiveBtn.setVisibility(View.GONE);
                bulkRestoreBtn.setVisibility(View.VISIBLE);
                bulkDeleteBtn.setVisibility(View.VISIBLE);
            } else {
                bulkArchiveBtn.setVisibility(View.VISIBLE);
                bulkRestoreBtn.setVisibility(View.GONE);
                bulkDeleteBtn.setVisibility(View.VISIBLE);
            }
        } else {
            bulkActionsBar.setVisibility(View.GONE);
            // Reset the per-tab buttons so a later selection cannot inherit a
            // stale layout from the tab the user was on before.
            bulkAssignBtn.setVisibility(View.GONE);
        }
    }

    private void observeViewModel() {
        viewModel.getLoadingState().observe(getViewLifecycleOwner(), loading -> {
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(Boolean.TRUE.equals(loading));
            }
        });

        viewModel.getReportsError().observe(getViewLifecycleOwner(), error -> {
            if (error == null) {
                lastReportError = null;
            } else if (!error.equals(lastReportError)) {
                lastReportError = error;
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getAllReports().observe(getViewLifecycleOwner(), reports -> {
            if (reports != null) {
                long pending = reports.stream().filter(r -> !r.isArchived() && !r.isDeleted() && "Pending".equalsIgnoreCase(r.getStatus())).count();
                long inProgress = reports.stream().filter(r -> !r.isArchived() && !r.isDeleted() && "In Progress".equalsIgnoreCase(r.getStatus())).count();
                long underReview = reports.stream().filter(r -> !r.isArchived() && !r.isDeleted() && "Under Review".equalsIgnoreCase(r.getStatus())).count();
                long archived = reports.stream().filter(r -> r.isArchived() && !r.isDeleted()).count();
                long trashed = reports.stream().filter(Report::isDeleted).count();
                long denied = reports.stream().filter(r -> !r.isDeleted() && "Denied".equalsIgnoreCase(r.getStatus())).count();
                long completed = reports.stream().filter(r -> !r.isDeleted() && "Completed".equalsIgnoreCase(r.getStatus())).count();

                // Exclude trashed reports completely from the "All" total sum counter
                long nonDeletedCount = reports.stream().filter(r -> !r.isDeleted()).count();

                tabAll.setText("All (" + nonDeletedCount + ")");
                tabPending.setText("Pending (" + pending + ")");
                tabInProgress.setText("In Progress (" + inProgress + ")");
                tabUnderReview.setText("Under Review (" + underReview + ")");
                tabArchived.setText("Archived (" + archived + ")");
                tabTrashed.setText("Trashed (" + trashed + ")");
                tabDenied.setText("Denied (" + denied + ")");
                tabCompleted.setText("Completed (" + completed + ")");
            }

            loadViewData();
            applyRequestedInitialTab();
            showPendingReportIfReady();
        });

        // A NEW_REPORT push received while this list is on screen must surface
        // the server's canonical report without any extra polling.
        com.example.ecowatchadmin1.data.repository.NotificationRepository
                .getInstance(requireContext().getApplicationContext())
                .getReportsInvalidated()
                .observe(getViewLifecycleOwner(), tick -> {
                    if (viewModel != null) viewModel.refreshReports();
                });

        // The possible-duplicate review cards follow the same All/Pending tab
        // rules as the reports list below them.
        viewModel.getAllReports().observe(getViewLifecycleOwner(), reports -> {
            fetchPossibleDuplicates();
        });
    }

    /**
     * Applies a caller-requested tab exactly once, after the first list load.
     * Waiting for the data keeps the per-tab counters in the pills consistent
     * with the content that is actually rendered.
     */
    private void applyRequestedInitialTab() {
        if (requestedInitialTab == null) return;

        String tab = requestedInitialTab;
        requestedInitialTab = null;
        selectTab(tab);
    }

    /** Cloned from the Resident app: waits for the report to exist, then opens it. */
    private void showPendingReportIfReady() {
        if (pendingReportShown
                || pendingNotificationReportId == null
                || pendingNotificationReportId.trim().isEmpty()) {
            return;
        }
        for (Report report : allCurrentReports) {
            String id = report.getReportId() == null
                    ? report.getMongoId() : report.getReportId();
            if (id != null && id.equalsIgnoreCase(pendingNotificationReportId)) {
                pendingReportShown = true;
                pendingNotificationReportId = null;
                requireView().post(() -> {
                    if (isAdded()) showReportDetail(report);
                });
                return;
            }
        }
    }

    private void loadViewData() {
        handleDataUpdate(viewModel.getAllReports().getValue());
    }

    private void handleDataUpdate(List<Report> reports) {
        allCurrentReports = reports != null ? reports : new ArrayList<>();
        filterReports(searchEditText.getText().toString());
    }

    private Date parseReportDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || "Just now".equalsIgnoreCase(dateStr.trim())) {
            return new Date();
        }

        String str = dateStr.trim();

        try {
            long epoch = Long.parseLong(str);
            return new Date(epoch);
        } catch (NumberFormatException ignored) {
        }

        String[] formats = new String[] {
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "MMM dd, yyyy"
        };

        for (String fmt : formats) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(fmt, Locale.US);
                if (fmt.contains("'Z'")) {
                    parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date parsed = parser.parse(str);
                if (parsed != null) return parsed;
            } catch (ParseException ignored) {
            }
        }

        return new Date();
    }

    private void filterReports(String query) {
        // 1. Determine base list for current tab view
        List<Report> baseViewReports = allCurrentReports.stream()
                .filter(r -> {
                    if ("All".equalsIgnoreCase(currentView)) return !r.isDeleted();
                    if ("Pending".equalsIgnoreCase(currentView)) return !r.isArchived() && !r.isDeleted() && "Pending".equalsIgnoreCase(r.getStatus());
                    if ("In Progress".equalsIgnoreCase(currentView)) return !r.isArchived() && !r.isDeleted() && "In Progress".equalsIgnoreCase(r.getStatus());
                    if ("Under Review".equalsIgnoreCase(currentView)) return !r.isArchived() && !r.isDeleted() && "Under Review".equalsIgnoreCase(r.getStatus());
                    if ("Archived".equalsIgnoreCase(currentView)) return r.isArchived() && !r.isDeleted();
                    if ("Trashed".equalsIgnoreCase(currentView)) return r.isDeleted();
                    if ("Denied".equalsIgnoreCase(currentView)) return "Denied".equalsIgnoreCase(r.getStatus()) && !r.isDeleted();
                    if ("Completed".equalsIgnoreCase(currentView)) return "Completed".equalsIgnoreCase(r.getStatus()) && !r.isDeleted();
                    return true;
                })
                .collect(Collectors.toList());

        // 2. Prepare Date bounds for Date Filter
        Calendar calTodayStart = Calendar.getInstance();
        calTodayStart.set(Calendar.HOUR_OF_DAY, 0);
        calTodayStart.set(Calendar.MINUTE, 0);
        calTodayStart.set(Calendar.SECOND, 0);
        calTodayStart.set(Calendar.MILLISECOND, 0);
        Date todayStart = calTodayStart.getTime();

        Calendar calTodayEnd = Calendar.getInstance();
        calTodayEnd.set(Calendar.HOUR_OF_DAY, 23);
        calTodayEnd.set(Calendar.MINUTE, 59);
        calTodayEnd.set(Calendar.SECOND, 59);
        calTodayEnd.set(Calendar.MILLISECOND, 999);
        Date todayEnd = calTodayEnd.getTime();

        Calendar calRangeStart = Calendar.getInstance();
        Date rangeStartDate = null;
        if (currentDateFilterId == R.id.radioLast7) {
            calRangeStart.add(Calendar.DAY_OF_YEAR, -7);
            rangeStartDate = calRangeStart.getTime();
        } else if (currentDateFilterId == R.id.radioLast30) {
            calRangeStart.add(Calendar.DAY_OF_YEAR, -30);
            rangeStartDate = calRangeStart.getTime();
        } else if (currentDateFilterId == R.id.radioLast90) {
            calRangeStart.add(Calendar.DAY_OF_YEAR, -90);
            rangeStartDate = calRangeStart.getTime();
        } else if (currentDateFilterId == R.id.radioLast180) {
            calRangeStart.add(Calendar.DAY_OF_YEAR, -180);
            rangeStartDate = calRangeStart.getTime();
        }
        final Date finalRangeStart = rangeStartDate;

        String q = (query != null) ? query.trim().toLowerCase(Locale.ROOT) : "";

        // 3. Multi-Criteria Filtering
        List<Report> filtered = baseViewReports.stream()
                .filter(r -> {
                    // Deep Multi-Field Search
                    if (q.isEmpty()) return true;
                    String title = r.getTitle() != null ? r.getTitle().toLowerCase(Locale.ROOT) : "";
                    String reportId = r.getReportId() != null ? r.getReportId().toLowerCase(Locale.ROOT) : "";
                    String mongoId = r.getMongoId() != null ? r.getMongoId().toLowerCase(Locale.ROOT) : "";
                    String wasteType = r.getWasteType() != null ? r.getWasteType().toLowerCase(Locale.ROOT) : "";
                    String description = r.getDescription() != null ? r.getDescription().toLowerCase(Locale.ROOT) : "";
                    String status = r.getStatus() != null ? r.getStatus().toLowerCase(Locale.ROOT) : "";
                    String assigned = r.getAssignedPersonnelName() != null
                            ? r.getAssignedPersonnelName().toLowerCase(Locale.ROOT) : "";
                    String assignedId = r.getAssignedPersonnelId() != null
                            ? r.getAssignedPersonnelId().toLowerCase(Locale.ROOT) : "";
                    String response = r.getAdminResponse() != null
                            ? r.getAdminResponse().toLowerCase(Locale.ROOT) : "";

                    return title.contains(q) || reportId.contains(q) || mongoId.contains(q) ||
                           wasteType.contains(q) || description.contains(q) || status.contains(q) ||
                           assigned.contains(q) || assignedId.contains(q) || response.contains(q);
                })
                .filter(r -> {
                    // Status Filter
                    if ("All".equalsIgnoreCase(selectedStatusFilter)) return true;
                    return selectedStatusFilter.equalsIgnoreCase(r.getStatus());
                })
                .filter(r -> {
                    // Category Filter
                    if ("All".equalsIgnoreCase(currentCategoryFilter)) return true;
                    return currentCategoryFilter.equalsIgnoreCase(r.getWasteType());
                })
                .filter(r -> {
                    // Date Filter
                    if (currentDateFilterId == -1) return true;
                    Date rDate = parseReportDate(r.getCreatedAt());

                    if (currentDateFilterId == R.id.radioToday) {
                        return rDate.getTime() >= todayStart.getTime() && rDate.getTime() <= todayEnd.getTime();
                    } else if (currentDateFilterId == R.id.radioCustom) {
                        if (customFromDate == null || customToDate == null) return true;
                        return rDate.getTime() >= customFromDate.getTimeInMillis() && rDate.getTime() <= customToDate.getTimeInMillis();
                    } else {
                        return finalRangeStart != null && rDate.getTime() >= finalRangeStart.getTime();
                    }
                })
                .collect(Collectors.toList());

        // 4. Expanded Sorting Options
        Collections.sort(filtered, (r1, r2) -> {
            if (currentSortId == R.id.radioOldest) {
                Date d1 = parseReportDate(r1.getCreatedAt());
                Date d2 = parseReportDate(r2.getCreatedAt());
                return d1.compareTo(d2);
            } else if (currentSortId == R.id.radioUpdated) {
                Date u1 = parseReportDate(r1.getUpdatedAt() != null ? r1.getUpdatedAt() : r1.getCreatedAt());
                Date u2 = parseReportDate(r2.getUpdatedAt() != null ? r2.getUpdatedAt() : r2.getCreatedAt());
                return u2.compareTo(u1);
            } else if (currentSortId == R.id.radioTitleAZ) {
                String t1 = r1.getTitle() != null ? r1.getTitle() : "";
                String t2 = r2.getTitle() != null ? r2.getTitle() : "";
                return t1.compareToIgnoreCase(t2);
            } else if (currentSortId == R.id.radioTitleZA) {
                String t1 = r1.getTitle() != null ? r1.getTitle() : "";
                String t2 = r2.getTitle() != null ? r2.getTitle() : "";
                return t2.compareToIgnoreCase(t1);
            } else {
                // Default: Newest First (radioNewest)
                Date d1 = parseReportDate(r1.getCreatedAt());
                Date d2 = parseReportDate(r2.getCreatedAt());
                return d2.compareTo(d1);
            }
        });

        adapter.setReports(filtered);
        int reviewCardsShown = updatePossibleDuplicateGroups();
        // "No reports found" must not cover a review card. Counts the cards
        // actually rendered, not every group the server returned, so the
        // history tabs are unaffected by groups hidden on those tabs.
        noReportsText.setVisibility(filtered.isEmpty() && reviewCardsShown == 0
                ? View.VISIBLE : View.GONE);

        // 5. Update Active Filter Chips and Results Counter
        updateActiveFilterChipsAndCounter(q, baseViewReports.size(), filtered.size());
    }

    private void updateActiveFilterChipsAndCounter(String query, int totalCount, int filteredCount) {
        if (activeChipGroup == null || chipScrollView == null) return;

        activeChipGroup.removeAllViews();
        int activeFilterCount = 0;

        // 1. Search Chip
        if (!query.isEmpty()) {
            activeFilterCount++;
            Chip searchChip = createChip("Search: \"" + query + "\"", v -> {
                searchEditText.setText("");
                filterReports("");
            });
            activeChipGroup.addView(searchChip);
        }

        // 2. Category Chip
        if (!"All".equalsIgnoreCase(currentCategoryFilter)) {
            activeFilterCount++;
            Chip categoryChip = createChip("Category: " + currentCategoryFilter, v -> {
                currentCategoryFilter = "All";
                filterReports(searchEditText.getText().toString());
            });
            activeChipGroup.addView(categoryChip);
        }

        // 3. Status Chip
        if (!"All".equalsIgnoreCase(selectedStatusFilter)) {
            activeFilterCount++;
            Chip statusChip = createChip("Status: " + selectedStatusFilter, v -> {
                selectedStatusFilter = "All";
                filterReports(searchEditText.getText().toString());
            });
            activeChipGroup.addView(statusChip);
        }

        // 4. Date Chip
        if (currentDateFilterId != -1) {
            activeFilterCount++;
            String dateText = "Date: ";
            if (currentDateFilterId == R.id.radioToday) dateText += "Today";
            else if (currentDateFilterId == R.id.radioLast7) dateText += "Last 7 days";
            else if (currentDateFilterId == R.id.radioLast30) dateText += "Last 30 days";
            else if (currentDateFilterId == R.id.radioLast90) dateText += "Last 90 days";
            else if (currentDateFilterId == R.id.radioLast180) dateText += "Last 180 days";
            else if (currentDateFilterId == R.id.radioCustom) dateText += "Custom Range";

            Chip dateChip = createChip(dateText, v -> {
                currentDateFilterId = -1;
                customFromDate = null;
                customToDate = null;
                filterReports(searchEditText.getText().toString());
            });
            activeChipGroup.addView(dateChip);
        }

        // 5. Sort Chip
        if (currentSortId != -1 && currentSortId != R.id.radioNewest) {
            activeFilterCount++;
            String sortText = "Sort: ";
            if (currentSortId == R.id.radioOldest) sortText += "Oldest First";
            else if (currentSortId == R.id.radioUpdated) sortText += "Recently Updated";
            else if (currentSortId == R.id.radioTitleAZ) sortText += "Title (A-Z)";
            else if (currentSortId == R.id.radioTitleZA) sortText += "Title (Z-A)";

            Chip sortChip = createChip(sortText, v -> {
                currentSortId = R.id.radioNewest;
                filterReports(searchEditText.getText().toString());
            });
            activeChipGroup.addView(sortChip);
        }

        // 6. Clear All Chip (if any filters or search active)
        if (activeFilterCount > 0) {
            Chip clearAllChip = createChip("Clear All ✕", null);
            clearAllChip.setOnClickListener(v -> resetAllFiltersAndSearch());
            activeChipGroup.addView(clearAllChip);

            chipScrollView.setVisibility(View.VISIBLE);
        } else {
            chipScrollView.setVisibility(View.GONE);
        }

        // Results Metrics
        if (filterSummaryText != null) {
            if (activeFilterCount > 0) {
                filterSummaryText.setText("Showing " + filteredCount + " of " + totalCount + " reports");
                filterSummaryText.setVisibility(View.VISIBLE);
            } else {
                filterSummaryText.setVisibility(View.GONE);
            }
        }
    }

    private void resetAllFiltersAndSearch() {
        selectedStatusFilter = "All";
        currentCategoryFilter = "All";
        currentDateFilterId = -1;
        currentSortId = R.id.radioNewest;
        customFromDate = null;
        customToDate = null;
        searchEditText.setText("");
        filterReports("");
    }

    private Chip createChip(String text, @Nullable View.OnClickListener onCloseClickListener) {
        Chip chip = new Chip(requireContext());
        chip.setText(text);
        if (onCloseClickListener != null) {
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(onCloseClickListener);
        } else {
            chip.setCloseIconVisible(false);
        }
        chip.setChipBackgroundColorResource(R.color.chip_background);
        ColorStateList textAndIconColor = ContextCompat.getColorStateList(requireContext(), R.color.chip_text_and_icon);
        chip.setTextColor(textAndIconColor);
        chip.setCloseIconTint(textAndIconColor);
        chip.setClickable(true);
        return chip;
    }

    private void showFilterDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_filter_main, null);
        MaterialButton btnCategory = dialogView.findViewById(R.id.btnFilterCategory);
        MaterialButton btnStatus = dialogView.findViewById(R.id.btnFilterStatus);
        MaterialButton btnDate = dialogView.findViewById(R.id.btnFilterDate);
        View btnClose = dialogView.findViewById(R.id.btnCloseFilter);
        View btnResetAll = dialogView.findViewById(R.id.btnResetCategory);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnResetAll.setOnClickListener(v -> {
            resetAllFiltersAndSearch();
            dialog.dismiss();
            Toast.makeText(getContext(), "All filters cleared", Toast.LENGTH_SHORT).show();
        });

        btnCategory.setOnClickListener(v -> {
            dialog.dismiss();
            showCategoryFilter();
        });

        if (btnStatus != null) {
            btnStatus.setOnClickListener(v -> {
                dialog.dismiss();
                showStatusFilter();
            });
        }

        btnDate.setOnClickListener(v -> {
            dialog.dismiss();
            showDateFilter();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showCategoryFilter() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_category_filter, null);
        RadioGroup radioGroup = dialogView.findViewById(R.id.categoryRadioGroup);
        MaterialButton btnApply = dialogView.findViewById(R.id.btnApplyCategoryFilter);
        View btnBack = dialogView.findViewById(R.id.btnBackCategory);
        View btnReset = dialogView.findViewById(R.id.btnResetCategory);

        // Pre-selection based on currentCategoryFilter
        if ("Biodegradable".equalsIgnoreCase(currentCategoryFilter)) radioGroup.check(R.id.radioBiodegradable);
        else if ("Recyclable".equalsIgnoreCase(currentCategoryFilter)) radioGroup.check(R.id.radioRecyclable);
        else if ("Residual".equalsIgnoreCase(currentCategoryFilter)) radioGroup.check(R.id.radioResidual);
        else if ("Hazardous".equalsIgnoreCase(currentCategoryFilter)) radioGroup.check(R.id.radioHazardous);
        else if ("Electronic Waste (E-Waste)".equalsIgnoreCase(currentCategoryFilter)) radioGroup.check(R.id.radioEWaste);
        else if ("General Waste".equalsIgnoreCase(currentCategoryFilter)) radioGroup.check(R.id.radioGeneral);
        else radioGroup.check(R.id.radioAll);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnApply.setOnClickListener(v -> {
            int checkedId = radioGroup.getCheckedRadioButtonId();
            if (checkedId == -1) {
                Toast.makeText(getContext(), "Please select a category", Toast.LENGTH_SHORT).show();
                return;
            }

            String category = "All";
            if (checkedId == R.id.radioBiodegradable) category = "Biodegradable";
            else if (checkedId == R.id.radioRecyclable) category = "Recyclable";
            else if (checkedId == R.id.radioResidual) category = "Residual";
            else if (checkedId == R.id.radioHazardous) category = "Hazardous";
            else if (checkedId == R.id.radioEWaste) category = "Electronic Waste (E-Waste)";
            else if (checkedId == R.id.radioGeneral) category = "General Waste";

            currentCategoryFilter = category;
            filterReports(searchEditText.getText().toString());
            dialog.dismiss();
            Toast.makeText(getContext(), "Category filter applied: " + currentCategoryFilter, Toast.LENGTH_SHORT).show();
        });

        btnBack.setOnClickListener(v -> {
            dialog.dismiss();
            showFilterDialog();
        });

        btnReset.setOnClickListener(v -> {
            currentCategoryFilter = "All";
            radioGroup.check(R.id.radioAll);
            filterReports(searchEditText.getText().toString());
            dialog.dismiss();
            Toast.makeText(getContext(), "Category filter reset", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showStatusFilter() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_status_filter, null);
        RadioGroup radioGroup = dialogView.findViewById(R.id.statusRadioGroup);
        MaterialButton btnApply = dialogView.findViewById(R.id.btnApplyStatusFilter);
        View btnBack = dialogView.findViewById(R.id.btnBackStatus);
        View btnReset = dialogView.findViewById(R.id.btnResetStatus);

        // Pre-selection based on selectedStatusFilter
        if ("Pending".equalsIgnoreCase(selectedStatusFilter)) radioGroup.check(R.id.radioStatusPending);
        else if ("In Progress".equalsIgnoreCase(selectedStatusFilter)) radioGroup.check(R.id.radioStatusInProgress);
        else if ("Under Review".equalsIgnoreCase(selectedStatusFilter)) radioGroup.check(R.id.radioStatusUnderReview);
        else if ("Completed".equalsIgnoreCase(selectedStatusFilter)) radioGroup.check(R.id.radioStatusCompleted);
        else if ("Denied".equalsIgnoreCase(selectedStatusFilter)) radioGroup.check(R.id.radioStatusDenied);
        else radioGroup.check(R.id.radioStatusAll);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnApply.setOnClickListener(v -> {
            int checkedId = radioGroup.getCheckedRadioButtonId();
            String status = "All";
            if (checkedId == R.id.radioStatusPending) status = "Pending";
            else if (checkedId == R.id.radioStatusInProgress) status = "In Progress";
            else if (checkedId == R.id.radioStatusUnderReview) status = "Under Review";
            else if (checkedId == R.id.radioStatusCompleted) status = "Completed";
            else if (checkedId == R.id.radioStatusDenied) status = "Denied";

            selectedStatusFilter = status;
            filterReports(searchEditText.getText().toString());
            dialog.dismiss();
            Toast.makeText(getContext(), "Status filter applied: " + selectedStatusFilter, Toast.LENGTH_SHORT).show();
        });

        btnBack.setOnClickListener(v -> {
            dialog.dismiss();
            showFilterDialog();
        });

        btnReset.setOnClickListener(v -> {
            selectedStatusFilter = "All";
            radioGroup.check(R.id.radioStatusAll);
            filterReports(searchEditText.getText().toString());
            dialog.dismiss();
            Toast.makeText(getContext(), "Status filter reset", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showDateFilter() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_date_filter, null);
        RadioGroup radioGroup = dialogView.findViewById(R.id.dateRadioGroup);
        RadioGroup sortRadioGroup = dialogView.findViewById(R.id.sortRadioGroup);
        LinearLayout customDateLayout = dialogView.findViewById(R.id.customDateLayout);
        TextView tvFromDate = dialogView.findViewById(R.id.tvFromDate);
        TextView tvToDate = dialogView.findViewById(R.id.tvToDate);
        View btnFromDate = dialogView.findViewById(R.id.btnFromDate);
        View btnToDate = dialogView.findViewById(R.id.btnToDate);
        View btnBack = dialogView.findViewById(R.id.btnBackDate);
        View btnReset = dialogView.findViewById(R.id.btnResetDate);
        MaterialButton btnApply = dialogView.findViewById(R.id.btnApplyFilter);

        // Pre-selection for date range
        if (currentDateFilterId != -1) {
            radioGroup.check(currentDateFilterId);
            if (currentDateFilterId == R.id.radioCustom) {
                customDateLayout.setVisibility(View.VISIBLE);
            }
        }

        // Pre-selection for sorting
        if (currentSortId != -1) {
            sortRadioGroup.check(currentSortId);
        } else {
            sortRadioGroup.check(R.id.radioNewest);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

        if (customFromDate != null) {
            tvFromDate.setText(sdf.format(customFromDate.getTime()));
        }
        if (customToDate != null) {
            tvToDate.setText(sdf.format(customToDate.getTime()));
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> 
            customDateLayout.setVisibility(checkedId == R.id.radioCustom ? View.VISIBLE : View.GONE));

        btnFromDate.setOnClickListener(v -> {
            Calendar cal = customFromDate != null ? customFromDate : Calendar.getInstance();
            new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
                customFromDate = Calendar.getInstance();
                customFromDate.set(year, month, dayOfMonth, 0, 0, 0);
                customFromDate.set(Calendar.MILLISECOND, 0);
                tvFromDate.setText(sdf.format(customFromDate.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnToDate.setOnClickListener(v -> {
            Calendar cal = customToDate != null ? customToDate : Calendar.getInstance();
            new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
                customToDate = Calendar.getInstance();
                customToDate.set(year, month, dayOfMonth, 23, 59, 59);
                customToDate.set(Calendar.MILLISECOND, 999);
                tvToDate.setText(sdf.format(customToDate.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnApply.setOnClickListener(v -> {
            currentDateFilterId = radioGroup.getCheckedRadioButtonId();
            currentSortId = sortRadioGroup.getCheckedRadioButtonId();
            
            filterReports(searchEditText.getText().toString());
            dialog.dismiss();
        });

        btnBack.setOnClickListener(v -> {
            dialog.dismiss();
            showFilterDialog();
        });

        btnReset.setOnClickListener(v -> {
            radioGroup.clearCheck();
            sortRadioGroup.check(R.id.radioNewest);
            currentDateFilterId = -1;
            currentSortId = R.id.radioNewest;
            customFromDate = null;
            customToDate = null;
            filterReports(searchEditText.getText().toString());
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showReportDetail(Report report) {
        ReportDetailDialog.show(this, report, () -> {
            if (isAdded()) viewModel.refreshReports();
        });
    }
}
