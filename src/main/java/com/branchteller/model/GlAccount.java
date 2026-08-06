package com.branchteller.model;

import java.math.BigDecimal;

public class GlAccount {
    private int id;
    private String code;
    private String name;
    private String accountClass; // ASSET, LIABILITY, EQUITY, INCOME, EXPENSE
    private String normalBalance; // DEBIT, CREDIT
    private BigDecimal debitTotal = BigDecimal.ZERO;
    private BigDecimal creditTotal = BigDecimal.ZERO;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAccountClass() { return accountClass; }
    public void setAccountClass(String accountClass) { this.accountClass = accountClass; }
    public String getNormalBalance() { return normalBalance; }
    public void setNormalBalance(String normalBalance) { this.normalBalance = normalBalance; }
    public BigDecimal getDebitTotal() { return debitTotal; }
    public void setDebitTotal(BigDecimal debitTotal) { this.debitTotal = debitTotal; }
    public BigDecimal getCreditTotal() { return creditTotal; }
    public void setCreditTotal(BigDecimal creditTotal) { this.creditTotal = creditTotal; }

    public BigDecimal getNetBalance() {
        BigDecimal net = "DEBIT".equals(normalBalance) ? debitTotal.subtract(creditTotal) : creditTotal.subtract(debitTotal);
        return net;
    }
}
