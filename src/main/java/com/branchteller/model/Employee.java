package com.branchteller.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Employee {
    private int id;
    private String fullName;
    private String position;
    private BigDecimal hourlyRate;
    private LocalDate hireDate;
    private boolean active;
    private Integer userId;

    public Employee() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public BigDecimal getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(BigDecimal hourlyRate) { this.hourlyRate = hourlyRate; }

    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
}
