package com.branchteller.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StandingInstruction {
    private int id;
    private int fromAccountId;
    private String fromAccountNumber;
    private String toAccountNumber;
    private BigDecimal amount;
    private String frequency; // WEEKLY, MONTHLY
    private LocalDate nextRunDate;
    private String status; // ACTIVE, PAUSED, CANCELLED
    private String note;
    private LocalDateTime createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getFromAccountId() { return fromAccountId; }
    public void setFromAccountId(int fromAccountId) { this.fromAccountId = fromAccountId; }
    public String getFromAccountNumber() { return fromAccountNumber; }
    public void setFromAccountNumber(String fromAccountNumber) { this.fromAccountNumber = fromAccountNumber; }
    public String getToAccountNumber() { return toAccountNumber; }
    public void setToAccountNumber(String toAccountNumber) { this.toAccountNumber = toAccountNumber; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public LocalDate getNextRunDate() { return nextRunDate; }
    public void setNextRunDate(LocalDate nextRunDate) { this.nextRunDate = nextRunDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
