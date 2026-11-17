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

  /* ================= CONNECTIONS ================= */
  const connections = [
    { name: 'Local PostgreSQL', type: 'PostgreSQL', host: 'localhost:5432', online: true },
  ];

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

  /* Sidebar nav highlight */
  $$('.nav-item').forEach((item) => {
    item.addEventListener('click', () => {
      $$('.nav-item').forEach((i) => i.classList.toggle('active', i === item));
    });
  });

  /* ================= CONNECTIONS ================= */
  const connSelect = $('#connSelect');

  function renderConnections(selectedIndex = 0) {
    connSelect.innerHTML = connections.map((c, i) => `<option value="${i}">${esc(c.name)}</option>`).join('');
    connSelect.value = selectedIndex;
    applyConnection();
  }
  function applyConnection() {
    const c = connections[connSelect.value];
    $('#connStatus').textContent = c.online ? 'Connected' : 'Offline';
    $('#connDot').className = 'dot ' + (c.online ? 'ok' : 'off');
    $('#sideStatus').textContent = c.online ? 'Connected' : 'Offline';
    $('#sideStatus').style.color = c.online ? '' : 'var(--bad)';
    $('#sideConn').textContent = c.name;
    $('#dbType').value = c.type;
  }
  connSelect.addEventListener('change', applyConnection);

  const modal = $('#connModal');
  $('#addConnBtn').addEventListener('click', () => { modal.hidden = false; $('#newConnName').focus(); });
  $('#cancelConn').addEventListener('click', () => { modal.hidden = true; });
  modal.addEventListener('click', (e) => { if (e.target === modal) modal.hidden = true; });
  $('#saveConn').addEventListener('click', () => {
    const name = $('#newConnName').value.trim();
    if (!name) { $('#newConnName').focus(); return; }
    connections.push({ name, type: $('#newConnType').value, host: $('#newConnHost').value.trim(), online: true });
    renderConnections(connections.length - 1);
    ['#newConnName', '#newConnHost'].forEach((s) => ($(s).value = ''));
    modal.hidden = true;
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

  function renderPlan(plan) {
    currentPlan = plan;
    selectedId = plan.id;

    $('#planTree').innerHTML = flatten(plan).map(({ n, depth }) => `
      <div class="plan-node k-${n.kind}" data-id="${n.id}" style="margin-left:${depth * 18}px">
        <div><b>${esc(n.type)} ${esc(n.label)}</b>
        <small>cost=${n.startup}..${n.total} rows=${n.rows} time=${n.time[0]}..${n.time[1]} ms</small></div>
        <span class="rows-badge">${n.rows} rows</span>
      </div>`).join('');

    $('#planJsonOut').textContent = JSON.stringify(plan, (k, v) => (k === 'id' || k === 'kind' ? undefined : v), 2);

    const viz = (n) => `<div class="viz-branch">
      <div class="viz-node k-${n.kind}" data-id="${n.id}"><b>${esc(n.type)}</b>
        ${n.label ? `<small>${esc(n.label.replace('on ', ''))}</small>` : ''}
        <small>${n.rows} rows</small><small>${n.time[0]}..${n.time[1]} ms</small></div>
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
      ['Estimated Rows', n.rows], ['Actual Rows', '-'], ['Node Time', `${n.time[0]}..${n.time[1]} ms`],
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

  let recToastTimer;
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
    const toast = $('#recToast');
    $$('[data-rec-message]').forEach((button) => button.addEventListener('click', () => {
      const rec = recs[Number(button.dataset.recMessage)];
      toast.textContent = `${rec.title}: ${rec.text}`;
      toast.hidden = false;
      clearTimeout(recToastTimer);
      recToastTimer = setTimeout(() => { toast.hidden = true; }, 4000);
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
      .map((t) => `<li><span>&#9638; ${esc(t.table_name)}</span></li>`)
      .join('');

    $('#indexList').innerHTML = schemaCache.indexes
      .filter((i) => (i.indexname + i.tablename).toLowerCase().includes(f))
      .map((i) => `<li><span>${esc(i.indexname)}</span><span class="meta">${esc(i.tablename)}</span></li>`)
      .join('');
  }
  $('#schemaSearch').addEventListener('input', (e) => renderSchema(e.target.value.trim()));

  /* ================= HEALTH ================= */
  async function loadHealth() {
    let ok = false;
    const typeMap = { POSTGRESQL: 'PostgreSQL', MYSQL: 'MySQL', SQLITE: 'SQLite', MONGODB: 'Mongodb' };
    let type = connections[0].type;
    try {
      const h = await API.get('/api/database/health');
      ok = !!h.connected;
      if (typeMap[h.databaseType]) type = typeMap[h.databaseType];
    } catch {
      // leave offline; the error box is not needed for a status check
    }

    connections[0].online = ok;
    connections[0].type = type;
    applyConnection();
  }

  /* ================= INIT ================= */
  renderConnections(0);
  loadHealth();
  loadSchema();
  refreshEditor();
  analyze();
})();
