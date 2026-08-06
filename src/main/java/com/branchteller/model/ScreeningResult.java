package com.branchteller.model;

import java.time.LocalDateTime;

public class ScreeningResult {
    private int id;
    private int customerId;
    private String customerName;
    private Integer matchedEntryId;
    private String matchedName;
    private double matchScore;
    private String status; // CLEAR, POTENTIAL_MATCH, CONFIRMED_MATCH
    private LocalDateTime screenedAt;
    private Integer reviewedBy;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public Integer getMatchedEntryId() { return matchedEntryId; }
    public void setMatchedEntryId(Integer matchedEntryId) { this.matchedEntryId = matchedEntryId; }
    public String getMatchedName() { return matchedName; }
    public void setMatchedName(String matchedName) { this.matchedName = matchedName; }
    public double getMatchScore() { return matchScore; }
    public void setMatchScore(double matchScore) { this.matchScore = matchScore; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getScreenedAt() { return screenedAt; }
    public void setScreenedAt(LocalDateTime screenedAt) { this.screenedAt = screenedAt; }
    public Integer getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Integer reviewedBy) { this.reviewedBy = reviewedBy; }
}
