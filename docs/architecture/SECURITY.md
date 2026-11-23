# Security hardening (Phase Ten)

VEIL's security model: every API endpoint except `/api/health`, static assets,
`/api/auth/*` and `/api/ai/status` requires a valid bearer token. All incoming
SQL is validated before it reaches the parser or the database. Errors are
sanitized so nothing leaks to the client.

## Authentication

- `JwtService` issues HS256 JWTs (issuer `veil`, subject = e-mail, `jti` for
  replay resistance). `JWT_SECRET` must be ≥ 32 chars; without it a random
  per-process key is used and tokens do not survive restarts.
- `JwtAuthenticationFilter` verifies the bearer token and sets the Spring
  security context; stateless, no sessions, no CSRF (token-based API).
- `AuthController` single-account login: `VEIL_AUTH_EMAIL` /
  `VEIL_AUTH_PASSWORD` / `VEIL_AUTH_PASSWORD_HASH`. With neither set, a random
  password is logged once.
- `RateLimitFilter` + `RateLimitConfig`: per-client-IP token buckets — auth 10,
  connections 30, AI 30, default 120 per minute. Only the last `X-Forwarded-For`
  hop is trusted.

## SQL input validation (`SqlValidator`)

Every SQL string accepted by an endpoint is validated before parsing or
execution:

- Must be non-blank
- Must be ≤ 64 KB (DoS guard for the lexer/parser)
- Must start with `SELECT` or `WITH` — `INSERT`, `UPDATE`, `DELETE`, `DROP`,
  `CREATE`, `ALTER`, `TRUNCATE`, etc. are rejected
- Must not contain multiple statements separated by `;`

Applied to:

| Endpoint | Validation |
| --- | --- |
| `POST /api/analyze` | `SqlValidator.validateSelectOnly` before parsing |
| `POST /api/explain` | `SqlValidator.validateSelectOnly` before EXPLAIN |
| `POST /api/explain/analyze` | `SqlValidator.validateSelectOnly` before EXPLAIN ANALYZE |
| `POST /api/benchmark` | Both `originalSql` and `optimizedSql` validated |

## Benchmark configuration bounds

Client-supplied benchmark config is clamped so a payload cannot exhaust the
connection pool or CPU:

| Setting | Default | Max |
| --- | --- | --- |
| warmupRuns | 3 | 10 |
| measurementRuns | 10 | 50 |
| statementTimeoutMs | 30000 | 300000 |

The active connection pool is read-only (`transaction_read_only = on`), so even
a valid SELECT cannot modify data.

## Error handling (`ApiExceptionHandler`)

All errors return `{"error": "..."}` with an appropriate status code. Database
and connection errors are sanitized — no schema details, no hostnames, no
credentials. A global `@ExceptionHandler(Exception.class)` returns HTTP 500
with `"Internal server error"` for anything unexpected, preventing stack traces
or implementation internals from leaking.

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{"error":"Only SELECT or WITH queries are supported"}
```

```http
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{"error":"Internal server error"}
```

## Headers (SecurityConfig)

- CSP: `default-src 'self'` with script/style `unsafe-inline`, connect to
  NVIDIA and a few AI providers only, `frame-ancestors 'none'`
- HSTS: 1 year, subdomains
- `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`
- CORS: configurable origins, credentials disabled
- No session cookies (stateless)

## Request size limits

```
spring.mvc.max-request-body-size=256KB
spring.servlet.multipart.max-file-size=1MB
spring.servlet.multipart.max-request-size=1MB
```

## Tests

- `SqlValidatorTest` — 10 unit tests: accepts SELECT/WITH, rejects empty,
  non-SELECT, multiple statements, injection attempts, oversized SQL, null.
- `SecurityHardeningIntegrationTest` — 10 MockMvc tests: analyze/rejects
  non-SELECT/multiple/oversized, explain/analyze rejects non-SELECT, benchmark
  rejects non-SELECT original/optimized SQL and missing SQL, benchmark validates
  SQL before running.
