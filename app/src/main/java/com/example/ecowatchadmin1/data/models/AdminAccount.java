package com.example.ecowatchadmin1.data.models;

import java.io.Serializable;

/**
 * Server-owned details for the currently authenticated administrator.
 *
 * <p>The administrator identifier is deliberately kept separate from the
 * resident identifier used by the legacy resident-facing endpoints. This
 * prevents a resident identifier from being displayed on the admin account
 * screen.</p>
 */
public class AdminAccount implements Serializable {
    private String adminId;
    private String role;
    private String createdAt;
    private String lastActive;
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private String address;
    private String profileImageUrl;

    public AdminAccount() {
        // Gson and the response mapper use this constructor.
    }

    public AdminAccount(String adminId, String role, String createdAt, String lastActive) {
        this.adminId = adminId;
        this.role = role;
        this.createdAt = createdAt;
        this.lastActive = lastActive;
    }

    public String getAdminId() {
        return adminId;
    }

    public void setAdminId(String adminId) {
        this.adminId = adminId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getLastActive() {
        return lastActive;
    }

    public void setLastActive(String lastActive) {
        this.lastActive = lastActive;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}
