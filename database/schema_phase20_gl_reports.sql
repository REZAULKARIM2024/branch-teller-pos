-- Phase 20: Journal / Ledger / Financial Reports support.
-- Adds an EQUITY account so the Balance Sheet has a capital line to balance against
-- (Assets = Liabilities + Equity + Net Income To Date). Idempotent: code has a UNIQUE
-- constraint, so INSERT IGNORE is a safe no-op if this has already been loaded.
USE branch_teller;

INSERT IGNORE INTO gl_accounts (code, name, account_class, normal_balance)
VALUES ('3000', 'Owners Equity / Capital', 'EQUITY', 'CREDIT');
