package com.branchteller.api;

import com.branchteller.model.Account;
import com.branchteller.model.Customer;
import com.branchteller.model.GlAccount;
import com.branchteller.model.Transaction;
import com.branchteller.service.BankingService;
import com.branchteller.service.CustomerService;
import com.branchteller.service.GlService;
import com.branchteller.service.InsufficientFundsException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

/**
 * Exposes the same service layer over HTTP/JSON, built entirely on the JDK's built-in
 * com.sun.net.httpserver.HttpServer + our hand-rolled Json class -- no Spring/Jackson,
 * same zero-dependency philosophy as the NY Coffee Co. POS project's ApiServer.
 *
 * Run: java -cp target/classes com.branchteller.api.ApiServer
 * Listens on http://localhost:8082 by default.
 */
public class ApiServer {

    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("API_PORT", "8082"));

    private final BankingService bankingService = new BankingService();
    private final CustomerService customerService = new CustomerService();
    private final GlService glService = new GlService();

    public static void main(String[] args) throws IOException {
        new ApiServer().start();
    }

    /** Returns the started HttpServer so callers (tests, embedders) can stop() it later --
     *  main() ignores the return value, which is a source-compatible change. */
    public HttpServer start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/health", this::handleHealth);
        server.createContext("/api/customers", this::handleCustomers);
        server.createContext("/api/accounts/", this::handleAccounts);
        server.createContext("/api/transactions/deposit", this::handleDeposit);
        server.createContext("/api/transactions/withdraw", this::handleWithdraw);
        server.createContext("/api/gl/trial-balance", this::handleTrialBalance);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Branch Teller API listening on http://localhost:" + PORT);
        return server;
    }

    // ---------- Handlers ----------

    private void handleHealth(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { sendNoContent(exchange); return; }
        Map<String, Object> body = Json.object();
        body.put("status", "ok");
        try {
            bankingService.lookupAccount("__health_check__");
            body.put("db", "connected");
        } catch (SQLException e) {
            body.put("db", "unreachable");
            body.put("dbError", e.getMessage());
        }
        sendJson(exchange, 200, body);
    }

    private void handleCustomers(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { sendNoContent(exchange); return; }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorBody("Method not allowed"));
            return;
        }
        try {
            List<Customer> customers = customerService.findAll();
            sendJson(exchange, 200, customers.stream().map(this::customerJson).toList());
        } catch (SQLException e) {
            sendJson(exchange, 500, errorBody(e.getMessage()));
        }
    }

    /** Routes both GET /api/accounts/{number} and GET /api/accounts/{number}/transactions. */
    private void handleAccounts(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { sendNoContent(exchange); return; }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorBody("Method not allowed"));
            return;
        }
        String path = exchange.getRequestURI().getPath(); // /api/accounts/{number}[/transactions]
        String remainder = path.substring("/api/accounts/".length());
        String[] parts = remainder.split("/");
        if (parts.length == 0 || parts[0].isEmpty()) {
            sendJson(exchange, 400, errorBody("Account number required"));
            return;
        }
        String accountNumber = parts[0];

        try {
            Optional<Account> account = bankingService.lookupAccount(accountNumber);
            if (account.isEmpty()) {
                sendJson(exchange, 404, errorBody("Account not found: " + accountNumber));
                return;
            }
            if (parts.length > 1 && "transactions".equals(parts[1])) {
                // Transaction history isn't exposed via BankingService directly (by design --
                // it's a DAO-level concern); a real build would add a TransactionService here.
                sendJson(exchange, 501, errorBody("Transaction history endpoint not yet implemented"));
                return;
            }
            sendJson(exchange, 200, accountJson(account.get()));
        } catch (SQLException e) {
            sendJson(exchange, 500, errorBody(e.getMessage()));
        }
    }

    /** Exposes the same trial balance the Swing General Ledger tab shows, so the React
     *  demo frontend can render it without duplicating any ledger logic. */
    private void handleTrialBalance(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { sendNoContent(exchange); return; }
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorBody("Method not allowed"));
            return;
        }
        try {
            List<GlAccount> accounts = glService.trialBalance();
            sendJson(exchange, 200, accounts.stream().map(this::glAccountJson).toList());
        } catch (SQLException e) {
            sendJson(exchange, 500, errorBody(e.getMessage()));
        }
    }

    private void handleDeposit(HttpExchange exchange) throws IOException {
        handleTransaction(exchange, true);
    }

    private void handleWithdraw(HttpExchange exchange) throws IOException {
        handleTransaction(exchange, false);
    }

    private void handleTransaction(HttpExchange exchange, boolean isDeposit) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { sendNoContent(exchange); return; }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorBody("Method not allowed"));
            return;
        }
        try {
            Map<String, Object> req = Json.parseObject(readBody(exchange));
            String accountNumber = (String) req.get("accountNumber");
            double amount = ((Number) req.get("amount")).doubleValue();
            int tellerId = ((Number) req.get("tellerId")).intValue();
            String note = req.get("note") == null ? "" : (String) req.get("note");

            Optional<Account> account = bankingService.lookupAccount(accountNumber);
            if (account.isEmpty()) {
                sendJson(exchange, 404, errorBody("Account not found: " + accountNumber));
                return;
            }

            Transaction txn = isDeposit
                    ? bankingService.deposit(account.get().getId(), java.math.BigDecimal.valueOf(amount), tellerId, note)
                    : bankingService.withdraw(account.get().getId(), java.math.BigDecimal.valueOf(amount), tellerId, note);

            sendJson(exchange, 201, transactionJson(txn));
        } catch (InsufficientFundsException e) {
            sendJson(exchange, 422, errorBody(e.getMessage()));
        } catch (SQLException e) {
            sendJson(exchange, 500, errorBody(e.getMessage()));
        } catch (RuntimeException e) {
            sendJson(exchange, 400, errorBody("Invalid request: " + e.getMessage()));
        }
    }

    // ---------- JSON shaping ----------

    private Map<String, Object> accountJson(Account a) {
        Map<String, Object> m = Json.object();
        m.put("accountNumber", a.getAccountNumber());
        m.put("customerName", a.getCustomerName());
        m.put("accountType", a.getAccountType());
        m.put("balance", a.getBalance().toString());
        m.put("status", a.getStatus());
        return m;
    }

    private Map<String, Object> customerJson(Customer c) {
        Map<String, Object> m = Json.object();
        m.put("id", c.getId());
        m.put("fullName", c.getFullName());
        m.put("phone", c.getPhone());
        m.put("email", c.getEmail());
        m.put("kycStatus", c.getKycStatus());
        return m;
    }

    private Map<String, Object> transactionJson(Transaction t) {
        Map<String, Object> m = Json.object();
        m.put("id", t.getId());
        m.put("type", t.getTxnType());
        m.put("amount", t.getAmount().toString());
        m.put("balanceAfter", t.getBalanceAfter().toString());
        m.put("note", t.getNote());
        return m;
    }

    private Map<String, Object> glAccountJson(GlAccount a) {
        Map<String, Object> m = Json.object();
        m.put("code", a.getCode());
        m.put("name", a.getName());
        m.put("accountClass", a.getAccountClass());
        m.put("normalBalance", a.getNormalBalance());
        m.put("debitTotal", a.getDebitTotal().toString());
        m.put("creditTotal", a.getCreditTotal().toString());
        m.put("netBalance", a.getNetBalance().toString());
        return m;
    }

    private Map<String, Object> errorBody(String message) {
        Map<String, Object> m = Json.object();
        m.put("error", message);
        return m;
    }

    // ---------- HTTP plumbing ----------

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[1024];
            int read;
            while ((read = is.read(chunk)) != -1) buffer.write(chunk, 0, read);
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] payload = Json.write(body).getBytes(StandardCharsets.UTF_8);
        addCorsHeaders(exchange);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    /** Answers a CORS preflight (OPTIONS) request with no body -- needed so the React
     *  dev server (a different origin: localhost:5173 vs this API's localhost:8082) is
     *  allowed to call GET/POST endpoints with a JSON Content-Type header. */
    private void sendNoContent(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        exchange.sendResponseHeaders(204, -1);
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }
}
