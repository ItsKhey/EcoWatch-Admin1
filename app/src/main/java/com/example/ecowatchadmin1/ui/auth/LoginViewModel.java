package com.example.ecowatchadmin1.ui.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ecowatchadmin1.data.models.User;
import com.example.ecowatchadmin1.data.repository.AuthRepository;

public class LoginViewModel extends AndroidViewModel {

    private AuthRepository repository;
    private MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private MutableLiveData<User> loginSuccessLiveData = new MutableLiveData<>();
    private MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);

    public LoginViewModel(@NonNull Application application) {
        super(application);
        repository = new AuthRepository(application);
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public LiveData<User> getLoginSuccess() {
        return loginSuccessLiveData;
    }

    public LiveData<Boolean> getLoading() {
        return loadingLiveData;
    }

    public void login(String username, String password) {
        if (username == null || password == null
                || username.trim().isEmpty() || password.isEmpty()) {
            loadingLiveData.setValue(false);
            errorLiveData.setValue("Username and password are required.");
            return;
        }

        errorLiveData.setValue(null);
        loadingLiveData.setValue(true);
        repository.login(username.trim(), password, new AuthRepository.ApiCallback<User>() {
            @Override
            public void onSuccess(User result) {
                loadingLiveData.setValue(false);
                loginSuccessLiveData.setValue(result);
            }

            @Override
            public void onError(String error) {
                loadingLiveData.setValue(false);
                errorLiveData.setValue(error);
            }
        });
    }

    public boolean isLoggedIn() {
        return repository.isLoggedIn();
    }
}
