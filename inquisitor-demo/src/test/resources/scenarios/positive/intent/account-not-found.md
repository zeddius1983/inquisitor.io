# Fetch a non-existent account

This scenario states only the business intent — it names no endpoints, paths, or
request bodies. The harness has been given the application's OpenAPI description, so
the model must read it to work out which call to make.

Look up an account whose id has never been created. The request should be rejected as
"not found": a `404` response whose body is a well-formed RFC 9457 problem detail
(carrying at least a `title` and a `detail`) served as `application/problem+json`,
rather than a generic error.
