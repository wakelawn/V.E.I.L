(() => {
  const $  = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  /* ================= API ================= */
  const readError = async (res) => {
    try {
      const body = await res.json();
      return body.error || body.message || `HTTP ${res.status}`;
    } catch {
      return `HTTP ${res.status} ${res.statusText}`;
    }
  };
  const API = {
    async get(path) {
      const res = await fetch(path, { headers: { Accept: 'application/json' } });
      if (!res.ok) throw new Error(await readError(res));
      return res.json();
    },
    async post(path, body) {
      const res = await fetch(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      if (!res.ok) throw new Error(await readError(res));
      return res.json();
    },
  };

  /* ================= CONNECTIONS (real, from /api/connections) ================= */
  let savedConnections = [];
  const connSelect = $('#connSelect');

  // shared toast for short messages (recommendation clicks + connection actions)
  let toastTimer;
  function toast(text) {
    const t = $('#recToast');
    t.textContent = text;
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { t.hidden = true; }, 4000);
  }

  async function loadConnections() {
    try {
      savedConnections = await API.get('/api/connections');
    } catch {
      // API unreachable - fall back to the real backend default
      // connection instead of claiming "no saved connections"
      savedConnections = [{ name: 'Default PostgreSQL' }];
    }
    renderConnections();
  }

  function renderConnections() {
    if (!savedConnections.length) {
      connSelect.innerHTML = '<option value="-1">No saved connections yet</option>';
    } else {
      connSelect.innerHTML = savedConnections
        .map((c, i) => `<option value="${i}">${esc(c.name)}</option>`)
        .join('');
    }
    applyConnection();
  }

  function applyConnection() {
    const c = savedConnections[connSelect.value];
    // the analysis always runs on the backend's default connection,
    // so the name shown is the saved connection (or the default)
    $('#sideConn').textContent = c ? c.name : 'Default PostgreSQL';
    $('#dbType').value = 'PostgreSQL';
    // Connected/Offline status comes from loadHealth(), not from this list
  }
  connSelect.addEventListener('change', applyConnection);

  // the type dropdown is informational: it shows the type of the
  // connection the backend actually runs on, so changing it manually
  // snaps back to the detected value instead of doing nothing silently
  $('#dbType').addEventListener('change', () => {
    toast('The backend runs on PostgreSQL - the type is detected automatically.');
    loadHealth();
  });

  const modal = $('#connModal');
  $('#addConnBtn').addEventListener('click', () => { modal.hidden = false; $('#newConnName').focus(); });
  $('#cancelConn').addEventListener('click', () => { modal.hidden = true; });
  modal.addEventListener('click', (e) => { if (e.target === modal) modal.hidden = true; });
  // Enter in any modal field saves; Escape closes
  $$('#connModal input').forEach((field) => field.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') { e.preventDefault(); $('#saveConn').click(); }
  }));
  document.addEventListener('keydown', (e) => {
    // Escape closes whichever modal is open (connection / saved / AI)
    if (e.key === 'Escape') $$('.modal').forEach((m) => { m.hidden = true; });
  });
  $('#saveConn').addEventListener('click', async () => {
    const name = $('#newConnName').value.trim();
    const hostPort = $('#newConnHost').value.trim();
    const databaseName = $('#newConnDatabase').value.trim();
    const username = $('#newConnUsername').value.trim();
    const password = $('#newConnPassword').value;

    if (!name || !hostPort || !databaseName || !username) {
      toast('Please fill in name, host, database and username.');
      return;
    }

    // only PostgreSQL exists in the backend today - be honest about it
    if ($('#newConnType').value !== 'PostgreSQL') {
      toast('Only PostgreSQL connections are supported right now.');
      return;
    }

    // the host field holds "host" or "host:port"
    const colon = hostPort.lastIndexOf(':');
    const host = colon >= 0 ? hostPort.slice(0, colon) : hostPort;
    const port = colon >= 0 ? (Number(hostPort.slice(colon + 1)) || 5432) : 5432;

    const btn = $('#saveConn');
    btn.disabled = true;
    try {
      const saved = await API.post('/api/connections',
        { name, host, port, databaseName, username, password });

      modal.hidden = true;
      ['#newConnName', '#newConnHost', '#newConnDatabase',
       '#newConnUsername', '#newConnPassword'].forEach((s) => ($(s).value = ''));

      await loadConnections();

      // select the connection we just saved
      const idx = savedConnections.findIndex((c) => c.id === saved.id);
      if (idx >= 0) { connSelect.value = idx; applyConnection(); }

      toast(`Connection "${saved.name}" saved.`);
    } catch (e) {
      toast(e.message);   // readable 400 from the backend
    } finally {
      btn.disabled = false;
    }
  });

  /* ================= TABS (generic) =================
     A button with data-group + data-target shows the pane with that id
     inside the same group. Works for schema and plan tabs. */
  $$('.tab-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      const g = btn.dataset.group, t = btn.dataset.target;
      $$(`.tab-btn[data-group="${g}"]`).forEach((b) => b.classList.toggle('active', b === btn));
      $$(`.tab-pane[data-group="${g}"]`).forEach((p) => p.classList.toggle('active', p.id === t));
    });
  });

  /* Sidebar nav: every item does something real
     analyzer -> already on this page (stays highlighted)
     connections -> opens the Add Connection modal
     schema -> refreshes the schema and pulses its card
     saved -> opens the Saved Queries modal
     ai -> opens the AI summary modal */
  $$('.nav-item').forEach((item) => {
    item.addEventListener('click', (e) => {
      e.preventDefault();
      $$('.nav-item').forEach((i) => i.classList.toggle('active', i === item));
      switch (item.dataset.nav) {
        case 'connections':
          modal.hidden = false;
          $('#newConnName').focus();
          break;
        case 'schema':
          loadSchema();
          pulseCard($('#schemaTables').closest('.card'));
          toast('Schema refreshed from the database.');
          break;
        case 'saved':
          savedModal.hidden = false;
          $('#savedName').focus();
          break;
        case 'ai':
          openRecModal();
          break;
        // "analyzer" needs no action - this IS the analyzer page
      }
    });
  });

  /* ================= EDITOR ================= */
  const input = $('#sqlInput'), hl = $('#sqlHighlight'), nums = $('#lineNums');
  const KW = 'SELECT|FROM|WHERE|JOIN|LEFT|RIGHT|INNER|OUTER|FULL|CROSS|ON|AND|OR|NOT|IN|IS|NULL|LIKE|BETWEEN|AS|GROUP|BY|ORDER|HAVING|LIMIT|OFFSET|DISTINCT|UNION|ALL|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CASE|WHEN|THEN|ELSE|END|DESC|ASC|WITH|COUNT|SUM|AVG|MIN|MAX';
  const tokenRe = () => new RegExp(`(--[^\\n]*)|('(?:[^']|'')*')|\\b(\\d+(?:\\.\\d+)?)\\b|\\b(${KW})\\b`, 'gi');

  function highlight(src) {
    let out = '', last = 0, m;
    const re = tokenRe();
    while ((m = re.exec(src))) {
      out += esc(src.slice(last, m.index));
      const cls = m[1] ? 'tok-com' : m[2] ? 'tok-str' : m[3] ? 'tok-num' : 'tok-kw';
      out += `<span class="${cls}">${esc(m[0])}</span>`;
      last = re.lastIndex;
    }
    return out + esc(src.slice(last)) + '\n';
  }
  function refreshEditor() {
    hl.innerHTML = input.value ? highlight(input.value) : '';
    const lines = input.value.split('\n').length;
    nums.textContent = Array.from({ length: lines }, (_, i) => i + 1).join('\n');
    syncScroll();
  }
  function syncScroll() {
    hl.scrollTop = input.scrollTop; hl.scrollLeft = input.scrollLeft;
    nums.scrollTop = input.scrollTop;
  }
  input.addEventListener('input', refreshEditor);
  input.addEventListener('scroll', syncScroll);
  input.addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); analyze(); }
  });

  function formatSQL(sql) {
    let s = sql.replace(/\s+/g, ' ').trim();
    s = s.replace(tokenRe(), (m, c, str, num, kw) => (kw ? m.toUpperCase() : m));
    s = s.replace(/\s(FROM|WHERE|GROUP BY|HAVING|ORDER BY|LIMIT)\b/g, '\n$1')
         .replace(/\s((?:LEFT |RIGHT |INNER |FULL |CROSS )?JOIN)\b/g, '\n$1')
         .replace(/\s(AND|OR)\s/g, '\n  $1 ');
    return s;
  }
  const doFormat = () => { input.value = formatSQL(input.value); refreshEditor(); };
  $('#formatBtn').addEventListener('click', doFormat);
  $('#formatTopBtn').addEventListener('click', doFormat);
  $('#clearBtn').addEventListener('click', () => { input.value = ''; hideError(); refreshEditor(); input.focus(); });

  function showError(msg) { const b = $('#errorBox'); b.textContent = msg; b.hidden = false; }
  function hideError() { $('#errorBox').hidden = true; }

  // drop text (a table name from the schema list) at the caret position
  function insertAtCursor(text) {
    const start = input.selectionStart, end = input.selectionEnd;
    input.value = input.value.slice(0, start) + text + input.value.slice(end);
    input.selectionStart = input.selectionEnd = start + text.length;
    input.focus();
    refreshEditor();
  }

  /* ================= PLAN (real EXPLAIN from the backend) ================= */
  const NODE_DESC = {
    'Limit':          'Stops after the requested number of rows have been produced.',
    'Sort':           'Sorts the rows to satisfy ORDER BY. Can spill to disk if the row set is large.',
    'HashAggregate':  'Groups rows using a hash table to compute GROUP BY aggregates.',
    'Hash Join':      'Builds a hash table from one input and probes it with the other. Fast for large unsorted inputs; an index on the join column can help.',
    'Seq Scan':       'Reads every row of the table. Fine for small tables, slow for large ones when only a few rows are needed.',
    'Index Scan':     'Uses an index to jump straight to matching rows instead of reading the whole table.',
  };

  /* ================= PLAN ADAPTER =================
     Backend PlanSummary -> the shape renderPlan()/selectNode() expect. */
  function adaptPlan(p) {
    let id = 0;
    const walk = (n) => {
      const type = n.nodeType || 'Unknown';
      const kind = type === 'Seq Scan' ? 'bad'
        : /Index/.test(type) ? 'good'
        : type === 'Sort' ? 'warn'
        : 'info';
      return {
        id: ++id,
        type,
        label: n.relation && n.relation !== 'NotAvailable' ? 'on ' + n.relation : '',
        startup: n.startupCost ?? 0,
        total: n.totalCost ?? 0,
        rows: n.estimatedRows ?? 0,
        time: [0, 0],
        kind,
        children: (n.children || []).map(walk),
      };
    };
    return walk(p);
  }

  const flatten = (n, depth = 0, out = []) => { out.push({ n, depth }); n.children.forEach((c) => flatten(c, depth + 1, out)); return out; };
  let currentPlan = null, selectedId = null;

  // plain EXPLAIN has no real timings - show '-' instead of a fake 0..0 ms
  const timeText = (n) => (n.time[0] || n.time[1]) ? `${n.time[0]}..${n.time[1]} ms` : '-';

  function renderPlan(plan) {
    currentPlan = plan;
    selectedId = plan.id;

    $('#planTree').innerHTML = flatten(plan).map(({ n, depth }) => `
      <div class="plan-node k-${n.kind}" data-id="${n.id}" style="margin-left:${depth * 18}px">
        <div><b>${esc(n.type)} ${esc(n.label)}</b>
        <small>cost=${n.startup}..${n.total} rows=${n.rows} time=${timeText(n)}</small></div>
        <span class="rows-badge">${n.rows} rows</span>
      </div>`).join('');

    $('#planJsonOut').textContent = JSON.stringify(plan, (k, v) => (k === 'id' || k === 'kind' ? undefined : v), 2);

    const viz = (n) => `<div class="viz-branch">
      <div class="viz-node k-${n.kind}" data-id="${n.id}"><b>${esc(n.type)}</b>
        ${n.label ? `<small>${esc(n.label.replace('on ', ''))}</small>` : ''}
        <small>${n.rows} rows</small><small>${timeText(n)}</small></div>
      ${n.children.length ? `<div class="viz-arrow"></div><div class="viz-children">${n.children.map(viz).join('')}</div>` : ''}
    </div>`;
    $('#viz').innerHTML = viz(plan);

    $$('.plan-node, .viz-node').forEach((el) => el.addEventListener('click', () => selectNode(+el.dataset.id)));
    selectNode(plan.id);
  }

  function selectNode(id) {
    selectedId = id;
    const n = flatten(currentPlan).find((x) => x.n.id === id).n;
    $$('.plan-node, .viz-node').forEach((el) => el.classList.toggle('selected', +el.dataset.id === id));
    $('#planDetails').innerHTML = [
      ['Node Type', n.type], ['Startup Cost', n.startup], ['Total Cost', n.total],
      ['Estimated Rows', n.rows], ['Actual Rows', '-'], ['Node Time', timeText(n)],
    ].map(([k, v]) => `<div><dt>${k}</dt><dd>${esc(v)}</dd></div>`).join('');
    $('#planDesc').textContent = NODE_DESC[n.type] || '';
  }

  /* ================= INFO / RECOMMENDATIONS / AI ================= */
  function renderInfo(a) {
    const row = (k, v) => `<div><dt>${k}</dt><dd>${v}</dd></div>`;
    $('#queryInfo').innerHTML =
      row('Query Type', '<span class="pill">SELECT</span>') +
      row('Table', esc(a.table || '-')) +
      row('Columns', esc((a.columns || []).join(', ') || '-')) +
      row('WHERE', esc(a.whereCondition || '-'));
  }

  function toRecs(a) {
    // structured findings from the optimization engine, when present
    const findings = (a && a.findings) || null;
    const out = findings
      ? findings.map((f) => {
          const impact = ['high', 'medium', 'low'].includes(f.severity)
            ? f.severity : 'low';
          return {
            icon: impact === 'high' ? '&#9888;' : '&#9432;',
            color: impact === 'high' ? 'var(--warn)' : 'var(--accent)',
            impact,
            title: f.title || 'Recommendation',
            text: f.message,
            sql: f.suggestionSql || '',
          };
        })
      : (a && a.recommendations || []).map((text) => ({
          icon: '&#9888;', color: 'var(--warn)', impact: 'medium',
          title: 'Recommendation', text, sql: '',
        }));
    if (!out.length) out.push({
      icon: '&#9432;', color: 'var(--good)', impact: 'low',
      title: 'No major issues found',
      text: 'No optimization issues detected.', sql: '',
    });
    return out;
  }

  function renderRecs(recs) {
    $('#recCount').textContent = `(${recs.length})`;
    $('#recList').innerHTML = recs.map((r, i) => `
      <div class="rec">
        <div class="rec-main">
          <span class="rec-ico" style="color:${r.color}">${r.icon}</span>
          <div class="rec-text">
            <button class="rec-message" type="button" data-rec-message="${i}">
              <b>${esc(r.title)}</b><span>${esc(r.text)}</span>
            </button>
          </div>
          <span class="impact ${r.impact}">${r.impact[0].toUpperCase() + r.impact.slice(1)} Impact</span>
          ${r.sql ? `<button class="btn-small" data-rec="${i}">Show SQL</button>` : ''}
        </div>
        <pre class="rec-extra" id="rec-extra-${i}" hidden>${esc(r.sql)}</pre>
      </div>`).join('');
    $$('[data-rec-message]').forEach((button) => button.addEventListener('click', () => {
      const rec = recs[Number(button.dataset.recMessage)];
      toast(`${rec.title}: ${rec.text}`);
    }));
    $$('[data-rec]').forEach((b) => b.addEventListener('click', () => {
      const box = $('#rec-extra-' + b.dataset.rec);
      box.hidden = !box.hidden;
      b.textContent = box.hidden ? 'Show SQL' : 'Hide SQL';
    }));
  }

  function renderAI(plan) {
    // the backend already produces a readable summary of the plan
    $('#aiText').textContent = (plan && plan.planSummary) || '';
  }

  /* ================= ANALYZE ================= */
  async function analyze() {
    hideError();
    const sql = input.value.trim();
    if (!sql) return showError('Please enter a SQL query first.');

    const btn = $('#analyzeBtn');
    const original = btn.textContent;
    btn.disabled = true;
    btn.textContent = 'Analyzing...';

    let failure = null;

    let recs = [];

    try {
      // 1. parser-based analysis (tokens/columns/recommendations)
      try {
        const a = await API.post('/api/analyze', { sql });
        renderInfo(a);
        recs = toRecs(a);
      } catch (e) {
        // parser may not support the grammar yet; the plan can still work
        failure = e.message;
      }

      // 2. real PostgreSQL EXPLAIN
      try {
        const ex = await API.post('/api/explain', { sql });
        renderPlan(adaptPlan(ex.plan));
        renderAI(ex.plan);

        // plan-level insights (bottleneck, filter scans) join the list
        for (const text of ex.plan.recommendations || []) {
          recs.push({
            icon: '&#9673;', color: 'var(--accent)', impact: 'low',
            title: 'Plan Insight', text, sql: '',
          });
        }
      } catch (e) {
        failure = failure || e.message;
      }

      if (!recs.length) recs = toRecs([]);
      renderRecs(recs);

      if (failure) showError(failure);
    } finally {
      btn.disabled = false;
      btn.textContent = original;
    }
  }
  $('#analyzeBtn').addEventListener('click', analyze);

  /* ================= SCHEMA LIST ================= */
  let schemaCache = { tables: [], indexes: [] };

  async function loadSchema() {
    try {
      const data = await API.get('/api/database');
      schemaCache = { tables: data.tables || [], indexes: data.indexes || [] };
      renderSchema($('#schemaSearch').value.trim());
    } catch (e) {
      $('#tableList').innerHTML = '';
      $('#indexList').innerHTML = '';
      showError(`Could not load schema: ${e.message}`);
    }
  }

  function renderSchema(filter = '') {
    const f = filter.toLowerCase();
    $('#tableList').innerHTML = schemaCache.tables
      .filter((t) => t.table_name.toLowerCase().includes(f))
      .map((t) => `<li data-table="${esc(t.table_name)}"><span>&#9638; ${esc(t.table_name)}</span></li>`)
      .join('');

    $('#indexList').innerHTML = schemaCache.indexes
      .filter((i) => (i.indexname + i.tablename).toLowerCase().includes(f))
      .map((i) => `<li data-index="${esc(i.indexname)}" data-idx-table="${esc(i.tablename)}">`
        + `<span>${esc(i.indexname)}</span><span class="meta">${esc(i.tablename)}</span></li>`)
      .join('');

    // clicking a table inserts its name into the editor at the caret
    $$('#tableList li').forEach((li) => li.addEventListener('click', () => {
      insertAtCursor(li.dataset.table);
      toast(`Inserted ${li.dataset.table} into the editor.`);
    }));

    // clicking an index explains where it lives
    $$('#indexList li').forEach((li) => {
      li.addEventListener('click', () => {
        toast(`Index ${li.dataset.index} is on table ${li.dataset.idxTable}.`);
      });
      // index name + table meta side by side (row layout)
      li.style.display = 'flex';
      li.style.flexDirection = 'row';
      li.style.alignItems = 'center';
      li.style.justifyContent = 'space-between';
      li.style.gap = '8px';
    });
  }
  $('#schemaSearch').addEventListener('input', (e) => renderSchema(e.target.value.trim()));

  /* ================= AI SUMMARY MODAL =================
     The sidebar "AI Recommendations" item opens the plan summary
     (the same text the Recommendations card shows) in a modal. */
  const recModal = $('#recModal');

  function openRecModal() {
    const text = $('#aiText').textContent.trim();
    $('#recModalText').textContent = text
      || 'Run Analyze Query first - the AI summary of the execution plan will appear here.';
    recModal.hidden = false;
  }

  $('#recModalClose').addEventListener('click', () => { recModal.hidden = true; });
  recModal.addEventListener('click', (e) => { if (e.target === recModal) recModal.hidden = true; });

  // brief highlight around a card, used when the sidebar refreshes it
  function pulseCard(card) {
    if (!card) return;
    card.classList.remove('pulse');
    void card.offsetWidth;          // restart the CSS animation
    card.classList.add('pulse');
    card.addEventListener('animationend', () => card.classList.remove('pulse'), { once: true });
  }

  /* ================= SAVED QUERIES (localStorage) =================
     Saved queries live only in this browser for now - localStorage
     keeps them across page loads, but they are NOT on the server. */
  const savedModal = $('#savedModal');
  const SAVED_KEY = 'veil_saved_queries';

  const readSaved = () => {
    try { return JSON.parse(localStorage.getItem(SAVED_KEY)) || []; }
    catch { return []; }
  };
  const writeSaved = (list) => localStorage.setItem(SAVED_KEY, JSON.stringify(list));

  function renderSaved() {
    const list = readSaved();
    $('#savedList').innerHTML = list.length
      ? list.map((q, i) => `
          <li data-saved="${i}">
            <span>&#9873; ${esc(q.name)}</span>
            <span class="meta">${esc(q.sql.length > 40 ? q.sql.slice(0, 40) + '\u2026' : q.sql)}</span>
            <button class="btn-small" type="button" data-saved-del="${i}">Delete</button>
          </li>`).join('')
      : '<li><span>No saved queries yet</span>'
        + '<span class="meta">Type a query in the editor, then save it here</span></li>';

    // deleting a saved query (does not load it)
    $$('[data-saved-del]').forEach((b) => b.addEventListener('click', (e) => {
      e.stopPropagation();
      const list = readSaved();
      const [removed] = list.splice(Number(b.dataset.savedDel), 1);
      writeSaved(list);
      renderSaved();
      toast(`Deleted "${removed.name}".`);
    }));

    // clicking a saved query loads it into the editor and analyzes it
    $$('[data-saved]').forEach((li) => li.addEventListener('click', () => {
      const q = readSaved()[Number(li.dataset.saved)];
      if (!q) return;
      input.value = q.sql;
      refreshEditor();
      savedModal.hidden = true;
      analyze();
      toast(`Loaded "${q.name}".`);
    }));
  }

  $('#saveQueryBtn').addEventListener('click', () => {
    const sql = input.value.trim();
    if (!sql) { toast('Nothing to save - the editor is empty.'); return; }

    const name = $('#savedName').value.trim() || `Query ${new Date().toLocaleString()}`;
    const list = readSaved();
    list.unshift({ name, sql, savedAt: Date.now() });
    writeSaved(list);

    $('#savedName').value = '';
    renderSaved();
    toast(`Saved "${name}".`);
  });

  $('#cancelSaved').addEventListener('click', () => { savedModal.hidden = true; });
  savedModal.addEventListener('click', (e) => { if (e.target === savedModal) savedModal.hidden = true; });
  $('#savedName').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') { e.preventDefault(); $('#saveQueryBtn').click(); }
  });

  /* ================= HEALTH ================= */
  const typeMap = { POSTGRESQL: 'PostgreSQL', MYSQL: 'MySQL', SQLITE: 'SQLite', MONGODB: 'Mongodb' };

  async function loadHealth() {
    // the status always reflects the backend's default connection,
    // because that is the connection the analysis actually uses
    let ok = false;
    let type = 'PostgreSQL';
    try {
      const h = await API.get('/api/database/health');
      ok = !!h.connected;
      if (typeMap[h.databaseType]) type = typeMap[h.databaseType];
    } catch {
      // leave offline; the error box is not needed for a status check
    }

    $('#connStatus').textContent = ok ? 'Connected' : 'Offline';
    $('#connDot').className = 'dot ' + (ok ? 'ok' : 'off');
    $('#sideStatus').textContent = ok ? 'Connected' : 'Offline';
    $('#sideStatus').style.color = ok ? '' : 'var(--bad)';
    $('#dbType').value = type;
  }

  /* ================= INIT ================= */
  loadConnections();
  loadHealth();
  loadSchema();
  refreshEditor();
  analyze();
})();
