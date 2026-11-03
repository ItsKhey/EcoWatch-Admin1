package com.example.ecowatchadmin1.data;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

/**
 * Builds the intent opened when the user taps a system notification.
 *
 * <p>The destination lives in the application module, which depends on this
 * module rather than the other way round. The shell installs an implementation
 * at startup (see {@code MyEcoWatchApp}) and the repository asks for an intent
 * when it posts a notification.
 */
public interface NotificationIntentProvider {

    @Nullable
    Intent createOpenNotificationIntent(
            Context context,
            @Nullable String notificationId,
            @Nullable String reportId
    );
}
