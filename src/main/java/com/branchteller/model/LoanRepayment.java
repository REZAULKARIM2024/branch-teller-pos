package com.branchteller.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class LoanRepayment {
    private int id;
    private int loanId;
    private int installmentNo;
    private LocalDate dueDate;
    private BigDecimal amountDue;
    private BigDecimal amountPaid;
    private String status; // PENDING, PAID, OVERDUE
    private LocalDate paidDate;

    public LoanRepayment() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getLoanId() { return loanId; }
    public void setLoanId(int loanId) { this.loanId = loanId; }

    public int getInstallmentNo() { return installmentNo; }
    public void setInstallmentNo(int installmentNo) { this.installmentNo = installmentNo; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public BigDecimal getAmountDue() { return amountDue; }
    public void setAmountDue(BigDecimal amountDue) { this.amountDue = amountDue; }

    public BigDecimal getAmountPaid() { return amountPaid; }
    public void setAmountPaid(BigDecimal amountPaid) { this.amountPaid = amountPaid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getPaidDate() { return paidDate; }
    public void setPaidDate(LocalDate paidDate) { this.paidDate = paidDate; }
}
