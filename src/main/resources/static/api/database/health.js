/**
 * GET /api/database/health
 * Returns database connectivity health, PostgreSQL version, and latency.
 */

const db = require('../lib/db');

module.exports = async function handler(req, res) {
  res.setHeader('Content-Type', 'application/json');

  const start = Date.now();
  try {
    const result = await db.query('SELECT version();');
    const latencyMs = Date.now() - start;
    const versionStr = result.rows[0]?.version || 'PostgreSQL';

    return res.status(200).json({
      status: 'UP',
      healthy: true,
      database: 'PostgreSQL',
      version: versionStr.split(',')[0],
      latencyMs,
      timestamp: new Date().toISOString()
    });
  } catch (err) {
    const latencyMs = Date.now() - start;
    const isNoConfig = err.code === 'NO_DB_CONFIG';

    return res.status(200).json({
      status: isNoConfig ? 'DEMO' : 'DOWN',
      healthy: false,
      database: 'PostgreSQL',
      latencyMs,
      message: err.message,
      notice: isNoConfig
        ? 'No DATABASE_URL configured in Vercel environment variables. Operating in demo mode.'
        : `Database connection failed: ${err.message}`,
      timestamp: new Date().toISOString()
    });
  }
};
