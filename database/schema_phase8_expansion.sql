-- Branch Teller POS -- Phases 8-19: real-world banking operations expansion
-- Run against an EXISTING branch_teller database (does not DROP DATABASE).
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
