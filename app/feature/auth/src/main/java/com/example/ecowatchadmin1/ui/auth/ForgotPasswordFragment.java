package com.example.ecowatchadmin1.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.repository.AuthRepository;
import com.google.android.material.button.MaterialButton;

public class ForgotPasswordFragment extends Fragment {

    private AuthRepository authRepository;
    private LinearLayout methodContainer, codeContainer, resetContainer;
    private EditText usernameInput, codeInput, newPasswordInput, confirmPasswordInput;
    private MaterialButton sendCodeBtn, verifyCodeBtn, resetPasswordBtn;
    private ProgressBar progressBar;
    private TextView backToLoginBtn;
    
    private String currentUsername;
    private String currentOTP;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_forgot_password, container, false);
        authRepository = new AuthRepository(requireActivity().getApplication());
        
        initViews(view);
        setupListeners();
        
        return view;
    }

    private void initViews(View view) {
        methodContainer = view.findViewById(R.id.methodContainer);
        codeContainer = view.findViewById(R.id.codeContainer);
        resetContainer = view.findViewById(R.id.resetContainer);
        
        usernameInput = view.findViewById(R.id.forgotUsernameInput);
        codeInput = view.findViewById(R.id.forgotCodeInput);
        newPasswordInput = view.findViewById(R.id.newPasswordInput);
        confirmPasswordInput = view.findViewById(R.id.confirmNewPasswordInput);
        
        sendCodeBtn = view.findViewById(R.id.sendCodeBtn);
        verifyCodeBtn = view.findViewById(R.id.verifyCodeBtn);
        resetPasswordBtn = view.findViewById(R.id.resetPasswordBtn);
        
        progressBar = view.findViewById(R.id.forgotProgressBar);
        backToLoginBtn = view.findViewById(R.id.backToLoginBtn);

        // Set hint to Username as per requirement
        usernameInput.setHint("Username");
    }

    private void setupListeners() {
        sendCodeBtn.setOnClickListener(v -> sendOTP());
        verifyCodeBtn.setOnClickListener(v -> verifyOTP());
        resetPasswordBtn.setOnClickListener(v -> resetPassword());
        backToLoginBtn.setOnClickListener(v -> closeFragment());
    }

    private void sendOTP() {
        currentUsername = usernameInput.getText().toString().trim();
        if (currentUsername.isEmpty()) {
            Toast.makeText(getContext(), "Please enter your username", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        authRepository.forgotPassword(currentUsername, new AuthRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                setLoading(false);
                methodContainer.setVisibility(View.GONE);
                codeContainer.setVisibility(View.VISIBLE);
                Toast.makeText(getContext(), "Reset code sent to your registered email", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                setLoading(false);
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void verifyOTP() {
        currentOTP = codeInput.getText().toString().trim();
        if (currentOTP.isEmpty() || currentOTP.length() < 4) {
            Toast.makeText(getContext(), "Please enter the 4-digit code", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        authRepository.verifyResetOTP(currentUsername, currentOTP, new AuthRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                setLoading(false);
                codeContainer.setVisibility(View.GONE);
                resetContainer.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String error) {
                setLoading(false);
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resetPassword() {
        String newPass = newPasswordInput.getText().toString().trim();
        String confirmPass = confirmPasswordInput.getText().toString().trim();

        if (newPass.isEmpty() || confirmPass.isEmpty()) {
            Toast.makeText(getContext(), "Please enter and confirm your new password", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPass.equals(confirmPass)) {
            Toast.makeText(getContext(), "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        authRepository.resetPassword(currentUsername, currentOTP, newPass, new AuthRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                setLoading(false);
                Toast.makeText(getContext(), "Password reset successfully! You can now login.", Toast.LENGTH_LONG).show();
                closeFragment();
            }

            @Override
            public void onError(String error) {
                setLoading(false);
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        sendCodeBtn.setEnabled(!isLoading);
        verifyCodeBtn.setEnabled(!isLoading);
        resetPasswordBtn.setEnabled(!isLoading);
    }

    private void closeFragment() {
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .remove(this)
                    .commit();
            
            // If in LoginActivity, we might need to show the login layout again
            View loginLayout = getActivity().findViewById(R.id.loginLayout);
            View fragmentContainer = getActivity().findViewById(R.id.fragmentContainer);
            if (loginLayout != null && fragmentContainer != null) {
                loginLayout.setVisibility(View.VISIBLE);
                fragmentContainer.setVisibility(View.GONE);
            } else {
                // If in Dashboard/Profile, just pop back
                getParentFragmentManager().popBackStack();
            }
        }
    }
}
