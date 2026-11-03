package com.example.ecowatchadmin1.data.repository;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.ecowatchadmin1.data.models.Announcement;
import com.example.ecowatchadmin1.data.remote.AdminApiService;
import com.example.ecowatchadmin1.data.remote.AnnouncementCreateRequest;
import com.example.ecowatchadmin1.data.remote.ApiClient;
import com.example.ecowatchadmin1.utils.SessionManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Announcement repository. Publishing is backed by the real EcoWatch backend;
 * nothing is stored locally or reported as sent when the request fails.
 */
public class AnnouncementRepository {

    /** Single-method error target both public callbacks satisfy. */
    private interface ErrorSink {
        void onError(String error);
    }

    /** The only audience the backend currently accepts. */
    public static final String AUDIENCE_ALL_RESIDENTS = "All Residents";
    public static final String PRIORITY_NORMAL = "Normal";
    public static final String PRIORITY_IMPORTANT = "Important";

    /**
     * Lifetime windows the composer offers, in days, paired with the option
     * ids they are shown under. 0 means the announcement does not expire.
     *
     * <p>Kept in step with the backend's allowlist; a window the backend would
     * refuse is never offered.
     */
    public static final int EXPIRY_1_DAY = 1;
    public static final int EXPIRY_3_DAYS = 3;
    public static final int EXPIRY_7_DAYS = 7;
    public static final int EXPIRY_14_DAYS = 14;
    public static final int EXPIRY_30_DAYS = 30;
    public static final int EXPIRY_NEVER = 0;

    /** Window applied when the composer has not recorded a choice. */
    public static final int EXPIRY_DEFAULT_DAYS = EXPIRY_7_DAYS;

    /**
     * Announcements fetched per page.
     *
     * <p>The backend caps a page at 100. The list screen appends further pages
     * until one comes back short, so a long history is reachable rather than
     * silently truncated.
     */
    public static final int DEFAULT_PAGE_SIZE = 50;

    private final AdminApiService apiService;
    private final SessionManager sessionManager;

    public AnnouncementRepository(@NonNull Application application) {
        apiService = ApiClient.getAdminApiService(application);
        sessionManager = new SessionManager(application);
    }

    public interface AnnouncementCallback extends ErrorSink {
        void onSuccess(Announcement announcement);
    }

    public interface AnnouncementListCallback extends ErrorSink {
        void onSuccess(List<Announcement> announcements);
    }

    /** Outcome of a permanent delete, with no announcement to hand back. */
    public interface AnnouncementDeleteCallback extends ErrorSink {
        void onSuccess();
    }

    public void publish(String title, String message, String audience, String priority,
                        int expiresInDays,
                        @NonNull AnnouncementCallback callback) {
        String normalizedTitle = title == null ? "" : title.trim();
        String normalizedMessage = message == null ? "" : message.trim();

        if (normalizedTitle.isEmpty()) {
            callback.onError("Announcement title is required.");
            return;
        }
        if (normalizedMessage.isEmpty()) {
            callback.onError("Announcement message is required.");
            return;
        }
        if (!requireAdmin(callback, "publish an announcement")) return;

        AnnouncementCreateRequest request = new AnnouncementCreateRequest(
                normalizedTitle,
                normalizedMessage,
                isBlank(audience) ? AUDIENCE_ALL_RESIDENTS : audience.trim(),
                isBlank(priority) ? PRIORITY_NORMAL : priority.trim(),
                expiresInDays);

        apiService.createAnnouncement(request).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "announcement")) return;

                Announcement announcement = parseAnnouncement(response.body());
                if (announcement == null) {
                    // The request may well have been accepted, so do not claim it
                    // failed; the confirmation screen simply cannot show the id.
                    announcement = new Announcement();
                    announcement.setTitle(normalizedTitle);
                    announcement.setMessage(normalizedMessage);
                    announcement.setAudience(request.getAudience());
                    announcement.setPriority(request.getPriority());
                }
                callback.onSuccess(announcement);
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    /**
     * One page of published announcements, oldest offset first.
     *
     * <p>Paged rather than a single capped call so a long history is reachable
     * instead of silently truncating at the first page.
     */
    public void getAnnouncements(int limit, int offset,
                                 @NonNull AnnouncementListCallback callback) {
        if (!requireAdmin(callback, "view announcements")) return;
        apiService.getAnnouncements(limit, offset).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "announcements")) return;
                callback.onSuccess(parseList(response.body()));
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    public void getAnnouncements(@NonNull AnnouncementListCallback callback) {
        getAnnouncements(AnnouncementRepository.DEFAULT_PAGE_SIZE, 0, callback);
    }

    /**
     * Permanently removes a published announcement.
     *
     * <p>The only way to take down something published with the "never expires"
     * window, which the automatic sweep will never collect. Announcements have no
     * trash table, so this cannot be undone.
     */
    public void deleteAnnouncement(@NonNull String announcementId,
                                   @NonNull AnnouncementDeleteCallback callback) {
        if (isBlank(announcementId)) {
            callback.onError("An announcement ID is required.");
            return;
        }
        if (!requireAdmin(callback, "delete an announcement")) return;

        apiService.deleteAnnouncement(announcementId.trim())
                .enqueue(new Callback<JsonElement>() {
                    @Override
                    public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                        if (isSuccessful(response, callback, "announcement")) {
                            callback.onSuccess();
                        }
                    }

                    @Override
                    public void onFailure(Call<JsonElement> call, Throwable throwable) {
                        callback.onError(networkMessage(throwable));
                    }
                });
    }

    @Nullable
    private static Announcement parseAnnouncement(@Nullable JsonElement body) {
        if (body == null || body.isJsonNull()) return null;

        JsonObject object = asObject(body);
        if (object == null) return null;

        JsonElement nested = object.has("announcement") ? object.get("announcement") : null;
        JsonObject source = nested != null && nested.isJsonObject()
                ? nested.getAsJsonObject()
                : object;

        Announcement announcement = new Announcement();
        announcement.setAnnouncementId(optString(source, "announcementId", "id"));
        announcement.setTitle(optString(source, "title"));
        announcement.setMessage(optString(source, "message"));
        announcement.setAudience(optString(source, "audience"));
        announcement.setBarangay(optString(source, "barangay"));
        announcement.setPriority(optString(source, "priority"));
        announcement.setCreatedByAdminId(optString(source, "createdByAdminId"));
        announcement.setCreatedAt(optString(source, "createdAt", "created_at"));
        announcement.setExpiresAt(optString(source, "expiresAt", "expires_at"));
        announcement.setTargetResidentIds(parseStringList(source.get("targetResidentIds")));

        return announcement;
    }

    private static List<Announcement> parseList(@Nullable JsonElement body) {
        List<Announcement> announcements = new ArrayList<>();
        if (body == null || body.isJsonNull()) return announcements;

        JsonObject object = asObject(body);
        JsonElement source = object == null ? body : object.get("announcements");
        if (source == null || !source.isJsonArray()) return announcements;

        JsonArray array = source.getAsJsonArray();
        for (JsonElement element : array) {
            Announcement announcement = parseAnnouncement(element);
            if (announcement != null) announcements.add(announcement);
        }
        return announcements;
    }

    @Nullable
    private static JsonObject asObject(JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        try {
            return element.getAsJsonObject();
        } catch (RuntimeException error) {
            return null;
        }
    }

    @Nullable
    private static String optString(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) continue;
            String text = value.getAsString();
            if (text != null && !text.trim().isEmpty()) return text;
        }
        return null;
    }

    private static List<String> parseStringList(@Nullable JsonElement element) {
        if (element == null || !element.isJsonArray()) return new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (item == null || item.isJsonNull() || !item.isJsonPrimitive()) continue;
            String text = item.getAsString();
            if (text != null && !text.trim().isEmpty()) values.add(text);
        }
        return values;
    }

    private boolean isSuccessful(Response<JsonElement> response, ErrorSink callback,
                                 String operation) {
        if (response.isSuccessful()) return true;
        if (response.code() == 401) sessionManager.clearSession();
        callback.onError(readError(response, operation));
        return false;
    }

    private boolean requireAdmin(ErrorSink callback, String action) {
        if (!sessionManager.isLoggedIn() || !sessionManager.isAdmin()) {
            callback.onError("Administrator access is required to " + action + ".");
            return false;
        }
        return true;
    }

    private String readError(Response<?> response, String operation) {
        String message = null;
        try (ResponseBody body = response.errorBody()) {
            if (body != null) {
                String raw = body.string();
                if (raw != null && !raw.trim().isEmpty()) {
                    try {
                        // The backend answers with { message } for every error.
                        JsonElement parsed = new JsonParser().parse(raw);
                        if (parsed.isJsonObject()) {
                            JsonObject object = parsed.getAsJsonObject();
                            if (object.has("message") && !object.get("message").isJsonNull()) {
                                message = object.get("message").getAsString();
                            }
                        }
                    } catch (RuntimeException ignored) {
                        // A non-JSON error body is reported verbatim below.
                    }
                    if (message == null) message = raw.trim();
                }
            }
        } catch (IOException ignored) {
            // Fall through to the status-based message.
        }

        if (message != null && !message.trim().isEmpty()) return message.trim();
        if (response.code() == 403) {
            return "Only an administrator can " + operation + ".";
        }
        return "Unable to " + operation + " (" + response.code() + ").";
    }

    private String networkMessage(Throwable throwable) {
        if (throwable instanceof SocketTimeoutException) {
            return "The server took too long to respond. Try again.";
        }
        if (throwable instanceof IOException) {
            return "Unable to reach the EcoWatch server. Check your connection and try again.";
        }
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Network error occurred. Please try again."
                : "Network error: " + message;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
