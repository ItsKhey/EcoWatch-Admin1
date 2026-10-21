package com.example.ecowatchadmin1.data.repository;

import android.app.Application;

import androidx.annotation.NonNull;

import com.example.ecowatchadmin1.data.models.CleaningTeam;
import com.example.ecowatchadmin1.data.remote.AdminApiService;
import com.example.ecowatchadmin1.data.remote.ApiClient;
import com.example.ecowatchadmin1.data.remote.CleaningTeamJsonParser;
import com.example.ecowatchadmin1.utils.SessionManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The cleaning teams the administrator can dispatch.
 *
 * <p>A team is the crew that goes out for a report filed under Medium- or
 * Large-Scale Waste. It is maintained on the server and read fresh here, so
 * the sheet always offers the crew as it actually is rather than as it was
 * when the app was installed.
 */
public class CleaningTeamRepository {

    private final AdminApiService apiService;
    private final SessionManager sessionManager;

    public CleaningTeamRepository(@NonNull Application application) {
        apiService = ApiClient.getAdminApiService(application);
        sessionManager = new SessionManager(application);
    }

    /** Every live team, without its member list. */
    public void getTeams(TeamListCallback callback) {
        if (!requireAdmin(callback)) return;
        apiService.getCleaningTeams().enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "cleaning team")) return;
                try {
                    callback.onSuccess(CleaningTeamJsonParser.parseTeams(response.body()));
                } catch (RuntimeException error) {
                    callback.onError(userFacing(error));
                }
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    /**
     * One team together with the people on it.
     *
     * <p>The member list is what the assignment sheet needs: the crew that is
     * dispatched has to be made of members of the chosen team, so the sheet
     * offers exactly those people.
     */
    public void getTeam(String teamId, TeamCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(teamId)) {
            callback.onError("A cleaning team id is required.");
            return;
        }
        apiService.getCleaningTeam(teamId.trim()).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "cleaning team")) return;
                CleaningTeam team = CleaningTeamJsonParser.parseTeam(response.body());
                if (team == null) {
                    callback.onError("The server returned an invalid cleaning team.");
                    return;
                }
                callback.onSuccess(team);
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    private boolean isSuccessful(Response<JsonElement> response, ErrorCallback callback,
                                 String operation) {
        if (response.isSuccessful()) return true;
        if (response.code() == 401) sessionManager.clearSession();
        callback.onError(readError(response, operation));
        return false;
    }

    private boolean requireAdmin(ErrorCallback callback) {
        if (!sessionManager.isLoggedIn() || !sessionManager.isAdmin()) {
            callback.onError("Administrator access is required to manage cleaning teams.");
            return false;
        }
        return true;
    }

    /**
     * Reads the server's own message out of an error body.
     *
     * <p>The server names the team when it can, which is more useful than a
     * generic "request failed".
     */
    private String readError(Response<?> response, String operation) {
        String message = null;
        try (okhttp3.ResponseBody body = response.errorBody()) {
            if (body != null) {
                String raw = body.string();
                if (raw != null && !raw.trim().isEmpty()) {
                    try {
                        JsonElement parsed = new JsonParser().parse(raw);
                        if (parsed.isJsonObject()) {
                            JsonObject object = parsed.getAsJsonObject();
                            if (object.has("message") && !object.get("message").isJsonNull()) {
                                message = object.get("message").getAsString();
                            } else if (object.has("error") && !object.get("error").isJsonNull()) {
                                message = object.get("error").getAsString();
                            }
                        }
                    } catch (RuntimeException ignored) {
                        // Use the status-specific message below.
                    }
                }
            }
        } catch (IOException ignored) {
            // Use the status-specific message below.
        }
        if (message != null && !message.trim().isEmpty()) return message.trim();

        switch (response.code()) {
            case 400:
                return "The " + operation + " request was invalid.";
            case 401:
                return "Your administrator session has expired. Please log in again.";
            case 403:
                return "You are not authorized to manage cleaning teams.";
            case 404:
                return "That cleaning team was not found.";
            default:
                return response.code() >= 500
                        ? "The EcoWatch server is temporarily unavailable. Try again later."
                        : "The " + operation + " request failed (" + response.code() + ").";
        }
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

    private String userFacing(RuntimeException error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty()
                ? "The server returned an invalid cleaning team response."
                : message;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public interface ErrorCallback {
        void onError(String message);
    }

    public interface TeamListCallback extends ErrorCallback {
        void onSuccess(List<CleaningTeam> teams);
    }

    public interface TeamCallback extends ErrorCallback {
        void onSuccess(CleaningTeam team);
    }
}
