# Fetch a non-existent account

Requesting an account that does not exist must return a well-formed RFC 9457
problem detail rather than a generic error.

**Intent:** Fetch an account id that has not been created.

```
GET /accounts/99999
```

**Expected response**
- Status: `404 Not Found`
- Content-Type `application/problem+json`
- Body is a valid RFC 9457 problem detail (includes at least the `title` and
  `detail` fields)
