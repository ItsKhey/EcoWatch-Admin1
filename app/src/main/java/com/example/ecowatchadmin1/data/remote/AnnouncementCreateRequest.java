package com.example.ecowatchadmin1.data.remote;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

/**
 * Body for {@code POST /api/announcements}.
 *
 * <p>Only the publishable audience is exposed. The narrower audiences are
 * intentionally not modelled here: the backend refuses them today, so offering
 * them in the payload would only invite a request that is guaranteed to fail.
 */
public final class AnnouncementCreateRequest {

    @SerializedName("title")
    private final String title;

    @SerializedName("message")
    private final String message;

    @SerializedName("audience")
    private final String audience;

    @SerializedName("priority")
    private final String priority;

    /**
     * Lifetime window in days, chosen by the administrator. 0 means the
     * announcement does not expire on a timer. Sent as a relative window rather
     * than an absolute timestamp so the backend clock decides the deadline.
     */
    @SerializedName("expiresInDays")
    private final int expiresInDays;

    public AnnouncementCreateRequest(String title, String message, String audience, String priority,
                                     int expiresInDays) {
        this.title = title;
        this.message = message;
        this.audience = audience;
        this.priority = priority;
        this.expiresInDays = expiresInDays;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getAudience() {
        return audience;
    }

    public String getPriority() {
        return priority;
    }

    public int getExpiresInDays() {
        return expiresInDays;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AnnouncementCreateRequest)) return false;
        AnnouncementCreateRequest that = (AnnouncementCreateRequest) other;
        return expiresInDays == that.expiresInDays
                && Objects.equals(title, that.title)
                && Objects.equals(message, that.message)
                && Objects.equals(audience, that.audience)
                && Objects.equals(priority, that.priority);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, message, audience, priority, expiresInDays);
    }
}
