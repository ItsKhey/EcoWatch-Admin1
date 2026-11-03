package com.example.ecowatchadmin1.data.models;

import java.io.Serializable;

/** Counts returned by the protected reporter statistics endpoint. */
public class ReportStatistics implements Serializable {
    private int total;
    private int pending;
    private int inProgress;
    private int completed;
    private int denied;

    public ReportStatistics() {
        // Gson/parser constructor.
    }

    public int getTotal() {
        return Math.max(0, total);
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getPending() {
        return Math.max(0, pending);
    }

    public void setPending(int pending) {
        this.pending = pending;
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

    public int getDenied() {
        return Math.max(0, denied);
    }

    public void setDenied(int denied) {
        this.denied = denied;
    }
}
