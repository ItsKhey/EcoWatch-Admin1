package com.example.ecowatchadmin1.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.example.ecowatchadmin1.utils.LongDateAdapter;
import java.io.Serializable;

@Entity(tableName = "history")
public class History implements Serializable {
    @PrimaryKey(autoGenerate = true)
    private int id;

    /**
     * The server's identifier for this record.
     *
     * <p>Two routes supply it under two names: the resident feed sends {@code _id},
     * the administrator audit feed sends {@code history_id}. Both are accepted so
     * one entity serves both, and the admin feed can address a record for bulk
     * operations with the same field.
     */
    @SerializedName(value = "_id", alternate = {"history_id", "mongoId"})
    private String mongoId;

    /**
     * The resident the record concerns.
     *
     * <p>Null on an account record, which belongs to no resident.
     */
    @SerializedName("resident_id")
    private String residentId;

    /** The report this record is about; null for records with no report. */
    @SerializedName("report_id")
    private String reportId;

    private String action;

    @SerializedName("old_status")
    private String oldStatus;

    @SerializedName("new_status")
    private String newStatus;

    private String description;

    /**
     * The administrator who made the decision, when the server recorded one.
     *
     * <p>Absent on rows written by the resident app and on rows written by the
     * scheduled report cleanup, where nobody decided anything. The absence is
     * meaningful: an administrator can tell an expiry apart from a choice.
     */
    @SerializedName("admin_id")
    private String actorId;

    private String actionDetails;

    /**
     * Which Admin History tab the server filed this row under.
     *
     * <p>Stored server-side rather than derived from the action string, because
     * several constants straddle two tabs. Null on rows written before the
     * column existed, and treated as Reports, which is what the clients did
     * before it did.
     */
    private String category;

    /** 'PERSONNEL', for records with no report to join to. */
    @SerializedName("subject_type")
    private String subjectType;

    /** Stable server id of that subject. */
    @SerializedName("subject_id")
    private String subjectId;

    /** Human-readable subject, so a card renders without a second lookup. */
    @SerializedName("subject_label")
    private String subjectLabel;

    @SerializedName("timestamp")
    @JsonAdapter(LongDateAdapter.class)
    private Long timestampValue; 
    
    /**
     * Whether the server has moved this record to the trash.
     *
     * <p>This is the flag the Trash view is selected by, so it has to come from the
     * server rather than be assumed. It previously had no serialized name, which
     * left it permanently false for every parsed row: the trash query could never
     * match anything, and a trashed record rendered as a live one on the feed.
     */
    @SerializedName("is_deleted")
    private boolean isDeleted;

    /**
     * When the record was moved to the trash, which is what the trash list orders
     * by. Annotated for the server's column name; it had been bound to a camelCase
     * key the API never sends, so it always arrived null.
     */
    @SerializedName(value = "deleted_at", alternate = {"deletedAt"})
    @JsonAdapter(LongDateAdapter.class)
    private Long deletedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getMongoId() { return mongoId; }
    public void setMongoId(String mongoId) { this.mongoId = mongoId; }

    public String getResidentId() { return residentId; }
    public void setResidentId(String residentId) { this.residentId = residentId; }

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getOldStatus() { return oldStatus; }
    public void setOldStatus(String oldStatus) { this.oldStatus = oldStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getTimestampValue() { return timestampValue; }
    public void setTimestampValue(Long timestampValue) { this.timestampValue = timestampValue; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public Long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getActionDetails() { return actionDetails; }
    public void setActionDetails(String actionDetails) { this.actionDetails = actionDetails; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

    public String getSubjectLabel() { return subjectLabel; }
    public void setSubjectLabel(String subjectLabel) { this.subjectLabel = subjectLabel; }
}
