-- Catch-up script: Phase 17-19 only (Phases 8-16 already loaded successfully).
-- The original schema_phase8_expansion.sql run aborted at the Phase 17 ALTER TABLE
-- statement because this MySQL server version doesn't support "ADD COLUMN IF NOT EXISTS".
USE branch_teller;

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
