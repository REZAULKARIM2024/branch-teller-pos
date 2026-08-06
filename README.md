# Branch Teller — Bank Branch Operations Platform

A full-stack core banking simulation: a Java Swing teller desktop application backed
by MySQL, a zero-dependency REST API layer, a React web console, an automated test
suite, CI, and containerized deployment. Built as an end-to-end demonstration of how a
mid-size retail bank branch's counter operations, general ledger, compliance, and
back-office workflows fit together — not a toy CRUD app.

## Overview

Branch Teller models a single bank branch from the teller counter outward: account
opening and KYC, deposits/withdrawals/transfers, cheque clearing, loans with
amortizing EMI schedules, monthly interest accrual, a full double-entry general
ledger, AML transaction monitoring, maker-checker dual-control approvals, card
management, standing instructions, multi-branch support, credit scoring, and a
compliance/CRM layer for complaints and regulatory reporting. Every balance-changing
action is atomic and audit-logged.

The same service layer is exposed three ways: a native Swing desktop client for
tellers, a REST/JSON API, and a React web console that consumes that API — proving
the business logic isn't tangled up with any one UI.

## Architecture

```mermaid
flowchart TB
    subgraph Clients
        Swing["Swing Desktop App<br/>(teller counter, admin console)"]
        React["React Web Console<br/>(Vite + TypeScript)"]
    end

    subgraph Backend["Java 17 Application"]
        API["REST API<br/>(com.sun.net.httpserver, hand-rolled JSON)"]
        Service["Service Layer<br/>Banking / GL / AML / Loans / Interest /<br/>Approvals / Compliance / Payroll ..."]
        DAO["DAO Layer<br/>plain JDBC"]
    end

    DB[("MySQL 8<br/>60+ tables")]

    Swing -->|direct calls| Service
    React -->|HTTP/JSON| API
    API --> Service
    Service --> DAO
    DAO --> DB

    subgraph DevOps["Build, Test & Deploy"]
        Tests["JUnit 5 + Mockito + H2<br/>unit & integration tests"]
        CI["GitHub Actions CI<br/>mvn verify on every push"]
        Docker["Docker Compose<br/>mysql + api services"]
    end

    Backend -.verified by.-> Tests
    Tests -.runs in.-> CI
    Backend -.packaged by.-> Docker
```

## Tech stack

| Layer | Technology |
|---|---|
| Desktop client | Java 17, Swing, custom theming/i18n (5 locales) |
| Web client | React 19, TypeScript, Vite |
| Backend | Java 17, JDBC, `com.sun.net.httpserver` (zero-dependency REST) |
| Database | MySQL 8.0 |
| Testing | JUnit 5, Mockito, H2 (in-memory integration tests) |
| CI/CD | GitHub Actions (`mvn verify` on every push/PR) |
| Containerization | Docker (multi-stage build), Docker Compose |
| Build | Maven |

No Spring, no Hibernate, no Jackson — the service/DAO/API layers are built directly
on the JDK and JDBC. That's a deliberate choice to keep the request path (HTTP →
JSON → service → JDBC → MySQL) fully inspectable end to end.

## Feature list

**Core banking** — account opening with KYC workflow, deposit/withdraw/transfer, all
wrapped in atomic JDBC transactions with an audit-trail row written alongside every
balance change.

**Cash operations** — cash drawer paid-in/paid-out/no-sale/till-count logging, cheque
deposit → clearing/bounce queue, receipt and statement printing.

**Lending** — loan origination, manager approval, disbursement, and full amortizing
EMI schedule generation and repayment tracking.

**General ledger** — a real double-entry chart of accounts (assets/liabilities/
equity/income/expense), trial balance, journal, per-account ledger view, balance
sheet, income statement, and a classified cash flow statement (operating/investing/
financing).

**Interest** — idempotent monthly interest accrual per account/period, posting both
to customer balances and to the general ledger (Interest Expense vs. Customer
Deposits Control).

**Compliance & risk** — AML flagging on transactions over a reporting threshold,
maker-checker dual-control approvals for large transactions, credit scoring/
underwriting, complaint/CRM tracking, and regulatory report (CTR/SAR-style) export.

**Operations** — multi-branch support, card issuance/management, standing
instructions (auto-pay), customer notifications, employee records + payroll, and a
simulated external payment network.

**Platform** — role-based login with 2FA (OTP), 5-locale i18n, a REST API surface,
and a React web console covering account lookup, teller deposit/withdraw, the
customer list, and the GL trial balance.

## Project structure

```
src/main/java/com/branchteller/
  config/    DBConnection — reads DB_URL/DB_USER/DB_PASSWORD env vars
  model/     Domain objects (Account, Customer, Transaction, Loan, GlAccount, ...)
  dao/       Plain-JDBC data access objects
  service/   Business logic — BankingService, GlService, AmlService, LoanService,
             InterestService, ApprovalService, ComplianceService, PayrollService, ...
  gui/       Swing panels — one per feature area (30 panels total)
  api/       ApiServer (REST/JSON), Json (hand-rolled reader/writer)
  util/      PasswordUtil, PrintableText, i18n Messages
src/test/java/com/branchteller/
  service/   Unit tests (pure logic — interest math, approval thresholds)
  dao/       Integration tests against an in-memory H2 database
database/    schema.sql + phase-by-phase migration scripts
frontend/    React + TypeScript web console (Vite)
docker/      Compose-ready init SQL (numbered for correct load order)
.github/     CI workflow
```

117 Java source files, 30 Swing panels, 25 service classes, 60+ database tables.

## Setup

### Prerequisites

- JDK 17+
- MySQL 8.x
- Maven 3.8+
- Node 18+ (only needed for the `frontend/` web console)

### Database

```bash
mysql -u root -p < database/schema.sql
# then apply the phase migration scripts in database/ in order, or use
# docker/initdb/ which already has them numbered correctly
```

Demo users ship with placeholder password hashes. Generate real ones:

```bash
mvn compile
java -cp target/classes com.branchteller.util.PasswordUtil <your-password>
```

and update the `users` table with the printed salt/hash, or set `DB_URL` /
`DB_USER` / `DB_PASSWORD` env vars to point at an already-seeded database.

### Run the desktop app

```bash
mvn compile exec:java -Dexec.mainClass=com.branchteller.Main
# or after `mvn package`:
java -jar target/branch-teller-pos.jar
```

### Run the REST API

```bash
java -cp target/classes:$(find ~/.m2 -name 'mysql-connector-j-*.jar') com.branchteller.api.ApiServer
# listens on http://localhost:8082 (override with API_PORT)
```

### Run the web console

```bash
cd frontend
npm install
cp .env.example .env   # point at your running API if not on localhost:8082
npm run dev
```

### Run everything with Docker Compose

```bash
docker compose up
```

Brings up MySQL (seeded with schema + demo credentials `admin`/`admin123`,
`teller1`/`teller123`) and the REST API on port 8082.

### Run the test suite

```bash
mvn verify
```

144 tests across two engines, both run by that single command (and in CI on every
push via `.github/workflows/ci.yml`):

- **JUnit 5** (130 tests) — pure-logic unit tests (interest math, approval thresholds,
  password hashing, role permissions), H2-backed integration tests (general ledger
  posting/trial-balance correctness, full deposit/withdraw/transfer/AML/approval/
  interest-accrual flows against an in-memory database), and real HTTP integration
  tests against a running `ApiServer` instance.
- **Cucumber** (14 scenarios) — the same account-lifecycle and API flows expressed as
  Gherkin `.feature` files under `src/test/resources/features/`, readable by
  non-programmers, run through the JUnit Platform Suite engine
  (`cucumber/RunCucumberTest.java`).

Every test that needs a database runs against H2 in-memory (see
`src/test/java/com/branchteller/support/TestDatabase.java`) — no MySQL server needed
to run the suite. `DBConnection` itself is untouched; Maven Surefire points its
`DB_URL`/`DB_USER`/`DB_PASSWORD` environment variables at H2 for the test JVM only
(see the `<environmentVariables>` block in `pom.xml`).

**Reports:**

```bash
mvn verify                                          # runs everything, writes raw results
mvn io.qameta.allure:allure-maven:2.15.2:report      # target/site/allure-maven-plugin/index.html
```

Cucumber's own HTML/JSON reports are written directly to `target/cucumber-report/`
on every `mvn verify` run, no extra command needed. Allure's results (from both the
JUnit5 and Cucumber test runs, via the `allure-junit5` and `allure-cucumber7-jvm`
adapters) land in `target/allure-results` and get turned into a browsable report by
the command above.

**Frontend tests** (React components, via Vitest + Testing Library):

```bash
cd frontend
npm install
npm test               # 16 tests, headless (jsdom)
npm run test:coverage  # same, with a coverage report
```

### Test category coverage

A useful way to plan test coverage for a mobile app doesn't map one-to-one onto a
desktop app + REST API + web console with no mobile client. Here's how each category
translates onto this stack, and what's deliberately out of scope:

| Category | How it's covered here |
|---|---|
| Smoke / regression | Every test class; `mvn verify` is the smoke+regression gate in CI |
| App lifecycle | Login/2FA, KYC onboarding → account open → transact flows (`EndToEndFlowTest`, `account_lifecycle.feature`) |
| Navigation | REST API routing: unknown routes → 404, wrong method → 405 (`ApiServerIntegrationTest`) |
| Permission | Role/approval-limit matrix (`UserPermissionTest`, `ApprovalServiceTest`), KYC-gated account opening |
| Negative | Invalid amounts, insufficient funds, unknown accounts, rejected KYC (throughout) |
| Performance | Out of scope for this pass — see "Known simplifications" |
| Security-focused | Password hashing/salting/OTP (`PasswordUtilTest`), CORS headers, AML thresholds |
| Accessibility basics | Frontend form controls verified reachable via `getByLabelText`/`getByPlaceholderText` (`AccountsPage.test.tsx`, `CustomersPage.test.tsx`) |
| Data-driven | `@ParameterizedTest`/`@CsvSource` boundary sweeps (interest math, approval limits, AML thresholds, password complexity) |
| E2E | `EndToEndFlowTest` + `account_lifecycle.feature` (onboarding → KYC → open account → deposit/withdraw/transfer → AML flag → approval → interest accrual) |
| API-integrated | `ApiServerIntegrationTest` + `api.feature` drive the real `ApiServer` over HTTP |
| Install / upgrade | `DockerInitScriptsTest` verifies the Docker init-script contract (numbered, no gaps, non-empty) |
| Device behavior, cross-device/OS, interrupt tests (call/SMS) | Not applicable — no mobile client exists in this project. The closest analogues (env-var-driven config for Docker/CI, rollback-on-failure leaving state consistent) are covered under Data-driven and E2E above |

## Demo flow

1. `docker compose up`, or run the desktop app against a locally seeded database.
2. Log in as `teller1` / `teller123` (or `admin` / `admin123` for manager/admin
   features — 2FA prompts for a one-time code shown in a dialog for demo purposes).
3. Look up an account, deposit/withdraw, and watch the General Ledger trial balance
   stay in balance.
4. As admin: run interest accrual for a period, review AML flags, approve a
   maker-checker request, or pull the balance sheet / income statement / cash flow
   statement from Financial Reports.
5. Point the React web console (`npm run dev` in `frontend/`) at the same API and
   repeat the account lookup / deposit / trial-balance flow from a browser.

## Screenshots

_Add screenshots of the Swing teller counter, General Ledger tab, and the React web
console here before sharing this README externally._

## Known simplifications

This is a demonstration/portfolio project, not a production banking system. A few
things are deliberately simplified rather than production-grade: AML logic is a flat
transaction-amount threshold (not real CTR/SAR rule engines), regulatory export is a
generic CSV rather than a government filing format, payroll withholding is a single
flat rate rather than itemized tax brackets, and the React web console covers a
representative subset of the API (account lookup/deposit/withdraw, customers, GL
trial balance) rather than full feature parity with the Swing app.
