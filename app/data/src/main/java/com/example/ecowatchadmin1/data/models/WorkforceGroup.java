package com.example.ecowatchadmin1.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.io.Serializable;
import java.util.List;

@Entity(tableName = "workforce_groups")
public class WorkforceGroup implements Serializable {
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private String mongoId;
    private String groupName;
    private List<String> memberIds;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getMongoId() { return mongoId; }
    public void setMongoId(String mongoId) { this.mongoId = mongoId; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public List<String> getMemberIds() { return memberIds; }
    public void setMemberIds(List<String> memberIds) { this.memberIds = memberIds; }
}
