package com.example.ecowatchadmin1.data.repository;

import android.app.Application;

import androidx.annotation.NonNull;

import com.example.ecowatchadmin1.data.models.ReportPage;
import com.example.ecowatchadmin1.data.models.ReportStatistics;
import com.example.ecowatchadmin1.data.models.ResidentAccount;
import com.example.ecowatchadmin1.data.models.ResidentPage;
import com.example.ecowatchadmin1.data.remote.AdminApiService;
import com.example.ecowatchadmin1.data.remote.ApiClient;
import com.example.ecowatchadmin1.data.remote.ResidentJsonParser;
import com.example.ecowatchadmin1.data.remote.ResidentPasswordRequest;
import com.example.ecowatchadmin1.utils.SessionManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Remote-only data access for the admin reporter-account feature. */
public class ResidentRepository {
    private final AdminApiService apiService;
    private final SessionManager sessionManager;

    public ResidentRepository(@NonNull Application application) {
        apiService = ApiClient.getAdminApiService(application);
        sessionManager = new SessionManager(application);
    }

    public void getResidents(String search, String status, int page,
                             ResidentPageCallback callback) {
        if (!requireAdmin(callback)) return;
        String normalizedSearch = search == null ? "" : search.trim();
        String normalizedStatus = status == null || status.trim().isEmpty()
                ? "ALL" : status.trim().toUpperCase(Locale.ROOT);
        int normalizedPage = Math.max(1, page);
        int limit = 100;
        int offset = (normalizedPage - 1) * limit;

        apiService.getResidents(normalizedSearch, normalizedStatus, limit, offset)
                .enqueue(new Callback<JsonElement>() {
                    @Override
                    public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                        if (!isSuccessful(response, callback, "reporter accounts")) return;
                        try {
                            ResidentPage result = ResidentJsonParser.parsePage(response.body());
                            callback.onSuccess(result == null ? new ResidentPage() : result);
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

    public void getResidentDetails(String residentId, ResidentDetailsCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(residentId)) {
            callback.onError("Resident ID is required.");
            return;
        }
        apiService.getResidentDetails(residentId.trim()).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "reporter details")) return;
                try {
                    ResidentAccount resident = ResidentJsonParser.parseResident(response.body());
                    if (resident == null) {
                        callback.onError("The server returned an invalid reporter account.");
                        return;
                    }
                    callback.onSuccess(resident,
                            ResidentJsonParser.parseStatistics(response.body()));
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

    public void getResidentReports(String residentId, int page, ReportPageCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(residentId)) {
            callback.onError("Resident ID is required.");
            return;
        }
        int normalizedPage = Math.max(1, page);
        int limit = 50;
        apiService.getResidentReports(residentId.trim(), normalizedPage, limit)
                .enqueue(new Callback<JsonElement>() {
                    @Override
                    public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                        if (!isSuccessful(response, callback, "reporter report history")) return;
                        try {
                            ReportPage result = ResidentJsonParser.parseReportPage(response.body());
                            callback.onSuccess(result == null ? new ReportPage() : result);
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

    public void getResidentReportStatistics(String residentId, StatisticsCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(residentId)) {
            callback.onError("Resident ID is required.");
            return;
        }
        apiService.getResidentReportStatistics(residentId.trim())
                .enqueue(new Callback<JsonElement>() {
                    @Override
                    public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                        if (!isSuccessful(response, callback, "reporter statistics")) return;
                        try {
                            callback.onSuccess(ResidentJsonParser.parseStatistics(response.body()));
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
     * Sets a reporter's password. The administrator chooses it and reads it back
     * from the result so it can be handed over, so this cannot be folded into
     * {@link #getResidentDetails}: the credential is returned once and must
     * reach a dialog, not a profile render.
     */
    public void resetPassword(String residentId, String newPassword,
                              ResidentMutationCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(residentId)) {
            callback.onError("Resident ID is required.");
            return;
        }
        if (isBlank(newPassword)) {
            callback.onError("A new password is required.");
            return;
        }
        apiService.resetResidentPassword(residentId.trim(),
                        new ResidentPasswordRequest(newPassword))
                .enqueue(new Callback<JsonElement>() {
                    @Override
                    public void onResponse(Call<JsonElement> call,
                                           Response<JsonElement> response) {
                        if (!isSuccessful(response, callback, "password reset")) return;
                        try {
                            callback.onSuccess(ResidentJsonParser.parseResidentMutation(
                                    response.body(), newPassword));
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
     * Disables a reporter account. The profile, reports and history are kept
     * server-side, so this is reversible only by an administrator, never by the
     * reporter signing in again.
     */
    public void disableResident(String residentId, ResidentCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(residentId)) {
            callback.onError("Resident ID is required.");
            return;
        }
        apiService.disableResident(residentId.trim()).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "account disable")) return;
                try {
                    callback.onSuccess(ResidentJsonParser.parseResident(response.body()));
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
 * Restores a reporter account disabled by an administrator. The reporter cannot
 * do this themselves, which is what makes this the only way back.
 */
public void enableResident(String residentId, ResidentCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(residentId)) {
            callback.onError("Resident ID is required.");
            return;
        }
        apiService.enableResident(residentId.trim()).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "account enable")) return;
                try {
                    callback.onSuccess(ResidentJsonParser.parseResident(response.body()));
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
     * Permanently deletes a reporter account and every row it owns.
     *
     * <p>Unlike {@link #disableResident} there is no undo here and no updated
     * profile to hand back: the response carries only a message, because by the
     * time it arrives there is nothing left to render. Deletion is refused while
     * another request for the same account is in flight by the server, so the
     * caller only has to avoid offering a second tap.
     */
    public void deleteResident(String residentId, ResidentDeletionCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(residentId)) {
            callback.onError("Resident ID is required.");
            return;
        }
        apiService.deleteResident(residentId.trim()).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "account deletion")) return;
                callback.onSuccess();
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    private boolean isSuccessful(Response<JsonElement> response,
                                 ErrorCallback callback,
                                 String operation) {
        if (response.isSuccessful() && response.body() != null) return true;
        if (response.code() == 401) sessionManager.clearSession();
        callback.onError(readError(response, operation));
        return false;
    }

    private <T> boolean requireAdmin(ErrorCallback callback) {
        if (!sessionManager.isLoggedIn() || !sessionManager.isAdmin()
                || !sessionManager.hasAuthToken()) {
            callback.onError("Administrator access is required for reporter accounts.");
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
                return "You are not authorized to manage reporter accounts.";
            case 404:
                return "The reporter account or report data was not found.";
            default:
                return response.code() >= 500
                        ? "The EcoWatch server is temporarily unavailable. Try again later."
                        : "Reporter account request failed (" + response.code() + ").";
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
                ? "The server returned an invalid reporter response."
                : message;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public interface ErrorCallback {
        void onError(String message);
    }

    public interface ResidentPageCallback extends ErrorCallback {
        void onSuccess(ResidentPage page);
    }

    public interface ResidentDetailsCallback extends ErrorCallback {
        void onSuccess(ResidentAccount resident, ReportStatistics statistics);
    }

    public interface ResidentCallback extends ErrorCallback {
        void onSuccess(ResidentAccount resident);
    }

    /**
     * A completed deletion. Carries nothing but success, because there is no
     * longer a record to describe.
     */
    public interface ResidentDeletionCallback extends ErrorCallback {
        void onSuccess();
    }

    /** A reset outcome: the updated account plus the password to hand over. */
    public interface ResidentMutationCallback extends ErrorCallback {
        void onSuccess(ResidentJsonParser.MutationResult result);
    }

    public interface ReportPageCallback extends ErrorCallback {
        void onSuccess(ReportPage page);
    }

    public interface StatisticsCallback extends ErrorCallback {
        void onSuccess(ReportStatistics statistics);
    }
}
