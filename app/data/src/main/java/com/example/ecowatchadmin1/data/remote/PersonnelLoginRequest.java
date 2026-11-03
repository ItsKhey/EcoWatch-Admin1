package com.example.ecowatchadmin1.data.remote;

/** Credentials accepted by POST /api/personnel/login. */
public class PersonnelLoginRequest {
    private String username;
    private String password;

    public PersonnelLoginRequest(String username, String password) {
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
