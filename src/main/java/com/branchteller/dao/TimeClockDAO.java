package com.branchteller.dao;

import com.branchteller.model.TimeClockEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TimeClockDAO {

    public int clockIn(Connection conn, int employeeId) throws SQLException {
        String sql = "INSERT INTO time_clock (employee_id, clock_in) VALUES (?, NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, employeeId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public Optional<TimeClockEntry> findOpenEntry(Connection conn, int employeeId) throws SQLException {
        String sql = "SELECT * FROM time_clock WHERE employee_id = ? AND clock_out IS NULL ORDER BY clock_in DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public void clockOut(Connection conn, int clockId) throws SQLException {
        String sql = "UPDATE time_clock SET clock_out = NOW() WHERE clock_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clockId);
            ps.executeUpdate();
        }
    }

    /** Sum of completed (clocked-out) hours for an employee between start and end (inclusive). */
    public double totalHours(Connection conn, int employeeId, java.time.LocalDate start, java.time.LocalDate end) throws SQLException {
        String sql = "SELECT SUM(TIMESTAMPDIFF(MINUTE, clock_in, clock_out)) AS minutes FROM time_clock " +
                "WHERE employee_id = ? AND clock_out IS NOT NULL AND clock_in >= ? AND clock_in < ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            ps.setTimestamp(2, Timestamp.valueOf(start.atStartOfDay()));
            ps.setTimestamp(3, Timestamp.valueOf(end.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int minutes = rs.getInt("minutes");
                    return rs.wasNull() ? 0.0 : minutes / 60.0;
                }
            }
        }
        return 0.0;
    }

    public List<TimeClockEntry> recentForEmployee(Connection conn, int employeeId, int limit) throws SQLException {
        String sql = "SELECT * FROM time_clock WHERE employee_id = ? ORDER BY clock_in DESC LIMIT ?";
        List<TimeClockEntry> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    private TimeClockEntry map(ResultSet rs) throws SQLException {
        TimeClockEntry e = new TimeClockEntry();
        e.setId(rs.getInt("clock_id"));
        e.setEmployeeId(rs.getInt("employee_id"));
        Timestamp in = rs.getTimestamp("clock_in");
        if (in != null) e.setClockIn(in.toLocalDateTime());
        Timestamp out = rs.getTimestamp("clock_out");
        if (out != null) e.setClockOut(out.toLocalDateTime());
        return e;
    }
}
