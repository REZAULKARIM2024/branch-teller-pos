package com.branchteller.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ExternalTransfer {
    private int id;
    private int accountId;
    private String accountNumber;
    private String transferType; // NEFT, RTGS, WIRE
    private String beneficiaryName;
    private String beneficiaryBank;
    private String beneficiaryAccount;
    private String routingSwift;
    private BigDecimal amount;
    private String status;
    private String referenceNo;
    private int initiatedBy;
    private LocalDateTime createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getTransferType() { return transferType; }
    public void setTransferType(String transferType) { this.transferType = transferType; }
    public String getBeneficiaryName() { return beneficiaryName; }
    public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }
    public String getBeneficiaryBank() { return beneficiaryBank; }
    public void setBeneficiaryBank(String beneficiaryBank) { this.beneficiaryBank = beneficiaryBank; }
    public String getBeneficiaryAccount() { return beneficiaryAccount; }
    public void setBeneficiaryAccount(String beneficiaryAccount) { this.beneficiaryAccount = beneficiaryAccount; }
    public String getRoutingSwift() { return routingSwift; }
    public void setRoutingSwift(String routingSwift) { this.routingSwift = routingSwift; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
    public int getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(int initiatedBy) { this.initiatedBy = initiatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
