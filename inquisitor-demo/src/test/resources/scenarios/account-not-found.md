# Fetch a non-existent account

Call GET /accounts/99999.
Verify the HTTP status is 404 and the response body is a valid RFC 9457 problem detail with type and title fields.
