package com.example.ecowatchadmin1.data.repository;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.ecowatchadmin1.data.local.AppDatabase;
import com.example.ecowatchadmin1.data.local.UserDao;
import com.example.ecowatchadmin1.data.models.AdminAccount;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.models.User;
import com.example.ecowatchadmin1.data.remote.AdminApiService;
import com.example.ecowatchadmin1.data.remote.AdminJsonParser;
import com.example.ecowatchadmin1.data.remote.AdminLoginRequest;
import com.example.ecowatchadmin1.data.remote.ApiClient;
import com.example.ecowatchadmin1.utils.SessionManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository for the admin-only Render endpoints. It owns authentication,
 * token persistence, response parsing, and network error translation.
 */
public class AdminRepository {
    private static final String TAG = "AdminRepository";

    private final AdminApiService apiService;
    private final SessionManager sessionManager;
    private final UserDao userDao;
    private final ExecutorService executorService;

    public AdminRepository(@NonNull Application application) {
        apiService = ApiClient.getAdminApiService(application);
        sessionManager = new SessionManager(application);
        userDao = AppDatabase.getInstance(application).userDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void login(String identifier, String password, LoginCallback callback) {
        if (callback == null) {
            return;
        }
        if (identifier == null || identifier.trim().isEmpty()
                || password == null || password.isEmpty()) {
            callback.onError("Username and password are required.");
            return;
        }

        AdminLoginRequest request = new AdminLoginRequest(identifier.trim(), password);
        enqueueLogin(apiService.login(request), request, callback, true);
    }

    private void enqueueLogin(Call<JsonObject> call,
                               AdminLoginRequest request,
                               LoginCallback callback,
                               boolean allowLegacyFallback) {
        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call,
                                   Response<JsonObject> response) {
                String errorMessage = readErrorMessage(response, "admin login");

                // The current Render deployment has historically exposed admin
                // login through /api/auth/login while /api/admin/login was
                // still protected. Prefer the requested endpoint, but use the
                // real legacy endpoint only when the server says that route is
                // unavailable. This is deliberately not a mock fallback.
                if (allowLegacyFallback && shouldUseLegacyLogin(response, errorMessage)) {
                    enqueueLogin(apiService.legacyLogin(request), request, callback, false);
                    return;
                }

                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError(errorMessage);
                    return;
                }

                try {
                    String token = AdminJsonParser.findToken(response.body());
                    if (token == null || token.trim().isEmpty()) {
                        throw new IllegalArgumentException("The server did not return a JWT.");
                    }

                    // Persist the token before parsing so a legacy response
                    // can be identified from its verified JWT without using
                    // residentId.
                    sessionManager.saveAuthToken(token);
                    String authenticatedAdminId = sessionManager.getAdminIdFromJwt(token);
                    User user = AdminJsonParser.parseLoginUser(
                            response.body(),
                            request.getUsername(),
                            authenticatedAdminId
                    );

                    // Save the canonical admin identity before exposing
                    // success so the first protected request is authenticated.
                    sessionManager.saveAdminId(user.getServerId());
                    sessionManager.saveUserRole("ADMIN");

                    AdminAccount account;
                    try {
                        account = AdminJsonParser.parseAccountDetails(response.body());
                    } catch (RuntimeException ignored) {
                        // The canonical ID is already authenticated above. A
                        // metadata omission must not prevent a valid login.
                        account = new AdminAccount(
                                user.getServerId(), "ADMIN", user.getCreatedAt(), null);
                    }
                    sessionManager.saveAdminAccount(account);

                    executorService.execute(() -> {
                        try {
                            userDao.logout();
                            userDao.insertUser(user);
                        } catch (RuntimeException error) {
                            Log.e(TAG, "Unable to cache admin profile", error);
                        }
                    });

                    callback.onSuccess(new LoginResult(token, user));
                } catch (RuntimeException error) {
                    sessionManager.clearSession();
                    callback.onError(userFacingMessage(error));
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    /**
     * Loads the canonical account document for the JWT-authenticated admin.
     * The request is deliberately routed through the admin API rather than
     * the resident compatibility endpoint.
     */
    public void getCurrentAdmin(AdminAccountCallback callback) {
        if (callback == null) {
            return;
        }
        if (!sessionManager.isLoggedIn() || !sessionManager.hasAuthToken()) {
            callback.onError("Please log in as an administrator to load account details.");
            return;
        }

        enqueueCurrentAdmin(apiService.getCurrentAdmin(), callback, true);
    }

    private void enqueueCurrentAdmin(Call<JsonObject> call,
                                     AdminAccountCallback callback,
                                     boolean allowLegacyFallback) {
        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call,
                                   Response<JsonObject> response) {
                if (allowLegacyFallback
                        && (response.code() == 404 || response.code() == 405)) {
                    enqueueCurrentAdmin(apiService.getLegacyCurrentAdmin(), callback, false);
                    return;
                }
                if (response.code() == 401) {
                    sessionManager.clearSession();
                    executorService.execute(userDao::logout);
                    callback.onError("Your administrator session has expired. Please log in again.");
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError(readErrorMessage(response, "admin account"));
                    return;
                }

                try {
                    String sessionAdminId = sessionManager.fetchAdminId();
                    AdminAccount account;
                    try {
                        account = allowLegacyFallback
                                ? AdminJsonParser.parseAccountDetails(response.body())
                                : AdminJsonParser.parseAccountDetails(
                                        response.body(), sessionAdminId);
                    } catch (RuntimeException parseError) {
                        if (allowLegacyFallback) {
                            enqueueCurrentAdmin(apiService.getLegacyCurrentAdmin(), callback, false);
                            return;
                        }
                        throw parseError;
                    }
                    if (sessionAdminId != null && !sessionAdminId.trim().isEmpty()
                            && !sessionAdminId.trim().equalsIgnoreCase(account.getAdminId())) {
                        sessionManager.clearSession();
                        executorService.execute(userDao::logout);
                        callback.onError("The administrator session changed. Please log in again.");
                        return;
                    }
                    sessionManager.saveAdminAccount(account);
                    callback.onSuccess(account);
                } catch (RuntimeException error) {
                    callback.onError(userFacingMessage(error));
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    public void getReports(ReportsCallback callback) {
        if (callback == null) {
            return;
        }

        String token = sessionManager.fetchAuthToken();
        if (!sessionManager.isLoggedIn() || !sessionManager.hasAuthToken()
                || token == null || token.trim().isEmpty()) {
            callback.onError("Please log in as an administrator to load reports.");
            return;
        }

        apiService.getReports().enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call,
                                   Response<JsonElement> response) {
                if (response.code() == 401) {
                    sessionManager.clearSession();
                    executorService.execute(userDao::logout);
                    callback.onError("Your administrator session has expired. Please log in again.");
                    return;
                }

                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError(readErrorMessage(response, "reports"));
                    return;
                }

                try {
                    callback.onSuccess(AdminJsonParser.parseReports(response.body()));
                } catch (RuntimeException error) {
                    callback.onError(userFacingMessage(error));
                }
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable throwable) {
                callback.onError(networkMessage(throwable));
            }
        });
    }

    private boolean shouldUseLegacyLogin(Response<?> response, String message) {
        if (response.code() == 404 || response.code() == 405) {
            return true;
        }

        if (response.code() == 401 && message != null) {
            String normalized = message.toLowerCase(Locale.US);
            return normalized.contains("not authorized") && normalized.contains("no token");
        }
        return false;
    }

    private String readErrorMessage(Response<?> response, String operation) {
        String message = null;
        try (ResponseBody errorBody = response.errorBody()) {
            if (errorBody != null) {
                String raw = errorBody.string();
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
                        // The status-specific fallback below is clearer than
                        // exposing an HTML/proxy error body to the UI.
                    }
                }
            }
        } catch (IOException ignored) {
            // Fall through to the status-specific message.
        }

        if (message != null && !message.trim().isEmpty()) {
            return message.trim();
        }

        switch (response.code()) {
            case 400:
                return "The " + operation + " request was invalid.";
            case 401:
                return "Invalid administrator credentials.";
            case 403:
                return "This account is not authorized as an administrator.";
            case 404:
                return "The " + operation + " endpoint was not found on the Render server.";
            case 408:
                return "The server took too long to respond. Try again.";
            default:
                if (response.code() >= 500) {
                    return "The EcoWatch server is temporarily unavailable. Try again later.";
                }
                return "Request failed (" + response.code() + ").";
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

    private String userFacingMessage(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? "The server returned an invalid response."
                : message;
    }

    public interface LoginCallback {
        void onSuccess(LoginResult result);

        void onError(String message);
    }

    public interface AdminAccountCallback {
        void onSuccess(AdminAccount account);

        void onError(String message);
    }

    public interface ReportsCallback {
        void onSuccess(List<Report> reports);

        void onError(String message);
    }

    public static final class LoginResult {
        private final String token;
        private final User user;

        public LoginResult(String token, User user) {
            this.token = token;
            this.user = user;
        }

        public String getToken() {
            return token;
        }

        public User getUser() {
            return user;
        }
    }
}
