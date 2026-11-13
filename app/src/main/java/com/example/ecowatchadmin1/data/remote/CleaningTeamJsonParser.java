package com.example.ecowatchadmin1.data.remote;

import com.example.ecowatchadmin1.data.models.CleaningTeam;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads cleaning teams out of the admin cleaning-team responses.
 *
 * <p>Written by hand rather than handed to Gson because the payloads are
 * nested one level deep ({@code { success, teams }}) and because the member
 * list has to survive a response that omits it.
 */
public final class CleaningTeamJsonParser {

    private CleaningTeamJsonParser() {
    }

    /** Every team the administrator can dispatch, live ones first. */
    public static List<CleaningTeam> parseTeams(JsonElement payload) {
        List<CleaningTeam> teams = new ArrayList<>();
        JsonElement teamsElement = findElement(payload, "teams", "items", "results", "data");
        if (teamsElement == null || !teamsElement.isJsonArray()) {
            return teams;
        }
        for (JsonElement element : teamsElement.getAsJsonArray()) {
            if (element == null || !element.isJsonObject()) continue;
            CleaningTeam team = parseTeam(element.getAsJsonObject());
            if (team != null) teams.add(team);
        }
        return teams;
    }

    /** One team, read from the single-team response or from a list row. */
    public static CleaningTeam parseTeam(JsonElement payload) {
        if (payload == null || payload.isJsonNull()) return null;

        JsonObject teamObject = null;
        if (payload.isJsonObject()) {
            JsonObject object = payload.getAsJsonObject();
            JsonElement nested = findElement(object, "team");
            teamObject = nested != null && nested.isJsonObject()
                    ? nested.getAsJsonObject() : object;
        }
        if (teamObject == null) return null;

        CleaningTeam team = new CleaningTeam();
        team.setTeamId(readString(teamObject, "teamId", "team_id", "id"));
        team.setTeamName(readString(teamObject, "teamName", "team_name", "name"));
        team.setDescription(readString(teamObject, "description"));
        team.setLabel(readString(teamObject, "label", "teamLabel", "team_label"));
        team.setMemberCount(readInt(teamObject, "memberCount", "member_count"));
        team.setLeaderId(readString(teamObject,
                "leaderId", "leader_id", "leaderPersonnelId", "leader_personnel_id"));
        team.setLeaderName(readString(teamObject,
                "leaderName", "leader_name", "leaderFullName", "leader_full_name"));
        team.setLastCollectionAt(readString(teamObject,
                "lastCollectionAt", "last_collection_at", "lastCollection", "last_collection",
                "lastCompletedAt", "last_completed_at"));
        team.setUpdatedAt(readString(teamObject, "updatedAt", "updated_at"));
        team.setCreatedAt(readString(teamObject, "createdAt", "created_at"));

        // A member may carry its own leader flag; a payload that only names the
        // leader on the team is resolved here rather than left for every caller.
        List<CleaningTeam.Member> members = parseMembers(teamObject);
        if (team.getLeaderId() != null) {
            String leaderId = team.getLeaderId().trim().toUpperCase(java.util.Locale.US);
            for (CleaningTeam.Member member : members) {
                if (member.getPersonnelId() != null
                        && leaderId.equals(member.getPersonnelId().trim().toUpperCase(
                        java.util.Locale.US))) {
                    member.setLeader(true);
                }
            }
        }
        team.setMembers(members);
        return team;
    }

    private static List<CleaningTeam.Member> parseMembers(JsonObject teamObject) {
        List<CleaningTeam.Member> members = new ArrayList<>();
        JsonElement membersElement = findElement(teamObject, "members");
        if (membersElement == null || !membersElement.isJsonArray()) {
            return members;
        }
        for (JsonElement element : membersElement.getAsJsonArray()) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject memberObject = element.getAsJsonObject();
            CleaningTeam.Member member = new CleaningTeam.Member();
            member.setPersonnelId(readString(memberObject,
                    "personnelId", "personnel_id", "id"));
            member.setFullName(readString(memberObject,
                    "fullName", "full_name", "name"));
            member.setOccupation(readString(memberObject,
                    "occupation", "occupationType", "occupation_type"));
            member.setAssignedVehicleId(readString(memberObject,
                    "assignedVehicleId", "assigned_vehicle_id", "vehicleId", "vehicle_id"));
            member.setStatus(readString(memberObject, "status"));
            member.setEnabled(readBoolean(memberObject, "enabled"));
            member.setLeader(readBoolean(memberObject, "leader", "isLeader", "is_leader"));
            members.add(member);
        }
        return members;
    }

    private static JsonElement findElement(JsonElement payload, String... keys) {
        if (payload == null || !payload.isJsonObject()) return null;
        return findElement(payload.getAsJsonObject(), keys);
    }

    private static JsonElement findElement(JsonObject object, String... keys) {
        if (object == null) return null;
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && !element.isJsonNull()) {
                return element;
            }
        }
        return null;
    }

    private static String readString(JsonObject object, String... keys) {
        JsonElement element = findElement(object, keys);
        if (element == null || !element.isJsonPrimitive()) return null;
        String value = element.getAsString();
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static int readInt(JsonObject object, String... keys) {
        JsonElement element = findElement(object, keys);
        if (element == null || !element.isJsonPrimitive()) return 0;
        try {
            return element.getAsInt();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static boolean readBoolean(JsonObject object, String... keys) {
        JsonElement element = findElement(object, keys);
        if (element == null || !element.isJsonPrimitive()) return false;
        try {
            return element.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
