package com.example.ecowatchadmin1.data.repository;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.local.AppDatabase;
import com.example.ecowatchadmin1.data.local.NotificationDao;
import com.example.ecowatchadmin1.data.models.Notification;
import com.example.ecowatchadmin1.data.remote.AdminApiService;
import com.example.ecowatchadmin1.data.remote.ApiClient;
import com.example.ecowatchadmin1.data.remote.ApiService;
import com.example.ecowatchadmin1.data.remote.FcmTokenRequest;
import com.example.ecowatchadmin1.data.remote.UnreadCountResponse;
import com.example.ecowatchadmin1.ui.home.DashboardActivity;
import com.example.ecowatchadmin1.utils.SessionManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Backend-backed notification repository, cloned from the Resident app so the
 * Admin app renders, delivers, and reconciles notifications identically.
 * Room is a display cache only; the protected API remains authoritative for
 * ordering, read state, and trash.
 *
 * <p>The channel is admin-specific ({@code ecowatch_admin_notifications}) so
 * the two applications never fight over a single user-visible channel. Every
 * other behavior — deduplication, unread dot, system notification styling,
 * deep-link extras, and trash logic — is the same code path as the Resident app.
 */
public final class NotificationRepository {
    public static final String CHANNEL_ID = "ecowatch_admin_notifications";
    private static final String NOTIFICATION_GROUP = "ecowatch_report_updates";

    /**
     * Administrative notification types shown under the "From Admin" tab.
     * {@code NEW_REPORT} is the Phase 3A push type; the remaining entries are
     * declared up front so {@code PERSONNEL_ACCEPTED} and
     * {@code PERSONNEL_COMPLETED} need no further change here.
     */
    private static final List<String> ADMIN_TYPES = Collections.unmodifiableList(Arrays.asList(
            "ADMIN",
            "NEW_REPORT",
            "PERSONNEL_ASSIGNED",
            "PERSONNEL_UNASSIGNED",
            "CLEANING_STARTED",
            "ADMIN_RESPONSE_UPDATED",
            "REPORT_DENIED",
            "REPORT_COMPLETED",
            "REPORT_UPDATE",
            "PERSONNEL_ACCEPTED",
            "PERSONNEL_COMPLETED"
    ));

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static volatile NotificationRepository instance;

    private final Context application;
    private final NotificationDao notificationDao;
    private final ApiService apiService;
    private final AdminApiService adminApiService;
    private final SessionManager sessionManager;
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    /**
     * Emitted when a push creates a new unread row so a visible report list can
     * pull the server's canonical copy without changing screen state. It starts
     * with no value so a newly attached observer is not triggered spuriously.
     */
    private final MutableLiveData<Long> reportsInvalidated = new MutableLiveData<>();
    private final AtomicLong invalidationCounter = new AtomicLong();

    private NotificationRepository(Context application) {
        this.application = application.getApplicationContext();
        this.notificationDao = AppDatabase.getInstance(application).notificationDao();
        this.apiService = ApiClient.getApiService(application);
        this.adminApiService = ApiClient.getAdminApiService(application);
        this.sessionManager = new SessionManager(application);
    }

    public static NotificationRepository getInstance(@NonNull Context application) {
        if (instance == null) {
            synchronized (NotificationRepository.class) {
                if (instance == null) {
                    instance = new NotificationRepository(application);
                }
            }
        }
        return instance;
    }

    public LiveData<List<Notification>> getAllNotifications() {
        return notificationDao.getAllNotifications(currentAdminId());
    }

    public LiveData<List<Notification>> getAdminNotifications() {
        return notificationDao.getAdminNotifications(currentAdminId(), ADMIN_TYPES);
    }

    public LiveData<List<Notification>> getTrashNotifications() {
        return notificationDao.getTrashNotifications(currentAdminId());
    }

    public LiveData<Integer> getUnreadCount() {
        String adminId = currentAdminId();
        return adminId == null || adminId.isEmpty()
                ? new MutableLiveData<>(0)
                : notificationDao.observeUnreadCount(adminId);
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Long> getReportsInvalidated() {
        return reportsInvalidated;
    }

    public void fetchNotifications() {
        fetchNotifications(null);
    }

    public void fetchNotifications(@Nullable RefreshCallback callback) {
        final String adminId = sessionManager.fetchResidentId();
        final String jwt = sessionManager.fetchAuthToken();
        if (adminId == null || jwt == null || jwt.trim().isEmpty()) {
            if (callback != null) callback.onError("Log in to load notifications.");
            return;
        }

        loading.setValue(true);
        error.setValue(null);
        apiService.getMyNotifications().enqueue(new Callback<List<Notification>>() {
            @Override
            public void onResponse(Call<List<Notification>> call, Response<List<Notification>> response) {
                if (!isCurrentSession(adminId, jwt)) return;
                if (!response.isSuccessful() || response.body() == null) {
                    finishWithError(readError(response, "Unable to load notifications."), callback);
                    return;
                }
                persistRemote(adminId, response.body(), false, true, () ->
                        fetchTrash(adminId, jwt, callback));
            }

            @Override
            public void onFailure(Call<List<Notification>> call, Throwable throwable) {
                if (isCurrentSession(adminId, jwt)) {
                    finishWithError(throwable.getMessage(), callback);
                }
            }
        });
    }

    private void fetchTrash(String adminId, String jwt, RefreshCallback callback) {
        apiService.getMyTrashNotifications().enqueue(new Callback<List<Notification>>() {
            @Override
            public void onResponse(Call<List<Notification>> call, Response<List<Notification>> response) {
                if (!isCurrentSession(adminId, jwt)) return;
                if (!response.isSuccessful() || response.body() == null) {
                    finishWithError(readError(response, "Unable to load notification trash."), callback);
                    return;
                }
                persistRemote(adminId, response.body(), true, false, null);
                refreshUnreadFromServer(adminId, jwt, callback);
            }

            @Override
            public void onFailure(Call<List<Notification>> call, Throwable throwable) {
                if (isCurrentSession(adminId, jwt)) {
                    finishWithError(throwable.getMessage(), callback);
                }
            }
        });
    }

    private void persistRemote(
            String adminId,
            List<Notification> remote,
            boolean trashed,
            boolean displayNew,
            Runnable completion
    ) {
        EXECUTOR.execute(() -> {
            List<Notification> accepted = new ArrayList<>();
            for (Notification incoming : remote) {
                if (incoming == null || isBlank(incoming.getMongoId())) continue;
                if (!adminId.equalsIgnoreCase(incoming.getResidentId())) continue;
                incoming.setResidentId(adminId);
                incoming.setDeleted(trashed);

                Notification existing = notificationDao.getByMongoId(
                        adminId,
                        incoming.getMongoId()
                );
                boolean isNew = existing == null;
                if (existing != null) incoming.setId(existing.getId());
                notificationDao.insertNotification(incoming);
                accepted.add(incoming);

                if (isNew && displayNew && !incoming.isRead() && !incoming.isDeleted()) {
                    showSystemNotification(incoming);
                }
            }
            if (completion != null) completion.run();
        });
    }

    private void refreshUnreadFromServer(String adminId, String jwt, RefreshCallback callback) {
        apiService.getUnreadCount().enqueue(new Callback<UnreadCountResponse>() {
            @Override
            public void onResponse(Call<UnreadCountResponse> call, Response<UnreadCountResponse> response) {
                if (!isCurrentSession(adminId, jwt)) return;
                if (response.isSuccessful() && response.body() != null) {
                    loading.postValue(false);
                    if (callback != null) callback.onComplete();
                } else {
                    finishWithError(readError(response, "Unable to refresh unread count."), callback);
                }
            }

            @Override
            public void onFailure(Call<UnreadCountResponse> call, Throwable throwable) {
                if (isCurrentSession(adminId, jwt)) {
                    finishWithError(throwable.getMessage(), callback);
                }
            }
        });
    }

    private void refreshUnreadFromServer() {
        String adminId = sessionManager.fetchResidentId();
        String jwt = sessionManager.fetchAuthToken();
        if (adminId != null && jwt != null) {
            refreshUnreadFromServer(adminId, jwt, null);
        }
    }

    public void markAsRead(Notification notification) {
        if (notification == null || isBlank(notification.getMongoId())) return;
        String adminId = sessionManager.fetchResidentId();
        if (adminId == null) return;
        String notificationId = notification.getMongoId();
        notification.setRead(true);
        EXECUTOR.execute(() -> notificationDao.markAsRead(adminId, notificationId));
        apiService.markNotificationAsRead(notificationId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!response.isSuccessful() && isCurrentAdmin(adminId)) {
                    notification.setRead(false);
                    EXECUTOR.execute(() -> notificationDao.markAsUnread(adminId, notificationId));
                    fetchNotifications();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable throwable) {
                if (isCurrentAdmin(adminId)) fetchNotifications();
            }
        });
    }

    public void markAsReadByServerId(@Nullable String notificationId) {
        if (isBlank(notificationId)) return;
        String adminId = sessionManager.fetchResidentId();
        if (adminId == null) return;
        EXECUTOR.execute(() -> notificationDao.markAsRead(adminId, notificationId));
        apiService.markNotificationAsRead(notificationId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!response.isSuccessful() && isCurrentAdmin(adminId)) fetchNotifications();
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable throwable) {
                if (isCurrentAdmin(adminId)) fetchNotifications();
            }
        });
    }

    public void markAllAsRead() {
        String adminId = sessionManager.fetchResidentId();
        if (adminId == null) return;
        EXECUTOR.execute(() -> notificationDao.markAllAsRead(adminId));
        apiService.markAllNotificationsAsRead().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!response.isSuccessful() && isCurrentAdmin(adminId)) fetchNotifications();
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable throwable) {
                if (isCurrentAdmin(adminId)) fetchNotifications();
            }
        });
    }

    public void softDelete(Notification notification) {
        mutateRemote(notification, true, () -> {
            if (isCurrentAdmin(notification.getResidentId())) {
                notificationDao.softDelete(
                        notification.getResidentId(),
                        notification.getId(),
                        nowIso()
                );
            }
        });
    }

    public void restore(Notification notification) {
        mutateRemote(notification, false, () -> {
            if (isCurrentAdmin(notification.getResidentId())) {
                notificationDao.restore(notification.getResidentId(), notification.getId());
            }
        });
    }

    public void permanentDelete(Notification notification) {
        if (notification == null || isBlank(notification.getMongoId())) return;
        String adminId = notification.getResidentId();
        apiService.deleteNotification(notification.getMongoId()).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && isCurrentAdmin(adminId)) {
                    EXECUTOR.execute(() ->
                            notificationDao.permanentDelete(adminId, notification.getId()));
                } else if (isCurrentAdmin(adminId)) {
                    fetchNotifications();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable throwable) {
                if (isCurrentAdmin(adminId)) fetchNotifications();
            }
        });
    }

    private void mutateRemote(Notification notification, boolean trash, Runnable onSuccess) {
        if (notification == null || isBlank(notification.getMongoId())) return;
        Call<Map<String, Object>> call = trash
                ? apiService.moveNotificationToTrash(notification.getMongoId())
                : apiService.restoreNotification(notification.getMongoId());
        String adminId = notification.getResidentId();
        call.enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && isCurrentAdmin(adminId)) {
                    EXECUTOR.execute(onSuccess);
                } else if (isCurrentAdmin(adminId)) {
                    fetchNotifications();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable throwable) {
                if (isCurrentAdmin(adminId)) fetchNotifications();
            }
        });
    }

    /** Handles a data-only FCM message with the same deduplication path as REST. */
    public void upsertPushNotification(Map<String, String> data) {
        String adminId = sessionManager.fetchResidentId();
        String jwt = sessionManager.fetchAuthToken();
        if (adminId == null || jwt == null || data == null) return;
        Notification notification = fromPushData(adminId, data);
        if (notification == null) return;
        persistRemote(adminId, Collections.singletonList(notification), false, true, () -> {
            refreshUnreadFromServer();
            signalReportsInvalidated();
        });
    }

    /**
     * Registers this device with {@code POST /api/admin/fcm-token}. Blocking by
     * design: WorkManager needs the definitive result to decide retry.
     */
    public boolean updateFcmTokenBlocking(String token) throws Exception {
        String adminId = sessionManager.fetchResidentId();
        String jwt = sessionManager.fetchAuthToken();
        if (isBlank(adminId) || isBlank(jwt) || isBlank(token)) return false;
        Response<Map<String, Object>> response = adminApiService
                .registerAdminFcmToken(new FcmTokenRequest(token))
                .execute();
        return response.isSuccessful();
    }

    public void clearFcmTokenBestEffort() {
        String jwt = sessionManager.fetchAuthToken();
        if (isBlank(jwt)) return;
        adminApiService.clearAdminFcmToken().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
            }

            @Override
            public void onFailure(Call<Void> call, Throwable throwable) {
                Log.w("NotificationRepo", "Unable to unregister FCM token", throwable);
            }
        });
    }

    public void clearLocalDataForAdmin(String adminId) {
        if (!isBlank(adminId)) {
            EXECUTOR.execute(() -> notificationDao.deleteAllForResident(adminId));
        }
    }

    @Nullable
    public static Notification fromPushData(String adminId, Map<String, String> data) {
        String serverId = firstNonBlank(
                data.get("notificationId"),
                data.get("notification_id"),
                data.get("_id")
        );
        if (isBlank(serverId) || isBlank(adminId)) return null;
        Notification notification = new Notification();
        notification.setMongoId(serverId);
        notification.setResidentId(adminId);
        notification.setReportId(blankToNull(firstNonBlank(
                data.get("reportId"),
                data.get("report_id")
        )));
        notification.setTitle(blankToNull(data.get("title")));
        notification.setMessage(blankToNull(data.get("message")));
        notification.setType(blankToNull(data.get("type")));
        // created_at is server-owned; the client never invents a timestamp.
        notification.setCreatedAt(blankToNull(firstNonBlank(
                data.get("createdAt"),
                data.get("created_at")
        )));
        notification.setRead(false);
        notification.setDeleted(false);
        return notification;
    }

    public static int stableNotificationId(@Nullable String serverId) {
        if (serverId == null) return 1;
        int value = serverId.hashCode() & Integer.MAX_VALUE;
        return value == 0 ? 1 : value;
    }

    public static boolean isAdminNotificationType(@Nullable String type) {
        return type != null && ADMIN_TYPES.contains(type.trim().toUpperCase());
    }

    public static List<String> getAdminTypes() {
        return ADMIN_TYPES;
    }

    @SuppressLint("MissingPermission")
    private void showSystemNotification(Notification notification) {
        if (ContextCompat.checkSelfPermission(
                application,
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(application, DashboardActivity.class)
                .setAction(DashboardActivity.ACTION_OPEN_NOTIFICATION)
                .putExtra(DashboardActivity.EXTRA_NOTIFICATION_ID, notification.getMongoId())
                .putExtra(DashboardActivity.EXTRA_REPORT_ID, notification.getReportId())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                application,
                stableNotificationId(notification.getMongoId()),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(application, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(notification.getTitle() == null
                        ? "EcoWatch" : notification.getTitle())
                .setContentText(notification.getMessage() == null
                        ? "Your report was updated." : notification.getMessage())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setGroup(NOTIFICATION_GROUP);
        try {
            NotificationManagerCompat.from(application).notify(
                    stableNotificationId(notification.getMongoId()),
                    builder.build()
            );
        } catch (SecurityException ignored) {
            // Android 13+ permission can be revoked between check and notify.
        }
    }

    private void signalReportsInvalidated() {
        reportsInvalidated.postValue(invalidationCounter.incrementAndGet());
    }

    private boolean isCurrentSession(String adminId, String jwt) {
        return isCurrentAdmin(adminId) && jwt.equals(sessionManager.fetchAuthToken());
    }

    private boolean isCurrentAdmin(String adminId) {
        return adminId != null && adminId.equalsIgnoreCase(sessionManager.fetchResidentId());
    }

    private String currentAdminId() {
        return sessionManager.fetchResidentId() == null ? "" : sessionManager.fetchResidentId();
    }

    private void finishWithError(String message, RefreshCallback callback) {
        String safeMessage = isBlank(message) ? "Notification sync failed." : message;
        error.postValue(safeMessage);
        loading.postValue(false);
        if (callback != null) callback.onError(safeMessage);
    }

    private String readError(Response<?> response, String fallback) {
        try (ResponseBody body = response.errorBody()) {
            if (body != null) {
                String message = new com.google.gson.JsonParser().parse(body.string())
                        .getAsJsonObject()
                        .get("message")
                        .getAsString();
                if (!isBlank(message)) return message;
            }
        } catch (Exception ignored) {
        }
        return fallback + " (" + response.code() + ")";
    }

    private static String nowIso() {
        SimpleDateFormat formatter = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                Locale.US
        );
        formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return formatter.format(new Date());
    }

    @Nullable
    private static String firstNonBlank(@Nullable String... values) {
        for (String value : values) {
            if (!isBlank(value)) return value;
        }
        return null;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    public interface RefreshCallback {
        void onComplete();
        void onError(String error);
    }
}
