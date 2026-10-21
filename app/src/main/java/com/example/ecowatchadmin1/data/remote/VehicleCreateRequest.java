package com.example.ecowatchadmin1.data.remote;

import com.google.gson.annotations.SerializedName;

/**
 * Register a vehicle.
 *
 * <p>The vehicle id is server-owned: it comes from the database sequence, so a
 * client-supplied one could only ever collide.
 */
public class VehicleCreateRequest {
    @SerializedName("plateNumber")
    private final String plateNumber;

    @SerializedName("vehicleType")
    private final String vehicleType;

    @SerializedName("ratedLoadCapacity")
    private final String ratedLoadCapacity;

    @SerializedName("status")
    private final String status;

    @SerializedName("notes")
    private final String notes;

    public VehicleCreateRequest(String plateNumber, String vehicleType,
                                String ratedLoadCapacity, String status, String notes) {
        this.plateNumber = plateNumber;
        this.vehicleType = vehicleType;
        this.ratedLoadCapacity = ratedLoadCapacity;
        this.status = status;
        this.notes = notes;
    }
}
