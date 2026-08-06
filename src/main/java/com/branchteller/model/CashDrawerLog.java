package com.branchteller.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CashDrawerLog {
    private int id;
    private int tellerId;
    private int branchId;
    private String action; // PAID_IN, PAID_OUT, CASH_PULL, NO_SALE, TILL_COUNT
    private BigDecimal amount;
    private String note;
    private LocalDateTime createdAt;

    public CashDrawerLog() {}

    public CashDrawerLog(int tellerId, int branchId, String action, BigDecimal amount, String note) {
        this.tellerId = tellerId;
        this.branchId = branchId;
        this.action = action;
        this.amount = amount;
        this.note = note;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getTellerId() { return tellerId; }
    public void setTellerId(int tellerId) { this.tellerId = tellerId; }

    public int getBranchId() { return branchId; }
    public void setBranchId(int branchId) { this.branchId = branchId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
