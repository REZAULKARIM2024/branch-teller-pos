import { useEffect, useState } from "react";
import { api, type GlAccount, ApiError } from "../api";

export default function TrialBalancePage() {
  const [accounts, setAccounts] = useState<GlAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .trialBalance()
      .then(setAccounts)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load trial balance"))
      .finally(() => setLoading(false));
  }, []);

  const totalDebit = accounts.reduce((sum, a) => sum + parseFloat(a.debitTotal || "0"), 0);
  const totalCredit = accounts.reduce((sum, a) => sum + parseFloat(a.creditTotal || "0"), 0);
  const balanced = Math.abs(totalDebit - totalCredit) < 0.005;

  return (
    <section className="panel">
      <h2>General Ledger -- Trial Balance</h2>
      <p className="panel-sub">GET /api/gl/trial-balance -- same figures as the Swing General Ledger tab</p>

      {loading && <p>Loading trial balance...</p>}
      {error && <p className="error-text">{error}</p>}

      {!loading && !error && (
        <>
          <table className="data-table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Account</th>
                <th>Class</th>
                <th>Normal Balance</th>
                <th className="num">Debit Total</th>
                <th className="num">Credit Total</th>
                <th className="num">Net Balance</th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((a) => (
                <tr key={a.code}>
                  <td>{a.code}</td>
                  <td>{a.name}</td>
                  <td>{a.accountClass}</td>
                  <td>{a.normalBalance}</td>
                  <td className="num">${a.debitTotal}</td>
                  <td className="num">${a.creditTotal}</td>
                  <td className="num">${a.netBalance}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={4}>Totals</td>
                <td className="num">${totalDebit.toFixed(2)}</td>
                <td className="num">${totalCredit.toFixed(2)}</td>
                <td className="num" />
              </tr>
            </tfoot>
          </table>
          <p className={balanced ? "success-text" : "error-text"}>
            {balanced ? "Balanced -- debits equal credits." : "Out of balance -- debits do not equal credits."}
          </p>
        </>
      )}
    </section>
  );
}
