package com.example.ecowatchadmin1.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.ecowatchadmin1.data.models.Notification;

import java.util.List;

/** Cloned from the Resident app; every query is owner-scoped. */
@Dao
public interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertNotification(Notification notification);

    @Query("SELECT * FROM notifications WHERE residentId = :residentId AND mongoId = :mongoId LIMIT 1")
    Notification getByMongoId(String residentId, String mongoId);

    @Query("SELECT * FROM notifications WHERE residentId = :residentId AND reportId = :reportId ORDER BY createdAt DESC LIMIT 1")
    Notification getLatestForReport(String residentId, String reportId);

    @Query("SELECT COUNT(*) FROM notifications WHERE residentId = :residentId AND isRead = 0 AND isDeleted = 0")
    int getUnreadCount(String residentId);

    @Query("SELECT COUNT(*) FROM notifications WHERE residentId = :residentId AND isRead = 0 AND isDeleted = 0")
    LiveData<Integer> observeUnreadCount(String residentId);

    @Query("UPDATE notifications SET isRead = 1 WHERE residentId = :residentId AND mongoId = :mongoId")
    void markAsRead(String residentId, String mongoId);

    @Query("UPDATE notifications SET isRead = 0 WHERE residentId = :residentId AND mongoId = :mongoId")
    void markAsUnread(String residentId, String mongoId);

    @Query("UPDATE notifications SET isRead = 1 WHERE residentId = :residentId AND isDeleted = 0")
    void markAllAsRead(String residentId);

    @Query("SELECT * FROM notifications WHERE residentId = :residentId AND isDeleted = 0 ORDER BY createdAt DESC")
    LiveData<List<Notification>> getAllNotifications(String residentId);

    @Query("SELECT * FROM notifications WHERE residentId = :residentId AND isDeleted = 0 AND type IN (:adminTypes) ORDER BY createdAt DESC")
    LiveData<List<Notification>> getAdminNotifications(String residentId, List<String> adminTypes);

    @Query("SELECT * FROM notifications WHERE residentId = :residentId AND isDeleted = 1 ORDER BY COALESCE(deletedAt, createdAt) DESC")
    LiveData<List<Notification>> getTrashNotifications(String residentId);

    @Query("UPDATE notifications SET isDeleted = 1, deletedAt = :deletedAt WHERE residentId = :residentId AND id = :id")
    void softDelete(String residentId, int id, String deletedAt);

    @Query("UPDATE notifications SET isDeleted = 0, deletedAt = NULL WHERE residentId = :residentId AND id = :id")
    void restore(String residentId, int id);

    @Query("DELETE FROM notifications WHERE residentId = :residentId AND id = :id")
    void permanentDelete(String residentId, int id);

    @Query("SELECT * FROM notifications WHERE residentId = :residentId AND id IN (:ids)")
    List<Notification> getByIds(String residentId, List<Integer> ids);

    @Query("DELETE FROM notifications WHERE residentId = :residentId AND id IN (:ids)")
    void bulkDelete(String residentId, List<Integer> ids);

    @Query("DELETE FROM notifications WHERE residentId = :residentId")
    void deleteAllForResident(String residentId);
}
