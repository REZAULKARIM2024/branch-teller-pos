Feature: REST API
  The React web console and any other external client talks to Branch Teller only
  through this HTTP/JSON API, so its routing, CORS, and status codes are contract.

  Scenario: Health check reports the service is up
    When I GET "/api/health"
    Then the API response status is 200
    And the API response body contains "status"

  Scenario: Customer list is reachable and returns 200
    When I GET "/api/customers"
    Then the API response status is 200

  Scenario: General ledger trial balance is reachable and returns 200
    When I GET "/api/gl/trial-balance"
    Then the API response status is 200

  Scenario: Looking up an unknown account returns 404
    When I GET "/api/accounts/DOES-NOT-EXIST-XYZ"
    Then the API response status is 404

  Scenario: Depositing to a real account succeeds
    Given a verified customer account funded with "100.00" for API testing
    When I deposit "25.00" into that account via the API
    Then the API response status is 201

  Scenario: An unregistered route returns 404
    When I GET "/api/totally-not-a-real-route"
    Then the API response status is 404

  Scenario: OPTIONS preflight on deposit returns CORS headers
    When I send an OPTIONS request to "/api/transactions/deposit"
    Then the API response status is 204
    And the API response has an Access-Control-Allow-Origin header
