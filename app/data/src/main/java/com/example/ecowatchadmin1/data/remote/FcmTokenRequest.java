package com.example.ecowatchadmin1.data.remote;

/** Body sent to {@code POST /api/admin/fcm-token} to bind this device to an admin. */
public class FcmTokenRequest {
    private final String token;

    public FcmTokenRequest(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }
}
