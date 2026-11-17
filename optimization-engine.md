# The Optimization Engine in VEIL

How `/api/analyze` turns a parsed query into concrete, honest advice.
Beginner-to-intermediate level. (Plan analysis lives in
`plan-analysis.md`; this file is about the query text itself.)

---

## 1. The pipeline

`veil-track.md` describes the flow like this:

```text
SQL -> AST -> Analysis -> Possible Problems -> Optimization Rules -> Recommendations
```

Phase 7 turns that last part into a real engine instead of one big
`generateRecommendations()` method glued to `QueryService`.

Everything lives in the `com.veil.analysis` package:

```text
OptimizationRule      the interface every rule implements
OptimizationFinding   one problem + advice (structured, not a bare string)
OptimizationEngine    runs every rule in order, collects the findings
SelectStarRule        rule 1
FilterIndexRule       rule 3
NoFilterRule          rule 2
SortRule              rule 4
```

Why separate classes? A rule is one *check* with one *reason to exist*.
When Phase 8 (Index Analysis) adds real index checking, it becomes another
rule (or an upgrade to `FilterIndexRule`) — the engine and the other rules
do not change.

---

## 2. The finding object

Every rule reports an `OptimizationFinding`:

```text
ruleId         stable machine name, e.g. "SELECT_STAR" (tests match on it)
severity       "high" | "medium" | "low"
title          short headline, shown bold in the dashboard
message        the advice itself
suggestionSql  optional SQL you could run (null = nothing honest to show)
```

`/api/analyze` returns a `findings` list **and** keeps the old
`recommendations` string list (each string is a finding's message) so
older consumers keep working.

---

## 3. The rules (and what they deliberately do NOT say)

| Rule | Fires when | Severity | Honest limit |
| --- | --- | --- | --- |
| `SELECT_STAR` | the column list is `*` | high | doesn't name columns — only the DB knows the table's schema |
| `FilterIndexRule` | WHERE/ON compare on column(s) | medium | says "may help", never emits `CREATE INDEX` — only Phase 8 can check which indexes already exist |
| `NoFilterRule` | no WHERE and no JOIN | low | a full read is sometimes intended; it only says "if you need a subset..." |
| `SortRule` | ORDER BY without LIMIT | low | doesn't claim a sort index would help — Phase 8 territory |

Two design choices worth noticing:

1. **No invented facts.** A rule can only reference things it can see in
   the AST: table names, column names, presence/absence of clauses.
   It cannot know row counts, real indexes, or runtime cost.
2. **Over-reporting is safe, wrong silence is not.** `FilterIndexRule`
   fires even if an index already exists on the column. Today VEIL cannot
   know that — so it prefers a harmless extra hint over missing a real
   candidate. Phase 8's job is to check the database's actual indexes and
   silence the hint where it is already satisfied.

---

## 4. Severity → what the dashboard shows

The dashboard maps each finding onto the existing recommendation card:

```text
severity "high"    warning icon, Medium/High Impact badge
severity "medium"  accent icon, Medium Impact badge
severity "low"     info icon, Low Impact badge
```

If a finding carries a `suggestionSql`, the card shows a **Show SQL**
button (expandable). Today no rule fills it; Phase 8 will use it for
`CREATE INDEX ...` suggestions once real indexes are checked.

Try it live:

```bash
curl -s -X POST http://127.0.0.1:8080/api/analyze \
  -H "Content-Type: application/json" \
  -d '{"sql":"SELECT * FROM users WHERE email = '\''a@b.c'\'' ORDER BY created_at;"}'
```

Expected findings: `SELECT_STAR` (high), `FILTER_INDEX` (medium),
`SORT_WITHOUT_LIMIT` (low).

---

## 5. How to add a rule (the pattern to copy)

```java
public class MyRule implements OptimizationRule {

	@Override
	public void check(SelectStatement select,
			List<OptimizationFinding> findings) {

		if (<problem not present>) {
			return;                       // quiet when nothing is wrong
		}

		findings.add(new OptimizationFinding(
				"MY_RULE", "low", "Title", "Advice...", null));
	}
}
```

…then register it in `OptimizationEngine`'s constructor. That is the whole
extension story — no engine changes, no service changes.
