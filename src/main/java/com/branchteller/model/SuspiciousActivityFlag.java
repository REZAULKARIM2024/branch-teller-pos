package com.branchteller.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SuspiciousActivityFlag {
    private int id;
    private int accountId;
    private Integer txnId;
    private String reason;
    private BigDecimal amount;
    private LocalDateTime flaggedAt;
    private boolean reviewed;
    private Integer reviewedBy;
    private LocalDateTime reviewDate;

    // populated by joins
    private String accountNumber;

    public SuspiciousActivityFlag() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }

    public Integer getTxnId() { return txnId; }
    public void setTxnId(Integer txnId) { this.txnId = txnId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDateTime getFlaggedAt() { return flaggedAt; }
    public void setFlaggedAt(LocalDateTime flaggedAt) { this.flaggedAt = flaggedAt; }

    public boolean isReviewed() { return reviewed; }
    public void setReviewed(boolean reviewed) { this.reviewed = reviewed; }

    public Integer getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Integer reviewedBy) { this.reviewedBy = reviewedBy; }

    public LocalDateTime getReviewDate() { return reviewDate; }
    public void setReviewDate(LocalDateTime reviewDate) { this.reviewDate = reviewDate; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
}
