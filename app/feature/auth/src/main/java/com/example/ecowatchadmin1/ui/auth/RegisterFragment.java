package com.example.ecowatchadmin1.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.ecowatchadmin1.R;

public class RegisterFragment extends Fragment {

    private EditText regFirstNameInput, regLastNameInput, regUsernameInput, regEmailInput, regPasswordInput, regConfirmPasswordInput;
    private EditText verificationCodeInput;
    private Button registerBtn, verifyBtn;
    private LinearLayout inputContainer, verificationContainer;
    private RegisterViewModel viewModel;
    private String currentRegistrationId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_register, container, false);

        viewModel = new ViewModelProvider(this).get(RegisterViewModel.class);

        inputContainer = view.findViewById(R.id.inputContainer);
        verificationContainer = view.findViewById(R.id.verificationContainer);

        regFirstNameInput = view.findViewById(R.id.regFirstNameInput);
        regLastNameInput = view.findViewById(R.id.regLastNameInput);
        regUsernameInput = view.findViewById(R.id.regUsernameInput);
        regEmailInput = view.findViewById(R.id.regEmailInput);
        regPasswordInput = view.findViewById(R.id.regPasswordInput);
        regConfirmPasswordInput = view.findViewById(R.id.regConfirmPasswordInput);
        registerBtn = view.findViewById(R.id.registerBtn);

        verificationCodeInput = view.findViewById(R.id.verificationCodeInput);
        verifyBtn = view.findViewById(R.id.verifyBtn);

        TextView toLoginBtn = view.findViewById(R.id.toLoginBtn);

        registerBtn.setOnClickListener(v -> {
            String fName = regFirstNameInput.getText().toString().trim();
            String lName = regLastNameInput.getText().toString().trim();
            String uName = regUsernameInput.getText().toString().trim();
            String email = regEmailInput.getText().toString().trim();
            String pass = regPasswordInput.getText().toString().trim();
            String confirmPass = regConfirmPasswordInput.getText().toString().trim();

            viewModel.register(fName, lName, uName, email, pass, confirmPass);
        });

        verifyBtn.setOnClickListener(v -> {
            String otp = verificationCodeInput.getText().toString().trim();
            viewModel.verifyOTP(currentRegistrationId, otp);
        });

        toLoginBtn.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        viewModel.getRegistrationId().observe(getViewLifecycleOwner(), regId -> {
            if (regId != null) {
                currentRegistrationId = regId;
                inputContainer.setVisibility(View.GONE);
                verificationContainer.setVisibility(View.VISIBLE);
                Toast.makeText(getContext(), "OTP sent to your email", Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getSuccess().observe(getViewLifecycleOwner(), success -> {
            if (success) {
                Toast.makeText(getContext(), "Account verified! Please login.", Toast.LENGTH_SHORT).show();
                if (getActivity() != null) {
                    getActivity().finish();
                }
            }
        });

        return view;
    }
}
