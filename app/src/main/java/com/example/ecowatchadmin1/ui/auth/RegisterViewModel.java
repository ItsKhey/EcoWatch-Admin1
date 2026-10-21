package com.example.ecowatchadmin1.ui.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ecowatchadmin1.data.repository.AuthRepository;
import com.example.ecowatchadmin1.utils.UsernameValidator;

public class RegisterViewModel extends AndroidViewModel {

    private AuthRepository repository;
    private MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private MutableLiveData<String> registrationIdLiveData = new MutableLiveData<>();
    private MutableLiveData<Boolean> successLiveData = new MutableLiveData<>();

    public RegisterViewModel(@NonNull Application application) {
        super(application);
        repository = new AuthRepository(application);
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public LiveData<String> getRegistrationId() {
        return registrationIdLiveData;
    }

    public LiveData<Boolean> getSuccess() {
        return successLiveData;
    }

    public void register(String firstName, String lastName, String username, String email, String password, String confirmPassword) {
        if (firstName.isEmpty() || lastName.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            errorLiveData.setValue("All fields are required.");
            return;
        }

        if (!UsernameValidator.isValid(username)) {
            errorLiveData.setValue("Invalid username. Use 3-20 alphanumeric characters.");
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorLiveData.setValue("Invalid email address.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            errorLiveData.setValue("Passwords do not match.");
            return;
        }

        repository.register(firstName, lastName, username, email, password, new AuthRepository.ApiCallback<AuthRepository.RegistrationOutcome>() {
            @Override
            public void onSuccess(AuthRepository.RegistrationOutcome outcome) {
                if (outcome.requiresVerification()) {
                    registrationIdLiveData.setValue(outcome.getRegistrationId());
                } else {
                    // The account already exists, so there is no code to type and
                    // nothing left to wait for.
                    successLiveData.setValue(true);
                }
            }

            @Override
            public void onError(String error) {
                errorLiveData.setValue(error);
            }
        });
    }

    public void verifyOTP(String registrationId, String otp) {
        if (otp.isEmpty()) {
            errorLiveData.setValue("OTP is required.");
            return;
        }

        repository.verifyOTP(registrationId, otp, new AuthRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                successLiveData.setValue(true);
            }

            @Override
            public void onError(String error) {
                errorLiveData.setValue(error);
            }
        });
    }
}
