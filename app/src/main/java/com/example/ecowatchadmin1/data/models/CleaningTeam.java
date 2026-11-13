package com.example.ecowatchadmin1.data.models;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A named cleanup crew the administrator dispatches as a unit.
 *
 * <p>A team exists because the scale of a report decides how much has to go
 * out on site: a medium job needs a crew and a large job needs a crew and a
 * truck. The team is maintained once by the administrator and then reused for
 * every report that needs one.
 *
 * <p>A crew has exactly one leader and at least two people in total, which is
 * the shape the Teams tab both creates and enforces. The leader is also a
 * member of the crew: they walk the route like everybody else, they just
 * answer for it.
 *
 * <p>This is not a Room entity. Team membership is server-owned and always
 * read fresh, so it is never cached locally.
 */
public class CleaningTeam implements Serializable {

    /**
     * The smallest crew the server accepts, leader included: the leader walks
     * the route too, so a team of a leader and one cleaner is two people.
     */
    public static final int MINIMUM_MEMBERS = 2;

    private String teamId;
    private String teamName;
    private String description;
    private String label;
    private int memberCount;
    private List<Member> members = new ArrayList<>();

    /** The person who answers for the crew. Server-owned, nullable on older teams. */
    @SerializedName(value = "leaderId", alternate = {
            "leader_id", "leaderPersonnelId", "leader_personnel_id"
    })
    private String leaderId;

    /** Carried alongside the id so a card can name the leader without a request. */
    @SerializedName(value = "leaderName", alternate = {"leader_name", "leaderFullName"})
    private String leaderName;

    /**
     * The last collection this crew completed.
     *
     * <p>Null means the crew has not completed a collection yet. The card
     * deliberately does not fall back to {@link #getUpdatedAt()}, because a
     * team edit is not a collection. Alternate names are accepted for the same
     * rolling-deployment reason as the personnel fields.
     */
    @SerializedName(value = "lastCollectionAt", alternate = {
            "last_collection_at", "lastCollection", "last_collection",
            "lastCompletedAt", "last_completed_at"
    })
    private String lastCollectionAt;

    @SerializedName(value = "updatedAt", alternate = {"updated_at"})
    private String updatedAt;

    @SerializedName(value = "createdAt", alternate = {"created_at"})
    private String createdAt;

    /**
     * One cleaner on the crew.
     *
     * <p>Carried so the assignment sheet can show who the team actually is,
     * and so the Teams tab can list and remove one person at a time.
     */
    public static class Member implements Serializable {
        private String personnelId;
        private String fullName;
        private String occupation;
        private String assignedVehicleId;
        private String status;
        private boolean enabled = true;

        /** The leader of this crew, marked on the one member who answers for it. */
        private boolean leader;

        public String getPersonnelId() { return personnelId; }
        public void setPersonnelId(String personnelId) { this.personnelId = personnelId; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getOccupation() { return occupation; }
        public void setOccupation(String occupation) { this.occupation = occupation; }

        public String getAssignedVehicleId() { return assignedVehicleId; }
        public void setAssignedVehicleId(String assignedVehicleId) {
            this.assignedVehicleId = assignedVehicleId;
        }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isLeader() { return leader; }
        public void setLeader(boolean leader) { this.leader = leader; }

        /** The name to show, never null, falling back to the id. */
        public String getDisplayName() {
            if (fullName != null && !fullName.trim().isEmpty()) return fullName.trim();
            return personnelId == null ? "" : personnelId.trim();
        }
    }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }

    public List<Member> getMembers() {
        return members == null ? new ArrayList<>() : members;
    }

    public void setMembers(List<Member> members) {
        this.members = members == null ? new ArrayList<>() : members;
    }

    public String getLeaderId() { return leaderId; }
    public void setLeaderId(String leaderId) { this.leaderId = leaderId; }

    public String getLeaderName() { return leaderName; }
    public void setLeaderName(String leaderName) { this.leaderName = leaderName; }

    public String getLastCollectionAt() { return lastCollectionAt; }
    public void setLastCollectionAt(String lastCollectionAt) {
        this.lastCollectionAt = lastCollectionAt;
    }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    /**
     * The stamp the card labels "Last Collection".
     *
     * <p>Only a completed collection counts. Falling back to {@code updatedAt}
     * would show a team edit as if the crew had just finished a cleanup, so a
     * blank value means the team has no completed collection yet.
     */
    public String getLastCollection() {
        if (lastCollectionAt != null && !lastCollectionAt.trim().isEmpty()) {
            return lastCollectionAt;
        }
        return null;
    }

    /** True when somebody answers for this crew. */
    public boolean hasLeader() {
        return (leaderId != null && !leaderId.trim().isEmpty())
                || (leaderName != null && !leaderName.trim().isEmpty());
    }

    /**
     * The leader's name, resolved from the member list when the response only
     * carried the id.
     */
    public String getResolvedLeaderName() {
        if (leaderName != null && !leaderName.trim().isEmpty()) return leaderName.trim();
        String id = normalizedLeaderId();
        if (id == null) return null;
        for (Member member : getMembers()) {
            if (member == null) continue;
            if (id.equals(normalize(member.getPersonnelId()))) {
                return member.getDisplayName();
            }
        }
        return id;
    }

    /**
     * The number of people on the crew.
     *
     * <p>The member list wins when it is present, because the list endpoint
     * omits it and only sends the count; a full team read is always the truth.
     */
    public int getCrewSize() {
        List<Member> crew = getMembers();
        return crew.isEmpty() ? memberCount : crew.size();
    }

    /**
     * True when the team is shaped the way a dispatch needs it: a leader and a
     * crew of at least two people, counting the leader.
     *
     * <p>Counted rather than assumed, because a team read without its member
     * list still reports a count and that is enough to answer the question.
     */
    public boolean isDispatchReady() {
        return hasLeader() && getCrewSize() >= MINIMUM_MEMBERS;
    }

    /** Whether a member row is the leader, read from the id or the member flag. */
    public boolean isLeader(String personnelId) {
        String leader = normalizedLeaderId();
        return leader != null && leader.equals(normalize(personnelId));
    }

    /** The leader row, when the member list is loaded. */
    public Member findLeader() {
        String leader = normalizedLeaderId();
        for (Member member : getMembers()) {
            if (member == null) continue;
            if (member.isLeader()) return member;
            if (leader != null && leader.equals(normalize(member.getPersonnelId()))) {
                return member;
            }
        }
        return null;
    }

    /**
     * The team as it appears in the picker: its name, and the size of the crew
     * so the administrator can tell two teams apart at a glance.
     */
    public String getDisplayName() {
        String name = teamName == null || teamName.trim().isEmpty() ? teamId : teamName.trim();
        if (name == null || name.trim().isEmpty()) return "";
        int count = getMembers().isEmpty() ? memberCount : getMembers().size();
        return count > 0 ? name + " (" + count + ")" : name;
    }

    /** Free-text match for the Teams search box: name, id, leader and members. */
    public boolean matchesQuery(String query) {
        if (query == null || query.trim().isEmpty()) return true;
        String needle = query.trim().toLowerCase(Locale.getDefault());
        if (contains(teamName, needle) || contains(teamId, needle)) return true;
        if (contains(leaderName, needle) || contains(leaderId, needle)) return true;
        for (Member member : getMembers()) {
            if (member == null) continue;
            if (contains(member.getFullName(), needle)
                    || contains(member.getPersonnelId(), needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalizedLeaderId() {
        String id = normalize(leaderId);
        return id.isEmpty() ? null : id;
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(needle);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.getDefault());
    }
}
