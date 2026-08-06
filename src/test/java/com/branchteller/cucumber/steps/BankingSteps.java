package com.branchteller.cucumber.steps;

import com.branchteller.model.PendingApproval;
import com.branchteller.model.SuspiciousActivityFlag;
import com.branchteller.model.User;
import com.branchteller.service.AmlService;
import com.branchteller.service.ApprovalService;
import com.branchteller.service.BankingService;
import com.branchteller.service.CustomerService;
import com.branchteller.service.InsufficientFundsException;
import com.branchteller.support.TestDatabase;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Step definitions for account_lifecycle.feature -- drives the real service layer
 *  against the shared H2 test database, exactly like EndToEndFlowTest but expressed
 *  as Gherkin scenarios readable by non-programmers. */
public class BankingSteps {

    private final CustomerService customerService = new CustomerService();
    private final BankingService bankingService = new BankingService();
    private final AmlService amlService = new AmlService();
    private final ApprovalService approvalService = new ApprovalService();

    private int customerId;
    private int branchId;
    private int tellerId;
    private int accountId;
    private Exception lastException;
    private PendingApproval lastApproval;

    @Before
    public void ensureSchema() throws Exception {
        TestDatabase.ensureSchema();
    }

    @Given("a newly registered customer")
    public void aNewlyRegisteredCustomer() throws Exception {
        var customer = customerService.register("Cucumber Test", "555-" + TestDatabase.nextSeq(),
                "cucumber@example.test", "1 Feature St");
        customerId = customer.getId();
        branchId = TestDatabase.insertBranch("Cucumber Branch " + TestDatabase.nextSeq());
    }

    @Then("opening an account for them is rejected because KYC is not verified")
    public void openingAnAccountIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> customerService.openAccount(customerId, branchId, "SAVINGS", BigDecimal.ZERO, 1));
    }

    @Given("a KYC-verified customer with an open SAVINGS account")
    public void aVerifiedCustomerWithAnAccount() throws Exception {
        setUpVerifiedFixture(BigDecimal.ZERO);
    }

    @Given("a KYC-verified customer with an open SAVINGS account funded with {string}")
    public void aVerifiedCustomerWithAFundedAccount(String opening) throws Exception {
        setUpVerifiedFixture(new BigDecimal(opening));
    }

    private void setUpVerifiedFixture(BigDecimal openingBalance) throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(openingBalance);
        branchId = fx.branchId;
        tellerId = fx.tellerId;
        customerId = fx.customerId;
        accountId = fx.accountId;
    }

    @When("the teller deposits {string} into the account")
    public void theTellerDeposits(String amount) throws Exception {
        bankingService.deposit(accountId, new BigDecimal(amount), tellerId, "cucumber deposit");
    }

    @When("the teller withdraws {string} from the account")
    public void theTellerWithdraws(String amount) throws Exception {
        bankingService.withdraw(accountId, new BigDecimal(amount), tellerId, "cucumber withdraw");
    }

    @When("the teller attempts to withdraw {string} from the account")
    public void theTellerAttemptsToWithdraw(String amount) {
        lastException = null;
        try {
            bankingService.withdraw(accountId, new BigDecimal(amount), tellerId, "cucumber attempted withdraw");
        } catch (Exception e) {
            lastException = e;
        }
    }

    @Then("the withdrawal is rejected as insufficient funds")
    public void theWithdrawalIsRejected() {
        assertNotNull(lastException, "Expected the withdrawal to throw");
        assertInstanceOf(InsufficientFundsException.class, lastException);
    }

    @Then("the account balance is {string}")
    public void theAccountBalanceIs(String expected) throws Exception {
        assertEquals(0, new BigDecimal(expected).compareTo(TestDatabase.balanceOf(accountId)));
    }

    @Then("the account balance is still {string}")
    public void theAccountBalanceIsStill(String expected) throws Exception {
        theAccountBalanceIs(expected);
    }

    @Then("the account has an unreviewed suspicious activity flag")
    public void theAccountHasAnUnreviewedFlag() throws Exception {
        boolean found = amlService.unreviewed().stream()
                .anyMatch((SuspiciousActivityFlag f) -> f.getAccountId() == accountId);
        assertTrue(found, "Expected an unreviewed AML flag for account " + accountId);
    }

    @When("the teller requests a withdrawal of {string} requiring approval")
    public void theTellerRequestsAWithdrawalRequiringApproval(String amount) throws Exception {
        lastApproval = approvalService.submitWithdrawal(accountId, new BigDecimal(amount), tellerId, "cucumber approval request");
    }

    @Then("the request is pending approval")
    public void theRequestIsPendingApproval() {
        assertEquals("PENDING", lastApproval.getStatus());
    }

    @When("a manager approves the request")
    public void aManagerApprovesTheRequest() throws Exception {
        int managerId = TestDatabase.insertUser("cucumber-manager", "MANAGER");
        User manager = new User(managerId, "cucumber-manager", "Cucumber Manager", "MANAGER", branchId);
        approvalService.approve(lastApproval.getId(), manager, "approved via cucumber");
    }
}
