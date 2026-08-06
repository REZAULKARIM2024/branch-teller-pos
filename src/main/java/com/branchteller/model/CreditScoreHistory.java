package com.branchteller.model;

import java.time.LocalDateTime;

public class CreditScoreHistory {
    private int id;
    private int customerId;
    private String customerName;
    private int score;
    private String rating;
    private LocalDateTime computedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }
    public LocalDateTime getComputedAt() { return computedAt; }
    public void setComputedAt(LocalDateTime computedAt) { this.computedAt = computedAt; }
}
