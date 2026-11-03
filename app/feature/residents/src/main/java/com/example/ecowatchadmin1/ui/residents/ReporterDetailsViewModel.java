package com.example.ecowatchadmin1.ui.residents;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.models.ReportPage;
import com.example.ecowatchadmin1.data.models.ReportStatistics;
import com.example.ecowatchadmin1.data.models.ResidentAccount;
import com.example.ecowatchadmin1.data.repository.ResidentRepository;
import com.example.ecowatchadmin1.data.remote.ResidentJsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** MVVM state holder for a reporter profile, live counts, and paged history. */
public class ReporterDetailsViewModel extends AndroidViewModel {
    private final ResidentRepository repository;
    private final MutableLiveData<ResidentAccount> resident = new MutableLiveData<>();
    private final MutableLiveData<ReportStatistics> statistics =
            new MutableLiveData<>(new ReportStatistics());
    private final MutableLiveData<List<Report>> reports =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loadingReports = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> hasMoreReports = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> deleting = new MutableLiveData<>(false);
    /*
     * Fires once, carrying the id of the account that was deleted, so the screen
     * can pop itself and tell the list above it to refresh. A boolean would not
     * do: the same reporter can legitimately be deleted twice from two different
     * screens, and a second `true` is indistinguishable from a re-delivery of the
     * first one after a rotation.
     */
    private final MutableLiveData<String> deletedResidentId = new MutableLiveData<>();
    /*
     * The temporary password from the last successful reset. Held apart from
     * `message` because it is a credential with a single presentation: the
     * handoff dialog consumes it and clears it, so it can never be replayed by a
     * later re-render such as the 30-second live refresh.
     */
    private final MutableLiveData<String> resetPassword = new MutableLiveData<>();
    private final AtomicLong requestVersion = new AtomicLong();
    private final AtomicLong reportRequestVersion = new AtomicLong();

    private String residentId = "";
    private int reportPage = 0;

    public ReporterDetailsViewModel(@NonNull Application application) {
        super(application);
        repository = new ResidentRepository(application);
    }

    public LiveData<ResidentAccount> getResident() {
        return resident;
    }

    public LiveData<ReportStatistics> getStatistics() {
        return statistics;
    }

    public LiveData<List<Report>> getReports() {
        return reports;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<Boolean> getLoadingReports() {
        return loadingReports;
    }

    public LiveData<Boolean> getHasMoreReports() {
        return hasMoreReports;
    }

    public LiveData<String> getError() {
        return error;
    }

    /** A short confirmation, e.g. "Reporter account disabled." */
    public LiveData<String> getMessage() {
        return message;
    }

    /**
     * Resets the password. The server echoes the password back so the
     * administrator can hand it to the reporter; it is surfaced through
     * {@link #getResetPassword()} for the copyable handoff dialog.
     */
    public void resetPassword(ResidentAccount target, String newPassword) {
        if (target == null) {
            error.setValue("Reporter record is missing.");
            return;
        }
        repository.resetPassword(target.getResidentId(), newPassword,
                new ResidentRepository.ResidentMutationCallback() {
                    @Override
                    public void onSuccess(ResidentJsonParser.MutationResult result) {
                        resetPassword.postValue(result == null ? "" : result.getPassword());
                    }

                    @Override
                    public void onError(String failure) {
                        error.postValue(failure);
                    }
                });
    }

    /** The new temporary password from the last successful reset, consumed once. */
    public LiveData<String> getResetPassword() {
        return resetPassword;
    }

    public void clearResetPassword() {
        resetPassword.setValue(null);
    }

    /**
     * Disables the account. The reloaded profile comes back rather than being
     * patched locally, so the status chip reflects exactly what the server
     * decided instead of the client's guess at it.
     */
    public void disableResident(ResidentAccount target) {
        if (target == null) {
            error.setValue("Reporter record is missing.");
            return;
        }
        repository.disableResident(target.getResidentId(),
                new ResidentRepository.ResidentCallback() {
                    @Override
                    public void onSuccess(ResidentAccount updated) {
                        message.postValue("Reporter account disabled.");
                        if (updated != null) resident.postValue(updated);
                    }

                    @Override
                    public void onError(String failure) {
                        error.postValue(failure);
                    }
                });
    }

    public void load(String id, boolean force) {
        String normalizedId = id == null ? "" : id.trim();
        if (normalizedId.isEmpty()) {
            error.setValue("Resident ID is required.");
            return;
        }
        if (!force && Boolean.TRUE.equals(loading.getValue())
                && normalizedId.equals(residentId)) {
            return;
        }

        residentId = normalizedId;
        reportPage = 0;
        final long version = requestVersion.incrementAndGet();
        reportRequestVersion.incrementAndGet();
        loading.setValue(true);
        hasMoreReports.setValue(false);
        final AtomicInteger remaining = new AtomicInteger(2);
        final AtomicBoolean errorPosted = new AtomicBoolean(false);

        Runnable complete = () -> {
            if (remaining.decrementAndGet() == 0 && version == requestVersion.get()) {
                loading.postValue(false);
            }
        };

        repository.getResidentDetails(normalizedId,
                new ResidentRepository.ResidentDetailsCallback() {
                    @Override
                    public void onSuccess(ResidentAccount result, ReportStatistics resultStatistics) {
                        if (version == requestVersion.get()) {
                            resident.postValue(result);
                            statistics.postValue(resultStatistics == null
                                    ? new ReportStatistics() : resultStatistics);
                        }
                        complete.run();
                    }

                    @Override
                    public void onError(String message) {
                        postErrorOnce(errorPosted, version, message);
                        complete.run();
                    }
                });

        requestReports(1, false, version, complete, errorPosted);
    }

    public void loadMoreReports() {
        if (Boolean.TRUE.equals(loadingReports.getValue())
                || !Boolean.TRUE.equals(hasMoreReports.getValue())
                || residentId.isEmpty()) {
            return;
        }
        requestReports(reportPage + 1, true, requestVersion.get(),
                null, new AtomicBoolean(false));
    }

    private void requestReports(int page, boolean append, long detailsVersion,
                                Runnable complete, AtomicBoolean errorPosted) {
        final long reportVersion = reportRequestVersion.incrementAndGet();
        loadingReports.setValue(true);
        repository.getResidentReports(residentId, page,
                new ResidentRepository.ReportPageCallback() {
                    @Override
                    public void onSuccess(ReportPage result) {
                        if (reportVersion != reportRequestVersion.get()
                                || detailsVersion != requestVersion.get()) return;
                        ReportPage safeResult = result == null ? new ReportPage() : result;
                        List<Report> next = safeResult.getReports();
                        if (append) {
                            List<Report> merged = new ArrayList<>(
                                    reports.getValue() == null
                                            ? Collections.emptyList() : reports.getValue());
                            merged.addAll(next);
                            reports.setValue(merged);
                        } else {
                            reports.setValue(next == null ? new ArrayList<>() : next);
                        }
                        reportPage = Math.max(1, safeResult.getPage());
                        hasMoreReports.setValue(safeResult.hasMore());
                        loadingReports.postValue(false);
                        if (complete != null) complete.run();
                    }

                    @Override
                    public void onError(String message) {
                        if (reportVersion != reportRequestVersion.get()
                                || detailsVersion != requestVersion.get()) return;
                        loadingReports.postValue(false);
                        if (errorPosted != null) {
                            postErrorOnce(errorPosted, detailsVersion, message);
                        }
                        if (complete != null) complete.run();
                    }
                });
    }

    public void refresh() {
        load(residentId, true);
    }

    /**
     * Restores an account disabled by an administrator. Like the disable, the
     * reloaded profile is what gets posted back, so the status chip shows what
     * the server actually decided.
     */
    public void enableResident(ResidentAccount target) {
        if (target == null) {
            error.setValue("Reporter record is missing.");
            return;
        }
        repository.enableResident(target.getResidentId(),
                new ResidentRepository.ResidentCallback() {
                    @Override
                    public void onSuccess(ResidentAccount updated) {
                        message.postValue("Reporter account enabled.");
                        if (updated != null) resident.postValue(updated);
                    }

                    @Override
                    public void onError(String failure) {
                        error.postValue(failure);
                    }
                });
    }

    /**
     * Permanently deletes the account. Irreversible: the server removes the
     * profile, every report it owns, its history, its notifications and its
     * uploaded photos, and there is no route back.
     *
     * <p>Refused while a deletion is already running, so a double tap cannot send
     * two deletes for one deliberate action. The second would otherwise come back
     * as a 404 and be reported as a failure of an operation that in fact
     * succeeded.
     */
    public void deleteResident(ResidentAccount target) {
        if (target == null) {
            error.setValue("Reporter record is missing.");
            return;
        }
        if (Boolean.TRUE.equals(deleting.getValue())) {
            return;
        }

        deleting.setValue(true);

        repository.deleteResident(target.getResidentId(),
                new ResidentRepository.ResidentDeletionCallback() {
                    @Override
                    public void onSuccess() {
                        deleting.postValue(false);
                        deletedResidentId.postValue(target.getResidentId());
                    }

                    @Override
                    public void onError(String failure) {
                        deleting.postValue(false);
                        error.postValue(failure);
                    }
                });
    }

    /** True while a deletion is in flight, so the screen can lock its controls. */
    public LiveData<Boolean> getDeleting() {
        return deleting;
    }

    /** The account that was deleted, consumed once by the screen that pops. */
    public LiveData<String> getDeletedResidentId() {
        return deletedResidentId;
    }

    public void clearDeletedResidentId() {
        deletedResidentId.setValue(null);
    }

    public void clearError() {
        error.setValue(null);
    }

    public void clearMessage() {
        message.setValue(null);
    }

    private void postErrorOnce(AtomicBoolean errorPosted, long version, String message) {
        if (version != requestVersion.get()) return;
        if (errorPosted.compareAndSet(false, true)) error.postValue(message);
    }
}
