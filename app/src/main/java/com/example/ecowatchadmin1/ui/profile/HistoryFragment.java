package com.example.ecowatchadmin1.ui.profile;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.History;
import com.example.ecowatchadmin1.data.repository.ReportRepository;
import com.example.ecowatchadmin1.utils.AdminHistoryCategory;
import com.example.ecowatchadmin1.utils.AdminHistoryClassifier;
import com.example.ecowatchadmin1.utils.AdminHistoryDateFormatter;
import com.example.ecowatchadmin1.utils.SessionManager;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Admin's audit trail: what was decided, on which report, by whom, and when.
 *
 * <p>Five tabs over one cached list — Reports, Assignments, Cleaning,
 * Announcements and Account — plus a trash view for logs the admin has moved
 * aside. Every tab is a filter on the same rows, so switching tabs never
 * refetches and the list is already populated when the screen opens offline.
 *
 * <p>What the screen deliberately does not contain: button clicks, navigation,
 * per-resident activity, and API-level events. Those belong in the resident's own
 * history and in server logs. What it does contain is a "View Details" action per
 * card, so the card stays compact while the full record stays reachable.
 */
public class HistoryFragment extends Fragment
        implements AdminHistoryAdapter.OnRowActionListener,
        AdminHistoryAdapter.OnSelectionChangedListener {

    private RecyclerView recyclerView;
    private AdminHistoryAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ReportRepository repository;
    private SessionManager sessionManager;

    private TextView tabAll, tabReports, tabAssignments, tabCleaning, tabAnnouncements,
            tabAccount, tabTrash;
    private TextView historyTitle, btnSelectAll, emptyText, filterSummaryText;
    private EditText etSearch;
    private ImageButton btnSort, btnClearSearch, btnDateFilter;
    private View tabContainer, searchSortLayout, selectAllLayout, bulkActionsLayout;
    /**
     * The three bulk controls are {@link MaterialButton}s in the layout, not
     * {@code ImageButton}s. MaterialButton extends AppCompatButton and not
     * ImageButton, so binding them to an ImageButton field throws a
     * ClassCastException in onCreateView and the screen takes the process down
     * before it can draw.
     */
    private MaterialButton btnDeleteBulk, btnRestoreBulk, btnArchiveBulk;
    private CheckBox btnSelectAllBox;

    /**
     * The selected category tab, or {@code null} while the Trash tab is showing.
     * Trash is modelled as a separate view rather than a seventh category because
     * it is about a row's {@code isDeleted} flag, not about what the action was:
     * a trashed report still belongs to Reports, it is just not active.
     */
    private AdminHistoryCategory currentTab = AdminHistoryCategory.ALL;
    private boolean isTrashView = false;

    /**
     * Whether the checkboxes, Select All and the bulk bar are on screen.
     *
     * <p>Off on open and turned on only by a long press on a card. See
     * {@link #setupSelectionMode()}.
     */
    private boolean selectionMode = false;

    private String searchQuery = "";
    private boolean isSortNewest = true;

    /** Active date window; either bound may be null for an open-ended range. */
    private Long dateFromInclusive, dateToInclusive;
    private String dateFilterLabel;

    private List<History> activeHistory = new ArrayList<>();
    private List<History> trashHistory = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        repository = new ReportRepository(requireActivity().getApplication());
        sessionManager = new SessionManager(requireContext());

        recyclerView = view.findViewById(R.id.historyRecyclerView);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        ImageButton btnBack = view.findViewById(R.id.btnBack);

        tabAll = view.findViewById(R.id.tabAll);
        tabReports = view.findViewById(R.id.tabReport);
        tabAssignments = view.findViewById(R.id.tabAssignments);
        tabCleaning = view.findViewById(R.id.tabCleaning);
        tabAnnouncements = view.findViewById(R.id.tabAnnouncements);
        tabAccount = view.findViewById(R.id.tabESPAccounts);
        tabTrash = view.findViewById(R.id.tabTrash);

        historyTitle = view.findViewById(R.id.historyTitle);
        emptyText = view.findViewById(R.id.historyEmptyText);
        filterSummaryText = view.findViewById(R.id.filterSummaryText);
        tabContainer = view.findViewById(R.id.tabContainer);
        searchSortLayout = view.findViewById(R.id.searchSortLayout);
        etSearch = view.findViewById(R.id.etSearch);
        btnSort = view.findViewById(R.id.btnSort);
        btnClearSearch = view.findViewById(R.id.btnClearSearch);
        btnDateFilter = view.findViewById(R.id.btnDateFilter);
        btnSelectAll = view.findViewById(R.id.btnSelectAll);
        btnSelectAllBox = view.findViewById(R.id.btnSelectAllBox);
        bulkActionsLayout = view.findViewById(R.id.bulkActionsBar);
        selectAllLayout = view.findViewById(R.id.selectAllLayout);
        btnDeleteBulk = view.findViewById(R.id.btnDeleteBulk);
        btnRestoreBulk = view.findViewById(R.id.btnRestoreBulk);
        btnArchiveBulk = view.findViewById(R.id.btnArchiveBulk);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AdminHistoryAdapter(this::showDetails, this, this, this::onRowLongPressed);
        recyclerView.setAdapter(adapter);

        setupTabs();
        setupSearchAndSort();
        setupDateFilter();
        setupSelectionMode();
        updateHeaderTitle();

        btnBack.setOnClickListener(v -> getParentFragmentManager().popBackStack());

        // Bulk actions are hidden until there is at least one row, and the archive
        // and restore controls swap with the view so the destructive pair is never
        // both showing at once.
        updateBulkActionsVisibility();

        btnSelectAll.setOnClickListener(v -> selectAllVisible());

        btnSelectAllBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                adapter.selectAllVisible();
            } else {
                adapter.clearSelection();
            }
        });

        btnArchiveBulk.setOnClickListener(v -> runOnSelection(
                this::confirmMoveToTrash, "Select a record first."));

        btnRestoreBulk.setOnClickListener(v -> runOnSelection(
                this::confirmRestore, "Select a record first."));

        btnDeleteBulk.setOnClickListener(v -> runOnSelection(
                this::confirmPermanentDelete, "Select a record first."));

        loadHistoryData();

        swipeRefreshLayout.setOnRefreshListener(() -> refreshFromServer());

        return view;
    }

    // --------------------------------------------------------- selection mode

    /**
     * Keeps every selection affordance behind a long press.
     *
     * <p>This screen is an audit trail, and it is read far more often than it is
     * changed. Showing a checkbox on every card, a Select All toggle in the header
     * and two bulk buttons at the bottom would put five controls on a screen whose
     * usual job is to be read, so none of them are drawn until someone long presses
     * a card.
     *
     * <p>Two exits are wired beyond the press that enters it: a long press on the
     * header bar, and the back button. Both clear the selection as they exit, so
     * there is never a hidden selection waiting behind a bulk press.
     */
    private void setupSelectionMode() {
        // Long-pressing the header bars exits the mode. It is the control surface
        // the mode itself put on screen, so pressing it again is the natural "done".
        selectAllLayout.setOnLongClickListener(v -> {
            exitSelectionMode();
            return true;
        });
        bulkActionsLayout.setOnLongClickListener(v -> {
            exitSelectionMode();
            return true;
        });

        // Back closes the mode before it navigates away, so the back button still
        // means "leave this screen" once the mode is off.
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (selectionMode) {
                            exitSelectionMode();
                        } else {
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });
    }

    /**
     * Enters selection mode on the first long press and leaves it on the next.
     *
     * <p>Toggling on the same card is what makes the gesture reversible without
     * hunting for a Done button. Entering pre-selects the pressed record, because
     * a long press on a specific card is a statement about that card.
     */
    private void onRowLongPressed(int rowId) {
        if (selectionMode) {
            exitSelectionMode();
            return;
        }
        selectionMode = true;
        adapter.setSelectionMode(true);
        if (rowId > 0) {
            adapter.setSelected(rowId, true);
        }
        updateSelectionUi();
    }

    /** Leaves selection mode, dropping anything that was selected on the way out. */
    private void exitSelectionMode() {
        if (!selectionMode) return;
        selectionMode = false;
        adapter.setSelectionMode(false);
        adapter.clearSelection();
        updateSelectionUi();
    }

    /**
     * Shows the selection affordances only in selection mode, and only with rows.
     *
     * <p>The row count is part of the condition because an empty Select All and an
     * empty bulk bar would both be controls with nothing to act on.
     */
    private void updateSelectionUi() {
        boolean visible = selectionMode && adapter.getItemCount() > 0;
        if (selectAllLayout != null) {
            selectAllLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (bulkActionsLayout != null) {
            bulkActionsLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    // ---------------------------------------------------------------- tabs

    private void setupTabs() {
        tabAll.setOnClickListener(v -> selectTab(AdminHistoryCategory.ALL, false));
        tabReports.setOnClickListener(v -> selectTab(AdminHistoryCategory.REPORTS, false));
        tabAssignments.setOnClickListener(v -> selectTab(AdminHistoryCategory.ASSIGNMENTS, false));
        tabCleaning.setOnClickListener(v -> selectTab(AdminHistoryCategory.CLEANING, false));
        tabAnnouncements.setOnClickListener(v -> selectTab(AdminHistoryCategory.ANNOUNCEMENTS, false));
        tabAccount.setOnClickListener(v -> selectTab(AdminHistoryCategory.ACCOUNT, false));
        tabTrash.setOnClickListener(v -> selectTab(AdminHistoryCategory.ALL, true));
    }

    private void selectTab(AdminHistoryCategory category, boolean trash) {
        currentTab = category;
        isTrashView = trash;

        updatePill(tabAll, !trash && category == AdminHistoryCategory.ALL);
        updatePill(tabReports, !trash && category == AdminHistoryCategory.REPORTS);
        updatePill(tabAssignments, !trash && category == AdminHistoryCategory.ASSIGNMENTS);
        updatePill(tabCleaning, !trash && category == AdminHistoryCategory.CLEANING);
        updatePill(tabAnnouncements, !trash && category == AdminHistoryCategory.ANNOUNCEMENTS);
        updatePill(tabAccount, !trash && category == AdminHistoryCategory.ACCOUNT);
        updatePill(tabTrash, trash);

        // The card's remove button means "move to trash" in the feed and "restore"
        // in the trash, and permanent deletion is only offered in the trash.
        adapter.setTrashMode(trash);

        // Switching tabs leaves selection mode. The selection is keyed to rows, the
        // rows are about to be replaced, and leaving a selection on screen over a
        // list the admin never chose is how a bulk action hits the wrong records.
        exitSelectionMode();
        adapter.clearSelection();
        updateBulkActionsVisibility();

        updateHeaderTitle();

        // A tab is a filter over the rows already cached, so switching is a local
        // re-render. It used to register another LiveData observer per tap and
        // never remove the old ones; they all wrote this same list and Room posts
        // their results in an order that is not guaranteed, so one tab's rows could
        // be overwritten before the screen drew them. Re-tapping then appeared to
        // fix it, because the extra observer landed last.
        render();
        refreshFromServer();
    }

    private void updatePill(TextView tab, boolean active) {
        if (active) {
            tab.setBackgroundResource(R.drawable.active_pill_background);
            tab.setTextColor(getResources().getColor(R.color.white, null));
        } else {
            tab.setBackground(null);
            tab.setTextColor(getResources().getColor(R.color.green, null));
        }
    }

    // ------------------------------------------------------- search and sort

    private void setupSearchAndSort() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s == null ? "" : s.toString().toLowerCase(Locale.getDefault()).trim();
                btnClearSearch.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                render();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnClearSearch.setOnClickListener(v -> etSearch.setText(""));

        btnSort.setOnClickListener(v -> {
            isSortNewest = !isSortNewest;
            Toast.makeText(getContext(),
                    isSortNewest ? "Sorted newest first" : "Sorted oldest first",
                    Toast.LENGTH_SHORT).show();
            render();
        });
    }

    // --------------------------------------------------------- date filtering

    /**
     * Opens the date filter.
     *
     * <p>Offers Today, This week, This month and a custom range, matching what the
     * screen promises. The dialog is shared with the resident history screen, so
     * the unused radio options in it are hidden here rather than offered and
     * ignored.
     */
    private void setupDateFilter() {
        btnDateFilter.setOnClickListener(filterClick -> {
            View dialogView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_date_filter, null);

            RadioGroup dateGroup = dialogView.findViewById(R.id.dateRadioGroup);
            RadioGroup sortGroup = dialogView.findViewById(R.id.sortRadioGroup);
            View customLayout = dialogView.findViewById(R.id.customDateLayout);
            TextView fromLabel = dialogView.findViewById(R.id.tvFromDate);
            TextView toLabel = dialogView.findViewById(R.id.tvToDate);
            View fromButton = dialogView.findViewById(R.id.btnFromDate);
            View toButton = dialogView.findViewById(R.id.btnToDate);
            View backButton = dialogView.findViewById(R.id.btnBackDate);
            View resetButton = dialogView.findViewById(R.id.btnResetDate);
            MaterialButton applyButton = dialogView.findViewById(R.id.btnApplyFilter);

            // This screen filters by category, not by report status or title, so
            // the options that cannot apply are hidden instead of shown and ignored.
            hideViewIfPresent(dialogView, R.id.radioUpdated);
            hideViewIfPresent(dialogView, R.id.radioTitleAZ);
            hideViewIfPresent(dialogView, R.id.radioTitleZA);

            AlertDialog dialog = new AlertDialog.Builder(requireContext())
                    .setView(dialogView)
                    .create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            SimpleDateFormat labelFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

            final Calendar[] customFrom = {null};
            final Calendar[] customTo = {null};

            // Pre-fill from the active filter so reopening the dialog shows the
            // range in force instead of resetting the admin's place.
            if (dateFromInclusive != null) {
                customFrom[0] = toCalendar(dateFromInclusive);
                fromLabel.setText(labelFormat.format(dateFromInclusive));
            }
            if (dateToInclusive != null) {
                customTo[0] = toCalendar(dateToInclusive);
                toLabel.setText(labelFormat.format(dateToInclusive));
            }

            dateGroup.setOnCheckedChangeListener((group, checkedId) ->
                    customLayout.setVisibility(
                            checkedId == R.id.radioCustom ? View.VISIBLE : View.GONE));

            Calendar today = Calendar.getInstance();
            fromButton.setOnClickListener(v -> new DatePickerDialog(requireContext(),
                    (view, year, month, day) -> {
                        customFrom[0] = Calendar.getInstance();
                        customFrom[0].set(year, month, day, 0, 0, 0);
                        customFrom[0].set(Calendar.MILLISECOND, 0);
                        fromLabel.setText(labelFormat.format(customFrom[0].getTime()));
                    },
                    today.get(Calendar.YEAR), today.get(Calendar.MONTH),
                    today.get(Calendar.DAY_OF_MONTH)).show());

            toButton.setOnClickListener(v -> new DatePickerDialog(requireContext(),
                    (view, year, month, day) -> {
                        customTo[0] = Calendar.getInstance();
                        // Inclusive of the chosen day, so the window ends at the
                        // last millisecond rather than at 00:00 and excludes it.
                        customTo[0].set(year, month, day, 23, 59, 59);
                        customTo[0].set(Calendar.MILLISECOND, 999);
                        toLabel.setText(labelFormat.format(customTo[0].getTime()));
                    },
                    today.get(Calendar.YEAR), today.get(Calendar.MONTH),
                    today.get(Calendar.DAY_OF_MONTH)).show());

            applyButton.setOnClickListener(v -> {
                isSortNewest = sortGroup.getCheckedRadioButtonId() != R.id.radioOldest;
                applyDateSelection(dateGroup.getCheckedRadioButtonId(),
                        customFrom[0], customTo[0]);
                dialog.dismiss();
            });

            backButton.setOnClickListener(v -> dialog.dismiss());

            resetButton.setOnClickListener(v -> {
                clearDateFilter();
                dateGroup.clearCheck();
                customFrom[0] = null;
                customTo[0] = null;
                fromLabel.setText("Select Date");
                toLabel.setText("Select Date");
                dialog.dismiss();
            });

            dialog.show();
        });
    }

    /** Resolves the chosen preset into an inclusive millisecond window. */
    private void applyDateSelection(int dateId, Calendar customFrom, Calendar customTo) {
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        String label;

        if (dateId == R.id.radioToday) {
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            label = "Today";
        } else if (dateId == R.id.radioLast7) {
            start.add(Calendar.DAY_OF_YEAR, -7);
            label = "Last 7 days";
        } else if (dateId == R.id.radioLast30) {
            start.add(Calendar.DAY_OF_YEAR, -30);
            label = "Last 30 days";
        } else if (dateId == R.id.radioCustom) {
            // A half-filled custom range is ignored rather than silently applied
            // as an unbounded window, which would look like the filter "did nothing".
            if (customFrom == null || customTo == null) {
                Toast.makeText(getContext(), "Select both a start and end date.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            start = customFrom;
            end = customTo;
            label = "Custom range";
        } else {
            // Nothing selected: treat the dialog as a cancel.
            return;
        }

        dateFromInclusive = start.getTimeInMillis();
        dateToInclusive = end.getTimeInMillis();
        dateFilterLabel = label;
        render();
    }

    private void clearDateFilter() {
        dateFromInclusive = null;
        dateToInclusive = null;
        dateFilterLabel = null;
        render();
    }

    private void hideViewIfPresent(View parent, int id) {
        View view = parent.findViewById(id);
        if (view != null) view.setVisibility(View.GONE);
    }

    private static Calendar toCalendar(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        return calendar;
    }

    // -------------------------------------------------------------- rendering

    private void updateHeaderTitle() {
        historyTitle.setText(isTrashView
                ? "History Trash (" + trashHistory.size() + ")"
                : "History (" + activeHistory.size() + ")");
    }

    /**
     * Applies the tab, search and date filters, then repaints the list.
     *
     * <p>The tab filter runs first because it is the cheapest and most selective;
     * search and date are then applied to what survives. All three are combined
     * rather than mutually exclusive, so a search inside the Cleaning tab inside a
     * date range behaves the way an admin expects.
     */
    private void render() {
        List<History> source = isTrashView ? trashHistory : activeHistory;
        List<History> visible = new ArrayList<>();

        for (History history : source) {
            if (history == null) continue;
            if (!isTrashView && !belongsOnCurrentTab(history)) {
                continue;
            }
            if (!AdminHistoryClassifier.matchesQuery(searchQuery, history.getAction(),
                    history.getDescription(), history.getReportId(), history.getActorId())) {
                continue;
            }
            if (!AdminHistoryClassifier.withinRange(history.getTimestampValue(),
                    dateFromInclusive, dateToInclusive)) {
                continue;
            }
            visible.add(history);
        }

        visible.sort((a, b) -> {
            long first = a.getTimestampValue() == null ? 0L : a.getTimestampValue();
            long second = b.getTimestampValue() == null ? 0L : b.getTimestampValue();
            // Undated rows sort last in either direction: an admin scanning for
            // recent decisions should not have a row with no date pinned to the top.
            if (first == 0L && second == 0L) return 0;
            if (first == 0L) return 1;
            if (second == 0L) return -1;
            return isSortNewest ? Long.compare(second, first) : Long.compare(first, second);
        });

        adapter.submit(visible);
        updateTabCounts();
        updateEmptyState(visible);
        updateHeaderTitle();
        updateFilterSummary(searchQuery, source.size(), visible.size());

        // The adapter only reports a selection change when something is ticked or
        // cleared, so without this the header's enabled state would never be set
        // for a list that has just been rendered. Visibility is not decided here;
        // it belongs to selection mode alone.
        onSelectionChanged(adapter.getSelectedCount(), visible.size());
        updateSelectionUi();
    }

    private void updateFilterSummary(String query, int totalCount, int filteredCount) {
        if (filterSummaryText == null) return;

        boolean hasActiveFilter = (query != null && !query.trim().isEmpty())
                || isTrashView
                || currentTab != AdminHistoryCategory.ALL
                || dateFilterLabel != null
                || !isSortNewest;

        if (hasActiveFilter) {
            filterSummaryText.setText("Showing " + filteredCount + " of " + totalCount + " records");
            filterSummaryText.setVisibility(View.VISIBLE);
        } else {
            filterSummaryText.setVisibility(View.GONE);
        }
    }

    /**
 * Whether a row belongs on the tab currently showing.
     *
     * <p>Reads the {@code category} the server stored on the row, which is the same
     * value the server filtered its response by. Re-deriving the tab from the action
     * string here instead is what made a tab disagree with its own results: bare
     * {@code COMPLETED} is stored as Reports but classified as Cleaning, so those
     * rows were fetched for Reports and then hidden by the client.
     */
    private boolean belongsOnCurrentTab(History history) {
        if (currentTab == AdminHistoryCategory.ALL) return true;
        return AdminHistoryClassifier.effectiveCategory(
                history.getCategory(), history.getAction()) == currentTab;
    }

    private void updateEmptyState(List<History> visible) {
        if (!visible.isEmpty()) {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        recyclerView.setVisibility(View.GONE);
        emptyText.setVisibility(View.VISIBLE);
        swipeRefreshLayout.setRefreshing(false);
        emptyText.setText(emptyMessage());
    }

    /** Distinguishes "nothing recorded yet" from "your filters hid everything". */
    private String emptyMessage() {
        boolean filtered = !searchQuery.isEmpty() || dateFilterLabel != null;
        if (isTrashView) {
            return filtered ? "No trashed records match these filters." : "Trash is empty.";
        }
        if (filtered) {
            return "No records match these filters.";
        }
        if (currentTab == AdminHistoryCategory.ALL) {
            return "No history records yet.";
        }
        return "No " + currentTab.getLabel().toLowerCase(Locale.US) + " records yet.";
    }

    /**
     * Labels each pill with how many active records it would show.
     *
     * <p>Counts every tab from the unfiltered active list, not from the currently
     * visible rows, so a pill keeps advertising how much is behind it while a
     * search or date filter is narrowing the list. Keyed by category rather than
     * by ordinal so the two can be reordered without silently mislabelling.
     */
    private void updateTabCounts() {
        Map<AdminHistoryCategory, Integer> counts = new EnumMap<>(AdminHistoryCategory.class);
        for (AdminHistoryCategory category : AdminHistoryCategory.values()) {
            counts.put(category, 0);
        }
        for (History history : activeHistory) {
            if (history == null) continue;
            AdminHistoryCategory category = AdminHistoryClassifier.effectiveCategory(
                    history.getCategory(), history.getAction());
            counts.merge(category, 1, Integer::sum);
        }
        counts.put(AdminHistoryCategory.ALL, activeHistory.size());

        tabAll.setText(labelWithCount(AdminHistoryCategory.ALL, counts));
        tabReports.setText(labelWithCount(AdminHistoryCategory.REPORTS, counts));
        tabAssignments.setText(labelWithCount(AdminHistoryCategory.ASSIGNMENTS, counts));
        tabCleaning.setText(labelWithCount(AdminHistoryCategory.CLEANING, counts));
        tabAnnouncements.setText(labelWithCount(AdminHistoryCategory.ANNOUNCEMENTS, counts));
        tabAccount.setText(labelWithCount(AdminHistoryCategory.ACCOUNT, counts));
        tabTrash.setText("Trash (" + trashHistory.size() + ")");
    }

    private static String labelWithCount(AdminHistoryCategory category,
                                         Map<AdminHistoryCategory, Integer> counts) {
        return category.getLabel() + " (" + counts.getOrDefault(category, 0) + ")";
    }

    // ----------------------------------------------------------- bulk actions

    private void selectAllVisible() {
        // Only reachable from the header's bulk bar. Selection is deliberately not
        // how a single record is removed: each card carries its own action, which
        // keeps the irreversible one behind a per-record decision.
        adapter.selectAllVisible();
        Toast.makeText(getContext(),
                adapter.getSelectedCount() + " selected",
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Runs a bulk action against the current selection.
     *
     * <p>Every bulk control funnels through here so an empty selection always
     * produces the same explanation rather than silently doing nothing.
     */
    private void runOnSelection(java.util.function.Consumer<List<History>> action,
                                String emptyMessage) {
        List<History> rows = adapter.getSelectedRows();
        if (rows.isEmpty()) {
            Toast.makeText(getContext(), emptyMessage, Toast.LENGTH_SHORT).show();
            return;
        }
        action.accept(rows);
    }

    /**
     * Keeps the header in step with the cards.
     *
     * <p>The controls enable only with a selection, and Select All reflects whether
     * every visible row is ticked rather than being a one-way switch. Visibility is
     * left entirely to {@link #updateSelectionUi()}: this method runs on every tick
     * as well as on every re-render, so deciding what is on screen here would
     * resurrect the controls the moment a card reported a selection.
     */
    @Override
    public void onSelectionChanged(int selectedCount, int totalCount) {
        boolean hasRows = totalCount > 0;
        boolean hasSelection = selectedCount > 0;

        boolean enabled = hasSelection;
        if (btnArchiveBulk != null) btnArchiveBulk.setEnabled(enabled);
        if (btnRestoreBulk != null) btnRestoreBulk.setEnabled(enabled);
        if (btnDeleteBulk != null) btnDeleteBulk.setEnabled(enabled);
        if (btnSelectAll != null) btnSelectAll.setEnabled(hasRows);
        if (btnSelectAll != null) {
            btnSelectAll.setText(adapter.isEverythingSelected() ? "Clear" : "Select All");
        }

        // Detached before assignment, or restoring the checked state would look
        // like a tap and clear the selection it is meant to display.
        if (btnSelectAllBox != null) {
            btnSelectAllBox.setOnCheckedChangeListener(null);
            btnSelectAllBox.setChecked(hasRows && adapter.isEverythingSelected());
            btnSelectAllBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    adapter.selectAllVisible();
                } else {
                    adapter.clearSelection();
                }
            });
        }
    }

    /**
     * Shows the controls that match the view.
     *
     * <p>Archive belongs to the feed and restore to the trash. Showing both at once
     * would put a reversible and an irreversible bulk action one tap apart.
     */
    private void updateBulkActionsVisibility() {
        if (btnArchiveBulk == null || btnRestoreBulk == null) return;
        btnArchiveBulk.setVisibility(isTrashView ? View.GONE : View.VISIBLE);
        btnRestoreBulk.setVisibility(isTrashView ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------- per-card actions

    @Override
    public void onRowMoveToTrash(History history) {
        confirmMoveToTrash(Collections.singletonList(history));
    }

    @Override
    public void onRowRestore(History history) {
        confirmRestore(Collections.singletonList(history));
    }

    @Override
    public void onRowDeletePermanently(History history) {
        confirmPermanentDelete(Collections.singletonList(history));
    }

    /**
     * Confirms the reversible removal.
     *
     * <p>Named for what it does rather than "delete", because the record survives
     * in the trash and the header offers a restore.
     */
    private void confirmMoveToTrash(List<History> rows) {
        final int count = rows.size();
        new AlertDialog.Builder(requireContext())
                .setTitle(count == 1 ? "Move record to trash?" : "Move " + count + " records to trash?")
                .setMessage(count == 1
                        ? "The record leaves the active history and can be restored from Trash."
                        : "These records leave the active history and can be restored from Trash.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Move to trash", (d, w) ->
                        repository.softDeleteAdminHistory(rows, actionCallback("Moved to trash")))
                .show();
    }

    private void confirmRestore(List<History> rows) {
        new AlertDialog.Builder(requireContext())
                .setTitle(rows.size() == 1 ? "Restore record?" : "Restore " + rows.size() + " records?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore", (d, w) ->
                        repository.restoreAdminHistory(rows, actionCallback("Restored")))
                .show();
    }

    /**
     * Confirms the irreversible removal.
     *
     * <p>This is the one action on the screen that cannot be undone, so it is
     * worded as a loss rather than as a tidy-up and the confirm button says
     * "Delete forever".
     */
    private void confirmPermanentDelete(List<History> rows) {
        final int count = rows.size();
        new AlertDialog.Builder(requireContext())
                .setTitle(count == 1 ? "Delete record permanently?" : "Delete " + count + " records permanently?")
                .setMessage(count == 1
                        ? "This cannot be undone. The audit record will be removed from the database."
                        : "This cannot be undone. These audit records will be removed from the database.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete forever", (d, w) ->
                        repository.deleteAdminHistoryPermanently(rows, actionCallback("Deleted permanently")))
                .show();
    }

    /**
     * Reports what the server actually did.
     *
     * <p>The success toast is only shown when the server confirmed the write. A
     * mutation that matched nothing used to be reported as a success, which is how
     * a delete could appear to work and then reappear on the next pull.
     */
    private ReportRepository.HistoryActionCallback actionCallback(String successMessage) {
        return new ReportRepository.HistoryActionCallback() {
            @Override
            public void onHistoryActionComplete(int succeeded, int failed) {
                if (getView() == null) return;
                adapter.clearSelection();
                if (succeeded > 0) {
                    Toast.makeText(getContext(),
                            failed > 0 ? successMessage + " (" + succeeded + " of "
                                    + (succeeded + failed) + ")" : successMessage,
                            Toast.LENGTH_SHORT).show();
                }
                if (failed > 0) {
                    Toast.makeText(getContext(),
                            succeeded > 0
                                    ? failed + " record(s) could not be changed."
                                    : "Nothing was changed. The server matched none of those records.",
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onHistoryActionFailed() {
                if (getView() == null) return;
                Toast.makeText(getContext(),
                        "Could not reach the server. Nothing was changed.",
                        Toast.LENGTH_LONG).show();
            }
        };
    }

    /**
     * Opens the full record behind a card.
     *
     * <p>Keeps every card to a title, a report reference and a timestamp, which is
     * the point of the compact layout: the detail lives one tap away rather than
     * making every row a wall of text.
     */
    private void showDetails(History history) {
        if (history == null) return;

        StringBuilder body = new StringBuilder();
        appendLine(body, "Action", AdminHistoryClassifier.title(history.getAction()));
        appendLine(body, "Category",
                AdminHistoryClassifier.categorize(history.getAction()).getLabel());
        appendLine(body, "Recorded", AdminHistoryDateFormatter.format(history.getTimestampValue()));

        if (history.getReportId() != null && !history.getReportId().trim().isEmpty()) {
            appendLine(body, "Report", "#" + history.getReportId().trim());
        }
        if (history.getOldStatus() != null && !history.getOldStatus().trim().isEmpty()) {
            appendLine(body, "Previous status", history.getOldStatus().trim());
        }
        if (history.getNewStatus() != null && !history.getNewStatus().trim().isEmpty()) {
            appendLine(body, "New status", history.getNewStatus().trim());
        }
        if (history.getActorId() != null && !history.getActorId().trim().isEmpty()) {
            appendLine(body, "By", history.getActorId().trim());
        }
        if (history.getActionDetails() != null
                && !history.getActionDetails().trim().isEmpty()) {
            appendLine(body, "Details", history.getActionDetails().trim());
        }
        if (history.getDescription() != null && !history.getDescription().trim().isEmpty()) {
            appendLine(body, "Description", history.getDescription().trim());
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("History Details")
                .setMessage(body.toString().trim())
                .setPositiveButton("Close", null)
                .show();
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        builder.append(label).append(": ").append(value.trim()).append("\n\n");
    }

    // ------------------------------------------------------------------ data

    /**
     * Observes the cached audit feed and pulls the current tab.
     *
     * <p>Reads from {@code GET /api/admin/history}, not the resident-scoped route.
     * An administrator's trail spans every resident, and decisions are recorded
     * against the resident who owns the report, so the resident route returns
     * nothing for an admin token.
     */
    private void loadHistoryData() {
        // Observed exactly once for the life of the screen. Every active row is
        // read, unfiltered, and the tab is applied locally in render(). An earlier
        // version re-observed per tab with the category in the query, which meant
        // the All tab asked for category='ALL' — a value no row carries — and
        // therefore matched nothing at all.
        repository.observeAdminFeed()
                .observe(getViewLifecycleOwner(), rows -> {
                    activeHistory = rows == null ? Collections.emptyList() : rows;
                    render();
                });

        repository.observeAdminTrash()
                .observe(getViewLifecycleOwner(), rows -> {
                    trashHistory = rows == null ? Collections.emptyList() : rows;
                    render();
                });

        refreshFromServer();
    }

    /** Pulls whichever tab is showing, plus the trash when that is what is open. */
    private void refreshFromServer() {
        if (isTrashView) {
            repository.fetchAdminHistoryTrash();
            return;
        }
        // The All tab is sent as null rather than "ALL": the server treats a null
        // category as no filter, which is what All means, and it keeps the
        // uncategorised-row fallback in the server's hands.
        String category = currentTab == AdminHistoryCategory.ALL
                ? null
                : currentTab.name();

        repository.fetchAdminFeed(category, searchQuery, dateFromInclusive, dateToInclusive);
    }
}
