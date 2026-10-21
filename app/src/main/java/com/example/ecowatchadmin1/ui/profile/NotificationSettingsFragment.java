package com.example.ecowatchadmin1.ui.profile;

import android.app.Activity;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
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
import androidx.fragment.app.Fragment;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.local.AppDatabase;
import com.example.ecowatchadmin1.data.local.NotificationSettingDao;
import com.example.ecowatchadmin1.data.models.NotificationSetting;
import com.example.ecowatchadmin1.data.repository.AdminNotificationPreferenceRepository;
import com.example.ecowatchadmin1.utils.SessionManager;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Admin-specific notification settings.
 *
 * Every category row is registered with the accessors for its own persisted
 * flag, so the row, its switch, and its stored value stay wired together in one
 * place. The master switch keeps its original behavior of gating the whole
 * category list.
 */
public class NotificationSettingsFragment extends Fragment {

    private static final String TAG = "NotificationSettings";

    private SessionManager sessionManager;
    private NotificationSettingDao dao;
    private AdminNotificationPreferenceRepository preferenceRepository;
    private ExecutorService executor;
    private NotificationSetting settings;

    private MaterialSwitch switchAllowNotifications;
    private View settingsContentLayout;

    /** A category row bound to its persisted flag. */
    private static final class Category {
        final View row;
        final MaterialSwitch toggle;
        final BooleanSupplier getter;
        final Consumer<Boolean> setter;
        /**
         * Server-side field name, so a change can be pushed on its own.
         *
         * <p>Sent one field at a time rather than as the whole set: a client that
         * only knows about one category would otherwise overwrite the others with
         * whatever it happened to hold locally.
         */
        final String field;

        Category(View row, MaterialSwitch toggle, BooleanSupplier getter,
                 Consumer<Boolean> setter, String field) {
            this.row = row;
            this.toggle = toggle;
            this.getter = getter;
            this.setter = setter;
            this.field = field;
        }
    }

    private final List<Category> categories = new ArrayList<>();

    private final ActivityResultLauncher<Intent> soundPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    if (uri != null && settings != null) {
                        settings.setSoundUri(uri.toString());
                        saveSettings();
                        // Synced so the choice survives a reinstall and matches
                        // on the administrator's other devices.
                        preferenceRepository.pushSoundUri(uri.toString());
                        Toast.makeText(getContext(), "Notification sound updated", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notification_settings, container, false);

        sessionManager = new SessionManager(requireContext());
        dao = AppDatabase.getInstance(requireContext()).notificationSettingDao();
        preferenceRepository =
                new AdminNotificationPreferenceRepository(requireActivity().getApplication());
        executor = Executors.newSingleThreadExecutor();

        initViews(view);
        loadSettings();

        return view;
    }

    private void initViews(View view) {
        view.findViewById(R.id.btnBack).setOnClickListener(v -> getParentFragmentManager().popBackStack());

        View rowAllowNotifications = view.findViewById(R.id.itemAllowNotifications);
        switchAllowNotifications = rowAllowNotifications.findViewById(R.id.rowSwitch);
        setupRowUI(
                rowAllowNotifications,
                R.drawable.ic_notification,
                "Allow Notifications",
                "Allow EcoWatch to send cleanup alerts, assignments, and report activity."
        );

        settingsContentLayout = view.findViewById(R.id.settingsContentLayout);

        // New Reports
        addCategory(view, R.id.itemNewReports, R.drawable.ic_inbox,
                "New Resident Reports",
                "Get notified the moment a resident submits a report.",
                () -> settings.isNewReports(), v -> settings.setNewReports(v),
                "reportStatusUpdates");
        addCategory(view, R.id.itemEmergencyReports, R.drawable.ic_notification,
                "Emergency Reports",
                "Reports flagged as an immediate environmental hazard.",
                () -> settings.isEmergencyReports(), v -> settings.setEmergencyReports(v),
                "securityAlerts");
        addCategory(view, R.id.itemHighPriorityReports, R.drawable.sort,
                "High-priority Reports",
                "Reports submitted with a high priority level.",
                () -> settings.isHighPriorityReports(), v -> settings.setHighPriorityReports(v),
                "highPriorityReports");

        // Assignment & Operations
        addCategory(view, R.id.itemPersonnelAccepted, R.drawable.ic_person_default,
                "Personnel Accepted Assignment",
                "Notify me when assigned personnel accept a cleanup task.",
                () -> settings.isPersonnelAccepted(), v -> settings.setPersonnelAccepted(v),
                "personnelAccepted");
        addCategory(view, R.id.itemPersonnelCleaning, R.drawable.ic_reset,
                "Personnel Started Cleaning",
                "Notify me when on-site cleaning operations begin.",
                () -> settings.isPersonnelCleaning(), v -> settings.setPersonnelCleaning(v),
                "personnelCleaning");
        addCategory(view, R.id.itemCleaningCompleted, R.drawable.checkbox,
                "Cleaning Completed",
                "Notify me when a cleanup has been fully completed.",
                () -> settings.isCleaningCompleted(), v -> settings.setCleaningCompleted(v),
                "reportCompleted");
        addCategory(view, R.id.itemPhotoUpdates, R.drawable.ic_gallery,
                "Before/After Photos Submitted",
                "Notify me when cleanup before and after photos are uploaded.",
                () -> settings.isPhotoUpdates(), v -> settings.setPhotoUpdates(v),
                "photoUpdates");

        // Approval Workflow
        addCategory(view, R.id.itemAwaitingApproval, R.drawable.ic_edit_pen,
                "Awaiting Approval",
                "Notify me when a completed cleanup needs my sign-off.",
                () -> settings.isAwaitingApproval(), v -> settings.setAwaitingApproval(v),
                "awaitingApproval");
        addCategory(view, R.id.itemReportDenied, R.drawable.trashic,
                "Report Denied",
                "Notify me when a report has been denied.",
                () -> settings.isReportDenied(), v -> settings.setReportDenied(v),
                "reportDenied");
        addCategory(view, R.id.itemArchivedReports, R.drawable.ic_inbox,
                "Archived Reports",
                "Notify me when reports are moved to the archive.",
                () -> settings.isArchivedReports(), v -> settings.setArchivedReports(v),
                "archivedReports");

        // System
        addCategory(view, R.id.itemAnnouncements, R.drawable.logo1,
                "Announcements",
                "Receive EcoWatch environmental announcements and important updates.",
                () -> settings.isAnnouncements(), v -> settings.setAnnouncements(v),
                "cenroAnnouncements");
        addCategory(view, R.id.itemMaintenanceAlerts, android.R.drawable.ic_menu_info_details,
                "Maintenance Alerts",
                "Receive EcoWatch system maintenance and service notices.",
                () -> settings.isMaintenanceAlerts(), v -> settings.setMaintenanceAlerts(v),
                "maintenanceAlerts");
        addCategory(view, R.id.itemSound, android.R.drawable.ic_lock_silent_mode_off,
                "Sound",
                "Play a sound for new notifications.",
                () -> settings.isSoundEnabled(), v -> settings.setSoundEnabled(v),
                "soundEnabled");
        addCategory(view, R.id.itemVibration, android.R.drawable.ic_lock_idle_alarm,
                "Vibration",
                "Vibrate device for incoming alerts.",
                () -> settings.isVibrationEnabled(), v -> settings.setVibrationEnabled(v),
                "vibrationEnabled");

        switchAllowNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (settings != null && settings.isMasterEnabled()) {
                    settingsContentLayout.setVisibility(View.VISIBLE);
                    return;
                }

                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Allow Push Notifications")
                        .setMessage("Do you want to allow EcoWatch to send high priority report, assignment, and approval alerts?")
                        .setPositiveButton("Allow", (dialog, which) -> {
                            settingsContentLayout.setVisibility(View.VISIBLE);
                            if (settings != null) {
                                settings.setMasterEnabled(true);
                                saveSettings();
                            }
                            preferenceRepository.pushField("masterEnabled", true);
                        })
                        .setNegativeButton("Deny", (dialog, which) -> {
                            switchAllowNotifications.setChecked(false);
                            settingsContentLayout.setVisibility(View.GONE);
                            if (settings != null) {
                                settings.setMasterEnabled(false);
                                saveSettings();
                            }
                            preferenceRepository.pushField("masterEnabled", false);
                        })
                        .setCancelable(false)
                        .show();
            } else {
                settingsContentLayout.setVisibility(View.GONE);
                if (settings != null) {
                    settings.setMasterEnabled(false);
                    saveSettings();
                }
                preferenceRepository.pushField("masterEnabled", false);
            }
        });

        rowAllowNotifications.setOnClickListener(v -> switchAllowNotifications.toggle());

        // Each toggle saves locally for instant feedback and pushes just that one
        // field, so a stale local value can never overwrite another category.
        for (Category category : categories) {
            category.row.setOnClickListener(v -> category.toggle.toggle());
            category.toggle.setOnClickListener(v -> {
                updateSettingsFromUI();
                saveSettings();
                preferenceRepository.pushField(category.field, category.getter.getAsBoolean());
            });
        }

        view.findViewById(R.id.btnChangeSound).setOnClickListener(v -> pickSound());
    }

    /**
     * Binds a row to its persisted flag in one call.
     *
     * <p>{@code field} is the server-side name the change is pushed under. It is
     * separate from the local accessor names because the app aliases several
     * legacy fields onto shared columns.
     */
    private void addCategory(View view, int rowId, int iconRes, String title, String description,
                             BooleanSupplier getter, Consumer<Boolean> setter, String field) {
        View row = view.findViewById(rowId);
        MaterialSwitch toggle = row.findViewById(R.id.rowSwitch);
        setupRowUI(row, iconRes, title, description);
        categories.add(new Category(row, toggle, getter, setter, field));
    }

    private void setupRowUI(View row, int iconRes, String title, String description) {
        ImageView icon = row.findViewById(R.id.rowIcon);
        TextView titleTv = row.findViewById(R.id.rowTitle);
        TextView descTv = row.findViewById(R.id.rowDescription);

        icon.setImageResource(iconRes);
        titleTv.setText(title);
        if (description != null) {
            descTv.setText(description);
            descTv.setVisibility(View.VISIBLE);
        }
    }

    private void loadSettings() {
        String residentId = sessionManager.fetchResidentId();
        if (residentId == null) return;

        executor.execute(() -> {
            settings = dao.getSettingsByResidentId(residentId);
            if (settings == null) {
                settings = new NotificationSetting(residentId);
                dao.saveSettings(settings);
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(this::updateUI);
            }
        });

        // Pull the server's copy over the local one. The screen renders from the
        // local row immediately, then re-renders once the server answers, so a
        // change made elsewhere or before a reinstall wins without the screen
        // ever appearing to hang on the network.
        preferenceRepository.syncFromServer(() -> {
            if (getView() == null) return;
            executor.execute(() -> {
                NotificationSetting refreshed = dao.getSettingsByResidentId(residentId);
                if (refreshed == null || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (getView() == null) return;
                    settings = refreshed;
                    updateUI();
                });
            });
        });
    }

    private void updateUI() {
        if (settings == null) return;
        boolean allowed = settings.isMasterEnabled();
        switchAllowNotifications.setChecked(allowed);
        settingsContentLayout.setVisibility(allowed ? View.VISIBLE : View.GONE);

        for (Category category : categories) {
            category.toggle.setChecked(category.getter.getAsBoolean());
        }
    }

    private void updateSettingsFromUI() {
        if (settings == null) return;
        settings.setMasterEnabled(switchAllowNotifications.isChecked());
        for (Category category : categories) {
            category.setter.accept(category.toggle.isChecked());
        }
    }

    private void saveSettings() {
        if (settings == null) return;
        executor.execute(() -> dao.saveSettings(settings));
    }

    private void pickSound() {
        if (settings == null) return;
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Notification Sound");
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                (settings.getSoundUri().isEmpty() ? null : Uri.parse(settings.getSoundUri())));
        soundPickerLauncher.launch(intent);
    }
}
