package com.example.ecowatchadmin1.data.remote;

/** Request body for the admin login endpoint. */
public final class AdminLoginRequest {
    private final String username;
    private final String password;

    public AdminLoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
