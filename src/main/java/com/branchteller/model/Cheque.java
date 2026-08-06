package com.branchteller.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Cheque {
    private int id;
    private int accountId;
    private String chequeNo;
    private BigDecimal amount;
    private String status; // PENDING, CLEARED, BOUNCED
    private int tellerId;
    private LocalDate depositDate;
    private LocalDate clearDate;
    private String note;

    // populated by joins
    private String accountNumber;

    public Cheque() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }

    public String getChequeNo() { return chequeNo; }
    public void setChequeNo(String chequeNo) { this.chequeNo = chequeNo; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTellerId() { return tellerId; }
    public void setTellerId(int tellerId) { this.tellerId = tellerId; }

    public LocalDate getDepositDate() { return depositDate; }
    public void setDepositDate(LocalDate depositDate) { this.depositDate = depositDate; }

    public LocalDate getClearDate() { return clearDate; }
    public void setClearDate(LocalDate clearDate) { this.clearDate = clearDate; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
}
