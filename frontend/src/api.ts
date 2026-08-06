/**
 * Typed client for the Branch Teller REST API (see
 * src/main/java/com/branchteller/api/ApiServer.java). Zero external HTTP
 * dependencies -- just the browser's fetch(), mirroring the API server's own
 * zero-dependency philosophy.
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8082";

export interface HealthStatus {
  status: string;
  db: string;
  dbError?: string;
}

export interface Customer {
  id: number;
  fullName: string;
  phone: string;
  email: string;
  kycStatus: string;
}

export interface Account {
  accountNumber: string;
  customerName: string;
  accountType: string;
  balance: string;
  status: string;
}

export interface Transaction {
  id: number;
  type: string;
  amount: string;
  balanceAfter: string;
  note: string;
}

export interface GlAccount {
  code: string;
  name: string;
  accountClass: string;
  normalBalance: string;
  debitTotal: string;
  creditTotal: string;
  netBalance: string;
}

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
    this.name = "ApiError";
  }
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });

  // The API returns { "error": "..." } bodies on non-2xx responses.
  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = await res.json();
      if (body && typeof body.error === "string") message = body.error;
    } catch {
      // response body wasn't JSON -- fall back to statusText
    }
    throw new ApiError(res.status, message);
  }

  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export interface TransactionRequest {
  accountNumber: string;
  amount: number;
  tellerId: number;
  note?: string;
}

export const api = {
  health: () => request<HealthStatus>("/api/health"),

  customers: () => request<Customer[]>("/api/customers"),

  lookupAccount: (accountNumber: string) =>
    request<Account>(`/api/accounts/${encodeURIComponent(accountNumber)}`),

  trialBalance: () => request<GlAccount[]>("/api/gl/trial-balance"),

  deposit: (req: TransactionRequest) =>
    request<Transaction>("/api/transactions/deposit", {
      method: "POST",
      body: JSON.stringify(req),
    }),

  withdraw: (req: TransactionRequest) =>
    request<Transaction>("/api/transactions/withdraw", {
      method: "POST",
      body: JSON.stringify(req),
    }),
};
