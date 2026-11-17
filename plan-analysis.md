# Execution Plan Analysis in VEIL

How VEIL reads a PostgreSQL `EXPLAIN` plan, and how it decides which part
of the plan deserves your attention. Beginner-to-intermediate level.

---

## 1. What is an execution plan?

When PostgreSQL receives a `SELECT`, it does not run it blind. The planner
first builds a **plan**: a tree of steps (scan this table, join those rows,
sort the result). `EXPLAIN (FORMAT JSON)` returns that tree as JSON.

VEIL sends your query to PostgreSQL with `EXPLAIN` and turns the JSON tree
into its own shape: `PlanSummary` (see `com.veil.explain`).

```text
EXPLAIN JSON node          PlanSummary field
-------------------------  ---------------------------
"Node Type"                nodeType        e.g. Seq Scan, Index Scan, Sort
"Relation Name"            relation        the table being read
"Index Name"               index           the index used (if any)
"Startup Cost"             startupCost     cost before the first row
"Total Cost"               totalCost       cost to produce ALL rows
"Plan Rows"                estimatedRows   planner's row estimate
"Filter"                   filter          row filter applied while scanning
"Sort Key"                 sortKey         what a Sort node sorts by
"Plans"                    children        the child nodes (a tree)
```

---

## 2. Startup cost vs total cost

- **Startup cost** = work done before the first row can be returned.
  A `Sort` node has a high startup cost: it must consume *all* input rows
  before it can emit even one.
- **Total cost** = work to produce the complete result.

For most analysis, **total cost** is the number to compare between plans.
Cost units are not milliseconds — they are the planner's abstract units
(page reads, CPU comparisons). 8.17 is cheap; 100000 is a serious query.

---

## 3. Self cost: how much work a node does itself

This is the idea behind VEIL's bottleneck analysis.

A node's **total cost always includes its children** — a parent cannot
finish before its inputs are ready. So a parent often looks "expensive"
only because it is carrying its children's cost.

```text
Sort        total = 100.00   <- looks biggest, but...
└─ Seq Scan total =  90.00   <- this node did 90 of the 100
```

**Self cost = totalCost − (sum of children's totalCost)**

In the example: Sort self cost = 100 − 90 = **10**, Seq Scan self cost = **90**.
The scan is the real workhorse — replacing the Sort would achieve almost
nothing; making the scan cheaper (e.g. an index) helps the entire plan.

VEIL implements this in `PlanAnalyzer.analyze()`:

1. Flatten the tree (depth-first).
2. Compute each node's self cost.
3. The node with the highest self cost is the **bottleneck**.
4. Report it only when it does **≥ 30%** of the plan's total cost —
   if no node dominates, there is no single place to optimize and
   saying otherwise would be misleading.

---

## 4. Seq Scan + Filter: the classic index candidate

Two fields matter together:

- A **Seq Scan** reads every row of the table.
- A **Filter** discards rows that do not match a condition.

That combination is the textbook case for an index: if the table is large
and only a few rows pass the filter, an index on the filtered column lets
PostgreSQL jump straight to the matching rows.

Important distinction (this trips people up):

```text
Filter      - rows are read, then thrown away if they don't match
Index Cond  - the index itself locates the matching rows (no waste)
```

So `WHERE id > 5` on an indexed `id` shows up as `Index Cond` (good,
nothing to fix), while `WHERE description > 'a'` on an unindexed column
shows up as a Seq Scan with a `Filter` (the case VEIL flags).

---

## 5. Where the insights appear

`PlanAnalyzer` adds its findings as plain strings to the relevant
`PlanSummary.recommendations`. `ExplainService` then collects every node's
recommendations into the root, so the `/api/explain` response always has
one flat list:

```json
{ "plan": { "recommendations": [
  "Sequential scan detected on table 'projects'. ...",
  "Plan bottleneck: Seq Scan on projects does 12.7 of the 31.4 total plan cost (~40%). ..."
]}}
```

The dashboard merges these into the existing **Recommendations** card
(badge: "Plan Insight") alongside the parser-based recommendations, and
shows the one-line `planSummary` as the AI summary text.

Try it live:

```bash
curl -s -X POST http://127.0.0.1:8080/api/explain \
  -H "Content-Type: application/json" \
  -d '{"sql":"SELECT q.id FROM queries q JOIN projects p ON q.project_id = p.id;"}'
```

Or just click **Analyze Query** in the dashboard — the JOIN query in the
editor produces a 4-node plan with a real bottleneck.

---

## 6. Phase 6: the cost breakdown

Phase 5 answered "which single node dominates?".
Phase 6 answers "where did the plan's cost go overall?".

`CostAnalyzer` (`com.veil.explain`) produces a `costBreakdown` list on the
root `PlanSummary`. Every node with self cost > 0 becomes one entry:

```json
{ "nodeType": "Seq Scan", "relation": "queries",
  "category": "scan", "selfCost": 14.2, "sharePercent": 45.22 }
```

- **selfCost** — the node's own work (children excluded, see section 3)
- **sharePercent** — selfCost / plan total × 100
- **category** — coarse grouping used for the summary line:
  anything ending in `Scan` → scan; `Sort` → sort; the join nodes
  (`Hash Join`, `Nested Loop`, `Merge Join`) → join;
  `HashAggregate`/`Aggregate` → aggregate; everything else → other

Entries are sorted most-expensive-first, and all shares together
always sum to the plan's total cost.

The root's `planSummary` also gains one line, e.g.:

```text
Cost breakdown: scan 26.9 (86%), join 4.5 (14%).
```

Read it like this: if `scan` eats most of the cost, indexes (Phase 8)
are where the wins are; if `sort` or `join` dominates, the shape of the
query (fewer columns, earlier filtering, smaller inputs) matters more.

Where to see it:
- Dashboard → Execution Plan card → **JSON View** (`costBreakdown`)
- Dashboard → AI summary text (the appended `Cost breakdown:` line)
- API: `POST /api/explain` → `plan.costBreakdown`

---

## 7. What VEIL deliberately does NOT claim

- Cost units are the planner's estimates, **not** measured runtime.
  (Run `EXPLAIN ANALYZE` manually when you want real timings — VEIL uses
  plain `EXPLAIN` so it never executes your query.)
- Recommendations are heuristic. "An index may help" is honest;
  "create this index" would not be — only you know the write workload.
- Row estimates come from PostgreSQL statistics and can be wrong;
  a stale-stats table can make the planner pick a bad plan.

These limits are part of the design: deterministic findings from real
plan facts, never invented numbers.
