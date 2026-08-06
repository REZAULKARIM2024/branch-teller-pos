package com.branchteller.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Transaction {
    private int id;
    private int accountId;
    private String txnType; // DEPOSIT, WITHDRAW, TRANSFER_OUT, TRANSFER_IN
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private int tellerId;
    private Integer relatedTxnId;
    private String channel;
    private String note;
    private LocalDateTime createdAt;

    public Transaction() {}

    public Transaction(int accountId, String txnType, BigDecimal amount, int tellerId, String note) {
        this.accountId = accountId;
        this.txnType = txnType;
        this.amount = amount;
        this.tellerId = tellerId;
        this.note = note;
        this.channel = "COUNTER";
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }

    public String getTxnType() { return txnType; }
    public void setTxnType(String txnType) { this.txnType = txnType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }

    public int getTellerId() { return tellerId; }
    public void setTellerId(int tellerId) { this.tellerId = tellerId; }

    public Integer getRelatedTxnId() { return relatedTxnId; }
    public void setRelatedTxnId(Integer relatedTxnId) { this.relatedTxnId = relatedTxnId; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
