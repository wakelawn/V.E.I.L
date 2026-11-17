# VEIL — SQL Query Analyzer & Optimization Engine

> Combined documentation for the VEIL project, including the core engine notes and PostgreSQL database notes.

---

## Table of Contents

1. [What is VEIL?](#1-what-is-veil)
2. [Current Technology Stack](#2-current-technology-stack)
3. [Current Project Progress](#3-current-project-progress)
4. [Lexer](#4-lexer)
5. [Token](#5-token)
6. [TokenType](#6-tokentype)
7. [Lexer Package](#7-lexer-package)
8. [Parser](#8-parser)
9. [Parser Package](#9-parser-package)
10. [AST](#10-ast)
11. [AST Package](#11-ast-package)
12. [Complete Current Flow](#12-complete-current-flow)
13. [Spring Boot Role](#13-spring-boot-role)
14. [Current Project Structure](#14-current-project-structure)
15. [Future Packages](#15-future-packages)
16. [Future Semantic Analyzer](#16-future-semantic-analyzer)
17. [Future Query Planner](#17-future-query-planner)
18. [Future Cost Analysis](#18-future-cost-analysis)
19. [Future Optimizer](#19-future-optimizer)
20. [Future Index Analysis](#20-future-index-analysis)
21. [PostgreSQL EXPLAIN](#21-postgresql-explain)
22. [Important Terms](#22-important-terms)
23. [What VEIL Is Not Yet](#23-what-veil-is-not-yet)
24. [Important Architectural Principle](#24-important-architectural-principle)
25. [Current Goal](#25-current-goal)
26. [Development Roadmap](#26-development-roadmap)


---

## 1. What is VEIL?

VEIL is a **SQL Query Analyzer and Optimization Engine**.

Its purpose is to take a SQL query, understand its structure, analyze it, and eventually identify possible ways to improve its execution.

The core idea is:

```text
SQL Query
    ↓
Lexer
    ↓
Tokens
    ↓
Parser
    ↓
AST
    ↓
Semantic Analysis
    ↓
Query Planning
    ↓
Cost Analysis
    ↓
Optimization
    ↓
Index Analysis
    ↓
PostgreSQL EXPLAIN
```

---

## 2. Current Technology Stack

| Component              | Technology                  | Version              |
| ---------------------- | --------------------------- | -------------------- |
| Programming Language   | Java                        | 25                   |
| Framework              | Spring Boot                 | 4.1.2                |
| Build Tool             | Maven (Wrapper)             | mvnw                 |
| IDE                    | Eclipse / Spring Tools      | —                    |
| Database               | PostgreSQL                  | —                    |
| JDBC Driver            | org.postgresql:postgresql   | 42.7.14              |
| Backend API            | Spring MVC / REST           | —                    |
| Frontend               | HTML + CSS + JavaScript     | —                    |

---

## 3. Current Project Progress

### Completed

| Area                     | Status |
| ------------------------ | ------ |
| Spring Boot Project      | ✅     |
| Maven                    | ✅     |
| Lexer                    | ✅     |
| Token System             | ✅     |
| Parser                   | ✅     |
| AST                      | ✅     |
| PostgreSQL Database      | ✅     |
| Database Tables          | ✅     |
| Initial Indexes          | ✅     |
| Database Abstraction Layer (DatabaseAdapter, DatabaseCapabilities, DatabaseType) | ✅ |
| Execution Plan Provider Abstraction | ✅ |
| EXPLAIN Integration (ExplainService) | ✅ |
| Plan Metrics & Rule-based Recommendations | ✅ |
| Deeper Plan Analysis (PlanAnalyzer — bottleneck by self-cost, Seq Scan + filter insight) | ✅ |
| Cost Analysis (CostAnalyzer — per-node cost shares, category breakdown) | ✅ |
| Optimization Engine (OptimizationRule engine, 4 AST rules, structured findings with severity) | ✅ |
| REST API Core (`/api/analyze`, `/api/explain`, `/api/database`, `/api/database/health`) | ✅ |
| CORS Configuration       | ✅     |
| Dashboard Frontend (HTML/CSS/JS) | ✅ |
| Unit Tests (Lexer, Parser, Optimization Engine, Plan/Cost Analysis — 41 passing) | ✅ |

### In Progress / Planned

| Area                     | Status |
| ------------------------ | ------ |
| Better SQL Grammar (JOIN, ORDER BY, GROUP BY, subqueries, more operators) | 🟡 (JOIN/ORDER BY/operators/aliases done; GROUP BY, subqueries pending) |
| Semantic Analyzer        | ⏳     |
| Query Planner            | ⏳     |
| Deeper Plan-Tree Analysis | ⏳    |
| Cost Analysis            | ⏳     |
| Optimizer                | ⏳     |
| Index Recommendation     | ⏳     |
| Multi-connection Support (ConnectionService, Registry, per-connection DataSource) | ⏳ |
| React Auth UI (veil-auth) | ⏳    |
| AI Explanation Layer     | ⏳     |
| MySQL / SQLite Adapters  | ⏳     |
| Integration Tests (explain endpoint, controllers) | ⏳ |

### Current Important Milestone

```text
SQL
 ↓
Lexer
 ↓
Tokens
 ↓
Parser
 ↓
AST
 ↓
Analysis
 ↓
PostgreSQL EXPLAIN
 ↓
PlanSummary
 ↓
Metrics + Recommendations
```

### Current Status Snapshot (last updated 17 November 2026)

Verified against the repository (not just planned):

```text
DONE (exists in code)
- com.veil.lexer / parser / ast           → SQL engine core
- com.veil.core.database                  → DatabaseAdapter, DatabaseCapabilities,
                                            DatabaseType, ExecutionPlanProvider
- com.veil.database.postgres              → PostgreSQLAdapter,
                                            PostgreSQLExecutionPlanProvider
- com.veil.explain                        → ExplainService, PlanSummary,
                                            PlanAnalyzer, ExplainController
- com.veil.service                        → QueryService, QueryController,
                                            AnalyzeResponse (with structured
                                            findings + recommendations)
- com.veil.analysis                       → OptimizationEngine,
                                            OptimizationRule, OptimizationFinding,
                                            rules: SelectStar, FilterIndex,
                                            NoFilter, Sort
- com.veil.config                         → CorsConfig, JacksonConfig,
                                            ApiExceptionHandler (400s with
                                            {"error": ...} messages)
- com.veil.lexer.LexException             → lexer errors are typed, not bare
                                            RuntimeException
- src/test/java                           → 41 JUnit tests passing
                                            (10 lexer + 13 parser + 7
                                            optimization engine + 5 plan
                                            analysis + 5 cost analysis
                                            + 1 context)
- static/                                 → dashboard + login wired to real
                                            backend: /api/analyze,
                                            /api/explain, /api/database,
                                            /api/database/health (script.js)
- WHERE grammar                           → condition tree: ComparisonCondition
                                            (WhereCondition leaf) + AND/OR
                                            (LogicalCondition), comparison
                                            operators, qualified columns (u.id)
- Full SELECT grammar                     → JOIN ... ON with table aliases,
                                            ORDER BY [ASC|DESC], LIMIT n

IN PROGRESS
- deeper plan-tree analysis (Phase 5)

SECURITY
- Spring Boot 4.1.2 (patch bump from 4.1.1)
  fixes 4 Tomcat CVEs: CVE-2026-65905, CVE-2026-65637,
  CVE-2026-65182, CVE-2026-68525 (Tomcat 11.0.24 → 11.0.27)
  also pulls Spring Framework 7.0.10 and Jackson 3.1.7

NOT STARTED (does not exist yet)
- connection/dto + ConnectionService/Registry (multi-connection)
- veil-auth React app
- semantic analyzer, cost module, index recommender
- backend authentication (JWT was previously added, then removed)
- subqueries, GROUP BY/HAVING, window functions in parser

OPEN THREADS
- CORS allow-list uses http://127.0.0.1:5500; dashboard is normally
  served same-origin from :8080 so CORS is only relevant for Live Server
- JacksonConfig ObjectMapper bean exists; harmless, decide later
- Two Jackson stacks on classpath: Boot 4 web layer uses Jackson 3
  (tools.jackson); ExplainService/JacksonConfig use Jackson 2
  (com.fasterxml, pinned 2.22.4)
- DB credentials are env-var based (DB_HOST, DB_PORT, DB_NAME,
  DB_USERNAME, DB_PASSWORD with local defaults); local DB is named
  v.e.i.l and DB_PASSWORD has no default (must be provided)
- login is still client-side demo auth (demo@veil.app / veil123)
```

---

## 4. Lexer

### What is a Lexer?

A lexer, also called a lexical analyzer, reads the raw SQL string and breaks it into smaller meaningful units called tokens.

Example:

```sql
SELECT name FROM users;
```

The lexer converts it into something like:

```text
SELECT
IDENTIFIER(name)
FROM
IDENTIFIER(users)
SEMICOLON(;)
EOF
```

The lexer does **NOT** understand the complete SQL structure.

It only identifies individual pieces.

---

## 5. Token

A token represents one recognized element of SQL.

For example:

```sql
SELECT
```

can be represented as:

```text
TokenType = SELECT
Value = SELECT
```

Another example:

```sql
name
```

becomes:

```text
TokenType = IDENTIFIER
Value = name
```

---

## 6. TokenType

`TokenType` is an enum that defines the types of tokens VEIL currently recognizes.

Current examples:

```text
SELECT
FROM
WHERE
AND
OR
JOIN
ON
AS
ORDER
BY
ASC
DESC
LIMIT
IDENTIFIER
NUMBER
STRING
EQUALS
NOT_EQUALS
LESS
LESS_OR_EQUAL
GREATER
GREATER_OR_EQUAL
COMMA
STAR
SEMICOLON
DOT
EOF
```

---

## 7. Lexer Package

Current structure:

```text
com.veil.lexer
├── TokenType.java
├── Token.java
└── Lexer.java
```

### TokenType.java

Defines possible token types.

### Token.java

Stores:

```text
Token Type
Token Value
```

### Lexer.java

Reads SQL character by character and creates:

```java
List<Token>
```

---

## 8. Parser

### What is a Parser?

The parser receives the tokens generated by the lexer and checks whether they follow the grammar supported by VEIL.

Example:

```text
SELECT
IDENTIFIER(name)
FROM
IDENTIFIER(users)
```

The parser understands this as:

```sql
SELECT name FROM users;
```

The parser also builds the AST.

Supported grammar (as of 17 November 2026):

```sql
SELECT <column|*>[, ...]
FROM <table> [alias]
[JOIN <table> [alias] ON <condition>]...
[WHERE <condition>]        -- condition tree: comparisons joined by AND/OR
[ORDER BY <column> [ASC|DESC][, ...]]
[LIMIT <n>][;]
```

WHERE precedence: AND binds tighter than OR (same as SQL).
Qualified columns (u.id) are supported in SELECT, WHERE, ON and values.

---

## 9. Parser Package

Current structure:

```text
com.veil.parser
├── Parser.java
└── ParseException.java
```

### Parser.java

Responsible for:

```text
Reading tokens
Checking grammar
Building AST objects
```

### ParseException.java

Used when the SQL does not match the supported grammar.

Example:

```text
Expected SELECT but found IDENTIFIER
```

---

## 10. AST

AST stands for:

```text
Abstract Syntax Tree
```

The AST represents the logical structure of the SQL query as objects.

Instead of treating the query as a simple string, VEIL represents its components structurally.

Example:

```sql
SELECT name FROM users;
```

AST:

```text
SelectStatement
├── Columns
│   └── name
│
└── Table
    └── users
```

---

## 11. AST Package

Current structure:

```text
com.veil.ast
├── Statement.java
├── SelectStatement.java
└── TableReference.java
```

### Statement.java

Base interface for SQL statements.

### SelectStatement.java

Represents a `SELECT` statement.

Currently stores:

```text
columns
table
whereCondition
```

### TableReference.java

Represents the table used by the SQL query.

Example:

```text
users
```

---

## 12. Complete Current Flow

For:

```sql
SELECT name FROM users;
```

VEIL performs:

```text
1. SQL Input

SELECT name FROM users;

        ↓

2. Lexer

SELECT
IDENTIFIER(name)
FROM
IDENTIFIER(users)
SEMICOLON
EOF

        ↓

3. Parser

Reads the token sequence.

        ↓

4. AST

SelectStatement
├── columns = [name]
└── table = users
```

---

## 13. Spring Boot Role

Spring Boot is the application and API layer around the VEIL engine.

Spring Boot does **NOT** replace the lexer, parser, or AST.

The architecture is:

```text
Browser
   ↓
Spring Boot Controller
   ↓
Query Service
   ↓
Lexer
   ↓
Parser
   ↓
AST
```

This allows VEIL to later expose its analysis functionality through REST APIs.

---

## 14. Current Project Structure

```text
VEIL
│
├── src/main/java
│   └── com.veil
│       │
│       ├── Application.java
│       │
│       ├── lexer
│       │   ├── TokenType.java
│       │   ├── Token.java
│       │   └── Lexer.java
│       │
│       ├── parser
│       │   ├── Parser.java
│       │   └── ParseException.java
│       │
│       ├── analysis
│       │   ├── OptimizationEngine.java
│       │   ├── OptimizationRule.java
│       │   ├── OptimizationFinding.java
│       │   ├── SelectStarRule.java
│       │   ├── FilterIndexRule.java
│       │   ├── NoFilterRule.java
│       │   └── SortRule.java
│       │
│       ├── ast
│       │   ├── Statement.java
│       │   ├── SelectStatement.java
│       │   ├── TableReference.java
│       │   ├── Condition.java
│       │   ├── WhereCondition.java
│       │   ├── LogicalCondition.java
│       │   ├── JoinClause.java
│       │   └── OrderByItem.java
│       │
│       ├── core
│       │   └── database
│       │       ├── DatabaseType.java
│       │       ├── DatabaseCapabilities.java
│       │       ├── DatabaseAdapter.java
│       │       └── ExecutionPlanProvider.java
│       │
│       ├── database
│       │   ├── DatabaseController.java
│       │   └── postgres
│       │       ├── PostgreSQLAdapter.java
│       │       └── PostgreSQLExecutionPlanProvider.java
│       │
│       ├── explain
│       │   ├── ExplainService.java
│       │   ├── ExplainController.java
│       │   ├── PlanAnalyzer.java
│       │   ├── CostAnalyzer.java
│       │   ├── CostEntry.java
│       │   └── PlanSummary.java
│       │
│       ├── service
│       │   ├── QueryService.java
│       │   ├── QueryController.java
│       │   └── AnalyzeResponse.java
│       │
│       └── config
│           ├── CorsConfig.java
│           └── JacksonConfig.java
│
├── src/main/resources
│   ├── application.properties
│   └── static
│       └── index.html
│
├── src/test/java
│   └── com.veil
│       ├── ApplicationTests.java
│       ├── lexer
│       │   └── LexerTest.java
│       ├── parser
│       │   └── ParserTest.java
│       ├── analysis
│       │   └── OptimizationEngineTest.java
│       └── explain
│           ├── PlanAnalyzerTest.java
│           └── CostAnalyzerTest.java
│
├── pom.xml
└── target
```

---

## 15. Future Packages

Later VEIL will contain:

```text
analyzer
planner
optimizer
cost
index
```

Possible future structure:

```text
com.veil
├── lexer
├── parser
├── ast
├── analyzer
├── planner
├── optimizer
├── cost
├── index
├── service
└── controller
```

---

## 16. Future Semantic Analyzer

The semantic analyzer will check whether a syntactically valid query actually makes sense.

Examples:

```text
Does the table exist?
Does the column exist?
Is the column ambiguous?
Does the JOIN condition make sense?
Are data types compatible?
```

So:

```text
Parser
↓
"Is this SQL grammatically valid?"

Semantic Analyzer
↓
"Does this SQL make sense?"
```

---

## 17. Future Query Planner

The query planner will represent possible ways of executing a query.

Example:

```sql
SELECT *
FROM users
WHERE id = 10;
```

Possible strategies:

```text
Sequential Scan
Index Scan
Bitmap Scan
```

VEIL can eventually compare these strategies.

---

## 18. Future Cost Analysis

Cost analysis will estimate the resources required by a query plan.

Important factors may include:

```text
Estimated Rows
Startup Cost
Total Cost
Number of Operations
Join Strategy
Scan Strategy
```

PostgreSQL already provides useful plan-cost information through `EXPLAIN`.

---

## 19. Future Optimizer

The optimizer will identify possible improvements.

Examples:

```text
Avoid SELECT *
Use an index
Improve JOIN condition
Reduce unnecessary rows
Push filters earlier
Improve ordering
```

The eventual flow:

```text
SQL
 ↓
AST
 ↓
Analysis
 ↓
Possible Problems
 ↓
Optimization Rules
 ↓
Recommendations
```

---

## 20. Future Index Analysis

VEIL will eventually inspect query patterns such as:

```sql
SELECT *
FROM users
WHERE email = 'example@gmail.com';
```

and determine that the query may benefit from:

```sql
CREATE INDEX idx_users_email
ON users(email);
```

This is different from the indexes used by VEIL's own database.

See the separate database README for this distinction.

---

## 21. PostgreSQL EXPLAIN

Later VEIL will use PostgreSQL `EXPLAIN` to inspect execution plans.

Example:

```sql
EXPLAIN
SELECT *
FROM users
WHERE id = 10;
```

A PostgreSQL execution plan might contain operations such as:

```text
Seq Scan
Index Scan
Bitmap Heap Scan
Nested Loop
Hash Join
Sort
Aggregate
```

VEIL can eventually interpret these operations and present them in a simpler format.

---

## 22. Important Terms

| Term | Meaning |
| -------------------------------- | ------------------------------------------------------------ |
| AST | Abstract Syntax Tree: structured representation of parsed SQL |
| Adapter | Database-specific implementation for connection and metadata operations (e.g., `PostgreSQLAdapter`) |
| DatabaseCapabilities | Flags describing what an adapter supports: SQL, EXPLAIN, indexes, transactions, schema inspection |
| ExecutionPlanProvider | Database-specific component that requests a native execution plan |
| PlanSummary | VEIL's normalized / summarized representation of an execution plan |
| Plan Metrics | Aggregated counts over the plan tree: plan nodes, scan nodes, index scans, sequential scans |
| Self cost | A plan node's own work: its total cost minus its children's total costs (parents include children) |
| Plan bottleneck | The node with the highest self cost (reported when it is ≥30% of the plan's total cost) |
| Cost breakdown | Per-node list of self costs with share % and a work category (scan/join/sort/aggregate/other), produced by CostAnalyzer |
| Optimization rule | One AST-level check (e.g. SelectStarRule) that appends findings to a shared list; the engine runs all rules in order |
| Finding | Structured advice entry: ruleId, severity (high/medium/low), title, message, optional suggestion SQL |
| DataSource | A configured source of database connections; each saved connection may need its own DataSource (planned) |
| JdbcTemplate | Spring JDBC helper for executing SQL through a DataSource |
| CORS | Browser security mechanism controlling cross-origin requests |
| Connection Registry | (Planned) named registry holding per-connection DataSources and adapters |
| Deterministic analysis | Rule-based findings computed from AST or plan facts; never invented |
| AI explanation layer | (Planned) model-generated commentary that explains deterministic findings but must not fabricate costs, rows, indexes, or schema facts |

---

## 23. What VEIL Is Not Yet

VEIL is currently **NOT** a complete SQL database engine.

It is an analysis and optimization project.

Currently supported parser functionality is limited.

More SQL grammar can be added later:

```text
JOIN
GROUP BY
HAVING
ORDER BY
Subqueries
Functions
Expressions
INSERT
UPDATE
DELETE
```

---

## 24. Important Architectural Principle

Keep the SQL engine separate from Spring Boot.

Recommended architecture:

```text
             VEIL
               │
      ┌────────┴────────┐
      │                 │
SQL ENGINE          APPLICATION
      │                 │
Lexer              Spring Boot
Parser             REST API
AST
Analyzer
Optimizer
      │
      └────── PostgreSQL
```

This means the core VEIL engine can work independently of the web layer.

---

## 25. Current Goal

The immediate goal is to make a working prototype that can:

```text
Enter SQL
   ↓
Tokenize SQL
   ↓
Parse SQL
   ↓
Generate AST
   ↓
Analyze query
   ↓
Display results
```

After this:

```text
PostgreSQL
   ↓
EXPLAIN
   ↓
Execution Plan
   ↓
Cost
   ↓
Optimization
   ↓
Index Recommendations
```

---

## 26. Development Roadmap

| Phase    | Focus                                | Status          |
| -------- | ------------------------------------ | --------------- |
| Phase 1  | Lexer, Parser, AST                   | ✅ Done          |
| Phase 2  | Better SQL Grammar, Semantic Analysis| ✅ Grammar done (JOIN/ON, aliases, AND/OR tree, comparison operators, ORDER BY, LIMIT); semantic analysis still basic |
| Phase 3  | PostgreSQL Integration               | ✅ Done         |
| Phase 4  | EXPLAIN Integration                  | ✅ Done         |
| Phase 5  | Execution Plan Analysis              | ✅ Done (bottleneck by self-cost, filter/sort-key capture, Seq Scan + filter insights) |
| Phase 6  | Cost Analysis                        | ✅ Done (CostAnalyzer: per-node self-cost shares, category breakdown) |
| Phase 7  | Optimization Engine                  | ✅ Done (com.veil.analysis: OptimizationRule engine, 4 rules, structured findings with severity) |
| Phase 8  | Index Analysis                       | ⏳ Planned      |
| Phase 9  | REST API                             | 🟡 Core endpoints + error handling done; connections API pending |
| Phase 10 | Frontend                             | 🟡 Dashboard wired to backend; React auth UI pending |
| Phase 11 | Testing + Documentation              | 🟡 Unit tests + docs in place; integration tests pending |

---


A simple explanation:

> VEIL is a SQL Query Analyzer and Optimization Engine. It first tokenizes the SQL using a lexer, parses the token stream according to a SQL grammar, and creates an Abstract Syntax Tree. Spring Boot provides the application layer, while PostgreSQL stores project and query information. Future modules will use PostgreSQL execution plans and query-cost information to generate optimization and index recommendations.

---
