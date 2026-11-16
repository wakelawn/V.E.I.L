(() => {
  const $  = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  /* ================= DEMO DATA ================= */
  const SCHEMA = {
    users:       ['id', 'name', 'email', 'created_at'],
    orders:      ['id', 'user_id', 'status', 'total_amount', 'created_at'],
    products:    ['id', 'name', 'price', 'category_id'],
    categories:  ['id', 'name'],
    order_items: ['id', 'order_id', 'product_id', 'quantity'],
    payments:    ['id', 'order_id', 'amount', 'paid_at'],
  };
  const INDEXES = [
    { name: 'users_pkey',               table: 'users',       cols: 'id' },
    { name: 'orders_pkey',              table: 'orders',      cols: 'id' },
    { name: 'products_pkey',            table: 'products',    cols: 'id' },
    { name: 'categories_pkey',          table: 'categories',  cols: 'id' },
    { name: 'order_items_order_id_idx', table: 'order_items', cols: 'order_id' },
    { name: 'payments_pkey',            table: 'payments',    cols: 'id' },
  ];
  const connections = [
    { name: 'Production PostgreSQL', type: 'PostgreSQL', host: 'prod-db:5432', online: true },
    { name: 'Staging MySQL',         type: 'MySQL',      host: 'stg-db:3306',  online: true },
    { name: 'Local SQLite',          type: 'SQLite',     host: 'local.db',     online: false },
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

  /* ================= SQL PARSING ================= */
  function parseSQL(sql) {
    const clean = sql.replace(/--[^\n]*/g, ' ').replace(/\s+/g, ' ').trim().replace(/;$/, '');
    const type = (clean.match(/^\w+/) || [''])[0].toUpperCase();

    const tables = [];
    const stop = 'ON|WHERE|JOIN|LEFT|RIGHT|INNER|OUTER|FULL|CROSS|GROUP|ORDER|LIMIT|HAVING|USING|SET|VALUES';
    const tre = new RegExp(`\\b(?:FROM|JOIN|INTO|UPDATE)\\s+([a-zA-Z_][\\w.]*)(?:\\s+(?:AS\\s+)?(?!(?:${stop})\\b)([a-zA-Z_]\\w*))?`, 'gi');
    let m;
    while ((m = tre.exec(clean))) tables.push({ name: m[1].toLowerCase(), alias: m[2] || m[1] });

    const joins = (clean.match(/\bJOIN\b/gi) || []).length;
    const wm = clean.match(/\bWHERE\b(.*?)(?=\bGROUP BY\b|\bHAVING\b|\bORDER BY\b|\bLIMIT\b|$)/i);
    const filters = wm ? wm[1].trim().split(/\s+(?:AND|OR)\s+/i).filter(Boolean) : [];
    const filterCols = filters.map((f) => (f.match(/([\w.]+)\s*(?:=|<|>|!|LIKE|IN\b)/i) || [])[1]).filter(Boolean);
    const on = clean.match(/\bON\s+([\w.]+)\s*=\s*([\w.]+)/i);

    return {
      type, tables, joins, filters, filterCols,
      on: on ? [on[1], on[2]] : null,
      sorting: /\bORDER BY\b/i.test(clean),
      grouping: /\bGROUP BY\b/i.test(clean),
      limit: (clean.match(/\bLIMIT\s+(\d+)/i) || [])[1] || null,
      hasStar: /SELECT\s+(?:\w+\.)?\*/i.test(clean),
    };
  }

  /* ================= PLAN (simulated from the parsed query) =================
     These costs/rows are estimates generated for the demo UI, not real
     database output. Swap buildPlan() for a real EXPLAIN call later. */
  const NODE_DESC = {
    'Limit':          'Stops after the requested number of rows have been produced.',
    'Sort':           'Sorts the rows to satisfy ORDER BY. Can spill to disk if the row set is large.',
    'HashAggregate':  'Groups rows using a hash table to compute GROUP BY aggregates.',
    'Hash Join':      'Builds a hash table from one input and probes it with the other. Fast for large unsorted inputs; an index on the join column can help.',
    'Seq Scan':       'Reads every row of the table. Fine for small tables, slow for large ones when only a few rows are needed.',
    'Index Scan':     'Uses an index to jump straight to matching rows instead of reading the whole table.',
  };

  function buildPlan(p) {
    let id = 0;
    const node = (type, label, startup, total, rows, kind, children = []) => ({
      id: ++id, type, label, startup: +startup.toFixed(2), total: +total.toFixed(2),
      rows, time: [+(startup / 100).toFixed(3), +(total / 20).toFixed(3)], kind, children,
    });

    const base = p.tables.length ? p.tables : [{ name: 'table' }];
    const rowSizes = [500, 200, 300, 150];
    const scans = base.map((t, i) => {
      const filtered = p.filterCols.some((c) => c.startsWith(t.alias + '.') || c.startsWith(t.name + '.'));
      const useIndex = i > 0 || filtered;
      return useIndex
        ? node('Index Scan', 'on ' + t.name, 0.42, 5 + i * 3, rowSizes[i % 4], 'good')
        : node('Seq Scan', 'on ' + t.name, 0, 10 + i * 5, rowSizes[i % 4], 'bad');
    });

    let cur = scans[0];
    for (let i = 1; i < scans.length; i++) {
      const rows = Math.round(Math.min(cur.rows, scans[i].rows) * 0.5) || 1;
      cur = node('Hash Join', '', 0.42, cur.total + scans[i].total + 5, rows, 'info', [cur, scans[i]]);
    }
    const wrap = (type, extra, rows, kind) => {
      cur = node(type, '', cur.startup + extra * 0.5, cur.total + extra, rows, kind, [cur]);
    };
    if (p.grouping) wrap('HashAggregate', 4, Math.max(1, Math.round(cur.rows * 0.6)), 'info');
    if (p.sorting)  wrap('Sort', 5, cur.rows, 'warn');
    if (p.limit)    wrap('Limit', 0.1, Math.min(+p.limit, cur.rows), 'info');
    return cur;
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
  function renderInfo(p) {
    const row = (k, v) => `<div><dt>${k}</dt><dd>${v}</dd></div>`;
    $('#queryInfo').innerHTML =
      row('Query Type', `<span class="pill">${esc(p.type)}</span>`) +
      row('Tables', esc(p.tables.map((t) => t.name).join(', ') || '-')) +
      row('Joins', p.joins) +
      row('Filters', p.filters.length) +
      row('Sorting', p.sorting ? '<span class="yes">Yes</span>' : 'No') +
      row('Limit', p.limit ? `<span class="yes">Yes (${esc(p.limit)})</span>` : 'No');
  }

  function buildRecs(p) {
    const recs = [];
    if (p.on) {
      const [a, b] = p.on.map((s) => s.split('.'));
      const target = p.tables.find((t) => t.alias === b[0] || t.name === b[0]);
      const col = b[1] || a[1];
      if (target && col) recs.push({
        icon: '&#9888;', color: 'var(--bad)', impact: 'high', title: `Add Index on ${target.name}.${col}`,
        text: `Consider adding an index on the ${target.name}.${col} column to improve join performance.`,
        sql: `CREATE INDEX idx_${target.name}_${col}\n  ON ${target.name} (${col});`,
      });
    }
    if (p.filterCols.length && p.sorting) {
      const t = p.tables[p.tables.length - 1];
      const cols = [...new Set(p.filterCols.map((c) => c.split('.').pop()))].join(', ');
      recs.push({
        icon: '&#9888;', color: 'var(--warn)', impact: 'medium', title: 'Consider Composite Index',
        text: `A composite index on (${cols}) could improve filtering performance.`,
        sql: `CREATE INDEX idx_${t.name}_composite\n  ON ${t.name} (${cols});`,
      });
    }
    if (p.hasStar) recs.push({
      icon: '&#9432;', color: 'var(--accent)', impact: 'low', title: 'Avoid SELECT *',
      text: 'Select only the columns you need to reduce data transferred.',
      sql: '-- List the needed columns explicitly instead of *',
    });
    if (!p.limit && p.type === 'SELECT') recs.push({
      icon: '&#9432;', color: 'var(--accent)', impact: 'low', title: 'Consider Adding a LIMIT',
      text: 'Limiting rows can reduce the work done on large result sets.',
      sql: '-- Add: LIMIT 100',
    });
    if (!recs.length) recs.push({
      icon: '&#9432;', color: 'var(--good)', impact: 'low', title: 'No major issues found',
      text: 'This query looks reasonable based on the estimated plan.', sql: '',
    });
    return recs;
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

  function renderAI(p, plan) {
    const tbls = p.tables.map((t) => t.name);
    const parts = [];
    parts.push(`This ${p.type.toLowerCase()} query reads from ${tbls.length > 1 ? tbls.slice(0, -1).join(', ') + ' and ' + tbls.slice(-1) : (tbls[0] || 'a table')}.`);
    if (p.joins) parts.push(`It combines ${p.joins + 1} tables using ${p.joins} join${p.joins > 1 ? 's' : ''}.`);
    if (p.filters.length) parts.push(`${p.filters.length} filter condition${p.filters.length > 1 ? 's' : ''} narrow down the rows.`);
    if (p.sorting) parts.push('Results are sorted' + (p.limit ? ` and cut to the first ${p.limit} rows.` : '.'));
    const seq = flatten(plan).find(({ n }) => n.type === 'Seq Scan');
    if (seq) parts.push(`The estimated plan shows a sequential scan ${seq.n.label}, which could be improved with an index.`);
    $('#aiText').textContent = parts.join(' ');
  }

  /* ================= ANALYZE ================= */
  function analyze() {
    hideError();
    const sql = input.value.trim();
    if (!sql) return showError('Please enter a SQL query first.');
    const p = parseSQL(sql);
    if (!['SELECT', 'WITH', 'INSERT', 'UPDATE', 'DELETE'].includes(p.type)) {
      return showError('This does not look like a valid SQL statement.');
    }
    if (!p.tables.length) return showError('No table found. Check your FROM clause.');
    const plan = buildPlan(p);
    renderInfo(p);
    renderPlan(plan);
    renderRecs(buildRecs(p));
    renderAI(p, plan);
  }
  $('#analyzeBtn').addEventListener('click', analyze);

  /* ================= SCHEMA LIST ================= */
  function renderSchema(filter = '') {
    const f = filter.toLowerCase();
    $('#tableList').innerHTML = Object.keys(SCHEMA).filter((t) => t.includes(f)).map((t) =>
      `<li data-table="${t}"><span>&#9638; ${esc(t)}</span><div class="cols" hidden>${SCHEMA[t].map(esc).join('\n')}</div></li>`).join('');
    $('#indexList').innerHTML = INDEXES.filter((i) => (i.name + i.table).includes(f)).map((i) =>
      `<li><span>${esc(i.name)}</span><span class="meta">${esc(i.table)} (${esc(i.cols)})</span></li>`).join('');
    $$('#tableList li').forEach((li) => li.addEventListener('click', () => {
      const cols = $('.cols', li);
      cols.hidden = !cols.hidden;
      cols.style.whiteSpace = 'pre';
      li.classList.toggle('open', !cols.hidden);
    }));
  }
  $('#schemaSearch').addEventListener('input', (e) => renderSchema(e.target.value.trim()));

  /* ================= INIT ================= */
  renderConnections(0);
  renderSchema();
  refreshEditor();
  analyze();
})();
