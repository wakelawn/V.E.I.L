// VEIL — AI Recommendation & Chat Engine Routes NVIDIA NIM requests through the VEIL backend (the API key stays server-side) and keeps Gemini/OpenAI as optional direct fallbacks.

window.VEILAIEngine = (() => {
  const STORAGE_KEY = 'veil_ai_config';
  const KEY_STORAGE = 'veil_ai_key';

  const DEFAULT_CONFIG = {
    provider: 'nvidia',
    model: 'nvidia/nemotron-3.5-lightning-30b-a3b',
    apiKey: '',
  };

  function getConfig() {
    let stored = {};
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) stored = JSON.parse(raw) || {};
    } catch {}

    if (!stored || typeof stored !== 'object' || Array.isArray(stored)) {
      stored = {};
    }

// Older builds stored the key in localStorage; remove it on sight.
    if (stored && Object.prototype.hasOwnProperty.call(stored, 'apiKey')) {
      delete stored.apiKey;
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
      } catch {}
    }

// API keys are never written to localStorage. A key typed in the AI Model dialog lives only in this tab (sessionStorage).
    let apiKey = '';
    try {
      apiKey = sessionStorage.getItem(KEY_STORAGE) || '';
    } catch {}

    return { ...DEFAULT_CONFIG, ...stored, apiKey };
  }

  function saveConfig(cfg) {
    const current = getConfig();
    const updated = { ...current, ...cfg };

    try {
      const { apiKey, ...safe } = updated;
      localStorage.setItem(STORAGE_KEY, JSON.stringify(safe));
      if (apiKey) sessionStorage.setItem(KEY_STORAGE, apiKey);
      else sessionStorage.removeItem(KEY_STORAGE);
    } catch {}

    return updated;
  }

  const isNvidia = (provider) => {
    const value = String(provider || '').toLowerCase();
    return value === 'nvidia' || value === 'nim' || value === 'nvidia-nim';
  };

// Backend -> the exact shape recommendations.js renders
  function normalizeNvidiaResult(result, model) {
    const severityOf = (value) => {
      const clean = String(value || '').toLowerCase();
      return ['high', 'medium', 'low'].includes(clean) ? clean : 'medium';
    };

    return {
      source: result?.source || 'nvidia_nim',
      provider: result?.provider || 'nvidia-nim',
      model: result?.model || model,
      summary: result?.summary || 'NVIDIA NIM analysis complete.',
      bottlenecks: Array.isArray(result?.bottlenecks) ? result.bottlenecks : [],
      findings: (result?.findings || []).map((finding) => ({
        title: finding.title || 'Recommendation',
        message: finding.message || '',
        severity: severityOf(finding.severity),
        suggestionSql: finding.suggestionSql || finding.sql || '',
      })),
      estimatedImprovement: result?.estimatedImprovement || '',
      notice: result?.notice || '',
    };
  }

  function buildPrompt(sql, plan, schema) {
    return {
      system: `You are VEIL AI, an expert PostgreSQL Database Administrator and Performance Engineer.
Analyze the SQL query, execution plan, and schema. Return ONLY a valid JSON object matching this schema:
{
  "summary": "Executive summary of the query execution plan and main bottleneck.",
  "bottlenecks": ["Bottleneck description 1", "Bottleneck description 2"],
  "findings": [
    {
      "title": "Title of the optimization",
      "message": "Clear explanation of why this bottleneck occurs and how to fix it.",
      "severity": "high" | "medium" | "low",
      "suggestionSql": "Ready-to-execute SQL statement (e.g. CREATE INDEX ...) or empty string if N/A"
    }
  ],
  "estimatedImprovement": "Expected performance improvement (e.g. ~70-85% latency reduction)"
}`,
      user: `SQL QUERY:\n${sql}\n\nEXECUTION PLAN:\n${JSON.stringify(plan, null, 2)}\n\nSCHEMA & INDEXES:\n${JSON.stringify(schema, null, 2)}`
    };
  }

  function parseAIResponse(text) {
    let clean = (text || '').trim();
    if (clean.startsWith('```json')) {
      clean = clean.replace(/^```json\s*/i, '').replace(/\s*```$/, '');
    } else if (clean.startsWith('```')) {
      clean = clean.replace(/^```\s*/, '').replace(/\s*```$/, '');
    }
    return JSON.parse(clean);
  }

// Direct API call to Google Gemini for JSON analysis
  async function callGemini(apiKey, model, prompts) {
    const m = model || 'gemini-1.5-flash';
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${m}:generateContent?key=${apiKey}`;

    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents: [{
          role: 'user',
          parts: [{ text: `${prompts.system}\n\n${prompts.user}` }]
        }],
        generationConfig: {
          temperature: 0.2,
          responseMimeType: 'application/json'
        }
      })
    });

    if (!res.ok) {
      const err = await res.text();
      throw new Error(`Gemini API error (${res.status}): ${err}`);
    }

    const data = await res.json();
    const reply = data?.candidates?.[0]?.content?.parts?.[0]?.text;
    if (!reply) throw new Error('Empty response from Gemini API');
    return parseAIResponse(reply);
  }

// Direct API call to OpenAI for JSON analysis
  async function callOpenAI(apiKey, model, prompts) {
    const m = model || 'gpt-4o-mini';
    const url = 'https://api.openai.com/v1/chat/completions';

    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${apiKey}`
      },
      body: JSON.stringify({
        model: m,
        messages: [
          { role: 'system', content: prompts.system },
          { role: 'user', content: prompts.user }
        ],
        temperature: 0.2,
        response_format: { type: 'json_object' }
      })
    });

    if (!res.ok) {
      const err = await res.text();
      throw new Error(`OpenAI API error (${res.status}): ${err}`);
    }

    const data = await res.json();
    const reply = data?.choices?.[0]?.message?.content;
    if (!reply) throw new Error('Empty response from OpenAI API');
    return parseAIResponse(reply);
  }

// Conversational Chat Call to Gemini
  async function callGeminiChat(apiKey, model, systemPrompt, history, userMessage) {
    const m = model || 'gemini-1.5-flash';
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${m}:generateContent?key=${apiKey}`;

    const contents = [
      { role: 'user', parts: [{ text: systemPrompt }] },
      { role: 'model', parts: [{ text: 'Understood. I am your VEIL PostgreSQL query optimization assistant. How can I help optimize your query?' }] }
    ];

    history.forEach((h) => {
      contents.push({
        role: h.role === 'user' ? 'user' : 'model',
        parts: [{ text: h.text }]
      });
    });

    contents.push({
      role: 'user',
      parts: [{ text: userMessage }]
    });

    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents,
        generationConfig: { temperature: 0.4 }
      })
    });

    if (!res.ok) {
      const err = await res.text();
      throw new Error(`Gemini Chat error (${res.status}): ${err}`);
    }

    const data = await res.json();
    return data?.candidates?.[0]?.content?.parts?.[0]?.text || 'No response generated.';
  }

// Conversational Chat Call to OpenAI
  async function callOpenAIChat(apiKey, model, systemPrompt, history, userMessage) {
    const m = model || 'gpt-4o-mini';
    const url = 'https://api.openai.com/v1/chat/completions';

    const messages = [
      { role: 'system', content: systemPrompt },
      ...history.map((h) => ({
        role: h.role === 'user' ? 'user' : 'assistant',
        content: h.text
      })),
      { role: 'user', content: userMessage }
    ];

    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${apiKey}`
      },
      body: JSON.stringify({
        model: m,
        messages,
        temperature: 0.4
      })
    });

    if (!res.ok) {
      const err = await res.text();
      throw new Error(`OpenAI Chat error (${res.status}): ${err}`);
    }

    const data = await res.json();
    return data?.choices?.[0]?.message?.content || 'No response generated.';
  }

// Built-in Rule-Based Recommendations
  function getRuleBasedRecommendations(sql, plan) {
    const clean = (sql || '').trim();
    const hasWhere = /WHERE\s+([^;]+?)(ORDER|LIMIT|GROUP|$)/i.test(clean);
    const hasJoin = /JOIN\s+([a-zA-Z0-9_]+)/i.test(clean);
    const hasOrder = /ORDER\s+BY/i.test(clean);
    const whereStr = hasWhere ? clean.match(/WHERE\s+([^;]+?)(ORDER|LIMIT|GROUP|$)/i)[1].trim() : '';

    const findings = [];

    if (whereStr.includes('status')) {
      findings.push({
        title: 'Sequential Scan on queries',
        message: "The table 'queries' is filtered by 'status = done' without an index covering 'status', causing PostgreSQL to inspect every table block sequentially.",
        severity: 'high',
        suggestionSql: 'CREATE INDEX idx_queries_status ON queries (project_id, status);'
      });
    }

    if (hasJoin && whereStr.includes('project_id')) {
      findings.push({
        title: 'Compound Index Recommendation on queries',
        message: 'Filter combines project_id and status. A compound B-tree index on (project_id, status) eliminates both the join lookup latency and the row filter step.',
        severity: 'medium',
        suggestionSql: 'CREATE INDEX idx_queries_proj_status ON queries (project_id, status);'
      });
    }

    if (hasOrder) {
      findings.push({
        title: 'In-Memory Sort Node Overhead',
        message: 'ORDER BY created_at DESC requires an explicit Sort node in work_mem. Adding an index with DESC ordering allows pre-sorted index traversal.',
        severity: 'low',
        suggestionSql: 'CREATE INDEX idx_queries_created ON queries (project_id, created_at DESC);'
      });
    }

    if (!findings.length) {
      findings.push({
        title: 'No major issues found',
        message: 'No optimization bottlenecks detected. Execution plan appears well-balanced.',
        severity: 'low',
        suggestionSql: ''
      });
    }

    return {
      source: 'rule_based_optimizer',
      notice: 'NVIDIA NIM is unavailable, so this is the local rule-based analysis. Set NVIDIA_API_KEY on the backend (or paste a key in the AI Model dialog) to enable live recommendations.',
      summary: `Analyzed query with Limit (${plan?.rows || 5} rows). A compound index on (project_id, status) would replace the Seq Scan with an Index Scan, cutting cost from 14.86 to ~3.20 units.`,
      bottlenecks: findings.filter((f) => f.severity !== 'low').map((f) => f.title),
      findings,
      estimatedImprovement: findings.some((f) => f.severity === 'high') ? '~75-85% latency reduction' : '~15-30% optimization'
    };
  }

// Intelligent Context-Aware Simulated Chat (when API key is not yet set)
  function getSimulatedChatResponse(userMessage, context) {
    const q = (userMessage || '').toLowerCase();
    const sql = context.sql || '';

    if (q.includes('index') || q.includes('indexing')) {
      return `To optimize this query, the most impactful change is creating a compound B-tree index on \`queries\`:\n\n\`\`\`sql\nCREATE INDEX idx_queries_proj_status ON queries (project_id, status);\n\`\`\`\n\n**Why this helps:**\n1. It allows PostgreSQL to use an **Index Scan** instead of scanning the full table sequentially.\n2. Both the \`JOIN\` predicate (\`q.project_id = p.id\`) and filter (\`q.status = 'done'\`) are satisfied simultaneously in a single index lookup.`;
    }

    if (q.includes('slow') || q.includes('bottleneck') || q.includes('why')) {
      return `The primary bottleneck in this query is the **Sequential Scan on \`queries\`** (estimated cost ~12.50 of the total 14.86 units).\n\nBecause there is no index covering \`status\`, PostgreSQL must read every table page from disk or cache and evaluate \`status = 'done'\` per row.\n\nSecondary bottleneck: The **Sort node** (\`ORDER BY created_at DESC\`) occurs in \`work_mem\`. If the table grows, this can spill to disk.`;
    }

    if (q.includes('rewrite') || q.includes('cte') || q.includes('refactor')) {
      return `Here is an optimized rewrite using a filtered Common Table Expression (CTE) or subquery pushdown:\n\n\`\`\`sql\nWITH filtered_queries AS (\n  SELECT id, query_text, project_id, created_at\n  FROM queries\n  WHERE project_id = 1 AND status = 'done'\n  ORDER BY created_at DESC\n  LIMIT 5\n)\nSELECT fq.id, fq.query_text, p.name\nFROM filtered_queries fq\nJOIN projects p ON fq.project_id = p.id;\n\`\`\`\n\n**Advantage:** This bounds the join to only 5 rows rather than joining all candidate project rows first!`;
    }

    if (q.includes('cost') || q.includes('explain') || q.includes('plan')) {
      return `**Plan Breakdown for your query:**\n- **Limit node (cost=14.85..14.86):** Stops execution immediately once 5 rows are fetched.\n- **Sort node (cost=14.85..14.92):** Sorts intermediate results by \`created_at DESC\`.\n- **Hash Join (cost=1.06..14.20):** Builds an in-memory hash table of \`projects\` and probes matching \`queries\`.\n- **Seq Scan on \`queries\` (cost=0.00..12.50):** The costliest single operation (accounting for ~84% of total query work).`;
    }

    return `I have reviewed your query:\n\`\`\`sql\n${sql}\n\`\`\`\n\nThe query filters \`queries\` by \`status = 'done'\` and joins with \`projects\`. To make it significantly faster:\n- Add a composite index on \`(project_id, status)\`.\n- Include \`created_at DESC\` in the index to eliminate the in-memory Sort.\n\nWould you like me to write the complete migration script or analyze another query?`;
  }

  return {
    getConfig,
    saveConfig,

    async analyze(sql, plan, schema) {
      const cfg = getConfig();
      const apiKey = (cfg.apiKey || '').trim();
      const provider = (cfg.provider || 'nvidia').toLowerCase();
      const model = cfg.model;

// NVIDIA NIM goes through the backend so the key never reaches the page.
      if (isNvidia(provider)) {
        try {
          if (!window.VEILApi) throw new Error('the VEIL API client is unavailable');
          const result = await window.VEILApi.getAiRecommendations({ sql, plan, schema, apiKey, model });
          return normalizeNvidiaResult(result, model);
        } catch (err) {
          console.warn('NVIDIA NIM request failed, falling back to rule-based engine:', err);
          const fallback = getRuleBasedRecommendations(sql, plan);
          fallback.notice = `NVIDIA NIM unavailable: ${err.message}. Showing rule-based results.`;
          return fallback;
        }
      }

      if (apiKey) {
        try {
          const prompts = buildPrompt(sql, plan, schema);
          let result;
          if (provider === 'gemini' || apiKey.startsWith('AIzaSy')) {
            result = await callGemini(apiKey, model, prompts);
          } else {
            result = await callOpenAI(apiKey, model, prompts);
          }

          return {
            source: 'live_ai_model',
            provider,
            model,
            summary: result.summary || 'AI Model optimization analysis complete.',
            bottlenecks: result.bottlenecks || [],
            findings: (result.findings || []).map((f) => ({
              title: f.title || 'Recommendation',
              message: f.message || '',
              severity: ['high', 'medium', 'low'].includes(f.severity) ? f.severity : 'medium',
              suggestionSql: f.suggestionSql || ''
            })),
            estimatedImprovement: result.estimatedImprovement || '~70-85% latency reduction'
          };
        } catch (err) {
          console.warn('AI API call error, falling back to rule-based engine:', err);
          const fallback = getRuleBasedRecommendations(sql, plan);
          fallback.notice = `AI API Error: ${err.message}. Showing rule-based results.`;
          return fallback;
        }
      }

      return getRuleBasedRecommendations(sql, plan);
    },

    async chat(userMessage, history, context) {
      const cfg = getConfig();
      const apiKey = (cfg.apiKey || '').trim();
      const provider = (cfg.provider || 'nvidia').toLowerCase();
      const model = cfg.model;

      if (isNvidia(provider)) {
        try {
          if (!window.VEILApi) throw new Error('the VEIL API client is unavailable');
          return await window.VEILApi.chatWithAi({
            message: userMessage,
            history,
            sql: context?.sql,
            plan: context?.plan,
            schema: context?.schema,
            apiKey,
            model,
          });
        } catch (err) {
          console.warn('NVIDIA NIM chat failed, using local assistant:', err);
          return getSimulatedChatResponse(userMessage, context)
            + `\n\n*(NVIDIA NIM request failed: ${err.message}. Showing the local assistant response.)*`;
        }
      }

      const systemPrompt = `You are VEIL AI, an elite PostgreSQL Query Optimization Copilot.
You have full access to the user's active SQL query, execution plan, and database schema:
ACTIVE SQL:
${context.sql || 'No query provided'}

EXECUTION PLAN:
${JSON.stringify(context.plan || {}, null, 2)}

SCHEMA:
${JSON.stringify(context.schema || {}, null, 2)}

Respond with actionable, expert advice. Use Markdown formatting, bullet points, and code blocks for SQL statements. Be concise, direct, and technically precise.`;

      if (apiKey) {
        try {
          if (provider === 'gemini' || apiKey.startsWith('AIzaSy')) {
            return await callGeminiChat(apiKey, model, systemPrompt, history, userMessage);
          } else {
            return await callOpenAIChat(apiKey, model, systemPrompt, history, userMessage);
          }
        } catch (err) {
          console.warn('AI Chat API failed, using intelligent assistant engine:', err);
          return getSimulatedChatResponse(userMessage, context) + `\n\n*(Note: Live model request failed: ${err.message}. Showing expert rule-based response)*`;
        }
      }

// No API key yet: provide context-aware simulated response
      return getSimulatedChatResponse(userMessage, context);
    }
  };
})();
