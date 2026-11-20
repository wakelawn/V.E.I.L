/**
 * VEIL — Database Connection Utility for Vercel Serverless
 * Manages pooled connections to PostgreSQL (Vercel Postgres, Neon, Supabase, RDS, etc.)
 */

let Pool = null;
try {
  Pool = require('pg').Pool;
} catch (e) {
  // pg module will be installed by Vercel during build/deploy
}

let globalPool = null;

function getConnectionString(reqBody = {}) {
  // 1. Connection string passed in request
  if (reqBody.connectionString) return reqBody.connectionString;

  // 2. Individual fields passed in request (from Add Connection modal)
  if (reqBody.host && reqBody.database && reqBody.username) {
    const port = reqBody.port || (reqBody.host.includes(':') ? reqBody.host.split(':')[1] : '5432');
    const host = reqBody.host.split(':')[0];
    const password = reqBody.password ? `:${encodeURIComponent(reqBody.password)}` : '';
    return `postgres://${encodeURIComponent(reqBody.username)}${password}@${host}:${port}/${reqBody.database}`;
  }

  // 3. Vercel & Cloud Environment Variables
  return (
    process.env.POSTGRES_URL ||
    process.env.DATABASE_URL ||
    process.env.POSTGRES_PRISMA_URL ||
    process.env.POSTGRES_URL_NON_POOLING ||
    null
  );
}

function getPool(connectionString) {
  if (!Pool) return null;
  const cs = connectionString || getConnectionString();
  if (!cs) return null;

  // Return existing pool if same connection string
  if (globalPool && globalPool._connectionString === cs) {
    return globalPool;
  }

  const isLocal = cs.includes('localhost') || cs.includes('127.0.0.1');

  globalPool = new Pool({
    connectionString: cs,
    ssl: isLocal ? false : { rejectUnauthorized: false },
    max: 10,
    idleTimeoutMillis: 30000,
    connectionTimeoutMillis: 5000,
  });

  globalPool._connectionString = cs;

  globalPool.on('error', (err) => {
    console.error('[VEIL DB Pool Error]:', err.message);
  });

  return globalPool;
}

async function query(sql, params = [], connDetails = {}) {
  const cs = getConnectionString(connDetails);
  const pool = getPool(cs);

  if (!pool) {
    const error = new Error('No PostgreSQL connection configured. Please set DATABASE_URL in Vercel environment variables or add a connection in VEIL.');
    error.code = 'NO_DB_CONFIG';
    throw error;
  }

  const client = await pool.connect();
  try {
    return await client.query(sql, params);
  } finally {
    client.release();
  }
}

module.exports = {
  getPool,
  query,
  getConnectionString
};
