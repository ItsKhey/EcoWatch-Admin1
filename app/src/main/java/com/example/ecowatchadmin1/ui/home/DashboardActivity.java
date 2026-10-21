package com.example.ecowatchadmin1.ui.home;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.models.User;
import com.example.ecowatchadmin1.data.remote.ServerConfig;
import com.example.ecowatchadmin1.data.repository.AuthRepository;
import com.example.ecowatchadmin1.ui.alerts.NotificationsFragment;
import com.example.ecowatchadmin1.ui.announcements.AnnouncementListFragment;
import com.example.ecowatchadmin1.ui.auth.LoginActivity;
import com.example.ecowatchadmin1.ui.personnel.PersonnelDetailsFragment;
import com.example.ecowatchadmin1.ui.personnel.PersonnelFragment;
import com.example.ecowatchadmin1.ui.profile.ProfileFragment;
import com.example.ecowatchadmin1.ui.profile.SettingsMenuFragment;
import com.example.ecowatchadmin1.ui.reports.ReportsFragment;
import com.example.ecowatchadmin1.ui.residents.ReporterAccountsFragment;
import com.example.ecowatchadmin1.ui.residents.ReporterDetailsFragment;
import com.example.ecowatchadmin1.utils.LocaleHelper;
import com.example.ecowatchadmin1.utils.SessionManager;

public class DashboardActivity extends AppCompatActivity {
    /**
     * Deep-link contract shared with the Resident app's notification center.
     * A tap on a background/terminated-app system notification launches this
     * activity single-top with these extras already attached.
     */
    public static final String ACTION_OPEN_NOTIFICATION =
            "com.example.ecowatchadmin1.action.OPEN_NOTIFICATION";
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";
    public static final String EXTRA_REPORT_ID = "report_id";

    private LinearLayout itemHome, itemInbox, itemCreate, itemPersonnel, itemProfile;
    private FrameLayout itemTopBarNotifications;
    private ImageView iconHome, iconInbox, iconMap, iconPersonnel, iconProfile, pfpImage, backgroundImage;
    private TextView labelHome, labelInbox, labelCreate, labelPersonnel, labelProfile;
    private TextView notificationBadge;
    private SessionManager sessionManager;
    private AuthRepository authRepository;
    private com.example.ecowatchadmin1.data.repository.NotificationRepository notificationRepository;
    private android.os.Handler pollerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    /** Used for the deferred bottom-navigation re-sync after a pop. */
    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    /**
     * Number of back stack entries the last time the back stack changed.
     *
     * <p>Used to tell a pop, which needs the lit tab put back, from a push,
     * which already lit the right tab when the screen was opened.
     */
    private int backStackCountAtLastChange;
    private Runnable pollerRunnable = new Runnable() {
        @Override
        public void run() {
            if (notificationRepository != null) {
                notificationRepository.fetchNotifications();
            }
            pollerHandler.postDelayed(this, 10000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sessionManager = new SessionManager(this);
        LocaleHelper.applyLocale(this, sessionManager.getLanguage());
        
        super.onCreate(savedInstanceState);
        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);
        authRepository = new AuthRepository(getApplication());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        pfpImage = findViewById(R.id.pfpImage);
        itemTopBarNotifications = findViewById(R.id.itemTopBarNotifications);

        if (itemTopBarNotifications != null) {
            itemTopBarNotifications.setOnClickListener(v -> {
                loadFragment(new NotificationsFragment(), "Alerts");
                // Optional: reset bottom nav UI since we're in a special fragment
                resetNavItem(itemHome, iconHome, labelHome);
                resetNavItem(itemInbox, iconInbox, labelInbox);
                resetNavItem(itemPersonnel, iconPersonnel, labelPersonnel);
                resetNavItem(itemProfile, iconProfile, labelProfile);
                resetNavItem(itemCreate, iconMap, labelCreate);
            });
        }
        pfpImage.setOnClickListener(v -> openProfile());
        backgroundImage = findViewById(R.id.backgroundImage);
        updateBackground();

        initBottomNav();

        // The lit bottom-navigation tab has to be put back when a screen that
        // was opened on top of another one is closed again.
        getSupportFragmentManager()
                .addOnBackStackChangedListener(this::syncNavigationAfterPop);
        backStackCountAtLastChange = getSupportFragmentManager().getBackStackEntryCount();

        // Only seed the default destination on a fresh activity. During a
        // theme/process recreation FragmentManager restores the current screen
        // and its back stack for us.
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment(), "Home");
            updateNavUI(itemHome, iconHome, labelHome);
        } else {
            restoreNavigationSelection();
        }
        updateTopBarDate();

        // Sync profile from server
        authRepository.fetchMe(new AuthRepository.ApiCallback<User>() {
            @Override public void onSuccess(User result) {}
            @Override public void onError(String error) {}
        });

        // Observe user for PFP
        authRepository.getUser().observe(this, user -> {
            if (user != null && user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
                Glide.with(this)
                        .load(ServerConfig.absoluteUrl(this, user.getProfileImageUrl()))
                        .placeholder(R.drawable.pfp)
                        .into(pfpImage);
            } else {
                pfpImage.setImageResource(R.drawable.pfp);
            }
        });

        notificationRepository = com.example.ecowatchadmin1.data.repository.NotificationRepository
                .getInstance(getApplication());

        // Unread badge, cloned from the Resident app's DashboardActivity. The
        // count is a Room LiveData, so every write to the notifications table
        // (FCM push, mark-as-read, trash/restore, permanent delete) re-emits it
        // and repaints the badge without any manual refresh calls.
        notificationBadge = findViewById(R.id.badgeTopBarNotifications);
        if (notificationBadge != null) {
            notificationRepository.getUnreadCount().observe(this, count -> {
                int unread = count == null ? 0 : Math.max(0, count);
                notificationBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
                notificationBadge.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
            });
        }

        checkNotificationPermission();
        com.example.ecowatchadmin1.notifications.FcmTokenSyncScheduler.syncCurrentToken(this);
        pollerHandler.postDelayed(this::handleNotificationIntent, 250);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent();
    }

    /**
     * Cloned from the Resident app. Marks the tapped notification read and
     * routes straight to the matching Report Details screen; falls back to the
     * notification center when the payload carried no report.
     */
    private void handleNotificationIntent() {
        Intent intent = getIntent();
        if (intent == null) return;
        String notificationId = intent.getStringExtra(EXTRA_NOTIFICATION_ID);
        if (notificationId == null) notificationId = intent.getStringExtra("notificationId");
        String reportId = intent.getStringExtra(EXTRA_REPORT_ID);
        if (reportId == null) reportId = intent.getStringExtra("reportId");

        boolean notificationAction = ACTION_OPEN_NOTIFICATION.equals(intent.getAction())
                || "OPEN_ECOWATCH_NOTIFICATION".equals(intent.getAction())
                || notificationId != null
                || reportId != null;
        if (!notificationAction) return;

        if (notificationId != null && notificationRepository != null) {
            notificationRepository.markAsReadByServerId(notificationId);
        }
        if (reportId != null && !reportId.trim().isEmpty()) {
            openReportById(reportId);
        } else {
            openNotifications();
        }
        intent.removeExtra(EXTRA_NOTIFICATION_ID);
        intent.removeExtra("notificationId");
        intent.removeExtra(EXTRA_REPORT_ID);
        intent.removeExtra("reportId");
        intent.setAction(Intent.ACTION_MAIN);
    }

    private void checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionManager.isSessionExpired()) {
            performLogout("Session expired. Please login again.");
        } else {
            sessionManager.updateLastActive();
            // Global refresh when returning to app
            authRepository.fetchMe(null);
            // Token rotation is cheap and idempotent, so re-register on resume
            // exactly like the Resident app does.
            com.example.ecowatchadmin1.notifications.FcmTokenSyncScheduler
                    .syncCurrentToken(this);

            // Start poller
            pollerHandler.removeCallbacks(pollerRunnable);
            pollerHandler.post(pollerRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop poller to save battery/bandwidth in background
        pollerHandler.removeCallbacks(pollerRunnable);
    }

    private void performLogout(String message) {
        authRepository.logout();
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void initBottomNav() {
        itemHome = findViewById(R.id.itemHome);
        itemInbox = findViewById(R.id.itemInbox);
        itemCreate = findViewById(R.id.itemCreate);
        itemPersonnel = findViewById(R.id.itemPersonnel);
        itemProfile = findViewById(R.id.itemProfile);

        iconHome = findViewById(R.id.iconHome);
        iconInbox = findViewById(R.id.iconInbox);
        iconMap = findViewById(R.id.iconMap);
        iconPersonnel = findViewById(R.id.iconPersonnel);
        iconProfile = findViewById(R.id.iconProfile);

        labelHome = findViewById(R.id.labelHome);
        labelInbox = findViewById(R.id.labelInbox);
        labelCreate = findViewById(R.id.labelCreate);
        labelPersonnel = findViewById(R.id.labelPersonnel);
        labelProfile = findViewById(R.id.labelProfile);

        // Removed the override causing it to change to logo2
        labelCreate.setText(R.string.map);

        itemHome.setOnClickListener(v -> {
            updateNavUI(itemHome, iconHome, labelHome);
            loadFragment(new HomeFragment(), "Home");
        });

        itemInbox.setOnClickListener(v -> {
            updateNavUI(itemInbox, iconInbox, labelInbox);
            loadFragment(new ReportsFragment(), "Reports");
        });

        itemPersonnel.setOnClickListener(v -> {
            updateNavUI(itemPersonnel, iconPersonnel, labelPersonnel);
            loadFragment(new PersonnelFragment(), "Personnel");
        });

        itemProfile.setOnClickListener(v -> {
            updateNavUI(itemProfile, iconProfile, labelProfile);
            loadFragment(new SettingsMenuFragment(), "Profile");
        });
        
        itemCreate.setOnClickListener(v -> {
            updateNavUI(itemCreate, iconMap, labelCreate);
            loadFragment(new AdminMapFragment(), "Live Map");
        });
    }

    /**
     * Opens the existing Live Map destination. Backs the Quick Actions entry so
     * the dashboard and the bottom navigation share one map screen.
     */
    public void openMap() {
        if (itemCreate == null) {
            loadFragment(new AdminMapFragment(), "Live Map");
            return;
        }
        itemCreate.performClick();
    }

    public void navigateToReports() {
        if (itemInbox != null) {
            itemInbox.performClick();
        }
    }

    public void navigateToHome() {
        if (itemHome != null) {
            itemHome.performClick();
        } else {
            loadFragment(new HomeFragment(), "Home");
        }
    }

    /**
     * Opens the reports tab already filtered to the unassigned queue. Backs the
     * "View All" action on the dashboard's Needs Assignment card.
     */
    public void navigateToPendingReports() {
        if (itemInbox == null) return;
        updateNavUI(itemInbox, iconInbox, labelInbox);
        loadFragment(ReportsFragment.forPendingList(), "Reports");
    }

    /**
     * Opens the reports tab already filtered to the in progress queue. Backs the
     * "View All" action on the dashboard's Ongoing Cleaning Operation card.
     */
    public void navigateToInProgressReports() {
        if (itemInbox == null) return;
        updateNavUI(itemInbox, iconInbox, labelInbox);
        loadFragment(ReportsFragment.forInProgressList(), "Reports");
    }

    /**
     * Opens the full list of announcements this administrator has published.
     * Backs the "View all" action on the dashboard's Your Announcements card.
     */
    public void navigateToAnnouncements() {
        loadFragment(new AnnouncementListFragment(), "Announcements");
    }

    /**
     * Opens the reports tab filtered by the given status.
     * Backs the dashboard card click actions.
     */
    public void navigateToReportsWithFilter(String status) {
        if (itemInbox == null) return;
        updateNavUI(itemInbox, iconInbox, labelInbox);
        if (status == null || status.trim().isEmpty()) {
            loadFragment(new ReportsFragment(), "Reports");
        } else {
            loadFragment(ReportsFragment.forStatus(status), "Reports");
        }
    }

    /**
     * Opens the personnel management tab.
     * Backs the dashboard personnel card click actions.
     */
    public void navigateToPersonnel() {
        if (itemPersonnel != null) {
            itemPersonnel.performClick();
        }
    }

    /**
     * Opens the reporter accounts list.
     *
     * <p>Backs the dashboard's "Reporter Accounts" quick action. Previously this
     * screen was only reachable through Settings, which is several taps away from
     * a report that needs an account looked at.
     *
     * <p>Lights the Profile tab deliberately, because
     * {@link #restoreNavigationSelection()} groups this screen with the settings
     * destinations. Lighting Personnel here would claim a tab that the screen is
     * not part of, and a rotation would immediately correct it.
     */
    public void openReporterAccounts() {
        updateNavUI(itemProfile, iconProfile, labelProfile);
        loadFragment(new ReporterAccountsFragment(), "Reporter Accounts");
    }

    /**
     * Opens the personnel list with the registration form already raised.
     *
     * <p>Backs the dashboard's "Register ESP Account" quick action.
     *
     * <p>Cannot reuse {@link #navigateToPersonnel()}: that replays a click on the
     * Personnel tab, which shows the list. This needs the fragment built with the
     * one-shot argument that makes it open the form on arrival, so the admin lands
     * on the form itself instead of having to find the register button.
     *
     * <p>The argument is consumed by the fragment once used, so returning to the
     * personnel list afterwards does not reopen the form.
     */
    public void registerNewPersonnel() {
        updateNavUI(itemPersonnel, iconPersonnel, labelPersonnel);
        loadFragment(PersonnelFragment.newInstance(true), "Personnel");
    }

    /** Opens the notification center. Used when a push carries no report link. */
    public void openNotifications() {
        if (itemTopBarNotifications != null) {
            itemTopBarNotifications.performClick();
        } else {
            loadFragment(new NotificationsFragment(), "Alerts");
        }
    }

    /**
     * Cloned from the Resident app: opens the Reports tab already targeting the
     * notification's report so the Report Details dialog is shown on arrival.
     */
    public void openReportById(String reportId) {
        if (reportId == null || reportId.trim().isEmpty()) return;
        if (itemInbox == null) return;
        updateNavUI(itemInbox, iconInbox, labelInbox);
        loadFragment(ReportsFragment.forReportId(reportId.trim()), "Reports");
    }

    /**
     * Opens the live map centred on one report and reuses
     * {@link AdminMapFragment#newInstance} rather than introducing a second map
     * screen. Backs the Ongoing Cleaning Operation card on the dashboard and
     * the report markers on the map itself.
     */
    public void openMapForReport(@NonNull Report report) {
        if (report == null) return;

        Double latitude = null;
        Double longitude = null;
        if (report.getLatitude() >= -90 && report.getLatitude() <= 90
                && report.getLongitude() >= -180 && report.getLongitude() <= 180) {
            latitude = report.getLatitude();
            longitude = report.getLongitude();
        }

        String personnelName = report.getAssignedPersonnelName();
        if (personnelName == null || personnelName.trim().isEmpty()) {
            personnelName = report.getAssignedPersonnelId();
        }

        String reportId = report.getReportId();
        if (reportId == null || reportId.trim().isEmpty()) {
            reportId = report.getMongoId();
        }

        updateNavUI(itemCreate, iconMap, labelCreate);
        showScreen(AdminMapFragment.newInstance(
                reportId, latitude, longitude, personnelName));
    }

    /** Opens the live map and passes the personnel assignment focus target. */
    public void openMapForPersonnel(Personnel personnel) {
        if (personnel == null) {
            Toast.makeText(this, "Personnel record is missing.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!personnel.isCleaning()) {
            Toast.makeText(this, "This personnel member is not cleaning a report.", Toast.LENGTH_SHORT).show();
            return;
        }

        updateNavUI(itemCreate, iconMap, labelCreate);
        AdminMapFragment mapFragment = AdminMapFragment.newInstance(
                personnel.getAssignmentReportId(),
                personnel.getAssignmentLatitude(),
                personnel.getAssignmentLongitude(),
                personnel.getFullName());
        showScreen(mapFragment);
    }

    /**
     * Opens the live map on a collection's coordinates.
     *
     * <p>The vehicle side of the personnel screen. Taken as a coordinate pair
     * rather than a Vehicle so this stays a navigation concern and the map does
     * not need to know anything about the fleet; the report id is passed through
     * when there is one so the map can focus that collection.
     *
     * <p>A missing or 0,0 coordinate is refused rather than sent, because the map
     * would otherwise centre on the Gulf of Guinea.
     */
    public void openMapForLocation(Double latitude, Double longitude, String label) {
        if (latitude == null || longitude == null) {
            Toast.makeText(this, "This collection has no location yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (latitude == 0d && longitude == 0d) {
            Toast.makeText(this, "This collection has no location yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        updateNavUI(itemCreate, iconMap, labelCreate);
        showScreen(AdminMapFragment.newInstance(null, latitude, longitude, label));
    }

    /**
     * Opens the profile screen from the top bar avatar.
     *
     * <p>The live map is hosted in its own container that spans the whole
     * window, so replacing the inset container on its own would draw the
     * profile on top of the map instead of replacing it. The map is removed in
     * the same transaction, which leaves the profile as the only screen on
     * show.
     *
     * <p>The transaction goes on the back stack, so the back button on the
     * profile returns to the screen the administrator came from, the map
     * included.
     */
    private void openProfile() {
        updateNavUI(itemProfile, iconProfile, labelProfile);

        androidx.fragment.app.FragmentManager fragmentManager = getSupportFragmentManager();
        androidx.fragment.app.FragmentTransaction transaction = fragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, new ProfileFragment());

        Fragment mapFragment = fragmentManager.findFragmentById(R.id.mapContainer);
        if (mapFragment != null) {
            transaction.remove(mapFragment);
        }

        transaction.addToBackStack(null).commit();
    }

    /**
     * Puts the bottom navigation back in step with the screen underneath a
     * screen that was closed again.
     *
     * <p>Only a pop is acted on: a push already lit the tab of the screen it
     * opened.
     *
     * <p>The re-sync is posted because a pop and the transaction that follows
     * it are executed in the same pass, and the screen that ends up on top is
     * only known once both of them have run.
     */
    private void syncNavigationAfterPop() {
        int backStackCount = getSupportFragmentManager().getBackStackEntryCount();
        boolean popped = backStackCount < backStackCountAtLastChange;
        backStackCountAtLastChange = backStackCount;
        if (!popped) return;
        mainHandler.post(this::restoreNavigationSelection);
    }

    private void updateTopBarDate() {
        TextView topBarTitle = findViewById(R.id.topBarTitle);
        if (topBarTitle != null) {
            // Locale.US and Manila so the top bar agrees with every other date in the
            // app. This is the date the administrator is looking at, not the
            // device's date, so it must not follow the device's zone.
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMMM d yyyy", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
            String currentDate = sdf.format(new Date());
            topBarTitle.setText(currentDate);
        }
    }

    private void updateNavUI(LinearLayout activeItem, ImageView activeIcon, TextView activeLabel) {
        // Reset all to idle
        resetNavItem(itemHome, iconHome, labelHome);
        resetNavItem(itemInbox, iconInbox, labelInbox);
        resetNavItem(itemPersonnel, iconPersonnel, labelPersonnel);
        resetNavItem(itemProfile, iconProfile, labelProfile);
        resetNavItem(itemCreate, iconMap, labelCreate);

        if (activeItem != null) {
            // Set active
            if (activeItem == itemCreate) {
                activeItem.setBackgroundResource(R.drawable.circular_green_background);
            } else {
                activeItem.setBackgroundResource(R.drawable.active_pill_background);
            }
            activeIcon.setColorFilter(getResources().getColor(R.color.white, getTheme()));
            if (activeLabel != null && activeItem != itemCreate) activeLabel.setVisibility(View.VISIBLE);
        }
    }

    private void resetNavItem(LinearLayout item, ImageView icon, TextView label) {
        if (item == itemCreate) {
            item.setBackgroundResource(R.drawable.circular_outlined_background);
            icon.setColorFilter(getResources().getColor(R.color.green, getTheme()));
        } else {
            item.setBackground(null);
            icon.setColorFilter(getResources().getColor(R.color.green, getTheme()));
        }
        if (label != null) label.setVisibility(View.GONE);
    }

    private void updateBackground() {
        if (backgroundImage == null) return;
        
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            backgroundImage.setImageResource(R.drawable.darktheme);
        } else {
            backgroundImage.setImageResource(R.drawable.bg4);
        }
    }

    private void restoreNavigationSelection() {
        // The map lives in its own container, so both have to be checked or a
        // rotation would come back to the map tab with the wrong item lit up.
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.mapContainer);
        if (current == null) {
            current = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        }
        if (current instanceof ReportsFragment) {
            updateNavUI(itemInbox, iconInbox, labelInbox);
        } else if (current instanceof PersonnelFragment
                || current instanceof PersonnelDetailsFragment) {
            updateNavUI(itemPersonnel, iconPersonnel, labelPersonnel);
        } else if (current instanceof AdminMapFragment) {
            updateNavUI(itemCreate, iconMap, labelCreate);
        } else if (current instanceof SettingsMenuFragment
                || current instanceof ProfileFragment
                || current instanceof ReporterAccountsFragment
                || current instanceof ReporterDetailsFragment) {
            updateNavUI(itemProfile, iconProfile, labelProfile);
        } else if (current instanceof HomeFragment) {
            updateNavUI(itemHome, iconHome, labelHome);
        }
    }

    private void loadFragment(Fragment fragment, String title) {
        showScreen(fragment);

        // Date is now permanent in the top bar pill, so we don't update it here
    }

    /**
     * Shows one destination in the container that suits it.
     *
     * <p>The map goes in {@link R.id#mapContainer}, which spans the whole window
     * so the map can sit underneath the top bar and the bottom navigation.
     * Everything else goes in {@link R.id#fragmentContainer}, which stays inset
     * so scrolling lists clear both bars.
     *
     * <p>Both containers are cleared in the same transaction. Leaving the map in
     * its container while another screen is shown would keep its surface alive
     * behind the next screen.
     */
    private void showScreen(@NonNull Fragment fragment) {
        androidx.fragment.app.FragmentManager fragmentManager = getSupportFragmentManager();
        // Bottom-navigation destinations are roots. Any nested personnel,
        // reporter, or settings screen must not reappear on a later Back press.
        fragmentManager.popBackStack(
                null,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
        );

        boolean isMap = fragment instanceof AdminMapFragment;
        int targetId = isMap ? R.id.mapContainer : R.id.fragmentContainer;
        int otherId = isMap ? R.id.fragmentContainer : R.id.mapContainer;

        androidx.fragment.app.FragmentTransaction transaction =
                fragmentManager.beginTransaction().replace(targetId, fragment);
        Fragment other = fragmentManager.findFragmentById(otherId);
        if (other != null) transaction.remove(other);
        transaction.commit();
    }
}
