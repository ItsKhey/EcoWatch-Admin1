package com.example.ecowatchadmin1.data.models;

import java.io.Serializable;
import java.util.Locale;

/**
 * Server-owned reporter/resident account projection used by the admin app.
 * This is intentionally not a Room entity: reporter management must always
 * reflect the protected backend response.
 */
public class ResidentAccount implements Serializable {
    private String residentId;
    private String serverId;
    private String firstName;
    private String lastName;
    private String fullName;
    private String username;
    private String email;
    private String address;
    private String phoneNumber;
    private String messengerLink;
    private String profileImage;
    private String profileImageUrl;
    private String role;
    private String status;
    private String accountStatus;
    private boolean active;
    private boolean disabled;
    private boolean verified;
    private String lastActive;
    private String dateCreated;
    private String createdAt;
    private String updatedAt;
    private int totalReports;

    public ResidentAccount() {
        // Gson/parser constructor.
    }

    public String getResidentId() {
        return residentId;
    }

    public void setResidentId(String residentId) {
        this.residentId = residentId;
    }

    public String getServerId() {
        return serverId != null && !serverId.trim().isEmpty() ? serverId : residentId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
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

    public String getFullName() {
        if (fullName != null && !fullName.trim().isEmpty()) {
            return fullName.trim();
        }
        String resolved = ((firstName == null ? "" : firstName)
                + " " + (lastName == null ? "" : lastName)).trim();
        return resolved.isEmpty() ? "Unnamed resident" : resolved;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getMessengerLink() {
        return messengerLink;
    }

    public void setMessengerLink(String messengerLink) {
        this.messengerLink = messengerLink;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public String getProfileImageUrl() {
        return profileImageUrl != null && !profileImageUrl.trim().isEmpty()
                ? profileImageUrl : profileImage;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAccountStatus() {
        if (accountStatus != null && !accountStatus.trim().isEmpty()) {
            return accountStatus;
        }
        return isDisabled() ? "Disabled" : "Active";
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public boolean isActive() {
        return active && !disabled;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDisabled() {
        if (disabled) {
            return true;
        }
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.US);
        return "DISABLED".equals(normalized)
                || "INACTIVE".equals(normalized)
                || "BLOCKED".equals(normalized)
                || "SUSPENDED".equals(normalized)
                || "DELETED".equals(normalized);
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public String getLastActive() {
        return lastActive;
    }

    public void setLastActive(String lastActive) {
        this.lastActive = lastActive;
    }

    public String getDateCreated() {
        return dateCreated != null && !dateCreated.trim().isEmpty() ? dateCreated : createdAt;
    }

    public void setDateCreated(String dateCreated) {
        this.dateCreated = dateCreated;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getTotalReports() {
        return Math.max(0, totalReports);
    }

    public void setTotalReports(int totalReports) {
        this.totalReports = totalReports;
    }
}
