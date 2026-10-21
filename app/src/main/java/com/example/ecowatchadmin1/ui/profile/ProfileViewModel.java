package com.example.ecowatchadmin1.ui.profile;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ecowatchadmin1.data.models.AdminAccount;
import com.example.ecowatchadmin1.data.repository.AdminRepository;
import com.example.ecowatchadmin1.utils.SessionManager;

/** MVVM state holder for the authenticated administrator account details. */
public class ProfileViewModel extends AndroidViewModel {

    private final AdminRepository repository;
    private final SessionManager sessionManager;
    private final MutableLiveData<AdminAccount> account = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        repository = new AdminRepository(application);
        sessionManager = new SessionManager(application);

        AdminAccount cached = sessionManager.getCachedAdminAccount();
        if (cached != null) {
            account.setValue(cached);
        }
    }

    public LiveData<AdminAccount> getAccount() {
        return account;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void refresh() {
        if (Boolean.TRUE.equals(loading.getValue())) {
            return;
        }

        loading.setValue(true);
        error.setValue(null);
        repository.getCurrentAdmin(new AdminRepository.AdminAccountCallback() {
            @Override
            public void onSuccess(AdminAccount result) {
                account.postValue(result);
                loading.postValue(false);
            }

            @Override
            public void onError(String message) {
                error.postValue(message);
                loading.postValue(false);
            }
        });
    }
}
