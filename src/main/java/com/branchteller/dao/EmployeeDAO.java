package com.branchteller.dao;

import com.branchteller.model.Employee;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EmployeeDAO {

    public int insert(Connection conn, Employee e) throws SQLException {
        String sql = "INSERT INTO employees (full_name, position, hourly_rate, hire_date, active) VALUES (?, ?, ?, ?, TRUE)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, e.getFullName());
            ps.setString(2, e.getPosition());
            ps.setBigDecimal(3, e.getHourlyRate());
            ps.setDate(4, Date.valueOf(e.getHireDate()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public List<Employee> findAllActive(Connection conn) throws SQLException {
        String sql = "SELECT * FROM employees WHERE active = TRUE ORDER BY full_name ASC";
        List<Employee> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) results.add(map(rs));
        }
        return results;
    }

    public Optional<Employee> findById(Connection conn, int employeeId) throws SQLException {
        String sql = "SELECT * FROM employees WHERE employee_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    private Employee map(ResultSet rs) throws SQLException {
        Employee e = new Employee();
        e.setId(rs.getInt("employee_id"));
        e.setFullName(rs.getString("full_name"));
        e.setPosition(rs.getString("position"));
        e.setHourlyRate(rs.getBigDecimal("hourly_rate"));
        Date hireDate = rs.getDate("hire_date");
        if (hireDate != null) e.setHireDate(hireDate.toLocalDate());
        e.setActive(rs.getBoolean("active"));
        int userId = rs.getInt("user_id");
        e.setUserId(rs.wasNull() ? null : userId);
        return e;
    }
}
