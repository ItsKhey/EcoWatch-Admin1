package com.example.ecowatchadmin1.data.remote;

import com.example.ecowatchadmin1.data.models.History;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Response for {@code GET /api/admin/history}.
 *
 * <p>The server owns the tab each record belongs under and returns its own
 * per-tab counts alongside the rows. Both are modelled here so the client never
 * has to derive a category from the action string, which is where two clients
 * would otherwise disagree.
 */
public class AdminHistoryResponse {

    public boolean success;

    @SerializedName("total")
    public int total;

    /** Per-tab counts, keyed by category name. Used for the pill labels. */
    @SerializedName("counts")
    public Map<String, Integer> counts;

    @SerializedName("history")
    public List<History> history;

    public List<History> getHistory() {
        return history == null ? new ArrayList<>() : history;
    }

    /** The server's count for a tab, or 0 when it did not report one. */
    public int countFor(String category, int fallback) {
        if (counts == null || counts.get(category) == null) return fallback;
        return counts.get(category);
    }
}
