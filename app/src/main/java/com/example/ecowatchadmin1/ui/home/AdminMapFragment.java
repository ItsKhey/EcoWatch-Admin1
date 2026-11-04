package com.example.ecowatchadmin1.ui.home;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.ui.reports.ReportDetailDialog;
import com.example.ecowatchadmin1.ui.reports.ReportsViewModel;
import com.example.ecowatchadmin1.utils.DateTimeUtils;
import com.example.ecowatchadmin1.utils.ReportSubmittedAtFormatter;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Admin map with report markers, filtering, and an on-map color legend.
 *
 * <h3>Filtering</h3>
 * Status, time range and free-text search are applied on the client against the
 * cached data, so they behave identically no matter what the server does with
 * its {@code status} query parameter.
 */
public class AdminMapFragment extends Fragment implements OnMapReadyCallback {
    private static final String ARG_REPORT_ID = "focus_report_id";
    private static final String ARG_LATITUDE = "focus_latitude";
    private static final String ARG_LONGITUDE = "focus_longitude";
    private static final String ARG_PERSONNEL_NAME = "focus_personnel_name";

    private static final String LAYER_REPORTS = "Reports";

    private static final String TIME_TODAY = "Today";
    private static final String TIME_THIS_WEEK = "This Week";

    /** "This Week" is the trailing 7 days, today included. */
    private static final int THIS_WINDOW_DAYS = 7;

    private static final long SEARCH_DEBOUNCE_MS = 350L;

    private static final String TAG = "AdminMapFragment";

    private GoogleMap mMap;
    private ReportsViewModel reportsViewModel;
    private FusedLocationProviderClient fusedLocationClient;
    private LatLng userLatLng;
    private LatLng focusLatLng;
    private String focusReportId;
    private String focusPersonnelName;
    private boolean focusApplied;
    private int mapBottomInset;

    /**
     * Cached layer data. Both feeds arrive on their own schedule and are held
     * here so a refresh can repaint without discarding filter state.
     */
    private List<Report> latestReports;

    // Filter state
    private String currentLayer = LAYER_REPORTS;
    private String currentStatusFilter = "";
    private String currentTimeFilter = "";
    private String currentSearchQuery = "";
    private String currentScaleFilter = ""; // "", or Small/Medium/Large-Scale Waste

    /**
     * Bottom margin the floating controls started with, so repeated inset
     * applications do not stack the navigation card height on top of itself.
     */
    private int controlsBaseBottomMargin = -1;

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            enableUserLocation();
                        } else if (focusLatLng == null) {
                            Toast.makeText(getContext(), "Location permission denied",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = this::renderMarkers;

    public static AdminMapFragment newInstance(String reportId, Double latitude,
                                               Double longitude, String personnelName) {
        AdminMapFragment fragment = new AdminMapFragment();
        Bundle args = new Bundle();
        if (reportId != null) args.putString(ARG_REPORT_ID, reportId);
        if (latitude != null) args.putDouble(ARG_LATITUDE, latitude);
        if (longitude != null) args.putDouble(ARG_LONGITUDE, longitude);
        if (personnelName != null) args.putString(ARG_PERSONNEL_NAME, personnelName);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_map, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            focusReportId = args.getString(ARG_REPORT_ID);
            focusPersonnelName = args.getString(ARG_PERSONNEL_NAME);
            if (args.containsKey(ARG_LATITUDE) && args.containsKey(ARG_LONGITUDE)) {
                double latitude = args.getDouble(ARG_LATITUDE);
                double longitude = args.getDouble(ARG_LONGITUDE);
                if (isValidCoordinate(latitude, longitude)) {
                    focusLatLng = new LatLng(latitude, longitude);
                }
            }
        }

        // A report opened directly from a list needs its own pin, so always
        // use the report layer.
        currentLayer = LAYER_REPORTS;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        reportsViewModel = new ViewModelProvider(this).get(ReportsViewModel.class);
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.adminMap);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        setupSearch(view);
        setupFilterButton(view);
        setupLegend(view);
        runAfterFirstLayout(view);

        reportsViewModel.getAllReports().observe(getViewLifecycleOwner(), reports -> {
            latestReports = reports;
            focusAssignedReport(reports);
            renderMarkers();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        searchHandler.removeCallbacks(searchRunnable);
    }

    /* ---------------------------------------------------------------- search */

    private void setupSearch(@NonNull View view) {
        View searchBar = view.findViewById(R.id.mapSearchBar);
        if (searchBar == null) return;

        TextInputEditText searchInput = searchBar.findViewById(R.id.mapSearchInput);
        if (searchInput == null) return;

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s == null ? "" : s.toString().trim();
                // Debounced: one request per pause in typing instead of one per
                // keystroke, which also keeps responses in order.
                searchHandler.removeCallbacks(searchRunnable);
                searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
                updateFilterButton();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    /* ---------------------------------------------------------------- filter */

    private void setupFilterButton(@NonNull View view) {
        View filterBtn = view.findViewById(R.id.btnMapFilter);
        if (filterBtn == null) return;

        filterBtn.setOnClickListener(v -> showFilterBottomSheet());
        updateFilterButton();
    }

    /** Keeps the button label honest about how much filtering is in effect. */
    private void updateFilterButton() {
        View filterBtn = getView() == null ? null : getView().findViewById(R.id.btnMapFilter);
        if (!(filterBtn instanceof MaterialButton)) return;

        int count = activeFilterCount();
        ((MaterialButton) filterBtn).setText(
                count == 0 ? getString(R.string.filter) : getString(R.string.filter) + " · " + count);
    }

    private int activeFilterCount() {
        int count = 0;
        if (!currentStatusFilter.isEmpty()) count++;
        if (!currentTimeFilter.isEmpty()) count++;
        if (!currentSearchQuery.isEmpty()) count++;
        if (!currentScaleFilter.isEmpty()) count++;
        return count;
    }

    private void showFilterBottomSheet() {
        if (!isAdded()) return;

        View sheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_map_filter, null, false);
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(sheetView);

        ChipGroup layerGroup = sheetView.findViewById(R.id.filterLayerGroup);
        Chip chipLayerReports = sheetView.findViewById(R.id.chipLayerReports);

        Chip chipAll = sheetView.findViewById(R.id.chipFilterAll);
        Chip chipPending = sheetView.findViewById(R.id.chipFilterPending);
        Chip chipInProgress = sheetView.findViewById(R.id.chipFilterInProgress);
        Chip chipCompleted = sheetView.findViewById(R.id.chipFilterCompleted);
        Chip chipDenied = sheetView.findViewById(R.id.chipFilterDenied);
        Chip chipToday = sheetView.findViewById(R.id.chipFilterToday);
        Chip chipThisWeek = sheetView.findViewById(R.id.chipFilterThisWeek);

        ChipGroup scaleGroup = sheetView.findViewById(R.id.filterWasteScaleGroup);
        Chip chipSmall = sheetView.findViewById(R.id.chipSmallScaleWaste);
        Chip chipMedium = sheetView.findViewById(R.id.chipMediumScaleWaste);
        Chip chipLarge = sheetView.findViewById(R.id.chipLargeScaleWaste);

        MaterialButton applyBtn = sheetView.findViewById(R.id.btnApplyFilter);
        MaterialButton resetBtn = sheetView.findViewById(R.id.btnResetFilter);

        boolean reportsLayer = LAYER_REPORTS.equals(currentLayer);
        if (chipLayerReports != null) chipLayerReports.setChecked(reportsLayer);

        if (scaleGroup != null && scaleGroup.getChildCount() == 0 && chipSmall != null) {
            // in case layout inflated differently
        }

        if (Report.SCALE_SMALL.equalsIgnoreCase(currentScaleFilter)) {
            if (chipSmall != null) chipSmall.setChecked(true);
        } else if (Report.SCALE_MEDIUM.equalsIgnoreCase(currentScaleFilter)) {
            if (chipMedium != null) chipMedium.setChecked(true);
        } else if (Report.SCALE_LARGE.equalsIgnoreCase(currentScaleFilter)) {
            if (chipLarge != null) chipLarge.setChecked(true);
        } else {
            if (scaleGroup != null) scaleGroup.clearCheck();
        }

        // Report pins support every status, including Denied.
        chipDenied.setEnabled(reportsLayer);

        // Both filter fields are never null, so the stored value can be matched
        // directly instead of walking an if-chain of equals checks.
        switch (currentStatusFilter) {
            case "":
                chipAll.setChecked(true);
                break;
            case "Pending":
                chipPending.setChecked(true);
                break;
            case "In Progress":
                chipInProgress.setChecked(true);
                break;
            case "Completed":
                chipCompleted.setChecked(true);
                break;
            case "Denied":
                chipDenied.setChecked(true);
                break;
            default:
                break;
        }

        switch (currentTimeFilter) {
            case TIME_TODAY:
                chipToday.setChecked(true);
                break;
            case TIME_THIS_WEEK:
                chipThisWeek.setChecked(true);
                break;
            default:
                break;
        }

        // Switching layer changes which options apply, so the sheet is updated
        // in place instead of making the admin reopen it.
        if (layerGroup != null) {
            layerGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                boolean nowReports = chipLayerReports.isChecked();
                chipDenied.setEnabled(nowReports);
            });
        }

        applyBtn.setOnClickListener(v -> {
            if (chipPending != null && chipPending.isChecked()) currentStatusFilter = "Pending";
            else if (chipInProgress != null && chipInProgress.isChecked()) currentStatusFilter = "In Progress";
            else if (chipCompleted != null && chipCompleted.isChecked()) currentStatusFilter = "Completed";
            else if (chipDenied != null && chipDenied.isChecked()) currentStatusFilter = "Denied";
            else currentStatusFilter = "";

            if (chipToday != null && chipToday.isChecked()) currentTimeFilter = TIME_TODAY;
            else if (chipThisWeek != null && chipThisWeek.isChecked()) currentTimeFilter = TIME_THIS_WEEK;
            else currentTimeFilter = "";

            if (chipLayerReports != null) {
                currentLayer = chipLayerReports.isChecked() ? LAYER_REPORTS : LAYER_REPORTS;
            } else {
                currentLayer = LAYER_REPORTS;
            }

            ChipGroup scaleGroupLocal = sheetView.findViewById(R.id.filterWasteScaleGroup);
            if (scaleGroupLocal != null && scaleGroupLocal.getCheckedChipId() != View.NO_ID) {
                int sid = scaleGroupLocal.getCheckedChipId();
                if (sid == R.id.chipSmallScaleWaste) currentScaleFilter = Report.SCALE_SMALL;
                else if (sid == R.id.chipMediumScaleWaste) currentScaleFilter = Report.SCALE_MEDIUM;
                else if (sid == R.id.chipLargeScaleWaste) currentScaleFilter = Report.SCALE_LARGE;
                else currentScaleFilter = "";
            } else {
                currentScaleFilter = "";
            }

            applyFilters();
            dialog.dismiss();
        });

        resetBtn.setOnClickListener(v -> {
            currentStatusFilter = "";
            currentTimeFilter = "";
            currentScaleFilter = "";
            ChipGroup scaleGroupLocal = sheetView.findViewById(R.id.filterWasteScaleGroup);
            if (scaleGroupLocal != null) scaleGroupLocal.clearCheck();
            clearSearch();
            applyFilters();
            dialog.dismiss();
        });

        dialog.show();
    }

    /** Clears the search box, which also trips the watcher and repaints. */
    private void clearSearch() {
        View mapSearchBar = getView() == null ? null : getView().findViewById(R.id.mapSearchBar);
        if (mapSearchBar == null) return;
        TextInputEditText field = mapSearchBar.findViewById(R.id.mapSearchInput);
        if (field != null) field.setText("");
    }

    /**
     * Re-reads the filter state and repaints.
     */
    private void applyFilters() {
        updateFilterButton();
        renderMarkers();
    }

    @Override
    public void onResume() {
        super.onResume();
        renderMarkers();
    }

    /* ---------------------------------------------------------------- legend */

    private void setupLegend(@NonNull View view) {
        View legend = view.findViewById(R.id.mapLegend);
        if (legend == null) return;

        // Status: report pins are colored by report status.
        bindLegendRow(legend, R.id.legendReportPending, R.color.map_marker_yellow, "Pending");
        bindLegendRow(legend, R.id.legendReportInProgress, R.color.map_marker_orange, "In Progress");
        bindLegendRow(legend, R.id.legendReportCompleted, R.color.map_marker_green, "Completed");
        bindLegendRow(legend, R.id.legendReportDenied, R.color.map_marker_red, "Denied");
        bindLegendRow(legend, R.id.legendReportOther, R.color.map_marker_cyan, "Other status");

        // Waste scale legend
        bindLegendRow(legend, R.id.legendScaleSmall, R.color.map_marker_green, "Small-Scale Waste");
        bindLegendRow(legend, R.id.legendScaleMedium, R.color.map_marker_orange, "Medium-Scale Waste");
        bindLegendRow(legend, R.id.legendScaleLarge, R.color.map_marker_red, "Large-Scale Waste");

        bindLegendRow(legend, R.id.legendYourLocation, R.color.map_marker_blue, "Your location");
        bindLegendRow(legend, R.id.legendAssignedReport, R.color.map_marker_violet, "Assigned report");

        View header = legend.findViewById(R.id.legendHeader);
        if (header == null) return;
        final View content = legend.findViewById(R.id.legendContent);
        final TextView hint = header.findViewById(R.id.legendToggleHint);

        header.setOnClickListener(v -> {
            if (content == null) return;
            boolean collapsing = content.getVisibility() == View.VISIBLE;
            content.setVisibility(collapsing ? View.GONE : View.VISIBLE);
            if (hint != null) hint.setText(collapsing ? "Show" : "Hide");
        });
    }

    private void bindLegendRow(@NonNull View legend, int rowId, int colorId,
                               @NonNull String label) {
        View row = legend.findViewById(rowId);
        if (row == null) return;

        ImageView dot = row.findViewById(R.id.legendDot);
        if (dot != null) {
            ImageViewCompat.setImageTintList(dot, ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(requireContext(), colorId)));
        }
        TextView text = row.findViewById(R.id.legendLabel);
        if (text != null) text.setText(label);
    }

    /* ----------------------------------------------------------------- layout */

    private void runAfterFirstLayout(@NonNull View target) {
        target.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                v.removeOnLayoutChangeListener(this);
                liftControlsAboveNav(v);
            }
        });
    }

    private void liftControlsAboveNav(@NonNull View root) {
        if (!isAdded()) return;
        View bottomNav = requireActivity().findViewById(R.id.bottomNavCard);
        if (bottomNav == null) return;

        int navHeight = bottomNav.getHeight() + bottomNavHeightMargin(bottomNav);
        mapBottomInset = navHeight;
        applyMapInsets(navHeight);

        // The legend and the filter button share one container, so lifting that
        // container once keeps both clear of the navigation card. Lifting the
        // legend as well would push it down by the inset a second time.
        View controls = root.findViewById(R.id.mapFloatingControls);
        if (controls == null) return;
        if (!(controls.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;

        ViewGroup.MarginLayoutParams controlsParams =
                (ViewGroup.MarginLayoutParams) controls.getLayoutParams();
        if (controlsBaseBottomMargin < 0) {
            controlsBaseBottomMargin = Math.max(0, controlsParams.bottomMargin);
        }
        controlsParams.bottomMargin = mapBottomInset + controlsBaseBottomMargin;
        controls.setLayoutParams(controlsParams);
    }

    private int bottomNavHeightMargin(View bottomNav) {
        ViewGroup.MarginLayoutParams params = bottomNav.getLayoutParams()
                instanceof ViewGroup.MarginLayoutParams
                ? (ViewGroup.MarginLayoutParams) bottomNav.getLayoutParams() : null;
        return params == null ? 0 : Math.max(0, params.bottomMargin);
    }

    private void applyMapInsets(int bottomInset) {
        if (mMap == null || bottomInset <= 0) return;
        mMap.setPadding(0, 0, 0, bottomInset);
    }

    /* -------------------------------------------------------------------- map */

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        applyMapInsets(mapBottomInset);
        LatLng manila = new LatLng(14.5995, 120.9842);
        if (focusLatLng == null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(manila, 12f));
            enableUserLocation();
        } else {
            // Opened straight from a report, so go to it now rather than waiting
            // for the first data load to trigger a repaint.
            focusApplied = true;
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(focusLatLng, 16f));
        }

        mMap.setOnMarkerClickListener(marker -> {
            Object tag = marker.getTag();
            if (tag instanceof Report) {
                showReportDetails((Report) tag);
                return true;
            }
            return false;
        });

        mMap.setOnInfoWindowClickListener(marker -> {
            Object tag = marker.getTag();
            if (tag instanceof Report) {
                showReportDetails((Report) tag);
            }
        });
    }

    private void showReportDetails(Report report) {
        if (report == null || !isAdded()) return;
        ReportDetailDialog.show(this, report, () -> {
            if (isAdded() && reportsViewModel != null) reportsViewModel.refreshReports();
        });
    }

    private void enableUserLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
        if (mMap == null || fusedLocationClient == null) return;

        try {
            mMap.setMyLocationEnabled(true);
        } catch (SecurityException ignored) {
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
            if (location == null) return;
            userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
            renderMarkers();
            if (focusLatLng == null && mMap != null) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f));
            }
        });
    }

    /* --------------------------------------------------------------- rendering */

    /**
     * Paints the map from the cached data and the active filters. This is the
     * only place that clears the map, which is what stops a background refresh
     * from throwing away the filter the admin just applied.
     */
    private void renderMarkers() {
        if (mMap == null) return;

        mMap.clear();

        if (userLatLng != null) {
            mMap.addMarker(new MarkerOptions()
                    .position(userLatLng)
                    .title("Your Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
        }

        renderReportMarkers();

        if (focusLatLng != null) {
            addFocusMarker(focusLatLng);
            if (!focusApplied) {
                focusApplied = true;
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(focusLatLng, 16f));
            }
        }
    }

    private void renderReportMarkers() {
        List<Report> reports = latestReports;
        if (reports == null) return;

        String query = currentSearchQuery.toLowerCase(Locale.US);

        for (Report report : reports) {
            if (report == null || report.isDeleted()) continue;
            if (!isValidCoordinate(report.getLatitude(), report.getLongitude())) continue;
            if (statusFilteredOut(report.getStatus())) continue;
            // Waste scale filter
            if (!currentScaleFilter.isEmpty()) {
                String s = report.getWasteScale();
                if (s == null || !s.equalsIgnoreCase(currentScaleFilter)) {
                    continue;
                }
            }
            // "Today" and "This Week" mean when the report came in.
            if (timeFilteredOut(firstNonBlank(report.getCreatedAt(), report.getUpdatedAt()))) continue;
            if (!query.isEmpty() && !reportMatchesQuery(report, query)) continue;

            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(report.getLatitude(), report.getLongitude()))
                    .title(reportTitle(report))
                    .snippet("Status: " + safe(report.getStatus()))
                    .icon(BitmapDescriptorFactory.defaultMarker(getHueForStatus(report.getStatus()))));
            if (marker != null) marker.setTag(report);
        }
    }

    private void addFocusMarker(LatLng location) {
        if (mMap == null || location == null) return;
        String title = isBlank(focusPersonnelName) ? "Personnel assignment" : focusPersonnelName;
        mMap.addMarker(new MarkerOptions()
                .position(location)
                .title(title)
                .snippet("Currently cleaning")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)));
    }

    /* ---------------------------------------------------------------- filters */

    /**
     * Status filters are applied locally so they work regardless of backend.
     * Named for the call site: a filtered-out row is simply skipped, so the
     * marker loop never has to negate the result.
     */
    private boolean statusFilteredOut(@Nullable String status) {
        if (currentStatusFilter.isEmpty()) return false;
        if (status == null) return true;
        return !status.trim().equalsIgnoreCase(currentStatusFilter);
    }

    /**
     * Time filters are applied locally. The "Today" and "This Week" chips used to
     * be stored and then never consulted, so they silently did nothing; this is
     * what makes them real.
     */
    private boolean timeFilteredOut(@Nullable String timestamp) {
        if (currentTimeFilter.isEmpty()) return false;
        if (TIME_TODAY.equals(currentTimeFilter)) return !DateTimeUtils.isToday(timestamp);
        if (TIME_THIS_WEEK.equals(currentTimeFilter)) {
            return !DateTimeUtils.isWithinLastDays(timestamp, THIS_WINDOW_DAYS);
        }
        return false;
    }

    private boolean reportMatchesQuery(@NonNull Report report, @NonNull String lowerQuery) {
        return containsIgnoreCase(report.getReportId(), lowerQuery)
                || containsIgnoreCase(report.getMongoId(), lowerQuery)
                || containsIgnoreCase(report.getTitle(), lowerQuery)
                || containsIgnoreCase(report.getResidentId(), lowerQuery)
                || containsIgnoreCase(report.getStatus(), lowerQuery);
    }

    private boolean containsIgnoreCase(@Nullable String value, @NonNull String lowerQuery) {
        return value != null && value.toLowerCase(Locale.US).contains(lowerQuery);
    }

    private void focusAssignedReport(List<Report> reports) {
        if (focusApplied || focusLatLng != null || reports == null || isBlank(focusReportId)) return;
        for (Report report : reports) {
            if (report == null) continue;
            String id = firstNonBlank(report.getReportId(), report.getMongoId());
            if ((focusReportId.equals(id)
                    || focusReportId.equals(report.getReportId())
                    || focusReportId.equals(report.getMongoId()))
                    && isValidCoordinate(report.getLatitude(), report.getLongitude())) {
                focusLatLng = new LatLng(report.getLatitude(), report.getLongitude());
                return;
            }
        }
    }

    /* ------------------------------------------------------------------ color */

    private float getHueForStatus(String status) {
        if (status == null) return BitmapDescriptorFactory.HUE_CYAN;
        switch (status.trim().toLowerCase(Locale.US)) {
            case "pending":
                return BitmapDescriptorFactory.HUE_YELLOW;
            case "assigned":
            case "in progress":
                return BitmapDescriptorFactory.HUE_ORANGE;
            case "completed":
                return BitmapDescriptorFactory.HUE_GREEN;
            case "denied":
                return BitmapDescriptorFactory.HUE_RED;
            default:
                return BitmapDescriptorFactory.HUE_CYAN;
        }
    }

    /* ------------------------------------------------------------------ helpers */

    private boolean isValidCoordinate(double latitude, double longitude) {
        return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180
                && !(latitude == 0 && longitude == 0);
    }

    private String firstNonBlank(String first, String second) {
        if (!isBlank(first)) return first;
        return isBlank(second) ? null : second;
    }

    private String reportTitle(Report report) {
        String title = report == null ? null : report.getTitle();
        return title == null || title.trim().isEmpty() ? "Untitled report" : title;
    }

    private String safe(String value) {
        return value == null ? "Unknown" : value;
    }

    private String safeId(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value.trim();
    }

    private String safeStatus(String status) {
        return status == null || status.trim().isEmpty() ? "Pending" : status.trim();
    }

    private String safeValue(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
