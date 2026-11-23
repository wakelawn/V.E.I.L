# Database connections (Phase 9)

VEIL talks to exactly one database at a time: the **target** database the user
asks it to analyse. Everything about that link — validating input, testing
credentials, opening a pool, switching connections — lives under
`com.veil.connections`.

## Two datasources

| Datasource | Purpose | Configured by |
| --- | --- | --- |
| `metadataDataSource` (primary, H2) | VEIL's own data: users, projects, saved queries | `VEIL_METADATA_URL` |
| `targetDataSource` | The database being analysed: EXPLAIN, benchmarks, differential tests | `DB_HOST`/`DB_PORT`/`DB_NAME`/… |

`DataSourceConfig` creates both. `JdbcTemplate` (used by benchmarks,
`PostgreSQLExecutionPlanProvider`, `AiDifferentialTester`) binds to
`targetDataSource`, never to VEIL's metadata store.

## Saved connections (`/api/connections`)

Endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/` | List saved connections (password never included) |
| `POST` | `/` | Create + test + activate one connection |
| `POST` | `/discover` | List databases on a server |
| `GET` | `/active` | Which connection is in use |
| `POST` | `/{id}/activate` | Switch to a saved connection (password per call) |
| `POST` | `/deactivate` | Fall back to the default datasource |
| `GET` | `/{id}` | Get one saved connection |
| `POST` | `/{id}/test` | Re-test (password required each call) |
| `DELETE` | `/{id}` | Delete; deactivates first if it was active |

Password handling:

- `ConnectionView` has **no password field** — it cannot leak by accident.
- Passwords are never written to disk (`ConnectionStore.StoredConnection`
  excludes them).
- After creation, activation or re-test, the registry entry's password is
  cleared (`registry.clearPassword`). The live pool keeps its own copy in
  memory only.
- Re-testing requires the password in the request body each time.

Limits (`ConnectionSettings`, all overridable via environment):

```
veil.connections.max-saved=100      # per-account saved-connection cap
veil.target.pool-size=4              # Hikari pool size
veil.target.connect-timeout-seconds=5
veil.target.socket-timeout-seconds=30
veil.target.ssl-mode=prefer
veil.target.read-only=true           # EXPLAIN/benchmarks are SELECT-only
```

Input validation (`ConnectionTarget`): host, port, database name and username
are validated before any URL is built, so no user input can inject JDBC URL
parameters (e.g. `?sslmode=disable` or `;options=...`). Hosts must match
`[a-zA-Z0-9.-]+`, database names `[a-zA-Z0-9._-]+`.

## Active connection (`ActiveConnection`)

Holds one Hikari pool at a time. Activating a new connection closes the old
pool first; `deactivate()` and `@PreDestroy shutdown()` close it too. The pool
is read-only, sized from `veil.target.pool-size`, and uses explicit
`connectTimeout`/`socketTimeout` so a dead host cannot hang a request.

`ConnectionTemplates.current()` returns the active pool's `JdbcTemplate`, or
the default one when nothing is active — so `/api/database`, `/api/explain` and
`/api/analyze` automatically use whichever connection is live.

## Rate limiting

`/api/connections/*` is the "connections" tier: 30 requests per minute per
client IP (`RateLimitConfig`), enforced by `RateLimitFilter` inside the security
chain.

## Tests

- `ConnectionTargetTest` — validation rules and URL construction (8 tests).
- `ActiveConnectionTest` — activate/deactivate/replace pool semantics, invalid
  details rejected (4 tests).
- `ConnectionRegistryTest` — `clearPassword` plus existing CRUD (6 tests).
- `DatabaseBackedIntegrationTest.connectionLifecycleCreateTestDelete` — live
  create → re-test → list → delete → 404, runs only when `DB_PASSWORD` is set.

## Render notes

- `JWT_SECRET` is `generateValue: true` in `render.yaml`, so it is regenerated
  on every deploy. Existing bearer tokens are invalidated — acceptable for the
  single-account deployment; re-login after redeploy.
- `VEIL_AUTH_EMAIL` / `VEIL_AUTH_PASSWORD` must be set in the Render dashboard
  (they are `sync: false`, i.e. not auto-generated).
- `VEIL_METADATA_URL` defaults to `jdbc:h2:file:/tmp/veil/metadata` — survives
  restarts on the same instance, not redeploys. Attach a Render disk and point
  it there to keep history permanently.
