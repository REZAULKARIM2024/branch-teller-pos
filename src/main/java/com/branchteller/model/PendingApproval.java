package com.branchteller.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PendingApproval {
    private int id;
    private String requestType; // WITHDRAW, TRANSFER
    private Integer accountId;
    private String accountNumber;
    private Integer toAccountId;
    private String toAccountNumber;
    private BigDecimal amount;
    private int requestedBy;
    private String requestedByName;
    private String status; // PENDING, APPROVED, REJECTED
    private Integer approvedBy;
    private String requestNote;
    private String decisionNote;
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public Integer getAccountId() { return accountId; }
    public void setAccountId(Integer accountId) { this.accountId = accountId; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public Integer getToAccountId() { return toAccountId; }
    public void setToAccountId(Integer toAccountId) { this.toAccountId = toAccountId; }
    public String getToAccountNumber() { return toAccountNumber; }
    public void setToAccountNumber(String toAccountNumber) { this.toAccountNumber = toAccountNumber; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public int getRequestedBy() { return requestedBy; }
    public void setRequestedBy(int requestedBy) { this.requestedBy = requestedBy; }
    public String getRequestedByName() { return requestedByName; }
    public void setRequestedByName(String requestedByName) { this.requestedByName = requestedByName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Integer approvedBy) { this.approvedBy = approvedBy; }
    public String getRequestNote() { return requestNote; }
    public void setRequestNote(String requestNote) { this.requestNote = requestNote; }
    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
}
