import { useState } from "react";
import "./App.css";
import HealthBanner from "./components/HealthBanner";
import CustomersPage from "./components/CustomersPage";
import AccountsPage from "./components/AccountsPage";
import TrialBalancePage from "./components/TrialBalancePage";

type Tab = "accounts" | "customers" | "ledger";

const TABS: { id: Tab; label: string }[] = [
  { id: "accounts", label: "Teller Operations" },
  { id: "customers", label: "Customers" },
  { id: "ledger", label: "General Ledger" },
];

/** Thin React demo frontend over the Branch Teller REST API. Not a full
 *  reimplementation of the Swing app -- it exercises the same service layer
 *  (health, customers, accounts, deposit/withdraw, GL trial balance) to show
 *  the backend works equally well behind a modern web client. */
export default function App() {
  const [tab, setTab] = useState<Tab>("accounts");

  return (
    <div className="app-shell">
      <header className="app-header">
        <h1>Branch Teller</h1>
        <span className="app-header-sub">Web Console (React + REST API)</span>
      </header>

      <HealthBanner />

      <nav className="tab-nav">
        {TABS.map((t) => (
          <button
            key={t.id}
            data-testid={`tab-${t.id}`}
            className={`tab-nav-btn ${tab === t.id ? "tab-nav-btn--active" : ""}`}
            onClick={() => setTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </nav>

      <main className="app-main">
        {tab === "accounts" && <AccountsPage />}
        {tab === "customers" && <CustomersPage />}
        {tab === "ledger" && <TrialBalancePage />}
      </main>
    </div>
  );
}
