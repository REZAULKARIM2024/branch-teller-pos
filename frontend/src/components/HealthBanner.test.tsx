import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import HealthBanner from "./HealthBanner";
import { api } from "../api";

vi.mock("../api", () => ({
  api: { health: vi.fn() },
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
}));

describe("HealthBanner", () => {
  it("shows a pending state before the health check resolves", () => {
    vi.mocked(api.health).mockReturnValue(new Promise(() => {})); // never resolves
    render(<HealthBanner />);
    expect(screen.getByText(/Checking API status/i)).toBeInTheDocument();
  });

  it("shows an up state when the API and DB are both healthy", async () => {
    vi.mocked(api.health).mockResolvedValue({ status: "ok", db: "connected" });
    render(<HealthBanner />);
    await waitFor(() => expect(screen.getByText(/API: ok/i)).toBeInTheDocument());
    expect(screen.getByText(/Database: connected/i)).toBeInTheDocument();
  });

  it("shows a down state when the API call rejects", async () => {
    vi.mocked(api.health).mockRejectedValue(new Error("network down"));
    render(<HealthBanner />);
    await waitFor(() => expect(screen.getByText(/API unreachable/i)).toBeInTheDocument());
  });

  it("shows a down state when the API responds but the database is unreachable", async () => {
    vi.mocked(api.health).mockResolvedValue({ status: "ok", db: "unreachable", dbError: "connection refused" });
    render(<HealthBanner />);
    await waitFor(() => expect(screen.getByText(/Database: unreachable/i)).toBeInTheDocument());
    expect(screen.getByText(/connection refused/i)).toBeInTheDocument();
  });
});
