package com.branchteller.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AccountHold {
    private int id;
    private int accountId;
    private String accountNumber;
    private BigDecimal amount;
    private String reason;
    private int placedBy;
    private String status; // ACTIVE, RELEASED
    private LocalDateTime placedAt;
    private LocalDateTime releasedAt;
    private Integer releasedBy;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public int getPlacedBy() { return placedBy; }
    public void setPlacedBy(int placedBy) { this.placedBy = placedBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getPlacedAt() { return placedAt; }
    public void setPlacedAt(LocalDateTime placedAt) { this.placedAt = placedAt; }
    public LocalDateTime getReleasedAt() { return releasedAt; }
    public void setReleasedAt(LocalDateTime releasedAt) { this.releasedAt = releasedAt; }
    public Integer getReleasedBy() { return releasedBy; }
    public void setReleasedBy(Integer releasedBy) { this.releasedBy = releasedBy; }
}
