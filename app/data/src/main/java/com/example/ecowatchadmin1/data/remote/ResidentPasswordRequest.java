package com.example.ecowatchadmin1.data.remote;

/**
 * Password reset body used by the admin reporter endpoint.
 *
 * <p>Mirrors {@link PersonnelPasswordRequest} so the two account types send the
 * same contract. The administrator chooses the new password here and reads it
 * back to the reporter out of band; nothing is emailed, so an admin-side reset
 * does not depend on the reporter's inbox.
 */
public class ResidentPasswordRequest {
    private final String newPassword;

    public ResidentPasswordRequest(String password) {
        this.newPassword = password;
    }

    public String getNewPassword() {
        return newPassword;
    }
}