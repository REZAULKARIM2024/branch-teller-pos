import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import AccountsPage from "./AccountsPage";
import { api, ApiError } from "../api";

vi.mock("../api", () => ({
  api: { lookupAccount: vi.fn(), deposit: vi.fn(), withdraw: vi.fn() },
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
}));

const ACCOUNT = {
  accountNumber: "TST-123",
  customerName: "Alice Anderson",
  accountType: "SAVINGS",
  balance: "500.00",
  status: "ACTIVE",
};

describe("AccountsPage", () => {
  it("looks up an account and displays its balance", async () => {
    vi.mocked(api.lookupAccount).mockResolvedValue(ACCOUNT);
    render(<AccountsPage />);

    await userEvent.type(screen.getByPlaceholderText(/Account number/i), "TST-123");
    await userEvent.click(screen.getByRole("button", { name: /Look up/i }));

    await waitFor(() => expect(screen.getByText("Alice Anderson")).toBeInTheDocument());
    expect(screen.getByText("$500.00")).toBeInTheDocument();
    expect(api.lookupAccount).toHaveBeenCalledWith("TST-123");
  });

  it("shows an error message when the account is not found", async () => {
    vi.mocked(api.lookupAccount).mockRejectedValue(new ApiError(404, "Account not found: NOPE"));
    render(<AccountsPage />);

    await userEvent.type(screen.getByPlaceholderText(/Account number/i), "NOPE");
    await userEvent.click(screen.getByRole("button", { name: /Look up/i }));

    await waitFor(() => expect(screen.getByText(/Account not found/i)).toBeInTheDocument());
  });

  it("the deposit/withdraw form fields all have accessible labels", async () => {
    // getByLabelText only succeeds when an <input> is correctly associated with a
    // <label> (implicit nesting counts) -- this is the basic accessibility contract
    // every form control on this page relies on for screen readers.
    vi.mocked(api.lookupAccount).mockResolvedValue(ACCOUNT);
    render(<AccountsPage />);

    await userEvent.type(screen.getByPlaceholderText(/Account number/i), "TST-123");
    await userEvent.click(screen.getByRole("button", { name: /Look up/i }));
    await waitFor(() => expect(screen.getByText("Alice Anderson")).toBeInTheDocument());

    expect(screen.getByLabelText("Amount")).toBeInTheDocument();
    expect(screen.getByLabelText("Teller ID")).toBeInTheDocument();
    expect(screen.getByLabelText("Note")).toBeInTheDocument();
  });

  it("submitting a deposit updates the displayed balance", async () => {
    vi.mocked(api.lookupAccount).mockResolvedValue(ACCOUNT);
    vi.mocked(api.deposit).mockResolvedValue({
      id: 1,
      type: "DEPOSIT",
      amount: "50.00",
      balanceAfter: "550.00",
      note: "",
    });
    render(<AccountsPage />);

    await userEvent.type(screen.getByPlaceholderText(/Account number/i), "TST-123");
    await userEvent.click(screen.getByRole("button", { name: /Look up/i }));
    await waitFor(() => expect(screen.getByText("Alice Anderson")).toBeInTheDocument());

    await userEvent.type(screen.getByLabelText("Amount"), "50");
    await userEvent.click(screen.getByRole("button", { name: /^Deposit$/i }));

    await waitFor(() => expect(screen.getByText("$550.00")).toBeInTheDocument());
    expect(screen.getByText(/DEPOSIT of \$50.00 posted/i)).toBeInTheDocument();
  });

  it("the look-up button is disabled until an account number is entered", () => {
    render(<AccountsPage />);
    expect(screen.getByRole("button", { name: /Look up/i })).toBeDisabled();
  });
});
