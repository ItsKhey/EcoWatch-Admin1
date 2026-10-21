package com.example.ecowatchadmin1.data.remote;

import com.google.gson.annotations.SerializedName;

/** Request body for the admin-only personnel creation endpoint. */
public class PersonnelCreateRequest {
    private String firstName;
    private String lastName;
    private String address;

    @SerializedName("gmail")
    private String gmail;

    /* The deployed API names this column email; keep both wire aliases. */
    @SerializedName("email")
    private String email;

    private String username;
    private String password;
    private String phoneNumber;
    private String messengerLink;

    /** Regular or Driver. Decides whether a truck has to travel with the account. */
    @SerializedName("occupation")
    private String occupation;

    @SerializedName("occupationType")
    private String occupationType;

    /**
     * The truck this person drives.
     *
     * <p>Null for a regular cleaner, and Gson drops null fields, so the wire
     * carries an assignment only when the administrator actually made one rather
     * than an empty value the server would store as a real assignment.
     */
    @SerializedName("assignedVehicleId")
    private String assignedVehicleId;

    @SerializedName("vehicleId")
    private String vehicleId;

    public PersonnelCreateRequest(String firstName, String lastName, String address,
                                  String gmail, String username, String password,
                                  String phoneNumber, String messengerLink) {
        this(firstName, lastName, address, gmail, username, password, phoneNumber,
                messengerLink, null, null);
    }

    public PersonnelCreateRequest(String firstName, String lastName, String address,
                                  String gmail, String username, String password,
                                  String phoneNumber, String messengerLink,
                                  String occupation, String assignedVehicleId) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.address = address;
        this.gmail = gmail;
        this.email = gmail;
        this.username = username;
        this.password = password;
        this.phoneNumber = phoneNumber;
        this.messengerLink = messengerLink;
        this.occupation = occupation;
        this.occupationType = occupation;
        this.assignedVehicleId = assignedVehicleId;
        this.vehicleId = assignedVehicleId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getAddress() {
        return address;
    }

    public String getGmail() {
        return gmail;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getMessengerLink() {
        return messengerLink;
    }

    public String getOccupation() {
        return occupation;
    }

    public String getAssignedVehicleId() {
        return assignedVehicleId;
    }
}
