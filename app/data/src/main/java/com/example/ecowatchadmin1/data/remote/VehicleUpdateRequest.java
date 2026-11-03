package com.example.ecowatchadmin1.data.remote;

import com.google.gson.annotations.SerializedName;

/**
 * A partial vehicle edit.
 *
 * <p>Gson omits null fields, which is exactly the behaviour the server's partial
 * update wants: a field left null here is not sent, so the stored value is kept
 * rather than cleared.
 */
public class VehicleUpdateRequest {
    @SerializedName("plateNumber")
    private final String plateNumber;

    @SerializedName("vehicleType")
    private final String vehicleType;

    @SerializedName("ratedLoadCapacity")
    private final String ratedLoadCapacity;

    @SerializedName("status")
    private final String status;

    @SerializedName("currentReportId")
    private final String currentReportId;

    @SerializedName("notes")
    private final String notes;

    public VehicleUpdateRequest(String plateNumber, String vehicleType,
                                String ratedLoadCapacity, String status,
                                String currentReportId, String notes) {
        this.plateNumber = plateNumber;
        this.vehicleType = vehicleType;
        this.ratedLoadCapacity = ratedLoadCapacity;
        this.status = status;
        this.currentReportId = currentReportId;
        this.notes = notes;
    }
}
