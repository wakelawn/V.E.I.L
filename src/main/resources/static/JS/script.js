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

  // ================= QUERY HISTORY & WORKSPACES =================
  let projects = [];
  let activeProjectId = null;
  let historyQueries = [];
  let historyTotal = 0;
  let currentQueryId = null;
  let queryVersions = [];
  let historyFilter = 'all'; // 'all' | 'favorites' | tag | projectId

  async function loadProjects() {
    try {
      const ordered = await window.VEILHistoryApi.listProjectsOrdered();
      projects = ordered || [];
      if (projects.length && activeProjectId === null) {
        activeProjectId = projects.find(p => p.isDefaultProject)?.id || projects[0].id;
      }
      renderProjectDropdown();
    } catch {
      projects = [];
      renderProjectDropdown();
    }
  }

  function renderProjectDropdown() {
    const sel = $('#projectSelect');
    if (!sel) return;
    const current = sel.value;
    sel.innerHTML =
      '<option value="default">Default Workspace</option>' +
      projects.map(p => `<option value="${p.id}">${esc(p.name)}</option>`).join('');
    const target = projects.find(p => p.id === current) ? current :
      (activeProjectId || 'default');
    sel.value = target;
  }

  async function onProjectChanged() {
    const sel = $('#projectSelect');
    const val = sel ? sel.value : 'default';
    if (val === 'default') {
      activeProjectId = null;
    } else {
      const p = projects.find(p => String(p.id) === val);
      if (p) activeProjectId = p.id;
    }
    await loadHistoryQueries();
  }

  async function loadHistoryQueries() {
    try {
      let result;
      if (historyFilter === 'favorites') {
        const favs = await window.VEILHistoryApi.listQueries({ page: 0, size: 100 });
        result = { content: favs.content.filter(q => q.favorite), totalElements: favs.content.filter(q => q.favorite).length };
      } else if (historyFilter.startsWith('tag:')) {
        const tag = historyFilter.slice(4);
        result = await window.VEILHistoryApi.listByTag(tag, { page: 0, size: 50 });
      } else if (activeProjectId) {
        result = await window.VEILHistoryApi.listQueriesByProject(activeProjectId, { page: 0, size: 50 });
      } else {
        result = await window.VEILHistoryApi.listQueries({ page: 0, size: 50 });
      }
      historyQueries = result.content || [];
      historyTotal = result.totalElements || historyQueries.length;
      renderHistoryList();
    } catch {
      historyQueries = [];
      renderHistoryList();
    }
  }

  function renderHistoryList() {
    const listEl = $('#historyList');
    if (!listEl) return;

    const filtered = historyFilter === 'favorites'
      ? historyQueries.filter(q => q.favorite)
      : historyQueries;

    if (filtered.length === 0) {
      listEl.innerHTML = '<div class="history-empty">No saved queries yet. Create one from the Query Analyzer.</div>';
      return;
    }

    listEl.innerHTML = filtered.map(q => {
      const isFav = q.favorite ? '\u2605' : '\u2606';
      const tagsHtml = (q.tags || []).map(t => `<span class="history-tag">${esc(t)}</span>`).join('');
      const verCount = q.version > 1 ? ` v${q.version}` : '';
      const updated = q.updatedAt ? new Date(q.updatedAt).toLocaleDateString() : '';
      return `
        <div class="history-item" data-query-id="${q.id}">
          <div class="history-item-main">
            <span class="history-fav" title="Toggle favorite">${isFav}</span>
            <div class="history-item-info">
              <div class="history-item-name">${esc(q.name)}</div>
              <div class="history-item-meta">
                ${esc(q.databaseName || '')} ${verCount} ${updated ? '\u2022 ' + updated : ''}
                ${tagsHtml}
              </div>
            </div>
          </div>
          <div class="history-item-actions">
            <button class="history-btn" title="Load query">\u2190</button>
            <button class="history-btn" title="View versions">\u21b7</button>
            <button class="history-btn history-del" title="Delete">\u2716</button>
          </div>
        </div>
      `;
    }).join('');

    $$('[data-query-id]').forEach((item) => {
      const id = item.dataset.queryId;
      item.querySelector('[title="Load query"]').addEventListener('click', () => loadHistoryQuery(id));
      item.querySelector('[title="View versions"]').addEventListener('click', () => showVersions(id));
      item.querySelector('.history-del').addEventListener('click', (e) => {
        e.stopPropagation();
        deleteHistoryQuery(id);
      });
      item.querySelector('.history-fav').addEventListener('click', (e) => {
        e.stopPropagation();
        toggleHistoryFavorite(id);
      });
    });
  }

  async function loadHistoryQuery(id) {
    try {
      const q = await window.VEILHistoryApi.getQuery(id);
      if (!q) { toast('Query not found.'); return; }
      window.VEILEditor.setValue(q.sqlText || '');
      currentQueryId = q.id;
      const savedModal = $('#savedModal');
      if (savedModal) savedModal.hidden = true;
      switchView('analyzer');
      runAnalysis();
      toast(`Loaded "${q.name}".`);
    } catch (error) {
      toast(`Could not load query: ${error.message}`);
    }
  }

  async function deleteHistoryQuery(id) {
    if (!confirm('Delete this saved query?')) return;
    try {
      await window.VEILHistoryApi.deleteQuery(id);
      toast('Query deleted.');
      await loadHistoryQueries();
    } catch (error) {
      toast(`Could not delete: ${error.message}`);
    }
  }

  async function toggleHistoryFavorite(id) {
    try {
      await window.VEILHistoryApi.toggleFavorite(id);
      await loadHistoryQueries();
    } catch (error) {
      toast(`Could not toggle favorite: ${error.message}`);
    }
  }

  async function showVersions(id) {
    try {
      const versions = await window.VEILHistoryApi.listVersions(id);
      const modal = $('#versionsModal');
      const listEl = $('#versionsList');
      if (!modal || !listEl) return;
      listEl.innerHTML = (versions || []).map(v => `
        <div class="version-item" data-version-id="${v.id}">
          <div class="version-info">
            <div class="version-name">v${v.version}</div>
            <div class="version-date">${v.updatedAt ? new Date(v.updatedAt).toLocaleString() : ''}</div>
          </div>
          <div class="version-actions">
            <button class="version-btn" title="Load version">\u2190</button>
            <button class="version-btn version-del" title="Delete version">\u2716</button>
          </div>
        </div>
      `).join('');
      listEl.querySelectorAll('[data-version-id]').forEach((item) => {
        const vid = item.dataset.versionId;
        item.querySelector('[title="Load version"]').addEventListener('click', () => {
          const v = versions.find(x => String(x.id) === vid);
          if (v) { window.VEILEditor.setValue(v.sqlText || ''); modal.hidden = true; switchView('analyzer'); runAnalysis(); toast(`Loaded version ${v.version}.`); }
        });
        item.querySelector('.version-del').addEventListener('click', (e) => {
          e.stopPropagation();
          if (!confirm('Delete this version?')) return;
          window.VEILHistoryApi.deleteQuery(vid).then(() => { toast('Version deleted.'); showVersions(id); }).catch(() => toast('Could not delete version.'));
        });
      });
      modal.hidden = false;
    } catch (error) {
      toast(`Could not load versions: ${error.message}`);
    }
  }

  async function saveHistoryQuery() {
    const sql = window.VEILEditor.getValue().trim();
    if (!sql) { toast('Nothing to save — SQL editor is empty.'); return; }

    const modal = $('#savedModal');
    const nameInput = $('#savedName');
    const projectSelect = $('#savedProjectSelect');
    const name = (nameInput?.value || '').trim() || `Query ${new Date().toLocaleTimeString()}`;
    const projectId = projectSelect && projectSelect.value !== 'default' ? projectSelect.value : null;

    try {
      const created = await window.VEILHistoryApi.createQuery({
        name, sqlText: sql, projectId,
      });
      currentQueryId = created.id;
      if (nameInput) nameInput.value = '';
      if (projectSelect) projectSelect.value = 'default';
      if (modal) modal.hidden = true;
      toast(`Query "${created.name}" saved.`);
      await loadHistoryQueries();
    } catch (error) {
      toast(`Could not save query: ${error.message}`);
    }
  }

  async function renderSavedProjects() {
    const sel = $('#savedProjectSelect');
    if (!sel) return;
    const current = sel.value;
    sel.innerHTML = '<option value="default">Default Workspace</option>' +
      projects.map(p => `<option value="${p.id}">${esc(p.name)}</option>`).join('');
    sel.value = current;
  }

  async function renderTagsFilter() {
    const container = $('#historyTags');
    if (!container) return;
    try {
      const tags = await window.VEILHistoryApi.listTags();
      container.innerHTML =
        '<button class="history-tag-filter" data-tag="all">All</button>' +
        (tags || []).map(t => `<button class="history-tag-filter" data-tag="${esc(t)}">${esc(t)}</button>`).join('');
      container.querySelectorAll('.history-tag-filter').forEach((btn) => {
        btn.addEventListener('click', () => {
          historyFilter = btn.dataset.tag === 'all' ? 'all' : `tag:${btn.dataset.tag}`;
          activeProjectId = null;
          $('#projectSelect')?.value = 'default';
          loadHistoryQueries();
        });
      });
    } catch {
      container.innerHTML = '<button class="history-tag-filter" data-tag="all">All</button>';
    }
  }

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
          await loadProjects();
          await loadHistoryQueries();
          await renderSavedProjects();
          await renderTagsFilter();
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
        analyzeBtn.disabled = false;
        analyzeBtn.textContent = originalText;
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

// ================= SAVED QUERIES (server-side via /api/history) =================
  const saveQueryBtn = $('#saveQueryBtn');
  if (saveQueryBtn) {
    saveQueryBtn.addEventListener('click', saveHistoryQuery);
  }

  const cancelSaved = $('#cancelSaved');
  const savedModal = $('#savedModal');
  if (cancelSaved && savedModal) {
    cancelSaved.addEventListener('click', () => { savedModal.hidden = true; });
    savedModal.addEventListener('click', (e) => { if (e.target === savedModal) savedModal.hidden = true; });
  }

  const versionsModal = $('#versionsModal');
  const cancelVersions = $('#cancelVersions');
  if (cancelVersions && versionsModal) {
    cancelVersions.addEventListener('click', () => { versionsModal.hidden = true; });
    versionsModal.addEventListener('click', (e) => { if (e.target === versionsModal) versionsModal.hidden = true; });
  }

  const addProjectBtn = $('#addProjectBtn');
  if (addProjectBtn) {
    addProjectBtn.addEventListener('click', async () => {
      const name = prompt('Workspace name:');
      if (!name) return;
      try {
        await window.VEILHistoryApi.createProject({ name });
        toast(`Workspace "${name}" created.`);
        await loadProjects();
      } catch (error) {
        toast(`Could not create workspace: ${error.message}`);
      }
    });
  }

  const projectSelect = $('#projectSelect');
  if (projectSelect) {
    projectSelect.addEventListener('change', onProjectChanged);
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
  $('#templatesBtn')?.addEventListener('click', async () => {
    await loadProjects();
    await loadHistoryQueries();
    await renderSavedProjects();
    await renderTagsFilter();
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
  loadProjects();
  loadHealth();
  loadSchema();
  runAnalysis();
})();
