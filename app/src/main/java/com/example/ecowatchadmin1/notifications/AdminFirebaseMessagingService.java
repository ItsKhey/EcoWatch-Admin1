package com.example.ecowatchadmin1.notifications;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.ecowatchadmin1.data.repository.NotificationRepository;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Admin-side mirror of the Resident app's {@code EcoWatchFirebaseMessagingService}.
 *
 * <p>The EcoWatch notification contract is shared: the backend sends data-only
 * messages whose content is server-owned, so this service never builds its own
 * copy. It only routes the payload into the same repository path the Resident
 * app uses, which keeps deduplication, read state, trash handling, and the
 * styled system notification identical across both applications.
 */
public class AdminFirebaseMessagingService extends FirebaseMessagingService {
    @Override
    public void onNewToken(@NonNull String token) {
        FcmTokenSyncScheduler.enqueue(getApplicationContext(), token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        NotificationRepository.getInstance(getApplicationContext())
                .upsertPushNotification(message.getData());
    }

    @Override
    public void onDeletedMessages() {
        // The next authenticated refresh reconciles any server-side deletion.
        Log.d("EcoWatchFCM", "Deleted FCM messages will be reconciled on refresh.");
    }
}
