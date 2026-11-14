package com.example.ecowatchadmin1.data.remote;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Retrofit contract for the admin-only EcoWatch backend endpoints. */
public interface AdminApiService {

    @POST("admin/login")
    Call<JsonObject> login(@Body AdminLoginRequest request);

    @GET("admin/me")
    Call<JsonObject> getCurrentAdmin();

    /** Transitional fallback for a Render deployment before /admin/me exists. */
    @GET("auth/me")
    Call<JsonObject> getLegacyCurrentAdmin();

    @GET("admin/reports")
    Call<JsonElement> getReports();

    /** Reporter account management (admin-only). */
    @GET("admin/residents")
    Call<JsonElement> getResidents(@Query("search") String search,
                                    @Query("status") String status,
                                    @Query("limit") int limit,
                                    @Query("offset") int offset);

    @GET("admin/residents/{residentId}")
    Call<JsonElement> getResidentDetails(@Path("residentId") String residentId);

    @GET("admin/residents/{residentId}/reports")
    Call<JsonElement> getResidentReports(@Path("residentId") String residentId,
                                         @Query("page") int page,
                                         @Query("limit") int limit);

    @GET("admin/residents/{residentId}/reports/statistics")
    Call<JsonElement> getResidentReportStatistics(@Path("residentId") String residentId);

    /**
     * Sets a reporter's password. The administrator picks it and the server
     * echoes it back so it can be handed over; nothing is emailed.
     */
    @POST("admin/residents/{residentId}/reset-password")
    Call<JsonElement> resetResidentPassword(@Path("residentId") String residentId,
                                            @Body ResidentPasswordRequest request);

    /**
     * Pauses a reporter account. Preserves the profile, reports and history;
     * unlike the resident's own /auth/disable-account, the reporter cannot
     * restore it by signing in again.
     */
    @PATCH("admin/residents/{residentId}/disable")
    Call<JsonElement> disableResident(@Path("residentId") String residentId);

    /**
     * The undo for {@link #disableResident}. An admin-disabled reporter carries
     * no self-service marker, so they cannot restore their own account by
     * signing in; this is the only way back.
     */
    @PATCH("admin/residents/{residentId}/enable")
    Call<JsonElement> enableResident(@Path("residentId") String residentId);

    /**
     * Permanently removes a reporter account and every row it owns.
     *
     * <p>Irreversible, and the only account action here with no undo: the enable
     * route restores a paused account because its rows are still there, and by
     * this point they are not. The server deletes the reports across the active,
     * archived and trash collections, the report history, the notifications and
     * the uploaded photos, so the client has nothing left to clean up.
     */
    @DELETE("admin/residents/{residentId}")
    Call<JsonElement> deleteResident(@Path("residentId") String residentId);

    /* Administrator-owned report lifecycle and completion actions. */
    @POST("admin/reports/{id}/move-to-archive")
    Call<JsonElement> moveReportToArchive(@Path("id") String reportId);

    @POST("admin/reports/{id}/move-to-trash")
    Call<JsonElement> moveReportToTrash(@Path("id") String reportId);

    @POST("admin/reports/{id}/restore")
    Call<JsonElement> restoreReport(@Path("id") String reportId);

    @DELETE("admin/reports/{id}")
    Call<JsonElement> deleteReport(@Path("id") String reportId);

    @POST("admin/reports/{id}/approve")
    Call<JsonElement> approveReport(@Path("id") String reportId);

    @POST("admin/reports/bulk-move-to-archive")
    Call<JsonElement> bulkMoveToArchive(@Body java.util.Map<String, java.util.List<String>> body);

    @POST("admin/reports/bulk-move-to-trash")
    Call<JsonElement> bulkMoveToTrash(@Body java.util.Map<String, java.util.List<String>> body);

    @POST("admin/reports/bulk-restore")
    Call<JsonElement> bulkRestore(@Body java.util.Map<String, java.util.List<String>> body);

    @POST("admin/reports/bulk-delete")
    Call<JsonElement> bulkDelete(@Body java.util.Map<String, java.util.List<String>> body);

    /**
     * Administrator notification choices.
     *
     * <p>Read on launch so the settings survive a reinstall and agree across
     * devices, and written per toggle so the server can honor them when it
     * decides who to push to.
     */
    @GET("notifications/admin-preferences")
    Call<AdminNotificationPreferencesResponse> getAdminNotificationPreferences();

    @PUT("notifications/admin-preferences")
    Call<AdminNotificationPreferencesResponse> updateAdminNotificationPreferences(
            @Body AdminNotificationPreferencesRequest preferences);

    /* Personnel management (admin-only). */
    /**
     * Binds this device's FCM token to the authenticated administrator so
     * {@code NEW_REPORT} pushes are delivered. The shared OkHttp auth
     * interceptor already supplies the admin JWT.
     */
    @POST("admin/fcm-token")
    Call<Map<String, Object>> registerAdminFcmToken(@Body FcmTokenRequest body);

    /** Releases the device token so a later login can rebind it. */
    @DELETE("admin/fcm-token")
    Call<Void> clearAdminFcmToken();

    @GET("admin/personnel")
    Call<JsonElement> getPersonnel(@Query("includeDisabled") boolean includeDisabled,
                                   @Query("limit") int limit,
                                   @Query("offset") int offset);

    @GET("admin/personnel/{personnelId}")
    Call<JsonElement> getPersonnelDetails(@Path("personnelId") String personnelId);

    /** Reports currently assigned to one personnel member, newest first. */
    @GET("admin/personnel/{personnelId}/reports")
    Call<JsonElement> getPersonnelReports(@Path("personnelId") String personnelId,
                                          @Query("page") int page,
                                          @Query("limit") int limit);

    /** Aggregate counts behind the assigned-task stat cards. */
    @GET("admin/personnel/{personnelId}/reports/statistics")
    Call<JsonElement> getPersonnelReportStatistics(@Path("personnelId") String personnelId);

    /**
     * Task counts counted from the cleaner's assignment rows.
     *
     * <p>This, not the report statistics above, is what the personnel detail
     * cards read: a report only counts as completed once an administrator
     * approves it, so report-status aggregates under-report finished work.
     */
    @GET("admin/personnel/{personnelId}/task-counts")
    Call<JsonElement> getPersonnelTaskCounts(@Path("personnelId") String personnelId);

    @POST("admin/personnel")
    Call<JsonElement> createPersonnel(@Body PersonnelCreateRequest request);

    /**
     * Stores the personnel photograph captured while the account is created.
     * The server answers with the updated personnel record, so the caller can
     * keep using the same object it created.
     */
    @Multipart
    @POST("admin/personnel/{personnelId}/photo")
    Call<JsonElement> uploadPersonnelPhoto(@Path("personnelId") String personnelId,
                                           @Part MultipartBody.Part image);

    @PATCH("admin/personnel/{personnelId}")
    Call<JsonElement> updatePersonnel(@Path("personnelId") String personnelId,
                                      @Body PersonnelUpdateRequest request);

    @POST("admin/personnel/{personnelId}/reset-password")
    Call<JsonElement> resetPersonnelPassword(@Path("personnelId") String personnelId,
                                            @Body PersonnelPasswordRequest request);

    @PATCH("admin/personnel/{personnelId}/status")
    Call<JsonElement> updatePersonnelStatus(@Path("personnelId") String personnelId,
                                           @Body PersonnelStatusRequest request);

    @PATCH("admin/personnel/{personnelId}/disable")
    Call<JsonElement> disablePersonnel(@Path("personnelId") String personnelId);

    @DELETE("admin/personnel/{personnelId}")
    Call<JsonElement> deletePersonnel(@Path("personnelId") String personnelId);

    /** Future Personnel App login route; kept here so deployments expose one contract. */
    @POST("personnel/login")
    Call<JsonElement> personnelLogin(@Body PersonnelLoginRequest request);

    /**
     * Compatibility endpoint used only when the deployed Render service still
     * exposes the legacy public auth route instead of /admin/login. The
     * repository first calls /admin/login; this is never a mock fallback.
     */
    @POST("auth/login")
    Call<JsonObject> legacyLogin(@Body AdminLoginRequest request);

    /* Vehicle fleet (admin-only). */

    /**
     * The whole live fleet, optionally narrowed to one status.
     *
     * <p>The response carries the summary counts alongside the list because the
     * cards and the rows are always read together, and two reads could otherwise
     * disagree about what the fleet looked like.
     */
    @GET("vehicles")
    Call<JsonElement> getVehicles(@Query("status") String status);

    /** The three card numbers on their own. */
    @GET("vehicles/status-counts")
    Call<JsonElement> getVehicleCounts();

    @GET("vehicles/{vehicleId}")
    Call<JsonElement> getVehicleDetails(@Path("vehicleId") String vehicleId);

    /** Assignment-derived work counts for one vehicle's details page. */
    @GET("vehicles/{vehicleId}/task-counts")
    Call<JsonElement> getVehicleTaskCounts(@Path("vehicleId") String vehicleId);

    /** Paginated completed and revision-requested work for one vehicle. */
    @GET("vehicles/{vehicleId}/task-history")
    Call<JsonElement> getVehicleTaskHistory(@Path("vehicleId") String vehicleId,
                                            @Query("page") int page,
                                            @Query("limit") int limit);

    @POST("vehicles")
    Call<JsonElement> createVehicle(@Body VehicleCreateRequest request);

    @PATCH("vehicles/{vehicleId}")
    Call<JsonElement> updateVehicle(@Path("vehicleId") String vehicleId,
                                    @Body VehicleUpdateRequest request);

    /** Stamps the last service date from the maintenance screen. */
    @PATCH("vehicles/{vehicleId}/service")
    Call<JsonElement> recordVehicleService(@Path("vehicleId") String vehicleId,
                                           @Body VehicleServiceRequest request);

    @DELETE("vehicles/{vehicleId}")
    Call<JsonElement> deleteVehicle(@Path("vehicleId") String vehicleId);

    /* Cleaning teams (admin-only). */

    /**
     * Every crew the administrator can dispatch.
     *
     * <p>Needed for a report filed under Medium- or Large-Scale Waste: the
     * scale decides that a crew goes out, and the crew has to be one the
     * administrator named in the first place.
     */
    @GET("admin/cleaning-teams")
    Call<JsonElement> getCleaningTeams();

    /** One crew together with the people on it. */
    @GET("admin/cleaning-teams/{teamId}")
    Call<JsonElement> getCleaningTeam(@Path("teamId") String teamId);

    @GET("admin/cleaning-teams/{teamId}/task-history")
    Call<JsonElement> getCleaningTeamTaskHistory(@Path("teamId") String teamId,
                                                 @Query("page") int page,
                                                 @Query("limit") int limit);

    /**
     * Creates a team with its leader and its first crew.
     *
     * <p>Membership travels with the name rather than as a second request: a
     * team with nobody on it cannot be dispatched and would only be a name in a
     * list until somebody remembered to finish it.
     */
    @POST("admin/cleaning-teams")
    Call<JsonElement> createCleaningTeam(@Body CleaningTeamRequest request);

    /** Renames a team, changes its leader, or replaces its crew wholesale. */
    @PUT("admin/cleaning-teams/{teamId}")
    Call<JsonElement> updateCleaningTeam(@Path("teamId") String teamId,
                                         @Body CleaningTeamRequest request);

    /** Soft-deletes a team. The id stays readable on reports it was sent to. */
    @DELETE("admin/cleaning-teams/{teamId}")
    Call<JsonElement> deleteCleaningTeam(@Path("teamId") String teamId);

    /** Adds one person to a crew without touching the rest of it. */
    @POST("admin/cleaning-teams/{teamId}/members")
    Call<JsonElement> addCleaningTeamMember(@Path("teamId") String teamId,
                                            @Body java.util.Map<String, String> body);

    /** Removes one person from a crew, leaving the rest of it intact. */
    @DELETE("admin/cleaning-teams/{teamId}/members/{personnelId}")
    Call<JsonElement> removeCleaningTeamMember(@Path("teamId") String teamId,
                                               @Path("personnelId") String personnelId);

    /* Possible Duplicate Reports review (admin-only). */

    /** Lists groups of reports that may be duplicates. */
    @GET("admin/possible-duplicates")
    Call<JsonElement> getPossibleDuplicates();

    /** Marks the chosen reports as confirmed duplicates. */
    @POST("admin/possible-duplicates/{groupId}/deny")
    Call<JsonElement> denyPossibleDuplicateGroup(@Path("groupId") String groupId,
                                                 @Body java.util.Map<String, Object> body);

    /** Confirms the reports in a group are not duplicates. */
    @POST("admin/possible-duplicates/{groupId}/accept")
    Call<JsonElement> acceptPossibleDuplicateGroup(@Path("groupId") String groupId);
}
