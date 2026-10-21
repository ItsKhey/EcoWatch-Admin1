package com.example.ecowatchadmin1.data.remote;

/** Optional status update exposed for the future personnel app and admin tools. */
public class PersonnelStatusRequest {
    private Boolean online;
    private Boolean cleaning;
    private String status;

    public PersonnelStatusRequest(Boolean online, Boolean cleaning, String status) {
        this.online = online;
        this.cleaning = cleaning;
        this.status = status;
    }

    public Boolean getOnline() {
        return online;
    }

    public Boolean getCleaning() {
        return cleaning;
    }

    public String getStatus() {
        return status;
    }
}
