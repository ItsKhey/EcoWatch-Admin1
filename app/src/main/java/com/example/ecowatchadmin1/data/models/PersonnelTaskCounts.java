package com.example.ecowatchadmin1.data.models;

import java.io.Serializable;

/**
 * Task counts for one cleaner, counted by the server from the assignment rows.
 *
 * <p>Deliberately separate from {@link ReportStatistics}: that model counts
 * <em>reports</em> by their status, so its completed figure only moves once an
 * administrator approves each report. These four numbers describe the work the
 * cleaner has actually done, which is what the stat cards on the personnel
 * details screen show, and what the cleaner sees on their own profile.
 */
public class PersonnelTaskCounts implements Serializable {

    /** Every task this cleaner was ever assigned, whatever its status now. */
    private int totalAssigned;
    /** Work still in this cleaner's hands: assigned, accepted or in progress. */
    private int onHand;
    private int inProgress;
    private int completed;
    private int assigned;
    private int accepted;
    private int cancelled;

    public PersonnelTaskCounts() {
        // Gson/parser constructor.
    }

    public int getTotalAssigned() {
        return Math.max(0, totalAssigned);
    }

    public void setTotalAssigned(int totalAssigned) {
        this.totalAssigned = totalAssigned;
    }

    public int getOnHand() {
        return Math.max(0, onHand);
    }

    public void setOnHand(int onHand) {
        this.onHand = onHand;
    }

    public int getInProgress() {
        return Math.max(0, inProgress);
    }

    public void setInProgress(int inProgress) {
        this.inProgress = inProgress;
    }

    public int getCompleted() {
        return Math.max(0, completed);
    }

    public void setCompleted(int completed) {
        this.completed = completed;
    }

    public int getAssigned() {
        return Math.max(0, assigned);
    }

    public void setAssigned(int assigned) {
        this.assigned = assigned;
    }

    public int getAccepted() {
        return Math.max(0, accepted);
    }

    public void setAccepted(int accepted) {
        this.accepted = accepted;
    }

    public int getCancelled() {
        return Math.max(0, cancelled);
    }

    public void setCancelled(int cancelled) {
        this.cancelled = cancelled;
    }
}