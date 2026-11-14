package com.example.ecowatchadmin1.data.repository;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.ecowatchadmin1.data.local.AppDatabase;
import com.example.ecowatchadmin1.data.local.NotificationSettingDao;
import com.example.ecowatchadmin1.data.models.NotificationSetting;
import com.example.ecowatchadmin1.data.remote.AdminApiService;
import com.example.ecowatchadmin1.data.remote.AdminNotificationPreferences;
import com.example.ecowatchadmin1.data.remote.AdminNotificationPreferencesRequest;
import com.example.ecowatchadmin1.data.remote.AdminNotificationPreferencesResponse;
import com.example.ecowatchadmin1.data.remote.ApiClient;
import com.example.ecowatchadmin1.utils.SessionManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Keeps administrator notification preferences in step with the server.
 *
 * <p>The local Room row stays the render cache so the settings screen is instant
 * and works offline, but the server owns the record. Until this existed the
 * toggles were device-local, which meant they were lost on reinstall, disagreed
 * between devices, and were invisible to the backend so it could never suppress
 * a push the administrator had switched off.
 *
 * <p>Writes are fire-and-forget: a failed sync leaves the local value in place and
 * is retried on the next launch, because losing a preference is worse than
 * briefly blocking a toggle.
 */
public class AdminNotificationPreferenceRepository {

    private static final String TAG = "AdminNotifPref";

    private final AdminApiService apiService;
    private final NotificationSettingDao dao;
    private final SessionManager sessionManager;
    private final ExecutorService executor;

    public AdminNotificationPreferenceRepository(@NonNull Application application) {
        this.apiService = ApiClient.getAdminApiService(application);
        this.dao = AppDatabase.getInstance(application).notificationSettingDao();
        this.sessionManager = new SessionManager(application);
        this.executor = Executors.newSingleThreadExecutor();
    }

    private String adminId() {
        String adminId = sessionManager.fetchResidentId();
        if (!sessionManager.isLoggedIn() || !sessionManager.isAdmin()) return null;
        if (adminId == null || adminId.trim().isEmpty()) return null;
        return adminId;
    }

    /**
     * Pulls the server's record over the local row.
     *
     * <p>Called when the settings screen opens. The server wins, so a change made
     * on another device or before a reinstall is reflected rather than silently
     * reverted by stale local state.
     */
    public void syncFromServer(@Nullable Runnable onComplete) {
        final String adminId = adminId();
        if (adminId == null) {
            notifyComplete(onComplete);
            return;
        }

        apiService.getAdminNotificationPreferences()
                .enqueue(new Callback<AdminNotificationPreferencesResponse>() {
                    @Override
                    public void onResponse(
                            Call<AdminNotificationPreferencesResponse> call,
                            Response<AdminNotificationPreferencesResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            applyRemote(adminId, response.body().getPreferences());
                        } else {
                            Log.w(TAG, "Could not read notification preferences (code "
                                    + response.code() + "); keeping local values.");
                        }
                        notifyComplete(onComplete);
                    }

                    @Override
                    public void onFailure(Call<AdminNotificationPreferencesResponse> call,
                                          Throwable t) {
                        Log.w(TAG, "Notification preference read failed: " + t.getMessage());
                        notifyComplete(onComplete);
                    }
                });
    }

    private void applyRemote(final String adminId, final AdminNotificationPreferences remote) {
        executor.execute(() -> {
            NotificationSetting settings = dao.getSettingsByResidentId(adminId);
            if (settings == null) settings = new NotificationSetting(adminId);

            settings.setMasterEnabled(remote.isMasterEnabled());
            settings.setReportStatusUpdates(remote.isReportStatusUpdates());
            settings.setReportCompleted(remote.isReportCompleted());
            settings.setReportDenied(remote.isReportDenied());
            settings.setSecurityAlerts(remote.isSecurityAlerts());
            settings.setHighPriorityReports(remote.isHighPriorityReports());
            settings.setPersonnelAccepted(remote.isPersonnelAccepted());
            settings.setPersonnelCleaning(remote.isPersonnelCleaning());
            settings.setPhotoUpdates(remote.isPhotoUpdates());
            settings.setAwaitingApproval(remote.isAwaitingApproval());
            settings.setArchivedReports(remote.isArchivedReports());
            settings.setMaintenanceAlerts(remote.isMaintenanceAlerts());
            settings.setSoundEnabled(remote.isSoundEnabled());
            settings.setVibrationEnabled(remote.isVibrationEnabled());
            settings.setSoundUri(remote.getSoundUri() == null ? "" : remote.getSoundUri());
            dao.saveSettings(settings);
        });
    }

    /**
     * Pushes one named toggle to the server.
     *
     * <p>Takes the field name because the settings screen binds categories
     * generically and has no compile-time list to dispatch over.
     */
    public void pushField(@NonNull String field, boolean enabled) {
        if (adminId() == null) return;

        AdminNotificationPreferencesRequest request;
        try {
            request = AdminNotificationPreferencesRequest.forField(field, enabled);
        } catch (IllegalArgumentException unknown) {
            Log.w(TAG, "Not pushing unknown preference field: " + unknown.getMessage());
            return;
        }
        push(request);
    }

    /** Stores the chosen ringtone, or clears it when {@code uri} is blank. */
    public void pushSoundUri(@Nullable String uri) {
        if (adminId() == null) return;
        push(AdminNotificationPreferencesRequest.forSoundUri(uri));
    }

    /**
     * Sends one field. Failures are logged only: the local row is already saved
     * and the next {@link #syncFromServer} reconciles, so a dropped request must
     * not surface as an error the administrator has to act on.
     */
    private void push(AdminNotificationPreferencesRequest request) {
        apiService.updateAdminNotificationPreferences(request)
                .enqueue(new Callback<AdminNotificationPreferencesResponse>() {
                    @Override
                    public void onResponse(
                            Call<AdminNotificationPreferencesResponse> call,
                            Response<AdminNotificationPreferencesResponse> response) {
                        if (!response.isSuccessful()) {
                            Log.w(TAG, "Notification preference write rejected (code "
                                    + response.code() + "); retried on next launch.");
                        }
                    }

                    @Override
                    public void onFailure(Call<AdminNotificationPreferencesResponse> call,
                                          Throwable t) {
                        Log.w(TAG, "Notification preference write failed: " + t.getMessage());
                    }
                });
    }

    private void notifyComplete(@Nullable final Runnable onComplete) {
        if (onComplete == null) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(onComplete);
    }
}
