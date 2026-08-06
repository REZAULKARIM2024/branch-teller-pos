Feature: Account lifecycle
  As a bank branch, money can only move for KYC-verified customers with open accounts,
  withdrawals can never exceed the available balance, large transactions must be flagged
  for AML review, and withdrawals over a teller's limit must go through manager approval.

  Scenario: A pending customer cannot have an account opened
    Given a newly registered customer
    Then opening an account for them is rejected because KYC is not verified

  Scenario: A verified customer can open an account and transact
    Given a KYC-verified customer with an open SAVINGS account
    When the teller deposits "250.00" into the account
    Then the account balance is "250.00"
    When the teller withdraws "100.00" from the account
    Then the account balance is "150.00"

  Scenario Outline: Withdrawals larger than the available balance are rejected
    Given a KYC-verified customer with an open SAVINGS account funded with "<opening>"
    When the teller attempts to withdraw "<amount>" from the account
    Then the withdrawal is rejected as insufficient funds
    And the account balance is still "<opening>"

    Examples:
      | opening | amount |
      | 100.00  | 500.00 |
      | 0.00    | 0.01   |
      | 50.00   | 50.01  |

  Scenario: A large deposit creates a suspicious activity flag for review
    Given a KYC-verified customer with an open SAVINGS account
    When the teller deposits "10000.00" into the account
    Then the account has an unreviewed suspicious activity flag

  Scenario: A teller-limit-exceeding withdrawal is queued for manager approval
    Given a KYC-verified customer with an open SAVINGS account funded with "50000.00"
    When the teller requests a withdrawal of "20000.00" requiring approval
    Then the request is pending approval
    And the account balance is still "50000.00"
    When a manager approves the request
    Then the account balance is "30000.00"
