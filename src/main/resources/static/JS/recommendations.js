// AI recommendations module: renders diagnostics, indexes, bottlenecks.

window.VEILRecommendations = (() => {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  let currentRecs = [];
  let currentSummary = '';
  let onToastCallback = null;

  return {
    init({ onToast }) {
      onToastCallback = onToast;

// Copy SQL from query preview button
      const copyQueryBtn = $('#aiCopyQueryBtn');
      if (copyQueryBtn) {
        copyQueryBtn.addEventListener('click', () => {
          const sql = window.VEILEditor ? window.VEILEditor.getValue() : '';
          navigator.clipboard.writeText(sql).then(() => {
            if (onToastCallback) onToastCallback('SQL Query copied to clipboard.');
          });
        });
      }
    },

    render({ findings = [], summary = '', bottlenecks = [], estimatedImprovement = '', source = '', model = '', notice = '' }) {
      currentRecs = findings;
      currentSummary = summary;

// 1. Update active model badge
      const modelBadge = $('#aiModelBadge');
      const cfg = window.VEILAIEngine ? window.VEILAIEngine.getConfig() : null;
      if (modelBadge) {
        if (source === 'nvidia_nim') {
          modelBadge.textContent = `NVIDIA NIM · ${model || cfg?.model || 'configured'}`;
          modelBadge.className = 'ai-badge live';
        } else if (source === 'java_backend') {
          modelBadge.textContent = 'Java Backend Engine';
          modelBadge.className = 'ai-badge live';
        } else if (source === 'rule_based_optimizer') {
          modelBadge.textContent = 'Rule-Based Engine (offline)';
          modelBadge.className = 'ai-badge fallback';
        } else if (cfg?.apiKey) {
          modelBadge.textContent = `${cfg.provider.toUpperCase()} (${cfg.model})`;
          modelBadge.className = 'ai-badge live';
        } else {
          modelBadge.textContent = 'NVIDIA NIM (not configured)';
          modelBadge.className = 'ai-badge fallback';
        }
      }

// 2. Update Query Context Breakdown
      const sql = window.VEILEditor ? window.VEILEditor.getValue().trim() : '';
      const sqlPreview = $('#aiQueryPreviewCode');
      if (sqlPreview) {
        sqlPreview.textContent = sql || '-- No SQL query loaded yet.';
      }

      const plan = window.VEILPlanViewer ? window.VEILPlanViewer.getCurrentPlan() : null;
      const metaType = $('#aiMetaType');
      const metaCost = $('#aiMetaCost');
      const metaRows = $('#aiMetaRows');
      const metaImprovement = $('#aiMetaImprovement');

      const firstWord = sql.match(/^([a-zA-Z]+)/);
      if (metaType) metaType.textContent = firstWord ? firstWord[1].toUpperCase() : '—';
      if (metaCost) metaCost.textContent = Number.isFinite(plan?.total) ? plan.total.toFixed(2) : '—';
      if (metaRows) metaRows.textContent = Number.isFinite(plan?.rows) ? plan.rows : '—';
      if (metaImprovement) metaImprovement.textContent = estimatedImprovement || '—';

// 3. Update Summary & Bottlenecks
      const summaryEl = $('#aiFullSummary');
      if (summaryEl) {
        let text = summary;
        if (notice) text += `\n\n📌 ${notice}`;
        summaryEl.textContent = text;
      }

      const bottlenecksEl = $('#aiBottlenecksList');
      if (bottlenecksEl) {
        if (bottlenecks && bottlenecks.length) {
          bottlenecksEl.innerHTML = bottlenecks
            .map((b) => `<span class="bottleneck-tag">&#9888; ${esc(b)}</span>`)
            .join('');
        } else {
          bottlenecksEl.innerHTML = '<span class="bottleneck-tag ok">&#10003; No critical bottlenecks detected</span>';
        }
      }

// 4. Render Deep Recommendations List
      const recListEl = $('#aiFullRecList');
      const recCountEl = $('#aiFullRecCount');
      if (recCountEl) recCountEl.textContent = `(${findings.length})`;

      if (recListEl) {
        recListEl.innerHTML = findings.map((r, i) => {
          const impact = r.severity || 'low';
          const icon = impact === 'high' ? '&#9888;' : impact === 'medium' ? '&#9888;' : '&#9432;';
          const sqlFix = r.suggestionSql || r.sql || '';
          return `
            <div class="ai-rec-card impact-${impact}">
              <div class="ai-rec-header">
                <div class="ai-rec-title-wrap">
                  <span class="ai-rec-icon">${icon}</span>
                  <h3 class="ai-rec-title">${esc(r.title)}</h3>
                </div>
                <span class="impact ${impact}">${impact[0].toUpperCase() + impact.slice(1)} Impact</span>
              </div>
              <p class="ai-rec-msg">${esc(r.message || r.text || '')}</p>
              ${sqlFix ? `
                <div class="ai-rec-sql-box">
                  <div class="ai-rec-sql-header">
                    <span>Suggested PostgreSQL Fix</span>
                    <button class="btn-copy-fix" type="button" data-fix="${esc(sqlFix)}">Copy SQL</button>
                  </div>
                  <pre class="ai-rec-code"><code>${esc(sqlFix)}</code></pre>
                </div>
              ` : ''}
            </div>`;
        }).join('');

// Copy SQL listeners
        recListEl.querySelectorAll('.btn-copy-fix').forEach((btn) => {
          btn.addEventListener('click', () => {
            navigator.clipboard.writeText(btn.dataset.fix).then(() => {
              btn.textContent = 'Copied!';
              if (onToastCallback) onToastCallback('Optimization SQL copied to clipboard.');
              setTimeout(() => { btn.textContent = 'Copy SQL'; }, 2000);
            });
          });
        });
      }
    },

    getCurrentSummary() {
      return currentSummary;
    }
  };
})();
