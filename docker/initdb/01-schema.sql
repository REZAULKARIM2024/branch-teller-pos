-- Branch Teller POS -- Phase 1 schema
DROP DATABASE IF EXISTS branch_teller;
CREATE DATABASE branch_teller CHARACTER SET utf8mb4;
USE branch_teller;

CREATE TABLE branches (
    branch_id     INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    address       VARCHAR(255),
    routing_code  VARCHAR(20) UNIQUE
);

CREATE TABLE users (
    user_id        INT AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(50) UNIQUE NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    salt           VARCHAR(64) NOT NULL,
    full_name      VARCHAR(100) NOT NULL,
    role           ENUM('ADMIN','MANAGER','TELLER') NOT NULL,
    branch_id      INT,
    active         BOOLEAN DEFAULT TRUE,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (branch_id) REFERENCES branches(branch_id)
);

CREATE TABLE customers (
    customer_id    INT AUTO_INCREMENT PRIMARY KEY,
    full_name      VARCHAR(100) NOT NULL,
    phone          VARCHAR(20) UNIQUE NOT NULL,
    email          VARCHAR(100),
    address        VARCHAR(255),
    kyc_status     ENUM('PENDING','VERIFIED','REJECTED') DEFAULT 'PENDING',
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE accounts (
    account_id      INT AUTO_INCREMENT PRIMARY KEY,
    account_number  VARCHAR(20) UNIQUE NOT NULL,
    customer_id     INT NOT NULL,
    branch_id       INT NOT NULL,
    account_type    ENUM('SAVINGS','CURRENT','FD','RD') NOT NULL DEFAULT 'SAVINGS',
    balance         DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    interest_rate   DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    status          ENUM('ACTIVE','DORMANT','CLOSED') DEFAULT 'ACTIVE',
    opened_date     DATE NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (branch_id) REFERENCES branches(branch_id)
);

CREATE TABLE transactions (
    txn_id         INT AUTO_INCREMENT PRIMARY KEY,
    account_id     INT NOT NULL,
    txn_type       ENUM('DEPOSIT','WITHDRAW','TRANSFER_OUT','TRANSFER_IN') NOT NULL,
    amount         DECIMAL(15,2) NOT NULL,
    balance_after  DECIMAL(15,2) NOT NULL,
    teller_id      INT NOT NULL,
    related_txn_id INT NULL,
    channel        ENUM('COUNTER','ATM','ONLINE') DEFAULT 'COUNTER',
    note           VARCHAR(255),
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    FOREIGN KEY (teller_id) REFERENCES users(user_id)
);

CREATE TABLE audit_trail (
    audit_id      INT AUTO_INCREMENT PRIMARY KEY,
    actor_id      INT,
    action        VARCHAR(50) NOT NULL,
    entity_type   VARCHAR(50) NOT NULL,
    entity_id     INT,
    before_value  VARCHAR(255),
    after_value   VARCHAR(255),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed data -------------------------------------------------------
INSERT INTO branches (name, address, routing_code) VALUES
    ('NY Financial Bank - Main Branch', '123 Broadway, New York, NY', 'RTG-0001');

-- Demo users: password for both is 'admin123' / 'teller123' (see README to regenerate hashes)
INSERT INTO users (username, password_hash, salt, full_name, role, branch_id) VALUES
    ('admin',  'CHANGE_ME_HASH', 'CHANGE_ME_SALT', 'Admin User',  'ADMIN',  1),
    ('teller1','CHANGE_ME_HASH', 'CHANGE_ME_SALT', 'Teller One',  'TELLER', 1);

INSERT INTO customers (full_name, phone, email, address, kyc_status) VALUES
    ('Rezaul Karim', '+1-555-0100', 'rknyc2021@gmail.com', '456 5th Ave, New York, NY', 'VERIFIED');

INSERT INTO accounts (account_number, customer_id, branch_id, account_type, balance, interest_rate, opened_date) VALUES
    ('NYC-SAV-000001', 1, 1, 'SAVINGS', 2500.00, 2.50, CURDATE());

-- Phase 2 -----------------------------------------------------------

CREATE TABLE cash_drawer_logs (
    log_id       INT AUTO_INCREMENT PRIMARY KEY,
    teller_id    INT NOT NULL,
    branch_id    INT NOT NULL,
    action       ENUM('PAID_IN','PAID_OUT','CASH_PULL','NO_SALE','TILL_COUNT') NOT NULL,
    amount       DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    note         VARCHAR(255),
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (teller_id) REFERENCES users(user_id),
    FOREIGN KEY (branch_id) REFERENCES branches(branch_id)
);

CREATE TABLE cheques (
    cheque_id    INT AUTO_INCREMENT PRIMARY KEY,
    account_id   INT NOT NULL,
    cheque_no    VARCHAR(30) NOT NULL,
    amount       DECIMAL(15,2) NOT NULL,
    status       ENUM('PENDING','CLEARED','BOUNCED') NOT NULL DEFAULT 'PENDING',
    teller_id    INT NOT NULL,
    deposit_date DATE NOT NULL,
    clear_date   DATE NULL,
    note         VARCHAR(255),
    FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    FOREIGN KEY (teller_id) REFERENCES users(user_id)
);

-- Phase 3 -----------------------------------------------------------

CREATE TABLE loans (
    loan_id        INT AUTO_INCREMENT PRIMARY KEY,
    customer_id    INT NOT NULL,
    account_id     INT NOT NULL,
    loan_type      VARCHAR(50) NOT NULL,
    principal      DECIMAL(15,2) NOT NULL,
    interest_rate  DECIMAL(5,2) NOT NULL,
    tenure_months  INT NOT NULL,
    status         ENUM('APPLIED','APPROVED','REJECTED','DISBURSED','CLOSED') NOT NULL DEFAULT 'APPLIED',
    applied_date   DATE NOT NULL,
    approved_by    INT NULL,
    disbursed_date DATE NULL,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    FOREIGN KEY (approved_by) REFERENCES users(user_id)
);

CREATE TABLE loan_repayments (
    repayment_id  INT AUTO_INCREMENT PRIMARY KEY,
    loan_id       INT NOT NULL,
    installment_no INT NOT NULL,
    due_date      DATE NOT NULL,
    amount_due    DECIMAL(15,2) NOT NULL,
    amount_paid   DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    status        ENUM('PENDING','PAID','OVERDUE') NOT NULL DEFAULT 'PENDING',
    paid_date     DATE NULL,
    FOREIGN KEY (loan_id) REFERENCES loans(loan_id)
);

-- Phase 4 -----------------------------------------------------------

CREATE TABLE interest_accruals (
    accrual_id    INT AUTO_INCREMENT PRIMARY KEY,
    account_id    INT NOT NULL,
    period        VARCHAR(7) NOT NULL,   -- 'YYYY-MM'
    rate_applied  DECIMAL(5,2) NOT NULL,
    amount        DECIMAL(15,2) NOT NULL,
    posted_date   DATE NOT NULL,
    UNIQUE KEY uniq_account_period (account_id, period),
    FOREIGN KEY (account_id) REFERENCES accounts(account_id)
);

-- Phase 7 -----------------------------------------------------------

CREATE TABLE suspicious_activity_flags (
    flag_id      INT AUTO_INCREMENT PRIMARY KEY,
    account_id   INT NOT NULL,
    txn_id       INT NULL,
    reason       VARCHAR(255) NOT NULL,
    amount       DECIMAL(15,2) NOT NULL,
    flagged_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed     BOOLEAN NOT NULL DEFAULT FALSE,
    reviewed_by  INT NULL,
    review_date  TIMESTAMP NULL,
    FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    FOREIGN KEY (txn_id) REFERENCES transactions(txn_id),
    FOREIGN KEY (reviewed_by) REFERENCES users(user_id)
);

CREATE TABLE employees (
    employee_id   INT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    position      VARCHAR(50) NOT NULL,
    hourly_rate   DECIMAL(8,2) NOT NULL,
    hire_date     DATE NOT NULL,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    user_id       INT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE time_clock (
    clock_id     INT AUTO_INCREMENT PRIMARY KEY,
    employee_id  INT NOT NULL,
    clock_in     TIMESTAMP NOT NULL,
    clock_out    TIMESTAMP NULL,
    FOREIGN KEY (employee_id) REFERENCES employees(employee_id)
);

CREATE TABLE payroll_runs (
    run_id         INT AUTO_INCREMENT PRIMARY KEY,
    employee_id    INT NOT NULL,
    period_start   DATE NOT NULL,
    period_end     DATE NOT NULL,
    hours_worked   DECIMAL(6,2) NOT NULL,
    gross_pay      DECIMAL(10,2) NOT NULL,
    tax_withheld   DECIMAL(10,2) NOT NULL,
    net_pay        DECIMAL(10,2) NOT NULL,
    run_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (employee_id) REFERENCES employees(employee_id)
);
USE branch_teller;

-- Phase 8: General Ledger / Chart of Accounts --------------------------

CREATE TABLE IF NOT EXISTS gl_accounts (
    gl_account_id  INT AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(20) UNIQUE NOT NULL,
    name           VARCHAR(100) NOT NULL,
    account_class  ENUM('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE') NOT NULL,
    normal_balance ENUM('DEBIT','CREDIT') NOT NULL
);

CREATE TABLE IF NOT EXISTS gl_entries (
    entry_id      INT AUTO_INCREMENT PRIMARY KEY,
    gl_account_id INT NOT NULL,
    txn_id        INT NULL,
    debit         DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    credit        DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    description   VARCHAR(255),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (gl_account_id) REFERENCES gl_accounts(gl_account_id),
    FOREIGN KEY (txn_id) REFERENCES transactions(txn_id)
);

INSERT INTO gl_accounts (code, name, account_class, normal_balance) VALUES
    ('1000','Cash and Cash Equivalents','ASSET','DEBIT'),
    ('1100','Customer Deposits Control','LIABILITY','CREDIT'),
    ('1200','Loans Receivable','ASSET','DEBIT'),
    ('2000','Interest Payable','LIABILITY','CREDIT'),
    ('4000','Interest Income','INCOME','CREDIT'),
    ('4100','Fee Income','INCOME','CREDIT'),
    ('5000','Interest Expense','EXPENSE','DEBIT'),
    ('5100','Salaries and Payroll Expense','EXPENSE','DEBIT'),
    ('5200','Loan Loss Provision','EXPENSE','DEBIT');

-- Phase 9: Maker-checker dual control -----------------------------------

CREATE TABLE IF NOT EXISTS pending_approvals (
    approval_id   INT AUTO_INCREMENT PRIMARY KEY,
    request_type  VARCHAR(50) NOT NULL,
    account_id    INT NULL,
    to_account_id INT NULL,
    amount        DECIMAL(15,2) NULL,
    requested_by  INT NOT NULL,
    status        ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    approved_by   INT NULL,
    request_note  VARCHAR(255),
    decision_note VARCHAR(255),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    decided_at    TIMESTAMP NULL,
    FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    FOREIGN KEY (to_account_id) REFERENCES accounts(account_id),
    FOREIGN KEY (requested_by) REFERENCES users(user_id),
    FOREIGN KEY (approved_by) REFERENCES users(user_id)
);

-- Phase 10: Account holds / liens ----------------------------------------

CREATE TABLE IF NOT EXISTS account_holds (
    hold_id      INT AUTO_INCREMENT PRIMARY KEY,
    account_id   INT NOT NULL,
    amount       DECIMAL(15,2) NOT NULL,
    reason       VARCHAR(255) NOT NULL,
    placed_by    INT NOT NULL,
    status       ENUM('ACTIVE','RELEASED') NOT NULL DEFAULT 'ACTIVE',
    placed_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    released_at  TIMESTAMP NULL,
    released_by  INT NULL,
    FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    FOREIGN KEY (placed_by) REFERENCES users(user_id),
    FOREIGN KEY (released_by) REFERENCES users(user_id)
);

-- Phase 11: Standing instructions / auto-pay ------------------------------

CREATE TABLE IF NOT EXISTS standing_instructions (
    instruction_id    INT AUTO_INCREMENT PRIMARY KEY,
    from_account_id   INT NOT NULL,
    to_account_number VARCHAR(20) NOT NULL,
    amount            DECIMAL(15,2) NOT NULL,
    frequency         ENUM('WEEKLY','MONTHLY') NOT NULL DEFAULT 'MONTHLY',
    next_run_date     DATE NOT NULL,
    status            ENUM('ACTIVE','PAUSED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    note              VARCHAR(255),
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (from_account_id) REFERENCES accounts(account_id)
);

CREATE TABLE IF NOT EXISTS standing_instruction_runs (
    run_id         INT AUTO_INCREMENT PRIMARY KEY,
    instruction_id INT NOT NULL,
    run_date       DATE NOT NULL,
    status         ENUM('SUCCESS','FAILED') NOT NULL,
    detail         VARCHAR(255),
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (instruction_id) REFERENCES standing_instructions(instruction_id)
);

-- Phase 12: Card management -----------------------------------------------

CREATE TABLE IF NOT EXISTS cards (
    card_id         INT AUTO_INCREMENT PRIMARY KEY,
    account_id      INT NOT NULL,
    card_number     VARCHAR(20) UNIQUE NOT NULL,
    card_type       ENUM('DEBIT','CREDIT') NOT NULL,
    cardholder_name VARCHAR(100) NOT NULL,
    expiry_date     DATE NOT NULL,
    credit_limit    DECIMAL(15,2) NULL,
    daily_limit     DECIMAL(15,2) NOT NULL DEFAULT 1000.00,
    status          ENUM('ACTIVE','BLOCKED','EXPIRED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    issued_date     DATE NOT NULL,
    FOREIGN KEY (account_id) REFERENCES accounts(account_id)
);

-- Phase 13: AML/compliance upgrade -----------------------------------------

CREATE TABLE IF NOT EXISTS sanctions_list (
    entry_id   INT AUTO_INCREMENT PRIMARY KEY,
    full_name  VARCHAR(100) NOT NULL,
    list_type  ENUM('OFAC','PEP','INTERNAL') NOT NULL,
    note       VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS screening_results (
    result_id        INT AUTO_INCREMENT PRIMARY KEY,
    customer_id       INT NOT NULL,
    matched_entry_id  INT NULL,
    match_score       DECIMAL(5,2) NOT NULL,
    status            ENUM('CLEAR','POTENTIAL_MATCH','CONFIRMED_MATCH') NOT NULL DEFAULT 'CLEAR',
    screened_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_by       INT NULL,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (matched_entry_id) REFERENCES sanctions_list(entry_id),
    FOREIGN KEY (reviewed_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS regulatory_reports (
    report_id          INT AUTO_INCREMENT PRIMARY KEY,
    report_type        ENUM('SAR','CTR') NOT NULL,
    reference_no       VARCHAR(30) UNIQUE NOT NULL,
    related_account_id INT NULL,
    related_flag_id    INT NULL,
    filed_by           INT NOT NULL,
    filed_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    narrative          TEXT,
    FOREIGN KEY (related_account_id) REFERENCES accounts(account_id),
    FOREIGN KEY (related_flag_id) REFERENCES suspicious_activity_flags(flag_id),
    FOREIGN KEY (filed_by) REFERENCES users(user_id)
);

INSERT INTO sanctions_list (full_name, list_type, note) VALUES
    ('Viktor Bout', 'OFAC', 'Sample OFAC SDN-style test entry (fictional use)'),
    ('Ali Khamenei', 'OFAC', 'Sample OFAC SDN-style test entry (fictional use)'),
    ('Nicolas Maduro', 'OFAC', 'Sample OFAC SDN-style test entry (fictional use)'),
    ('John Doe PEP', 'PEP', 'Sample politically-exposed-person test entry'),
    ('Jane Smith PEP', 'PEP', 'Sample politically-exposed-person test entry');

-- Phase 14: Multi-branch support --------------------------------------------

INSERT INTO branches (name, address, routing_code) VALUES
    ('NY Financial Bank - Midtown Branch', '350 5th Ave, New York, NY', 'RTG-0002'),
    ('NY Financial Bank - Brooklyn Branch', '1 MetroTech Center, Brooklyn, NY', 'RTG-0003');

-- Phase 15: Payment network simulation ---------------------------------------

CREATE TABLE IF NOT EXISTS external_transfers (
    ext_transfer_id     INT AUTO_INCREMENT PRIMARY KEY,
    account_id          INT NOT NULL,
    transfer_type       ENUM('NEFT','RTGS','WIRE') NOT NULL,
    beneficiary_name    VARCHAR(100) NOT NULL,
    beneficiary_bank    VARCHAR(100) NOT NULL,
    beneficiary_account VARCHAR(30) NOT NULL,
    routing_swift       VARCHAR(20) NOT NULL,
    amount              DECIMAL(15,2) NOT NULL,
    status              ENUM('INITIATED','PROCESSING','COMPLETED','FAILED') NOT NULL DEFAULT 'INITIATED',
    reference_no        VARCHAR(30) UNIQUE NOT NULL,
    initiated_by        INT NOT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMP NULL,
    FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    FOREIGN KEY (initiated_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS billers (
    biller_id  INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    category   VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS bill_payments (
    payment_id   INT AUTO_INCREMENT PRIMARY KEY,
    account_id   INT NOT NULL,
    biller_id    INT NOT NULL,
    reference_no VARCHAR(30) UNIQUE NOT NULL,
    amount       DECIMAL(15,2) NOT NULL,
    status       ENUM('COMPLETED','FAILED') NOT NULL DEFAULT 'COMPLETED',
    paid_by      INT NOT NULL,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    FOREIGN KEY (biller_id) REFERENCES billers(biller_id),
    FOREIGN KEY (paid_by) REFERENCES users(user_id)
);

INSERT INTO billers (name, category) VALUES
    ('Con Edison Electric', 'Utilities'),
    ('National Grid Gas', 'Utilities'),
    ('NYC Water Board', 'Utilities'),
    ('Verizon Wireless', 'Telecom'),
    ('Spectrum Cable & Internet', 'Telecom'),
    ('Metro Property Insurance', 'Insurance'),
    ('NY Financial Bank Credit Card', 'Credit Card'),
    ('NYC Department of Finance (Property Tax)', 'Government');

-- Phase 16: Customer notifications ---------------------------------------------

CREATE TABLE IF NOT EXISTS notifications (
    notification_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id      INT NOT NULL,
    channel          ENUM('SMS','EMAIL') NOT NULL,
    subject          VARCHAR(150),
    message          VARCHAR(500) NOT NULL,
    status           ENUM('QUEUED','SENT','FAILED') NOT NULL DEFAULT 'SENT',
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);

-- Phase 17: Credit scoring / underwriting ---------------------------------------

ALTER TABLE customers ADD COLUMN credit_score INT NULL;

CREATE TABLE IF NOT EXISTS credit_score_history (
    history_id   INT AUTO_INCREMENT PRIMARY KEY,
    customer_id   INT NOT NULL,
    score         INT NOT NULL,
    rating        ENUM('POOR','FAIR','GOOD','VERY_GOOD','EXCELLENT') NOT NULL,
    computed_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);

-- Phase 18: Security hardening ----------------------------------------------------

ALTER TABLE users ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN password_last_changed TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE users ADD COLUMN otp_required BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN approval_limit DECIMAL(15,2) NOT NULL DEFAULT 5000.00;

CREATE TABLE IF NOT EXISTS login_otps (
    otp_id      INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT NOT NULL,
    otp_code    VARCHAR(6) NOT NULL,
    expires_at  TIMESTAMP NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

UPDATE users SET approval_limit = 1000000.00 WHERE role IN ('ADMIN','MANAGER');
UPDATE users SET approval_limit = 5000.00 WHERE role = 'TELLER';

-- Phase 19: Complaint management / CRM ---------------------------------------------

CREATE TABLE IF NOT EXISTS complaints (
    complaint_id     INT AUTO_INCREMENT PRIMARY KEY,
    customer_id      INT NOT NULL,
    category         VARCHAR(50) NOT NULL,
    description      VARCHAR(500) NOT NULL,
    status           ENUM('OPEN','IN_PROGRESS','RESOLVED','CLOSED') NOT NULL DEFAULT 'OPEN',
    priority         ENUM('LOW','MEDIUM','HIGH') NOT NULL DEFAULT 'MEDIUM',
    assigned_to      INT NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at      TIMESTAMP NULL,
    resolution_note  VARCHAR(500),
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (assigned_to) REFERENCES users(user_id)
);
