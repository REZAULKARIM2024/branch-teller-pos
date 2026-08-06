package com.branchteller.model;

public class Customer {
    private int id;
    private String fullName;
    private String phone;
    private String email;
    private String address;
    private String kycStatus;
    private Integer creditScore;

    public Customer() {}

    public Customer(int id, String fullName, String phone, String kycStatus) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.kycStatus = kycStatus;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getKycStatus() { return kycStatus; }
    public void setKycStatus(String kycStatus) { this.kycStatus = kycStatus; }

    public Integer getCreditScore() { return creditScore; }
    public void setCreditScore(Integer creditScore) { this.creditScore = creditScore; }
}
