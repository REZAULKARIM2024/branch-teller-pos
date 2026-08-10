package com.branchteller.service;

import com.branchteller.config.DBConnection;
import com.branchteller.dao.EmployeeDAO;
import com.branchteller.dao.PayrollDAO;
import com.branchteller.dao.TimeClockDAO;
import com.branchteller.model.Employee;
import com.branchteller.model.PayrollRun;
import com.branchteller.model.TimeClockEntry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Employee roster, time clock, and payroll -- adapted from the same shape as the
 * NY Coffee Co. POS project's PayrollService, simplified to a single flat withholding
 * rate rather than the full federal/FICA/state/city bracket stack.
 */
public class PayrollService {

    /** Flat withholding rate stand-in for the POS project's itemized tax brackets. */
    public static final BigDecimal FLAT_TAX_RATE = BigDecimal.valueOf(0.20);

    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final TimeClockDAO timeClockDAO = new TimeClockDAO();
    private final PayrollDAO payrollDAO = new PayrollDAO();
    private final AuditService auditService = new AuditService();
    private final GlService glService = new GlService();

    /**
     * QA finding (fixed): this had zero input validation -- a blank name, or a null/zero/negative
     * hourly rate, was silently accepted and written straight to the employees table. A negative
     * rate is the dangerous one: {@link GlService#post} only skips exactly-zero amounts (its guard
     * is {@code amount.signum() == 0}), NOT negative ones. So running payroll for an employee hired
     * at, say, "-18.50" (an easy typo for "18.50") would compute a negative net pay and post a
     * "balanced" GL entry with the debit/credit roles economically reversed -- Salaries Expense
     * (5100) would go DOWN and Cash (1000) would go UP, as if paying an employee somehow generated
     * cash for the bank -- without ever tripping the double-entry invariant, since debits still
     * equal credits (just with reversed sign). There's no REST API for hiring, so the GUI's own
     * hire-form was the only guard, and that only checked for blank name/position, never the rate's
     * sign. Validating here closes it off at the one real entry point.
     */
    public Employee hire(String fullName, String position, BigDecimal hourlyRate) throws SQLException {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Employee full name is required");
        }
        if (position == null || position.isBlank()) {
            throw new IllegalArgumentException("Employee position is required");
        }
        if (hourlyRate == null || hourlyRate.signum() <= 0) {
            throw new IllegalArgumentException("Hourly rate must be a positive amount");
        }
        try (Connection conn = DBConnection.getConnection()) {
            Employee e = new Employee();
            e.setFullName(fullName);
            e.setPosition(position);
            e.setHourlyRate(hourlyRate);
            e.setHireDate(LocalDate.now());
            int id = employeeDAO.insert(conn, e);
            e.setId(id);
            e.setActive(true);
            return e;
        }
    }

    public List<Employee> activeEmployees() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return employeeDAO.findAllActive(conn);
        }
    }

    public void clockIn(int employeeId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Optional<TimeClockEntry> open = timeClockDAO.findOpenEntry(conn, employeeId);
            if (open.isPresent()) {
                throw new IllegalStateException("Employee " + employeeId + " is already clocked in");
            }
            timeClockDAO.clockIn(conn, employeeId);
        }
    }

    public void clockOut(int employeeId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            TimeClockEntry open = timeClockDAO.findOpenEntry(conn, employeeId)
                    .orElseThrow(() -> new IllegalStateException("Employee " + employeeId + " is not clocked in"));
            timeClockDAO.clockOut(conn, open.getId());
        }
    }

    public List<TimeClockEntry> recentPunches(int employeeId, int limit) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return timeClockDAO.recentForEmployee(conn, employeeId, limit);
        }
    }

    /** Computes gross/tax/net from clocked hours in [periodStart, periodEnd] and records the run.
     *
     * <p>Wrapped in an explicit transaction (setAutoCommit(false)/commit()/rollback()) around the
     * payroll-run insert, audit log, and the two-leg GL post -- matching every other caller of
     * glService.post() (BankingService, LoanService, PaymentsService, InterestService). Without
     * this, each statement on the connection auto-commits independently, so if the GL post failed
     * partway through (e.g. after the debit leg but before the credit leg), the payroll run row
     * would already be permanently saved while the ledger was left out of balance with no way to
     * roll back -- silently violating the double-entry invariant this whole feature depends on. */
    public PayrollRun runPayroll(int employeeId, LocalDate periodStart, LocalDate periodEnd, int actorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Employee employee = employeeDAO.findById(conn, employeeId)
                        .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

                double hours = timeClockDAO.totalHours(conn, employeeId, periodStart, periodEnd);
                BigDecimal hoursWorked = BigDecimal.valueOf(hours).setScale(2, RoundingMode.HALF_UP);
                BigDecimal grossPay = hoursWorked.multiply(employee.getHourlyRate()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal taxWithheld = grossPay.multiply(FLAT_TAX_RATE).setScale(2, RoundingMode.HALF_UP);
                BigDecimal netPay = grossPay.subtract(taxWithheld);

                PayrollRun run = new PayrollRun();
                run.setEmployeeId(employeeId);
                run.setPeriodStart(periodStart);
                run.setPeriodEnd(periodEnd);
                run.setHoursWorked(hoursWorked);
                run.setGrossPay(grossPay);
                run.setTaxWithheld(taxWithheld);
                run.setNetPay(netPay);
                int id = payrollDAO.insert(conn, run);
                run.setId(id);
                run.setEmployeeName(employee.getFullName());

                auditService.log(conn, actorId, "PAYROLL_RUN", "employee", employeeId,
                        null, "net pay " + netPay + " for " + periodStart + " to " + periodEnd);
                glService.post(conn, "5100", "1000", netPay, null,
                        "Payroll run for " + employee.getFullName() + " (" + periodStart + " to " + periodEnd + ")");

                conn.commit();
                return run;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<PayrollRun> payrollHistory(int employeeId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return payrollDAO.findByEmployee(conn, employeeId);
        }
    }
}
