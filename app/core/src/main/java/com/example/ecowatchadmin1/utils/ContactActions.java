package com.example.ecowatchadmin1.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.ecowatchadmin1.R;

/**
 * Opens the phone, email and Messenger apps from a contact row.
 *
 * <p>Every action degrades to a message rather than a crash: a tablet with no
 * dialer, a device with no mail client, or a Messenger install that refuses the
 * link are all normal states on the hardware this admin app runs on.
 *
 * <p>Phone uses {@link Intent#ACTION_DIAL} instead of {@code ACTION_CALL} on
 * purpose. {@code ACTION_DIAL} needs no permission and only pre-fills the
 * number, which is the right behaviour for a staff directory where an admin may
 * not want to place a call.
 */
public final class ContactActions {

    /** Packages tried in order when a Messenger link is opened. */
    private static final String[] MESSENGER_PACKAGES = {
            "com.facebook.orca",
            "com.facebook.messenger",
            "com.facebook.orca.web",
    };

    private ContactActions() {
        // Utility class.
    }

    /** Opens the dialer with the number pre-filled. */
    public static void dial(@NonNull Context context, String phoneNumber) {
        String usable = usablePhoneNumber(phoneNumber);
        if (usable == null) {
            toast(context, R.string.contact_no_phone_number);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + usable));
        if (!start(context, intent)) {
            toast(context, R.string.contact_no_phone_app);
        }
    }

    /**
     * Opens a compose window addressed to the recipient.
     *
     * <p>Uses {@link Intent#ACTION_SENDTO} with a {@code mailto:} URI rather than
     * {@code ACTION_SEND}, because {@code ACTION_SEND} offers every app that
     * accepts text as a share target and an admin tapping "Email" would be
     * offered a notes app instead.
     */
    public static void email(@NonNull Context context, String recipient) {
        String address = isBlank(recipient) ? null : recipient.trim();
        if (address == null) {
            toast(context, R.string.contact_no_email_address);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + address));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{address});
        intent.putExtra(Intent.EXTRA_SUBJECT, "");
        if (!start(context, intent)) {
            toast(context, R.string.contact_no_email_app);
        }
    }

    /**
     * Opens the person's Messenger conversation.
     *
     * <p>Resolves an explicit installed Messenger package first so a phone with
     * several Messenger-family apps lands in Messenger rather than the browser,
     * then falls back to {@code ACTION_VIEW} which lets the OS pick a handler for
     * the https link.
     */
    public static void messenger(@NonNull Context context, String messengerLink) {
        String link = isBlank(messengerLink) ? null : messengerLink.trim();
        if (link == null) {
            toast(context, R.string.contact_no_messenger_link);
            return;
        }

        Uri uri;
        try {
            uri = Uri.parse(link);
        } catch (RuntimeException error) {
            toast(context, R.string.contact_messenger_link_invalid);
            return;
        }
        if (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme())) {
            toast(context, R.string.contact_messenger_link_invalid);
            return;
        }

        String installedPackage = firstInstalledMessengerPackage(context);
        if (installedPackage != null) {
            Intent targeted = new Intent(Intent.ACTION_VIEW, uri);
            targeted.setPackage(installedPackage);
            if (start(context, targeted)) return;
        }

        if (!start(context, new Intent(Intent.ACTION_VIEW, uri))) {
            toast(context, R.string.contact_no_messenger_app);
        }
    }

    private static String firstInstalledMessengerPackage(@NonNull Context context) {
        android.content.pm.PackageManager packages = context.getPackageManager();
        for (String candidate : MESSENGER_PACKAGES) {
            try {
                packages.getPackageInfo(candidate, 0);
                return candidate;
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
                // Try the next Messenger variant.
            }
        }
        return null;
    }

    private static boolean start(@NonNull Context context, @NonNull Intent intent) {
        try {
            // Newer Android versions require this flag when starting an activity
            // from a non-activity context such as a fragment.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException notFound) {
            return false;
        } catch (SecurityException denied) {
            return false;
        }
    }

    /**
     * Strips the characters a dialer cannot use from a stored number.
     *
     * <p>The server already constrains the column to digits, {@code +}, spaces,
     * dots and parentheses, so anything else is a legacy row and is dropped
     * rather than producing a malformed {@code tel:} URI.
     */
    private static String usablePhoneNumber(String phoneNumber) {
        if (isBlank(phoneNumber)) return null;
        StringBuilder cleaned = new StringBuilder();
        for (int index = 0; index < phoneNumber.length(); index++) {
            char character = phoneNumber.charAt(index);
            if (Character.isDigit(character) || character == '+') {
                cleaned.append(character);
            } else if (character != ' ' && character != '-' && character != '.'
                    && character != '(' && character != ')') {
                return null;
            }
        }
        return cleaned.length() == 0 ? null : cleaned.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void toast(@NonNull Context context, int messageRes) {
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show();
    }
}
