package com.example.ecowatchadmin1.data.repository;

import android.app.Application;

import androidx.annotation.NonNull;

import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.data.models.PersonnelTaskCounts;
import com.example.ecowatchadmin1.data.models.ReportPage;
import com.example.ecowatchadmin1.data.models.ReportStatistics;
import com.example.ecowatchadmin1.data.remote.AdminApiService;
import com.example.ecowatchadmin1.data.remote.ApiClient;
import com.example.ecowatchadmin1.data.remote.PersonnelCreateRequest;
import com.example.ecowatchadmin1.data.remote.PersonnelJsonParser;
import com.example.ecowatchadmin1.data.remote.PersonnelLoginRequest;
import com.example.ecowatchadmin1.data.remote.PersonnelPasswordRequest;
import com.example.ecowatchadmin1.data.remote.PersonnelStatusRequest;
import com.example.ecowatchadmin1.data.remote.PersonnelUpdateRequest;
import com.example.ecowatchadmin1.data.remote.ResidentJsonParser;
import com.example.ecowatchadmin1.utils.SessionManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Personnel repository. Every method is backed by the Render API; there is no
 * local fallback or fabricated personnel data.
 */
public class PersonnelRepository {
    /** Matches the reporter history endpoint so both detail screens page alike. */
    private static final int REPORT_PAGE_LIMIT = 50;

    private final AdminApiService apiService;
    private final SessionManager sessionManager;

    public PersonnelRepository(@NonNull Application application) {
        apiService = ApiClient.getAdminApiService(application);
        sessionManager = new SessionManager(application);
    }

    public void getPersonnel(PersonnelListCallback callback) {
        if (!requireAdmin(callback)) return;
        apiService.getPersonnel(false, 100, 0).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "personnel")) return;
                try {
                    List<Personnel> people = PersonnelJsonParser.parseList(response.body());
                    callback.onSuccess(people);
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

    public void getDetails(String personnelId, PersonnelCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(personnelId)) {
            callback.onError("Personnel ID is required.");
            return;
        }
        apiService.getPersonnelDetails(personnelId).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "personnel details")) return;
                Personnel person = PersonnelJsonParser.parsePersonnel(response.body());
                if (person == null) {
                    callback.onError("The server returned an invalid personnel record.");
                    return;
                }
                callback.onSuccess(person);
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    /**
     * Registers a cleanup team member.
     *
     * @param occupation Regular or Driver.
     * @param assignedVehicleId the truck for a driver, or null for a cleaner.
     */
    public void createPersonnel(String firstName, String lastName, String address,
                                String gmail, String username, String password,
                                String phoneNumber, String messengerLink,
                                String occupation, String assignedVehicleId,
                                PersonnelMutationCallback callback) {
        if (!requireAdmin(callback)) return;
        PersonnelCreateRequest request = new PersonnelCreateRequest(
                firstName, lastName, address, gmail, username, password,
                phoneNumber, messengerLink, occupation, assignedVehicleId);
        apiService.createPersonnel(request).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "personnel creation")) return;
                PersonnelJsonParser.MutationResult result =
                        PersonnelJsonParser.parseMutation(response.body(), password);
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    /**
     * Stores the personnel photograph captured during registration.
     *
     * <p>Runs as a separate request after the account exists, so account
     * creation keeps its plain JSON contract and a failed photo upload can be
     * reported without rolling back an account that was already created.
     */
    public void uploadPhoto(String personnelId, byte[] imageBytes,
                            PersonnelCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(personnelId)) {
            callback.onError("Personnel ID is required.");
            return;
        }
        if (imageBytes == null || imageBytes.length == 0) {
            callback.onError("The selected photo could not be read.");
            return;
        }
        RequestBody requestFile =
                RequestBody.create(MediaType.parse("image/jpeg"), imageBytes);
        MultipartBody.Part body =
                MultipartBody.Part.createFormData("image", "personnel.jpg", requestFile);
        apiService.uploadPersonnelPhoto(personnelId.trim(), body)
                .enqueue(new Callback<JsonElement>() {
                    @Override
                    public void onResponse(Call<JsonElement> call,
                                           Response<JsonElement> response) {
                        if (!isSuccessful(response, callback, "personnel photo")) return;
                        Personnel person = PersonnelJsonParser.parsePersonnel(response.body());
                        if (person == null) {
                            callback.onError(
                                    "The server returned an invalid personnel record.");
                            return;
                        }
                        callback.onSuccess(person);
                    }

                    @Override
                    public void onFailure(Call<JsonElement> call, Throwable throwable) {
                        callback.onError(networkMessage(throwable));
                    }
                });
    }

    public void updatePersonnel(String personnelId, String firstName, String lastName,
                                String address, String gmail, String username,
                                String phoneNumber, String messengerLink,
                                PersonnelCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(personnelId)) {
            callback.onError("Personnel ID is required.");
            return;
        }
        PersonnelUpdateRequest request = new PersonnelUpdateRequest(
                firstName, lastName, address, gmail, username, phoneNumber, messengerLink);
        apiService.updatePersonnel(personnelId, request).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "personnel update")) return;
                Personnel person = PersonnelJsonParser.parsePersonnel(response.body());
                callback.onSuccess(person);
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    public void resetPassword(String personnelId, String newPassword,
                              PersonnelMutationCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(personnelId)) {
            callback.onError("Personnel ID is required.");
            return;
        }
        PersonnelPasswordRequest request = new PersonnelPasswordRequest(newPassword);
        apiService.resetPersonnelPassword(personnelId, request).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "password reset")) return;
                PersonnelJsonParser.MutationResult result =
                        PersonnelJsonParser.parseMutation(response.body(), newPassword);
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    public void updateStatus(String personnelId, Boolean online, Boolean cleaning,
                             String status, PersonnelCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(personnelId)) {
            callback.onError("Personnel ID is required.");
            return;
        }
        PersonnelStatusRequest request = new PersonnelStatusRequest(online, cleaning, status);
        apiService.updatePersonnelStatus(personnelId, request).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "status update")) return;
                callback.onSuccess(PersonnelJsonParser.parsePersonnel(response.body()));
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    public void disablePersonnel(String personnelId, PersonnelCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(personnelId)) {
            callback.onError("Personnel ID is required.");
            return;
        }
        apiService.disablePersonnel(personnelId).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "account disable")) return;
                Personnel person = PersonnelJsonParser.parsePersonnel(response.body());
                if (person != null) person.setEnabled(false);
                callback.onSuccess(person);
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    public void deletePersonnel(String personnelId, PersonnelCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(personnelId)) {
            callback.onError("Personnel ID is required.");
            return;
        }
        apiService.deletePersonnel(personnelId).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!isSuccessful(response, callback, "account deletion")) return;
                Personnel person = PersonnelJsonParser.parsePersonnel(response.body());
                if (person != null) person.setEnabled(false);
                callback.onSuccess(person);
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    /** Kept for the future Personnel App; the Admin app does not use it to authenticate itself. */
    public void login(String username, String password, PersonnelLoginCallback callback) {
        if (isBlank(username) || isBlank(password)) {
            callback.onError("Username and password are required.");
            return;
        }
        apiService.personnelLogin(new PersonnelLoginRequest(username.trim(), password))
                .enqueue(new Callback<JsonElement>() {
                    @Override
                    public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                        if (!isSuccessful(response, callback, "personnel login")) return;
                        callback.onSuccess(response.body());
                    }

                    @Override
                    public void onFailure(Call<JsonElement> call, Throwable throwable) {
                        callback.onError(networkMessage(throwable));
                    }
                });
    }

    /** Reports with current or historical assignments for one personnel member. */
    public void getAssignedReports(String personnelId, int page,
                                   PersonnelReportPageCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(personnelId)) {
            callback.onError("Personnel ID is required.");
            return;
        }
        int normalizedPage = Math.max(1, page);
        apiService.getPersonnelReports(personnelId.trim(), normalizedPage, REPORT_PAGE_LIMIT)
                .enqueue(new Callback<JsonElement>() {
                    @Override
                    public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                        if (!isSuccessful(response, callback, "assigned tasks")) return;
                        try {
                            // These two helpers read the shared success/pagination
                            // envelope rather than anything resident-specific, so the
                            // personnel endpoints reuse them instead of duplicating.
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

    /**
     * Task counts for one cleaner, taken from the assignment rows.
     *
     * <p>This is what the stat cards read rather than the report statistics
     * above: report status only becomes "completed" after an administrator
     * approves the report, so counting reports under-reports the work the
     * cleaner has finished. The assignment row moves to COMPLETED the moment the
     * cleaner submits, which is the same event the Personnel app counts.
     */
    public void getPersonnelTaskCounts(String personnelId,
                                       PersonnelTaskCountsCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(personnelId)) {
            callback.onError("Personnel ID is required.");
            return;
        }
        apiService.getPersonnelTaskCounts(personnelId.trim())
                .enqueue(new Callback<JsonElement>() {
                    @Override
                    public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                        if (!isSuccessful(response, callback, "assigned task statistics")) return;
                        try {
                            callback.onSuccess(PersonnelJsonParser.parseTaskCounts(response.body()));
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
     * Report-status aggregates for one cleaner's assigned reports.
     *
     * <p>Not what the stat cards read (see
     * {@link #getPersonnelTaskCounts}): these count a report as completed only
     * once an administrator approves it.
     */
    public void getAssignedReportStatistics(String personnelId,
                                            PersonnelStatisticsCallback callback) {
        if (!requireAdmin(callback)) return;
        if (isBlank(personnelId)) {
            callback.onError("Personnel ID is required.");
            return;
        }
        apiService.getPersonnelReportStatistics(personnelId.trim())
                .enqueue(new Callback<JsonElement>() {
                    @Override
                    public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                        if (!isSuccessful(response, callback, "assigned task statistics")) return;
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

    private boolean isSuccessful(Response<JsonElement> response, ErrorCallback callback,
                                 String operation) {
        if (response.isSuccessful()) {
            return true;
        }
        if (response.code() == 401) {
            sessionManager.clearSession();
        }
        callback.onError(readError(response, operation));
        return false;
    }

    private <T> boolean requireAdmin(ErrorCallback callback) {
        if (!sessionManager.isLoggedIn() || !sessionManager.isAdmin()) {
            callback.onError("Administrator access is required for personnel management.");
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
                return "You are not authorized to manage personnel accounts.";
            case 404:
                if (operation.contains("details") || operation.contains("password")
                        || operation.contains("status") || operation.contains("disable")
                        || operation.contains("deletion")) {
                    return "The personnel account or requested operation was not found.";
                }
                return "The personnel service is not available on the server yet.";
            case 409:
                return "That personnel account already exists.";
            default:
                return response.code() >= 500
                        ? "The EcoWatch server is temporarily unavailable. Try again later."
                        : "Personnel request failed (" + response.code() + ").";
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
                ? "The server returned an invalid personnel response."
                : message;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public interface ErrorCallback {
        void onError(String message);
    }

    public interface PersonnelListCallback extends ErrorCallback {
        void onSuccess(List<Personnel> personnel);
    }

    public interface PersonnelCallback extends ErrorCallback {
        void onSuccess(Personnel personnel);
    }

    public interface PersonnelReportPageCallback extends ErrorCallback {
        void onSuccess(ReportPage page);
    }

    public interface PersonnelStatisticsCallback extends ErrorCallback {
        void onSuccess(ReportStatistics statistics);
    }

    public interface PersonnelTaskCountsCallback extends ErrorCallback {
        void onSuccess(PersonnelTaskCounts counts);
    }

    public interface PersonnelMutationCallback extends ErrorCallback {
        void onSuccess(PersonnelJsonParser.MutationResult result);
    }

    public interface PersonnelLoginCallback extends ErrorCallback {
        void onSuccess(JsonElement response);
    }
}
