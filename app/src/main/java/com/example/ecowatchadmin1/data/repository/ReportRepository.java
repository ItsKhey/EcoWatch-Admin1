package com.example.ecowatchadmin1.data.repository;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.ecowatchadmin1.data.local.AppDatabase;
import com.example.ecowatchadmin1.data.local.ReportDao;
import com.example.ecowatchadmin1.data.local.HistoryDao;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.models.History;
import com.example.ecowatchadmin1.data.remote.AdminHistoryResponse;
import com.example.ecowatchadmin1.data.remote.ApiService;
import com.example.ecowatchadmin1.data.remote.ReportUpdateMerger;
import com.example.ecowatchadmin1.data.remote.RetrofitClient;
import com.example.ecowatchadmin1.utils.SessionManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReportRepository {
    private static final String TAG = "ReportRepository";

    private ReportDao reportDao;
    private HistoryDao historyDao;

    /**
     * How many audit records the Admin History screen asks for at a time.
     *
     * <p>The backend caps a page at 500. 200 covers a working session of admin
     * activity without shipping a table scan to the device, and matches the
     * server default so an unparameterised request behaves the same way.
     */
    private static final int ADMIN_HISTORY_PAGE_SIZE = 200;
    private ApiService apiService;
    private AdminRepository adminRepository;
    private SessionManager sessionManager;
    private ExecutorService executorService;
    private Application application;
    private final MutableLiveData<List<Report>> remoteReports =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loadingLiveData =
            new MutableLiveData<>(false);
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final AtomicBoolean reportsRequestInFlight = new AtomicBoolean(false);
    private final AtomicBoolean reportsRefreshQueued = new AtomicBoolean(false);

    public ReportRepository(Application application) {
        this.application = application;
        AppDatabase db = AppDatabase.getInstance(application);
        reportDao = db.reportDao();
        historyDao = db.historyDao();
        apiService = RetrofitClient.getApiService(application);
        adminRepository = new AdminRepository(application);
        sessionManager = new SessionManager(application);
        executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * The backend response is the UI source of truth. Room is only maintained
     * as a write-through cache for legacy action code; it is never returned as
     * the report list consumed by the map or reports screens.
     */
    public LiveData<List<Report>> getAllReports() {
        return remoteReports;
    }

    public LiveData<List<Report>> getInboxReports() {
        return Transformations.map(remoteReports, reports -> {
            List<Report> filtered = new ArrayList<>();
            for (Report report : reports) {
                if (!report.isDeleted() && !report.isArchived()
                        && "Pending".equalsIgnoreCase(report.getStatus())) {
                    filtered.add(report);
                }
            }
            return filtered;
        });
    }

    public LiveData<List<Report>> getArchivedReports() {
        return Transformations.map(remoteReports, reports -> {
            List<Report> filtered = new ArrayList<>();
            for (Report report : reports) {
                if (!report.isDeleted()
                        && (report.isArchived()
                        || "Completed".equalsIgnoreCase(report.getStatus())
                        || "Denied".equalsIgnoreCase(report.getStatus()))) {
                    filtered.add(report);
                }
            }
            return filtered;
        });
    }

    public LiveData<List<Report>> getTrashReports() {
        return Transformations.map(remoteReports, reports -> {
            List<Report> filtered = new ArrayList<>();
            for (Report report : reports) {
                if (report.isDeleted()) {
                    filtered.add(report);
                }
            }
            return filtered;
        });
    }

    public LiveData<Boolean> getLoadingState() {
        return loadingLiveData;
    }

    public LiveData<String> getErrorState() {
        return errorLiveData;
    }

    /**
     * Every active row of the administrator's audit trail, read from the local cache.
     *
     * <p>Backed by {@code GET /api/admin/history} rather than the resident-scoped
     * route: an administrator's audit trail spans every resident, and the
     * decisions it holds are recorded against the resident who owns the report, so
     * the resident route returns nothing for an admin token.
     *
     * <p>Unfiltered on purpose. Observed once for the life of the screen, with the
     * tab applied locally, so switching tabs cannot race another observer's result.
     *
     * <p>The query itself restricts rows to those the server issued. This app
     * records no history of its own, so a row without a server id is not a
     * decision and must never reach the screen.
     */
    public LiveData<List<History>> observeAdminFeed() {
        return historyDao.observeAdminFeed();
    }

    /** The Admin History trash, read from the local cache. */
    public LiveData<List<History>> observeAdminTrash() {
        return historyDao.observeAdminTrash();
    }

    /**
     * Pulls one tab of the audit feed and caches it.
     *
     * <p>Filtering, searching and paging happen server-side, so this sends the
     * active tab and the active date window rather than fetching everything and
     * discarding most of it on the device.
     *
     * @param category    the tab to pull, e.g. {@code REPORTS}; null for All
     * @param search      free-text query, or null
     * @param fromMillis  inclusive window start, or null
     * @param toMillis    inclusive window end, or null
     */
    public void fetchAdminFeed(String category, String search,
                               Long fromMillis, Long toMillis) {
        repairAdminHistoryCache();
        apiService.getAdminHistory(
                        category,
                        (search == null || search.trim().isEmpty()) ? null : search.trim(),
                        fromMillis == null ? null : isoUtc(fromMillis),
                        toMillis == null ? null : isoUtc(toMillis),
                        ADMIN_HISTORY_PAGE_SIZE,
                        0
                )
                .enqueue(new Callback<AdminHistoryResponse>() {
                    @Override
                    public void onResponse(Call<AdminHistoryResponse> call,
                                          Response<AdminHistoryResponse> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            // The cached rows stay on screen. A refresh that fails
                            // must not read as "there is no history".
                            Log.w("ReportRepository",
                                    "Admin history feed failed: " + response.code());
                            return;
                        }
                        cacheAdminHistory(response.body().getHistory(), false);
                    }

                    @Override
                    public void onFailure(Call<AdminHistoryResponse> call, Throwable t) {
                        Log.w("ReportRepository", "Admin history feed failed", t);
                    }
                });
    }

    /** Pulls the trashed audit records and caches them. */
    public void fetchAdminHistoryTrash() {
        repairAdminHistoryCache();
        apiService.getAdminHistoryTrash(ADMIN_HISTORY_PAGE_SIZE, 0)
                .enqueue(new Callback<AdminHistoryResponse>() {
                    @Override
                    public void onResponse(Call<AdminHistoryResponse> call,
                                          Response<AdminHistoryResponse> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            Log.w("ReportRepository",
                                    "Admin history trash failed: " + response.code());
                            return;
                        }
                        cacheAdminHistory(response.body().getHistory(), true);
                    }

                    @Override
                    public void onFailure(Call<AdminHistoryResponse> call, Throwable t) {
                        Log.w("ReportRepository", "Admin history trash failed", t);
                    }
                });
    }

    /**
     * Writes a fetched page into the cache.
     *
     * <p>The local primary key is left at 0 so Room assigns one, which keeps a
     * re-sync from colliding with a row that is already on screen under a
     * different local id. The server's history id is what identifies the record,
     * and it is kept in {@code mongoId} for the bulk operations.
     *
     * <p>Because that key is reassigned on every sync, REPLACE cannot match the
     * existing row and would append a duplicate instead of updating it. The stale
     * copies are deleted first, which is what makes a pull-to-refresh idempotent:
     * without it the tab counts doubled on each refresh, and removing a record by
     * its local id deleted only one of several copies.
     *
     * @param trashed whether these rows came from the trash endpoint. The caller
     *                owns that decision rather than the row's own flag, because a
     *                row parsed before {@code is_deleted} was bound always reported
     *                itself as active and would otherwise be filed back into the
     *                feed it was just pulled out of.
     */
    private void cacheAdminHistory(List<History> rows, boolean trashed) {
        if (rows == null || rows.isEmpty()) return;
        executorService.execute(() -> {
            List<String> mongoIds = new ArrayList<>();
            for (History history : rows) {
                if (history == null || isBlank(history.getMongoId())) continue;
                mongoIds.add(history.getMongoId().trim());
                history.setId(0);
                history.setDeleted(trashed);
            }
            if (mongoIds.isEmpty()) return;

            historyDao.deleteByMongoIds(mongoIds);
            historyDao.upsertAdminHistory(rows);
        });
    }

    /**
     * Collapses duplicate cached history rows left by an earlier build.
     *
     * <p>Runs before every admin feed and trash pull rather than as part of
     * caching a page, because the damage has to be repaired even when the server
     * answers with nothing at all. Gating it on a non-empty response would leave
     * an over-sized table untouched on exactly the tab that returns no rows, and
     * the next read of that table would raise {@code SQLiteBlobTooBigException}
     * again.
     *
     * <p>Android backs a cursor with a single shared {@code CursorWindow} of about
     * 2 MB, so a table that grew past it fails the feed query outright rather than
     * paging. A build cached the feed by inserting each row under a fresh local
     * key, so {@code INSERT OR REPLACE} never matched the row it meant to update
     * and every pull-to-refresh appended another copy. Self-healing here means an
     * affected install recovers on its next sync with no reinstall and no schema
     * change.
     */
    public void repairAdminHistoryCache() {
        executorService.execute(() -> {
            // Rows with no server id are deleted before anything else. Older builds
            // wrote them: a locally inferred status change, a locally inferred
            // submission, and anything the resident-scoped my-history route had
            // returned and re-tagged with the administrator's own id. None of those
            // is a decision the server recorded, so none belongs in an audit trail.
            // Removing them rather than only filtering them means no other query can
            // surface them again.
            try {
                int discarded = historyDao.deleteLocalDuplicates();
                if (discarded > 0) {
                    Log.i(TAG, "Discarded " + discarded
                            + " locally fabricated history row(s) with no server id");
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not discard fabricated history rows", e);
            }

            try {
                int removed = historyDao.collapseDuplicateHistoryRows();
                if (removed > 0) {
                    Log.w(TAG, "Collapsed " + removed + " duplicate cached history row(s)");
                }
            } catch (RuntimeException e) {
                // A repair failure must not stop the feed from loading; the query
                // limits already bound what a single read can pull back.
                Log.e(TAG, "Could not collapse duplicate history rows", e);
            }
        });
    }

    /** ISO-8601 in UTC, which is what the backend's date window expects. */
    private static String isoUtc(long millis) {
        SimpleDateFormat formatter =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(new Date(millis));
    }

    public void softDeleteReport(Report report) {
        String reportId = stableReportId(report);
        if (isBlank(reportId)) {
            Log.e("ReportRepository", "Cannot move report to trash: stable report ID is missing.");
            return;
        }
        enqueueLifecycle(apiService.moveReportToTrashAdmin(reportId), report,
                "move to trash", null);
    }

    public void restoreReport(Report report) {
        String reportId = stableReportId(report);
        if (isBlank(reportId)) {
            Log.e("ReportRepository", "Cannot restore report: stable report ID is missing.");
            return;
        }
        enqueueLifecycle(apiService.restoreReportAdmin(reportId), report,
                "restore", null);
    }

    public void archiveReport(Report report) {
        String reportId = stableReportId(report);
        if (isBlank(reportId)) {
            Log.e("ReportRepository", "Cannot archive report: stable report ID is missing.");
            return;
        }
        enqueueLifecycle(apiService.moveReportToArchiveAdmin(reportId), report,
                "archive", null);
    }

    public void bulkSoftDelete(List<Integer> ids) {
        enqueueBulkLifecycle(ids, apiService::bulkMoveToTrashAdmin,
                "bulk move to trash");
    }

    public void bulkPermanentDelete(List<Integer> ids) {
        enqueueBulkLifecycle(ids, apiService::bulkDeleteAdmin,
                "bulk permanent delete");
    }

    public void bulkArchive(List<Integer> ids) {
        enqueueBulkLifecycle(ids, apiService::bulkMoveToArchiveAdmin,
                "bulk archive");
    }

    public void bulkRestore(List<Integer> ids) {
        enqueueBulkLifecycle(ids, apiService::bulkRestoreAdmin,
                "bulk restore");
    }

    /**
     * Result of one admin history mutation, as the server actually applied it.
     *
     * <p>{@code succeeded} counts the rows the server confirmed it changed, not
     * the rows the device optimistically removed. The distinction is the whole
     * point: a mutation that silently matched nothing has to surface as zero
     * rather than as a cheerful "Logs deleted".
     */
    public interface HistoryActionCallback {
        void onHistoryActionComplete(int succeeded, int failed);

        /** The server was unreachable or refused the request. */
        void onHistoryActionFailed();
    }

    /** A callback that ignores the outcome, for callers that only want the write. */
    private static final HistoryActionCallback IGNORE_HISTORY_ACTION =
            new HistoryActionCallback() {
                @Override
                public void onHistoryActionComplete(int succeeded, int failed) {
                }

                @Override
                public void onHistoryActionFailed() {
                }
            };

    /**
     * Pairs each row's server identifier with its local Room primary key.
     *
     * <p>The two ids are not interchangeable. The server addresses a history row by
     * {@code history_id}, which arrives in {@link History#getMongoId()}; Room
     * addresses the cached copy by its own autoincrement key. Both are needed to
     * call the server and then mirror its answer into the cache, and keeping them
     * together here is what stops a local delete from running against a server id
     * the backend never heard of.
     */
    private static final class HistoryRowIds {
        final String serverId;
        final int localId;

        HistoryRowIds(String serverId, int localId) {
            this.serverId = serverId;
            this.localId = localId;
        }
    }

    /**
     * Collects the ids an admin history mutation needs, dropping rows the server
     * could never address.
     *
     * <p>A row with no server id is a local-only record that the admin feed never
     * served, so there is nothing on the server to mutate. Passing its local key
     * upward anyway would produce a {@code notFound} the UI would have to explain.
     */
    private List<HistoryRowIds> collectHistoryRowIds(List<History> rows) {
        List<HistoryRowIds> pairs = new ArrayList<>();
        if (rows == null) return pairs;

        for (History history : rows) {
            if (history == null) continue;
            String serverId = history.getMongoId();
            if (serverId == null || serverId.trim().isEmpty()) {
                Log.w(TAG, "Skipping admin history row with no server history_id");
                continue;
            }
            pairs.add(new HistoryRowIds(serverId.trim(), history.getId()));
        }
        return pairs;
    }

    /**
     * Runs one admin history mutation and mirrors the server's answer into Room.
     *
     * <p>The server is called first and the cache is updated only from the ids it
     * confirms. The previous order was the other way round: Room dropped the rows
     * and the request went out afterwards, so a rejected or unmatched write left
     * the device showing a deletion the database had never accepted, and the rows
     * reappeared on the next pull.
     *
     * <p>{@code request} builds the call for a given id list, so soft delete,
     * restore and permanent delete share the id plumbing and the confirmation
     * handling rather than each reimplementing it.
     *
     * @param localUpdate applied on the executor once the server has confirmed
     */
    private void runAdminHistoryMutation(
            List<History> rows,
            java.util.function.Function<List<Integer>, Call<Map<String, Object>>> request,
            java.util.function.Consumer<List<Integer>> localUpdate,
            HistoryActionCallback callback) {

        HistoryActionCallback safeCallback = callback == null ? IGNORE_HISTORY_ACTION : callback;

        List<HistoryRowIds> pairs = collectHistoryRowIds(rows);
        if (pairs.isEmpty()) {
            safeCallback.onHistoryActionComplete(0, rows == null ? 0 : rows.size());
            return;
        }

        List<Integer> serverIds = new ArrayList<>();
        Map<Integer, Integer> localIdByServerId = new HashMap<>();
        for (HistoryRowIds pair : pairs) {
            try {
                int parsed = Integer.parseInt(pair.serverId);
                serverIds.add(parsed);
                localIdByServerId.put(parsed, pair.localId);
            } catch (NumberFormatException e) {
                // A non-numeric history_id cannot be a report_histories key, which
                // is what the admin routes parse their ids to.
                Log.w(TAG, "Ignoring non-numeric admin history id: " + pair.serverId);
            }
        }

        if (serverIds.isEmpty()) {
            safeCallback.onHistoryActionComplete(0, pairs.size());
            return;
        }

        request.apply(serverIds).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call,
                                   Response<Map<String, Object>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w(TAG, "Admin history mutation rejected: HTTP " + response.code());
                    safeCallback.onHistoryActionFailed();
                    return;
                }

                Map<String, Object> payload = response.body();
                int succeeded = readInt(payload.get("deleted"));
                Object notFound = payload.get("notFound");

                // Only the ids the server reports as deleted are mirrored into the
                // cache. Anything it listed as notFound is still in the database, so
                // removing the local copy would recreate the original divergence.
                List<Integer> confirmedLocalIds = new ArrayList<>();
                if (notFound instanceof List) {
                    for (Object entry : (List<?>) notFound) {
                        Integer serverId = parseIntOrNull(entry);
                        if (serverId != null) localIdByServerId.remove(serverId);
                    }
                }
                for (Integer serverId : localIdByServerId.keySet()) {
                    Integer localId = localIdByServerId.get(serverId);
                    if (localId != null && localId > 0) confirmedLocalIds.add(localId);
                }

                if (!confirmedLocalIds.isEmpty()) {
                    List<Integer> toApply = confirmedLocalIds;
                    executorService.execute(() -> localUpdate.accept(toApply));
                }

                Log.d(TAG, "Admin history mutation: " + succeeded + " applied on server, "
                        + (pairs.size() - succeeded) + " not matched");

                safeCallback.onHistoryActionComplete(succeeded, pairs.size() - succeeded);
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.e(TAG, "Admin history mutation failed", t);
                safeCallback.onHistoryActionFailed();
            }
        });
    }

    private static int readInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        return parseIntOrNull(value) == null ? 0 : parseIntOrNull(value);
    }

    private static Integer parseIntOrNull(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value == null) return null;
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Moves admin history rows to the trash.
     *
     * <p>Calls {@code POST /api/admin/history/bulk-soft-delete}. The
     * administrator-scoped route is the one that matches this screen: it addresses
     * rows by history id alone. The resident route this replaced added
     * {@code AND resident_id = <caller>}, which an admin token never satisfies,
     * because admin decisions are recorded against the resident who owns the
     * report, while account rows belong to no resident at all.
     */
    public void softDeleteAdminHistory(List<History> rows, HistoryActionCallback callback) {
        runAdminHistoryMutation(
                rows,
                ids -> apiService.bulkSoftDeleteAdminHistory(bodyOf(ids)),
                ids -> historyDao.bulkSoftDelete(ids, System.currentTimeMillis()),
                callback);
    }

    /** Returns trashed admin history rows to the active feed. */
    public void restoreAdminHistory(List<History> rows, HistoryActionCallback callback) {
        runAdminHistoryMutation(
                rows,
                ids -> apiService.bulkRestoreAdminHistory(bodyOf(ids)),
                historyDao::bulkRestore,
                callback);
    }

    /**
     * Deletes admin history rows for good.
     *
     * <p>Irreversible, so the screen confirms before calling it. The cache is
     * updated only for the ids the server confirms it destroyed.
     */
    public void deleteAdminHistoryPermanently(List<History> rows, HistoryActionCallback callback) {
        runAdminHistoryMutation(
                rows,
                ids -> apiService.bulkDeleteAdminHistory(bodyOf(ids)),
                historyDao::deleteHistoryByIds,
                callback);
    }

    private Map<String, List<Integer>> bodyOf(List<Integer> ids) {
        Map<String, List<Integer>> body = new HashMap<>();
        body.put("ids", ids);
        return body;
    }

    /** Bulk hard delete straight from the cache; the server write is separate. */
    public void deleteHistoryByIds(List<Integer> ids) {
        executorService.execute(() -> historyDao.deleteHistoryByIds(ids));
    }

    public void cleanupTrash() {
        // Permanent trash cleanup is server-owned. Do not delete local rows
        // independently, otherwise the Room cache and Render disagree.
        fetchMyReports();
    }

    public void submitReport(Report report, File imageFile, ApiCallback<Report> callback) {
        executorService.execute(() -> {
            // Set resident ID
            String residentId = sessionManager.fetchResidentId();
            report.setResidentId(residentId);
            
            // 1. Save image locally
            if (imageFile != null && imageFile.exists()) {
                String localPath = saveImageToInternalStorage(imageFile);
                report.setLocalPhotoPath(localPath);
            }
            
            // 2. Save to Room
            long id = reportDao.insertReport(report);
            report.setId((int) id);

            // No history row is written here. An audit record is something the
            // server records about a decision someone actually made; a client
            // guessing that "a report was submitted, therefore log SUBMITTED"
            // manufactures an event that never happened, and it landed in the same
            // table the administrator's audit trail reads.

            // 3. Sync to server
            syncReportToServer(report, imageFile, callback);
        });
    }

    public void updateReport(Report report, File imageFile, ApiCallback<Report> callback) {
        executorService.execute(() -> {
            // 1. Save image locally if new
            if (imageFile != null && imageFile.exists()) {
                String localPath = saveImageToInternalStorage(imageFile);
                report.setLocalPhotoPath(localPath);
            }

            // 2. Update Room
            reportDao.insertReport(report);

            // 3. Sync update to server if it has a server ID
            String targetId = report.getMongoId() != null ? report.getMongoId() : report.getReportId();
            if (targetId != null) {
                syncUpdateToServer(targetId, report, imageFile, callback);
            } else {
                // If not synced yet, just try to sync it fully
                syncReportToServer(report, imageFile, callback);
            }
        });
    }

    private void syncUpdateToServer(String targetId, Report report, File imageFile, ApiCallback<Report> callback) {
        MultipartBody.Part body = null;
        if (imageFile != null && imageFile.exists()) {
            RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), imageFile);
            body = MultipartBody.Part.createFormData("image", imageFile.getName(), requestFile);
        }

        Map<String, RequestBody> fields = new HashMap<>();
        fields.put("title", RequestBody.create(MediaType.parse("text/plain"), report.getTitle()));
        fields.put("category", RequestBody.create(MediaType.parse("text/plain"), report.getWasteType()));
        fields.put("description", RequestBody.create(MediaType.parse("text/plain"), report.getDescription()));
        fields.put("latitude", RequestBody.create(MediaType.parse("text/plain"), String.valueOf(report.getLatitude())));
        fields.put("longitude", RequestBody.create(MediaType.parse("text/plain"), String.valueOf(report.getLongitude())));

        apiService.updateReport(targetId, body, fields).enqueue(new Callback<Report>() {
            @Override
            public void onResponse(Call<Report> call, Response<Report> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Report serverReport = response.body();
                    executorService.execute(() -> {
                        // Preserve local ID and photo path
                        serverReport.setId(report.getId());
                        serverReport.setLocalPhotoPath(report.getLocalPhotoPath());
                        serverReport.setSynced(true);
                        reportDao.insertReport(serverReport);
                    });
                    if (callback != null) callback.onSuccess(serverReport);
                } else {
                    String errorMsg = "Update failed: " + response.code();
                    try {
                        if (response.errorBody() != null) {
                            errorMsg += " - " + response.errorBody().string();
                        }
                    } catch (IOException ignored) {}
                    if (callback != null) callback.onError(errorMsg);
                }
            }

            @Override
            public void onFailure(Call<Report> call, Throwable t) {
                if (callback != null) callback.onError(t.getMessage());
            }
        });
    }

    private String saveImageToInternalStorage(File sourceFile) {
        File directory = application.getDir("report_images", Application.MODE_PRIVATE);
        String fileName = "IMG_" + UUID.randomUUID().toString() + ".jpg";
        File destinationFile = new File(directory, fileName);

        try (FileInputStream fis = new FileInputStream(sourceFile);
             FileOutputStream fos = new FileOutputStream(destinationFile);
             FileChannel source = fis.getChannel();
             FileChannel destination = fos.getChannel()) {
            destination.transferFrom(source, 0, source.size());
            return destinationFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e("ReportRepository", "Error saving image locally", e);
            return null;
        }
    }

    public void deleteReport(Report report, ApiCallback<String> callback) {
        String reportId = stableReportId(report);
        if (isBlank(reportId)) {
            if (callback != null) callback.onError("Report ID is required.");
            return;
        }

        apiService.deleteReportAdmin(reportId).enqueue(new Callback<com.google.gson.JsonElement>() {
            @Override
            public void onResponse(Call<com.google.gson.JsonElement> call,
                                   Response<com.google.gson.JsonElement> response) {
                if (!response.isSuccessful()) {
                    String error = readApiError(response, "delete");
                    if (callback != null) callback.onError(error);
                    else errorLiveData.postValue(error);
                    return;
                }
                executorService.execute(() -> reportDao.deleteByLocalId(report.getId()));
                fetchMyReports();
                if (callback != null) callback.onSuccess("Deleted successfully");
            }

            @Override
            public void onFailure(Call<com.google.gson.JsonElement> call, Throwable error) {
                String message = networkMessage(error);
                if (callback != null) callback.onError(message);
                else errorLiveData.postValue(message);
            }
        });
    }

    private interface BulkCallFactory {
        Call<com.google.gson.JsonElement> create(Map<String, List<String>> body);
    }

    private String stableReportId(Report report) {
        if (report == null) return null;
        if (!isBlank(report.getReportId())) return report.getReportId().trim();
        return isBlank(report.getMongoId()) ? null : report.getMongoId().trim();
    }

    private void cacheLifecycleResult(Report result) {
        if (result == null) return;
        result.setSynced(true);
        executorService.execute(() -> {
            Report local = null;
            if (!isBlank(result.getMongoId())) {
                local = reportDao.getReportByMongoId(result.getMongoId());
            }
            if (local == null && !isBlank(result.getReportId())) {
                local = reportDao.getReportByServerId(result.getReportId());
            }
            if (local != null) {
                result.setId(local.getId());
                result.setLocalPhotoPath(local.getLocalPhotoPath());
            }
            long localId = reportDao.insertReport(result);
            if (result.getId() == 0 && localId > 0) {
                result.setId((int) localId);
            }
            mergeRemoteReport(result);
        });
    }

    private void enqueueLifecycle(Call<com.google.gson.JsonElement> call,
                                  Report sourceReport,
                                  String operation,
                                  ApiCallback<Report> callback) {
        call.enqueue(new Callback<com.google.gson.JsonElement>() {
            @Override
            public void onResponse(Call<com.google.gson.JsonElement> call,
                                   Response<com.google.gson.JsonElement> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    String error = readApiError(response, operation);
                    if (callback != null) callback.onError(error);
                    else errorLiveData.postValue(error);
                    return;
                }

                try {
                    Report parsed = com.example.ecowatchadmin1.data.remote.AdminJsonParser
                            .parseReport(response.body());
                    Report result = ReportUpdateMerger.merge(sourceReport, parsed);
                    cacheLifecycleResult(result);
                    fetchMyReports();
                    if (callback != null) callback.onSuccess(result);
                } catch (RuntimeException error) {
                    String message = "The server returned an invalid report response.";
                    if (callback != null) callback.onError(message);
                    else errorLiveData.postValue(message);
                }
            }

            @Override
            public void onFailure(Call<com.google.gson.JsonElement> call, Throwable error) {
                String message = networkMessage(error);
                if (callback != null) callback.onError(message);
                else errorLiveData.postValue(message);
            }
        });
    }

    private void enqueueBulkLifecycle(List<Integer> localIds,
                                      BulkCallFactory factory,
                                      String operation) {
        executorService.execute(() -> {
            List<Report> reports = localIds == null
                    ? Collections.emptyList() : reportDao.getReportsByIds(localIds);
            List<String> serverIds = new ArrayList<>();
            for (Report report : reports) {
                String reportId = stableReportId(report);
                if (!isBlank(reportId) && !serverIds.contains(reportId)) {
                    serverIds.add(reportId);
                }
            }
            if (serverIds.isEmpty()) return;

            Map<String, List<String>> body = new HashMap<>();
            body.put("ids", serverIds);
            factory.create(body).enqueue(new Callback<com.google.gson.JsonElement>() {
                @Override
                public void onResponse(Call<com.google.gson.JsonElement> call,
                                       Response<com.google.gson.JsonElement> response) {
                    if (response.isSuccessful()) {
                        fetchMyReports();
                    } else {
                        errorLiveData.postValue(readApiError(response, operation));
                    }
                }

                @Override
                public void onFailure(Call<com.google.gson.JsonElement> call, Throwable error) {
                    errorLiveData.postValue(networkMessage(error));
                }
            });
        });
    }

    private void syncReportToServer(Report report, File imageFile, ApiCallback<Report> callback) {
        if (imageFile == null || !imageFile.exists()) {
            if (callback != null) callback.onError("Image file not found");
            return;
        }

        RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), imageFile);
        MultipartBody.Part body = MultipartBody.Part.createFormData("image", imageFile.getName(), requestFile);

        Map<String, RequestBody> fields = new HashMap<>();
        fields.put("title", RequestBody.create(MediaType.parse("text/plain"), report.getTitle()));
        fields.put("category", RequestBody.create(MediaType.parse("text/plain"), report.getWasteType()));
        fields.put("description", RequestBody.create(MediaType.parse("text/plain"), report.getDescription()));
        fields.put("latitude", RequestBody.create(MediaType.parse("text/plain"), String.valueOf(report.getLatitude())));
        fields.put("longitude", RequestBody.create(MediaType.parse("text/plain"), String.valueOf(report.getLongitude())));

        apiService.createReport(body, fields).enqueue(new Callback<Report>() {
            @Override
            public void onResponse(Call<Report> call, Response<Report> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Report serverReport = response.body();
                    executorService.execute(() -> {
                        reportDao.markAsSynced(report.getId(), serverReport.getReportId(), serverReport.getMongoId());
                    });
                    if (callback != null) callback.onSuccess(serverReport);
                } else {
                    if (callback != null) callback.onError("Sync failed: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<Report> call, Throwable t) {
                if (callback != null) callback.onError(t.getMessage());
            }
        });
    }

    /**
     * Assigns one active PERS-XXXX account. The administrator identity and
     * assignment timestamp are deliberately server-owned; only the selected
     * personnel, the resident-facing response and the personnel note are sent by
     * this client.
     */
    public void assignPersonnel(String reportId, String personnelId,
                                String adminResponse, String personnelNote,
                                String priority,
                                ApiCallback<Report> callback) {
        assignPersonnel(reportId,
                personnelId == null
                        ? Collections.emptyList() : Collections.singletonList(personnelId),
                null, null, null, adminResponse, personnelNote, priority, callback);
    }

    /**
     * Dispatches the crew and the truck the report's scale calls for.
     *
     * <p>A small report goes to one cleaner. A medium one goes to a cleaning
     * team, and a large one to a cleaning team with a collection vehicle, so
     * the crew and the truck travel with the assignment instead of being
     * worked out separately afterwards.
     */
    public void assignPersonnel(String reportId, List<String> personnelIds,
                                String wasteScale, String teamId, String vehicleId,
                                String adminResponse, String personnelNote,
                                String priority,
                                ApiCallback<Report> callback) {
        assignPersonnelUsing(apiService.assignPersonnel(reportId,
                assignmentBody(personnelIds, wasteScale, teamId, vehicleId,
                        adminResponse, personnelNote, priority)),
                callback);
    }

    /** Assigns or reassigns a report using the explicit reassignment route. */
    public void reassignPersonnel(String reportId, String personnelId,
                                  String adminResponse, String personnelNote,
                                  String priority,
                                  ApiCallback<Report> callback) {
        reassignPersonnel(reportId,
                personnelId == null
                        ? Collections.emptyList() : Collections.singletonList(personnelId),
                null, null, null, adminResponse, personnelNote, priority, callback);
    }

    /** Replaces the crew and the truck on a report that already has one. */
    public void reassignPersonnel(String reportId, List<String> personnelIds,
                                  String wasteScale, String teamId, String vehicleId,
                                  String adminResponse, String personnelNote,
                                  String priority,
                                  ApiCallback<Report> callback) {
        assignPersonnelUsing(apiService.reassignPersonnel(reportId,
                assignmentBody(personnelIds, wasteScale, teamId, vehicleId,
                        adminResponse, personnelNote, priority)),
                callback);
    }

    /** Retrieves the current server assignment for a report. */
    public void getReportAssignment(String reportId, ApiCallback<Report> callback) {
        if (isBlank(reportId)) {
            if (callback != null) callback.onError("Report ID is required.");
            return;
        }
        apiService.getReportAssignment(reportId).enqueue(new Callback<com.google.gson.JsonElement>() {
            @Override
            public void onResponse(Call<com.google.gson.JsonElement> call,
                                   Response<com.google.gson.JsonElement> response) {
                if (!response.isSuccessful()) {
                    if (callback != null) callback.onError(readApiError(response, "assignment"));
                    return;
                }
                try {
                    Report report = com.example.ecowatchadmin1.data.remote.AdminJsonParser
                            .parseReport(response.body());
                    if (callback != null) callback.onSuccess(report);
                } catch (RuntimeException error) {
                    if (callback != null) callback.onError("The server returned an invalid assignment.");
                }
            }

            @Override
            public void onFailure(Call<com.google.gson.JsonElement> call, Throwable error) {
                if (callback != null) callback.onError(networkMessage(error));
            }
        });
    }

    /** Removes the current assignment and returns the report to Pending. */
    public void unassignPersonnel(String reportId, ApiCallback<Report> callback) {
        if (isBlank(reportId)) {
            if (callback != null) callback.onError("Report ID is required.");
            return;
        }
        apiService.unassignPersonnel(reportId).enqueue(assignmentElementCallback(callback));
    }

    public void unassignPersonnel(String reportId, boolean useDeleteRoute,
                                  ApiCallback<Report> callback) {
        if (isBlank(reportId)) {
            if (callback != null) callback.onError("Report ID is required.");
            return;
        }
        Call<com.google.gson.JsonElement> call = useDeleteRoute
                ? apiService.deleteReportAssignment(reportId)
                : apiService.unassignPersonnel(reportId);
        call.enqueue(assignmentElementCallback(callback));
    }

    /** Updates administrator-owned report status and response. */
    public void updateReportStatus(String reportId, String status, String adminResponse,
                                   Report sourceReport, ApiCallback<Report> callback) {
        if (isBlank(reportId)) {
            if (callback != null) callback.onError("Report ID is required.");
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("adminResponse", adminResponse == null ? "" : adminResponse.trim());
        enqueueLifecycle(apiService.updateReportStatus(reportId.trim(), body), sourceReport,
                "update report status", callback);
    }

    /** Updates only the resident-visible administrator response. */
    public void updateReportResponse(String reportId, String adminResponse,
                                     Report sourceReport, ApiCallback<Report> callback) {
        if (isBlank(reportId)) {
            if (callback != null) callback.onError("Report ID is required.");
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("adminResponse", adminResponse == null ? "" : adminResponse.trim());
        enqueueLifecycle(apiService.updateReportResponse(reportId.trim(), body), sourceReport,
                "update admin response", callback);
    }

    /** Approves a completed cleanup through the administrator-owned route. */
    public void approveReport(String reportId, ApiCallback<Report> callback) {
        if (isBlank(reportId)) {
            if (callback != null) callback.onError("Report ID is required.");
            return;
        }
        enqueueLifecycle(apiService.approveReport(reportId.trim()), null,
                "approve completion", callback);
    }

    /**
     * Sends a reviewed report back to the cleaner for another attempt. The note
     * is required by the server and becomes the personnel-visible message.
     */
    public void reviseReport(String reportId, String adminResponse,
                             Report sourceReport, ApiCallback<Report> callback) {
        if (isBlank(reportId)) {
            if (callback != null) callback.onError("Report ID is required.");
            return;
        }
        String note = adminResponse == null ? "" : adminResponse.trim();
        if (note.isEmpty()) {
            if (callback != null) callback.onError("A revision note is required.");
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("adminResponse", note);
        enqueueLifecycle(apiService.reviseReport(reportId.trim(), body), sourceReport,
                "request revision", callback);
    }

    /** Compatibility entry point retained for older report screens. */
    public void assignPersonnel(String reportId, List<String> personnelIds,
                                String priority, ApiCallback<Report> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("assignedPersonnelIds", personnelIds == null
                ? Collections.emptyList() : personnelIds);
        if (priority != null && !priority.trim().isEmpty()) {
            body.put("priority", priority.trim());
        }
        // Keep the legacy array contract for older screens. New Phase 2B UI
        // calls the canonical single-personnel overload above.
        apiService.assignPersonnel(reportId, body)
                .enqueue(assignmentElementCallback(callback));
    }

    /**
     * The body of an assignment command.
     *
     * <p>The shape depends on how many people are going out. A single cleaner
     * is named outright, because the server keeps one canonical pointer to
     * them. A crew is sent as a list and has no single owner, so the canonical
     * pointer is left out — sending both would make the server read the
     * request as a contradiction and refuse it.
     *
     * <p>The scale travels with the assignment so the server can check the
     * dispatch against it: a crew for a medium job, a crew and a truck for a
     * large one, and neither for a small one.
     */
    private Map<String, Object> assignmentBody(List<String> personnelIds, String wasteScale,
                                                String teamId, String vehicleId,
                                                String adminResponse, String personnelNote,
                                                String priority) {
        Map<String, Object> body = new HashMap<>();
        List<String> ids = new ArrayList<>();
        if (personnelIds != null) {
            for (String id : personnelIds) {
                if (id == null) continue;
                String trimmed = id.trim();
                if (!trimmed.isEmpty()) ids.add(trimmed);
            }
        }

        if (ids.size() == 1) {
            body.put("personnelId", ids.get(0));
            body.put("assignedPersonnelId", ids.get(0));
        } else {
            body.put("assignedPersonnelIds", ids);
        }

        if (!isBlank(wasteScale)) body.put("wasteScale", wasteScale.trim());
        if (!isBlank(teamId)) body.put("teamId", teamId.trim());
        if (!isBlank(vehicleId)) body.put("vehicleId", vehicleId.trim());

        body.put("adminResponse", adminResponse == null ? "" : adminResponse.trim());
        // Only sent when the admin actually wrote something. Omitting it leaves
        // the stored note untouched instead of clearing instructions the cleaner
        // still needs.
        if (personnelNote != null && !personnelNote.trim().isEmpty()) {
            body.put("personnelNote", personnelNote.trim());
        }
        if (priority != null && !priority.trim().isEmpty()) body.put("priority", priority.trim());
        return body;
    }

    private void assignPersonnelUsing(Call<com.google.gson.JsonElement> call,
                                      ApiCallback<Report> callback) {
        call.enqueue(assignmentElementCallback(callback));
    }

    private Callback<com.google.gson.JsonElement> assignmentElementCallback(
            ApiCallback<Report> callback) {
        return new Callback<com.google.gson.JsonElement>() {
            @Override
            public void onResponse(Call<com.google.gson.JsonElement> call,
                                   Response<com.google.gson.JsonElement> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        Report parsed = com.example.ecowatchadmin1.data.remote.AdminJsonParser
                                .parseReport(response.body());
                        Report result = ReportUpdateMerger.merge(findRemoteReport(parsed), parsed);
                        result.setSynced(true);
                        executorService.execute(() -> {
                            Report local = reportDao.getReportByMongoId(result.getMongoId());
                            if (local == null) local = reportDao.getReportByServerId(result.getReportId());
                            if (local != null) {
                                result.setId(local.getId());
                                result.setLocalPhotoPath(local.getLocalPhotoPath());
                            }
                            Report merged = ReportUpdateMerger.merge(local, result);
                            reportDao.insertReport(merged);
                            mergeRemoteReport(merged);
                        });
                        if (callback != null) callback.onSuccess(result);
                    } catch (RuntimeException error) {
                        if (callback != null) callback.onError("The server returned an invalid assignment.");
                    }
                } else if (callback != null) {
                    callback.onError(readApiError(response, "assignment"));
                }
            }

            @Override
            public void onFailure(Call<com.google.gson.JsonElement> call, Throwable error) {
                if (callback != null) callback.onError(networkMessage(error));
            }
        };
    }

    private Report findRemoteReport(Report candidate) {
        if (candidate == null) return null;
        List<Report> current = remoteReports.getValue();
        if (current == null) return null;
        for (Report report : current) {
            if (report == null) continue;
            if (candidate.getReportId() != null
                    && candidate.getReportId().equalsIgnoreCase(report.getReportId())) {
                return report;
            }
            if (candidate.getMongoId() != null
                    && candidate.getMongoId().equalsIgnoreCase(report.getMongoId())) {
                return report;
            }
        }
        return null;
    }

    private void mergeRemoteReport(Report result) {
        List<Report> current = remoteReports.getValue();
        List<Report> merged = new ArrayList<>();
        if (current != null) {
            for (Report report : current) {
                if (report != null && result.getReportId() != null
                        && result.getReportId().equalsIgnoreCase(report.getReportId())) {
                    continue;
                }
                if (report != null && result.getMongoId() != null
                        && result.getMongoId().equalsIgnoreCase(report.getMongoId())) {
                    continue;
                }
                merged.add(report);
            }
        }
        merged.add(result);
        remoteReports.postValue(merged);
    }

    public void fetchMyReports() {
        if (!reportsRequestInFlight.compareAndSet(false, true)) {
            reportsRefreshQueued.set(true);
            return;
        }

        loadingLiveData.postValue(true);

        adminRepository.getReports(new AdminRepository.ReportsCallback() {
            @Override
            public void onSuccess(List<Report> reports) {
                List<Report> serverReports = reports == null
                        ? Collections.emptyList()
                        : new ArrayList<>(reports);

                remoteReports.postValue(serverReports);
                errorLiveData.postValue(null);
                loadingLiveData.postValue(false);
                reportsRequestInFlight.set(false);
                if (reportsRefreshQueued.getAndSet(false)) {
                    fetchMyReports();
                }

                // Keep a cache for legacy mutation code, but never expose it as
                // the source of truth to the map or reports UI. The audit trail is
                // not touched from here: its rows are the server's to decide, and
                // inferring them from a report's status change invents events.
                executorService.execute(() -> {
                    try {
                        cacheReports(serverReports);
                    } catch (RuntimeException cacheError) {
                        Log.e("ReportRepository", "Report cache update failed", cacheError);
                    }
                });
            }

            @Override
            public void onError(String error) {
                loadingLiveData.postValue(false);
                errorLiveData.postValue(error);
                reportsRequestInFlight.set(false);
                if (reportsRefreshQueued.getAndSet(false)) {
                    fetchMyReports();
                }
                Log.e("ReportRepository", "Failed to fetch admin reports: " + error);
            }
        });
    }

    private void cacheReports(List<Report> reports) {
        if (reports == null) return;

        for (Report serverReport : reports) {
            Report localReport = null;
            if (serverReport.getMongoId() != null && !serverReport.getMongoId().isEmpty()) {
                localReport = reportDao.getReportByMongoId(serverReport.getMongoId());
            }
            if (localReport == null && serverReport.getReportId() != null
                    && !serverReport.getReportId().isEmpty()) {
                localReport = reportDao.getReportByServerId(serverReport.getReportId());
            }

            if (localReport != null) {
                serverReport.setId(localReport.getId());
                serverReport.setLocalPhotoPath(localReport.getLocalPhotoPath());
            }

            serverReport.setSynced(true);
            long localId = reportDao.insertReport(serverReport);
            if (serverReport.getId() == 0 && localId > 0) {
                serverReport.setId((int) localId);
            }
        }
    }

    private String readApiError(Response<?> response, String operation) {
        if (response.code() == 401) {
            sessionManager.clearSession();
            return "Your administrator session has expired. Please log in again.";
        }
        try {
            if (response.errorBody() != null) {
                String raw = response.errorBody().string();
                if (raw != null && !raw.trim().isEmpty()) {
                    try {
                        com.google.gson.JsonElement parsed =
                                new com.google.gson.JsonParser().parse(raw);
                        if (parsed.isJsonObject()) {
                            com.google.gson.JsonObject object = parsed.getAsJsonObject();
                            if (object.has("message")) return object.get("message").getAsString();
                            if (object.has("error")) return object.get("error").getAsString();
                        }
                    } catch (RuntimeException ignored) {
                        // Use the status-specific fallback.
                    }
                }
            }
        } catch (IOException ignored) {
            // Use the status-specific fallback.
        }
        if (response.code() == 409) return "That personnel account is unavailable or already assigned.";
        if (response.code() >= 500) return "The EcoWatch server is temporarily unavailable. Try again later.";
        return "The " + operation + " request failed (" + response.code() + ").";
    }

    private String networkMessage(Throwable error) {
        if (error instanceof java.net.SocketTimeoutException) {
            return "The server took too long to respond. Try again.";
        }
        if (error instanceof IOException) {
            return "Unable to reach the EcoWatch server. Check your connection and try again.";
        }
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Network error occurred. Please try again."
                : message;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }
}
