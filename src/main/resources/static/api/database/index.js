/**
 * GET /api/database
 * Returns tables, indexes, and schema catalog from live PostgreSQL or demo fallback.
 */

const db = require('../lib/db');

module.exports = async function handler(req, res) {
  res.setHeader('Content-Type', 'application/json');

  try {
    const [tablesRes, indexesRes, statsRes] = await Promise.all([
      db.query(`
        SELECT table_name, table_type 
        FROM information_schema.tables 
        WHERE table_schema = 'public' 
        ORDER BY table_name;
      `),
      db.query(`
        SELECT indexname, tablename, indexdef 
        FROM pg_indexes 
        WHERE schemaname = 'public' 
        ORDER BY tablename, indexname;
      `),
      db.query(`
        SELECT relname AS table_name, n_live_tup AS row_count 
        FROM pg_stat_user_tables;
      `).catch(() => ({ rows: [] }))
    ]);

    const rowCountMap = {};
    for (const r of statsRes.rows) {
      rowCountMap[r.table_name] = parseInt(r.row_count, 10) || 0;
    }

    const tables = tablesRes.rows.map((t) => ({
      table_name: t.table_name,
      row_count: rowCountMap[t.table_name] || 0
    }));

    const indexes = indexesRes.rows.map((idx) => ({
      indexname: idx.indexname,
      tablename: idx.tablename,
      definition: idx.indexdef
    }));

    return res.status(200).json({
      database: 'PostgreSQL',
      live: true,
      tables,
      indexes
    });
  } catch (err) {
    // Return standard demo schema if database is not configured
    return res.status(200).json({
      database: 'PostgreSQL (Demo)',
      live: false,
      notice: err.code === 'NO_DB_CONFIG' ? 'Operating in standalone demo mode' : err.message,
      tables: [
        { table_name: 'queries', row_count: 14200 },
        { table_name: 'projects', row_count: 350 },
        { table_name: 'users', row_count: 1200 },
        { table_name: 'query_executions', row_count: 85000 },
        { table_name: 'database_metrics', row_count: 5400 }
      ],
      indexes: [
        { indexname: 'projects_pkey', tablename: 'projects' },
        { indexname: 'queries_pkey', tablename: 'queries' },
        { indexname: 'users_pkey', tablename: 'users' },
        { indexname: 'idx_queries_project_id', tablename: 'queries' },
        { indexname: 'idx_users_email', tablename: 'users' }
      ]
    });
  }
};
