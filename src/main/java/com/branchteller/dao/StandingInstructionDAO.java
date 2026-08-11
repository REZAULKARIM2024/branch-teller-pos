package com.branchteller.dao;

import com.branchteller.model.StandingInstruction;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class StandingInstructionDAO {

    public int insert(Connection conn, StandingInstruction si) throws SQLException {
        String sql = "INSERT INTO standing_instructions (from_account_id, to_account_number, amount, frequency, next_run_date, note) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, si.getFromAccountId());
            ps.setString(2, si.getToAccountNumber());
            ps.setBigDecimal(3, si.getAmount());
            ps.setString(4, si.getFrequency());
            ps.setDate(5, Date.valueOf(si.getNextRunDate()));
            ps.setString(6, si.getNote());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public java.util.Optional<StandingInstruction> findById(Connection conn, int id) throws SQLException {
        String sql = "SELECT si.*, a.account_number AS from_account_number FROM standing_instructions si " +
                "JOIN accounts a ON a.account_id = si.from_account_id WHERE si.instruction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return java.util.Optional.of(map(rs));
            }
        }
        return java.util.Optional.empty();
    }

    public List<StandingInstruction> findAll(Connection conn) throws SQLException {
        String sql = "SELECT si.*, a.account_number AS from_account_number FROM standing_instructions si " +
                "JOIN accounts a ON a.account_id = si.from_account_id ORDER BY si.next_run_date";
        List<StandingInstruction> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) results.add(map(rs));
        }
        return results;
    }

    public List<StandingInstruction> findDue(Connection conn, LocalDate asOf) throws SQLException {
        String sql = "SELECT si.*, a.account_number AS from_account_number FROM standing_instructions si " +
                "JOIN accounts a ON a.account_id = si.from_account_id " +
                "WHERE si.status = 'ACTIVE' AND si.next_run_date <= ?";
        List<StandingInstruction> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(asOf));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(map(rs));
            }
        }
        return results;
    }

    public void updateStatus(Connection conn, int id, String status) throws SQLException {
        String sql = "UPDATE standing_instructions SET status = ? WHERE instruction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public void advanceNextRunDate(Connection conn, int id, LocalDate newDate) throws SQLException {
        String sql = "UPDATE standing_instructions SET next_run_date = ? WHERE instruction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(newDate));
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public void logRun(Connection conn, int instructionId, LocalDate runDate, String status, String detail) throws SQLException {
        String sql = "INSERT INTO standing_instruction_runs (instruction_id, run_date, status, detail) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, instructionId);
            ps.setDate(2, Date.valueOf(runDate));
            ps.setString(3, status);
            ps.setString(4, detail);
            ps.executeUpdate();
        }
    }

    private StandingInstruction map(ResultSet rs) throws SQLException {
        StandingInstruction si = new StandingInstruction();
        si.setId(rs.getInt("instruction_id"));
        si.setFromAccountId(rs.getInt("from_account_id"));
        si.setFromAccountNumber(rs.getString("from_account_number"));
        si.setToAccountNumber(rs.getString("to_account_number"));
        si.setAmount(rs.getBigDecimal("amount"));
        si.setFrequency(rs.getString("frequency"));
        Date nextRun = rs.getDate("next_run_date");
        if (nextRun != null) si.setNextRunDate(nextRun.toLocalDate());
        si.setStatus(rs.getString("status"));
        si.setNote(rs.getString("note"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) si.setCreatedAt(createdAt.toLocalDateTime());
        return si;
    }
}
