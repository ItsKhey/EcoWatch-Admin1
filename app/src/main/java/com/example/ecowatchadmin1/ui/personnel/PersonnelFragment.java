package com.example.ecowatchadmin1.ui.personnel;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.CleaningTeam;
import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.data.models.Vehicle;
import com.example.ecowatchadmin1.data.repository.CleaningTeamRepository;
import com.example.ecowatchadmin1.ui.home.DashboardActivity;
import com.example.ecowatchadmin1.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.yalantis.ucrop.UCrop;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Personnel dashboard and account-management UI. */
public class PersonnelFragment extends Fragment {
    private static final String ARG_OPEN_REGISTRATION = "open_registration";
    private static final String ARG_OPEN_VEHICLES = "open_vehicles";
    private static final String STATE_REGISTRATION_FORM = "registration_form";
    private static final String STATE_PHOTO_URI = "photoUri";
    private static final String STATE_CAMERA_URI = "cameraUri";
    private static final String STATE_OCCUPATION = "occupation";
    private static final String STATE_ASSIGNED_VEHICLE = "assignedVehicleId";
    private static final String CROP_FILE_NAME = "registration_photo.jpg";
    private static final String CAPTURE_FILE_NAME = "registration_capture.jpg";

    private PersonnelViewModel viewModel;
    private VehicleViewModel vehicleViewModel;
    private TeamViewModel teamViewModel;
    private int teamDetailsLoadVersion;
    private PersonnelAdapter adapter;
    private VehicleAdapter vehicleAdapter;
    private TeamAdapter teamAdapter;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private TextView totalCount;
    private TextView onlineCount;
    private TextView cleaningCount;
    private TextView vehicleMaintenanceCount;
    private TextView vehicleFunctionalCount;
    private View vehicleMaintenanceCard;
    private View vehicleFunctionalCard;
    private View managementCardsScroll;
    private TextView totalLabel;
    private TextView secondLabel;
    private TextView thirdLabel;
    private TextView resultCount;
    private TextView emptyText;
    private TextView titleView;
    private TextView subtitleView;
    private EditText searchEditText;
    private ImageButton managementMenuButton;
    private PopupMenu managementMenu;
    private MaterialButton addVehicleButton;
    private MaterialButton registerPersonnelButton;
    private MaterialButton createTeamButton;
    private Chip filterAll;
    private Chip filterSecond;
    private Chip filterThird;
    private Chip filterFourth;

    private final List<Personnel> allPersonnel = new ArrayList<>();
    private final List<Vehicle> allVehicles = new ArrayList<>();
    private final List<CleaningTeam> allTeams = new ArrayList<>();

    /**
     * Which section of the management screen is showing. Everything below the
     * cards — the search bar, the chips, the list — reads this rather than
     * keeping three sets of state that could disagree.
     */
    private ManagementMode.Kind mode = ManagementMode.Kind.PERSONNEL;
    private ManagementMode activeMode = ManagementMode.PERSONNEL;
    private String currentFilter = "All";
    private AlertDialog registrationDialog;
    private ButtonState registrationButton;
    private RegistrationData pendingRegistration;
    private Bundle restoredRegistrationFormState;
    private TextInputEditText registrationFirstNameInput;
    private TextInputEditText registrationLastNameInput;
    private TextInputEditText registrationAddressInput;
    private TextInputEditText registrationGmailInput;
    private TextInputEditText registrationUsernameInput;
    private TextInputEditText registrationPhoneNumberInput;
    private TextInputEditText registrationMessengerLinkInput;
    private TextInputEditText registrationPasswordInput;
    private TextInputEditText registrationConfirmPasswordInput;
    private ShapeableImageView registrationPhotoPreview;
    private MaterialButton registrationRemovePhotoButton;

    // Occupation and the vehicle a driver is given, held while the dialog is up
    // so the fleet observer can repaint the picker in place when it answers.
    private TextInputLayout registrationOccupationLayout;
    private MaterialAutoCompleteTextView registrationOccupationInput;
    private View registrationVehicleSection;
    private RecyclerView registrationVehicleList;
    private TextView registrationVehicleEmpty;
    private RegistrationVehicleAdapter registrationVehicleAdapter;
    private Vehicle selectedRegistrationVehicle;
    /** Cropped photo the administrator selected, kept until the dialog closes. */
    private Uri registrationPhotoUri;
    /** Where the camera should write the next capture. */
    private Uri pendingCameraPhotoUri;
    private final android.os.Handler statusRefreshHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable statusRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (viewModel != null && isAdded()) {
                if (mode == ManagementMode.Kind.VEHICLES) vehicleViewModel.refreshFleet();
                else if (mode == ManagementMode.Kind.TEAMS) {
                    teamViewModel.refreshTeams();
                    viewModel.refreshPersonnel();
                }
                else viewModel.refreshPersonnel();
            }
            statusRefreshHandler.postDelayed(this, 30_000L);
        }
    };

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    launchCameraCapture();
                } else {
                    Toast.makeText(requireContext(), R.string.personnel_photo_camera_permission,
                            Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<Intent> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Uri captureUri = pendingCameraPhotoUri;
                pendingCameraPhotoUri = null;
                if (captureUri != null && result.getResultCode() == Activity.RESULT_OK) {
                    startRegistrationCrop(captureUri);
                }
            });

    private final ActivityResultLauncher<String> pickPhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) startRegistrationCrop(uri);
            });

    private final ActivityResultLauncher<Intent> cropPhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getData() == null) return;
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Uri output = UCrop.getOutput(result.getData());
                    if (output != null) setRegistrationPhoto(output);
                } else if (result.getResultCode() == UCrop.RESULT_ERROR) {
                    Throwable cropError = UCrop.getError(result.getData());
                    String message = cropError != null && cropError.getMessage() != null
                            ? cropError.getMessage()
                            : getString(R.string.personnel_photo_unreadable);
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                }
            });

    public static PersonnelFragment newInstance(boolean openRegistration) {
        PersonnelFragment fragment = new PersonnelFragment();
        if (openRegistration) {
            Bundle args = new Bundle();
            args.putBoolean(ARG_OPEN_REGISTRATION, true);
            fragment.setArguments(args);
        }
        return fragment;
    }

    /**
     * Opens the management screen already showing the Vehicles section.
     *
     * <p>Used by the dashboard's fleet cards. The argument is consumed on
     * arrival, so switching back to Personnel later does not reopen Vehicles.
     */
    public static PersonnelFragment newInstanceForVehicles() {
        PersonnelFragment fragment = new PersonnelFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_OPEN_VEHICLES, true);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            restoredRegistrationFormState = savedInstanceState.getBundle(STATE_REGISTRATION_FORM);
        }
        return inflater.inflate(R.layout.fragment_personnel, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle arguments = getArguments();
        if (arguments != null && arguments.getBoolean(ARG_OPEN_VEHICLES, false)) {
            mode = ManagementMode.Kind.VEHICLES;
            arguments.remove(ARG_OPEN_VEHICLES);
        }

        bindViews(view);
        setupManagementCardsScroll();

        SessionManager sessionManager = new SessionManager(requireContext());
        if (!sessionManager.isAdmin() || !sessionManager.isLoggedIn()) {
            showAdminOnlyState();
            return;
        }

        setupDashboard();
        setupFilters();
        observeViewModel();
        setupManagementMenu();
        viewModel.loadPersonnel();
        // The fleet is fetched up front rather than on first switch, so flipping
        // to Vehicles shows real rows instead of a spinner the administrator has
        // to wait out. One request on entry, not one per tab tap.
        vehicleViewModel.loadFleet();
        teamViewModel.loadTeams();
        if (restoredRegistrationFormState != null) {
            Bundle formState = restoredRegistrationFormState;
            restoredRegistrationFormState = null;
            view.post(() -> showRegistrationDialog(formState));
        } else {
            openRegistrationIfRequested();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (registrationDialog != null && registrationDialog.isShowing()) {
            Bundle formState = new Bundle();
            formState.putString("firstName", value(registrationFirstNameInput));
            formState.putString("lastName", value(registrationLastNameInput));
            formState.putString("address", value(registrationAddressInput));
            formState.putString("gmail", value(registrationGmailInput));
            formState.putString("username", value(registrationUsernameInput));
            formState.putString("phoneNumber", value(registrationPhoneNumberInput));
            formState.putString("messengerLink", value(registrationMessengerLinkInput));
            formState.putString("password", value(registrationPasswordInput));
            formState.putString("confirmPassword", value(registrationConfirmPasswordInput));
            formState.putString(STATE_OCCUPATION, selectedOccupation());
            formState.putString(STATE_ASSIGNED_VEHICLE, selectedRegistrationVehicleId());
            formState.putString(STATE_PHOTO_URI, uriString(registrationPhotoUri));
            formState.putString(STATE_CAMERA_URI, uriString(pendingCameraPhotoUri));
            outState.putBundle(STATE_REGISTRATION_FORM, formState);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onResume() {
        super.onResume();
        statusRefreshHandler.removeCallbacks(statusRefreshRunnable);
        if (viewModel != null) {
            // Only the showing side is polled. Backgrounding the screen should
            // not keep hitting the fleet API for rows nobody can see.
            if (mode == ManagementMode.Kind.VEHICLES) vehicleViewModel.refreshFleet();
            else if (mode == ManagementMode.Kind.TEAMS) {
                teamViewModel.refreshTeams();
                viewModel.refreshPersonnel();
            }
            else viewModel.refreshPersonnel();
            statusRefreshHandler.postDelayed(statusRefreshRunnable, 30_000L);
        }
    }

    @Override
    public void onPause() {
        statusRefreshHandler.removeCallbacks(statusRefreshRunnable);
        super.onPause();
    }

    private void bindViews(View view) {
        swipeRefresh = view.findViewById(R.id.personnelSwipeRefresh);
        recyclerView = view.findViewById(R.id.personnelRecyclerView);
        totalCount = view.findViewById(R.id.personnelTotalCount);
        onlineCount = view.findViewById(R.id.personnelOnlineCount);
        cleaningCount = view.findViewById(R.id.personnelCleaningCount);
        vehicleMaintenanceCount = view.findViewById(R.id.vehicleMaintenanceCount);
        vehicleFunctionalCount = view.findViewById(R.id.vehicleFunctionalCount);
        vehicleMaintenanceCard = view.findViewById(R.id.vehicleMaintenanceCard);
        vehicleFunctionalCard = view.findViewById(R.id.vehicleFunctionalCard);
        managementCardsScroll = view.findViewById(R.id.managementCardsScroll);
        totalLabel = view.findViewById(R.id.managementTotalLabel);
        secondLabel = view.findViewById(R.id.managementSecondLabel);
        thirdLabel = view.findViewById(R.id.managementThirdLabel);
        resultCount = view.findViewById(R.id.personnelResultCount);
        emptyText = view.findViewById(R.id.personnelEmptyText);
        titleView = view.findViewById(R.id.personnelTitle);
        subtitleView = view.findViewById(R.id.personnelSubtitle);
        searchEditText = view.findViewById(R.id.personnelSearch);
        managementMenuButton = view.findViewById(R.id.btnBurger);
        addVehicleButton = view.findViewById(R.id.btnAddVehicle);
        registerPersonnelButton = view.findViewById(R.id.btnRegisterPersonnel);
        createTeamButton = view.findViewById(R.id.btnCreateTeam);
        filterAll = view.findViewById(R.id.personnelFilterAll);
        filterSecond = view.findViewById(R.id.managementFilterSecond);
        filterThird = view.findViewById(R.id.managementFilterThird);
        filterFourth = view.findViewById(R.id.managementFilterFourth);
    }

    private void showAdminOnlyState() {
        if (emptyText != null) {
            emptyText.setText(R.string.personnel_error_admin_only);
            emptyText.setVisibility(View.VISIBLE);
        }
        if (swipeRefresh != null) swipeRefresh.setEnabled(false);
    }

    /** Keep horizontal card drags from being mistaken for pull-to-refresh. */
    private void setupManagementCardsScroll() {
        if (managementCardsScroll == null) return;
        final float[] down = new float[2];
        final int touchSlop = android.view.ViewConfiguration.get(requireContext())
                .getScaledTouchSlop();
        managementCardsScroll.setOnTouchListener((view, event) -> {
            ViewParent parent = view.getParent();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getX();
                    down[1] = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = Math.abs(event.getX() - down[0]);
                    float dy = Math.abs(event.getY() - down[1]);
                    if (dx > touchSlop && dx > dy) {
                        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private void setupDashboard() {
        adapter = new PersonnelAdapter();
        vehicleAdapter = new VehicleAdapter();
        teamAdapter = new TeamAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        adapter.setListener(new PersonnelAdapter.Listener() {
            @Override
            public void onLocate(Personnel personnel) {
                locatePersonnel(personnel);
            }

            @Override
            public void onViewDetails(Personnel personnel) {
                openDetails(personnel);
            }
        });
        vehicleAdapter.setListener(new VehicleAdapter.Listener() {
            @Override
            public void onLocate(Vehicle vehicle) {
                locateVehicle(vehicle);
            }

            @Override
            public void onViewDetails(Vehicle vehicle) {
                openVehicleDetails(vehicle);
            }
        });
        teamAdapter.setListener(team -> openTeamDetails(team));

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.green));
        // Pull-to-refresh refreshes whichever side is showing, not both: doing
        // both would issue two requests for one gesture.
        swipeRefresh.setOnRefreshListener(() -> {
            if (mode == ManagementMode.Kind.VEHICLES) vehicleViewModel.refreshFleet();
            else if (mode == ManagementMode.Kind.TEAMS) {
                teamViewModel.refreshTeams();
                viewModel.refreshPersonnel();
            }
            else viewModel.refreshPersonnel();
        });
    }

    /**
     * Wires the burger menu that switches between Personnel, Vehicles and Teams,
     * plus the four filter chips.
     *
     * <p>The three sections live in the menu instead of a row of buttons so the
     * list keeps that height. The chips are neutral views that the selected
     * section labels and binds, so there is one row of chips rather than three
     * that could fall out of step.
     */
    private void setupManagementMenu() {
        managementMenu = new PopupMenu(requireContext(), managementMenuButton);
        managementMenu.getMenuInflater().inflate(R.menu.menu_management_selector,
                managementMenu.getMenu());
        // The menu items carry the same icons the old segmented control used.
        managementMenu.setForceShowIcon(true);
        managementMenu.setOnMenuItemClickListener(item -> {
            int checkedId = item.getItemId();
            if (checkedId == R.id.managementSelectorVehicles) {
                mode = ManagementMode.Kind.VEHICLES;
            } else if (checkedId == R.id.managementSelectorTeams) {
                mode = ManagementMode.Kind.TEAMS;
            } else {
                mode = ManagementMode.Kind.PERSONNEL;
            }
            applyMode();
            return true;
        });

        managementMenuButton.setOnClickListener(v -> {
            // Check the item for the section that is showing before the menu
            // opens, so the menu itself reports the current state.
            MenuItem selected = managementMenu.getMenu().findItem(menuItemFor(mode));
            if (selected != null) selected.setChecked(true);
            managementMenu.show();
        });

        filterAll.setOnClickListener(v -> selectFilter(activeMode.filterAllValue));
        filterSecond.setOnClickListener(v -> selectFilter(activeMode.filterSecondValue));
        filterThird.setOnClickListener(v -> selectFilter(activeMode.filterThirdValue));
        filterFourth.setOnClickListener(v -> selectFilter(activeMode.filterFourthValue));

        addVehicleButton.setOnClickListener(v ->
                VehicleDialogs.showEditor(requireContext(), null, vehicleViewModel,
                        (created, error) -> vehicleViewModel.refreshFleet()));

        // The Personnel side's own entry point to the registration form, which
        // previously lived only on the dashboard and in Settings. Same form, same
        // dialog: the fragment already owns the photo crop and the fleet behind it.
        registerPersonnelButton.setOnClickListener(v -> showRegistrationDialog(null));

        createTeamButton.setOnClickListener(v -> TeamDialogs.showEditor(requireContext(), null,
                teamViewModel, new ArrayList<>(allPersonnel), this::refreshTeams));

        applyMode();
    }

    /** The menu item that represents the currently showing section. */
    private int menuItemFor(ManagementMode.Kind kind) {
        if (kind == ManagementMode.Kind.VEHICLES) return R.id.managementSelectorVehicles;
        if (kind == ManagementMode.Kind.TEAMS) return R.id.managementSelectorTeams;
        return R.id.managementSelectorPersonnel;
    }

    /**
     * Repaints every piece of shared chrome for the selected side.
     *
     * <p>Title, card labels, search hint, chip labels and the empty text all come
     * from {@link ManagementMode}, so a card cannot still read "Online" above a
     * list of vehicles.
     */
    private void applyMode() {
        activeMode = ManagementMode.of(mode);

        titleView.setText(activeMode.title);
        subtitleView.setText(activeMode.subtitle);
        totalLabel.setText(activeMode.totalLabel);
        secondLabel.setText(activeMode.secondLabel);
        thirdLabel.setText(activeMode.thirdLabel);
        searchEditText.setHint(activeMode.searchHint);
        emptyText.setText(activeMode.emptyText);

        filterAll.setText(activeMode.filterAllLabel);
        filterSecond.setText(activeMode.filterSecondLabel);
        filterThird.setText(activeMode.filterThirdLabel);
        filterFourth.setText(activeMode.filterFourthLabel);

        // Each side registers its own kind of record, so only its own button shows.
        // Two buttons that swapped labels would leave the administrator unsure
        // which form the plus was about to open.
        boolean showingVehicles = mode == ManagementMode.Kind.VEHICLES;
        boolean showingTeams = mode == ManagementMode.Kind.TEAMS;
        boolean showingPersonnel = !showingVehicles && !showingTeams;
        addVehicleButton.setVisibility(showingVehicles ? View.VISIBLE : View.GONE);
        registerPersonnelButton.setVisibility(showingPersonnel ? View.VISIBLE : View.GONE);
        createTeamButton.setVisibility(showingTeams ? View.VISIBLE : View.GONE);
        vehicleMaintenanceCard.setVisibility(showingVehicles ? View.VISIBLE : View.GONE);
        vehicleFunctionalCard.setVisibility(showingVehicles ? View.VISIBLE : View.GONE);

        // A filter means nothing on the other side: "Cleaning" has no vehicle
        // equivalent, and leaving it selected would show an empty list with no
        // obvious way back. All is the one value both sides share.
        if (activeMode.chipIndexFor(currentFilter) < 0) {
            currentFilter = activeMode.filterAllValue;
        }
        filterAll.setChecked(true);

        // Clear a search term that only matched the other side, so switching
        // shows the fleet rather than an empty list under a stale query.
        if (searchEditText != null && searchEditText.getText() != null) {
            searchEditText.setText("");
        }

        if (showingVehicles) {
            recyclerView.setAdapter(vehicleAdapter);
        } else if (showingTeams) {
            recyclerView.setAdapter(teamAdapter);
        } else {
            recyclerView.setAdapter(adapter);
        }
        render();
    }

    private void setupFilters() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                render();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void openRegistrationIfRequested() {
        Bundle arguments = getArguments();
        if (arguments == null || !arguments.getBoolean(ARG_OPEN_REGISTRATION, false)) {
            return;
        }

        // Consume the one-shot argument before posting so a rotation or return
        // to the personnel list does not unexpectedly reopen the form.
        arguments.remove(ARG_OPEN_REGISTRATION);
        View view = getView();
        if (view != null) view.post(() -> showRegistrationDialog(null));
    }

    private void observeViewModel() {
        viewModel = new ViewModelProvider(this).get(PersonnelViewModel.class);
        vehicleViewModel = new ViewModelProvider(this).get(VehicleViewModel.class);
        teamViewModel = new ViewModelProvider(this).get(TeamViewModel.class);

        viewModel.getPersonnel().observe(getViewLifecycleOwner(), people -> {
            allPersonnel.clear();
            if (people != null) allPersonnel.addAll(people);
            // The fleet cards name each truck's driver, and the driver lives on
            // the personnel record rather than the vehicle one, so the vehicle
            // list reads this same roster.
            if (vehicleAdapter != null) vehicleAdapter.setDrivers(allPersonnel);
            render();
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            if (swipeRefresh == null) return;
            // The spinner follows whichever side is showing, so the two
            // ViewModels cannot fight over one indicator.
            boolean personnelLoading = mode == ManagementMode.Kind.PERSONNEL
                    && Boolean.TRUE.equals(loading);
            boolean vehicleLoading = mode == ManagementMode.Kind.VEHICLES
                    && Boolean.TRUE.equals(vehicleViewModel.getLoading().getValue());
            boolean teamLoading = mode == ManagementMode.Kind.TEAMS
                    && Boolean.TRUE.equals(teamViewModel.getLoading().getValue());
            swipeRefresh.setRefreshing(personnelLoading || vehicleLoading || teamLoading);
        });

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.trim().isEmpty()) return;
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            restoreRegistrationButton();
            viewModel.clearError();
        });

        viewModel.getWarning().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.trim().isEmpty()) return;
            Toast.makeText(requireContext(),
                    getString(R.string.personnel_photo_upload_failed, message),
                    Toast.LENGTH_LONG).show();
            viewModel.clearWarning();
        });

        viewModel.getAction().observe(getViewLifecycleOwner(), action -> {
            if (action == null) return;
            handleAction(action);
            viewModel.clearAction();
        });

        vehicleViewModel.getVehicles().observe(getViewLifecycleOwner(), vehicles -> {
            allVehicles.clear();
            if (vehicles != null) allVehicles.addAll(vehicles);
            // The personnel cards name a driver's truck, so they read the same
            // fleet snapshot the Vehicles tab does.
            if (adapter != null) adapter.setFleet(allVehicles);
            // A registration dialog can be open on top of this screen, offering
            // the very vehicles this response carries.
            renderRegistrationVehicles();
            render();
        });

        vehicleViewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            if (swipeRefresh == null || mode != ManagementMode.Kind.VEHICLES) return;
            swipeRefresh.setRefreshing(Boolean.TRUE.equals(loading));
        });

        vehicleViewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.trim().isEmpty()) return;
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            vehicleViewModel.clearError();
        });

        teamViewModel.getTeams().observe(getViewLifecycleOwner(), teams -> {
            allTeams.clear();
            if (teams != null) allTeams.addAll(teams);
            render();
            loadTeamMembersForPersonnel(teams);
        });

        teamViewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            if (swipeRefresh == null || mode != ManagementMode.Kind.TEAMS) return;
            swipeRefresh.setRefreshing(Boolean.TRUE.equals(loading));
        });

        // The dialogs report mutation failures themselves, so these two are only
        // drained to stop a rotation from replaying an old toast.
        teamViewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message != null) teamViewModel.clearError();
        });

        teamViewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null) teamViewModel.clearMessage();
        });
    }

    /** Loads membership details so driver cards can name the crew they belong to. */
    private void loadTeamMembersForPersonnel(List<CleaningTeam> listedTeams) {
        if (teamViewModel == null || listedTeams == null || listedTeams.isEmpty()) return;
        final int version = ++teamDetailsLoadVersion;
        final List<CleaningTeam> detailed =
                new ArrayList<>(java.util.Collections.nCopies(listedTeams.size(), null));
        final int[] remaining = {listedTeams.size()};
        for (int i = 0; i < listedTeams.size(); i++) {
            final int index = i;
            CleaningTeam listed = listedTeams.get(i);
            if (listed == null || isBlank(listed.getTeamId())) {
                if (--remaining[0] == 0) applyDetailedTeams(version, detailed);
                continue;
            }
            teamViewModel.loadTeam(listed.getTeamId(), new CleaningTeamRepository.TeamCallback() {
                @Override public void onSuccess(CleaningTeam team) {
                    detailed.set(index, team == null ? listed : team);
                    if (--remaining[0] == 0) applyDetailedTeams(version, detailed);
                }
                @Override public void onError(String message) {
                    detailed.set(index, listed);
                    if (--remaining[0] == 0) applyDetailedTeams(version, detailed);
                }
            });
        }
    }

    private void applyDetailedTeams(int version, List<CleaningTeam> detailed) {
        if (version != teamDetailsLoadVersion || !isAdded()) return;
        allTeams.clear();
        for (CleaningTeam team : detailed) {
            if (team != null) allTeams.add(team);
        }
        render();
    }

    /**
     * Handles the mutation results this screen triggers.
     *
     * <p>Only registration and status changes originate here. Edit, password
     * reset and disable are owned by {@link PersonnelDetailsFragment} and report
     * through their own ViewModel, so they never reach this observer.
     */
    private void handleAction(PersonnelViewModel.Action action) {
        switch (action.getType()) {
            case CREATED:
                RegistrationData registration = pendingRegistration;
                if (registrationDialog != null) registrationDialog.dismiss();
                showCredentialsDialog(action.getPersonnel(), action.getPassword(),
                        getString(R.string.personnel_credentials_title), registration);
                pendingRegistration = null;
                break;
            case STATUS_UPDATED:
                Toast.makeText(requireContext(), action.getMessage(), Toast.LENGTH_SHORT).show();
                break;
            default:
                break;
        }
    }

    /**
     * Repaints the cards and the list for whichever side the selector is on.
     *
     * <p>This is the single choke point: the chips, the search box and both
     * ViewModels' observers all funnel through it, so there is one place where
     * the counts, the filtered rows and the result line are decided together and
     * cannot describe different sets.
     */
    private void render() {
        if (adapter == null || vehicleAdapter == null) return;
        if (mode == ManagementMode.Kind.VEHICLES) renderVehicles();
        else if (mode == ManagementMode.Kind.TEAMS) renderTeams();
        else renderPersonnel();
    }

    private void renderPersonnel() {
        if (adapter != null) {
            adapter.setTeams(allTeams);
        }
        List<Personnel> active = new ArrayList<>();
        for (Personnel person : allPersonnel) {
            if (person != null && person.isEnabled()) active.add(person);
        }

        int online = 0;
        int cleaning = 0;
        for (Personnel person : active) {
            if (person.isCleaning()) cleaning++;
            if (person.isOnline()) online++;
        }
        totalCount.setText(String.valueOf(active.size()));
        onlineCount.setText(String.valueOf(online));
        cleaningCount.setText(String.valueOf(cleaning));

        String query = searchEditText == null ? "" : searchEditText.getText().toString().trim();
        String normalizedQuery = query.toLowerCase(Locale.getDefault());
        List<Personnel> filtered = new ArrayList<>();
        for (Personnel person : active) {
            if (!matchesFilter(person)) continue;
            if (normalizedQuery.isEmpty() || matchesQuery(person, normalizedQuery)) {
                filtered.add(person);
            }
        }

        adapter.setPersonnel(filtered);
        resultCount.setText(getString(activeMode.resultCount,
                filtered.size(), active.size()));
        emptyText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void renderVehicles() {
        // Counted from the held fleet rather than from the server's counts
        // object, so the cards and the list are always describing the same
        // snapshot even if one arrived from an older refresh.
        int available = 0;
        int assigned = 0;
        int underMaintenance = 0;
        int functional = 0;
        for (Vehicle vehicle : allVehicles) {
            if (vehicle == null) continue;
            if (vehicle.isAssigned()) assigned++;
            else if (vehicle.isAvailable()) available++;
            if (vehicle.isInMaintenance()) underMaintenance++;
            else functional++;
        }
        totalCount.setText(String.valueOf(allVehicles.size()));
        onlineCount.setText(String.valueOf(available));
        cleaningCount.setText(String.valueOf(assigned));
        vehicleMaintenanceCount.setText(String.valueOf(underMaintenance));
        vehicleFunctionalCount.setText(String.valueOf(functional));

        String query = searchEditText == null ? "" : searchEditText.getText().toString().trim();
        List<Vehicle> filtered = new ArrayList<>();
        for (Vehicle vehicle : allVehicles) {
            if (vehicle == null) continue;
            if (!matchesVehicleFilter(vehicle)) continue;
            if (query.isEmpty() || vehicle.matchesQuery(query.toLowerCase(Locale.getDefault()))) {
                filtered.add(vehicle);
            }
        }

        vehicleAdapter.setVehicles(filtered);
        resultCount.setText(getString(activeMode.resultCount,
                filtered.size(), allVehicles.size()));
        emptyText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void renderTeams() {
        int ready = 0;
        java.util.Set<String> workingTeamNames = new java.util.HashSet<>();
        for (Personnel person : allPersonnel) {
            if (person == null || !person.isEnabled() || !person.isCleaning()
                    || isBlank(person.getTeamName())) continue;
            workingTeamNames.add(person.getTeamName().trim().toLowerCase(Locale.ROOT));
        }
        int working = 0;
        for (CleaningTeam team : allTeams) {
            if (team == null) continue;
            if (team.isDispatchReady()) ready++;

            boolean isWorking = false;
            if (!isBlank(team.getTeamName()) && workingTeamNames.contains(
                    team.getTeamName().trim().toLowerCase(Locale.ROOT))) {
                isWorking = true;
            } else {
                for (CleaningTeam.Member member : team.getMembers()) {
                    if (member == null) continue;
                    if (member.getStatus() != null && (
                            member.getStatus().equalsIgnoreCase("cleaning")
                            || member.getStatus().equalsIgnoreCase("busy")
                            || member.getStatus().equalsIgnoreCase("in_progress")
                            || member.getStatus().equalsIgnoreCase("active"))) {
                        isWorking = true;
                        break;
                    }
                }
            }
            if (isWorking) working++;
        }
        totalCount.setText(String.valueOf(allTeams.size()));
        onlineCount.setText(String.valueOf(ready));
        cleaningCount.setText(String.valueOf(working));

        if (teamAdapter != null) {
            teamAdapter.setPersonnel(allPersonnel);
        }

        String query = searchEditText == null ? "" : searchEditText.getText().toString().trim();
        String normalizedQuery = query.toLowerCase(Locale.getDefault());
        List<CleaningTeam> filtered = new ArrayList<>();
        for (CleaningTeam team : allTeams) {
            if (team == null) continue;
            if (!matchesTeamFilter(team)) continue;
            if (normalizedQuery.isEmpty() || team.matchesQuery(normalizedQuery)) {
                filtered.add(team);
            }
        }

        teamAdapter.setTeams(filtered);
        resultCount.setText(getString(activeMode.resultCount, filtered.size(), allTeams.size()));
        emptyText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * The crews filter, against whether a team can actually be dispatched.
     *
     * <p>Needs attention is split into the two reasons it happens — no leader
     * at all, or a leader with fewer than two members — because those are fixed
     * in different places.
     */
    private boolean matchesTeamFilter(CleaningTeam team) {
        if (ManagementMode.TEAMS.filterAllValue.equalsIgnoreCase(currentFilter)) return true;
        if (ManagementMode.TEAMS.filterSecondValue.equalsIgnoreCase(currentFilter)) {
            return team.isDispatchReady();
        }
        if (ManagementMode.TEAMS.filterThirdValue.equalsIgnoreCase(currentFilter)) {
            return !team.hasLeader();
        }
        if (ManagementMode.TEAMS.filterFourthValue.equalsIgnoreCase(currentFilter)) {
            return team.hasLeader() && team.getCrewSize() < CleaningTeam.MINIMUM_MEMBERS;
        }
        return true;
    }

    /** Re-reads the crew list after a dialog changed something. */
    private void refreshTeams() {
        if (teamViewModel != null) teamViewModel.refreshTeams();
    }

    /** Opens the full-screen team details destination. */
    private void openTeamDetails(CleaningTeam team) {
        if (team == null || team.getTeamId() == null) return;
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, TeamDetailsFragment.newInstance(team.getTeamId()))
                .addToBackStack(null)
                .commit();
    }

    /** Opens the full-screen vehicle details destination. */
    private void openVehicleDetails(Vehicle vehicle) {
        if (vehicle == null || vehicle.getVehicleId() == null) return;
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer,
                        VehicleDetailsFragment.newInstance(vehicle.getVehicleId()))
                .addToBackStack(null)
                .commit();
    }

    private boolean matchesFilter(Personnel person) {
        if ("All".equalsIgnoreCase(currentFilter)) return true;
        if ("Online".equalsIgnoreCase(currentFilter)) return person.isOnline() && !person.isCleaning();
        if ("Cleaning".equalsIgnoreCase(currentFilter)) return person.isCleaning();
        if ("Offline".equalsIgnoreCase(currentFilter)) return !person.isOnline() && !person.isCleaning();
        return true;
    }

    /**
     * The fleet's filter, against the one status a vehicle has.
     *
     * <p>Maintenance is a state of its own, so it never collides with Assigned
     * the way an idle-but-available truck would under a flag-based scheme.
     */
    private boolean matchesVehicleFilter(Vehicle vehicle) {
        if ("All".equalsIgnoreCase(currentFilter)) return true;
        if ("Available".equalsIgnoreCase(currentFilter)) return vehicle.isAvailable();
        if ("Assigned".equalsIgnoreCase(currentFilter)) return vehicle.isAssigned();
        if ("Maintenance".equalsIgnoreCase(currentFilter)) return vehicle.isInMaintenance();
        return true;
    }

    private boolean matchesQuery(Personnel person, String query) {
        return contains(person.getFullName(), query)
                || contains(person.getPersonnelId(), query)
                || contains(person.getUsername(), query)
                || contains(person.getGmail(), query)
                || contains(person.getCurrentTask(), query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(query);
    }

    private void selectFilter(String filter) {
        currentFilter = filter;
        render();
    }

    /**
     * Centres the map on the vehicle's current collection.
     *
     * <p>Only available while the truck is actually out on a collection, which is
     * why the card's button is disabled otherwise: there is nothing to centre on.
     */
    private void locateVehicle(Vehicle vehicle) {
        if (vehicle == null || !vehicle.isAssigned()
                || vehicle.getCurrentReportLatitude() == null
                || vehicle.getCurrentReportLongitude() == null) {
            Toast.makeText(requireContext(),
                    "This vehicle is not on a collection right now.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (getActivity() instanceof DashboardActivity) {
            ((DashboardActivity) getActivity()).openMapForLocation(
                    vehicle.getCurrentReportLatitude(),
                    vehicle.getCurrentReportLongitude(),
                    vehicle.getPlateNumber());
        }
    }

    private void locatePersonnel(Personnel person) {
        if (person == null || !person.isCleaning()) {
            Toast.makeText(requireContext(), "This personnel member is not cleaning a report.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (getActivity() instanceof DashboardActivity) {
            ((DashboardActivity) getActivity()).openMapForPersonnel(person);
        }
    }

    /**
     * Opens the full-screen personnel details page.
     *
     * <p>Replaces the former details dialog. The fetch the dialog used to do
     * first is no longer needed: the details fragment owns its own loading and
     * error states, so navigating straight away shows the screen immediately
     * instead of a "Loadingâ€¦" toast while the round trip completes.
     */
    private void openDetails(Personnel person) {
        if (person == null || isBlank(person.getPersonnelId())) {
            Toast.makeText(requireContext(), "Personnel ID is missing.", Toast.LENGTH_SHORT).show();
            return;
        }
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer,
                        PersonnelDetailsFragment.newInstance(person.getPersonnelId()))
                .addToBackStack(null)
                .commit();
    }

    private void showRegistrationDialog(Bundle restoredForm) {
        if (!isAdded()) return;
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_personnel_register, null, false);
        TextInputLayout firstNameLayout = content.findViewById(R.id.registerFirstNameLayout);
        TextInputLayout lastNameLayout = content.findViewById(R.id.registerLastNameLayout);
        TextInputLayout addressLayout = content.findViewById(R.id.registerAddressLayout);
        TextInputLayout gmailLayout = content.findViewById(R.id.registerGmailLayout);
        TextInputLayout usernameLayout = content.findViewById(R.id.registerUsernameLayout);
        TextInputLayout phoneNumberLayout = content.findViewById(R.id.registerPhoneNumberLayout);
        TextInputLayout messengerLinkLayout =
                content.findViewById(R.id.registerMessengerLinkLayout);
        TextInputLayout passwordLayout = content.findViewById(R.id.registerPasswordLayout);
        TextInputLayout confirmPasswordLayout = content.findViewById(R.id.registerConfirmPasswordLayout);
        TextInputEditText firstName = content.findViewById(R.id.registerFirstName);
        TextInputEditText lastName = content.findViewById(R.id.registerLastName);
        TextInputEditText address = content.findViewById(R.id.registerAddress);
        TextInputEditText gmail = content.findViewById(R.id.registerGmail);
        TextInputEditText username = content.findViewById(R.id.registerUsername);
        TextInputEditText phoneNumber = content.findViewById(R.id.registerPhoneNumber);
        TextInputEditText messengerLink = content.findViewById(R.id.registerMessengerLink);
        TextInputEditText password = content.findViewById(R.id.registerPassword);
        TextInputEditText confirmPassword = content.findViewById(R.id.registerConfirmPassword);

        // The fleet behind the driver picker. Refreshed as the dialog opens rather
        // than at the tab switch, because which trucks are free is the one fact
        // that goes stale while a form sits on screen.
        vehicleViewModel.refreshFleet();

        registrationFirstNameInput = firstName;
        registrationLastNameInput = lastName;
        registrationAddressInput = address;
        registrationGmailInput = gmail;
        registrationUsernameInput = username;
        registrationPhoneNumberInput = phoneNumber;
        registrationMessengerLinkInput = messengerLink;
        registrationPasswordInput = password;
        registrationConfirmPasswordInput = confirmPassword;
        if (restoredForm != null) {
            firstName.setText(restoredForm.getString("firstName", ""));
            lastName.setText(restoredForm.getString("lastName", ""));
            address.setText(restoredForm.getString("address", ""));
            gmail.setText(restoredForm.getString("gmail", ""));
            username.setText(restoredForm.getString("username", ""));
            phoneNumber.setText(restoredForm.getString("phoneNumber", ""));
            messengerLink.setText(restoredForm.getString("messengerLink", ""));
            password.setText(restoredForm.getString("password", ""));
            confirmPassword.setText(restoredForm.getString("confirmPassword", ""));
            registrationPhotoUri = parseUri(restoredForm.getString(STATE_PHOTO_URI));
            pendingCameraPhotoUri = parseUri(restoredForm.getString(STATE_CAMERA_URI));
        }

        setupRegistrationOccupation(content, restoredForm);

        ShapeableImageView photoPreview = content.findViewById(R.id.registerPhotoPreview);
        MaterialButton takePhoto = content.findViewById(R.id.registerTakePhoto);
        MaterialButton pickPhoto = content.findViewById(R.id.registerPickPhoto);
        MaterialButton removePhoto = content.findViewById(R.id.registerRemovePhoto);
        registrationPhotoPreview = photoPreview;
        registrationRemovePhotoButton = removePhoto;
        takePhoto.setOnClickListener(v -> requestRegistrationPhotoCapture());
        pickPhoto.setOnClickListener(v -> pickPhotoLauncher.launch("image/*"));
        removePhoto.setOnClickListener(v -> {
            registrationPhotoUri = null;
            renderRegistrationPhoto();
        });
        renderRegistrationPhoto();

        registrationDialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Register", null)
                .create();
        registrationDialog.setOnShowListener(dialog -> {
            Button positive = (Button) registrationDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            registrationButton = new ButtonState(positive, "Register");
            positive.setOnClickListener(v -> {
                clearErrors(firstNameLayout, lastNameLayout, addressLayout, gmailLayout,
                        usernameLayout, phoneNumberLayout, messengerLinkLayout,
                        passwordLayout, confirmPasswordLayout, registrationOccupationLayout);
                String first = value(firstName);
                String last = value(lastName);
                String fullAddress = value(address);
                String gmailValue = value(gmail).toLowerCase(Locale.US);
                String usernameValue = value(username);
                String phoneValue = value(phoneNumber);
                String messengerValue = value(messengerLink);
                String passwordValue = value(password);
                String confirmValue = value(confirmPassword);
                boolean valid = true;
                valid &= requireField(firstNameLayout, first);
                valid &= requireField(lastNameLayout, last);
                valid &= requireField(addressLayout, fullAddress);
                if (!isGmail(gmailValue)) {
                    gmailLayout.setError(getString(R.string.personnel_error_gmail));
                    valid = false;
                } else {
                    requireField(gmailLayout, gmailValue);
                }
                if (!isValidPersonnelUsername(usernameValue)) {
                    usernameLayout.setError(getString(R.string.personnel_error_username));
                    valid = false;
                }
                if (!isValidPhoneNumber(phoneValue)) {
                    phoneNumberLayout.setError(
                            getString(R.string.personnel_details_error_phone));
                    valid = false;
                }
                if (!isValidMessengerLink(messengerValue)) {
                    messengerLinkLayout.setError(
                            getString(R.string.personnel_details_error_messenger_link));
                    valid = false;
                }
                if (passwordValue.length() < 8) {
                    passwordLayout.setError(getString(R.string.personnel_error_password));
                    valid = false;
                }
                if (!passwordValue.equals(confirmValue)) {
                    confirmPasswordLayout.setError(getString(R.string.personnel_error_confirm));
                    valid = false;
                }
                if (selectedOccupation().equals(Personnel.OCCUPATION_DRIVER)
                        && requiresAssignedVehicle() && selectedRegistrationVehicle == null) {
                    // A driver is registered with the truck they will drive, so
                    // there is no sensible half-finished account to save here.
                    registrationOccupationLayout.setError(
                            getString(R.string.personnel_assign_vehicle_required));
                    valid = false;
                }
                if (!valid) return;

                byte[] photoBytes = readRegistrationPhotoBytes();
                if (registrationPhotoUri != null && photoBytes == null) {
                    // The chosen photo can no longer be opened; let the
                    // administrator pick it again instead of silently
                    // registering without the face they selected.
                    Toast.makeText(requireContext(), R.string.personnel_photo_unreadable,
                            Toast.LENGTH_LONG).show();
                    return;
                }

                pendingRegistration = new RegistrationData(first, last, fullAddress,
                        gmailValue, usernameValue, passwordValue);
                restoredRegistrationFormState = null;
                setRegistrationSubmitting(true);
                viewModel.createPersonnel(first, last, fullAddress, gmailValue,
                        usernameValue, passwordValue, phoneValue, messengerValue,
                        selectedOccupation(), selectedRegistrationVehicleId(), photoBytes);
            });
        });
        registrationDialog.setOnCancelListener(dialog -> {
            restoredRegistrationFormState = null;
            resetRegistrationPhoto();
        });
        registrationDialog.setOnDismissListener(dialog -> {
            registrationButton = null;
            pendingRegistration = null;
            registrationPhotoPreview = null;
            registrationRemovePhotoButton = null;
            // Released here rather than at the next open: while the fields are
            // null the fleet observer cannot reach a dismissed dialog's views, and
            // the chosen truck must not carry over to the next registration.
            registrationOccupationLayout = null;
            registrationOccupationInput = null;
            registrationVehicleSection = null;
            registrationVehicleList = null;
            registrationVehicleEmpty = null;
            registrationVehicleAdapter = null;
            selectedRegistrationVehicle = null;
            resetRegistrationPhoto();
        });
        registrationDialog.show();
    }

    /**
     * Wires the occupation dropdown and the vehicle picker it controls.
     *
     * <p>The picker is built here but painted from
     * {@link #renderRegistrationVehicles()}, which the fleet observer also calls.
     * Reading the held fleet at open time would show whatever arrived with the tab
     * switch; repainting on every fleet response means the list of free trucks is
     * the live one for as long as the form is up.
     */
    private void setupRegistrationOccupation(View content, Bundle restoredForm) {
        registrationOccupationLayout = content.findViewById(R.id.registerOccupationLayout);
        registrationOccupationInput = content.findViewById(R.id.registerOccupationInput);
        registrationVehicleSection = content.findViewById(R.id.registerVehicleSection);
        registrationVehicleList = content.findViewById(R.id.registerVehicleList);
        registrationVehicleEmpty = content.findViewById(R.id.registerVehicleEmpty);

        registrationOccupationInput.setAdapter(new android.widget.ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1,
                new String[]{getString(R.string.personnel_occupation_regular),
                        getString(R.string.personnel_occupation_driver)}));

        // Regular is the default rather than an empty dropdown: most of a cleanup
        // team walks the route, and leaving the field blank would only ever send
        // the answer the form already implies.
        String occupation = restoredForm == null ? Personnel.OCCUPATION_REGULAR
                : restoredForm.getString(STATE_OCCUPATION, Personnel.OCCUPATION_REGULAR);
        registrationOccupationInput.setText(occupationLabel(occupation), false);

        registrationVehicleAdapter = new RegistrationVehicleAdapter();
        registrationVehicleAdapter.setListener(vehicle -> {
            selectedRegistrationVehicle = vehicle;
            registrationOccupationLayout.setError(null);
            registrationVehicleAdapter.setAssignedVehicleId(selectedRegistrationVehicleId());
        });
        registrationVehicleList.setLayoutManager(new LinearLayoutManager(requireContext()));
        registrationVehicleList.setAdapter(registrationVehicleAdapter);

        String restoredVehicleId = restoredForm == null ? null
                : restoredForm.getString(STATE_ASSIGNED_VEHICLE);
        if (restoredVehicleId != null) {
            for (Vehicle vehicle : availableVehicles()) {
                if (restoredVehicleId.equals(vehicle.getVehicleId())) {
                    selectedRegistrationVehicle = vehicle;
                    break;
                }
            }
        }

        registrationOccupationInput.setOnItemClickListener((parent, item, position, id) -> {
            // Picking Regular drops the truck: it belongs to the driver answer,
            // not to the person, and carrying it over would assign a cleaner.
            if (!isDriverSelected()) selectedRegistrationVehicle = null;
            registrationOccupationLayout.setError(null);
            syncRegistrationVehicleSection();
        });
        syncRegistrationVehicleSection();
    }

    /** Shows the vehicle section exactly for a driver, and repaints what it holds. */
    private void syncRegistrationVehicleSection() {
        if (registrationVehicleSection == null) return;
        boolean driver = isDriverSelected();
        registrationVehicleSection.setVisibility(driver ? View.VISIBLE : View.GONE);
        // The helper explains the extra rows the answer just produced, rather than
        // leaving the dropdown to imply a rule the form is not restating.
        registrationOccupationLayout.setHelperText(driver
                ? getString(R.string.personnel_occupation_driver_helper) : null);
        if (driver) renderRegistrationVehicles();
    }

    /**
     * Repaints the picker from the held fleet, dropping a choice that is no longer
     * available.
     *
     * <p>Called both when the dialog opens and whenever the fleet changes, which
     * is what makes a truck assigned elsewhere in the app disappear from under a
     * stale selection instead of being submitted.
     */
    private void renderRegistrationVehicles() {
        if (registrationVehicleAdapter == null) return;

        List<Vehicle> available = availableVehicles();
        if (selectedRegistrationVehicle != null) {
            boolean stillAvailable = false;
            for (Vehicle vehicle : available) {
                if (vehicle.getVehicleId() != null
                        && vehicle.getVehicleId().equals(selectedRegistrationVehicle.getVehicleId())) {
                    stillAvailable = true;
                    break;
                }
            }
            if (!stillAvailable) selectedRegistrationVehicle = null;
        }

        registrationVehicleAdapter.setVehicles(available);
        registrationVehicleAdapter.setAssignedVehicleId(selectedRegistrationVehicleId());
        if (registrationVehicleEmpty != null) {
            registrationVehicleEmpty.setVisibility(available.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (registrationVehicleList != null) {
            registrationVehicleList.setVisibility(available.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    /** The trucks a driver could be given right now. */
    private List<Vehicle> availableVehicles() {
        java.util.Set<String> takenVehicleIds = new java.util.HashSet<>();
        if (allPersonnel != null) {
            for (Personnel person : allPersonnel) {
                if (person == null) continue;
                if (!person.isEnabled()) continue;
                if (!person.isDriver()) continue;
                String assignedVehicleId = person.getAssignedVehicleId();
                if (isBlank(assignedVehicleId)) continue;
                takenVehicleIds.add(assignedVehicleId.trim());
            }
        }

        List<Vehicle> available = new ArrayList<>();
        for (Vehicle vehicle : allVehicles) {
            if (vehicle == null) continue;
            if (!vehicle.isAvailable()) continue;
            if (isBlank(vehicle.getVehicleId())) continue;
            if (takenVehicleIds.contains(vehicle.getVehicleId().trim())) continue;
            available.add(vehicle);
        }
        return available;
    }

    /**
     * Whether a driver has to leave with a truck.
     *
     * <p>True whenever there is a truck to choose. With an empty fleet the answer
     * is no: requiring a pick from an empty list would block the registration of
     * a driver the office genuinely hired, over a truck that does not exist yet.
     */
    private boolean requiresAssignedVehicle() {
        return !availableVehicles().isEmpty();
    }

    private boolean isDriverSelected() {
        if (registrationOccupationInput == null) return false;
        CharSequence current = registrationOccupationInput.getText();
        return current != null && getString(R.string.personnel_occupation_driver)
                .equalsIgnoreCase(current.toString().trim());
    }

    /**
     * The occupation to send, in the server's own vocabulary.
     *
     * <p>The dropdown shows translated labels while the wire carries
     * {@link Personnel}'s constants, so a Tagalog locale still registers an
     * account the server reads as a Driver.
     */
    private String selectedOccupation() {
        return isDriverSelected() ? Personnel.OCCUPATION_DRIVER : Personnel.OCCUPATION_REGULAR;
    }

    /** The dropdown's label for a stored wire value, defaulting to Regular. */
    private String occupationLabel(String wireValue) {
        return Personnel.OCCUPATION_DRIVER.equalsIgnoreCase(wireValue)
                ? getString(R.string.personnel_occupation_driver)
                : getString(R.string.personnel_occupation_regular);
    }

    @Nullable
    private String selectedRegistrationVehicleId() {
        return selectedRegistrationVehicle == null ? null : selectedRegistrationVehicle.getVehicleId();
    }

    private void showCredentialsDialog(Personnel person, String password, String title,
                                        RegistrationData registrationData) {
        if (!isAdded()) return;
        Personnel displayed = person;
        if (displayed == null && registrationData != null) {
            displayed = new Personnel();
            displayed.setFirstName(registrationData.firstName);
            displayed.setLastName(registrationData.lastName);
            displayed.setFullName(registrationData.firstName + " " + registrationData.lastName);
            displayed.setAddress(registrationData.address);
            displayed.setGmail(registrationData.gmail);
            displayed.setUsername(registrationData.username);
        }
        if (displayed == null) displayed = new Personnel();
        String safePassword = password == null ? "" : password;
        if (safePassword.isEmpty() && registrationData != null) {
            safePassword = registrationData.password;
        }

        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_personnel_credentials, null, false);
        TextView id = content.findViewById(R.id.credentialPersonnelId);
        TextView name = content.findViewById(R.id.credentialName);
        TextView address = content.findViewById(R.id.credentialAddress);
        TextView gmail = content.findViewById(R.id.credentialGmail);
        TextView username = content.findViewById(R.id.credentialUsername);
        TextView passwordText = content.findViewById(R.id.credentialPassword);
        id.setText(orDash(displayed.getPersonnelId()));
        name.setText(displayed.getFullName());
        address.setText(orDash(displayed.getAddress()));
        gmail.setText(orDash(displayed.getGmail()));
        username.setText(orDash(displayed.getUsername()));
        passwordText.setText(safePassword.isEmpty() ? "Not returned by server" : safePassword);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(content)
                .setPositiveButton(R.string.personnel_done, null)
                .setCancelable(false)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        bindCopy(content, R.id.copyPersonnelId, id.getText().toString(), getString(R.string.personnel_id_label));
        bindCopy(content, R.id.copyName, name.getText().toString(), getString(R.string.personnel_name_label));
        bindCopy(content, R.id.copyAddress, address.getText().toString(), getString(R.string.personnel_address));
        bindCopy(content, R.id.copyGmail, gmail.getText().toString(), getString(R.string.personnel_gmail_label));
        bindCopy(content, R.id.copyUsername, username.getText().toString(), getString(R.string.personnel_username_label));
        bindCopy(content, R.id.copyPassword, passwordText.getText().toString(), getString(R.string.personnel_password));
        dialog.show();
    }


    private void bindCopy(View root, int buttonId, String value, String label) {
        View button = root.findViewById(buttonId);
        button.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager)
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
                Toast.makeText(requireContext(), label + " copied", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setRegistrationSubmitting(boolean submitting) {
        if (registrationButton == null) return;
        registrationButton.view.setEnabled(!submitting);
        registrationButton.view.setAlpha(submitting ? 0.6f : 1f);
        if (submitting) registrationButton.view.setText("Creating...");
    }

    private void restoreRegistrationButton() {
        if (registrationButton == null) return;
        registrationButton.view.setEnabled(true);
        registrationButton.view.setAlpha(1f);
        registrationButton.view.setText(registrationButton.text);
    }

    private boolean requireField(TextInputLayout layout, String value) {
        if (isBlank(value)) {
            layout.setError(getString(R.string.personnel_error_required));
            return false;
        }
        return true;
    }

    private void clearErrors(TextInputLayout... layouts) {
        for (TextInputLayout layout : layouts) layout.setError(null);
    }

    private boolean isGmail(String value) {
        return value != null && value.matches("^[A-Za-z0-9._%+\\-]+@gmail\\.com$");
    }

    /** Mirrors the backend phone constraint so the error shows before the round trip. */
    private boolean isValidPhoneNumber(String value) {
        return value == null || value.isEmpty() || value.matches("^[0-9+() .-]{7,25}$");
    }

    /** Mirrors the backend messenger constraint: an absolute http(s) URL. */
    private boolean isValidMessengerLink(String value) {
        return value == null || value.isEmpty() || value.matches("^https?://\\S+$");
    }

    private boolean isValidPersonnelUsername(String value) {
        return value != null && value.matches("^[A-Za-z0-9][A-Za-z0-9._-]{2,49}$");
    }

    private String value(TextInputEditText editText) {
        return editText == null || editText.getText() == null
                ? "" : editText.getText().toString().trim();
    }

    private String formatDate(String raw) {
        if (isBlank(raw)) return "Never";
        String dateValue = raw.trim();
        try {
            return formatDate(new Date(Long.parseLong(dateValue)));
        } catch (NumberFormatException ignored) {
            // Continue with ISO parsing.
        }
        String[] patterns = new String[]{
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
                parser.setLenient(false);
                if (pattern.endsWith("'Z'")) parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                return formatDate(parser.parse(dateValue));
            } catch (ParseException ignored) {
                // Try the next supported format.
            }
        }
        return dateValue;
    }

    private String formatDate(Date date) {
        if (date == null) return "Never";

        // Locale.US and Asia/Manila, pinned rather than taken from the device.
        // Locale.getDefault() let the app's own language switch the month name and
        // the AM/PM marker, and the implicit default timezone rendered these in the
        // device's zone rather than the zone the personnel actually worked in.
        SimpleDateFormat formatter = new SimpleDateFormat("MMM d, yyyy Â· h:mm a", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
        return formatter.format(date);
    }

    private String orDash(String value) {
        return isBlank(value) ? "â€”" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** Starts the photo flow, asking for the camera permission the first time. */
    private void requestRegistrationPhotoCapture() {
        boolean granted = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            launchCameraCapture();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    /**
     * Points the camera at a cache file shared through the manifest
     * FileProvider, so the capture lands on disk without writing to
     * external storage.
     */
    private void launchCameraCapture() {
        try {
            File imagesDir = new File(requireContext().getCacheDir(), "images");
            if (!imagesDir.exists() && !imagesDir.mkdirs()) {
                Toast.makeText(requireContext(), R.string.personnel_photo_capture_failed,
                        Toast.LENGTH_LONG).show();
                return;
            }
            File captureFile = new File(imagesDir, CAPTURE_FILE_NAME);
            pendingCameraPhotoUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", captureFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraPhotoUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            takePictureLauncher.launch(intent);
        } catch (RuntimeException error) {
            pendingCameraPhotoUri = null;
            Toast.makeText(requireContext(), R.string.personnel_photo_capture_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Crops to the shared 1:1 avatar settings used by the profile screen:
     * a circle preview and a small square output the upload keeps light.
     */
    private void startRegistrationCrop(Uri sourceUri) {
        if (sourceUri == null) return;
        File cropFile = new File(requireContext().getCacheDir(), CROP_FILE_NAME);
        UCrop uCrop = UCrop.of(sourceUri, Uri.fromFile(cropFile));
        uCrop.withAspectRatio(1, 1);
        uCrop.withMaxResultSize(500, 500);

        UCrop.Options options = new UCrop.Options();
        options.setCircleDimmedLayer(true);
        options.setShowCropGrid(false);
        options.setShowCropFrame(false);
        options.setToolbarTitle(getString(R.string.personnel_photo_crop_title));
        int green = ContextCompat.getColor(requireContext(), R.color.green);
        options.setToolbarColor(green);
        options.setStatusBarColor(green);
        options.setToolbarWidgetColor(ContextCompat.getColor(requireContext(), R.color.white));
        options.setActiveControlsWidgetColor(green);
        uCrop.withOptions(options);

        cropPhotoLauncher.launch(uCrop.getIntent(requireContext()));
    }

    private void setRegistrationPhoto(Uri uri) {
        registrationPhotoUri = uri;
        renderRegistrationPhoto();
    }

    private void resetRegistrationPhoto() {
        registrationPhotoUri = null;
        pendingCameraPhotoUri = null;
    }

    private void renderRegistrationPhoto() {
        if (registrationPhotoPreview == null) return;
        boolean hasPhoto = registrationPhotoUri != null;
        if (hasPhoto) {
            // The crop output file is reused for every attempt, so a fresh
            // signature keeps Glide from serving the previously cropped face.
            Glide.with(this)
                    .load(registrationPhotoUri)
                    .signature(new ObjectKey(String.valueOf(System.currentTimeMillis())))
                    .centerCrop()
                    .placeholder(R.drawable.ic_person_default)
                    .into(registrationPhotoPreview);
        } else {
            registrationPhotoPreview.setImageResource(R.drawable.ic_person_default);
        }
        if (registrationRemovePhotoButton != null) {
            registrationRemovePhotoButton.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
        }
    }

    /** Reads the selected photo as raw JPEG bytes, or {@code null} when it is gone. */
    @Nullable
    private byte[] readRegistrationPhotoBytes() {
        if (registrationPhotoUri == null) return null;
        try (InputStream input = requireContext().getContentResolver()
                .openInputStream(registrationPhotoUri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) return null;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            byte[] bytes = output.toByteArray();
            return bytes.length == 0 ? null : bytes;
        } catch (IOException | SecurityException error) {
            return null;
        }
    }

    @Nullable
    private Uri parseUri(String value) {
        return isBlank(value) ? null : Uri.parse(value.trim());
    }

    @Nullable
    private String uriString(Uri uri) {
        return uri == null ? null : uri.toString();
    }

    @Override
    public void onDestroyView() {
        statusRefreshHandler.removeCallbacks(statusRefreshRunnable);
        dismissDialog(registrationDialog);
        registrationDialog = null;
        registrationButton = null;
        pendingRegistration = null;
        registrationFirstNameInput = null;
        registrationLastNameInput = null;
        registrationAddressInput = null;
        registrationGmailInput = null;
        registrationUsernameInput = null;
        registrationPhoneNumberInput = null;
        registrationMessengerLinkInput = null;
        registrationPasswordInput = null;
        registrationConfirmPasswordInput = null;
        registrationPhotoPreview = null;
        registrationRemovePhotoButton = null;
        super.onDestroyView();
    }

    private void dismissDialog(AlertDialog dialog) {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }

    private static final class ButtonState {
        private final Button view;
        private final String text;

        private ButtonState(Button view, String text) {
            this.view = view;
            this.text = text;
        }
    }

    private static final class RegistrationData {
        private final String firstName;
        private final String lastName;
        private final String address;
        private final String gmail;
        private final String username;
        private final String password;

        private RegistrationData(String firstName, String lastName, String address,
                                 String gmail, String username, String password) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.address = address;
            this.gmail = gmail;
            this.username = username;
            this.password = password;
        }
    }
}
