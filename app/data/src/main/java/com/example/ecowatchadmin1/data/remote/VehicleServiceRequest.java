package com.example.ecowatchadmin1.data.remote;

import com.google.gson.annotations.SerializedName;

/** Records that a vehicle has been serviced. */
public class VehicleServiceRequest {
    @SerializedName("servicedAt")
    private final String servicedAt;

    public VehicleServiceRequest(String servicedAt) {
        this.servicedAt = servicedAt;
    }
}
