package com.example.ecowatchadmin1.data.remote;

/** Password reset body used by the admin personnel endpoint. */
public class PersonnelPasswordRequest {
    private final String newPassword;

    public PersonnelPasswordRequest(String password) {
        this.newPassword = password;
    }

    public String getNewPassword() {
        return newPassword;
    }
}
