package com.example.ecowatchadmin1.data;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

/**
 * Holds the {@link NotificationIntentProvider} installed by the application
 * shell.
 *
 * <p>Keeping the registration behind a small holder lets the data layer raise
 * notifications without importing an Activity from a feature module. When no
 * provider is installed (for example in a unit test or a headless worker) the
 * notification is still posted, just without a content intent.
 */
public final class NotificationIntentFactory {

    private static volatile NotificationIntentProvider provider;

    private NotificationIntentFactory() {
    }

    /** Called once from the {@code Application} subclass. */
    public static void install(@Nullable NotificationIntentProvider newProvider) {
        provider = newProvider;
    }

    @Nullable
    public static Intent createOpenNotificationIntent(
            Context context,
            @Nullable String notificationId,
            @Nullable String reportId
    ) {
        NotificationIntentProvider current = provider;
        if (current == null) return null;
        return current.createOpenNotificationIntent(context, notificationId, reportId);
    }
}
