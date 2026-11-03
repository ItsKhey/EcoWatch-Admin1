package com.example.ecowatchadmin1.ui.reportsanalytics;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.ui.reports.ReportDetailDialog;
import com.example.ecowatchadmin1.ui.reports.ReportsViewModel;
import com.example.ecowatchadmin1.utils.ReportSubmittedAtFormatter;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ReportsAnalyticsFragment extends Fragment {

    private static final SimpleDateFormat ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
    private static final SimpleDateFormat DISPLAY_DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    private static final SimpleDateFormat DAY_FORMAT = new SimpleDateFormat("MMM dd", Locale.US);
    private static final SimpleDateFormat MONTH_FORMAT = new SimpleDateFormat("MMM yyyy", Locale.US);

    /** Anything inside this radius of a hotspot centre belongs to the same spot. */
    private static final int HOTSPOT_RADIUS_METERS = 250;

    /** The ranking shows at most this many spots. */
    private static final int HOTSPOT_LIMIT = 3;

    /**
     * A cleaned spot stops counting as a hotspot once its completion is this
     * old, so finished cleanups do not dominate the ranking forever. Pending and
     * in-progress reports are not aged out: the waste they describe is still
     * there.
     */
    private static final int COMPLETED_HOTSPOT_WINDOW_DAYS = 30;

    private static final LatLng DEFAULT_MAP_CENTER = new LatLng(14.5995, 120.9842);

    static {
        ISO_FORMAT.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
        DISPLAY_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
        DAY_FORMAT.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
        MONTH_FORMAT.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
    }

    private ReportsViewModel reportsViewModel;
    private SwipeRefreshLayout swipeRefreshLayout;

    private Spinner periodSpinner;
    private LinearLayout customDateLayout;
    private Button btnStartDate;
    private Button btnEndDate;
    private Button btnApplyCustom;
    private Button btnResetPeriod;
    private TextView tvSelectedPeriod;

    private RecyclerView calendarHeatmapRecycler;
    private TextView tvCalendarPeriod;
    private TextView tvCalendarReportCount;
    private TextView tvCalendarMonthYear;
    private ImageButton btnCalendarPrevMonth;
    private ImageButton btnCalendarNextMonth;

    private String currentPeriod = "Daily";
    private Calendar selectedCalendarDate;
    private Calendar calendarMonth;
    private Calendar customStartDate;
    private Calendar customEndDate;
    private CalendarHeatmapAdapter calendarHeatmapAdapter;

    private TextView tvTotalReports;
    private TextView tvPendingReports;
    private TextView tvCompletedReports;
    private TextView tvActiveTeams;
    private TextView tvCompletionRate;
    private TextView tvAvgResolutionTime;

    private LineChart lineChartTrends;
    private PieChart pieChartStatus;
    private BarChart barChartWasteSize;
    private BarChart barChartWorkload;

    private GoogleMap hotspotMap;
    private TextView tvHotspotTotal;
    private TextView tvHotspotReports;
    private TextView tvHotspotEmpty;
    private LinearLayout llHotspotRanking;
    private final List<Hotspot> hotspots = new ArrayList<>();
    private final List<Marker> hotspotMarkers = new ArrayList<>();
    private boolean hotspotCameraInitialized;

    /**
     * Place names come from reverse geocoding the hotspot centre, which is a
     * blocking network call. Results are cached per rounded coordinate so a
     * recompute does not geocode the same spot again, and requests already in
     * flight are not duplicated when the report feed emits twice.
     */
    private final ExecutorService geocodeExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, String> geocodeCache =
            Collections.synchronizedMap(new HashMap<>());
    private final Set<String> pendingGeocodes =
            Collections.synchronizedSet(new HashSet<>());

    private RecyclerView recyclerDetailedRecords;
    private AnalyticsRecordsAdapter recordsAdapter;
    private TextView tvNoData;

    private List<Report> allReports = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reports_analytics, container, false);

        reportsViewModel = new ViewModelProvider(this).get(ReportsViewModel.class);

        initViews(view);
        setupReportCalendar();
        setupPeriodSelector();
        setupRecyclerView();
        setupSwipeRefresh();

        reportsViewModel.refreshReports();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        observeReports();
    }

    private void initViews(View view) {
        View btnBack = view.findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            });
        }

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        periodSpinner = view.findViewById(R.id.periodSpinner);
        customDateLayout = view.findViewById(R.id.customDateLayout);
        btnStartDate = view.findViewById(R.id.btnStartDate);
        btnEndDate = view.findViewById(R.id.btnEndDate);
        btnApplyCustom = view.findViewById(R.id.btnApplyCustom);
        btnResetPeriod = view.findViewById(R.id.btnResetPeriod);
        tvSelectedPeriod = view.findViewById(R.id.tvSelectedPeriod);

        calendarHeatmapRecycler = view.findViewById(R.id.calendarHeatmapRecycler);
        tvCalendarPeriod = view.findViewById(R.id.tvCalendarPeriod);
        tvCalendarReportCount = view.findViewById(R.id.tvCalendarReportCount);
        tvCalendarMonthYear = view.findViewById(R.id.tvCalendarMonthYear);
        btnCalendarPrevMonth = view.findViewById(R.id.btnCalendarPrevMonth);
        btnCalendarNextMonth = view.findViewById(R.id.btnCalendarNextMonth);

        tvTotalReports = view.findViewById(R.id.tvTotalReports);
        tvPendingReports = view.findViewById(R.id.tvPendingReports);
        tvCompletedReports = view.findViewById(R.id.tvCompletedReports);
        tvActiveTeams = view.findViewById(R.id.tvActiveTeams);
        tvCompletionRate = view.findViewById(R.id.tvCompletionRate);
        tvAvgResolutionTime = view.findViewById(R.id.tvAvgResolutionTime);

        lineChartTrends = view.findViewById(R.id.lineChartTrends);
        pieChartStatus = view.findViewById(R.id.pieChartStatus);
        barChartWasteSize = view.findViewById(R.id.barChartWasteSize);
        barChartWorkload = view.findViewById(R.id.barChartWorkload);

        tvHotspotTotal = view.findViewById(R.id.tvHotspotTotal);
        tvHotspotReports = view.findViewById(R.id.tvHotspotReports);
        tvHotspotEmpty = view.findViewById(R.id.tvHotspotEmpty);
        llHotspotRanking = view.findViewById(R.id.llHotspotRanking);

        recyclerDetailedRecords = view.findViewById(R.id.recyclerDetailedRecords);
        tvNoData = view.findViewById(R.id.tvNoData);

        setupCharts();
        setupHotspotMap();
    }


    private void setupCharts() {
        boolean isDarkMode = (getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int chartTextColor = isDarkMode ? Color.WHITE : Color.parseColor("#06402B");

        if (lineChartTrends != null) {
            lineChartTrends.getDescription().setEnabled(false);
            lineChartTrends.setTouchEnabled(true);
            lineChartTrends.setDragEnabled(true);
            lineChartTrends.setScaleEnabled(true);

            // --- Match Donut Graph Legend & Axis Text Behavior ---
            Legend lineLegend = lineChartTrends.getLegend();
            lineLegend.setEnabled(true);
            lineLegend.setTextColor(chartTextColor);

            XAxis xAxis = lineChartTrends.getXAxis();
            xAxis.setTextColor(chartTextColor);

            lineChartTrends.getAxisLeft().setTextColor(chartTextColor);
            lineChartTrends.getAxisRight().setTextColor(chartTextColor);
            // ----------------------------------------------------
        }

        if (pieChartStatus != null) {
            pieChartStatus.getDescription().setEnabled(false);
            pieChartStatus.setUsePercentValues(true);

            // --- Remove the glowing/transparent circle effects ---
            pieChartStatus.setDrawHoleEnabled(true);
            pieChartStatus.setHoleColor(Color.WHITE);
            pieChartStatus.setTransparentCircleRadius(0f);
            // ----------------------------------------------------

            pieChartStatus.setDrawCenterText(true);
            pieChartStatus.setRotationEnabled(true);
            pieChartStatus.setHighlightPerTapEnabled(true);

            Legend l = pieChartStatus.getLegend();
            l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
            l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
            l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
            l.setDrawInside(false);

            if (isDarkMode) {
                l.setTextColor(Color.WHITE); // White text for dark mode
            } else {
                l.setTextColor(Color.parseColor("#06402B")); // Custom color for light mode
            }
        }

        if (barChartWasteSize != null || barChartWorkload != null) {
            Description desc = new Description();
            desc.setText("");
            if (barChartWasteSize != null) {
                barChartWasteSize.setDescription(desc);
                barChartWasteSize.getLegend().setEnabled(false);
            }
            if (barChartWorkload != null) {
                barChartWorkload.setDescription(desc);
                barChartWorkload.getLegend().setEnabled(false);
            }
        }
    }

    private void setupReportCalendar() {
        selectedCalendarDate = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
        setStartOfDay(selectedCalendarDate);
        calendarMonth = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
        calendarMonth.setTimeInMillis(selectedCalendarDate.getTimeInMillis());
        setStartOfDay(calendarMonth);
        calendarMonth.set(Calendar.DAY_OF_MONTH, 1);

        if (calendarHeatmapRecycler != null) {
            calendarHeatmapRecycler.setLayoutManager(new GridLayoutManager(getContext(), 7));
            calendarHeatmapAdapter = new CalendarHeatmapAdapter(new ArrayList<>(), dayLabel -> {
                try {
                    selectedCalendarDate = (Calendar) calendarMonth.clone();
                    selectedCalendarDate.set(Calendar.DAY_OF_MONTH, Integer.parseInt(dayLabel));
                    setStartOfDay(selectedCalendarDate);
                    refreshAnalytics();
                    updateCalendarHeatmap();
                } catch (NumberFormatException ignored) {
                    // Blank cells are not selectable.
                }
            });
            calendarHeatmapRecycler.setAdapter(calendarHeatmapAdapter);
        }

        if (btnCalendarPrevMonth != null) {
            btnCalendarPrevMonth.setOnClickListener(v -> {
                calendarMonth.add(Calendar.MONTH, -1);
                calendarMonth.set(Calendar.DAY_OF_MONTH, 1);
                updateCalendarHeatmap();
            });
        }
        if (btnCalendarNextMonth != null) {
            btnCalendarNextMonth.setOnClickListener(v -> {
                calendarMonth.add(Calendar.MONTH, 1);
                calendarMonth.set(Calendar.DAY_OF_MONTH, 1);
                updateCalendarHeatmap();
            });
        }

        updateCalendarHeatmap();
    }

    private void setupPeriodSelector() {
        String[] periods = {"Daily", "Weekly", "Monthly", "Annual", "Custom"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, periods);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        periodSpinner.setAdapter(adapter);

        periodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentPeriod = periods[position];
                if ("Custom".equals(currentPeriod)) {
                    if (customDateLayout != null) {
                        customDateLayout.setVisibility(View.VISIBLE);
                    }
                    if (customStartDate == null) {
                        customStartDate = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
                        customStartDate.add(Calendar.DAY_OF_MONTH, -7);
                    }
                    if (customEndDate == null) {
                        customEndDate = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
                    }
                    updateCustomDateButtons();
                } else {
                    if (customDateLayout != null) {
                        customDateLayout.setVisibility(View.GONE);
                    }
                    refreshAnalytics();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        if (btnStartDate != null) btnStartDate.setOnClickListener(v -> showDatePicker(true));
        if (btnEndDate != null) btnEndDate.setOnClickListener(v -> showDatePicker(false));
        if (btnApplyCustom != null) btnApplyCustom.setOnClickListener(v -> {
            if (customStartDate == null || customEndDate == null) {
                Toast.makeText(getContext(), "Please select both dates", Toast.LENGTH_SHORT).show();
                return;
            }
            if (customStartDate.after(customEndDate)) {
                Toast.makeText(getContext(), "Start date must be before or equal to end date", Toast.LENGTH_SHORT).show();
                return;
            }
            Calendar start = (Calendar) customStartDate.clone();
            setStartOfDay(start);
            customStartDate = start;
            Calendar end = (Calendar) customEndDate.clone();
            setEndOfDay(end);
            customEndDate = end;
            if (tvSelectedPeriod != null) {
                tvSelectedPeriod.setText(DISPLAY_DATE_FORMAT.format(customStartDate.getTime()) + " \u2013 " + DISPLAY_DATE_FORMAT.format(customEndDate.getTime()));
            }
            refreshAnalytics();
        });

        if (btnResetPeriod != null) btnResetPeriod.setOnClickListener(v -> {
            periodSpinner.setSelection(0);
            if (customDateLayout != null) {
                customDateLayout.setVisibility(View.GONE);
            }
            currentPeriod = "Daily";
            selectedCalendarDate = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
            setStartOfDay(selectedCalendarDate);
            calendarMonth = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
            calendarMonth.setTimeInMillis(selectedCalendarDate.getTimeInMillis());
            setStartOfDay(calendarMonth);
            calendarMonth.set(Calendar.DAY_OF_MONTH, 1);
            updateCalendarHeatmap();
            refreshAnalytics();
        });
    }

    private void setupRecyclerView() {
        if (recyclerDetailedRecords == null) return;
        recyclerDetailedRecords.setLayoutManager(new LinearLayoutManager(requireContext()));
        recordsAdapter = new AnalyticsRecordsAdapter(this);
        recyclerDetailedRecords.setAdapter(recordsAdapter);
    }

    private void setupSwipeRefresh() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> reportsViewModel.refreshReports());
        }
    }

    private void observeReports() {
        reportsViewModel.getAllReports().observe(getViewLifecycleOwner(), reports -> {
            allReports = reports == null ? new ArrayList<>() : new ArrayList<>(reports);
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            updateHotspots();
            updateCalendarHeatmap();
            refreshAnalytics();
        });
        reportsViewModel.getReportsError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.trim().isEmpty()) {
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshAnalytics() {
        List<Report> reports = reportsForSelectedPeriod();
        int total = reports.size();
        int completed = 0;
        int pending = 0;
        java.util.Set<String> activeTeams = new java.util.HashSet<>();
        long resolutionMillis = 0L;
        int resolvedCount = 0;

        for (Report report : reports) {
            // Safe check on status matching your backend values (e.g., "resolved", "completed")
            String status = safe(report.getStatus()).toLowerCase(Locale.ROOT);

            // --- 1. Completion Status Check ---
            if (status.contains("complete") || status.contains("resolved")) {
                completed++;

                // Parse creation time and resolution time
                Date created = ReportSubmittedAtFormatter.parse(report.getCreatedAt());
                Date resolved = ReportSubmittedAtFormatter.parse(
                        firstNonEmpty(report.getResolvedAt(), report.getUpdatedAt()));

                if (created != null && resolved != null && !resolved.before(created)) {
                    resolutionMillis += resolved.getTime() - created.getTime();
                    resolvedCount++;
                }
            } else if (status.contains("pending")) {
                pending++;
            }

            if (status.contains("progress") && !safe(report.getAssignedTeamId()).isEmpty()) {
                activeTeams.add(report.getAssignedTeamId());
            }
        }

        // Set basic counts
        if (tvTotalReports != null) tvTotalReports.setText(String.valueOf(total));
        if (tvPendingReports != null) tvPendingReports.setText(String.valueOf(pending));
        if (tvCompletedReports != null) tvCompletedReports.setText(String.valueOf(completed));
        if (tvActiveTeams != null) tvActiveTeams.setText(String.valueOf(activeTeams.size()));

        // --- 2. Wiring Up Completion Rate ---
        if (tvCompletionRate != null) {
            if (total == 0) {
                tvCompletionRate.setText("0.0%");
            } else {
                float completionRate = (completed * 100f) / total;
                tvCompletionRate.setText(String.format(Locale.getDefault(), "%.1f%%", completionRate));
            }
        }

        // --- 3. Wiring Up Average Resolution Time ---
        if (tvAvgResolutionTime != null) {
            if (resolvedCount == 0) {
                tvAvgResolutionTime.setText("N/A");
            } else {
                long avgMillis = resolutionMillis / resolvedCount;
                double avgHours = avgMillis / (1000.0 * 60 * 60);

                if (avgHours >= 24) {
                    tvAvgResolutionTime.setText(String.format(Locale.getDefault(), "%.1f days", avgHours / 24.0));
                } else {
                    tvAvgResolutionTime.setText(String.format(Locale.getDefault(), "%.1f hrs", avgHours));
                }
            }
        }

        updateCharts(reports);
        if (recordsAdapter != null) recordsAdapter.submit(reports);
        if (tvNoData != null) tvNoData.setVisibility(reports.isEmpty() ? View.VISIBLE : View.GONE);
        if (recyclerDetailedRecords != null) {
            recyclerDetailedRecords.setVisibility(reports.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private List<Report> reportsForSelectedPeriod() {
        List<Report> result = new ArrayList<>();
        Calendar start = (Calendar) selectedCalendarDate.clone();
        Calendar end = (Calendar) selectedCalendarDate.clone();
        if ("Weekly".equals(currentPeriod)) {
            start.setFirstDayOfWeek(Calendar.MONDAY);
            start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            setStartOfDay(start);
            end.setTimeInMillis(start.getTimeInMillis());
            end.add(Calendar.DAY_OF_YEAR, 6);
            setEndOfDay(end);
        } else if ("Monthly".equals(currentPeriod)) {
            start.set(Calendar.DAY_OF_MONTH, 1);
            setStartOfDay(start);
            end.setTimeInMillis(start.getTimeInMillis());
            end.add(Calendar.MONTH, 1);
            end.add(Calendar.MILLISECOND, -1);
        } else if ("Annual".equals(currentPeriod)) {
            start.set(Calendar.DAY_OF_YEAR, 1);
            setStartOfDay(start);
            end.setTimeInMillis(start.getTimeInMillis());
            end.add(Calendar.YEAR, 1);
            end.add(Calendar.MILLISECOND, -1);
        } else if ("Custom".equals(currentPeriod) && customStartDate != null && customEndDate != null) {
            start.setTimeInMillis(customStartDate.getTimeInMillis());
            end.setTimeInMillis(customEndDate.getTimeInMillis());
            setStartOfDay(start);
            setEndOfDay(end);
        } else {
            setStartOfDay(start);
            setEndOfDay(end);
        }
        for (Report report : allReports) {
            Date created = ReportSubmittedAtFormatter.parse(report.getCreatedAt());
            if (created != null && !created.before(start.getTime()) && !created.after(end.getTime())) {
                result.add(report);
            }
        }
        result.sort((left, right) -> {
            Date l = ReportSubmittedAtFormatter.parse(left.getCreatedAt());
            Date r = ReportSubmittedAtFormatter.parse(right.getCreatedAt());
            if (l == null) return r == null ? 0 : 1;
            return r == null ? -1 : r.compareTo(l);
        });
        return result;
    }

    private void updateCalendarHeatmap() {
        if (calendarMonth == null) return;
        if (tvCalendarMonthYear != null) tvCalendarMonthYear.setText(MONTH_FORMAT.format(calendarMonth.getTime()));
        if (tvCalendarPeriod != null) tvCalendarPeriod.setText(currentPeriod);
        Map<Integer, Integer> counts = new HashMap<>();
        for (Report report : allReports) {
            Date date = ReportSubmittedAtFormatter.parse(report.getCreatedAt());
            if (date == null) continue;
            Calendar submitted = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
            submitted.setTime(date);
            if (submitted.get(Calendar.YEAR) == calendarMonth.get(Calendar.YEAR)
                    && submitted.get(Calendar.MONTH) == calendarMonth.get(Calendar.MONTH)) {
                int day = submitted.get(Calendar.DAY_OF_MONTH);
                counts.put(day, counts.getOrDefault(day, 0) + 1);
            }
        }
        Calendar first = (Calendar) calendarMonth.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);

        int currentYear = calendarMonth.get(Calendar.YEAR);
        int currentMonth = calendarMonth.get(Calendar.MONTH);

        // The tapped day survives month navigation, so only flag it when it is
        // actually inside the displayed month.
        int highlightedDay = selectedCalendarDate != null
                && selectedCalendarDate.get(Calendar.YEAR) == currentYear
                && selectedCalendarDate.get(Calendar.MONTH) == currentMonth
                ? selectedCalendarDate.get(Calendar.DAY_OF_MONTH) : -1;

        List<CalendarHeatmapAdapter.Day> daysInMonth = new ArrayList<>();
        for (int i = 0; i < offset; i++) {
            daysInMonth.add(new CalendarHeatmapAdapter.Day("", 0, false, -1, -1, -1, false));
        }
        for (int day = 1; day <= days; day++) {
            daysInMonth.add(new CalendarHeatmapAdapter.Day(
                    String.valueOf(day),
                    counts.getOrDefault(day, 0),
                    true,
                    currentYear,
                    currentMonth,
                    day,
                    day == highlightedDay
            ));
        }
        while (daysInMonth.size() % 7 != 0) {
            daysInMonth.add(new CalendarHeatmapAdapter.Day("", 0, false, -1, -1, -1, false));
        }
        if (calendarHeatmapAdapter != null) calendarHeatmapAdapter.submit(daysInMonth);
        Calendar selected = (Calendar) selectedCalendarDate.clone();
        int selectedCount = selected.get(Calendar.YEAR) == calendarMonth.get(Calendar.YEAR)
                && selected.get(Calendar.MONTH) == calendarMonth.get(Calendar.MONTH)
                ? counts.getOrDefault(selected.get(Calendar.DAY_OF_MONTH), 0) : 0;
        if (tvCalendarReportCount != null) {
            tvCalendarReportCount.setText(selectedCount + " reports on selected date");
        }
    }

    /* ---------------------------------------------------------------- hotspots */

    /**
     * Connects the map declared in the layout. The fragment is a child of this
     * screen, so the map follows this fragment's lifecycle automatically; only
     * the GoogleMap callback has to be registered.
     */
    private void setupHotspotMap() {
        SupportMapFragment mapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.hotspotMap);
        if (mapFragment == null) return;

        mapFragment.getMapAsync(googleMap -> {
            hotspotMap = googleMap;
            hotspotMap.getUiSettings().setMapToolbarEnabled(false);
            hotspotMap.getUiSettings().setZoomControlsEnabled(false);
            hotspotMap.getUiSettings().setRotateGesturesEnabled(false);
            hotspotMap.getUiSettings().setTiltGesturesEnabled(false);
            hotspotMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_MAP_CENTER, 11f));
            renderHotspots();
        });
    }

    /** Recomputes the ranking from the cached report feed and repaints the map. */
    private void updateHotspots() {
        List<Hotspot> computed = computeHotspots(hotspotEligibleReports());
        hotspots.clear();
        hotspots.addAll(computed);
        applyCachedHotspotNames();
        bindHotspotStats();
        bindHotspotRanking();
        renderHotspots();
        requestMissingHotspotNames();
    }

    /**
     * The reports allowed into a hotspot.
     *
     * <p>Pending, In Progress and Completed only. Denied reports are left out,
     * which is also what removes confirmed duplicates: marking a report as a
     * duplicate moves it to Denied. Rows repeated by the local cache are folded
     * together by server id. A Completed report stops counting once its
     * completion is more than 30 days old, because the waste it described has
     * already been cleared; open reports are never aged out.</p>
     */
    private List<Report> hotspotEligibleReports() {
        long cutoff = System.currentTimeMillis()
                - TimeUnit.DAYS.toMillis(COMPLETED_HOTSPOT_WINDOW_DAYS);
        List<Report> eligible = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Report report : allReports) {
            if (report == null || report.isDeleted() || report.isArchived()) continue;
            if (!isValidCoordinate(report.getLatitude(), report.getLongitude())) continue;

            String identity = firstNonEmpty(report.getReportId(), report.getMongoId());
            if (identity.isEmpty()) identity = "local:" + report.getId();
            if (!seen.add(identity)) continue;

            String status = safe(report.getStatus()).toLowerCase(Locale.ROOT);
            boolean pending = status.contains("pending");
            boolean inProgress = status.contains("progress");
            boolean completed = status.contains("complete") || status.contains("resolved");
            if (!pending && !inProgress && !completed) continue;

            if (completed) {
                String completedAt = firstNonEmpty(report.getResolvedAt(),
                        report.getWorkCompletedAt(), report.getUpdatedAt(),
                        report.getCreatedAt());
                if (timestampOf(completedAt) < cutoff) continue;
            }
            eligible.add(report);
        }
        return eligible;
    }

    /**
     * Groups reports into spots no wider than {@link #HOTSPOT_RADIUS_METERS}.
     * Each report joins the nearest existing centre it is within range of, and
     * the centre is kept as the running mean of the reports in the group. The
     * top spots by report count survive, capped at {@link #HOTSPOT_LIMIT}.
     */
    private List<Hotspot> computeHotspots(List<Report> reports) {
        List<Report> ordered = new ArrayList<>(reports);
        ordered.sort((left, right) -> Long.compare(
                timestampOf(left.getCreatedAt()), timestampOf(right.getCreatedAt())));

        List<Hotspot> clusters = new ArrayList<>();
        for (Report report : ordered) {
            Hotspot nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (Hotspot cluster : clusters) {
                double distance = distanceMeters(cluster.getLatitude(), cluster.getLongitude(),
                        report.getLatitude(), report.getLongitude());
                if (distance <= HOTSPOT_RADIUS_METERS && distance < nearestDistance) {
                    nearest = cluster;
                    nearestDistance = distance;
                }
            }
            if (nearest == null) {
                nearest = new Hotspot();
                clusters.add(nearest);
            }
            nearest.add(report);
        }

        clusters.sort((left, right) -> {
            int byCount = Integer.compare(right.getCount(), left.getCount());
            if (byCount != 0) return byCount;
            int byFreshness = Long.compare(right.latestMillis, left.latestMillis);
            if (byFreshness != 0) return byFreshness;
            return left.titleFallbackName().compareToIgnoreCase(right.titleFallbackName());
        });

        return clusters.size() > HOTSPOT_LIMIT
                ? new ArrayList<>(clusters.subList(0, HOTSPOT_LIMIT))
                : clusters;
    }

    private void bindHotspotStats() {
        int totalReports = 0;
        for (Hotspot hotspot : hotspots) totalReports += hotspot.getCount();

        if (tvHotspotTotal != null) tvHotspotTotal.setText(String.valueOf(hotspots.size()));
        if (tvHotspotReports != null) tvHotspotReports.setText(String.valueOf(totalReports));
        if (tvHotspotEmpty != null) {
            tvHotspotEmpty.setVisibility(hotspots.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void bindHotspotRanking() {
        if (llHotspotRanking == null) return;
        llHotspotRanking.removeAllViews();

        if (hotspots.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("No active hotspots.");
            empty.setTextColor(ContextCompat.getColor(requireContext(), R.color.faint_green_color));
            empty.setTextSize(12f);
            int padding = Math.round(8 * getResources().getDisplayMetrics().density);
            empty.setPadding(0, padding, 0, padding);
            llHotspotRanking.addView(empty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < hotspots.size(); i++) {
            Hotspot hotspot = hotspots.get(i);
            View row = inflater.inflate(R.layout.item_hotspot_ranking, llHotspotRanking, false);
            ((TextView) row.findViewById(R.id.tvHotspotRank)).setText((i + 1) + ".");
            ((TextView) row.findViewById(R.id.tvHotspotName)).setText(hotspot.displayName());
            ((TextView) row.findViewById(R.id.tvHotspotCount)).setText(String.valueOf(hotspot.getCount()));
            // The card already draws a rule below the list, so the last row's
            // own divider would show as a double line.
            row.findViewById(R.id.hotspotRowDivider)
                    .setVisibility(i == hotspots.size() - 1 ? View.GONE : View.VISIBLE);
            llHotspotRanking.addView(row);
        }
    }

    /**
     * Fills in the place names that have already been geocoded, so a recompute
     * does not flash back to the fallback label while the cache is warm.
     */
    private void applyCachedHotspotNames() {
        for (Hotspot hotspot : hotspots) {
            String name = geocodeCache.get(hotspotKey(hotspot.getLatitude(), hotspot.getLongitude()));
            if (name != null) hotspot.resolvedName = name;
        }
    }

    /** Resolves a readable place name for each hotspot that does not have one yet. */
    private void requestMissingHotspotNames() {
        final android.content.Context context = getContext();
        if (context == null) return;

        for (Hotspot hotspot : hotspots) {
            if (hotspot.resolvedName != null) continue;

            final String key = hotspotKey(hotspot.getLatitude(), hotspot.getLongitude());
            if (!pendingGeocodes.add(key)) continue;

            final double latitude = hotspot.getLatitude();
            final double longitude = hotspot.getLongitude();
            geocodeExecutor.execute(() -> {
                String name = reverseGeocode(context, latitude, longitude);
                if (name != null) geocodeCache.put(key, name);
                View root = getView();
                if (root == null) {
                    pendingGeocodes.remove(key);
                    return;
                }
                root.post(() -> {
                    pendingGeocodes.remove(key);
                    if (name != null) applyHotspotName(key, name);
                });
            });
        }
    }

    private void applyHotspotName(String key, String name) {
        if (!isAdded()) return;
        boolean changed = false;
        for (int i = 0; i < hotspots.size(); i++) {
            Hotspot hotspot = hotspots.get(i);
            if (!hotspotKey(hotspot.getLatitude(), hotspot.getLongitude()).equals(key)) continue;
            hotspot.resolvedName = name;
            changed = true;
            if (i < hotspotMarkers.size()) {
                hotspotMarkers.get(i).setTitle((i + 1) + ". " + name);
            }
        }
        if (changed) bindHotspotRanking();
    }

    /**
     * Turns a hotspot centre into something a reader can recognise on the
     * ground. Barangay and city win because they answer "where is this spot";
     * the street is the next best detail, and the raw coordinates are only a
     * last resort when geocoding is unavailable.
     */
    private String reverseGeocode(android.content.Context context, double latitude, double longitude) {
        if (!Geocoder.isPresent()) return null;
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses == null || addresses.isEmpty()) return null;

            Address address = addresses.get(0);
            String subLocality = safe(address.getSubLocality());
            String locality = safe(address.getLocality());
            String thoroughfare = safe(address.getThoroughfare());

            if (!subLocality.isEmpty()) {
                String barangay = subLocality.toLowerCase(Locale.ROOT).startsWith("barangay")
                        ? subLocality
                        : "Barangay " + subLocality;
                return locality.isEmpty() ? barangay : barangay + ", " + locality;
            }
            if (!thoroughfare.isEmpty()) {
                return locality.isEmpty() ? thoroughfare : thoroughfare + ", " + locality;
            }
            if (!locality.isEmpty()) return locality;

            String line = safe(address.getAddressLine(0));
            return line.isEmpty() ? null : line;
        } catch (IOException | IllegalArgumentException unavailable) {
            return null;
        }
    }

    private static String hotspotKey(double latitude, double longitude) {
        return String.format(Locale.US, "%.4f,%.4f", latitude, longitude);
    }

    private void renderHotspots() {
        if (hotspotMap == null) return;
        hotspotMap.clear();
        hotspotMarkers.clear();

        // Rank colours descend from dark to light, matching the heatmap scale.
        int[] rankColors = {
                Color.parseColor("#2E7D32"),
                Color.parseColor("#66BB6A"),
                Color.parseColor("#81C784")
        };

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (int i = 0; i < hotspots.size(); i++) {
            Hotspot hotspot = hotspots.get(i);
            LatLng center = new LatLng(hotspot.getLatitude(), hotspot.getLongitude());
            int color = rankColors[Math.min(i, rankColors.length - 1)];

            hotspotMap.addCircle(new CircleOptions()
                    .center(center)
                    .radius(HOTSPOT_RADIUS_METERS)
                    .strokeWidth(3f)
                    .strokeColor(color)
                    .fillColor(withAlpha(color, 0x33)));

            hotspotMarkers.add(hotspotMap.addMarker(new MarkerOptions()
                    .position(center)
                    .title((i + 1) + ". " + hotspot.displayName())
                    .snippet(hotspot.getCount() + (hotspot.getCount() == 1
                            ? " report" : " reports"))
                    .icon(BitmapDescriptorFactory.defaultMarker(
                            BitmapDescriptorFactory.HUE_GREEN))));

            bounds.include(center);
            includeCircleBounds(bounds, hotspot.getLatitude(), hotspot.getLongitude());
        }

        if (hotspotCameraInitialized || hotspots.isEmpty()) return;
        hotspotCameraInitialized = true;

        // Frame the whole 250 m radius, not just the pin, so the circle that
        // defines the hotspot is not clipped by the short map card.
        try {
            hotspotMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 36));
        } catch (IllegalStateException notLaidOutYet) {
            // The map can report ready before the card has a size, and bounds
            // cannot be projected onto an empty viewport.
            hotspotMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    bounds.build().getCenter(), hotspots.size() == 1 ? 15f : 13f));
        }
    }

    /** Adds the corners of a hotspot's radius so the camera can frame the circle. */
    private static void includeCircleBounds(LatLngBounds.Builder bounds,
                                            double latitude, double longitude) {
        double latitudeDelta = HOTSPOT_RADIUS_METERS / 111320d;
        double cosine = Math.max(0.01d, Math.abs(Math.cos(Math.toRadians(latitude))));
        double longitudeDelta = HOTSPOT_RADIUS_METERS / (111320d * cosine);
        bounds.include(new LatLng(latitude + latitudeDelta, longitude + longitudeDelta));
        bounds.include(new LatLng(latitude - latitudeDelta, longitude - longitudeDelta));
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static double distanceMeters(double startLatitude, double startLongitude,
                                         double endLatitude, double endLongitude) {
        float[] result = new float[1];
        Location.distanceBetween(startLatitude, startLongitude,
                endLatitude, endLongitude, result);
        return result[0];
    }

    private static long timestampOf(String raw) {
        Date date = ReportSubmittedAtFormatter.parse(raw);
        return date == null ? 0L : date.getTime();
    }

    private static boolean isValidCoordinate(double latitude, double longitude) {
        return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180
                && !(latitude == 0 && longitude == 0);
    }

    /** One clustered waste spot, with the reports counted inside its radius. */
    private static final class Hotspot {
        private double sumLatitude;
        private double sumLongitude;
        private int count;
        private long latestMillis;
        private final Map<String, Integer> nameCounts = new HashMap<>();
        private final Map<String, Long> nameLatest = new HashMap<>();

        /** Reverse-geocoded place name, or null until that lookup finishes. */
        private String resolvedName;

        double getLatitude() {
            return count == 0 ? 0d : sumLatitude / count;
        }

        double getLongitude() {
            return count == 0 ? 0d : sumLongitude / count;
        }

        int getCount() {
            return count;
        }

        void add(Report report) {
            count++;
            sumLatitude += report.getLatitude();
            sumLongitude += report.getLongitude();

            long submitted = timestampOf(report.getCreatedAt());
            latestMillis = Math.max(latestMillis, submitted);

            String name = safe(report.getTitle());
            if (!name.isEmpty()) {
                nameCounts.merge(name, 1, Integer::sum);
                nameLatest.merge(name, submitted, Math::max);
            }
        }

        /** What the ranking and the marker call this spot. */
        String displayName() {
            return resolvedName != null ? resolvedName : titleFallbackName();
        }

        /**
         * The name the reports themselves use for this spot, taken as the title
         * repeated by the most reports. Ties go to the most recent title; a spot
         * whose reports are untitled falls back to its coordinates instead of
         * inventing a name.
         *
         * <p>Only a fallback: report titles are free text ("Report #31"), so a
         * reverse-geocoded place name is preferred whenever one is available.</p>
         */
        String titleFallbackName() {
            String best = "";
            int bestCount = 0;
            long bestLatest = Long.MIN_VALUE;
            for (Map.Entry<String, Integer> entry : nameCounts.entrySet()) {
                int occurrences = entry.getValue();
                long latest = nameLatest.getOrDefault(entry.getKey(), 0L);
                if (occurrences > bestCount
                        || (occurrences == bestCount && latest > bestLatest)) {
                    best = entry.getKey();
                    bestCount = occurrences;
                    bestLatest = latest;
                }
            }
            return best.isEmpty()
                    ? String.format(Locale.US, "%.5f, %.5f", getLatitude(), getLongitude())
                    : best;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // The map fragment is recreated with the next view, so forget the
        // callback target and let the camera frame the data again.
        hotspotMap = null;
        hotspotMarkers.clear();
        hotspotCameraInitialized = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        geocodeExecutor.shutdownNow();
    }

    private void showDatePicker(boolean selectingStart) {
        Calendar initial = selectingStart ? customStartDate : customEndDate;
        if (initial == null) initial = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
        Calendar value = initial;
        new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
            Calendar chosen = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
            chosen.set(year, month, day);
            setStartOfDay(chosen);
            if (selectingStart) customStartDate = chosen;
            else customEndDate = chosen;
            updateCustomDateButtons();
        }, value.get(Calendar.YEAR), value.get(Calendar.MONTH), value.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateCustomDateButtons() {
        if (btnStartDate != null && customStartDate != null) btnStartDate.setText(DISPLAY_DATE_FORMAT.format(customStartDate.getTime()));
        if (btnEndDate != null && customEndDate != null) btnEndDate.setText(DISPLAY_DATE_FORMAT.format(customEndDate.getTime()));
    }

    private void updateCharts(List<Report> reports) {
        Map<String, Integer> statusCounts = new TreeMap<>();
        Map<String, Integer> scales = new TreeMap<>();
        Map<String, Integer> workload = new TreeMap<>();
        TreeMap<Long, Integer> daily = new TreeMap<>();
        for (Report report : reports) {
            increment(statusCounts, firstNonEmpty(report.getStatus(), "Unknown"));
            increment(scales, firstNonEmpty(report.getWasteScale(), "Unspecified"));
            increment(workload, firstNonEmpty(report.getAssignedTeamName(), report.getAssignedPersonnelName(), "Unassigned"));
            Date date = ReportSubmittedAtFormatter.parse(report.getCreatedAt());
            if (date != null) {
                Calendar day = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
                day.setTime(date);
                setStartOfDay(day);
                daily.merge(day.getTimeInMillis(), 1, Integer::sum);
            }
        }

        // --- Determine Theme-Aware Text Color ---
        boolean isDarkMode = (getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int chartTextColor = isDarkMode ? Color.WHITE : Color.parseColor("#06402B");

        if (lineChartTrends != null) {
            List<Entry> entries = new ArrayList<>();
            List<String> labels = new ArrayList<>();

            /*
             * daily is keyed by the day's start time, so the points come out in
             * chronological order. The old "MMM dd" string keys sorted
             * alphabetically by month name, which put Nov before Oct and
             * mislabelled every point that crossed a month boundary.
             */
            boolean spansYears = daily.size() > 1
                    && yearOf(daily.firstKey()) != yearOf(daily.lastKey());
            SimpleDateFormat axisFormat = spansYears
                    ? new SimpleDateFormat("MMM dd ''yy", Locale.US) : DAY_FORMAT;
            if (spansYears) axisFormat.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));

            int index = 0;
            for (Map.Entry<Long, Integer> item : daily.entrySet()) {
                entries.add(new Entry(index++, item.getValue()));
                labels.add(axisFormat.format(new Date(item.getKey())));
            }
            LineDataSet dataSet = new LineDataSet(entries, "Reports");
            dataSet.setColor(getResources().getColor(R.color.green, null));
            dataSet.setCircleColor(getResources().getColor(R.color.green, null));

            // --- Apply Theme Color to Line Data Point Values ---
            dataSet.setDrawValues(true); // Set to true if you want values visible on points
            dataSet.setValueTextColor(chartTextColor);
            dataSet.setValueTextSize(10f);
            // --------------------------------------------------

            lineChartTrends.setData(new LineData(dataSet));

            XAxis xAxis = lineChartTrends.getXAxis();
            xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

            /*
             * IndexAxisValueFormatter blanks any axis value that is not an exact
             * integer index, while the renderer otherwise picks fractional
             * intervals for short ranges (e.g. 0.8 apart for five days). That is
             * what made some dates vanish and the rest sit half a slot away from
             * their dots. Granularity 1 plus half a slot of padding on each end
             * keeps every date centred under its own point.
             */
            xAxis.setGranularity(1f);
            xAxis.setGranularityEnabled(true);
            xAxis.setAxisMinimum(-0.5f);
            xAxis.setAxisMaximum(entries.size() - 0.5f);
            xAxis.setLabelCount(Math.max(1, Math.min(labels.size(), 7)), false);
            xAxis.setAvoidFirstLastClipping(false);
            // Room for the half-width of the first and last date labels.
            lineChartTrends.setExtraOffsets(16f, 8f, 16f, 10f);

            lineChartTrends.notifyDataSetChanged();
            lineChartTrends.invalidate();
        }
        if (pieChartStatus != null) {
            List<PieEntry> entries = new ArrayList<>();
            statusCounts.forEach((label, count) -> entries.add(new PieEntry(count, label)));

            PieDataSet dataSet = new PieDataSet(entries, "Status");

            // Custom colors for status
            List<Integer> colors = new ArrayList<>();
            for (PieEntry entry : entries) {
                String labelLower = entry.getLabel().toLowerCase(Locale.ROOT);
                if (labelLower.contains("pending")) {
                    colors.add(Color.parseColor("#FBBC05")); // Yellow
                } else if (labelLower.contains("denied") || labelLower.contains("reject")) {
                    colors.add(Color.parseColor("#F44336")); // Red
                } else if (labelLower.contains("complete") || labelLower.contains("resolved")) {
                    colors.add(Color.parseColor("#06402B")); // Green
                } else {
                    colors.add(Color.parseColor("#9E9E9E")); // Default gray for others
                }
            }
            dataSet.setColors(colors);

            PieData data = new PieData(dataSet);
            data.setValueFormatter(new PercentFormatter(pieChartStatus));
            data.setValueTextSize(11f);
            data.setValueTextColor(Color.WHITE);

            pieChartStatus.setData(data);
            pieChartStatus.invalidate();
        }
    }

    private static void increment(Map<String, Integer> counts, String key) {
        counts.put(key, counts.getOrDefault(key, 0) + 1);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static void setStartOfDay(Calendar value) {
        value.set(Calendar.HOUR_OF_DAY, 0);
        value.set(Calendar.MINUTE, 0);
        value.set(Calendar.SECOND, 0);
        value.set(Calendar.MILLISECOND, 0);
    }

    private static void setEndOfDay(Calendar value) {
        value.set(Calendar.HOUR_OF_DAY, 23);
        value.set(Calendar.MINUTE, 59);
        value.set(Calendar.SECOND, 59);
        value.set(Calendar.MILLISECOND, 999);
    }

    private static int yearOf(long millis) {
        Calendar value = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
        value.setTimeInMillis(millis);
        return value.get(Calendar.YEAR);
    }

    private static final class CalendarHeatmapAdapter extends RecyclerView.Adapter<CalendarHeatmapAdapter.Holder> {
        private final List<Day> days = new ArrayList<>();
        private final DayClickListener listener;

        CalendarHeatmapAdapter(List<Day> initial, DayClickListener listener) {
            this.listener = listener;
            submit(initial);
        }

        void submit(List<Day> items) {
            days.clear();
            days.addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_heatmap_day, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Day day = days.get(position);
            holder.label.setText(day.label);
            holder.label.setTypeface(
                    holder.label.getTypeface(),
                    android.graphics.Typeface.BOLD
            );
            holder.itemView.setVisibility(day.label.isEmpty() ? View.INVISIBLE : View.VISIBLE);
            holder.itemView.setOnClickListener(day.enabled ? v -> listener.onDayClick(day.label) : null);
            if (day.label.isEmpty()) {
                holder.label.setContentDescription(null);
            } else {
                holder.label.setContentDescription(day.label + ", " + day.count
                        + " reports" + (day.selected ? ", selected" : ""));
            }

            /*
             * The cell colour IS the reading, so it is always set from the count.
             * Zero is the exception: it is drawn hollow (transparent fill, green
             * outline) so an empty day cannot be mistaken for a filled step.
             * A tapped day overrides both with a solid highlight, so the active
             * date is visible whatever its report count is.
             */
            int backgroundRes = heatmapColorFor(day.count);
            boolean emptyDay = backgroundRes == R.color.calendar_heatmap_empty;

            if (day.selected) {
                holder.label.setBackgroundResource(R.drawable.bg_calendar_heatmap_selected);
            } else if (emptyDay) {
                holder.label.setBackgroundResource(R.drawable.bg_calendar_heatmap_empty);
            } else {
                holder.label.setBackgroundColor(
                        ContextCompat.getColor(holder.itemView.getContext(), backgroundRes));
            }

            /*
             * Dark green on the pale swatches and on the selected highlight.
             * White on the darkest bucket, and on the hollow one too: a hollow
             * cell is transparent over the calendar's dark green card, so a green
             * number would disappear.
             */
            int onCellColor = !day.selected
                    && (emptyDay || backgroundRes == R.color.calendar_heatmap_dark)
                    ? Color.WHITE
                    : Color.parseColor("#06402B");
            holder.label.setTextColor(onCellColor);

            // The "today" dot was drawn for light cells only, so it would vanish on
            // a dark one. Flip it with the background it sits on.
            if (holder.viewCurrentDayDot != null) {
                holder.viewCurrentDayDot.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(onCellColor));
            }

            // --- Check if this cell represents Today ---
            Calendar today = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
            boolean isToday = day.enabled &&
                    day.year == today.get(Calendar.YEAR) &&
                    day.month == today.get(Calendar.MONTH) &&
                    day.dayOfMonth == today.get(Calendar.DAY_OF_MONTH);

            if (holder.viewCurrentDayDot != null) {
                holder.viewCurrentDayDot.setVisibility(isToday ? View.VISIBLE : View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return days.size();
        }

        /**
         * Maps a day's total onto the same five buckets the legend below the
         * calendar shows: 0, 1-2, 3-5, 6-10, 11+. Keeping the thresholds here
         * means the calendar and its legend cannot drift apart.
         */
        private static int heatmapColorFor(int count) {
            if (count <= 0) return R.color.calendar_heatmap_empty;
            if (count <= 2) return R.color.calendar_heatmap_very_light;
            if (count <= 5) return R.color.calendar_heatmap_light;
            if (count <= 10) return R.color.calendar_heatmap_medium;
            return R.color.calendar_heatmap_dark;
        }

        static final class Day {
            final String label;
            final int count;
            final boolean enabled;
            final int year;
            final int month;
            final int dayOfMonth;
            final boolean selected;

            Day(String label, int count, boolean enabled, int year, int month, int dayOfMonth,
                boolean selected) {
                this.label = label;
                this.count = count;
                this.enabled = enabled;
                this.year = year;
                this.month = month;
                this.dayOfMonth = dayOfMonth;
                this.selected = selected;
            }
        }

        interface DayClickListener {
            void onDayClick(String dayLabel);
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView label;
            final View viewCurrentDayDot;

            Holder(@NonNull View itemView) {
                super(itemView);
                label = itemView.findViewById(R.id.tvDay);
                viewCurrentDayDot = itemView.findViewById(R.id.viewCurrentDayDot);
            }
        }
    }

    private static final class AnalyticsRecordsAdapter extends RecyclerView.Adapter<AnalyticsRecordsAdapter.Holder> {
        private final ReportsAnalyticsFragment fragment;
        private final List<Report> reports = new ArrayList<>();

        AnalyticsRecordsAdapter(ReportsAnalyticsFragment fragment) {
            this.fragment = fragment;
        }

        void submit(List<Report> items) {
            reports.clear();
            reports.addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_analytics_record, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Report report = reports.get(position);
            holder.id.setText(firstNonEmpty(report.getReportId(), report.getMongoId(), "Report"));
            holder.status.setText(firstNonEmpty(report.getStatus(), "Unknown"));
            holder.location.setText(firstNonEmpty(report.getTitle(), report.getDescription(), "Location unavailable"));

            String scaleText = firstNonEmpty(report.getWasteScale(), "N/A");
            holder.scale.setText(scaleText);

            // Check if waste scale is small-scale
            boolean isSmallScale = scaleText.toLowerCase(Locale.ROOT).contains("small");

            if (isSmallScale) {
                if (holder.teamLabel != null) {
                    holder.teamLabel.setText("Personnel: ");
                }
                // Uses the robust model functions to find personnel across active, completed, or ID records[cite: 1]
                holder.team.setText(firstNonEmpty(
                        report.getCleanerLabel(),
                        report.getAssignedPersonnelName(),
                        report.getCompletedByPersonnelName(),
                        "Unassigned"
                ));
            } else {
                if (holder.teamLabel != null) {
                    holder.teamLabel.setText("Team: ");
                }
                // Uses team label functions for medium/large-scale wastes[cite: 1]
                holder.team.setText(firstNonEmpty(
                        report.getAssignedTeamLabel(),
                        report.getAssignedTeamName(),
                        "Unassigned"
                ));
            }

            String date = ReportSubmittedAtFormatter.formatDate(report.getCreatedAt());
            holder.timestamp.setText(date == null ? "Date unavailable" : date);

            holder.itemView.setOnClickListener(v -> ReportDetailDialog.show(fragment, report,
                    () -> fragment.reportsViewModel.refreshReports()));
        }

        @Override
        public int getItemCount() {
            return reports.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView id, status, location, scale, team, teamLabel, timestamp;

            Holder(@NonNull View itemView) {
                super(itemView);
                id = itemView.findViewById(R.id.tvReportId);
                status = itemView.findViewById(R.id.tvStatus);
                location = itemView.findViewById(R.id.tvLocation);
                scale = itemView.findViewById(R.id.tvWasteSize);
                team = itemView.findViewById(R.id.tvTeam);
                teamLabel = itemView.findViewById(R.id.tvTeamLabel);
                timestamp = itemView.findViewById(R.id.tvTimestamp);
            }
        }
    }
}
