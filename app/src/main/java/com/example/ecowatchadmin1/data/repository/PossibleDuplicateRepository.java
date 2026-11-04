package com.example.ecowatchadmin1.data.repository;

import android.app.Application;

import androidx.annotation.NonNull;

import com.example.ecowatchadmin1.data.models.PossibleDuplicateGroup;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.remote.AdminApiService;
import com.example.ecowatchadmin1.data.remote.AdminJsonParser;
import com.example.ecowatchadmin1.data.remote.ApiClient;
import com.example.ecowatchadmin1.utils.SessionManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Backs the Possible Duplicate Reports review in the admin app. Each group is
 * a set of reports the backend believes may describe the same waste incident;
 * no cluster terminology reaches the UI.
 */
public class PossibleDuplicateRepository {

    private final AdminApiService adminApiService;
    private final SessionManager sessionManager;

    public PossibleDuplicateRepository(@NonNull Application application) {
        this.adminApiService = ApiClient.getAdminApiService(application);
        this.sessionManager = new SessionManager(application);
    }

    public interface GroupsCallback {
        void onSuccess(List<PossibleDuplicateGroup> groups);
        void onError(String error);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String error);
    }

    public void getPossibleDuplicates(@NonNull GroupsCallback callback) {
        if (!sessionManager.isLoggedIn() || !sessionManager.hasAuthToken()) {
            callback.onError("Please log in as an administrator to review possible duplicates.");
            return;
        }

        adminApiService.getPossibleDuplicates().enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call,
                                   @NonNull Response<JsonElement> response) {
                if (response.code() == 401) {
                    sessionManager.clearSession();
                    callback.onError("Your administrator session has expired. Please log in again.");
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError("Possible duplicate reports could not load.");
                    return;
                }
                try {
                    callback.onSuccess(parseGroups(response.body()));
                } catch (RuntimeException error) {
                    callback.onError("Possible duplicate reports could not be read.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                callback.onError(friendlyNetworkError(t));
            }
        });
    }

    public void markAsDuplicates(@NonNull String groupId, @NonNull List<String> reportIds,
                                 @NonNull ActionCallback callback) {
        if (!sessionManager.isLoggedIn() || !sessionManager.hasAuthToken()) {
            callback.onError("Please log in again.");
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("reportIds", reportIds);
        adminApiService.denyPossibleDuplicateGroup(groupId, body)
                .enqueue(new Callback<JsonElement>() {
                    @Override
                    public void onResponse(@NonNull Call<JsonElement> call,
                                           @NonNull Response<JsonElement> response) {
                        if (response.code() == 401) {
                            sessionManager.clearSession();
                            callback.onError("Your administrator session has expired. Please log in again.");
                            return;
                        }
                        if (!response.isSuccessful()) {
                            callback.onError("Those reports could not be marked as duplicates.");
                            return;
                        }
                        callback.onSuccess();
                    }

                    @Override
                    public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                        callback.onError(friendlyNetworkError(t));
                    }
                });
    }

    public void markAsNotDuplicates(@NonNull String groupId, @NonNull ActionCallback callback) {
        if (!sessionManager.isLoggedIn() || !sessionManager.hasAuthToken()) {
            callback.onError("Please log in again.");
            return;
        }
        adminApiService.acceptPossibleDuplicateGroup(groupId)
                .enqueue(new Callback<JsonElement>() {
                    @Override
                    public void onResponse(@NonNull Call<JsonElement> call,
                                           @NonNull Response<JsonElement> response) {
                        if (response.code() == 401) {
                            sessionManager.clearSession();
                            callback.onError("Your administrator session has expired. Please log in again.");
                            return;
                        }
                        if (!response.isSuccessful()) {
                            callback.onError("The duplicate review could not be saved.");
                            return;
                        }
                        callback.onSuccess();
                    }

                    @Override
                    public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                        callback.onError(friendlyNetworkError(t));
                    }
                });
    }

    private List<PossibleDuplicateGroup> parseGroups(JsonElement payload) {
        List<PossibleDuplicateGroup> groups = new ArrayList<>();
        if (payload == null || !payload.isJsonObject()) return groups;
        JsonElement groupsElement = payload.getAsJsonObject().get("groups");
        if (groupsElement == null || !groupsElement.isJsonArray()) return groups;

        for (JsonElement element : groupsElement.getAsJsonArray()) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            PossibleDuplicateGroup group = new PossibleDuplicateGroup();
            if (object.has("groupId") && !object.get("groupId").isJsonNull()) {
                group.setGroupId(object.get("groupId").getAsString());
            }
            if (object.has("centerLatitude") && !object.get("centerLatitude").isJsonNull()) {
                group.setCenterLatitude(object.get("centerLatitude").getAsDouble());
            }
            if (object.has("centerLongitude") && !object.get("centerLongitude").isJsonNull()) {
                group.setCenterLongitude(object.get("centerLongitude").getAsDouble());
            }
            JsonElement reportsElement = object.get("reports");
            if (reportsElement != null && reportsElement.isJsonArray()) {
                JsonObject wrapper = new JsonObject();
                wrapper.add("reports", reportsElement.getAsJsonArray());
                List<Report> reports = AdminJsonParser.parseReports(wrapper);
                group.setReports(reports);
            }
            if (group.getReports() != null && group.getReports().size() >= 2) {
                groups.add(group);
            }
        }
        // The server groups by location; the review queue must also agree on
        // the waste scale. Regrouping here keeps the Admin app correct against
        // an older server and makes the split explicit in one testable place.
        return PossibleDuplicateGrouper.regroup(groups);
    }

    private String friendlyNetworkError(Throwable t) {
        if (t instanceof java.net.SocketTimeoutException) {
            return "The server took too long to respond.";
        }
        return "Please check your internet connection.";
    }
}
