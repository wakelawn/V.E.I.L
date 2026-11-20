/**
 * GET & POST /api/connections
 * Returns active connection list or tests/saves a new database connection.
 */

const db = require('./lib/db');

// In-memory cache for serverless invocation context
let activeConnections = [
  {
    id: 'default',
    name: 'Default PostgreSQL',
    type: 'PostgreSQL',
    host: 'cloud-postgres',
    database: 'veil',
    isDefault: true
  }
];

module.exports = async function handler(req, res) {
  res.setHeader('Content-Type', 'application/json');

  if (req.method === 'GET') {
    const cs = db.getConnectionString();
    if (cs) {
      try {
        const url = new URL(cs.replace(/^postgres:\/\//i, 'http://'));
        activeConnections[0].host = `${url.hostname}:${url.port || 5432}`;
        activeConnections[0].database = url.pathname.replace(/^\//, '') || 'veil';
        activeConnections[0].username = url.username || 'postgres';
      } catch {}
    }
    return res.status(200).json(activeConnections);
  }

  if (req.method === 'POST') {
    const body = req.body || {};
    const { name, host, database, username, password } = body;

    if (!name) {
      return res.status(400).json({ error: 'Connection name is required.' });
    }

    // Attempt test query if credentials provided
    let testSuccess = false;
    let testMessage = 'Saved without live verification.';

    if (host && database) {
      try {
        await db.query('SELECT 1;', [], { host, database, username, password });
        testSuccess = true;
        testMessage = 'Connection successfully verified with live PostgreSQL server.';
      } catch (err) {
        testMessage = `Credentials saved, but verification failed: ${err.message}`;
      }
    }

    const connRecord = {
      id: `conn_${Date.now()}`,
      name,
      type: 'PostgreSQL',
      host: host || 'localhost:5432',
      database: database || 'veil',
      username: username || 'postgres',
      verified: testSuccess,
      message: testMessage
    };

    activeConnections.push(connRecord);

    return res.status(201).json(connRecord);
  }

  return res.status(405).json({ error: 'Method not allowed' });
};
