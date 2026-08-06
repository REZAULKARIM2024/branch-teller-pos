package com.branchteller.model;

public class Branch {
    private int id;
    private String name;
    private String address;
    private String routingCode;
    private int accountCount;
    private int employeeCount;
    private java.math.BigDecimal totalDeposits;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getRoutingCode() { return routingCode; }
    public void setRoutingCode(String routingCode) { this.routingCode = routingCode; }
    public int getAccountCount() { return accountCount; }
    public void setAccountCount(int accountCount) { this.accountCount = accountCount; }
    public int getEmployeeCount() { return employeeCount; }
    public void setEmployeeCount(int employeeCount) { this.employeeCount = employeeCount; }
    public java.math.BigDecimal getTotalDeposits() { return totalDeposits; }
    public void setTotalDeposits(java.math.BigDecimal totalDeposits) { this.totalDeposits = totalDeposits; }
}
