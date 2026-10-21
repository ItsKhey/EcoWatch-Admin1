package com.example.ecowatchadmin1.data.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A named cleanup crew the administrator dispatches as a unit.
 *
 * <p>A team exists because the scale of a report decides how much has to go
 * out on site: a medium job needs a crew and a large job needs a crew and a
 * truck. The team is maintained once by the administrator and then reused for
 * every report that needs one.
 *
 * <p>This is not a Room entity. Team membership is server-owned and always
 * read fresh, so it is never cached locally.
 */
public class CleaningTeam implements Serializable {

    private String teamId;
    private String teamName;
    private String description;
    private int memberCount;
    private List<Member> members = new ArrayList<>();

    /**
     * One cleaner on the crew.
     *
     * <p>Carried so the assignment sheet can show who the team actually is,
     * and can pre-select the crew instead of making the administrator tick
     * every name by hand.
     */
    public static class Member implements Serializable {
        private String personnelId;
        private String fullName;
        private String occupation;
        private String status;
        private boolean enabled = true;

        public String getPersonnelId() { return personnelId; }
        public void setPersonnelId(String personnelId) { this.personnelId = personnelId; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getOccupation() { return occupation; }
        public void setOccupation(String occupation) { this.occupation = occupation; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }

    public List<Member> getMembers() {
        return members == null ? new ArrayList<>() : members;
    }

    public void setMembers(List<Member> members) {
        this.members = members == null ? new ArrayList<>() : members;
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
}
