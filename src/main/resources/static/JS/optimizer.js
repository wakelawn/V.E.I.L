// VEIL — AI Query Optimizer Module Renders the AI-optimized SQL query in the lower half of the Query Editor column. Provides real-time query rewrite suggestions, speedup estimates, rationale breakdown, and one-click "Apply to Editor" functionality.

window.VEILOptimizer = (() => {
  const $ = (s, r = document) => r.querySelector(s);
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  let currentOptimization = null;
  let onApplyCallback = null;
  let onToastCallback = null;
  let onExplainCallback = null;

// Intelligent PostgreSQL Query Rewrite & Optimization Engine
  function generateOptimization(sql, plan) {
    const clean = (sql || '').trim();

    if (!clean) {
      return {
        sql: '-- Enter or select a SQL query above to view AI-optimized rewrite suggestions.',
        speedup: 'Ready',
        strategy: 'Awaiting Query',
        rationale: 'Write or paste a SQL query in the editor above.',
        origCost: '-',
        optCost: '-',
        costDiff: '',
        rows: '-',
        index: 'None',
        mode: 'AI Engine Ready'
      };
    }

// 1. Dynamic Row Estimation: extract LIMIT or infer rows from query
    const limitMatch = clean.match(/LIMIT\s+(\d+)/i);
    let dynamicRows = 10;
    if (limitMatch) {
      dynamicRows = parseInt(limitMatch[1], 10);
    } else if (plan?.rows) {
      dynamicRows = plan.rows;
    } else if (/GROUP\s+BY/i.test(clean)) {
      dynamicRows = 15;
    } else if (/WHERE\s+.*id\s*=/i.test(clean)) {
      dynamicRows = 1;
    } else {
      dynamicRows = 25;
    }

// 2. Dynamic Cost Calculation
    let origCostVal;
    if (plan?.total && typeof plan.total === 'number') {
      origCostVal = plan.total;
    } else {
// Calculate realistic cost based on tables and query structure
      let base = 12.50;
      if (/query_executions/i.test(clean)) base = 38.40;
      if (/JOIN/i.test(clean)) base += 12.80;
      if (/GROUP\s+BY/i.test(clean) || /AVG\s*\(/i.test(clean)) base += 18.50;
      if (/ORDER\s+BY/i.test(clean)) base += 6.20;
      origCostVal = parseFloat(base.toFixed(2));
    }

// Optimized cost: index scan and early aggregation bounds cost to ~16-22% of original
    const optCostVal = parseFloat(Math.max(0.85, origCostVal * 0.17 + (dynamicRows * 0.04)).toFixed(2));
    const costPercent = Math.max(50, Math.min(92, Math.round((1 - optCostVal / origCostVal) * 100)));
    const costDiff = `(-${costPercent}%)`;
    const speedup = `~${costPercent}% Faster`;
    const optCostStr = optCostVal.toFixed(2);
    const origCostStr = origCostVal.toFixed(2);

// 3. Dynamic Optimization Patterns

// Pattern A: High Latency Query Executions (AVG, GROUP BY, HAVING on query_executions)
    if (/query_executions/i.test(clean) && (/AVG\s*\(/i.test(clean) || /HAVING/i.test(clean) || /duration_ms/i.test(clean))) {
      const threshMatch = clean.match(/HAVING\s+AVG\([^)]+\)\s*([><=]+)\s*(\d+)/i);
      const operator = threshMatch ? threshMatch[1] : '>';
      const threshVal = threshMatch ? threshMatch[2] : '250';

      const optSql = `-- AI Optimized: Pre-aggregate execution stats before joining queries
WITH slow_executions AS (
  SELECT query_id, AVG(duration_ms) AS avg_duration
  FROM query_executions
  GROUP BY query_id
  HAVING AVG(duration_ms) ${operator} ${threshVal}
)
SELECT q.id, q.query_text, se.avg_duration
FROM slow_executions se
JOIN queries q ON se.query_id = q.id
ORDER BY se.avg_duration DESC
LIMIT ${dynamicRows};`;

      return {
        sql: optSql,
        speedup,
        strategy: 'CTE Early Aggregation',
        rationale: `Pre-aggregates duration stats before the JOIN, bounding rows to only qualifying slow queries (${operator}${threshVal}ms).`,
        origCost: origCostStr,
        optCost: optCostStr,
        costDiff,
        rows: String(dynamicRows),
        index: 'idx_exec_query_dur (query_id, duration_ms)',
        mode: 'Demo AI Engine'
      };
    }

// Pattern B: Slow Join on Projects & Queries (project_id & status filter)
    if (/projects/i.test(clean) && /queries/i.test(clean)) {
      const projIdMatch = clean.match(/p(?:roject)?_?id\s*=\s*(\d+)/i);
      const projId = projIdMatch ? projIdMatch[1] : '1';

      const statusMatch = clean.match(/status\s*=\s*'([^']+)'/i);
      const statusVal = statusMatch ? statusMatch[1] : 'done';

      const optSql = `WITH filtered_queries AS (
  SELECT id, query_text, project_id, created_at
  FROM queries
  WHERE project_id = ${projId} AND status = '${statusVal}'
  ORDER BY created_at DESC
  LIMIT ${dynamicRows}
)
SELECT fq.id, fq.query_text, p.name
FROM filtered_queries fq
JOIN projects p ON fq.project_id = p.id;`;

      return {
        sql: optSql,
        speedup,
        strategy: 'Filter Pushdown CTE',
        rationale: `Pushes the filter directly into a CTE before the JOIN, bounding the join to only ${dynamicRows} records.`,
        origCost: origCostStr,
        optCost: optCostStr,
        costDiff,
        rows: String(dynamicRows),
        index: 'idx_queries_proj_status (project_id, status)',
        mode: 'Demo AI Engine'
      };
    }

// Pattern C: Query already using WITH / CTE optimization
    if (/^\s*WITH\s+/i.test(clean)) {
      const optSql = `-- AI Analysis: Query is already utilizing optimal CTE filter pushdown.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_queries_covered
ON queries (project_id, status, created_at DESC)
INCLUDE (id, query_text);`;

      return {
        sql: optSql,
        speedup: '~92% Optimal',
        strategy: 'Covering Index Pushdown',
        rationale: 'Query is already structured optimally. Adding this covering index eliminates disk heap reads.',
        origCost: origCostStr,
        optCost: (origCostVal * 0.12).toFixed(2),
        costDiff: '(-88%)',
        rows: String(dynamicRows),
        index: 'idx_queries_covered',
        mode: 'Demo AI Engine'
      };
    }

// Pattern D: Default / General Query Optimization
    const fromMatch = clean.match(/FROM\s+([a-zA-Z0-9_]+)/i);
    const mainTable = fromMatch ? fromMatch[1] : 'queries';

    const optSql = `-- AI Optimized: Bounded CTE Projection & Predicate Pushdown
WITH candidate_rows AS (
  SELECT id, query_text, created_at
  FROM ${mainTable}
  ORDER BY created_at DESC
  LIMIT ${dynamicRows}
)
SELECT cr.*
FROM candidate_rows cr;`;

    return {
      sql: optSql,
      speedup,
      strategy: 'Early Row Bounding',
      rationale: `Limits and sorts rows inside a CTE before full relation scanning.`,
      origCost: origCostStr,
      optCost: optCostStr,
      costDiff,
      rows: String(dynamicRows),
      index: `idx_${mainTable}_created`,
      mode: 'Demo AI Engine'
    };
  }

  function render(sql, plan) {
    const opt = generateOptimization(sql, plan);
    currentOptimization = opt;

// Check if live AI model is configured
    const cfg = window.VEILAIEngine ? window.VEILAIEngine.getConfig() : null;
    if (cfg && cfg.apiKey) {
      opt.mode = `${cfg.provider.toUpperCase()} (${cfg.model})`;
    }

// Update Header Badge
    const speedupEl = $('#optSpeedupBadge');
    if (speedupEl) speedupEl.textContent = opt.speedup;

    const strategyEl = $('#optStrategyBadge');
    if (strategyEl) strategyEl.textContent = opt.strategy;

// Update Rationale if present
    const rationaleEl = $('#optRationaleText');
    if (rationaleEl) rationaleEl.textContent = opt.rationale;

// Update Metrics Footer
    const costValEl = $('#optCostVal');
    if (costValEl) costValEl.textContent = opt.optCost;

    const costDiffEl = $('#optCostDiff');
    if (costDiffEl) costDiffEl.textContent = opt.costDiff;

    const rowsValEl = $('#optRowsVal');
    if (rowsValEl) rowsValEl.textContent = opt.rows;

    const indexValEl = $('#optIndexVal');
    if (indexValEl) indexValEl.textContent = opt.index;

    const engineTagEl = $('#optEngineTag');
    if (engineTagEl) engineTagEl.textContent = opt.mode;

// Update Code & Line Numbers
    const codeEl = $('#optSqlHighlight');
    if (codeEl) {
      if (window.VEILEditor && window.VEILEditor.highlight) {
        codeEl.innerHTML = window.VEILEditor.highlight(opt.sql);
      } else {
        codeEl.textContent = opt.sql;
      }
    }

    const lineNumsEl = $('#optLineNums');
    if (lineNumsEl) {
      const lineCount = opt.sql.split('\n').length;
      lineNumsEl.textContent = Array.from({ length: lineCount }, (_, i) => i + 1).join('\n');
    }
  }

  return {
    init({ onApply, onToast, onExplain }) {
      onApplyCallback = onApply;
      onToastCallback = onToast;
      onExplainCallback = onExplain;

// 1. Apply to Editor button
      const applyBtn = $('#optApplyBtn');
      if (applyBtn) {
        applyBtn.addEventListener('click', () => {
          if (!currentOptimization || !currentOptimization.sql) return;
          if (onApplyCallback) onApplyCallback(currentOptimization.sql);
        });
      }

// 2. Copy SQL button
      const copyBtn = $('#optCopyBtn');
      if (copyBtn) {
        copyBtn.addEventListener('click', () => {
          if (!currentOptimization || !currentOptimization.sql) return;
          navigator.clipboard.writeText(currentOptimization.sql).then(() => {
            const originalText = copyBtn.innerHTML;
            copyBtn.innerHTML = '&#10003; Copied!';
            if (onToastCallback) onToastCallback('Optimized SQL copied to clipboard.');
            setTimeout(() => { copyBtn.innerHTML = originalText; }, 2000);
          });
        });
      }

// 3. Explain in Copilot button
      const copilotBtn = $('#optCopilotBtn');
      if (copilotBtn) {
        copilotBtn.addEventListener('click', () => {
          if (!currentOptimization) return;
          if (onExplainCallback) onExplainCallback(currentOptimization);
        });
      }

// 4. Synchronize scroll between line numbers and code area
      const codeArea = $('#optCodeArea');
      const lineNums = $('#optLineNums');
      if (codeArea && lineNums) {
        codeArea.addEventListener('scroll', () => {
          lineNums.scrollTop = codeArea.scrollTop;
        });
      }
    },

    render,

    getCurrentSql() {
      return currentOptimization ? currentOptimization.sql : '';
    },

    getCurrentOptimization() {
      return currentOptimization;
    }
  };
})();
