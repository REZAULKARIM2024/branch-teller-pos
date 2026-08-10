package com.branchteller.dao;

import com.branchteller.model.Branch;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BranchDAO {

    public List<Branch> findAllWithStats(Connection conn) throws SQLException {
        String sql = "SELECT b.branch_id, b.name, b.address, b.routing_code, " +
                "(SELECT COUNT(*) FROM accounts a WHERE a.branch_id = b.branch_id) AS account_count, " +
                "(SELECT COUNT(*) FROM users u WHERE u.branch_id = b.branch_id) AS employee_count, " +
                "(SELECT COALESCE(SUM(a.balance),0) FROM accounts a WHERE a.branch_id = b.branch_id) AS total_deposits " +
                "FROM branches b ORDER BY b.branch_id";
        List<Branch> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Branch b = new Branch();
                b.setId(rs.getInt("branch_id"));
                b.setName(rs.getString("name"));
                b.setAddress(rs.getString("address"));
                b.setRoutingCode(rs.getString("routing_code"));
                b.setAccountCount(rs.getInt("account_count"));
                b.setEmployeeCount(rs.getInt("employee_count"));
                b.setTotalDeposits(rs.getBigDecimal("total_deposits"));
                results.add(b);
            }
        }
        return results;
    }

    /** True if a branch already has this exact routing code -- the DB's UNIQUE constraint on
     *  routing_code would catch this too, but checking first lets the caller raise a clear
     *  business-rule exception instead of surfacing a raw constraint-violation SQLException
     *  straight to the GUI. */
    public boolean routingCodeExists(Connection conn, String routingCode) throws SQLException {
        String sql = "SELECT COUNT(*) FROM branches WHERE routing_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, routingCode);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    public int insert(Connection conn, Branch b) throws SQLException {
        String sql = "INSERT INTO branches (name, address, routing_code) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, b.getName());
            ps.setString(2, b.getAddress());
            ps.setString(3, b.getRoutingCode());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }
}
