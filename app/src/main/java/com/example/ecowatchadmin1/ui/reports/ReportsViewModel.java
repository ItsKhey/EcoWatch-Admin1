package com.example.ecowatchadmin1.ui.reports;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.repository.ReportRepository;

import java.io.File;
import java.util.List;

public class ReportsViewModel extends AndroidViewModel {

    private ReportRepository repository;
    private MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private LiveData<String> reportErrorLiveData;
    private LiveData<Boolean> loadingLiveData;
    private MutableLiveData<Boolean> submissionSuccessLiveData = new MutableLiveData<>();
    
    private LiveData<List<Report>> inboxReports;
    private LiveData<List<Report>> archivedReports;
    private LiveData<List<Report>> trashReports;
    private LiveData<List<Report>> allReports;

    public ReportsViewModel(@NonNull Application application) {
        super(application);
        repository = new ReportRepository(application);
        
        // Initialize LiveData references once
        inboxReports = repository.getInboxReports();
        archivedReports = repository.getArchivedReports();
        trashReports = repository.getTrashReports();
        allReports = repository.getAllReports();
        loadingLiveData = repository.getLoadingState();
        reportErrorLiveData = repository.getErrorState();

        // The map and report screens can be opened directly, so the first
        // report request must not depend on another fragment calling refresh.
        repository.fetchMyReports();
    }

    public LiveData<List<Report>> getAllReports() {
        return allReports;
    }

    public LiveData<Boolean> getLoadingState() {
        return loadingLiveData;
    }

    public LiveData<String> getReportsError() {
        return reportErrorLiveData;
    }

    public LiveData<List<Report>> getInboxReports() {
        return inboxReports;
    }

    public LiveData<List<Report>> getArchivedReports() {
        return archivedReports;
    }

    public LiveData<List<Report>> getTrashReports() {
        return trashReports;
    }

    public void softDeleteReport(Report report) {
        repository.softDeleteReport(report);
    }

    public void restoreReport(Report report) {
        repository.restoreReport(report);
    }

    public void archiveReport(Report report) {
        repository.archiveReport(report);
    }

    public void bulkSoftDelete(List<Integer> ids) {
        repository.bulkSoftDelete(ids);
    }

    public void bulkPermanentDelete(List<Integer> ids) {
        repository.bulkPermanentDelete(ids);
    }

    public void bulkArchive(List<Integer> ids) {
        repository.bulkArchive(ids);
    }

    public void bulkRestore(List<Integer> ids) {
        repository.bulkRestore(ids);
    }

    public void cleanupTrash() {
        repository.cleanupTrash();
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public LiveData<Boolean> getSubmissionSuccess() {
        return submissionSuccessLiveData;
    }

    public void submitReport(String title, String description, String wasteType, File imageFile) {
        submitReport(title, description, wasteType, imageFile, 0, 0);
    }

    public void submitReport(String title, String description, String wasteType, File imageFile, double lat, double lon) {
        if (title.isEmpty()) {
            errorLiveData.setValue("Title is required.");
            return;
        }

        if (imageFile == null) {
            errorLiveData.setValue("Image is required.");
            return;
        }

        Report report = new Report(title, description, wasteType, lat, lon);
        
        repository.submitReport(report, imageFile, new ReportRepository.ApiCallback<Report>() {
            @Override
            public void onSuccess(Report result) {
                submissionSuccessLiveData.postValue(true);
            }

            @Override
            public void onError(String error) {
                errorLiveData.postValue(error);
            }
        });
    }

    public void updateReport(Report report, File imageFile) {
        if (report.getTitle().isEmpty()) {
            errorLiveData.setValue("Title is required.");
            return;
        }

        repository.updateReport(report, imageFile, new ReportRepository.ApiCallback<Report>() {
            @Override
            public void onSuccess(Report result) {
                submissionSuccessLiveData.postValue(true);
            }

            @Override
            public void onError(String error) {
                errorLiveData.postValue(error);
            }
        });
    }

    public void deleteReport(Report report) {
        repository.deleteReport(report, null);
    }

    public void assignPersonnel(String reportId, String personnelId,
                                String adminResponse, String personnelNote,
                                String priority,
                                ReportRepository.ApiCallback<Report> callback) {
        repository.assignPersonnel(reportId, personnelId, adminResponse, personnelNote,
                priority, mutationCallback(callback));
    }

    /**
     * Dispatches the crew and the truck that match the report's scale.
     *
     * <p>A crew has no single owner, so the people are sent as a list and the
     * crew and the truck travel with them.
     */
    public void assignPersonnel(String reportId, List<String> personnelIds,
                                String wasteScale, String teamId, String vehicleId,
                                String adminResponse, String personnelNote,
                                String priority,
                                ReportRepository.ApiCallback<Report> callback) {
        repository.assignPersonnel(reportId, personnelIds, wasteScale, teamId, vehicleId,
                adminResponse, personnelNote, priority, mutationCallback(callback));
    }

    public void reassignPersonnel(String reportId, String personnelId,
                                  String adminResponse, String personnelNote,
                                  String priority,
                                  ReportRepository.ApiCallback<Report> callback) {
        repository.reassignPersonnel(reportId, personnelId, adminResponse, personnelNote,
                priority, mutationCallback(callback));
    }

    /** Replaces the crew and the truck on a report that already has one. */
    public void reassignPersonnel(String reportId, List<String> personnelIds,
                                  String wasteScale, String teamId, String vehicleId,
                                  String adminResponse, String personnelNote,
                                  String priority,
                                  ReportRepository.ApiCallback<Report> callback) {
        repository.reassignPersonnel(reportId, personnelIds, wasteScale, teamId, vehicleId,
                adminResponse, personnelNote, priority, mutationCallback(callback));
    }

    public void unassignPersonnel(String reportId,
                                   ReportRepository.ApiCallback<Report> callback) {
        repository.unassignPersonnel(reportId, mutationCallback(callback));
    }

    public void updateReportStatus(Report report, String status, String adminResponse,
                                   ReportRepository.ApiCallback<Report> callback) {
        String reportId = report == null ? null : report.getReportId();
        if (reportId == null && report != null) reportId = report.getMongoId();
        repository.updateReportStatus(reportId, status, adminResponse, report,
                mutationCallback(callback));
    }

    public void updateReportResponse(Report report, String adminResponse,
                                     ReportRepository.ApiCallback<Report> callback) {
        String reportId = report == null ? null : report.getReportId();
        if (reportId == null && report != null) reportId = report.getMongoId();
        repository.updateReportResponse(reportId, adminResponse, report,
                mutationCallback(callback));
    }

    public void approveReport(String reportId,
                              ReportRepository.ApiCallback<Report> callback) {
        repository.approveReport(reportId, mutationCallback(callback));
    }

    public void reviseReport(Report report, String adminResponse,
                             ReportRepository.ApiCallback<Report> callback) {
        String reportId = report == null ? null : report.getReportId();
        if (reportId == null && report != null) reportId = report.getMongoId();
        repository.reviseReport(reportId, adminResponse, report,
                mutationCallback(callback));
    }

    private ReportRepository.ApiCallback<Report> mutationCallback(
            final ReportRepository.ApiCallback<Report> callback) {
        return new ReportRepository.ApiCallback<Report>() {
            @Override
            public void onSuccess(Report result) {
                repository.fetchMyReports();
                if (callback != null) callback.onSuccess(result);
            }

            @Override
            public void onError(String error) {
                if (callback != null) callback.onError(error);
            }
        };
    }

    public void refreshReports() {
        repository.fetchMyReports();
    }
}
