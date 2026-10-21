# EcoWatch Admin — Firebase Cloud Messaging (Phase 3A)

The Admin app reuses the Resident app's production notification implementation
verbatim: same repository, same notification card, same EcoWatch green channel
styling, same unread/read/trash logic, and the same report deep-link contract.
Only the channel id and the token-registration route are admin-specific, so the
two applications never share a user-visible channel or a device token.

The backend sends **data-only** messages. The client never builds notification
copy; `title`, `message`, `type`, `reportId`, `created_at`, and `notificationId`
are all server-owned.

## 1. Register the Admin Android app

1. Open the EcoWatch Firebase project (`ecowatch1-4445f`, or your own).
2. Add an Android app with package name:

   ```text
   com.example.ecowatchadmin1
   ```

3. Download the generated configuration and place it at:

   ```text
   EcoWatch Admin1/app/google-services.json
   ```

The real file is ignored by Git. A non-functional template is available at
`app/google-services.json.example`.

4. Rebuild the Admin app. The Google Services Gradle plugin is applied only when
   the real file exists, so source builds remain possible without secrets. A
   release task fails fast with a clear message if the file is missing.

> The `google-services.json.resident-copy.bak` file kept in `app/` is a verbatim
> copy of the **Resident** app's configuration. It registers
> `com.example.myecowatch1` and can never authenticate this app — it is kept
> only for reference and must not be renamed to `google-services.json`.

## 2. Backend routes

The Admin app calls these authenticated routes (base URL
`https://ecowatch-backend1.onrender.com/api/`). The shared OkHttp interceptor
supplies the admin JWT.

| Purpose | Route |
| --- | --- |
| Register / rotate this device's token | `POST /admin/fcm-token` |
| Release the token on logout | `DELETE /admin/fcm-token` |
| List notifications | `GET /notifications/my-notifications` |
| List trashed notifications | `GET /notifications/trash` |
| Server unread count | `GET /notifications/unread-count` |
| Mark one read | `PATCH /notifications/{id}/read` |
| Mark all read | `PATCH /notifications/mark-as-read` |
| Move to trash | `POST /notifications/{id}/move-to-trash` |
| Restore | `POST /notifications/{id}/restore` |
| Permanent delete | `DELETE /notifications/{id}` |

### 2.1 One-time backend deployment

These routes do not exist in the previously deployed backend, so the Admin app
cannot receive a push until the following are deployed from
`MyEcoWatchBackend1`:

```text
npm run migrate:admin-fcm-token     # additive, idempotent
```

The migration adds `admins.fcm_token`, the `admin_notifications` and
`trash_admin_notifications` tables, and allows `NEW_REPORT` in the
`notifications.type` check constraint. It alters no resident data.

Administrator notifications are stored in `admin_notifications` rather than
`notifications`, because `notifications.resident_id` is referentially bound to
the `residents` table. The server resolves the correct table pair from the
owning account ID, so the client calls the same routes as the Resident app.

Verify readiness at any time:

```text
npm run verify:phase-3a
```

## 3. Sending a `NEW_REPORT` push

Send a **data-only** message (no `notification` block) to the
`ecowatch_admin_notifications` channel with Android priority `high`:

```json
{
  "to": "<admin fcm token>",
  "android": {
    "priority": "high",
    "notification": {
      "channel_id": "ecowatch_admin_notifications"
    }
  },
  "data": {
    "notificationId": "<server notification id>",
    "reportId": "<report id>",
    "type": "NEW_REPORT",
    "title": "New report submitted",
    "message": "A resident submitted a new report.",
    "createdAt": "2026-01-01T12:00:00.000Z"
  }
}
```

`created_at` is accepted as an alias for `createdAt`, and `notification_id` /
`report_id` as aliases for their camelCase forms.

Because the payload is data-only, `onMessageReceived` runs in **both** the
foreground and the background, so the Admin app always follows one code path:

1. `AdminFirebaseMessagingService.onMessageReceived`
2. `NotificationRepository.upsertPushNotification`
3. Room upsert (deduplicated on `residentId` + `notificationId`)
4. Styled system notification on `ecowatch_admin_notifications`
5. Unread count refreshed from the server; visible report list re-pulls

App-closed taps are delivered to `DashboardActivity` (`singleTop`) with
`notification_id` and `report_id` extras, which marks the notification read and
opens Report Details for that report.

## 4. Configure Render

The backend sends the push, so the same Firebase service account the Resident
app uses is required:

```text
FIREBASE_SERVICE_ACCOUNT_JSON={...complete JSON...}
```

or:

```text
FIREBASE_PROJECT_ID=...
FIREBASE_CLIENT_EMAIL=...
FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
```

After restart this should report:

```json
{ "pushNotifications": "CONFIGURED" }
```

```text
GET https://ecowatch-backend1.onrender.com/api/health
```

Do not commit the service account JSON or private key.

## 5. Production verification

1. Install and log in to the Admin app; confirm `POST /api/admin/fcm-token`
   succeeds with the admin JWT. Until at least one admin device is bound, the
   broadcast is a deliberate no-op.
2. Submit a report from the Resident app.
3. Confirm the Admin app shows the in-app row **and** the styled system
   notification.
4. Foreground: leave the Admin app open on Reports and confirm the list
   refreshes to include the new report.
5. Background: send the app to the background and confirm the same styled
   notification appears.
6. App closed: force-stop the Admin app, send another report, tap the
   notification, and confirm Report Details opens for the matching `reportId`.
7. Verify unread dot, mark-as-read, move to trash, and restore all behave
   identically to the Resident app.
8. Force-rotate the token (reinstall) and confirm `onNewToken()` re-registers it.
