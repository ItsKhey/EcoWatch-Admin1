package com.example.ecowatchadmin1.data.repository;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.example.ecowatchadmin1.data.local.AppDatabase;
import com.example.ecowatchadmin1.data.local.ReportDao;
import com.example.ecowatchadmin1.data.local.UserDao;
import com.example.ecowatchadmin1.data.models.AdminAccount;
import com.example.ecowatchadmin1.data.models.User;
import com.example.ecowatchadmin1.data.remote.ApiService;
import com.example.ecowatchadmin1.data.remote.RetrofitClient;
import com.example.ecowatchadmin1.utils.SessionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {
    private UserDao userDao;
    private ReportDao reportDao;
    private ApiService apiService;
    private AdminRepository adminRepository;
    private PersonnelRepository personnelRepository;
    private SessionManager sessionManager;
    private NotificationRepository notificationRepository;
    private ExecutorService executorService;
    private Handler mainHandler;

    public AuthRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        userDao = db.userDao();
        reportDao = db.reportDao();
        apiService = RetrofitClient.getApiService(application);
        notificationRepository = NotificationRepository.getInstance(application);
        adminRepository = new AdminRepository(application);
        personnelRepository = new PersonnelRepository(application);
        sessionManager = new SessionManager(application);
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    private String getErrorMessage(Response<?> response, String defaultMsg) {
        try (ResponseBody errorBody = response.errorBody()) {
            if (errorBody != null) {
                JSONObject jsonObject = new JSONObject(errorBody.string());
                if (jsonObject.has("message")) {
                    String msg = jsonObject.getString("message");
                    if (msg.contains("ENETUNREACH") || msg.contains("5432") || msg.contains("ECONNREFUSED") || msg.contains("ETIMEDOUT")) {
                        return "Backend server error: Database connection unreachable";
                    }
                    return msg;
                }
            }
        } catch (Exception e) {
            // fallback
        }
        return defaultMsg + ": " + response.message();
    }

    public LiveData<User> getUser() {
        return userDao.getUser();
    }

    /**
     * Outcome of a registration, which the server decides.
     *
     * <p>With verification turned off the account exists as soon as the request
     * succeeds, so there is no registrationId and no code to type. With
     * verification on, the account is staged behind a registrationId and only
     * {@link #verifyOTP} creates it.
     */
    public static final class RegistrationOutcome {
        private final String registrationId;
        private final boolean requiresVerification;

        RegistrationOutcome(String registrationId, boolean requiresVerification) {
            this.registrationId = registrationId;
            this.requiresVerification = requiresVerification;
        }

        /** Non-null only while the account is still pending a verification code. */
        public String getRegistrationId() {
            return registrationId;
        }

        public boolean requiresVerification() {
            return requiresVerification;
        }
    }

    /**
     * Registers a resident.
     *
     * <p>No verificationMethod is sent, so the server applies whatever policy is
     * configured. The response reports which path it took, and the screen either
     * asks for the emailed code or sends the resident straight to login.
     */
    public void register(String firstName, String lastName, String username, String email, String password, ApiCallback<RegistrationOutcome> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("username", username);
        body.put("email", email);
        body.put("password", password);

        apiService.register(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String registrationId = (String) response.body().get("registrationId");
                    boolean requiresVerification = Boolean.TRUE.equals(
                            response.body().get("requiresVerification"));
                    callback.onSuccess(new RegistrationOutcome(registrationId, requiresVerification));
                } else {
                    callback.onError(getErrorMessage(response, "Registration failed"));
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error occurred.");
            }
        });
    }

    public void verifyOTP(String registrationId, String otp, ApiCallback<String> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("registrationId", registrationId);
        body.put("otp", otp);

        apiService.verifyOTP(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess((String) response.body().get("residentId"));
                } else {
                    callback.onError(getErrorMessage(response, "Verification failed"));
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error occurred.");
            }
        });
    }

    public void forgotPassword(String username, ApiCallback<String> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        // Backend also supports email, but we use username as per request
        
        apiService.forgotPassword(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess((String) response.body().get("message"));
                } else {
                    callback.onError(getErrorMessage(response, "Failed to send reset code"));
                }
            }
            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void verifyResetOTP(String username, String otp, ApiCallback<String> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("otp", otp);

        apiService.verifyResetOTP(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess((String) response.body().get("message"));
                } else {
                    callback.onError(getErrorMessage(response, "OTP verification failed"));
                }
            }
            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void resetPassword(String username, String otp, String newPassword, ApiCallback<String> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("otp", otp);
        body.put("password", newPassword);

        apiService.resetPassword(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess((String) response.body().get("message"));
                } else {
                    callback.onError(getErrorMessage(response, "Password reset failed"));
                }
            }
            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void changePassword(String oldPassword, String newPassword, ApiCallback<String> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("oldPassword", oldPassword);
        body.put("newPassword", newPassword);

        apiService.changePassword(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess((String) response.body().get("message"));
                } else {
                    callback.onError(getErrorMessage(response, "Password update failed"));
                }
            }
            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void resetLoggedInPassword(String newPassword, ApiCallback<String> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("password", newPassword);

        apiService.resetLoggedInPassword(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess((String) response.body().get("message"));
                } else {
                    callback.onError(getErrorMessage(response, "Password reset failed"));
                }
            }
            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void login(String username, String password, ApiCallback<User> callback) {
        adminRepository.login(username, password, new AdminRepository.LoginCallback() {
            @Override
            public void onSuccess(AdminRepository.LoginResult result) {
                callback.onSuccess(result.getUser());
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void updateProfile(String firstName, String lastName, String username, String email,
                              String address, ApiCallback<User> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("username", username);
        body.put("email", email);
        body.put("address", address != null ? address : "");

        apiService.updateProfile(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call,
                                   Response<Map<String, Object>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    if (callback != null) {
                        callback.onError(getErrorMessage(response, "Update failed"));
                    }
                    return;
                }

                // New responses use an admin object. The resident wrapper is
                // accepted only for old profile fields; its residentId is
                // deliberately never read.
                Map<String, Object> resData = mapValue(response.body().get("admin"));
                if (resData == null) {
                    resData = mapValue(response.body().get("resident"));
                }
                if (resData == null) {
                    if (callback != null) {
                        callback.onError("The server returned an invalid profile response.");
                    }
                    return;
                }

                String adminId = firstNonBlank(
                        stringValue(resData, "adminId"),
                        stringValue(resData, "admin_id"),
                        sessionManager.fetchAdminId()
                );
                if (adminId == null) {
                    if (callback != null) {
                        callback.onError("The server did not return the administrator ID.");
                    }
                    return;
                }

                final String effectiveRole = "ADMIN";
                final String firstName = stringValue(resData, "firstName");
                final String lastName = stringValue(resData, "lastName");
                final String username = stringValue(resData, "username");
                final String email = stringValue(resData, "email");
                final String updatedAddress = stringValue(resData, "address");
                final String profileImage = stringValue(resData, "profileImage");
                final String updatedAt = stringValue(resData, "updatedAt");

                executorService.execute(() -> {
                    User existingUser = userDao.getSyncUser();
                    User updatedUser = new User(
                            adminId,
                            valueOrExisting(firstName,
                                    existingUser == null ? null : existingUser.getFirstName()),
                            valueOrExisting(lastName,
                                    existingUser == null ? null : existingUser.getLastName()),
                            valueOrExisting(username,
                                    existingUser == null ? null : existingUser.getUsername()),
                            valueOrExisting(email,
                                    existingUser == null ? null : existingUser.getEmail()),
                            valueOrExisting(updatedAddress,
                                    existingUser == null ? null : existingUser.getAddress()),
                            valueOrExisting(profileImage,
                                    existingUser == null ? null : existingUser.getProfileImageUrl()),
                            effectiveRole
                    );
                    updatedUser.setCreatedAt(existingUser == null
                            ? cachedAdminCreatedAt()
                            : existingUser.getCreatedAt());
                    updatedUser.setUpdatedAt(updatedAt);
                    userDao.logout();
                    userDao.insertUser(updatedUser);
                    sessionManager.saveAdminId(adminId);
                    sessionManager.saveUserRole(effectiveRole);
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(updatedUser);
                    });
                });
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                if (callback != null) {
                    callback.onError(t.getMessage() == null
                            ? "Profile update failed." : t.getMessage());
                }
            }
        });
    }

    public void updateProfileImage(byte[] imageBytes, ApiCallback<String> callback) {
        RequestBody requestFile = RequestBody.create(MediaType.parse("image/jpeg"), imageBytes);
        MultipartBody.Part body = MultipartBody.Part.createFormData("image", "profile.jpg", requestFile);

        apiService.updateProfileImage(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String imageUrl = (String) response.body().get("profileImage");
                    callback.onSuccess(imageUrl);
                    // Force a refresh of the user object locally
                    fetchMe(new ApiCallback<User>() {
                        @Override public void onSuccess(User result) {}
                        @Override public void onError(String error) {}
                    });
                } else {
                    callback.onError(getErrorMessage(response, "Image upload failed"));
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void fetchMe(ApiCallback<User> callback) {
        adminRepository.getCurrentAdmin(new AdminRepository.AdminAccountCallback() {
            @Override
            public void onSuccess(AdminAccount account) {
                executorService.execute(() -> {
                    User existing = userDao.getSyncUser();
                    User user = new User(
                            account.getAdminId(),
                            valueOrExisting(account.getFirstName(),
                                    existing == null ? null : existing.getFirstName()),
                            valueOrExisting(account.getLastName(),
                                    existing == null ? null : existing.getLastName()),
                            valueOrExisting(account.getUsername(),
                                    existing == null ? null : existing.getUsername()),
                            valueOrExisting(account.getEmail(),
                                    existing == null ? null : existing.getEmail()),
                            valueOrExisting(account.getAddress(),
                                    existing == null ? null : existing.getAddress()),
                            valueOrExisting(account.getProfileImageUrl(),
                                    existing == null ? null : existing.getProfileImageUrl()),
                            "ADMIN"
                    );
                    user.setCreatedAt(account.getCreatedAt());
                    userDao.logout();
                    userDao.insertUser(user);
                    sessionManager.saveUserRole("ADMIN");
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(user);
                    });
                });
            }

            @Override
            public void onError(String message) {
                if (callback != null) callback.onError(message);
            }
        });
    }

    public void logout() {
        // Release the device token before the session disappears so the call is
        // still authenticated, then drop the cached notifications for that
        // administrator. Mirrors the Resident app's logout contract.
        String adminId = sessionManager.fetchResidentId();
        notificationRepository.clearFcmTokenBestEffort();
        if (adminId != null) {
            notificationRepository.clearLocalDataForAdmin(adminId);
        }

        executorService.execute(() -> {
            userDao.logout();
            reportDao.deleteAllReports();
            sessionManager.clearSession();
        });
    }

    public boolean isLoggedIn() {
        return sessionManager.isLoggedIn();
    }

    /**
     * @deprecated Use PersonnelRepository for the complete Phase 2A contract.
     * Kept as a compatibility facade for older admin screens.
     */
    @Deprecated
    public void createPersonnel(String firstName, String lastName, String username, String email,
                                String password, String phoneNumber, ApiCallback<String> callback) {
        // This legacy facade has no Messenger link to pass, so it is created empty.
        // It also asks for no occupation, so the account is registered as a
        // Regular cleaner; the Personnel screen is where an occupation and a
        // truck are actually chosen.
        personnelRepository.createPersonnel(firstName, lastName, "", email, username, password,
                phoneNumber, "",
                com.example.ecowatchadmin1.data.models.Personnel.OCCUPATION_REGULAR, null,
                new PersonnelRepository.PersonnelMutationCallback() {
                    @Override
                    public void onSuccess(com.example.ecowatchadmin1.data.remote.PersonnelJsonParser.MutationResult result) {
                        if (callback != null) callback.onSuccess("Personnel account created successfully");
                    }

                    @Override
                    public void onError(String error) {
                        if (callback != null) callback.onError(error);
                    }
                });
    }

    /** @deprecated Use PersonnelRepository#getPersonnel. */
    @Deprecated
    public void getPersonnel(ApiCallback<List<User>> callback) {
        personnelRepository.getPersonnel(new PersonnelRepository.PersonnelListCallback() {
            @Override
            public void onSuccess(List<com.example.ecowatchadmin1.data.models.Personnel> personnel) {
                List<User> legacyUsers = new ArrayList<>();
                if (personnel != null) {
                    for (com.example.ecowatchadmin1.data.models.Personnel person : personnel) {
                        User user = new User(person.getPersonnelId(), person.getFirstName(),
                                person.getLastName(), person.getUsername(), person.getGmail(),
                                person.getAddress(), "", "PERSONNEL");
                        user.setStatus(person.getStatus());
                        user.setCreatedAt(person.getDateCreated());
                        legacyUsers.add(user);
                    }
                }
                if (callback != null) callback.onSuccess(legacyUsers);
            }

            @Override
            public void onError(String error) {
                if (callback != null) callback.onError(error);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private String valueOrExisting(String value, String fallback) {
        return value == null || value.trim().isEmpty()
                ? (fallback == null ? "" : fallback) : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private String cachedAdminCreatedAt() {
        AdminAccount cached = sessionManager.getCachedAdminAccount();
        return cached == null ? null : cached.getCreatedAt();
    }

    private String stringValue(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }
}
