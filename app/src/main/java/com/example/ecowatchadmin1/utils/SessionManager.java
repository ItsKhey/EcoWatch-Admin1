package com.example.ecowatchadmin1.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import com.example.ecowatchadmin1.data.local.AppDatabase;
import com.example.ecowatchadmin1.data.local.BiometricDao;
import com.example.ecowatchadmin1.data.models.AdminAccount;
import com.example.ecowatchadmin1.data.models.BiometricSetting;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionManager {
    private static final String PREF_NAME = "EcoWatchSession";
    private static final String KEY_JWT_TOKEN = "jwt_token";
    /**
     * Legacy key retained for resident/report compatibility. The admin
     * account screen must use KEY_ADMIN_ID instead.
     */
    private static final String KEY_RESIDENT_ID = "resident_id";
    private static final String KEY_ADMIN_ID = "admin_id";
    private static final String KEY_ADMIN_ROLE = "admin_account_role";
    private static final String KEY_ADMIN_CREATED_AT = "admin_account_created_at";
    private static final String KEY_ADMIN_LAST_ACTIVE = "admin_account_last_active";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_LAST_ACTIVE = "last_active";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_PASSWORD = "user_password";
    private static final String KEY_USER_USERNAME = "user_username";
    private static final String KEY_LAST_USED_RESIDENT_ID = "last_used_resident_id";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_LANGUAGE = "app_language";
    private static final long SESSION_TIMEOUT = 30L * 24 * 60 * 60 * 1000; // 30 days

    private SharedPreferences sharedPreferences;
    private BiometricDao biometricDao;
    private ExecutorService executorService;
    private Handler mainHandler;

    public SessionManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        biometricDao = AppDatabase.getInstance(context).biometricDao();
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void saveAuthToken(String token) {
        sharedPreferences.edit()
                .putString(KEY_JWT_TOKEN, token)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putLong(KEY_LAST_ACTIVE, System.currentTimeMillis())
                .apply();
    }

    public void saveResidentId(String residentId) {
        sharedPreferences.edit()
                .putString(KEY_RESIDENT_ID, residentId)
                .putString(KEY_LAST_USED_RESIDENT_ID, residentId)
                .apply();
    }

    /**
     * Stores the canonical administrator ID returned by the authenticated
     * admin API. This is intentionally not an alias for the legacy resident
     * ID preference.
     */
    public void saveAdminId(String adminId) {
        if (adminId == null || adminId.trim().isEmpty()) {
            return;
        }
        sharedPreferences.edit().putString(KEY_ADMIN_ID, adminId.trim()).apply();
    }

    public String fetchAdminId() {
        String storedId = sharedPreferences.getString(KEY_ADMIN_ID, null);

        // Prefer the current token so a stale preference from a previous login
        // can never be displayed as the active administrator.
        String jwtAdminId = getAdminIdFromJwt(fetchAuthToken());
        if (jwtAdminId != null && !jwtAdminId.trim().isEmpty()) {
            if (!jwtAdminId.equals(storedId)) {
                saveAdminId(jwtAdminId);
            }
            return jwtAdminId;
        }
        return storedId != null && !storedId.trim().isEmpty() ? storedId : null;
    }

    /** Java-friendly alias for callers that use getter naming. */
    public String getAdminId() {
        return fetchAdminId();
    }

    public String getAdminIdFromJwt(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        String[] parts = token.trim().split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] decoded = Base64.decode(parts[1], Base64.URL_SAFE
                    | Base64.NO_WRAP | Base64.NO_PADDING);
            JSONObject claims = new JSONObject(new String(decoded, "UTF-8"));
            String role = claims.optString("role", "");
            String id = claims.optString("id", "");
            if (id.trim().isEmpty()) {
                id = claims.optString("admin_id", "");
            }
            if (id.trim().isEmpty()) {
                return null;
            }
            if (!id.regionMatches(true, 0, "ADM-", 0, 4)) {
                return null;
            }
            if (!role.isEmpty()
                    && !"ADMIN".equalsIgnoreCase(role)
                    && !"ADMINISTRATOR".equalsIgnoreCase(role)) {
                return null;
            }
            return id.trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    public void saveAdminAccount(AdminAccount account) {
        if (account == null) {
            return;
        }
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (account.getAdminId() != null && !account.getAdminId().trim().isEmpty()) {
            editor.putString(KEY_ADMIN_ID, account.getAdminId().trim());
        } else {
            editor.remove(KEY_ADMIN_ID);
        }
        editor.putString(KEY_ADMIN_ROLE, account.getRole());
        editor.putString(KEY_ADMIN_CREATED_AT, account.getCreatedAt());
        editor.putString(KEY_ADMIN_LAST_ACTIVE, account.getLastActive());
        editor.apply();
    }

    /**
     * Returns only metadata cached from the authenticated admin response. No
     * resident preference is consulted, so legacy sessions cannot populate
     * this screen with a resident ID.
     */
    public AdminAccount getCachedAdminAccount() {
        String adminId = fetchAdminId();
        if (adminId == null || adminId.trim().isEmpty()) {
            return null;
        }
        AdminAccount account = new AdminAccount(
                adminId,
                sharedPreferences.getString(KEY_ADMIN_ROLE, "ADMIN"),
                sharedPreferences.getString(KEY_ADMIN_CREATED_AT, null),
                sharedPreferences.getString(KEY_ADMIN_LAST_ACTIVE, null)
        );
        return account;
    }

    public String fetchResidentId() {
        return sharedPreferences.getString(KEY_RESIDENT_ID, null);
    }

    public void saveUserRole(String role) {
        sharedPreferences.edit().putString(KEY_USER_ROLE, role).apply();
    }

    public String fetchUserRole() {
        return sharedPreferences.getString(KEY_USER_ROLE, "");
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(fetchUserRole());
    }

    public void updateLastActive() {
        sharedPreferences.edit().putLong(KEY_LAST_ACTIVE, System.currentTimeMillis()).apply();
    }

    public boolean isSessionExpired() {
        if (!isLoggedIn()) return false;
        long lastActive = sharedPreferences.getLong(KEY_LAST_ACTIVE, 0);
        if (lastActive == 0) {
            updateLastActive();
            return false;
        }
        return (System.currentTimeMillis() - lastActive) > SESSION_TIMEOUT;
    }

    public String fetchAuthToken() {
        return sharedPreferences.getString(KEY_JWT_TOKEN, null);
    }

    public boolean hasAuthToken() {
        String token = fetchAuthToken();
        return token != null
                && !token.trim().isEmpty()
                && !token.regionMatches(true, 0, "mock_", 0, 5);
    }

    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
                && hasAuthToken();
    }

    public void clearSession() {
        sharedPreferences.edit()
                .remove(KEY_JWT_TOKEN)
                .remove(KEY_RESIDENT_ID)
                .remove(KEY_ADMIN_ID)
                .remove(KEY_ADMIN_ROLE)
                .remove(KEY_ADMIN_CREATED_AT)
                .remove(KEY_ADMIN_LAST_ACTIVE)
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .remove(KEY_USER_EMAIL)
                .remove(KEY_USER_PASSWORD)
                .remove(KEY_USER_USERNAME)
                .remove(KEY_USER_ROLE)
                .remove(KEY_LAST_ACTIVE)
                .apply();
    }

    public String getLastUsedResidentId() {
        return sharedPreferences.getString(KEY_LAST_USED_RESIDENT_ID, "");
    }

    public void setBiometricEnabled(String residentId, boolean enabled) {
        if (residentId == null || residentId.isEmpty()) return;
        
        executorService.execute(() -> {
            BiometricSetting setting = biometricDao.getSettingByResidentId(residentId);
            if (setting == null) {
                setting = new BiometricSetting(residentId);
            }
            setting.setEnabled(enabled);
            // Also enable both if general is enabled? Or just keep it as legacy
            biometricDao.saveSetting(setting);
        });
    }

    public void setFingerprintEnabled(String residentId, boolean enabled) {
        if (residentId == null || residentId.isEmpty()) return;
        executorService.execute(() -> {
            BiometricSetting setting = biometricDao.getSettingByResidentId(residentId);
            if (setting == null) setting = new BiometricSetting(residentId);
            setting.setFingerprintEnabled(enabled);
            biometricDao.saveSetting(setting);
        });
    }

    public void setFaceIDEnabled(String residentId, boolean enabled) {
        if (residentId == null || residentId.isEmpty()) return;
        executorService.execute(() -> {
            BiometricSetting setting = biometricDao.getSettingByResidentId(residentId);
            if (setting == null) setting = new BiometricSetting(residentId);
            setting.setFaceIDEnabled(enabled);
            biometricDao.saveSetting(setting);
        });
    }

    public void isBiometricEnabledAsync(String residentId, BiometricCheckCallback callback) {
        if (residentId == null || residentId.isEmpty()) {
            callback.onResult(false);
            return;
        }

        executorService.execute(() -> {
            BiometricSetting setting = biometricDao.getSettingByResidentId(residentId);
            boolean enabled = setting != null && (setting.isEnabled() || setting.isFingerprintEnabled() || setting.isFaceIDEnabled());
            mainHandler.post(() -> callback.onResult(enabled));
        });
    }

    public void getBiometricSettingsAsync(String residentId, BiometricSettingsCallback callback) {
        if (residentId == null || residentId.isEmpty()) {
            callback.onResult(null);
            return;
        }
        executorService.execute(() -> {
            BiometricSetting setting = biometricDao.getSettingByResidentId(residentId);
            mainHandler.post(() -> callback.onResult(setting));
        });
    }

    public interface BiometricSettingsCallback {
        void onResult(BiometricSetting setting);
    }

    public void hasAnyEnabledBiometricsAsync(BiometricCheckCallback callback) {
        executorService.execute(() -> {
            java.util.List<BiometricSetting> enabled = biometricDao.getAllEnabledSettings();
            boolean exists = enabled != null && !enabled.isEmpty();
            mainHandler.post(() -> callback.onResult(exists));
        });
    }

    public interface BiometricCheckCallback {
        void onResult(boolean enabled);
    }

    /**
     * Stores credentials for the admin session while retaining the legacy
     * resident-key write required by older biometric/report features.
     */
    public void saveAdminCredentials(String adminId, String email, String username, String password) {
        saveAdminId(adminId);
        saveUserCredentials(adminId, email, username, password);
    }

    public void saveUserCredentials(String residentId, String email, String username, String password) {
        sharedPreferences.edit()
                .putString(KEY_RESIDENT_ID, residentId)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_USERNAME, username)
                .putString(KEY_USER_PASSWORD, password)
                .putString(KEY_LAST_USED_RESIDENT_ID, residentId)
                .apply();

        executorService.execute(() -> {
            BiometricSetting setting = biometricDao.getSettingByResidentId(residentId);
            if (setting == null) {
                setting = new BiometricSetting(residentId);
            }
            setting.setSavedEmail(email);
            setting.setSavedUsername(username);
            setting.setSavedPassword(password);
            biometricDao.saveSetting(setting);
        });
    }

    public String fetchUserEmail() {
        return sharedPreferences.getString(KEY_USER_EMAIL, "");
    }

    public String fetchUserUsername() {
        return sharedPreferences.getString(KEY_USER_USERNAME, "");
    }

    public String fetchUserPassword() {
        return sharedPreferences.getString(KEY_USER_PASSWORD, "");
    }

    public void setThemeMode(int mode) {
        sharedPreferences.edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    public void setNotificationsEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean("notifications_enabled", enabled).apply();
    }

    public boolean areNotificationsEnabled() {
        return sharedPreferences.getBoolean("notifications_enabled", false);
    }

    public int getThemeMode() {
        // Default to -1 (AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        return sharedPreferences.getInt(KEY_THEME_MODE, -1);
    }

    public void setLanguage(String language) {
        sharedPreferences.edit().putString(KEY_LANGUAGE, language).apply();
    }

    public String getLanguage() {
        return sharedPreferences.getString(KEY_LANGUAGE, "en"); // Default to English
    }

    public void syncBiometricsFromDb(String residentId, Runnable onComplete) {
        final String targetId = (residentId != null && !residentId.isEmpty()) ? residentId : fetchResidentId();
        
        if (targetId == null || targetId.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        executorService.execute(() -> {
            BiometricSetting setting = biometricDao.getSettingByResidentId(targetId);
            if (setting != null) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                
                String currentId = fetchResidentId();
                // Sync to session if it's the same user OR if we don't have a user session yet (login/startup)
                if (currentId == null || currentId.isEmpty() || targetId.equalsIgnoreCase(currentId)) {
                    if (setting.getSavedEmail() != null) editor.putString(KEY_USER_EMAIL, setting.getSavedEmail());
                    if (setting.getSavedUsername() != null) editor.putString(KEY_USER_USERNAME, setting.getSavedUsername());
                    if (setting.getSavedPassword() != null) editor.putString(KEY_USER_PASSWORD, setting.getSavedPassword());
                }
                
                editor.putString(KEY_LAST_USED_RESIDENT_ID, targetId);
                editor.apply();
            }
            if (onComplete != null) onComplete.run();
        });
    }

    public void getAllEnabledBiometrics(BiometricListCallback callback) {
        executorService.execute(() -> {
            java.util.List<BiometricSetting> settings = biometricDao.getAllEnabledSettings();
            if (callback != null) {
                callback.onResult(settings);
            }
        });
    }

    public interface BiometricListCallback {
        void onResult(java.util.List<BiometricSetting> settings);
    }
}
