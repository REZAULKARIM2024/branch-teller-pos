import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import TrialBalancePage from "./TrialBalancePage";
import { api } from "../api";

vi.mock("../api", () => ({
  api: { trialBalance: vi.fn() },
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
}));

describe("TrialBalancePage", () => {
  it("reports balanced when total debits equal total credits", async () => {
    vi.mocked(api.trialBalance).mockResolvedValue([
      { code: "1000", name: "Cash", accountClass: "ASSET", normalBalance: "DEBIT", debitTotal: "500.00", creditTotal: "0.00", netBalance: "500.00" },
      { code: "1100", name: "Customer Deposits Control", accountClass: "LIABILITY", normalBalance: "CREDIT", debitTotal: "0.00", creditTotal: "500.00", netBalance: "500.00" },
    ]);
    render(<TrialBalancePage />);
    await waitFor(() => expect(screen.getByText(/Balanced/i)).toBeInTheDocument());
    expect(screen.getByText("Cash")).toBeInTheDocument();
  });

  it("reports out of balance when total debits do not equal total credits", async () => {
    vi.mocked(api.trialBalance).mockResolvedValue([
      { code: "1000", name: "Cash", accountClass: "ASSET", normalBalance: "DEBIT", debitTotal: "500.00", creditTotal: "0.00", netBalance: "500.00" },
    ]);
    render(<TrialBalancePage />);
    await waitFor(() => expect(screen.getByText(/Out of balance/i)).toBeInTheDocument());
  });

  it("shows an error message when the API call fails", async () => {
    vi.mocked(api.trialBalance).mockRejectedValue(new Error("boom"));
    render(<TrialBalancePage />);
    await waitFor(() => expect(screen.getByText(/Failed to load trial balance/i)).toBeInTheDocument());
  });
});
