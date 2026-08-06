package com.branchteller.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PayrollRun {
    private int id;
    private int employeeId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal hoursWorked;
    private BigDecimal grossPay;
    private BigDecimal taxWithheld;
    private BigDecimal netPay;

    // populated by joins
    private String employeeName;

    public PayrollRun() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getEmployeeId() { return employeeId; }
    public void setEmployeeId(int employeeId) { this.employeeId = employeeId; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public BigDecimal getHoursWorked() { return hoursWorked; }
    public void setHoursWorked(BigDecimal hoursWorked) { this.hoursWorked = hoursWorked; }

    public BigDecimal getGrossPay() { return grossPay; }
    public void setGrossPay(BigDecimal grossPay) { this.grossPay = grossPay; }

    public BigDecimal getTaxWithheld() { return taxWithheld; }
    public void setTaxWithheld(BigDecimal taxWithheld) { this.taxWithheld = taxWithheld; }

    public BigDecimal getNetPay() { return netPay; }
    public void setNetPay(BigDecimal netPay) { this.netPay = netPay; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
}
