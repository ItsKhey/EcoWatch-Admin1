package com.example.ecowatchadmin1.data.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The readiness rules behind the Teams cards, chips and search.
 *
 * <p>The list endpoint sends a member count but no member list, while a single
 * team read sends both. These cases pin that the same answer comes out of either
 * shape, because a team that is counted as ready on the card but opened as
 * unready (or the reverse) would make the chips lie.
 */
public class CleaningTeamTest {

    private static CleaningTeam listRow(int memberCount, String leaderId) {
        CleaningTeam team = new CleaningTeam();
        team.setMemberCount(memberCount);
        team.setLeaderId(leaderId);
        return team;
    }

    private static CleaningTeam.Member member(String personnelId, String fullName, boolean leader) {
        CleaningTeam.Member member = new CleaningTeam.Member();
        member.setPersonnelId(personnelId);
        member.setFullName(fullName);
        member.setLeader(leader);
        return member;
    }

    /** A list row has no members, so the count the server sent is the truth. */
    @Test
    public void aListRowUsesTheSentMemberCount() {
        CleaningTeam team = listRow(3, "PERS-0001");

        assertEquals(3, team.getCrewSize());
        assertTrue(team.isDispatchReady());
    }

    /** A full read prefers the loaded list over the count that came with it. */
    @Test
    public void aLoadedMemberListWinsOverTheCount() {
        CleaningTeam team = listRow(9, "PERS-0001");
        team.setMembers(java.util.Arrays.asList(
                member("PERS-0001", "Ana Cruz", true),
                member("PERS-0002", "Ben Reyes", false)));

        assertEquals(2, team.getCrewSize());
        assertTrue(team.isDispatchReady());
    }

    /** The leader is crew too: a leader and one cleaner is a dispatchable pair. */
    @Test
    public void theLeaderCountsTowardTheMinimumCrew() {
        CleaningTeam team = listRow(2, "PERS-0001");

        assertTrue(team.isDispatchReady());
        assertEquals(CleaningTeam.MINIMUM_MEMBERS, team.getCrewSize());
    }

    @Test
    public void aLeaderAloneIsUnderstaffed() {
        CleaningTeam team = listRow(1, "PERS-0001");

        assertTrue(team.hasLeader());
        assertFalse(team.isDispatchReady());
    }

    @Test
    public void aBigCrewWithoutALeaderIsNotReady() {
        CleaningTeam team = listRow(4, null);

        assertFalse(team.hasLeader());
        assertFalse(team.isDispatchReady());
    }

    /** A team with neither a leader nor members is not ready either. */
    @Test
    public void anEmptyTeamIsNotReady() {
        CleaningTeam team = listRow(0, null);

        assertEquals(0, team.getCrewSize());
        assertFalse(team.isDispatchReady());
    }

    @Test
    public void theLeaderNameResolvesFromTheLoadedMemberList() {
        CleaningTeam team = listRow(2, "PERS-0001");
        team.setMembers(java.util.Arrays.asList(
                member("PERS-0001", "Ana Cruz", true),
                member("PERS-0002", "Ben Reyes", false)));

        assertEquals("Ana Cruz", team.getResolvedLeaderName());
        assertEquals("PERS-0001", team.findLeader() == null ? null : team.findLeader().getPersonnelId());
    }

    @Test
    public void theLeaderIdIsNormalisedWhenMatchingAMember() {
        CleaningTeam team = listRow(2, " pers-0001 ");
        team.setMembers(java.util.Arrays.asList(
                member("PERS-0001", "Ana Cruz", false),
                member("PERS-0002", "Ben Reyes", false)));

        assertTrue(team.isLeader("pers-0001"));
        assertFalse(team.isLeader("PERS-0002"));
        assertEquals("Ana Cruz", team.getResolvedLeaderName());
    }

    /** The joined leader name from the list endpoint avoids a second request. */
    @Test
    public void theJoinedLeaderNameIsUsedWhenNoMembersAreLoaded() {
        CleaningTeam team = listRow(2, "PERS-0001");
        team.setLeaderName("Ana Cruz");

        assertEquals("Ana Cruz", team.getResolvedLeaderName());
    }

    @Test
    public void theSearchFindsTheLeaderNotJustTheTeamName() {
        CleaningTeam team = new CleaningTeam();
        team.setTeamId("TEAM-0001");
        team.setTeamName("North Route");
        team.setLeaderId("PERS-0001");
        team.setLeaderName("Ana Cruz");

        assertTrue(team.matchesQuery("north"));
        assertTrue(team.matchesQuery("team-0001"));
        assertTrue(team.matchesQuery("ana"));
        assertTrue(team.matchesQuery("pers-0001"));
        assertFalse(team.matchesQuery("south"));
    }

    @Test
    public void theDisplayNameCarriesTheCrewSizeWhenItIsKnown() {
        CleaningTeam team = new CleaningTeam();
        team.setTeamId("TEAM-0001");
        team.setTeamName("North Route");
        team.setMemberCount(3);

        assertEquals("North Route (3)", team.getDisplayName());
    }

    /** No name and no id is still a safe string rather than a null preview. */
    @Test
    public void anEmptyTeamHasAnEmptyDisplayName() {
        assertEquals("", new CleaningTeam().getDisplayName());
        assertNull(new CleaningTeam().getResolvedLeaderName());
    }
}
