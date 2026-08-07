import { useState } from "react";
import { api, type Account, type Transaction, ApiError } from "../api";

/** Account lookup plus a deposit/withdraw form -- the closest React equivalent
 *  to the Swing app's Teller Operations tab, driven entirely by the REST API. */
export default function AccountsPage() {
  const [accountNumber, setAccountNumber] = useState("");
  const [account, setAccount] = useState<Account | null>(null);
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [lookupLoading, setLookupLoading] = useState(false);

  const [amount, setAmount] = useState("");
  const [tellerId, setTellerId] = useState("1");
  const [note, setNote] = useState("");
  const [lastTxn, setLastTxn] = useState<Transaction | null>(null);
  const [txnError, setTxnError] = useState<string | null>(null);
  const [txnLoading, setTxnLoading] = useState(false);

  async function lookup(e: React.FormEvent) {
    e.preventDefault();
    setLookupLoading(true);
    setLookupError(null);
    setAccount(null);
    setLastTxn(null);
    try {
      const result = await api.lookupAccount(accountNumber.trim());
      setAccount(result);
    } catch (e) {
      setLookupError(e instanceof ApiError ? e.message : "Lookup failed");
    } finally {
      setLookupLoading(false);
    }
  }

  async function submitTxn(kind: "deposit" | "withdraw") {
    if (!account) return;
    setTxnLoading(true);
    setTxnError(null);
    try {
      const req = {
        accountNumber: account.accountNumber,
        amount: Number(amount),
        tellerId: Number(tellerId),
        note,
      };
      const txn = kind === "deposit" ? await api.deposit(req) : await api.withdraw(req);
      setLastTxn(txn);
      setAccount({ ...account, balance: txn.balanceAfter });
      setAmount("");
      setNote("");
    } catch (e) {
      setTxnError(e instanceof ApiError ? e.message : "Transaction failed");
    } finally {
      setTxnLoading(false);
    }
  }

  return (
    <section className="panel">
      <h2>Account Lookup &amp; Teller Operations</h2>
      <p className="panel-sub">
        GET /api/accounts/{"{"}number{"}"} &middot; POST /api/transactions/deposit|withdraw
      </p>

      <form onSubmit={lookup} className="inline-form">
        <input
          className="text-input"
          data-testid="account-number-input"
          placeholder="Account number"
          value={accountNumber}
          onChange={(e) => setAccountNumber(e.target.value)}
        />
        <button className="btn" data-testid="lookup-button" type="submit" disabled={lookupLoading || !accountNumber.trim()}>
          {lookupLoading ? "Looking up..." : "Look up"}
        </button>
      </form>

      {lookupError && <p className="error-text" data-testid="lookup-error">{lookupError}</p>}

      {account && (
        <div className="card">
          <div className="card-row">
            <span className="card-label">Account</span>
            <span>{account.accountNumber}</span>
          </div>
          <div className="card-row">
            <span className="card-label">Customer</span>
            <span>{account.customerName}</span>
          </div>
          <div className="card-row">
            <span className="card-label">Type</span>
            <span>{account.accountType}</span>
          </div>
          <div className="card-row">
            <span className="card-label">Status</span>
            <span className={`badge badge--${account.status.toLowerCase()}`}>{account.status}</span>
          </div>
          <div className="card-row card-row--balance">
            <span className="card-label">Balance</span>
            <span className="balance" data-testid="account-balance">${account.balance}</span>
          </div>

          <hr />

          <div className="txn-form">
            <label>
              Amount
              <input
                className="text-input"
                data-testid="amount-input"
                type="number"
                min="0.01"
                step="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
            </label>
            <label>
              Teller ID
              <input
                className="text-input"
                type="number"
                value={tellerId}
                onChange={(e) => setTellerId(e.target.value)}
              />
            </label>
            <label>
              Note
              <input
                className="text-input"
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="optional"
              />
            </label>
            <div className="txn-buttons">
              <button
                className="btn btn--primary"
                data-testid="deposit-button"
                disabled={txnLoading || !amount}
                onClick={() => submitTxn("deposit")}
              >
                Deposit
              </button>
              <button
                className="btn btn--danger"
                data-testid="withdraw-button"
                disabled={txnLoading || !amount}
                onClick={() => submitTxn("withdraw")}
              >
                Withdraw
              </button>
            </div>
            {txnError && <p className="error-text" data-testid="txn-error">{txnError}</p>}
            {lastTxn && (
              <p className="success-text" data-testid="txn-success-message">
                {lastTxn.type} of ${lastTxn.amount} posted -- new balance ${lastTxn.balanceAfter}
              </p>
            )}
          </div>
        </div>
      )}
    </section>
  );
}
