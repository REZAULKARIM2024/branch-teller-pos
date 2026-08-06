import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import CustomersPage from "./CustomersPage";
import { api } from "../api";

vi.mock("../api", () => ({
  api: { customers: vi.fn() },
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
}));

const CUSTOMERS = [
  { id: 1, fullName: "Alice Anderson", phone: "555-0001", email: "alice@example.test", kycStatus: "VERIFIED" },
  { id: 2, fullName: "Bob Baker", phone: "555-0002", email: "bob@example.test", kycStatus: "PENDING" },
];

describe("CustomersPage", () => {
  it("renders every customer returned by the API", async () => {
    vi.mocked(api.customers).mockResolvedValue(CUSTOMERS);
    render(<CustomersPage />);
    await waitFor(() => expect(screen.getByText("Alice Anderson")).toBeInTheDocument());
    expect(screen.getByText("Bob Baker")).toBeInTheDocument();
  });

  it("shows an error message when the API call fails", async () => {
    vi.mocked(api.customers).mockRejectedValue(new Error("boom"));
    render(<CustomersPage />);
    await waitFor(() => expect(screen.getByText(/Failed to load customers/i)).toBeInTheDocument());
  });

  it("the filter input is reachable via its placeholder text (basic accessible-name check)", async () => {
    vi.mocked(api.customers).mockResolvedValue(CUSTOMERS);
    render(<CustomersPage />);
    await waitFor(() => expect(screen.getByText("Alice Anderson")).toBeInTheDocument());
    // getByPlaceholderText only finds a real, singular <input> -- proves the filter
    // control has a human-readable accessible name rather than being an unlabeled div.
    expect(screen.getByPlaceholderText(/Filter by name, email, or phone/i)).toBeInTheDocument();
  });

  it("filtering narrows the visible rows to matching customers", async () => {
    vi.mocked(api.customers).mockResolvedValue(CUSTOMERS);
    render(<CustomersPage />);
    await waitFor(() => expect(screen.getByText("Alice Anderson")).toBeInTheDocument());

    const filterInput = screen.getByPlaceholderText(/Filter by name, email, or phone/i);
    await userEvent.type(filterInput, "Bob");

    expect(screen.queryByText("Alice Anderson")).not.toBeInTheDocument();
    expect(screen.getByText("Bob Baker")).toBeInTheDocument();
  });
});
