package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.BranchDAO;
import com.branchteller.model.Branch;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class BranchService {

    private final BranchDAO branchDAO = new BranchDAO();

    public List<Branch> allWithStats() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return branchDAO.findAllWithStats(conn);
        }
    }

    public Branch openBranch(String name, String address, String routingCode) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Branch b = new Branch();
            b.setName(name);
            b.setAddress(address);
            b.setRoutingCode(routingCode);
            int id = branchDAO.insert(conn, b);
            b.setId(id);
            return b;
        }
    }
}
