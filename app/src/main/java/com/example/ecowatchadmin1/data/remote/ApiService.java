package com.example.ecowatchadmin1.data.remote;

import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.models.User;
import com.example.ecowatchadmin1.data.models.Notification;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PATCH;
import retrofit2.http.Part;
import retrofit2.http.PartMap;
import retrofit2.http.Query;

public interface ApiService {

    @POST("auth/register")
    Call<Map<String, Object>> register(@Body Map<String, String> body);

    @POST("auth/verify-otp")
    Call<Map<String, Object>> verifyOTP(@Body Map<String, String> body);

    @POST("auth/forgot-password")
    Call<Map<String, Object>> forgotPassword(@Body Map<String, String> body);

    @POST("auth/verify-reset-otp")
    Call<Map<String, Object>> verifyResetOTP(@Body Map<String, String> body);

    @POST("auth/reset-password")
    Call<Map<String, Object>> resetPassword(@Body Map<String, String> body);

    @GET("auth/me")
    Call<Map<String, Object>> getProfile();

    @PATCH("auth/profile")
    Call<Map<String, Object>> updateProfile(@Body Map<String, String> body);

    @POST("auth/change-password")
    Call<Map<String, Object>> changePassword(@Body Map<String, String> body);

    @PATCH("auth/reset-logged-in")
    Call<Map<String, Object>> resetLoggedInPassword(@Body Map<String, String> body);

    @Multipart
    @PATCH("auth/profile/image")
    Call<Map<String, Object>> updateProfileImage(@Part MultipartBody.Part image);

    @GET("reports/my-reports")
    Call<List<Report>> getMyReports();

    @Multipart
    @POST("reports")
    Call<Report> createReport(
            @Part MultipartBody.Part image,
            @PartMap Map<String, RequestBody> fields
    );

    @retrofit2.http.DELETE("reports/{id}")
    Call<Map<String, Object>> deleteReport(@retrofit2.http.Path("id") String reportId);

    @Multipart
    @PATCH("reports/{id}")
    Call<Report> updateReport(
            @retrofit2.http.Path("id") String reportId,
            @Part MultipartBody.Part image,
            @PartMap Map<String, RequestBody> fields
    );

    // Admin Endpoints
    @POST("admin/reports/{id}/assign")
    Call<com.google.gson.JsonElement> assignPersonnel(@retrofit2.http.Path("id") String reportId,
                                                      @Body Map<String, Object> body);

    @POST("admin/reports/{id}/reassign")
    Call<com.google.gson.JsonElement> reassignPersonnel(@retrofit2.http.Path("id") String reportId,
                                                        @Body Map<String, Object> body);

    @GET("admin/reports/{id}/assignment")
    Call<com.google.gson.JsonElement> getReportAssignment(
            @retrofit2.http.Path("id") String reportId);

    @POST("admin/reports/{id}/unassign")
    Call<com.google.gson.JsonElement> unassignPersonnel(@retrofit2.http.Path("id") String reportId);

    @DELETE("admin/reports/{id}/assignment")
    Call<com.google.gson.JsonElement> deleteReportAssignment(@retrofit2.http.Path("id") String reportId);

    /** Administrator-owned report lifecycle commands. */
    @POST("admin/reports/{id}/move-to-archive")
    Call<com.google.gson.JsonElement> moveReportToArchiveAdmin(
            @retrofit2.http.Path("id") String reportId);

    @POST("admin/reports/{id}/move-to-trash")
    Call<com.google.gson.JsonElement> moveReportToTrashAdmin(
            @retrofit2.http.Path("id") String reportId);

    @POST("admin/reports/{id}/restore")
    Call<com.google.gson.JsonElement> restoreReportAdmin(
            @retrofit2.http.Path("id") String reportId);

    @DELETE("admin/reports/{id}")
    Call<com.google.gson.JsonElement> deleteReportAdmin(
            @retrofit2.http.Path("id") String reportId);

    @PATCH("admin/reports/{id}/status")
    Call<com.google.gson.JsonElement> updateReportStatus(
            @retrofit2.http.Path("id") String reportId,
            @Body Map<String, Object> body);

    @PATCH("admin/reports/{id}/response")
    Call<com.google.gson.JsonElement> updateReportResponse(
            @retrofit2.http.Path("id") String reportId,
            @Body Map<String, Object> body);

    @POST("admin/reports/{id}/approve")
    Call<com.google.gson.JsonElement> approveReport(
            @retrofit2.http.Path("id") String reportId);

    /** Sends reviewed work back to the cleaner with a required note. */
    @POST("admin/reports/{id}/revise")
    Call<com.google.gson.JsonElement> reviseReport(
            @retrofit2.http.Path("id") String reportId,
            @Body Map<String, Object> body);

    @POST("admin/reports/bulk-move-to-archive")
    Call<com.google.gson.JsonElement> bulkMoveToArchiveAdmin(
            @Body Map<String, List<String>> body);

    @POST("admin/reports/bulk-move-to-trash")
    Call<com.google.gson.JsonElement> bulkMoveToTrashAdmin(
            @Body Map<String, List<String>> body);

    @POST("admin/reports/bulk-restore")
    Call<com.google.gson.JsonElement> bulkRestoreAdmin(
            @Body Map<String, List<String>> body);

    @POST("admin/reports/bulk-delete")
    Call<com.google.gson.JsonElement> bulkDeleteAdmin(
            @Body Map<String, List<String>> body);

    @GET("admin/personnel")
    Call<List<User>> getPersonnel();

    @POST("admin/create-personnel")
    Call<Map<String, Object>> createPersonnel(@Body Map<String, String> body);

    @POST("reports/{id}/move-to-archive")
    Call<Map<String, Object>> moveToArchive(@retrofit2.http.Path("id") String reportId);

    @POST("reports/{id}/move-to-trash")
    Call<Map<String, Object>> moveToTrash(@retrofit2.http.Path("id") String reportId);

    @POST("reports/{id}/restore")
    Call<Map<String, Object>> restoreFromCollection(@retrofit2.http.Path("id") String reportId);

    @POST("reports/bulk-move-to-archive")
    Call<Map<String, Object>> bulkMoveToArchive(@Body Map<String, List<String>> body);

    @POST("reports/bulk-move-to-trash")
    Call<Map<String, Object>> bulkMoveToTrash(@Body Map<String, List<String>> body);

    @POST("reports/bulk-restore")
    Call<Map<String, Object>> bulkRestore(@Body Map<String, List<String>> body);

    @POST("reports/bulk-delete")
    Call<Map<String, Object>> bulkDelete(@Body Map<String, List<String>> body);

    /**
     * The administrator's audit feed: every decision, across every resident.
     *
     * <p>This is the only history feed this app reads. There is deliberately no
     * resident-scoped {@code history/my-history} route bound here: it answers
     * "what did one resident do", which an administrator cannot use, and every
     * row it returned was written into the same cache this screen reads, so the
     * trail ended up showing the administrator's own id against other people's
     * activity. The admin route can answer it because it spans every resident and
     * files each decision against whoever acted.
     *
     * <p>Filtering, searching and paging are done server-side so a wide feed is
     * not shipped in full only to be discarded on the device.
     */
    @GET("admin/history")
    Call<AdminHistoryResponse> getAdminHistory(
            @Query("category") String category,
            @Query("search") String search,
            @Query("from") String from,
            @Query("to") String to,
            @Query("limit") int limit,
            @Query("offset") int offset
    );

    @GET("admin/history/trash")
    Call<AdminHistoryResponse> getAdminHistoryTrash(
            @Query("limit") int limit,
            @Query("offset") int offset
    );

    /* Administrator-side audit-trail management. Scoped by history id rather than
       by resident, because an announcement or account record has no resident.

       The payload is {"ids":[...]} keyed on the server's history_id, so the value
       type is a list even when a single record is being acted on. It was declared
       as a bare Integer, which no caller could satisfy without building a
       malformed body. */
    @POST("admin/history/bulk-soft-delete")
    Call<Map<String, Object>> bulkSoftDeleteAdminHistory(@Body Map<String, List<Integer>> body);

    @POST("admin/history/bulk-restore")
    Call<Map<String, Object>> bulkRestoreAdminHistory(@Body Map<String, List<Integer>> body);

    @POST("admin/history/bulk-delete")
    Call<Map<String, Object>> bulkDeleteAdminHistory(@Body Map<String, List<Integer>> body);

    /* EcoWatch notification center. Shared with the Resident app so admin
       unread/read/trash behavior and server-owned timestamps stay identical. */
    @GET("notifications/my-notifications")
    Call<List<Notification>> getMyNotifications();

    @GET("notifications/trash")
    Call<List<Notification>> getMyTrashNotifications();

    @GET("notifications/unread-count")
    Call<UnreadCountResponse> getUnreadCount();

    @PATCH("notifications/{id}/read")
    Call<Map<String, Object>> markNotificationAsRead(
            @retrofit2.http.Path("id") String notificationId
    );

    @PATCH("notifications/mark-as-read")
    Call<Map<String, Object>> markAllNotificationsAsRead();

    @POST("notifications/{id}/move-to-trash")
    Call<Map<String, Object>> moveNotificationToTrash(@retrofit2.http.Path("id") String mongoId);

    @POST("notifications/{id}/restore")
    Call<Map<String, Object>> restoreNotification(@retrofit2.http.Path("id") String mongoId);

    @retrofit2.http.DELETE("notifications/{id}")
    Call<Map<String, Object>> deleteNotification(@retrofit2.http.Path("id") String mongoId);
}
