package com.example.ecowatchadmin1.ui.alerts;

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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Notification;
import com.example.ecowatchadmin1.ui.nav.DashboardNavigator;
import com.example.ecowatchadmin1.utils.NotificationDateFormatter;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Cloned from the Resident app. Tapping a row marks the notification read and
 * deep-links to the matching Report Details screen; the Trash tab exposes
 * restore and permanent delete exactly as in the Resident build.
 */
public class NotificationsFragment extends Fragment {
    private NotificationsViewModel viewModel;
    private NotificationsAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private TextView tabAll, tabTrash;
    private EditText searchEditText;
    private ImageView filterBtn, selectModeBtn, markAllReadBtn;
    private LinearLayout selectionControlLayout, bulkActionsBar;
    private CheckBox selectAllCheckbox;
    private TextView selectionCountText, noNotifsText;
    private MaterialButton bulkRestoreBtn, bulkDeleteBtn;

    private List<Notification> currentList = new ArrayList<>();
    private String currentTab = "All";
    private Calendar fromDate, toDate;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_notifications, container, false);
        viewModel = new ViewModelProvider(this).get(NotificationsViewModel.class);
        initViews(v);
        setupRecycler();
        setupTabs();
        setupSearch();
        setupBulkActions();
        updateTabUI();
        observeViewModel();

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.refresh();
    }

    private void initViews(View v) {
        swipeRefreshLayout = v.findViewById(R.id.swipeRefreshLayout);
        recyclerView = v.findViewById(R.id.notificationsRecyclerView);
        tabAll = v.findViewById(R.id.tabAll);
        tabTrash = v.findViewById(R.id.tabTrash);
        searchEditText = v.findViewById(R.id.searchEditText);
        filterBtn = v.findViewById(R.id.filterBtn);
        selectModeBtn = v.findViewById(R.id.selectModeBtn);
        markAllReadBtn = v.findViewById(R.id.markAllReadBtn);
        selectionControlLayout = v.findViewById(R.id.selectionControlLayout);
        bulkActionsBar = v.findViewById(R.id.bulkActionsBar);
        selectAllCheckbox = v.findViewById(R.id.selectAllCheckbox);
        selectionCountText = v.findViewById(R.id.selectionCountText);
        noNotifsText = v.findViewById(R.id.noNotifsText);
        bulkRestoreBtn = v.findViewById(R.id.bulkRestoreBtn);
        bulkDeleteBtn = v.findViewById(R.id.bulkDeleteBtn);

        selectModeBtn.setOnClickListener(view -> toggleSelection());
        filterBtn.setOnClickListener(view -> showDateFilter());
        markAllReadBtn.setOnClickListener(view -> {
            viewModel.markAllAsRead();
            Toast.makeText(getContext(), "All notifications marked as read", Toast.LENGTH_SHORT).show();
        });
        swipeRefreshLayout.setOnRefreshListener(() -> viewModel.refresh());
    }

    private void toggleSelection() {
        boolean mode = !selectionControlLayout.isShown();
        adapter.setSelectionMode(mode);
        selectionControlLayout.setVisibility(mode ? View.VISIBLE : View.GONE);
        selectAllCheckbox.setChecked(false);
        if (!mode) bulkActionsBar.setVisibility(View.GONE);
    }

    private void setupRecycler() {
        adapter = new NotificationsAdapter(new NotificationsAdapter.OnNotifListener() {
            @Override
            public void onNotificationClick(Notification notification) {
                viewModel.markAsRead(notification);
                if (notification.getReportId() != null
                        && !notification.getReportId().trim().isEmpty()
                        && getActivity() instanceof DashboardNavigator) {
                    ((DashboardNavigator) getActivity()).openReportById(
                            notification.getReportId()
                    );
                }
            }

            @Override
            public void onDelete(Notification n) {
                if ("Trash".equals(currentTab)) viewModel.permanentDelete(n);
                else viewModel.softDelete(n);
            }
            @Override
            public void onRestore(Notification n) { viewModel.restore(n); }
            @Override
            public void onSelectionChanged(int count) {
                selectionCountText.setText(count + " selected");
                bulkActionsBar.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                bulkRestoreBtn.setVisibility("Trash".equals(currentTab) ? View.VISIBLE : View.GONE);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setupBulkActions() {
        selectAllCheckbox.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) adapter.selectAll();
            else adapter.unselectAll();
        });
        bulkRestoreBtn.setOnClickListener(v -> {
            for (Notification notification : selectedNotifications()) {
                viewModel.restore(notification);
            }
            exitSelectionMode();
        });
        bulkDeleteBtn.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle("Delete Notifications")
                .setMessage("Permanently delete the selected notifications?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    for (Notification notification : selectedNotifications()) {
                        if ("Trash".equals(currentTab)) {
                            viewModel.permanentDelete(notification);
                        } else {
                            viewModel.softDelete(notification);
                        }
                    }
                    exitSelectionMode();
                })
                .show());
    }

    private List<Notification> selectedNotifications() {
        List<Integer> selected = adapter.getSelectedIds();
        List<Notification> result = new ArrayList<>();
        for (Notification notification : currentList) {
            if (selected.contains(notification.getId())) result.add(notification);
        }
        return result;
    }

    private void exitSelectionMode() {
        adapter.setSelectionMode(false);
        selectionControlLayout.setVisibility(View.GONE);
        bulkActionsBar.setVisibility(View.GONE);
        selectAllCheckbox.setChecked(false);
        selectionCountText.setText("0 selected");
    }

    private void setupTabs() {
        tabAll.setOnClickListener(v -> selectTab("All"));
        tabTrash.setOnClickListener(v -> selectTab("Trash"));
    }

    private void selectTab(String tab) {
        currentTab = tab;
        updateTabUI();
        adapter.setTab(tab);
        loadData();
    }

    private void updateTabUI() {
        tabAll.setBackgroundResource("All".equals(currentTab) ? R.drawable.active_pill_background : 0);
        tabAll.setTextColor(getResources().getColor("All".equals(currentTab) ? R.color.white : R.color.green, null));
        tabTrash.setBackgroundResource("Trash".equals(currentTab) ? R.drawable.active_pill_background : 0);
        tabTrash.setTextColor(getResources().getColor("Trash".equals(currentTab) ? R.color.white : R.color.green, null));
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void observeViewModel() {
        viewModel.getAllNotifications().observe(getViewLifecycleOwner(), list -> { if ("All".equals(currentTab)) handleUpdate(list); tabAll.setText("All ("+(list!=null?list.size():0)+")"); });
        viewModel.getTrashNotifications().observe(getViewLifecycleOwner(), list -> { if ("Trash".equals(currentTab)) handleUpdate(list); tabTrash.setText("Trash ("+(list!=null?list.size():0)+")"); });
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(Boolean.TRUE.equals(loading));
        });
        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.trim().isEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadData() {
        if ("All".equals(currentTab)) handleUpdate(viewModel.getAllNotifications().getValue());
        else handleUpdate(viewModel.getTrashNotifications().getValue());
    }

    private void handleUpdate(List<Notification> list) {
        currentList = list != null ? list : new ArrayList<>();
        filter(searchEditText.getText().toString());
        swipeRefreshLayout.setRefreshing(false);
    }

    private void filter(String q) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.getDefault());
        List<Notification> f = currentList.stream()
                .filter(n -> {
                    String title = n.getTitle() == null ? "" : n.getTitle().toLowerCase(Locale.getDefault());
                    String message = n.getMessage() == null ? "" : n.getMessage().toLowerCase(Locale.getDefault());
                    String reportId = n.getReportId() == null ? "" : n.getReportId().toLowerCase(Locale.getDefault());
                    return query.isEmpty()
                            || title.contains(query)
                            || message.contains(query)
                            || reportId.contains(query);
                })
                .collect(Collectors.toList());
        adapter.setList(f);
        noNotifsText.setVisibility(f.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showDateFilter() {
        View dv = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_date_filter, null);
        RadioGroup rg = dv.findViewById(R.id.dateRadioGroup);
        RadioGroup sg = dv.findViewById(R.id.sortRadioGroup);
        LinearLayout customDateLayout = dv.findViewById(R.id.customDateLayout);
        TextView tvFrom = dv.findViewById(R.id.tvFromDate);
        TextView tvTo = dv.findViewById(R.id.tvToDate);
        View btnFrom = dv.findViewById(R.id.btnFromDate);
        View btnTo = dv.findViewById(R.id.btnToDate);
        View btnBack = dv.findViewById(R.id.btnBackDate);
        View btnReset = dv.findViewById(R.id.btnResetDate);
        MaterialButton btnApply = dv.findViewById(R.id.btnApplyFilter);

        // Hide "Recently Updated" and Categories logic as requested
        dv.findViewById(R.id.radioUpdated).setVisibility(View.GONE);

        AlertDialog d = new AlertDialog.Builder(requireContext()).setView(dv).create();
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        rg.setOnCheckedChangeListener((group, checkedId) ->
            customDateLayout.setVisibility(checkedId == R.id.radioCustom ? View.VISIBLE : View.GONE));

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

        btnFrom.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(requireContext(), (view, year, month, day) -> {
                fromDate = Calendar.getInstance();
                fromDate.set(year, month, day, 0, 0, 0);
                tvFrom.setText(sdf.format(fromDate.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnTo.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(requireContext(), (view, year, month, day) -> {
                toDate = Calendar.getInstance();
                toDate.set(year, month, day, 23, 59, 59);
                tvTo.setText(sdf.format(toDate.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnApply.setOnClickListener(view -> {
            applyDateFilter(rg.getCheckedRadioButtonId(), sg.getCheckedRadioButtonId());
            d.dismiss();
        });

        btnBack.setOnClickListener(v -> d.dismiss());

        btnReset.setOnClickListener(v -> {
            // Reset date filter selection
            rg.clearCheck();

            // Reset custom date range
            fromDate = null;
            toDate = null;

            // Restore the complete list for the current tab
            // while preserving the search text
            filter(searchEditText.getText().toString());

            // Close the filter dialog
            d.dismiss();
        });

        d.show();
    }

    private void applyDateFilter(int dateId, int sortId) {
        Calendar cal = Calendar.getInstance();
        Date startDate = null;

        if (dateId == R.id.radioToday) {
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            startDate = cal.getTime();
        } else if (dateId == R.id.radioLast7) {
            cal.add(Calendar.DAY_OF_YEAR, -7);
            startDate = cal.getTime();
        } else if (dateId == R.id.radioLast30) {
            cal.add(Calendar.DAY_OF_YEAR, -30);
            startDate = cal.getTime();
        } else if (dateId == R.id.radioLast90) {
            cal.add(Calendar.DAY_OF_YEAR, -90);
            startDate = cal.getTime();
        } else if (dateId == R.id.radioLast180) {
            cal.add(Calendar.DAY_OF_YEAR, -180);
            startDate = cal.getTime();
        }

        final Date finalStart = startDate;

        List<Notification> filtered = currentList.stream().filter(n -> {
            if (dateId == -1) return true;
            Date date = NotificationDateFormatter.parse(n.getCreatedAt());
            if (date == null) return false;
            if (dateId == R.id.radioCustom) {
                if (fromDate == null || toDate == null) return true;
                return !date.before(fromDate.getTime()) && !date.after(toDate.getTime());
            }
            return finalStart != null && !date.before(finalStart);
        }).collect(Collectors.toList());

        Collections.sort(filtered, (n1, n2) -> {
            String d1 = n1.getCreatedAt();
            String d2 = n2.getCreatedAt();
            if (d1 == null) return 1;
            if (d2 == null) return -1;
            return sortId == R.id.radioOldest ? d1.compareTo(d2) : d2.compareTo(d1);
        });

        adapter.setList(filtered);
        noNotifsText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
