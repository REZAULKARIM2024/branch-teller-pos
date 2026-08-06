package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.NotificationDAO;
import com.branchteller.model.Notification;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Simulated SMS/email alerts -- no real gateway is called; every "send" is logged to the
 * notifications table so the app can show what would have gone out. Auto-fires on every
 * teller-counter transaction from TellerPanel.
 */
public class NotificationService {

    private final NotificationDAO notificationDAO = new NotificationDAO();

    public Notification send(int customerId, String channel, String subject, String message) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Notification n = new Notification();
            n.setCustomerId(customerId);
            n.setChannel(channel);
            n.setSubject(subject);
            n.setMessage(message);
            n.setStatus("SENT");
            int id = notificationDAO.insert(conn, n);
            n.setId(id);
            return n;
        }
    }

    public void notifyTransaction(int customerId, String txnType, BigDecimal amount, BigDecimal balanceAfter) throws SQLException {
        String message = "A " + txnType + " of $" + amount + " was posted to your account." +
                (balanceAfter != null ? " New balance: $" + balanceAfter + "." : "");
        send(customerId, "SMS", "Transaction Alert", message);
        send(customerId, "EMAIL", "Transaction Alert", message);
    }

    public List<Notification> recent(int limit) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return notificationDAO.findRecent(conn, limit);
        }
    }

    public List<Notification> forCustomer(int customerId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return notificationDAO.findByCustomer(conn, customerId);
        }
    }
}
