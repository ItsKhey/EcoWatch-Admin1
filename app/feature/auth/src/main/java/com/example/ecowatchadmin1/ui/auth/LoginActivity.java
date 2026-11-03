package com.example.ecowatchadmin1.ui.auth;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.content.pm.PackageManager;
import androidx.biometric.BiometricManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.BiometricSetting;
import com.example.ecowatchadmin1.data.remote.ApiClient;
import com.example.ecowatchadmin1.data.remote.ServerConfig;
import com.example.ecowatchadmin1.ui.nav.AppNavigation;
import com.example.ecowatchadmin1.utils.LocaleHelper;
import com.example.ecowatchadmin1.utils.ServerHealthCheck;
import com.example.ecowatchadmin1.utils.SessionManager;
import android.widget.ImageView;

import java.util.List;

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput, passwordInput;
    private Button loginBtn, btnFingerprintLogin;
    private View biometricButtonsLayout;
    private LoginViewModel viewModel;
    private SessionManager sessionManager;
    private boolean isBiometricLogin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sessionManager = new SessionManager(this);
        LocaleHelper.applyLocale(this, sessionManager.getLanguage());

        // Force light mode for login screen as requested
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        
        super.onCreate(savedInstanceState);
        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        if (viewModel.isLoggedIn()) {
            startDashboard();
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        emailInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginBtn = findViewById(R.id.loginBtn);

        biometricButtonsLayout = findViewById(R.id.biometricButtonsLayout);
        btnFingerprintLogin = findViewById(R.id.btnFingerprintLogin);

        sessionManager.syncBiometricsFromDb(null, () -> {
            runOnUiThread(this::updateBiometricButtonVisibility);
        });

        loginBtn.setOnClickListener(v -> {
            isBiometricLogin = false;
            String identifier = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            if (identifier.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter credentials", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show();
            viewModel.login(identifier, password);
        });

        btnFingerprintLogin.setOnClickListener(v -> handleBiometricLogin("fingerprint"));

        viewModel.getError().observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getLoading().observe(this, loading -> {
            loginBtn.setEnabled(!Boolean.TRUE.equals(loading));
        });

        viewModel.getLoginSuccess().observe(this, user -> {
            if (user != null) {
                Toast.makeText(this, "Logged in Successfully", Toast.LENGTH_SHORT).show();
                
                String officialEmail = user.getEmail();
                String officialUsername = user.getUsername();
                String officialId = user.getServerId();
                String password = passwordInput.getText().toString().trim();

                // Always save credentials to ensure session is updated correctly
                sessionManager.saveAdminCredentials(officialId, officialEmail, officialUsername, password);
                
                sessionManager.syncBiometricsFromDb(officialId, () -> {
                    sessionManager.saveResidentId(officialId);
                    // Bind this device to the administrator so NEW_REPORT pushes
                    // are delivered. Identical to the Resident app's login hook.
                    com.example.ecowatchadmin1.notifications.FcmTokenSyncScheduler
                            .syncCurrentToken(this);
                    runOnUiThread(this::startDashboard);
                });
            }
        });

        TextView toRegisterBtn = findViewById(R.id.toRegisterBtn);
        // Disable registration for Admin App - only one admin allowed
        toRegisterBtn.setVisibility(View.GONE);
        toRegisterBtn.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        TextView forgotPasswordBtn = findViewById(R.id.forgotPasswordBtn);
        forgotPasswordBtn.setOnClickListener(v -> showForgotPasswordFragment());

        TextView serverSettingsBtn = findViewById(R.id.serverSettingsBtn);
        if (serverSettingsBtn != null) {
            serverSettingsBtn.setOnClickListener(v -> showServerSettings());
        }
    }

    /**
     * Lets the server address be corrected without rebuilding the app.
     *
     * <p>The address is compiled in at build time, which is right for normal use
     * but wrong the moment this machine's DHCP address changes. Because that is the
     * single most likely reason a working install stops working, it is fixable
     * from here rather than only from a build.
     */
    private void showServerSettings() {
        View content = getLayoutInflater().inflate(R.layout.dialog_server_settings, null);
        EditText input = content.findViewById(R.id.serverAddressInput);
        TextView status = content.findViewById(R.id.serverStatusText);
        input.setText(ServerConfig.editableAddress(this));

        View findBtn = content.findViewById(R.id.findServerBtn);
        TextView scanStatus = content.findViewById(R.id.scanStatusText);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Server settings")
                .setView(content)
                .setPositiveButton("Save", null)
                .setNeutralButton("Reset", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(shown -> {
            // Set the listener after show(), otherwise the default button
            // handler dismisses the dialog before the address is applied.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String typed = input.getText().toString().trim();

                if (typed.isEmpty()) {
                    applyServerChange(null, dialog, "Using the address this app was built with.");
                    return;
                }

                ServerHealthCheck.probe(this, typed, result ->
                        runOnUiThread(() -> {
                            if (!result.isReachable()) {
                                // Offer to keep it anyway: a server can be
                                // reachable from the laptop and not from this
                                // phone, and the typed value may be correct.
                                new AlertDialog.Builder(this)
                                        .setTitle("Cannot reach that address")
                                        .setMessage(result.getSummary()
                                                + "\n\nSave it anyway?")
                                        .setNegativeButton("Cancel", null)
                                        .setPositiveButton("Save anyway", (d2, w2) ->
                                                applyServerChange(typed, dialog, null))
                                        .show();
                                return;
                            }
                            applyServerChange(typed, dialog, result.getSummary());
                        }));
            });

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    applyServerChange(null, dialog, "Using the address this app was built with."));

            if (findBtn != null) {
                findBtn.setOnClickListener(v -> scanForServer(findBtn, scanStatus, input, status));
            }
        });

        dialog.show();
    }

    /**
     * Searches the local network for a running EcoWatch server and offers what it
     * finds.
     *
     * <p>The reason this exists: the development machine's address is assigned by
     * whichever network the laptop is on, so it changes every time the laptop joins
     * a different WiFi. Reading it off the laptop is possible, but awkward when you
     * are holding the phone, which is exactly the situation at a demonstration.
     */
    private void scanForServer(View findBtn, TextView scanStatus, EditText input, TextView status) {
        findBtn.setEnabled(false);
        scanStatus.setVisibility(View.VISIBLE);
        scanStatus.setText("Searching this network...");

        final int port = ServerConfig.portOf(ServerConfig.editableAddress(this));

        ServerHealthCheck.scanLocalNetwork(port, found ->
                runOnUiThread(() -> {
                    findBtn.setEnabled(true);

                    if (found.isEmpty()) {
                        scanStatus.setText("No EcoWatch server found. Check the server is "
                                + "running, and that this phone is on the same network as the "
                                + "laptop.");
                        return;
                    }

                    if (found.size() == 1) {
                        // The common case, and unambiguous: just take it.
                        input.setText(found.get(0));
                        scanStatus.setText("Found " + found.get(0) + ". Tap Save to use it.");
                        status.setText("");
                        return;
                    }

                    // More than one is genuinely ambiguous, so let the resident
                    // pick rather than guessing which machine is the laptop.
                    scanStatus.setText("Found " + found.size() + ". Choose one:");
                    final String[] options = found.toArray(new String[0]);
                    new AlertDialog.Builder(this)
                            .setTitle("Which server?")
                            .setItems(options, (d, which) -> {
                                input.setText(options[which]);
                                scanStatus.setText("Selected " + options[which]
                                        + ". Tap Save to use it.");
                            })
                            .show();
                }));
    }

    /**
     * Stores a new address and discards the cached HTTP client so the next request
     * uses it. Passing null clears any override and returns to the build default.
     */
    private void applyServerChange(String address, AlertDialog dialog, String message) {
        if (address == null) {
            ServerConfig.clearOverride(this);
        } else {
            String problem = ServerConfig.setOverride(this, address);
            if (problem != null) {
                showMessage("Invalid address", problem);
                return;
            }
        }

        ApiClient.reset();
        dialog.dismiss();
        showMessage("Server updated", message == null ? "Server address saved." : message);
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void handleBiometricLogin(String type) {
        // 1. Hardware check
        boolean hasSensor = getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);
        if (!hasSensor) {
            showMessage("Fingerprint login isn't available",
                    "This device has no fingerprint sensor. Please log in with your password.");
            return;
        }

        // 2. Enrollment / availability check
        BiometricManager bm = BiometricManager.from(this);
        int result = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);

        switch (result) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                break;

            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                showMessage("No fingerprint set up",
                        "Add a fingerprint in Settings > Security > Fingerprint, then try again.");
                return;

            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                showMessage("Sensor unavailable",
                        "The fingerprint sensor is busy or unavailable right now. Try again later or use your password.");
                return;

            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
            default:
                showMessage("Fingerprint login isn't available",
                        "This device doesn't support fingerprint login. Please use your password.");
                return;
        }

        sessionManager.getAllEnabledBiometrics(settings -> {
            runOnUiThread(() -> {
                if (settings == null || settings.isEmpty()) {
                    Toast.makeText(this, "No accounts have fingerprint login enabled. Please use your password.", Toast.LENGTH_LONG).show();
                } else {
                    java.util.List<BiometricSetting> filtered = new java.util.ArrayList<>();
                    for (BiometricSetting s : settings) {
                        if (s.isFingerprintEnabled()) filtered.add(s);
                    }

                    if (filtered.isEmpty()) {
                        Toast.makeText(this, "No accounts have fingerprint recognition enabled.", Toast.LENGTH_LONG).show();
                    } else {
                        showAccountSelectionDialog(filtered, "fingerprint");
                    }
                }
            });
        });
    }

    private void showForgotPasswordFragment() {
        findViewById(R.id.loginLayout).setVisibility(View.GONE);
        findViewById(R.id.fragmentContainer).setVisibility(View.VISIBLE);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new ForgotPasswordFragment())
                .commit();
    }

    private void showAccountSelectionDialog(List<BiometricSetting> settings, String type) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_account_selection, null);
        RecyclerView rvAccounts = dialogView.findViewById(R.id.rvAccounts);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        rvAccounts.setLayoutManager(new LinearLayoutManager(this));
        rvAccounts.setAdapter(new AccountSelectionAdapter(settings, setting -> {
            dialog.dismiss();
            showBiometricPrompt(setting.getResidentId(), setting.getSavedUsername(), setting.getSavedPassword(), type);
        }));

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private static class AccountSelectionAdapter extends RecyclerView.Adapter<AccountSelectionAdapter.ViewHolder> {
        private final List<BiometricSetting> settings;
        private final OnAccountSelectedListener listener;

        interface OnAccountSelectedListener {
            void onAccountSelected(BiometricSetting setting);
        }

        AccountSelectionAdapter(List<BiometricSetting> settings, OnAccountSelectedListener listener) {
            this.settings = settings;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account_selection, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BiometricSetting setting = settings.get(position);
            holder.tvUsername.setText(setting.getSavedUsername());
            holder.itemView.setOnClickListener(v -> listener.onAccountSelected(setting));
        }

        @Override
        public int getItemCount() {
            return settings.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvUsername;
            ImageView ivProfilePicture;

            ViewHolder(View itemView) {
                super(itemView);

                tvUsername = itemView.findViewById(R.id.tvEmail);
                ivProfilePicture = itemView.findViewById(R.id.ivProfilePicture);
            }
        }
    }

    private void showBiometricPrompt(String residentId, String username, String password, String type) {
        String title = "Fingerprint Login";
        
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Log in as " + username)
                .setNegativeButtonText("Use Password")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                            Toast.makeText(LoginActivity.this, "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        isBiometricLogin = true;
                        
                        emailInput.setText(username);
                        passwordInput.setText(password);
                        
                        Toast.makeText(LoginActivity.this, "Fingerprint Authentication Succeeded!", Toast.LENGTH_SHORT).show();
                        
                        sessionManager.syncBiometricsFromDb(residentId, () -> {
                            runOnUiThread(() -> viewModel.login(username, password));
                        });
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(LoginActivity.this, "Authentication failed. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                });

        biometricPrompt.authenticate(promptInfo);
    }

    private void startDashboard() {
        startActivity(AppNavigation.dashboardIntent(LoginActivity.this));
        finish();
    }

    private void updateBiometricButtonVisibility() {
        boolean hasSensor = getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);
        if (!hasSensor) {
            biometricButtonsLayout.setVisibility(View.GONE);
            return;
        }

        sessionManager.getAllEnabledBiometrics(settings -> {
            runOnUiThread(() -> {
                if (settings != null && !settings.isEmpty()) {
                    boolean hasFingerprint = false;
                    for (BiometricSetting s : settings) {
                        if (s.isFingerprintEnabled()) hasFingerprint = true;
                    }
                    
                    biometricButtonsLayout.setVisibility(hasFingerprint ? View.VISIBLE : View.GONE);
                    btnFingerprintLogin.setVisibility(hasFingerprint ? View.VISIBLE : View.GONE);
                } else {
                    biometricButtonsLayout.setVisibility(View.GONE);
                }
            });
        });
    }
}
