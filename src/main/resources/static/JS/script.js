// Application coordinator: connects API client, editor, AI, chat.

(() => {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

// Shared Toast Notification
  let toastTimer;
  function toast(text) {
    const t = $('#recToast');
    if (!t) return;
    t.textContent = text;
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { t.hidden = true; }, 3500);
  }

// Card Pulse Animation
  function pulseCard(card) {
    if (!card) return;
    card.classList.remove('pulse');
    void card.offsetWidth;
    card.classList.add('pulse');
    card.addEventListener('animationend', () => card.classList.remove('pulse'), { once: true });
  }

// Masked password dialog, built from the app's existing modal styles. Resolves to the typed password, or null when cancelled.
  function askPassword(connection) {
    return new Promise((resolve) => {
      const overlay = document.createElement('div');
      overlay.className = 'modal';
      overlay.innerHTML = `
        <div class="modal-box">
          <h2>Unlock Saved Connection</h2>
          <p class="modal-desc">Enter the password for <strong>${esc(connection.name)}</strong>
            (${esc(connection.username || 'postgres')}@${esc(connection.host || 'localhost')}:${esc(connection.port || 5432)}/${esc(connection.databaseName || '')}).
            It is used once and never saved.</p>
          <label>Password
            <input id="unlockPassword" type="password" autocomplete="off" placeholder="••••••••">
          </label>
          <div class="modal-actions">
            <button class="btn ghost" type="button" data-act="cancel">Cancel</button>
            <button class="btn primary" type="button" data-act="ok">Unlock</button>
          </div>
        </div>`;

      document.body.appendChild(overlay);

      const input = overlay.querySelector('#unlockPassword');

      const finish = (value) => {
        overlay.remove();
        resolve(value);
      };

      overlay.querySelector('[data-act="cancel"]').addEventListener('click', () => finish(null));
      overlay.querySelector('[data-act="ok"]').addEventListener('click', () => finish(input.value));
      overlay.addEventListener('click', (event) => {
        if (event.target === overlay) finish(null);
      });
      input.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') finish(input.value);
        if (event.key === 'Escape') finish(null);
      });

      input.focus();
    });
  }

// ================= VIEW SWITCHER =================
  function switchView(viewName) {
    const viewAnalyzer = $('#viewAnalyzer');
    const viewAi = $('#viewAi');
    const viewSchema = $('#viewSchema');

// Synchronize top toolbar active state
    $$('.toolbar-btn').forEach((b) => b.classList.remove('active'));

    if (viewName === 'ai') {
      if (viewAnalyzer) viewAnalyzer.hidden = true;
      if (viewAi) viewAi.hidden = false;
      if (viewSchema) viewSchema.hidden = true;
      $$('.nav-item').forEach((i) => i.classList.toggle('active', i.dataset.nav === 'ai'));
      $('#tbBtnVeilAi')?.classList.add('active');
      runAnalysis();
    } else if (viewName === 'schema') {
      if (viewAnalyzer) viewAnalyzer.hidden = true;
      if (viewAi) viewAi.hidden = true;
      if (viewSchema) viewSchema.hidden = false;
      $$('.nav-item').forEach((i) => i.classList.toggle('active', i.dataset.nav === 'schema'));
      $('#tbBtnSchema')?.classList.add('active');
      if (window.VEILSchemaViewer) {
        window.VEILSchemaViewer.render();
      }
    } else {
      if (viewAnalyzer) viewAnalyzer.hidden = false;
      if (viewAi) viewAi.hidden = true;
      if (viewSchema) viewSchema.hidden = true;
      $$('.nav-item').forEach((i) => i.classList.toggle('active', i.dataset.nav === 'analyzer'));
      $('#tbBtnExplain')?.classList.add('active');
      pulseCard($('.editor-panel'));
    }
  }

  $('#backToAnalyzerBtn')?.addEventListener('click', () => switchView('analyzer'));
  $('#schemaBackBtn')?.addEventListener('click', () => switchView('analyzer'));

// ================= DATABASE CONNECTIONS =================
  let savedConnections = [];
  let activeConnectionId = null;
  const connSelect = $('#connSelect');

  async function loadConnections() {
    try {
      savedConnections = await window.VEILApi.getConnections();

// Which saved connection is the analysis actually pointed at?
      const active = await window.VEILApi.getActiveConnection().catch(() => null);
      activeConnectionId = active && active.active ? active.connection.id : null;
    } catch {
      const stored = localStorage.getItem('veil_connections');
      if (stored) {
        try { savedConnections = JSON.parse(stored); } catch { savedConnections = []; }
      }
      if (!Array.isArray(savedConnections)) savedConnections = [];
      activeConnectionId = null;
    }
    renderConnections();
  }

// The dropdown always offers the default datasource. Saved connections are only listed as active when the backend confirms one is active.
  function visibleConnections() {
    const defaultEntry = {
      id: 'default',
      name: 'Default PostgreSQL',
      type: 'PostgreSQL',
      synthetic: true,
    };

    return [defaultEntry, ...savedConnections];
  }

  function activeIndex() {
    const list = visibleConnections();
    if (activeConnectionId === null) {
      return list.findIndex((c) => c.id === 'default');
    }
    return list.findIndex((c) => c.id === activeConnectionId);
  }

  function selectActiveOption() {
    const idx = activeIndex();
    if (connSelect) connSelect.value = String(idx >= 0 ? idx : 0);
    applyConnection();
  }

  function renderConnections() {
    if (!connSelect) return;

    connSelect.innerHTML = visibleConnections()
      .map((c, i) => `<option value="${i}">${esc(c.name)}</option>`)
      .join('');

    selectActiveOption();
  }

  function applyConnection() {
    const list = visibleConnections();
    const c = list[connSelect ? connSelect.value : 0] || list[0];
    const name = c ? c.name : 'Default PostgreSQL';
    const sideConn = $('#sideConn');
    const dbType = $('#dbType');
    if (sideConn) sideConn.textContent = name;
    if (dbType) dbType.value = c?.type || 'PostgreSQL';

    if (window.VEILSchemaViewer) {
      window.VEILSchemaViewer.setConnection(c || { name, type: 'PostgreSQL' });
    }
  }

// Selecting a saved connection asks for its password (never stored) and makes it the database used by schema, EXPLAIN and index analysis.
  async function onConnectionSelected() {
    const list = visibleConnections();
    const previous = activeIndex() >= 0 ? activeIndex() : 0;
    const selectedIndex = Number(connSelect ? connSelect.value : 0);
    const c = list[selectedIndex];

// "Default PostgreSQL" goes back to application.properties
    if (!c || c.id === 'default') {
      if (activeConnectionId === null) {
        applyConnection();
        return;
      }

      try {
        await window.VEILApi.deactivateConnection();
        activeConnectionId = null;
        toast('Using the default database from application.properties.');
        applyConnection();
        await loadHealth();
        await loadSchema();
      } catch (error) {
        toast(`Could not switch to the default database: ${error.message}`);
        if (connSelect) connSelect.value = String(previous);
        applyConnection();
      }
      return;
    }

// Offline-only entries have no backend id and cannot be activated.
    if (typeof c.id !== 'number' || c.id === activeConnectionId) {
      applyConnection();
      return;
    }

    const password = await askPassword(c);

    if (password === null) {
      if (connSelect) connSelect.value = String(previous);
      applyConnection();
      return;
    }

    if (!password) {
      toast(`No password entered for "${c.name}".`);
      if (connSelect) connSelect.value = String(previous);
      applyConnection();
      return;
    }

    try {
      await window.VEILApi.activateConnection(c.id, password);
      activeConnectionId = c.id;
      toast(`Analysing "${c.databaseName}" on ${c.host}.`);
      applyConnection();
      await loadHealth();
      await loadSchema();
    } catch (error) {
      toast(`Could not activate "${c.name}": ${error.message}`);
      if (connSelect) connSelect.value = String(previous);
      applyConnection();
    }
  }

  if (connSelect) connSelect.addEventListener('change', onConnectionSelected);

// Adds a "Find databases" button + datalist to the existing modal without changing the modal's HTML or its CSS.
  function ensureDiscoverButton() {
    const dbInput = $('#newConnDatabase');
    if (!dbInput || $('#discoverDbsBtn')) return;

    const list = document.createElement('datalist');
    list.id = 'newConnDatabaseOptions';
    dbInput.setAttribute('list', 'newConnDatabaseOptions');
    dbInput.insertAdjacentElement('afterend', list);

    const btn = document.createElement('button');
    btn.type = 'button';
    btn.id = 'discoverDbsBtn';
    btn.className = 'btn ghost';
    btn.textContent = 'Find databases';
    btn.style.marginTop = '6px';
    btn.addEventListener('click', discoverDatabases);

    const anchor = dbInput.closest('label') || dbInput;
    anchor.insertAdjacentElement('afterend', btn);
  }

  async function discoverDatabases() {
    const hostPort = ($('#newConnHost')?.value || '').trim() || 'localhost:5432';
    const [hostPart, portPart] = hostPort.split(':');
    const host = (hostPart || 'localhost').trim();
    const port = Number.parseInt(portPart, 10) || 5432;
    const username = ($('#newConnUsername')?.value || '').trim() || 'postgres';
    const password = $('#newConnPassword')?.value || '';

    if (!password) {
      toast('Enter the password first — it is used once to list databases.');
      $('#newConnPassword')?.focus();
      return;
    }

    const btn = $('#discoverDbsBtn');
    const originalText = btn ? btn.textContent : '';
    if (btn) { btn.disabled = true; btn.textContent = 'Searching...'; }

    try {
      const result = await window.VEILApi.discoverDatabases({
        host, port, username, password,
      });
      const databases = result.databases || [];

      const list = $('#newConnDatabaseOptions');
      if (list) {
        list.innerHTML = databases
          .map((name) => `<option value="${esc(name)}"></option>`)
          .join('');
      }

      const dbInput = $('#newConnDatabase');
      if (dbInput && !databases.includes(dbInput.value)) {
        const preferred = databases.find((name) => /veil/i.test(name)) || databases[0];
        if (preferred) dbInput.value = preferred;
      }

      toast(databases.length
        ? `Found ${databases.length} database(s) on ${host}. Pick one in the Database field.`
        : `No databases found on ${host}.`);
    } catch (error) {
      toast(`Could not list databases: ${error.message}`);
    } finally {
      if (btn) { btn.disabled = false; btn.textContent = originalText; }
    }
  }

  const dbTypeSelect = $('#dbType');
  if (dbTypeSelect) {
    dbTypeSelect.addEventListener('change', () => {
      toast('VEIL is currently optimized for PostgreSQL query intelligence.');
      loadHealth();
    });
  }

// Connection Modal
  const connModal = $('#connModal');
  const addConnBtn = $('#addConnBtn');
  const cancelConn = $('#cancelConn');
  const saveConn = $('#saveConn');

  if (addConnBtn) {
    addConnBtn.addEventListener('click', () => {
      if (connModal) {
        connModal.hidden = false;
        ensureDiscoverButton();
        const nameInput = $('#newConnName');
        if (nameInput) nameInput.focus();
      }
    });
  }

  if (cancelConn && connModal) {
    cancelConn.addEventListener('click', () => { connModal.hidden = true; });
    connModal.addEventListener('click', (e) => { if (e.target === connModal) connModal.hidden = true; });
  }

  if (saveConn) {
    saveConn.addEventListener('click', async () => {
      const name = ($('#newConnName')?.value || '').trim();
      const hostPort = ($('#newConnHost')?.value || '').trim() || 'localhost:5432';
      const [hostPart, portPart] = hostPort.split(':');
      const host = (hostPart || 'localhost').trim();
      const port = Number.parseInt(portPart, 10) || 5432;
      const databaseName = ($('#newConnDatabase')?.value || '').trim() || 'veil';
      const username = ($('#newConnUsername')?.value || '').trim() || 'postgres';
      const password = $('#newConnPassword')?.value || '';

      if (!name) {
        toast('Please provide a name for the database connection.');
        return;
      }

// Field names must match the Java ConnectionsController contract.
      const newConn = {
        name,
        type: $('#newConnType')?.value || 'PostgreSQL',
        host,
        port,
        databaseName,
        username,
      };

      let savedToBackend = false;

      try {
        const created = await window.VEILApi.addConnection({ ...newConn, password });
        savedToBackend = true;
        activeConnectionId = created.id;
      } catch (error) {
        const offline = /failed to fetch|networkerror|load failed/i.test(error.message || '');

        if (!offline) {
// The backend tests the credentials before saving: report the real reason and keep the modal open so it can be corrected.
          toast(`Connection rejected by backend: ${error.message}`);
          return;
        }

// Backend unreachable: keep an offline copy so the UI still works.
        savedConnections.push({ ...newConn, id: `local-${Date.now()}` });
        localStorage.setItem('veil_connections', JSON.stringify(savedConnections));
        toast('Backend offline — connection stored locally only.');
      }

      if (connModal) connModal.hidden = true;
      ['#newConnName', '#newConnHost', '#newConnDatabase', '#newConnUsername', '#newConnPassword']
        .forEach((s) => { const el = $(s); if (el) el.value = ''; });

      await loadConnections();

      if (savedToBackend) {
// The backend activates the new connection immediately, so the panels below now describe that database.
        await loadHealth();
        await loadSchema();
        toast(`Connection "${name}" saved and activated.`);
      }
    });
  }

// ================= SIDEBAR NAVIGATION & TOGGLE =================
  const sidebarToggle = $('#sidebarToggle');
  const sidebarEl = $('#sidebar');
  if (sidebarToggle && sidebarEl) {
    sidebarToggle.addEventListener('click', () => {
      sidebarEl.classList.toggle('open');
    });
  }

  $$('.nav-item').forEach((item) => {
    item.addEventListener('click', (e) => {
      e.preventDefault();
      if (window.innerWidth <= 820 && sidebarEl) {
        sidebarEl.classList.remove('open');
      }
      switch (item.dataset.nav) {
        case 'analyzer':
          switchView('analyzer');
          break;
        case 'ai':
          switchView('ai');
          break;
        case 'connections':
          if (connModal) {
            connModal.hidden = false;
            ensureDiscoverButton();
            $('#newConnName')?.focus();
          }
          break;
        case 'schema':
          switchView('schema');
          toast('Schema Explorer opened.');
          break;
        case 'saved':
          renderSaved();
          const savedModal = $('#savedModal');
          if (savedModal) {
            savedModal.hidden = false;
            $('#savedName')?.focus();
          }
          break;
      }
    });
  });

// ================= QUERY INFORMATION =================
  function renderQueryInfo(info, plan) {
    const row = (k, v) => `<div><dt>${k}</dt><dd>${v}</dd></div>`;
    const queryInfoEl = $('#queryInfo');
    if (!queryInfoEl || !info) return;

// Cost and rows are only honest when the planner returned them.
    const cost = plan && Number.isFinite(plan.total) ? plan.total.toFixed(2) : null;
    const rows = plan && Number.isFinite(plan.rows) ? plan.rows : null;

    queryInfoEl.innerHTML =
      row('Query Type', `<span class="pill">${esc(info.type || 'SELECT')}</span>`) +
      row('Tables', esc((info.tables || []).join(', ') || info.table || '-')) +
      row('Columns', esc((info.columns || []).join(', ') || '*')) +
      row('WHERE Condition', esc(info.where || info.whereCondition || 'None')) +
      row('Estimated Cost', cost !== null ? esc(cost) : '&mdash;') +
      row('Estimated Rows', rows !== null ? esc(rows) : '&mdash;');
  }

// ================= CORE QUERY ANALYZER =================
  async function runAnalysis() {
    window.VEILEditor.hideError();
    const sql = window.VEILEditor.getValue().trim();
    if (!sql) return window.VEILEditor.showError('Please enter a SQL query first.');

    const analyzeBtn = $('#analyzeBtn');
    const originalText = analyzeBtn ? analyzeBtn.textContent : '';
    if (analyzeBtn) {
      analyzeBtn.disabled = true;
      analyzeBtn.textContent = 'Analyzing...';
    }

    try {
      let analysisDone = false;
      let planObj = null;

// 1. Call Backend API
      try {
        const [analyzeRes, explainRes] = await Promise.all([
          window.VEILApi.analyze(sql).catch(() => null),
          window.VEILApi.explain(sql).catch(() => null),
        ]);

        if (explainRes && explainRes.plan) {
          planObj = explainRes.plan;
          window.VEILPlanViewer.render(explainRes.plan);
          analysisDone = true;
        }

        if (analyzeRes) {
          renderQueryInfo(analyzeRes, planObj);
        }

// Backend findings + plan notes (falls back to the client engine)
        const schema = window.VEILSchemaViewer.getSchemaCache();
        const aiRes = await window.VEILApi.getRecommendations(sql, planObj, schema, analyzeRes).catch(() => null);
        if (aiRes) {
          window.VEILRecommendations.render(aiRes);
        }
      } catch (apiErr) {
// Fallback to local intelligence if backend is not yet started
      }

// 2. Client-side intelligence fallback if backend was unavailable
      if (!analysisDone) {
        const mock = generateLocalAnalysis(sql);
        planObj = mock.plan;
        renderQueryInfo(mock.info, mock.plan);
        window.VEILPlanViewer.render(mock.plan);
        const schema = window.VEILSchemaViewer.getSchemaCache();
        const aiRecs = window.VEILAIEngine ? await window.VEILAIEngine.analyze(sql, mock.plan, schema) : {
          findings: mock.recs,
          summary: mock.summary,
          notice: 'Operating in standalone mode'
        };
        window.VEILRecommendations.render(aiRecs);
      }

// 3. Update AI Optimized Query panel in lower half of editor column
      if (window.VEILOptimizer) {
        window.VEILOptimizer.render(sql, planObj);
        pulseCard($('.optimized-panel'));
      }
    } catch (e) {
      window.VEILEditor.showError(e.message);
    } finally {
      if (analyzeBtn) {
        analyzeBtn.disabled = true;
        analyzeBtn.textContent = 'Analyzed';
        analyzeBtn.classList.add('btn-analyzed');
      }
    }
  }

// Local parser and execution plan generator for standalone demo mode
  function generateLocalAnalysis(sql) {
    const clean = sql.trim();
    const isSelect = /^\s*SELECT/i.test(clean);
    const hasJoin = /JOIN\s+(\w+)/i.test(clean);
    const hasWhere = /WHERE\s+([^;]+?)(ORDER|LIMIT|GROUP|$)/i.test(clean);
    const hasOrder = /ORDER\s+BY\s+([^;]+?)(LIMIT|$)/i.test(clean);
    const hasLimit = /LIMIT\s+(\d+)/i.test(clean);

    const tables = [];
    const fromMatch = clean.match(/FROM\s+([a-zA-Z0-9_]+)/i);
    if (fromMatch) tables.push(fromMatch[1]);
    const joinMatches = [...clean.matchAll(/JOIN\s+([a-zA-Z0-9_]+)/gi)];
    joinMatches.forEach((m) => { if (!tables.includes(m[1])) tables.push(m[1]); });

    let cols = [];
    const selectMatch = clean.match(/SELECT\s+(.+?)\s+FROM/is);
    if (selectMatch) {
      cols = selectMatch[1].split(',').map((c) => c.trim().replace(/^q\.|^p\.|^u\./, ''));
    }

    const whereStr = hasWhere ? clean.match(/WHERE\s+([^;]+?)(ORDER|LIMIT|GROUP|$)/i)[1].trim() : '';
    const isExecQuery = tables.includes('query_executions') || clean.includes('duration_ms');
    const limitVal = hasLimit ? parseInt(clean.match(/LIMIT\s+(\d+)/i)[1], 10) : (isExecQuery ? 10 : 5);

    let root;
    if (isExecQuery) {
      const seqScan = {
        id: 4, type: 'Seq Scan', label: 'on query_executions e',
        startup: 0.00, total: 12.50, rows: 35, time: [0, 0], kind: 'bad', children: []
      };
      const indexScan = {
        id: 5, type: 'Index Scan', label: 'on queries q using queries_pkey',
        startup: 0.15, total: 8.17, rows: 1, time: [0, 0], kind: 'good', children: []
      };
      const hashJoin = {
        id: 3, type: 'Hash Join', label: 'on (q.id = e.query_id)',
        startup: 1.06, total: 14.20, rows: 28, time: [0, 0], kind: 'info',
        children: [seqScan, indexScan]
      };
      const sortNode = {
        id: 2, type: 'Sort', label: 'by avg_duration DESC',
        startup: 14.85, total: 14.92, rows: 28, time: [0, 0], kind: 'warn',
        children: [hashJoin]
      };
      root = {
        id: 1, type: 'Limit', label: `rows=${limitVal}`,
        startup: 14.85, total: 14.86, rows: limitVal, time: [0, 0], kind: 'info',
        children: [sortNode]
      };
    } else if (hasJoin) {
      const seqScan = {
        id: 4, type: 'Seq Scan', label: 'on queries q',
        startup: 0.00, total: 12.50, rows: 35, time: [0, 0], kind: 'bad', children: []
      };
      const indexScan = {
        id: 5, type: 'Index Scan', label: 'on projects p using projects_pkey',
        startup: 0.15, total: 8.17, rows: 1, time: [0, 0], kind: 'good', children: []
      };
      const hashJoin = {
        id: 3, type: 'Hash Join', label: 'on (q.project_id = p.id)',
        startup: 1.06, total: 14.20, rows: 28, time: [0, 0], kind: 'info',
        children: [seqScan, indexScan]
      };
      const sortNode = {
        id: 2, type: 'Sort', label: 'by created_at DESC',
        startup: 14.85, total: 14.92, rows: 28, time: [0, 0], kind: 'warn',
        children: [hashJoin]
      };
      root = {
        id: 1, type: 'Limit', label: `rows=${limitVal}`,
        startup: 14.85, total: 14.86, rows: limitVal, time: [0, 0], kind: 'info',
        children: [sortNode]
      };
    } else {
      root = {
        id: 1, type: 'Limit', label: `rows=${limitVal}`,
        startup: 0.00, total: 4.50, rows: limitVal, time: [0, 0], kind: 'info',
        children: [{
          id: 2, type: whereStr.includes('id') ? 'Index Scan' : 'Seq Scan',
          label: `on ${tables[0] || 'queries'}`,
          startup: 0.00, total: 10.20, rows: 25, time: [0, 0],
          kind: whereStr.includes('id') ? 'good' : 'bad', children: []
        }]
      };
    }

    const recs = [];
    if (isExecQuery) {
      recs.push({
        title: 'Sequential Scan on query_executions',
        message: "The table 'query_executions' is scanned sequentially across duration records. An index on (query_id, duration_ms) cuts scan cost.",
        severity: 'high',
        suggestionSql: 'CREATE INDEX idx_exec_query_dur ON query_executions (query_id, duration_ms);'
      });
      recs.push({
        title: 'Early CTE Pre-Aggregation',
        message: 'Aggregating duration_ms inside a CTE bounds join input from 14,200 records to only qualifying slow queries.',
        severity: 'medium',
        suggestionSql: 'WITH slow_executions AS (SELECT query_id, AVG(duration_ms) FROM query_executions GROUP BY query_id) ...'
      });
    } else {
      if (whereStr.includes('status')) {
        recs.push({
          title: 'Sequential Scan on queries',
          message: "The table 'queries' is filtered on 'status = done' without an index, triggering a full sequential scan.",
          severity: 'high',
          suggestionSql: 'CREATE INDEX idx_queries_status ON queries (project_id, status);'
        });
      }
      if (hasOrder) {
        recs.push({
          title: 'In-Memory Sort Node Overhead',
          message: 'Sorting by created_at DESC is performed in work_mem. Adding a compound index avoids sort overhead.',
          severity: 'low',
          suggestionSql: 'CREATE INDEX idx_queries_created ON queries (project_id, created_at DESC);'
        });
      }
    }
    if (!recs.length) {
      recs.push({
        title: 'No major issues found',
        message: 'No optimization issues detected.',
        severity: 'low',
        suggestionSql: ''
      });
    }

    return {
      info: {
        type: isSelect ? 'SELECT' : 'QUERY',
        tables: tables.length ? tables : ['queries'],
        columns: cols.length ? cols : ['*'],
        where: whereStr || 'None',
        cost: root.total.toFixed(2),
        rows: root.rows
      },
      plan: root,
      recs,
      summary: `Plan evaluates ${tables.join(', ')} with Limit (${root.rows} rows). An optimized rewrite reduces total cost.`
    };
  }

// ================= SAVED QUERIES =================
  const SAVED_KEY = 'veil_saved_queries';
  const DEFAULT_SAVED = [
    {
      name: 'Slow Join on Projects',
      sql: `SELECT q.id, q.query_text, p.name\nFROM queries q\nJOIN projects p ON q.project_id = p.id\nWHERE p.id = 1 AND q.status = 'done'\nORDER BY q.created_at DESC\nLIMIT 5;`,
      savedAt: Date.now() - 3600000
    },
    {
      name: 'High Latency Query Executions',
      sql: `SELECT q.id, q.query_text, AVG(e.duration_ms) as avg_duration\nFROM queries q\nJOIN query_executions e ON q.id = e.query_id\nGROUP BY q.id, q.query_text\nHAVING AVG(e.duration_ms) > 250\nORDER BY avg_duration DESC\nLIMIT 10;`,
      savedAt: Date.now() - 7200000
    },
    {
      name: 'Active Project Member Breakdown',
      sql: `SELECT p.name, COUNT(u.id) as total_users\nFROM projects p\nLEFT JOIN users u ON u.role = 'Analyst'\nWHERE p.plan_tier = 'enterprise'\nGROUP BY p.name;`,
      savedAt: Date.now() - 86400000
    }
  ];

  function readSaved() {
    try {
      const raw = localStorage.getItem(SAVED_KEY);
      if (raw) return JSON.parse(raw);
    } catch {}
    return DEFAULT_SAVED;
  }

  function writeSaved(list) {
    localStorage.setItem(SAVED_KEY, JSON.stringify(list));
  }

  function renderSaved() {
    const list = readSaved();
    const listEl = $('#savedList');
    if (!listEl) return;

    listEl.innerHTML = list.length
      ? list.map((q, i) => `
          <li data-saved="${i}">
            <span>&#9873; <b>${esc(q.name)}</b></span>
            <span class="meta">${esc(q.sql.replace(/\s+/g, ' ').slice(0, 42))}&hellip;</span>
            <button class="btn-small" type="button" data-saved-del="${i}">Delete</button>
          </li>`).join('')
      : '<li><span>No saved queries yet. Type a query in the editor and save it here.</span></li>';

    $$('[data-saved-del]').forEach((b) => b.addEventListener('click', (e) => {
      e.stopPropagation();
      const currentList = readSaved();
      const [removed] = currentList.splice(Number(b.dataset.savedDel), 1);
      writeSaved(currentList);
      renderSaved();
      toast(`Deleted "${removed.name}".`);
    }));

    $$('[data-saved]').forEach((li) => li.addEventListener('click', () => {
      const q = readSaved()[Number(li.dataset.saved)];
      if (!q) return;
      window.VEILEditor.setValue(q.sql);
      const savedModal = $('#savedModal');
      if (savedModal) savedModal.hidden = true;
      switchView('analyzer');
      runAnalysis();
      toast(`Loaded "${q.name}".`);
    }));
  }

  const saveQueryBtn = $('#saveQueryBtn');
  if (saveQueryBtn) {
    saveQueryBtn.addEventListener('click', () => {
      const sql = window.VEILEditor.getValue().trim();
      if (!sql) { toast('Nothing to save — SQL editor is empty.'); return; }

      const name = ($('#savedName')?.value || '').trim() || `Query ${new Date().toLocaleTimeString()}`;
      const list = readSaved();
      list.unshift({ name, sql, savedAt: Date.now() });
      writeSaved(list);

      const savedNameInput = $('#savedName');
      if (savedNameInput) savedNameInput.value = '';
      renderSaved();
      toast(`Query "${name}" saved.`);
    });
  }

  const cancelSaved = $('#cancelSaved');
  const savedModal = $('#savedModal');
  if (cancelSaved && savedModal) {
    cancelSaved.addEventListener('click', () => { savedModal.hidden = true; });
    savedModal.addEventListener('click', (e) => { if (e.target === savedModal) savedModal.hidden = true; });
  }

// ================= HEALTH POLLING =================
  const DB_TYPE_LABELS = {
    POSTGRESQL: 'PostgreSQL',
    MYSQL: 'MySQL',
    SQLITE: 'SQLite',
    MONGODB: 'Mongodb',
  };

  async function loadHealth() {
    let ok = false;
    let type = 'PostgreSQL';

    try {
      const h = await window.VEILApi.getHealth();
      ok = !!h.connected;
      if (h.databaseType) type = h.databaseType;
    } catch {
      ok = false;
    }

    const connStatus = $('#connStatus');
    const connDot = $('#connDot');
    const sideStatus = $('#sideStatus');
    const sideDot = $('#sideDot');
    const dbType = $('#dbType');

    if (connStatus) {
      connStatus.textContent = ok ? 'Connected' : 'Offline';
      connStatus.className = 'label ' + (ok ? 'connected' : 'offline');
    }
    if (connDot) connDot.className = 'dot ' + (ok ? 'ok' : 'off');
    if (sideStatus) {
      sideStatus.textContent = ok ? 'Connected' : 'Offline';
      sideStatus.className = 'side-foot-status ' + (ok ? 'connected' : '');
    }
    if (sideDot) sideDot.className = 'side-foot-dot ' + (ok ? 'ok' : 'off');
    if (dbType) dbType.value = DB_TYPE_LABELS[String(type).toUpperCase()] || type;
  }

// ================= SCHEMA LOADING =================
  async function loadSchema() {
    try {
      const schema = await window.VEILApi.getSchema();
      window.VEILSchemaViewer.setSchema(schema);
    } catch {
      window.VEILSchemaViewer.setSchema({
        tables: [
          { table_name: 'queries', row_count: 14200 },
          { table_name: 'projects', row_count: 350 },
          { table_name: 'users', row_count: 1200 },
          { table_name: 'query_executions', row_count: 85000 },
          { table_name: 'database_metrics', row_count: 5400 },
        ],
        indexes: [
          { indexname: 'projects_pkey', tablename: 'projects' },
          { indexname: 'queries_pkey', tablename: 'queries' },
          { indexname: 'users_pkey', tablename: 'users' },
          { indexname: 'idx_queries_project_id', tablename: 'queries' },
          { indexname: 'idx_users_email', tablename: 'users' },
        ]
      });
    }
  }

// ================= COPY PLAN JSON =================
  const copyPlanBtn = $('#copyPlanBtn');
  if (copyPlanBtn) {
    copyPlanBtn.addEventListener('click', () => {
      const jsonOut = $('#planJsonOut');
      if (!jsonOut) return;
      jsonOut.select();
      navigator.clipboard.writeText(jsonOut.value).then(() => {
        toast('Execution plan JSON copied to clipboard.');
      }).catch(() => {
        document.execCommand('copy');
        toast('Execution plan JSON copied.');
      });
    });
  }

// ================= TABS =================
  $$('.tab-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      const g = btn.dataset.group, t = btn.dataset.target;
      $$(`.tab-btn[data-group="${g}"]`).forEach((b) => b.classList.toggle('active', b === btn));
      $$(`.tab-pane[data-group="${g}"]`).forEach((p) => p.classList.toggle('active', p.id === t));
    });
  });

// ================= INITIALIZATION ================= 1. Initialize Editor
  window.VEILEditor.init({
    inputSelector: '#sqlInput',
    highlightSelector: '#sqlHighlight',
    lineNumsSelector: '#lineNums',
    errorBoxSelector: '#errorBox',
    onAnalyze: runAnalysis,
  });

// Top Toolbar Interactive Actions (EXPLAIN, Indexes, Schema, Transactions)
  $('#tbBtnExplain')?.addEventListener('click', () => {
    $$('.toolbar-btn').forEach(b => b.classList.remove('active'));
    $('#tbBtnExplain')?.classList.add('active');
    switchView('analyzer');
    const planTreeTabBtn = $('[data-group="plan"][data-target="planTreeTab"]');
    if (planTreeTabBtn) planTreeTabBtn.click();
    runAnalysis();
    toast('⚡ EXPLAIN plan executed on active query.');
  });

  $('#tbBtnIndexes')?.addEventListener('click', () => {
    $$('.toolbar-btn').forEach(b => b.classList.remove('active'));
    $('#tbBtnIndexes')?.classList.add('active');
    switchView('schema');
    const catIndexesTabBtn = $('[data-group="schemaCatalog"][data-target="catalogIndexesTab"]');
    if (catIndexesTabBtn) catIndexesTabBtn.click();
    toast('🗂 Opened Database Indexes in Schema Explorer.');
  });

  $('#tbBtnSchema')?.addEventListener('click', () => {
    $$('.toolbar-btn').forEach(b => b.classList.remove('active'));
    $('#tbBtnSchema')?.classList.add('active');
    switchView('schema');
    const catTablesTabBtn = $('[data-group="schemaCatalog"][data-target="catalogTablesTab"]');
    if (catTablesTabBtn) catTablesTabBtn.click();
    toast('⊞ Database Schema Explorer opened.');
  });

  $('#tbBtnVeilAi')?.addEventListener('click', () => {
    $$('.toolbar-btn').forEach(b => b.classList.remove('active'));
    $('#tbBtnVeilAi')?.classList.add('active');
    switchView('ai');
    toast('✦ VEIL AI Recommendations & Copilot opened.');
  });

// Editor Actions
  $('#analyzeBtn')?.addEventListener('click', async () => {
    await runAnalysis();
    toast('⚡ Query analyzed — AI optimized rewrite generated.');
  });
  $('#formatBtn')?.addEventListener('click', () => {
    window.VEILEditor.format();
    toast('SQL Query formatted.');
  });
  $('#templatesBtn')?.addEventListener('click', () => {
    renderSaved();
    const savedModal = $('#savedModal');
    if (savedModal) savedModal.hidden = false;
  });
  $('#formatTopBtn')?.addEventListener('click', () => {
    window.VEILEditor.format();
    toast('SQL Query formatted.');
  });
  $('#clearBtn')?.addEventListener('click', () => {
    window.VEILEditor.clear();
    if (window.VEILOptimizer) window.VEILOptimizer.render('', null);
  });

// Real-time AI optimizer preview on query input
  let optDebounceTimer = null;
  $('#sqlInput')?.addEventListener('input', () => {
    clearTimeout(optDebounceTimer);
    optDebounceTimer = setTimeout(() => {
      const sql = window.VEILEditor.getValue().trim();
      if (sql && window.VEILOptimizer) {
        window.VEILOptimizer.render(sql, null);
      }
    }, 350);
  });

// 2. Initialize Schema Viewer
  window.VEILSchemaViewer.init({
    onTableClick: (tableName) => {
      window.VEILEditor.insertAtCursor(tableName);
      switchView('analyzer');
    },
    onOpenInEditor: (sql) => {
      window.VEILEditor.setValue(sql);
      switchView('analyzer');
    },
    onToast: toast,
  });

// 3. Initialize AI Query Optimizer (Lower Half of Editor Column)
  if (window.VEILOptimizer) {
    window.VEILOptimizer.init({
      onApply: (optSql) => {
        window.VEILEditor.setValue(optSql);
        pulseCard($('.editor-panel'));
        runAnalysis();
        toast('AI optimized query applied to editor.');
      },
      onToast: toast,
      onExplain: (opt) => {
        switchView('ai');
        if (window.VEILChat) {
          window.VEILChat.send(`Can you explain in detail why this optimized query rewrite is faster than the original query?\n\nORIGINAL SQL:\n${window.VEILEditor.getValue()}\n\nOPTIMIZED SQL (${opt.strategy}):\n${opt.sql}`);
        }
      }
    });
  }

// 4. Initialize Recommendations
  window.VEILRecommendations.init({
    onToast: toast,
  });

// 5. Initialize Interactive Chat Assistant
  window.VEILChat.init();

// 6. Load Data & Run Initial Analysis
  ensureDiscoverButton();
  loadConnections();
  loadHealth();
  loadSchema();
  runAnalysis();
})();
