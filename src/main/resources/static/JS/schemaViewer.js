// Schema explorer: table/index/column navigation and DDL generation.

window.VEILSchemaViewer = (() => {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

// "CREATE INDEX x ON public.t USING btree (project_id, created_at DESC)" -> { method: 'btree', columns: ['project_id', 'created_at DESC'] }
  function extractParenBody(text) {
    const open = text.indexOf('(');
    if (open < 0) return '';

    let depth = 0;
    for (let i = open; i < text.length; i++) {
      if (text[i] === '(') depth++;
      else if (text[i] === ')') {
        depth--;
        if (depth === 0) return text.slice(open + 1, i);
      }
    }
    return text.slice(open + 1);
  }

  function splitTopLevel(body) {
    const parts = [];
    let depth = 0;
    let current = '';

    for (const ch of body) {
      if (ch === '(') depth++;
      if (ch === ')') depth--;

      if (ch === ',' && depth === 0) {
        parts.push(current.trim());
        current = '';
      } else {
        current += ch;
      }
    }

    if (current.trim()) parts.push(current.trim());
    return parts.filter(Boolean);
  }

  function parseIndexDefinition(definition) {
    const def = String(definition || '');
    const match = def.match(/USING\s+([a-zA-Z0-9_]+)/i);

    return {
      method: match ? match[1].toLowerCase() : 'n/a',
      columns: splitTopLevel(extractParenBody(def)),
    };
  }

// Rich metadata definitions for PostgreSQL schema
  const DEFAULT_TABLE_METADATA = {
    queries: {
      table_name: 'queries',
      description: 'Logged SQL queries evaluated and tracked by the VEIL Query Intelligence platform.',
      row_count: 14200,
      columns: [
        { name: 'id', type: 'bigserial', pk: true, nullable: false, defaultVal: "nextval('queries_id_seq'::regclass)" },
        { name: 'project_id', type: 'bigint', fk: 'projects(id)', nullable: false, defaultVal: '1' },
        { name: 'query_text', type: 'text', nullable: false, defaultVal: "''" },
        { name: 'query_hash', type: 'character varying(64)', nullable: false, defaultVal: "md5(query_text)" },
        { name: 'status', type: 'character varying(32)', nullable: false, defaultVal: "'done'" },
        { name: 'estimated_cost', type: 'numeric(10,2)', nullable: true, defaultVal: '0.00' },
        { name: 'created_at', type: 'timestamp with time zone', nullable: false, defaultVal: 'now()' }
      ],
      indexes: [
        { name: 'queries_pkey', method: 'btree', columns: ['id'], isUnique: true, isPk: true },
        { name: 'idx_queries_project_id', method: 'btree', columns: ['project_id'], isUnique: false, isPk: false },
        { name: 'idx_queries_created_at', method: 'btree', columns: ['created_at DESC'], isUnique: false, isPk: false }
      ],
      sampleRows: [
        { id: 1, project_id: 1, query_text: "SELECT q.id, q.query_text, p.name FROM queries q JOIN projects p...", query_hash: '9a8b1c4d', status: 'done', estimated_cost: '14.86', created_at: '2026-11-19 14:32:00 UTC' },
        { id: 2, project_id: 1, query_text: "SELECT * FROM users WHERE email = 'admin@veil.app' LIMIT 1;", query_hash: '3f5e7a9b', status: 'done', estimated_cost: '4.12', created_at: '2026-11-19 14:15:20 UTC' },
        { id: 3, project_id: 2, query_text: "SELECT count(*) FROM database_metrics WHERE recorded_at > now() - interval '1 hour';", query_hash: 'c8d2e4f6', status: 'done', estimated_cost: '52.40', created_at: '2026-11-19 13:50:00 UTC' },
        { id: 4, project_id: 1, query_text: "SELECT id, duration_ms FROM query_executions ORDER BY duration_ms DESC LIMIT 10;", query_hash: '7b1c3d5e', status: 'done', estimated_cost: '88.10', created_at: '2026-11-19 13:30:15 UTC' },
        { id: 5, project_id: 3, query_text: "UPDATE queries SET status = 'archived' WHERE created_at < '2026-01-01';", query_hash: '1e3f5a7b', status: 'done', estimated_cost: '110.50', created_at: '2026-11-19 12:45:00 UTC' }
      ]
    },
    projects: {
      table_name: 'projects',
      description: 'Projects and database environments monitored within VEIL workspaces.',
      row_count: 350,
      columns: [
        { name: 'id', type: 'serial', pk: true, nullable: false, defaultVal: "nextval('projects_id_seq'::regclass)" },
        { name: 'name', type: 'character varying(100)', nullable: false, defaultVal: "''" },
        { name: 'database_type', type: 'character varying(50)', nullable: false, defaultVal: "'PostgreSQL'" },
        { name: 'connection_string', type: 'character varying(255)', nullable: true, defaultVal: 'NULL' },
        { name: 'created_at', type: 'timestamp with time zone', nullable: false, defaultVal: 'now()' }
      ],
      indexes: [
        { name: 'projects_pkey', method: 'btree', columns: ['id'], isUnique: true, isPk: true },
        { name: 'idx_projects_name', method: 'btree', columns: ['name'], isUnique: true, isPk: false }
      ],
      sampleRows: [
        { id: 1, name: 'Production Analytics', database_type: 'PostgreSQL', connection_string: 'postgresql://app:***@db.prod.internal:5432/analytics', created_at: '2026-09-01 09:00:00 UTC' },
        { id: 2, name: 'Staging Cluster', database_type: 'PostgreSQL', connection_string: 'postgresql://stage:***@db.stage.internal:5432/app', created_at: '2026-09-15 11:30:00 UTC' },
        { id: 3, name: 'Core Microservices', database_type: 'PostgreSQL', connection_string: 'postgresql://svc:***@db.core.internal:5432/services', created_at: '2026-10-01 10:00:00 UTC' },
        { id: 4, name: 'Customer Reporting DB', database_type: 'PostgreSQL', connection_string: 'postgresql://report:***@db.reports.internal:5432/reporting', created_at: '2026-10-20 16:45:00 UTC' }
      ]
    },
    users: {
      table_name: 'users',
      description: 'Database engineers, analysts, and administrators with access to VEIL.',
      row_count: 1200,
      columns: [
        { name: 'id', type: 'serial', pk: true, nullable: false, defaultVal: "nextval('users_id_seq'::regclass)" },
        { name: 'email', type: 'character varying(255)', nullable: false, defaultVal: "''" },
        { name: 'full_name', type: 'character varying(100)', nullable: true, defaultVal: 'NULL' },
        { name: 'role', type: 'character varying(50)', nullable: false, defaultVal: "'analyst'" },
        { name: 'is_active', type: 'boolean', nullable: false, defaultVal: 'true' },
        { name: 'created_at', type: 'timestamp with time zone', nullable: false, defaultVal: 'now()' }
      ],
      indexes: [
        { name: 'users_pkey', method: 'btree', columns: ['id'], isUnique: true, isPk: true },
        { name: 'idx_users_email', method: 'btree', columns: ['email'], isUnique: true, isPk: false }
      ],
      sampleRows: [
        { id: 1, email: 'admin@veil.app', full_name: 'Lead Database Architect', role: 'admin', is_active: 'true', created_at: '2026-08-01 08:00:00 UTC' },
        { id: 2, email: 'analyst@veil.app', full_name: 'Senior Data Engineer', role: 'analyst', is_active: 'true', created_at: '2026-08-15 10:20:00 UTC' },
        { id: 3, email: 'dev@veil.app', full_name: 'Backend Platform Engineer', role: 'developer', is_active: 'true', created_at: '2026-09-02 14:10:00 UTC' },
        { id: 4, email: 'qa@veil.app', full_name: 'Performance Testing Specialist', role: 'tester', is_active: 'true', created_at: '2026-09-25 15:30:00 UTC' }
      ]
    },
    query_executions: {
      table_name: 'query_executions',
      description: 'Execution timeline log recording query latency, shared buffer hits, and disk reads.',
      row_count: 85000,
      columns: [
        { name: 'id', type: 'bigserial', pk: true, nullable: false, defaultVal: "nextval('query_executions_id_seq'::regclass)" },
        { name: 'query_id', type: 'bigint', fk: 'queries(id)', nullable: false, defaultVal: '1' },
        { name: 'duration_ms', type: 'numeric(10,3)', nullable: false, defaultVal: '0.000' },
        { name: 'rows_returned', type: 'integer', nullable: false, defaultVal: '0' },
        { name: 'buffer_hits', type: 'integer', nullable: false, defaultVal: '0' },
        { name: 'buffer_reads', type: 'integer', nullable: false, defaultVal: '0' },
        { name: 'executed_at', type: 'timestamp with time zone', nullable: false, defaultVal: 'now()' }
      ],
      indexes: [
        { name: 'query_executions_pkey', method: 'btree', columns: ['id'], isUnique: true, isPk: true },
        { name: 'idx_executions_query_id', method: 'btree', columns: ['query_id'], isUnique: false, isPk: false },
        { name: 'idx_executions_executed_at', method: 'btree', columns: ['executed_at DESC'], isUnique: false, isPk: false }
      ],
      sampleRows: [
        { id: 10421, query_id: 1, duration_ms: '3.421', rows_returned: 5, buffer_hits: 124, buffer_reads: 0, executed_at: '2026-11-19 14:32:01 UTC' },
        { id: 10420, query_id: 2, duration_ms: '0.840', rows_returned: 1, buffer_hits: 42, buffer_reads: 0, executed_at: '2026-11-19 14:15:21 UTC' },
        { id: 10419, query_id: 3, duration_ms: '48.910', rows_returned: 1, buffer_hits: 1840, buffer_reads: 112, executed_at: '2026-11-19 13:50:01 UTC' },
        { id: 10418, query_id: 4, duration_ms: '12.150', rows_returned: 10, buffer_hits: 310, buffer_reads: 4, executed_at: '2026-11-19 13:30:16 UTC' }
      ]
    },
    database_metrics: {
      table_name: 'database_metrics',
      description: 'System-level telemetry metrics from pg_stat_database, connection pools, and cache ratios.',
      row_count: 5400,
      columns: [
        { name: 'id', type: 'bigserial', pk: true, nullable: false, defaultVal: "nextval('database_metrics_id_seq'::regclass)" },
        { name: 'metric_name', type: 'character varying(100)', nullable: false, defaultVal: "''" },
        { name: 'metric_value', type: 'numeric(12,4)', nullable: false, defaultVal: '0.0000' },
        { name: 'recorded_at', type: 'timestamp with time zone', nullable: false, defaultVal: 'now()' }
      ],
      indexes: [
        { name: 'database_metrics_pkey', method: 'btree', columns: ['id'], isUnique: true, isPk: true },
        { name: 'idx_metrics_name_time', method: 'btree', columns: ['metric_name', 'recorded_at DESC'], isUnique: false, isPk: false }
      ],
      sampleRows: [
        { id: 1, metric_name: 'cache_hit_ratio', metric_value: '99.4200', recorded_at: '2026-11-19 14:30:00 UTC' },
        { id: 2, metric_name: 'active_connections', metric_value: '24.0000', recorded_at: '2026-11-19 14:30:00 UTC' },
        { id: 3, metric_name: 'transactions_per_sec', metric_value: '342.1500', recorded_at: '2026-11-19 14:30:00 UTC' },
        { id: 4, metric_name: 'disk_read_kb_per_sec', metric_value: '184.2000', recorded_at: '2026-11-19 14:30:00 UTC' }
      ]
    }
  };

  let tableCatalog = { ...DEFAULT_TABLE_METADATA };
  let selectedTableName = 'queries';
  let activeTab = 'tabColumns';
  let activeCatalogTab = 'catalogTablesTab';
  let currentConnection = {
    name: 'Default PostgreSQL',
    type: 'PostgreSQL',
    host: 'localhost:5432',
    database: 'veil',
    status: 'Connected'
  };

  let onTableClickCallback = null;
  let onToastCallback = null;
  let onOpenInEditorCallback = null;

  function generateDdl(table) {
    const lines = [];
    lines.push(`-- DDL Export for table: ${table.table_name}`);
    lines.push(`-- Database: ${currentConnection.name} (${currentConnection.type})`);
    lines.push(`-- Generated: ${new Date().toISOString()}`);
    lines.push('');
    lines.push(`CREATE TABLE public.${table.table_name} (`);

    const colDefs = (table.columns || []).map((col) => {
      let def = `    ${col.name.padEnd(20, ' ')} ${col.type.toUpperCase()}`;
      if (!col.nullable) def += ' NOT NULL';
      if (col.defaultVal && col.defaultVal !== 'NULL') def += ` DEFAULT ${col.defaultVal}`;
      if (col.pk) def += ' PRIMARY KEY';
      return def;
    });

    lines.push(colDefs.join(',\n'));
    lines.push(');');
    lines.push('');

// Index definitions
    (table.indexes || []).forEach((idx) => {
      if (!idx.isPk) {
        const u = idx.isUnique ? 'UNIQUE ' : '';
        lines.push(`CREATE ${u}INDEX ${idx.name} ON public.${table.table_name} USING ${idx.method} (${idx.columns.join(', ')});`);
      }
    });

    return lines.join('\n');
  }

  function getTableDetails(name) {
    return tableCatalog[name] || {
      table_name: name,
      row_count: 0,
      columns: [
        { name: 'id', type: 'serial', pk: true, nullable: false, defaultVal: 'nextval()' },
        { name: 'name', type: 'varchar(255)', nullable: false, defaultVal: "''" },
        { name: 'created_at', type: 'timestamptz', nullable: false, defaultVal: 'now()' }
      ],
      indexes: [
        { name: `${name}_pkey`, method: 'btree', columns: ['id'], isUnique: true, isPk: true }
      ],
      sampleRows: []
    };
  }

  return {
    init({ onTableClick, onToast, onOpenInEditor }) {
      onTableClickCallback = onTableClick;
      onToastCallback = onToast;
      onOpenInEditorCallback = onOpenInEditor;

// Catalog search input
      const searchInput = $('#schemaCatalogSearch');
      if (searchInput) {
        searchInput.addEventListener('input', (e) => {
          this.renderCatalog(e.target.value.trim());
        });
      }

// Catalog Switcher Tabs
      $$('.tab-btn[data-group="schemaCatalog"]').forEach((btn) => {
        btn.addEventListener('click', () => {
          activeCatalogTab = btn.dataset.target;
          $$('.tab-btn[data-group="schemaCatalog"]').forEach((b) => b.classList.toggle('active', b === btn));
          $$('.tab-pane[data-group="schemaCatalog"]').forEach((p) => p.classList.toggle('active', p.id === activeCatalogTab));
        });
      });

// Table Detail Tabs (Columns, Indexes, Data Preview, DDL)
      $$('.tab-btn[data-group="tableDetail"]').forEach((btn) => {
        btn.addEventListener('click', () => {
          activeTab = btn.dataset.target;
          $$('.tab-btn[data-group="tableDetail"]').forEach((b) => b.classList.toggle('active', b === btn));
          $$('.tab-pane[data-group="tableDetail"]').forEach((p) => p.classList.toggle('active', p.id === activeTab));
        });
      });

// Header button handlers
      $('#refreshSchemaBtn')?.addEventListener('click', () => {
        this.refresh();
        if (onToastCallback) onToastCallback(`Schema introspected for ${currentConnection.name}.`);
      });

// Table Detail Actions
      $('#copyTableNameBtn')?.addEventListener('click', () => {
        navigator.clipboard.writeText(selectedTableName);
        if (onToastCallback) onToastCallback(`Table name "${selectedTableName}" copied.`);
      });

      $('#copyDdlBtn')?.addEventListener('click', () => {
        const table = getTableDetails(selectedTableName);
        const ddl = generateDdl(table);
        navigator.clipboard.writeText(ddl);
        if (onToastCallback) onToastCallback(`DDL for "${selectedTableName}" copied.`);
      });

      $('#openInEditorBtn')?.addEventListener('click', () => {
        const sql = `SELECT * FROM ${selectedTableName} LIMIT 50;`;
        if (onOpenInEditorCallback) onOpenInEditor(sql);
        if (onToastCallback) onToastCallback(`Query loaded into editor.`);
      });

      this.render();
    },

    setConnection(conn) {
      if (!conn) return;
      const host = conn.host
        ? (conn.port ? `${conn.host}:${conn.port}` : conn.host)
        : 'localhost:5432';

      currentConnection = {
        name: conn.name || 'Default PostgreSQL',
        type: conn.type || 'PostgreSQL',
        host,
        database: conn.databaseName || conn.database || 'veil',
        status: conn.status || 'Connected'
      };

      const connText = $('#schemaConnStatusText');
      if (connText) {
        connText.textContent = `Connected • ${currentConnection.name} (${currentConnection.type})`;
      }

      this.renderHeaderStats();
    },

    setSchema(schema) {
      if (!schema) return;

      if (schema.database) {
        currentConnection.database = schema.database;
        const connText = $('#schemaConnStatusText');
        if (connText) {
          connText.textContent = `Connected • ${currentConnection.name} (${currentConnection.type})`;
        }
      }

// A real /api/database response carries the database name. Its data is authoritative, so replace the demo catalog instead of merging: switching connections must not leave another database's tables behind. (The offline fallback has no `database`, so it keeps the curated demo metadata.)
      const fromBackend = typeof schema.database === 'string'
        && schema.database.length > 0;

      if (fromBackend && Array.isArray(schema.tables) && schema.tables.length) {
        const columnsByTable = {};

        (schema.columns || []).forEach((col) => {
          const tableName = col.table_name || col.tableName;
          const columnName = col.column_name || col.name;
          if (!tableName || !columnName) return;

          (columnsByTable[tableName] = columnsByTable[tableName] || []).push({
            name: columnName,
            type: col.data_type || col.type || 'text',
            pk: false,
            nullable: String(col.is_nullable || '').toUpperCase() === 'YES',
            defaultVal: col.column_default || '',
          });
        });

        const indexesByTable = {};

        (schema.indexes || []).forEach((idx) => {
          const indexName = idx.indexname || idx.name;
          const tableName = idx.tablename || idx.table_name;
          if (!indexName || !tableName) return;

          const definition = idx.indexdef || idx.indexDefinition || '';
          const parsed = parseIndexDefinition(definition);
          const isPk = /_pkey$/i.test(indexName) || /PRIMARY KEY/i.test(definition);

          (indexesByTable[tableName] = indexesByTable[tableName] || []).push({
            name: indexName,
            method: parsed.method,
            columns: parsed.columns,
            isUnique: isPk || /_key$/i.test(indexName) || /UNIQUE\s+INDEX/i.test(definition),
            isPk,
          });
        });

        const rebuilt = {};

        schema.tables.forEach((t) => {
          const tableName = t.table_name || t.name;
          if (!tableName) return;

          const indexes = indexesByTable[tableName] || [];
          const columns = (columnsByTable[tableName] || []).map((col) => ({ ...col }));

// mark columns that lead a primary-key index
          const pkColumns = new Set();
          indexes.filter((idx) => idx.isPk).forEach((idx) => {
            idx.columns.forEach((column) => {
              const name = column.split(/\s+/)[0].replace(/"/g, '');
              pkColumns.add(name.toLowerCase());
            });
          });

          columns.forEach((col) => {
            col.pk = pkColumns.has(col.name.toLowerCase());
          });

          rebuilt[tableName] = {
            table_name: tableName,
            description: `Table in ${schema.database}.`,
            row_count: t.row_count || null,
            columns,
            indexes,
            sampleRows: [],
          };
        });

        tableCatalog = rebuilt;

        if (!tableCatalog[selectedTableName]) {
          selectedTableName = Object.keys(tableCatalog)[0] || '';
        }
      }

      this.render();
    },

    selectTable(tableName) {
      if (!tableCatalog[tableName]) {
        tableName = Object.keys(tableCatalog)[0] || 'queries';
      }
      selectedTableName = tableName;
      this.renderCatalog();
      this.renderDetail();
    },

    render(filter = '') {
      this.renderHeaderStats();
      this.renderCatalog(filter);
      this.renderDetail();
    },

    renderHeaderStats() {
      const tables = Object.values(tableCatalog);
      const totalTables = tables.length;
      let totalRows = 0;
      let totalIndexes = 0;

      tables.forEach((t) => {
        totalRows += (t.row_count || 0);
        totalIndexes += (t.indexes || []).length;
      });

      const tableCountEl = $('#schemaTableCount');
      const totalRowsEl = $('#schemaTotalRows');
      const indexCountEl = $('#schemaIndexCount');
      const catTablesCount = $('#catTablesCount');
      const catIndexesCount = $('#catIndexesCount');

      if (tableCountEl) tableCountEl.textContent = totalTables.toLocaleString();
      if (totalRowsEl) totalRowsEl.textContent = tables.some((t) => t.row_count)
        ? totalRows.toLocaleString()
        : '—';
      if (indexCountEl) indexCountEl.textContent = totalIndexes.toLocaleString();
      if (catTablesCount) catTablesCount.textContent = totalTables;
      if (catIndexesCount) catIndexesCount.textContent = totalIndexes;
    },

    renderCatalog(filter = '') {
      const f = filter.toLowerCase();
      const tablesUl = $('#schemaTablesUl');
      const indexesUl = $('#schemaIndexesUl');

// 1. Tables Catalog
      if (tablesUl) {
        const matchedTables = Object.values(tableCatalog).filter((t) => {
          if (!f) return true;
          if (t.table_name.toLowerCase().includes(f)) return true;
          return (t.columns || []).some((c) => c.name.toLowerCase().includes(f));
        });

        if (!matchedTables.length) {
          tablesUl.innerHTML = `<li style="padding:12px 8px; color:var(--color-text-muted); font-size:11.5px; text-align:center;">No tables match "${esc(filter)}"</li>`;
        } else {
          tablesUl.innerHTML = matchedTables.map((t) => {
            const isActive = t.table_name === selectedTableName ? ' active' : '';
            const rowCount = t.row_count
              ? t.row_count.toLocaleString() + ' rows'
              : (t.columns || []).length + ' cols';
            return `
              <li class="schema-table-item${isActive}" data-table="${esc(t.table_name)}" title="Inspect table: ${esc(t.table_name)}">
                <span class="schema-table-item-name">
                  <span class="tbl-ico">&#9638;</span>
                  <span>${esc(t.table_name)}</span>
                </span>
                <span class="schema-table-item-badge">${rowCount}</span>
              </li>`;
          }).join('');

          $$('#schemaTablesUl .schema-table-item').forEach((li) => {
            li.addEventListener('click', () => {
              this.selectTable(li.dataset.table);
            });
          });
        }
      }

// 2. All Indexes Catalog
      if (indexesUl) {
        const allIndexes = [];
        Object.values(tableCatalog).forEach((t) => {
          (t.indexes || []).forEach((idx) => {
            allIndexes.push({ ...idx, table_name: t.table_name });
          });
        });

        const matchedIndexes = allIndexes.filter((idx) => {
          if (!f) return true;
          return (idx.name + idx.table_name + (idx.columns || []).join(' ')).toLowerCase().includes(f);
        });

        if (!matchedIndexes.length) {
          indexesUl.innerHTML = `<li style="padding:12px 8px; color:var(--color-text-muted); font-size:11.5px; text-align:center;">No indexes found</li>`;
        } else {
          indexesUl.innerHTML = matchedIndexes.map((idx) => `
            <li class="schema-table-item" data-table="${esc(idx.table_name)}" title="Table: ${esc(idx.table_name)}">
              <span class="schema-table-item-name">
                <span style="color:var(--color-primary); font-size:12px;">&#9873;</span>
                <span>${esc(idx.name)}</span>
              </span>
              <span class="schema-table-item-badge">${esc(idx.table_name)}</span>
            </li>`).join('');

          $$('#schemaIndexesUl .schema-table-item').forEach((li) => {
            li.addEventListener('click', () => {
              this.selectTable(li.dataset.table);
// Switch to indexes tab in details
              const idxTabBtn = $('.tab-btn[data-target="tabIndexes"]');
              if (idxTabBtn) idxTabBtn.click();
            });
          });
        }
      }
    },

    renderDetail() {
      const table = getTableDetails(selectedTableName);

// Title & Badges
      const titleEl = $('#selectedTableName');
      const rowsEl = $('#selectedTableRows');
      const colCountEl = $('#tableColCount');
      const idxCountEl = $('#tableIdxCount');

      if (titleEl) titleEl.textContent = table.table_name;
      if (rowsEl) rowsEl.textContent = table.row_count ? table.row_count.toLocaleString() + ' rows' : '—';
      if (colCountEl) colCountEl.textContent = (table.columns || []).length;
      if (idxCountEl) idxCountEl.textContent = (table.indexes || []).length;

// 1. Columns Table
      const columnsTbody = $('#columnsTbody');
      if (columnsTbody) {
        columnsTbody.innerHTML = (table.columns || []).map((col) => {
          let keysBadge = '';
          if (col.pk) keysBadge += '<span class="col-pk-badge">PRIMARY KEY</span>';
          if (col.fk) keysBadge += `<span class="col-fk-badge" title="References ${esc(col.fk)}">FK &rarr; ${esc(col.fk)}</span>`;
          if (!keysBadge) keysBadge = '<span style="color:var(--color-text-muted);">&mdash;</span>';

          const nullBadge = col.nullable
            ? '<span style="color:var(--color-text-secondary);">NULL</span>'
            : '<span style="color:var(--color-text); font-weight:600;">NOT NULL</span>';

          return `
            <tr>
              <td>
                <b>${esc(col.name)}</b>
              </td>
              <td><span class="type-mono">${esc(col.type)}</span></td>
              <td>${keysBadge}</td>
              <td>${nullBadge}</td>
              <td><span class="default-mono">${esc(col.defaultVal || '&mdash;')}</span></td>
            </tr>`;
        }).join('');
      }

// 2. Indexes Table
      const indexesTbody = $('#indexesTbody');
      if (indexesTbody) {
        indexesTbody.innerHTML = (table.indexes || []).map((idx) => {
          const typeLabel = `<span class="type-mono">${esc(idx.method.toUpperCase())}</span>`;
          const colsLabel = `<span style="font-family:var(--font-mono); font-size:11.5px;">(${esc(idx.columns.join(', '))})</span>`;
          const uniqueLabel = idx.isUnique ? '<span class="col-pk-badge">UNIQUE</span>' : '<span style="color:var(--color-text-muted);">Non-unique</span>';
          const constraintLabel = idx.isPk ? '<span class="col-pk-badge">PRIMARY KEY</span>' : '<span style="color:var(--color-text-muted);">&mdash;</span>';

          return `
            <tr>
              <td><b>${esc(idx.name)}</b></td>
              <td>${typeLabel}</td>
              <td>${colsLabel}</td>
              <td>${uniqueLabel}</td>
              <td>${constraintLabel}</td>
            </tr>`;
        }).join('');
      }

// 3. Sample Data Preview
      const sampleThead = $('#sampleDataThead');
      const sampleTbody = $('#sampleDataTbody');
      const cols = table.columns || [];
      const rows = table.sampleRows || [];

      if (sampleThead) {
        sampleThead.innerHTML = `<tr>${cols.map((c) => `<th>${esc(c.name)}</th>`).join('')}</tr>`;
      }

      if (sampleTbody) {
        if (!rows.length) {
          sampleTbody.innerHTML = `<tr><td colspan="${cols.length}" style="text-align:center; padding:18px; color:var(--color-text-muted);">No sample data available for table "${esc(table.table_name)}".</td></tr>`;
        } else {
          sampleTbody.innerHTML = rows.map((r) => `
            <tr>
              ${cols.map((c) => {
                const val = r[c.name] !== undefined ? r[c.name] : '&mdash;';
                return `<td style="font-family:var(--font-mono); font-size:11.5px;">${esc(val)}</td>`;
              }).join('')}
            </tr>`).join('');
        }
      }

// 4. DDL Source
      const ddlCodeEl = $('#tableDdlCode');
      if (ddlCodeEl) {
        ddlCodeEl.textContent = generateDdl(table);
      }
    },

    refresh() {
      this.render();
    },

    getSchemaCache() {
      const tables = Object.values(tableCatalog).map((t) => ({
        table_name: t.table_name,
        row_count: t.row_count,
        columns: (t.columns || []).map((c) => c.name)
      }));

      const indexes = [];
      Object.values(tableCatalog).forEach((t) => {
        (t.indexes || []).forEach((idx) => {
          indexes.push({
            indexname: idx.name,
            tablename: t.table_name,
            columns: idx.columns
          });
        });
      });

      return { tables, indexes };
    }
  };
})();
