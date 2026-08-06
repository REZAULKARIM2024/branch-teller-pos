package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.AccountDAO;
import com.branchteller.dao.CustomerDAO;
import com.branchteller.model.Account;
import com.branchteller.model.Customer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Customer onboarding + KYC review + account opening. A customer starts PENDING on
 * registration; a manager/admin marks them VERIFIED or REJECTED. Only VERIFIED customers
 * can have a new account opened for them (enforced here, not just in the UI).
 */
public class CustomerService {

    private final CustomerDAO customerDAO = new CustomerDAO();
    private final AccountDAO accountDAO = new AccountDAO();
    private final AuditService auditService = new AuditService();

    public Customer register(String fullName, String phone, String email, String address) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Customer c = new Customer();
            c.setFullName(fullName);
            c.setPhone(phone);
            c.setEmail(email);
            c.setAddress(address);
            c.setKycStatus("PENDING");
            int id = customerDAO.create(conn, c);
            c.setId(id);
            auditService.log(conn, null, "CUSTOMER_REGISTERED", "customer", id, null, "PENDING");
            return c;
        }
    }

    public List<Customer> findAll() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return customerDAO.findAll(conn);
        }
    }

    public void verifyKyc(int customerId, int actorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            customerDAO.updateKycStatus(conn, customerId, "VERIFIED");
            auditService.log(conn, actorId, "KYC_VERIFIED", "customer", customerId, "PENDING", "VERIFIED");
        }
    }

    public void rejectKyc(int customerId, int actorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            customerDAO.updateKycStatus(conn, customerId, "REJECTED");
            auditService.log(conn, actorId, "KYC_REJECTED", "customer", customerId, "PENDING", "REJECTED");
        }
    }

    /** Opens a new account for a VERIFIED customer with a zero opening balance. */
    public Account openAccount(int customerId, int branchId, String accountType, BigDecimal interestRate, int actorId)
            throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Customer customer = customerDAO.findById(conn, customerId)
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
            if (!"VERIFIED".equals(customer.getKycStatus())) {
                throw new IllegalStateException("Customer " + customerId + " is not KYC-verified");
            }

            Account account = new Account();
            account.setAccountNumber(generateAccountNumber());
            account.setCustomerId(customerId);
            account.setBranchId(branchId);
            account.setAccountType(accountType);
            account.setBalance(BigDecimal.ZERO);
            account.setInterestRate(interestRate);
            account.setOpenedDate(LocalDate.now());
            int id = accountDAO.create(conn, account);
            account.setId(id);

            auditService.log(conn, actorId, "ACCOUNT_OPENED", "account", id, null, accountType);
            return account;
        }
    }

    private String generateAccountNumber() {
        long suffix = ThreadLocalRandom.current().nextLong(100000, 999999);
        return "NYC-" + suffix;
    }
}
