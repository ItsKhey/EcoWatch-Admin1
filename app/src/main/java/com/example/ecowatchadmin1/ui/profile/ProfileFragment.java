package com.example.ecowatchadmin1.ui.profile;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.AdminAccount;
import com.example.ecowatchadmin1.data.models.User;
import com.example.ecowatchadmin1.data.repository.AuthRepository;
import com.example.ecowatchadmin1.utils.AdminAccountDateFormatter;
import com.example.ecowatchadmin1.data.remote.ServerConfig;
import com.example.ecowatchadmin1.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.yalantis.ucrop.UCrop;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class ProfileFragment extends Fragment {

    private AuthRepository authRepository;
    private SessionManager sessionManager;
    private ProfileViewModel profileViewModel;
    private ImageView profileImage;
    private TextView profileName, profileSubtitle, profileUsername, profileEmail, profileAddress;
    private TextView adminIdValue, adminRoleValue, adminCreatedValue, adminLastActiveValue;
    private MaterialButton editProfileBtn;
    private MaterialButton copyAdminIdButton;
    private SwipeRefreshLayout swipeRefreshLayout;
    private User currentUser;
    private String currentAdminId;

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    startCrop(imageUri);
                }
            }
    );

    private final ActivityResultLauncher<Intent> cropImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri resultUri = UCrop.getOutput(result.getData());
                    if (resultUri != null) {
                        uploadProfileImage(resultUri);
                    }
                } else if (result.getResultCode() == UCrop.RESULT_ERROR) {
                    Throwable cropError = UCrop.getError(result.getData());
                    if (cropError != null) {
                        Toast.makeText(getContext(), cropError.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        currentAdminId = null;

        authRepository = new AuthRepository(requireActivity().getApplication());
        sessionManager = new SessionManager(requireContext().getApplicationContext());
        profileViewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        profileImage = view.findViewById(R.id.profileImage);
        profileName = view.findViewById(R.id.profileName);
        profileSubtitle = view.findViewById(R.id.profileSubtitle);
        profileUsername = view.findViewById(R.id.profileUsername);
        profileEmail = view.findViewById(R.id.profileEmail);
        profileAddress = view.findViewById(R.id.profileAddress);
        adminIdValue = view.findViewById(R.id.adminIdValue);
        adminRoleValue = view.findViewById(R.id.adminRoleValue);
        adminCreatedValue = view.findViewById(R.id.adminCreatedValue);
        adminLastActiveValue = view.findViewById(R.id.adminLastActiveValue);
        editProfileBtn = view.findViewById(R.id.editProfileBtn);
        copyAdminIdButton = view.findViewById(R.id.copyAdminIdButton);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        View backButton = view.findViewById(R.id.btnBack);
        if (backButton != null) {
            backButton.setOnClickListener(v -> getParentFragmentManager().popBackStack());
        }

        View editImageBtn = view.findViewById(R.id.editProfileImageBtn);
        View.OnClickListener pickImageListener = v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        };

        // Profile image editing remains available; the account rows in the
        // unified card stay read-only.
        profileImage.setOnClickListener(pickImageListener);
        if (editImageBtn != null) {
            editImageBtn.setOnClickListener(pickImageListener);
        }

        copyAdminIdButton.setOnClickListener(v -> copyAdminId());

        swipeRefreshLayout.setOnRefreshListener(() -> {
            profileViewModel.refresh();
            authRepository.fetchMe(new AuthRepository.ApiCallback<User>() {
                @Override
                public void onSuccess(User result) {
                    swipeRefreshLayout.setRefreshing(false);
                }

                @Override
                public void onError(String error) {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(getContext(), "Sync failed: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        });

        profileViewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            if (!Boolean.TRUE.equals(loading)) {
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        profileViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.trim().isEmpty() && isAdded()) {
                Toast.makeText(getContext(), getString(R.string.account_details_load_failed),
                        Toast.LENGTH_SHORT).show();
            }
        });

        profileViewModel.getAccount().observe(getViewLifecycleOwner(), this::renderAccountDetails);

        authRepository.getUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                currentUser = user;
                String fullName = joinName(user.getFirstName(), user.getLastName());
                profileName.setText(fullName);
                profileUsername.setText(isBlank(user.getUsername())
                        ? getString(R.string.account_value_unavailable) : user.getUsername());
                profileEmail.setText(isBlank(user.getEmail())
                        ? getString(R.string.account_value_unavailable) : user.getEmail());
                profileAddress.setText(isBlank(user.getAddress())
                        ? getString(R.string.account_value_unavailable) : user.getAddress());

                if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
                    Glide.with(this)
                            .load(ServerConfig.absoluteUrl(requireContext(), user.getProfileImageUrl()))
                            .placeholder(R.drawable.pfp)
                            .into(profileImage);
                } else {
                    profileImage.setImageResource(R.drawable.pfp);
                }
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        editProfileBtn.setOnClickListener(v -> showEditProfileDialog());
        profileViewModel.refresh();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (profileViewModel != null) {
            profileViewModel.refresh();
        }
    }

    private void renderAccountDetails(AdminAccount account) {
        if (account == null) {
            return;
        }

        String adminId = account.getAdminId();
        if (adminId == null || adminId.trim().isEmpty()) {
            adminId = sessionManager.fetchAdminId();
        }
        boolean hasAdminId = adminId != null && !adminId.trim().isEmpty();
        currentAdminId = hasAdminId ? adminId : null;
        adminIdValue.setText(hasAdminId
                ? adminId : getString(R.string.account_value_unavailable));

        // Keep the role row as a green Administrator badge rather than exposing
        // the raw API enum in the compact profile layout.
        profileSubtitle.setText(getString(R.string.administrator_subtitle));
        adminRoleValue.setText(getString(R.string.administrator_badge));

        if (!isBlank(account.getUsername())) {
            profileUsername.setText(account.getUsername());
        }
        if (!isBlank(account.getEmail())) {
            profileEmail.setText(account.getEmail());
        }
        if (!isBlank(account.getAddress())) {
            profileAddress.setText(account.getAddress());
        }
        if (!isBlank(account.getFirstName()) || !isBlank(account.getLastName())) {
            profileName.setText(joinName(account.getFirstName(), account.getLastName()));
        }

        String created = AdminAccountDateFormatter.format(account.getCreatedAt());
        String lastActive = AdminAccountDateFormatter.format(account.getLastActive());
        adminCreatedValue.setText(created == null
                ? getString(R.string.account_value_unavailable) : created);
        adminLastActiveValue.setText(lastActive == null
                ? getString(R.string.account_value_unavailable) : lastActive);

        copyAdminIdButton.setEnabled(hasAdminId);
    }

    private void copyAdminId() {
        String adminId = currentAdminId;
        if (adminId == null || adminId.trim().isEmpty()) {
            adminId = sessionManager.fetchAdminId();
        }
        if (adminId == null || adminId.trim().isEmpty() || !isAdded()) {
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Admin ID", adminId));
        Toast.makeText(getContext(), R.string.admin_id_copied, Toast.LENGTH_SHORT).show();
    }

    private String joinName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String fullName = (first + " " + last).trim();
        if ("cenro".equalsIgnoreCase(first)
                && (last.isEmpty() || "admin".equalsIgnoreCase(last)
                || "administrator".equalsIgnoreCase(last))) {
            return getString(R.string.profile_name_default);
        }
        return fullName.isEmpty() ? getString(R.string.profile_name_default) : fullName;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void showEditProfileDialog() {
        if (currentUser == null) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_profile, null);
        TextInputEditText editFirstName = dialogView.findViewById(R.id.editFirstName);
        TextInputEditText editLastName = dialogView.findViewById(R.id.editLastName);
        TextInputEditText editUsername = dialogView.findViewById(R.id.editUsername);
        TextInputEditText editEmail = dialogView.findViewById(R.id.editEmail);
        TextInputEditText editAddress = dialogView.findViewById(R.id.editAddress);

        // Pre-fill
        editFirstName.setText(currentUser.getFirstName());
        editLastName.setText(currentUser.getLastName());
        editUsername.setText(currentUser.getUsername());
        editEmail.setText(currentUser.getEmail());
        editAddress.setText(currentUser.getAddress());

        new AlertDialog.Builder(requireContext())
                .setTitle("Update Profile")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String fName = editFirstName.getText().toString().trim();
                    String lName = editLastName.getText().toString().trim();
                    String username = editUsername.getText().toString().trim();
                    String email = editEmail.getText().toString().trim();
                    String address = editAddress.getText().toString().trim();

                    if (fName.isEmpty() || lName.isEmpty() || username.isEmpty() || email.isEmpty()) {
                        Toast.makeText(getContext(), "Name, Username and Email are required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    authRepository.updateProfile(fName, lName, username, email, address, new AuthRepository.ApiCallback<User>() {
                        @Override
                        public void onSuccess(User result) {
                            Toast.makeText(getContext(), "Profile updated", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(String error) {
                            // Detailed error handling to debug "next is not a function"
                            String msg = error.contains("next") ? "Server configuration error. Please contact admin." : error;
                            Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startCrop(Uri sourceUri) {
        String destinationFileName = "cropped_pfp.jpg";
        UCrop uCrop = UCrop.of(sourceUri, Uri.fromFile(new File(requireContext().getCacheDir(), destinationFileName)));
        uCrop.withAspectRatio(1, 1);
        uCrop.withMaxResultSize(400, 400); // Reduced size for better compatibility
        
        UCrop.Options options = new UCrop.Options();
        options.setCircleDimmedLayer(true);
        options.setShowCropGrid(false);
        options.setShowCropFrame(false);
        options.setToolbarTitle("Crop Profile Picture");
        options.setToolbarColor(getResources().getColor(R.color.green, null));
        options.setStatusBarColor(getResources().getColor(R.color.green, null));
        options.setToolbarWidgetColor(getResources().getColor(R.color.white, null));
        options.setActiveControlsWidgetColor(getResources().getColor(R.color.green, null));
        
        uCrop.withOptions(options);

        cropImageLauncher.launch(uCrop.getIntent(requireContext()));
    }

    private void uploadProfileImage(Uri uri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), uri);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
            byte[] imageBytes = baos.toByteArray();

            Toast.makeText(getContext(), "Uploading...", Toast.LENGTH_SHORT).show();
            authRepository.updateProfileImage(imageBytes, new AuthRepository.ApiCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    Toast.makeText(getContext(), "Image updated successfully", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(getContext(), "Upload error: " + error, Toast.LENGTH_LONG).show();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
        }
    }
}
