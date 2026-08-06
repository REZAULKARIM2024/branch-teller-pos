package com.branchteller.model;

public class User {
    private int id;
    private String username;
    private String passwordHash;
    private String salt;
    private String fullName;
    private String role; // ADMIN, MANAGER, TELLER
    private int branchId;
    private boolean active;
    private java.math.BigDecimal approvalLimit = java.math.BigDecimal.valueOf(5000);
    private boolean otpRequired = true;
    private int failedLoginAttempts;

    public User() {}

    public User(int id, String username, String fullName, String role, int branchId) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.branchId = branchId;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public int getBranchId() { return branchId; }
    public void setBranchId(int branchId) { this.branchId = branchId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isManagerOrAbove() {
        return "MANAGER".equals(role) || "ADMIN".equals(role);
    }

    public java.math.BigDecimal getApprovalLimit() { return approvalLimit; }
    public void setApprovalLimit(java.math.BigDecimal approvalLimit) { this.approvalLimit = approvalLimit; }

    public boolean isOtpRequired() { return otpRequired; }
    public void setOtpRequired(boolean otpRequired) { this.otpRequired = otpRequired; }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }
}
