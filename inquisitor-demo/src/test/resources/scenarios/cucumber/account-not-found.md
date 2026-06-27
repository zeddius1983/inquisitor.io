# Fetch a non-existent account

Feature: looking up an account that does not exist returns a well-formed RFC 9457
problem detail rather than a generic error.

Scenario: fetch an account id that was never created

- **Given** no account with id 99999 has been created
- **When** that account is requested
- **Then** the response status is `404 Not Found`
- **And** the `Content-Type` is `application/problem+json`
- **And** the body is a valid RFC 9457 problem detail carrying at least a `title`
  and a `detail`
