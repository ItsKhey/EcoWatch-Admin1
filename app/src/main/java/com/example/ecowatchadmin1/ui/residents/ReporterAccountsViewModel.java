package com.example.ecowatchadmin1.ui.residents;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ecowatchadmin1.data.models.ResidentAccount;
import com.example.ecowatchadmin1.data.models.ResidentPage;
import com.example.ecowatchadmin1.data.repository.ResidentRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/** MVVM state holder for reporter-account search, filtering, and pagination. */
public class ReporterAccountsViewModel extends AndroidViewModel {
    private final ResidentRepository repository;
    private final MutableLiveData<List<ResidentAccount>> residents =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loadingMore = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> hasMore = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final AtomicLong requestVersion = new AtomicLong();

    private String currentSearch = "";
    private String currentStatus = "ALL";
    private int currentPage = 0;

    public ReporterAccountsViewModel(@NonNull Application application) {
        super(application);
        repository = new ResidentRepository(application);
    }

    public LiveData<List<ResidentAccount>> getResidents() {
        return residents;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<Boolean> getLoadingMore() {
        return loadingMore;
    }

    public LiveData<Boolean> getHasMore() {
        return hasMore;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void loadResidents(String search, String status) {
        currentSearch = search == null ? "" : search.trim();
        currentStatus = normalizeStatus(status);
        currentPage = 0;
        requestPage(1, false);
    }

    public void refreshResidents() {
        currentPage = 0;
        requestPage(1, false);
    }

    public void loadMore() {
        if (Boolean.TRUE.equals(loading.getValue())
                || Boolean.TRUE.equals(loadingMore.getValue())
                || !Boolean.TRUE.equals(hasMore.getValue())) {
            return;
        }
        requestPage(currentPage + 1, true);
    }

    private void requestPage(int page, boolean append) {
        final long version = requestVersion.incrementAndGet();
        if (append) {
            loadingMore.setValue(true);
        } else {
            loadingMore.setValue(false);
            loading.setValue(true);
        }

        repository.getResidents(currentSearch, currentStatus, page,
                new ResidentRepository.ResidentPageCallback() {
                    @Override
                    public void onSuccess(ResidentPage result) {
                        if (version != requestVersion.get()) return;
                        ResidentPage safeResult = result == null ? new ResidentPage() : result;
                        List<ResidentAccount> next = safeResult.getResidents();
                        if (append) {
                            List<ResidentAccount> merged = new ArrayList<>(
                                    residents.getValue() == null
                                            ? Collections.emptyList() : residents.getValue());
                            merged.addAll(next);
                            residents.setValue(merged);
                        } else {
                            residents.setValue(next == null ? new ArrayList<>() : next);
                        }
                        currentPage = Math.max(1, safeResult.getPage());
                        hasMore.setValue(safeResult.hasMore());
                        loading.postValue(false);
                        loadingMore.postValue(false);
                    }

                    @Override
                    public void onError(String message) {
                        if (version != requestVersion.get()) return;
                        loading.postValue(false);
                        loadingMore.postValue(false);
                        error.postValue(message);
                    }
                });
    }

    public void clearError() {
        error.setValue(null);
    }

    private String normalizeStatus(String status) {
        if (status == null) return "ALL";
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("ACTIVE".equals(normalized) || "DISABLED".equals(normalized)) return normalized;
        return "ALL";
    }
}
