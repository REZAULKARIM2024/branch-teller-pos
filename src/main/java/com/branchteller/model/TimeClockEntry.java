package com.branchteller.model;

import java.time.LocalDateTime;

public class TimeClockEntry {
    private int id;
    private int employeeId;
    private LocalDateTime clockIn;
    private LocalDateTime clockOut;

    public TimeClockEntry() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getEmployeeId() { return employeeId; }
    public void setEmployeeId(int employeeId) { this.employeeId = employeeId; }

    public LocalDateTime getClockIn() { return clockIn; }
    public void setClockIn(LocalDateTime clockIn) { this.clockIn = clockIn; }

    public LocalDateTime getClockOut() { return clockOut; }
    public void setClockOut(LocalDateTime clockOut) { this.clockOut = clockOut; }
}
