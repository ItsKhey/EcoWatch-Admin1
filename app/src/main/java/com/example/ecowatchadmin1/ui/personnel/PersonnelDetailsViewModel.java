package com.example.ecowatchadmin1.ui.personnel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.data.models.PersonnelTaskCounts;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.models.ReportPage;
import com.example.ecowatchadmin1.data.remote.PersonnelJsonParser;
import com.example.ecowatchadmin1.data.repository.PersonnelRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MVVM state holder for the personnel details screen.
 *
 * <p>Deliberately shaped like {@code ReporterDetailsViewModel} so the two
 * detail screens stay interchangeable: same load/refresh contract, same
 * paging, same request-versioning guards against a slow response for an old
 * personnel id overwriting the current one after a fast back-and-return.
 */
public class PersonnelDetailsViewModel extends AndroidViewModel {
    private final PersonnelRepository repository;
    private final MutableLiveData<Personnel> personnel = new MutableLiveData<>();
    /**
     * Null until the statistics endpoint answers. The screen paints the counts
     * it saved for this personnel until then, so the placeholder zeros the
     * layout ships with never flash over them, and a failed request leaves the
     * saved numbers on the cards instead of blanking them.
     */
    /**
     * The three numbers on the stat cards, counted by the server from the
     * cleaner's assignment rows.
     *
     * <p>Counting reports instead of assignments cannot work here: a report only
     * reaches status "completed" once an administrator approves it, so a cleaner
     * with finished tasks showed zero until every one of their reports had been
     * signed off.
     *
     * <p>Null until the endpoint answers, so the screen keeps painting its saved
     * counts rather than flashing the layout's placeholder zeros.
     */
    private final MutableLiveData<PersonnelTaskCounts> taskCounts = new MutableLiveData<>();
    private final MutableLiveData<List<Report>> reports =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loadingReports = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> hasMoreReports = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<String> resetPassword = new MutableLiveData<>();
    private final AtomicLong requestVersion = new AtomicLong();
    private final AtomicLong reportRequestVersion = new AtomicLong();

    private String personnelId = "";
    private int reportPage = 0;

    public PersonnelDetailsViewModel(@NonNull Application application) {
        super(application);
        repository = new PersonnelRepository(application);
    }

    public LiveData<Personnel> getPersonnel() {
        return personnel;
    }

    /** The stat-card counts, counted from the cleaner's assignment rows. */
    public LiveData<PersonnelTaskCounts> getTaskCounts() {
        return taskCounts;
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

    /** One-shot confirmation text for a mutation that succeeded. */
    public LiveData<String> getMessage() {
        return message;
    }

    public void load(String id, boolean force) {
        String normalizedId = id == null ? "" : id.trim();
        if (normalizedId.isEmpty()) {
            error.setValue("Personnel ID is required.");
            return;
        }
        if (!force && Boolean.TRUE.equals(loading.getValue())
                && normalizedId.equals(personnelId)) {
            return;
        }

        personnelId = normalizedId;
        reportPage = 0;
        final long version = requestVersion.incrementAndGet();
        // Invalidate any in-flight page request from the previous personnel so a
        // late response cannot append the wrong reports to this list.
        reportRequestVersion.incrementAndGet();
        loading.setValue(true);
        hasMoreReports.setValue(false);
        // One slot per in-flight request: details, the task counts, and the first
        // page of reports.
        final AtomicInteger remaining = new AtomicInteger(3);
        final AtomicBoolean errorPosted = new AtomicBoolean(false);

        Runnable complete = () -> {
            if (remaining.decrementAndGet() == 0 && version == requestVersion.get()) {
                loading.postValue(false);
            }
        };

        repository.getDetails(normalizedId, new PersonnelRepository.PersonnelCallback() {
            @Override
            public void onSuccess(Personnel result) {
                if (version == requestVersion.get()) personnel.postValue(result);
                complete.run();
            }

            @Override
            public void onError(String failure) {
                postErrorOnce(errorPosted, version, failure);
                complete.run();
            }
        });

        // Counted from the assignment rows, so the cards show the work this cleaner
        // has actually finished rather than the work an administrator has
        // approved.
        repository.getPersonnelTaskCounts(normalizedId,
                new PersonnelRepository.PersonnelTaskCountsCallback() {
                    @Override
                    public void onSuccess(PersonnelTaskCounts result) {
                        if (version == requestVersion.get()) {
                            taskCounts.postValue(result == null
                                    ? new PersonnelTaskCounts() : result);
                        }
                        complete.run();
                    }

                    @Override
                    public void onError(String failure) {
                        postErrorOnce(errorPosted, version, failure);
                        complete.run();
                    }
                });

        requestReports(1, false, version, complete, errorPosted);
    }

    public void loadMoreReports() {
        if (Boolean.TRUE.equals(loadingReports.getValue())
                || !Boolean.TRUE.equals(hasMoreReports.getValue())
                || personnelId.isEmpty()) {
            return;
        }
        requestReports(reportPage + 1, true, requestVersion.get(),
                null, new AtomicBoolean(false));
    }

    private void requestReports(int page, boolean append, long detailsVersion,
                                Runnable complete, AtomicBoolean errorPosted) {
        final long reportVersion = reportRequestVersion.incrementAndGet();
        loadingReports.setValue(true);
        repository.getAssignedReports(personnelId, page,
                new PersonnelRepository.PersonnelReportPageCallback() {
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
                    public void onError(String failure) {
                        if (reportVersion != reportRequestVersion.get()
                                || detailsVersion != requestVersion.get()) return;
                        loadingReports.postValue(false);
                        if (errorPosted != null) {
                            postErrorOnce(errorPosted, detailsVersion, failure);
                        }
                        if (complete != null) complete.run();
                    }
                });
    }

    public void refresh() {
        load(personnelId, true);
    }

    /** Saves the profile fields, including the contact details shown on this page. */
    public void updatePersonnel(Personnel person, String firstName, String lastName,
                                String address, String gmail, String username,
                                String phoneNumber, String messengerLink) {
        if (person == null) {
            error.setValue("Personnel record is missing.");
            return;
        }
        repository.updatePersonnel(person.getPersonnelId(), firstName, lastName, address,
                gmail, username, phoneNumber, messengerLink,
                new PersonnelRepository.PersonnelCallback() {
                    @Override
                    public void onSuccess(Personnel updated) {
                        message.postValue("Personnel details updated.");
                        if (updated != null) personnel.postValue(updated);
                    }

                    @Override
                    public void onError(String failure) {
                        error.postValue(failure);
                    }
                });
    }

    /**
     * Resets the password. The server echoes the temporary password back so the
     * admin can hand it over; it is surfaced through {@link #getResetPassword()}
     * so the screen can present it in a copyable dialog.
     */
    public void resetPassword(Personnel person, String newPassword) {
        if (person == null) {
            error.setValue("Personnel record is missing.");
            return;
        }
        repository.resetPassword(person.getPersonnelId(), newPassword,
                new PersonnelRepository.PersonnelMutationCallback() {
                    @Override
                    public void onSuccess(PersonnelJsonParser.MutationResult result) {
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

    public void disablePersonnel(Personnel person) {
        if (person == null) {
            error.setValue("Personnel record is missing.");
            return;
        }
        repository.disablePersonnel(person.getPersonnelId(),
                new PersonnelRepository.PersonnelCallback() {
                    @Override
                    public void onSuccess(Personnel updated) {
                        message.postValue("Personnel account disabled.");
                        if (updated != null) personnel.postValue(updated);
                    }

                    @Override
                    public void onError(String failure) {
                        error.postValue(failure);
                    }
                });
    }

    public void clearError() {
        error.setValue(null);
    }

    public void clearMessage() {
        message.setValue(null);
    }

    private void postErrorOnce(AtomicBoolean errorPosted, long version, String failure) {
        if (version != requestVersion.get()) return;
        if (errorPosted.compareAndSet(false, true)) error.postValue(failure);
    }
}
