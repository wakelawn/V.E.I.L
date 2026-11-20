// VEIL — Execution Plan & Details Module Manages Tree View rendering, interactive node selection, Plan Details breakdown, and JSON export.

window.VEILPlanViewer = (() => {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  const NODE_DESC = {
    'Limit': 'Stops scanning immediately after the requested row count (LIMIT) has been produced, saving unnecessary downstream reads.',
    'Sort': 'Sorts intermediate rows to satisfy ORDER BY. Sorting without an index can spill from memory to disk if row volume grows.',
    'Hash Join': 'Builds an in-memory hash table from one relation and probes it with rows from the outer table. Fast for unindexed joins.',
    'Nested Loop': 'Iterates over the outer relation and executes a lookup on the inner relation for each matching row.',
    'Seq Scan': 'Scans every single page and row in the table. Inefficient on medium-to-large tables when filtering down to a small fraction of rows.',
    'Index Scan': 'Traverses a B-tree index structure directly to the qualifying rows, avoiding a full table read.',
    'Index Only Scan': 'Retrieves all requested data directly from the index without reading the heap table pages at all.',
    'Aggregate': 'Aggregates row values (COUNT, SUM, AVG) across grouping sets.',
    'HashAggregate': 'Computes GROUP BY aggregation using an in-memory hash map.',
  };

  let currentPlan = null;
  let selectedId = null;

  const flatten = (n, depth = 0, out = []) => {
    out.push({ n, depth });
    (n.children || []).forEach((c) => flatten(c, depth + 1, out));
    return out;
  };

  const timeText = (n) => (n.time && (n.time[0] || n.time[1])) ? `${n.time[0]}..${n.time[1]} ms` : '-';

  return {
    render(plan) {
      currentPlan = plan;
      selectedId = plan.id;

      const treeEl = $('#planTree');
      const jsonEl = $('#planJsonOut');

      if (treeEl) {
        treeEl.innerHTML = flatten(plan).map(({ n, depth }) => `
          <div class="plan-node k-${n.kind || 'info'}" data-id="${n.id}" style="margin-left:${depth * 18}px">
            <div>
              <div class="node-type-label">${esc(n.type)} ${esc(n.label || '')}</div>
              <div class="node-meta">cost=${n.startup}..${n.total} &bull; est_rows=${n.rows} &bull; time=${timeText(n)}</div>
            </div>
            <span class="rows-badge">${n.rows} rows</span>
          </div>`).join('');

        $$('.plan-node').forEach((el) => {
          el.addEventListener('click', () => this.selectNode(+el.dataset.id));
        });
      }

      if (jsonEl) {
        jsonEl.value = JSON.stringify(plan, (k, v) => (k === 'id' || k === 'kind' ? undefined : v), 2);
      }

      const statusBadge = $('#planStatusBadge');
      if (statusBadge) {
        statusBadge.innerHTML = '&#9679; Analysis Complete';
        statusBadge.className = 'status-pill ok';
      }

      this.selectNode(plan.id);
    },

    selectNode(id) {
      if (!currentPlan) return;
      selectedId = id;

      const flat = flatten(currentPlan);
      const item = flat.find((x) => x.n.id === id);
      if (!item) return;
      const n = item.n;

      $$('.plan-node').forEach((el) => {
        el.classList.toggle('selected', +el.dataset.id === id);
      });

      const detailsEl = $('#planDetails');
      const descEl = $('#planDesc');
      const emptyEl = $('#planDetailsEmpty');
      const wrapEl = $('#planDetailsWrap');

      if (detailsEl) {
        detailsEl.innerHTML = [
          ['Node Type', n.type],
          ['Relation', n.label ? n.label.replace(/^on\s+/, '') : (n.relation || 'None')],
          ['Startup Cost', typeof n.startup === 'number' ? n.startup.toFixed(2) : n.startup],
          ['Total Cost', typeof n.total === 'number' ? n.total.toFixed(2) : n.total],
          ['Estimated Rows', n.rows],
          ['Execution Time', timeText(n)],
        ].map(([k, v]) => `
          <div class="plan-detail-item">
            <dt>${esc(k)}</dt>
            <dd>${esc(v)}</dd>
          </div>`).join('');
      }

      if (descEl) {
        descEl.textContent = NODE_DESC[n.type]
          || `Executes a ${n.type} operation on the target relation. Cost reflects estimated work units.`;
      }

      if (emptyEl) emptyEl.hidden = true;
      if (wrapEl) wrapEl.hidden = false;
    },

    getCurrentPlan() {
      return currentPlan;
    }
  };
})();
