import { useEffect, useState } from "react";
import { api, type HealthStatus, ApiError } from "../api";

/** Small status strip showing whether the API + its DB connection are up.
 *  Polls every 15s so a demo left open catches a backend restart. */
export default function HealthBanner() {
  const [health, setHealth] = useState<HealthStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function check() {
      try {
        const result = await api.health();
        if (!cancelled) {
          setHealth(result);
          setError(null);
        }
      } catch (e) {
        if (!cancelled) {
          setHealth(null);
          setError(e instanceof ApiError ? e.message : "API unreachable");
        }
      }
    }

    check();
    const id = setInterval(check, 15000);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, []);

  if (error) {
    return (
      <div className="health-banner health-banner--down" data-testid="health-banner">
        API unreachable ({error}) -- is ApiServer running on the configured
        VITE_API_BASE_URL?
      </div>
    );
  }

  if (!health) {
    return <div className="health-banner health-banner--pending">Checking API status...</div>;
  }

  const ok = health.status === "ok" && health.db === "connected";
  return (
    <div className={`health-banner ${ok ? "health-banner--up" : "health-banner--down"}`} data-testid="health-banner">
      API: {health.status} &middot; Database: {health.db}
      {health.dbError ? ` (${health.dbError})` : ""}
    </div>
  );
}
