package com.example.ecowatchadmin1.data.remote;

import java.util.List;

/**
 * One cleaning team, as the admin create/update routes read it.
 *
 * <p>A single body serves both writes. {@code memberIds} is the whole crew, not
 * a delta: the server replaces the membership with the list it is given, which
 * is what lets the editor tick and untick people in one pass.
 *
 * <p>{@code leaderId} names the person who answers for the crew. It is also
 * expected to appear in {@code memberIds}; the server adds it if a caller sends
 * a leader who is not on the list, so a crew can never be led by somebody
 * outside it.
 */
public class CleaningTeamRequest {

    private final String teamName;
    private final String description;
    private final String label;
    private final String leaderId;
    private final List<String> memberIds;

    public CleaningTeamRequest(String teamName, String description, String label, String leaderId,
                               List<String> memberIds) {
        this.teamName = teamName;
        this.description = description;
        this.label = label;
        this.leaderId = leaderId;
        this.memberIds = memberIds;
    }

    public String getTeamName() { return teamName; }

    public String getDescription() { return description; }

    public String getLabel() { return label; }

    public String getLeaderId() { return leaderId; }

    public List<String> getMemberIds() { return memberIds; }
}
