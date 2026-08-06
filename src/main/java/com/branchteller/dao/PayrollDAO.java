package com.branchteller.dao;

import com.branchteller.model.PayrollRun;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PayrollDAO {

    public int insert(Connection conn, PayrollRun run) throws SQLException {
        String sql = "INSERT INTO payroll_runs (employee_id, period_start, period_end, hours_worked, gross_pay, tax_withheld, net_pay) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, run.getEmployeeId());
            ps.setDate(2, Date.valueOf(run.getPeriodStart()));
            ps.setDate(3, Date.valueOf(run.getPeriodEnd()));
            ps.setBigDecimal(4, run.getHoursWorked());
            ps.setBigDecimal(5, run.getGrossPay());
            ps.setBigDecimal(6, run.getTaxWithheld());
            ps.setBigDecimal(7, run.getNetPay());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public List<PayrollRun> findByEmployee(Connection conn, int employeeId) throws SQLException {
        String sql = "SELECT p.*, e.full_name AS employee_name FROM payroll_runs p " +
                "JOIN employees e ON e.employee_id = p.employee_id " +
                "WHERE p.employee_id = ? ORDER BY p.period_start DESC";
        List<PayrollRun> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    public List<PayrollRun> findAll(Connection conn, int limit) throws SQLException {
        String sql = "SELECT p.*, e.full_name AS employee_name FROM payroll_runs p " +
                "JOIN employees e ON e.employee_id = p.employee_id " +
                "ORDER BY p.run_date DESC LIMIT ?";
        List<PayrollRun> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    private PayrollRun map(ResultSet rs) throws SQLException {
        PayrollRun run = new PayrollRun();
        run.setId(rs.getInt("run_id"));
        run.setEmployeeId(rs.getInt("employee_id"));
        Date start = rs.getDate("period_start");
        if (start != null) run.setPeriodStart(start.toLocalDate());
        Date end = rs.getDate("period_end");
        if (end != null) run.setPeriodEnd(end.toLocalDate());
        run.setHoursWorked(rs.getBigDecimal("hours_worked"));
        run.setGrossPay(rs.getBigDecimal("gross_pay"));
        run.setTaxWithheld(rs.getBigDecimal("tax_withheld"));
        run.setNetPay(rs.getBigDecimal("net_pay"));
        run.setEmployeeName(rs.getString("employee_name"));
        return run;
    }
}
