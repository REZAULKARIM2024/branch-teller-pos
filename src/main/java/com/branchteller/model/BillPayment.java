package com.branchteller.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BillPayment {
    private int id;
    private int accountId;
    private String accountNumber;
    private int billerId;
    private String billerName;
    private String referenceNo;
    private BigDecimal amount;
    private String status;
    private int paidBy;
    private LocalDateTime createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public int getBillerId() { return billerId; }
    public void setBillerId(int billerId) { this.billerId = billerId; }
    public String getBillerName() { return billerName; }
    public void setBillerName(String billerName) { this.billerName = billerName; }
    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getPaidBy() { return paidBy; }
    public void setPaidBy(int paidBy) { this.paidBy = paidBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
