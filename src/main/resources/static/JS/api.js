// VEIL — Frontend API Client Module Connects directly to the Java backend (Spring Boot). The UI modules were written against a flat "viewer" shape, while the Java API returns its own DTOs (AnalyzeResponse, PlanSummary, ...). This module is the single translation layer between the two, so the UI files never have to know about backend field names.

window.VEILApi = (() => {
// Configurable Java Backend URL (defaults to same-origin, or 8080 if running on 5500)
  let baseUrl = localStorage.getItem('veil_java_backend_url') || '';
  if (!baseUrl && window.location.port === '5500') {
    baseUrl = 'http://localhost:8080';
  }

  const NOT_AVAILABLE = 'NotAvailable';

  const readError = async (res) => {
    try {
      const body = await res.json();
      return body.error || body.message || `HTTP ${res.status}`;
    } catch {
      return `HTTP ${res.status} ${res.statusText}`;
    }
  };

  const firstKeyword = (sql) => {
    const match = String(sql || '').trim().match(/^([a-zA-Z]+)/);
    return match ? match[1].toUpperCase() : 'SELECT';
  };

  const toNumber = (value, fallback = 0) => {
    const number = typeof value === 'number' ? value : Number(value);
    return Number.isFinite(number) ? number : fallback;
  };

// Highlight a plan node in the tree (bad = expensive, good = index/limit)
  const nodeKind = (type) => {
    if (/seq scan|bitmap heap scan/i.test(type)) return 'bad';
    if (/sort|hash|aggregate|nested loop/i.test(type)) return 'warn';
    if (/index|limit/i.test(type)) return 'good';
    return 'info';
  };

// Build the one-line node caption from the fields PostgreSQL actually gives us
  const planLabel = (node) => {
    const relation = node.relation && node.relation !== NOT_AVAILABLE ? node.relation : '';
    const index = node.index && node.index !== NOT_AVAILABLE ? node.index : '';

    if (relation && index) return `on ${relation} using ${index}`;
    if (relation) return `on ${relation}`;
    if (index) return `using ${index}`;
    if (node.sortKey) return `by ${node.sortKey}`;
    return '';
  };

// PlanSummary (camelCase, no ids) -> the flat node shape planViewer.js renders
  const normalizePlanNode = (node, counter) => {
    const type = node.nodeType || node.type || 'Unknown';

    return {
      ...node,
      id: counter.next++,
      type,
      label: node.label || planLabel(node),
      startup: toNumber(node.startupCost ?? node.startup),
      total: toNumber(node.totalCost ?? node.total),
      rows: toNumber(node.estimatedRows ?? node.rows),
      kind: node.kind || nodeKind(type),
      children: (node.children || []).map((child) => normalizePlanNode(child, counter)),
    };
  };

  const normalizePlan = (plan) => (plan ? normalizePlanNode(plan, { next: 1 }) : null);

// AnalyzeResponse -> the fields the Query Information panel reads
  const normalizeAnalysis = (result, sql) => {
    if (!result) return result;

    const table = result.table && result.table !== NOT_AVAILABLE ? result.table : '';

    return {
      ...result,
      type: result.type || firstKeyword(sql),
      tables: result.tables || (table ? [table] : []),
      where: result.where || result.whereCondition || '',
    };
  };

  const collectPlanNotes = (node, notes = []) => {
    if (!node) return notes;

    (node.recommendations || []).forEach((note) => {
      if (note && !notes.includes(note)) notes.push(note);
    });
    (node.children || []).forEach((child) => collectPlanNotes(child, notes));

    return notes;
  };

// Java findings -> the {findings, summary, bottlenecks} shape the UI expects
  const backendRecommendations = (analysis, plan) => {
    let findings = (analysis?.findings || []).map((finding) => ({
      title: finding.title || 'Optimization finding',
      message: finding.message || '',
      severity: ['high', 'medium', 'low'].includes(String(finding.severity).toLowerCase())
        ? String(finding.severity).toLowerCase()
        : 'low',
      suggestionSql: finding.suggestionSql || '',
    }));

// Older responses only carried plain recommendation strings
    if (!findings.length && Array.isArray(analysis?.recommendations)) {
      findings = analysis.recommendations
        .filter((message) => message && !/already indexed/i.test(message))
        .map((message) => ({
          title: 'Query observation',
          message,
          severity: 'low',
          suggestionSql: '',
        }));
    }

    return {
      source: 'java_backend',
      findings,
      summary: plan?.planSummary
        || (analysis?.ast ? `Parsed query: ${analysis.ast}` : 'Analysis completed by the VEIL backend.'),
      bottlenecks: findings.filter((f) => f.severity !== 'low').map((f) => f.title),
      planNotes: collectPlanNotes(plan),
// the backend does not predict speedups - never invent one
      estimatedImprovement: '',
    };
  };

  const API = {
    getBaseUrl() {
      return baseUrl;
    },
    setBaseUrl(url) {
      baseUrl = (url || '').replace(/\/$/, '');
      localStorage.setItem('veil_java_backend_url', baseUrl);
    },

    async get(path) {
      const url = `${baseUrl}${path}`;
      const res = await fetch(url, { headers: { Accept: 'application/json' } });
      if (!res.ok) throw new Error(await readError(res));
      return res.json();
    },

    async post(path, body) {
      const url = `${baseUrl}${path}`;
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      if (!res.ok) throw new Error(await readError(res));
      return res.json();
    },

    async del(path) {
      const url = `${baseUrl}${path}`;
      const res = await fetch(url, { method: 'DELETE' });
      if (!res.ok) throw new Error(await readError(res));
      return res.json();
    },

// API Methods for the Java Backend
    getHealth() {
      return this.get('/api/database/health');
    },

    getConnections() {
      return this.get('/api/connections');
    },

    addConnection(connection) {
      return this.post('/api/connections', connection);
    },

    deleteConnection(id) {
      return this.del(`/api/connections/${id}`);
    },

// List the databases running on a host (uses the password once).
    discoverDatabases({ host, port, username, password }) {
      return this.post('/api/connections/discover', {
        host,
        port,
        username,
        password,
      });
    },

// Make a saved connection the active database for schema/EXPLAIN.
    activateConnection(id, password) {
      return this.post(`/api/connections/${id}/activate`, { password });
    },

    deactivateConnection() {
      return this.post('/api/connections/deactivate', {});
    },

    getActiveConnection() {
      return this.get('/api/connections/active');
    },

    getSchema() {
      return this.get('/api/database');
    },

// NVIDIA NIM runs on the backend, so the key never reaches the page.
    getAiStatus() {
      return this.get('/api/ai/status');
    },

    getAiRecommendations({ sql, plan, schema, apiKey, model }) {
      const body = { sql, plan, schema };
      if (apiKey) body.apiKey = apiKey;
      if (model) body.model = model;
      return this.post('/api/ai/recommend', body);
    },

    async chatWithAi({ message, history, sql, plan, schema, apiKey, model }) {
      const body = { message, history, sql, plan, schema };
      if (apiKey) body.apiKey = apiKey;
      if (model) body.model = model;
      const result = await this.post('/api/ai/chat', body);
      return result.reply || 'No response generated.';
    },

    async analyze(sql) {
      const result = await this.post('/api/analyze', { sql });
      return normalizeAnalysis(result, sql);
    },

    async explain(sql) {
      const result = await this.post('/api/explain', { sql });
      return { sql: result.sql ?? sql, plan: normalizePlan(result.plan) };
    },

// Recommendations: NVIDIA NIM (through the backend) by default, then a direct Gemini/OpenAI key if one was configured, then the Java analysis.
    async getRecommendations(sql, plan, schema, analysis) {
      const cfg = window.VEILAIEngine ? window.VEILAIEngine.getConfig() : null;
      const provider = (cfg?.provider || 'nvidia').toLowerCase();
      const usesNvidia = provider === 'nvidia' || provider === 'nim' || provider === 'nvidia-nim';

      if (usesNvidia) {
        try {
          const result = await this.getAiRecommendations({
            sql,
            plan,
            schema,
            apiKey: (cfg?.apiKey || '').trim(),
            model: cfg?.model,
          });
          return {
            ...result,
            source: result.source || 'nvidia_nim',
            notice: result.notice || '',
          };
        } catch (error) {
// Keep the page useful and honest: real backend findings, no numbers.
          if (analysis) {
            const fallback = backendRecommendations(analysis, plan);
            fallback.notice = `NVIDIA NIM unavailable (${error.message}). Showing the VEIL backend analysis.`;
            return fallback;
          }

          const fallback = window.VEILAIEngine
            ? await window.VEILAIEngine.analyze(sql, plan, schema)
            : null;
          if (fallback) {
            fallback.notice = `NVIDIA NIM unavailable (${error.message}). Showing rule-based results.`;
            return fallback;
          }
          throw error;
        }
      }

      if (cfg && (cfg.apiKey || '').trim()) {
        return window.VEILAIEngine.analyze(sql, plan, schema);
      }

      if (analysis) {
        return backendRecommendations(analysis, plan);
      }

      try {
        const fresh = await this.analyze(sql);
        return backendRecommendations(fresh, plan);
      } catch (error) {
        if (window.VEILAIEngine) {
          const fallback = await window.VEILAIEngine.analyze(sql, plan, schema);
          fallback.notice = `Java backend unavailable (${error.message}). Showing rule-based results.`;
          return fallback;
        }
        throw error;
      }
    }
  };

  return API;
})();
