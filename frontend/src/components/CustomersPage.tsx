import { useEffect, useState } from "react";
import { api, type Customer, ApiError } from "../api";

export default function CustomersPage() {
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState("");

  useEffect(() => {
    api
      .customers()
      .then(setCustomers)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load customers"))
      .finally(() => setLoading(false));
  }, []);

  const visible = customers.filter((c) =>
    c.fullName.toLowerCase().includes(filter.toLowerCase()) ||
    c.email.toLowerCase().includes(filter.toLowerCase()) ||
    c.phone.includes(filter),
  );

  return (
    <section className="panel">
      <h2>Customers</h2>
      <p className="panel-sub">GET /api/customers -- {customers.length} on file</p>

      <input
        className="text-input"
        placeholder="Filter by name, email, or phone..."
        value={filter}
        onChange={(e) => setFilter(e.target.value)}
      />

      {loading && <p>Loading customers...</p>}
      {error && <p className="error-text">{error}</p>}

      {!loading && !error && (
        <table className="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Phone</th>
              <th>Email</th>
              <th>KYC Status</th>
            </tr>
          </thead>
          <tbody>
            {visible.slice(0, 200).map((c) => (
              <tr key={c.id}>
                <td>{c.id}</td>
                <td>{c.fullName}</td>
                <td>{c.phone}</td>
                <td>{c.email}</td>
                <td>
                  <span className={`badge badge--${c.kycStatus.toLowerCase()}`}>
                    {c.kycStatus}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {!loading && !error && visible.length > 200 && (
        <p className="panel-sub">Showing first 200 of {visible.length} matches.</p>
      )}
    </section>
  );
}
