# Query History and Workspaces

Phase Eight adds server-side persistence for saved queries, query versions and
workspace-style projects. It deliberately does **not** use the database VEIL
analyses.

## Where the data lives

| Concern | Store |
| --- | --- |
| Users, projects, saved queries, tags, versions | Embedded H2 "metadata" database |
| The database being analysed | Configured target database (`DB_HOST`, `DB_PORT`, …) |

`DataSourceConfig` defines two datasources:

- `metadataDataSource` (primary) — Hibernate/JPA bind here.
- `targetDataSource` — used by `JdbcTemplate`, EXPLAIN, benchmarks and
  differential tests. Qualify this bean explicitly when injecting it.

Default metadata URL:

```properties
veil.metadata.url=jdbc:h2:mem:veil-metadata;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
```

`VEIL_METADATA_URL` overrides it. On Render, `render.yaml` points it at
`jdbc:h2:file:/tmp/veil/metadata`, which survives restarts on the same instance
but not redeploys. Attach a Render disk and point the variable at it to keep
history permanently.

Why H2 and not the analysed database: VEIL may only have read access, and
`ddl-auto=update` must never create or alter tables inside a user's schema.

## Ownership

Every history and project endpoint derives the owner from the verified bearer
token (`JwtAuthenticationFilter` puts the JWT subject — the e-mail — in the
security context). A `userId` in the request body or query string is ignored.

`QueryHistoryService.resolveUser(email)` maps the principal to a local `users`
row, creating it on first use. This keeps the single-account deployment free of
a separate registration flow while still giving each token an isolated history.

## API

All routes require `Authorization: Bearer <token>`.

### History (`/api/history`)

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/` | Save a query (`name`, `sqlText`, optional `description`, `tags`, `projectId`) |
| `GET` | `/` | Page of top-level saved queries (newest first by default) |
| `GET` | `/{id}` | One saved query |
| `PUT` | `/{id}` | Partial update |
| `DELETE` | `/{id}` | Delete |
| `GET` | `/search?q=` | Case-insensitive name search |
| `GET` | `/tag/{tag}` | Filter by tag |
| `GET` | `/tags` | Distinct tags |
| `GET` | `/project/{projectId}` | Queries in a project |
| `POST` | `/{id}/favorite` | Toggle favourite |
| `POST` | `/{id}/version` | Save a new SQL revision (`{"sql": "..."}`) |
| `GET` | `/{id}/versions` | Revisions of a saved query |
| `GET` | `/stats` | Counts for the current account |

### Projects (`/api/projects`)

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/` | Create a project |
| `GET` | `/` | Paged projects |
| `GET` | `/list` | Unarchived projects, default first |
| `GET` | `/{id}` | One project |
| `PUT` | `/{id}` | Partial update |
| `DELETE` | `/{id}` | Delete; saved queries are unassigned, not deleted |
| `GET` | `/default` | The default project |
| `POST` | `/{id}/default` | Move the default flag |

The first project created for an account automatically becomes the default.

## Data model

`QueryHistory`

- `version` starts at 1; a new revision copies name, description, database
  metadata and tags, sets `parent`, and increments `version`.
- `tags` is an element collection (`query_history_tags`).
- `project` owns the `project_id` column; `projectId` is a read-only mirror used
  by queries, so code that clears a project must set both.

`Project`

- `color`, `icon`, `archived`, `defaultProject`; exactly one default per user is
  enforced by the service.

## Tests

- `QueryHistoryServiceTest` — CRUD, search, tags, versions, default project,
  project deletion, owner resolution.
- `HistoryApiIntegrationTest` — bearer-token ownership, cross-account isolation,
  401 without a token.
