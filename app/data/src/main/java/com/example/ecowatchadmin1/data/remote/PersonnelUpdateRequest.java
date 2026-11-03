package com.example.ecowatchadmin1.data.remote;

import com.google.gson.annotations.SerializedName;

/** Editable profile fields for a personnel account. */
public class PersonnelUpdateRequest {
    private String firstName;
    private String lastName;
    private String address;

    @SerializedName("gmail")
    private String gmail;

    @SerializedName("email")
    private String email;

    private String username;
    private String phoneNumber;
    private String messengerLink;

    public PersonnelUpdateRequest(String firstName, String lastName, String address,
                                  String gmail, String username,
                                  String phoneNumber, String messengerLink) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.address = address;
        this.gmail = gmail;
        this.email = gmail;
        this.username = username;
        // An empty link clears the stored one, so the field is always sent
        // rather than omitted when the admin blanks it out.
        this.phoneNumber = phoneNumber;
        this.messengerLink = messengerLink;
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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getMessengerLink() {
        return messengerLink;
    }
}
