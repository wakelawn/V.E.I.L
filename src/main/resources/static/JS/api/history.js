// Frontend API client for Query History & Workspaces (Phase 8 backend).
// Wraps /api/history and /api/projects. Every call carries the bearer token.
// Ownership is always derived from the JWT - never from a userId parameter.
window.VEILHistoryApi = (() => {

  const NOT_FOUND = 404;

  const readError = async (res) => {
    try {
      const body = await res.json();
      return body.error || body.message || `HTTP ${res.status}`;
    } catch {
      return `HTTP ${res.status} ${res.statusText}`;
    }
  };

  const handleUnauthorized = (res) => {
    if (res.status !== 401 || !window.VEILAuth) return;
    if (/login\.html$/.test(window.location.pathname)) return;
    window.VEILAuth.clearSession();
    window.location.href = 'login.html';
  };

  // ------------------------------------------------------------------
  // Queries (query history)
  // ------------------------------------------------------------------

  // Create a saved query. name + sqlText required; description/tags/projectId optional.
  async function createQuery({ name, sqlText, description, tags, projectId, databaseType, databaseName }) {
    const body = { name, sqlText };
    if (description !== undefined) body.description = description;
    if (tags !== undefined) body.tags = tags;
    if (projectId !== undefined) body.projectId = projectId;
    if (databaseType !== undefined) body.databaseType = databaseType;
    if (databaseName !== undefined) body.databaseName = databaseName;

    const res = await fetch('/api/history', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      handleUnauthorized(res);
      throw new Error(await readError(res));
    }
    return res.json();
  }

  // List top-level saved queries (pageable). Returns { content, totalElements, ... }.
  async function listQueries({ page = 0, size = 20 } = {}) {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const res = await fetch(`/api/history?${params}`, { headers: await authHeaders() });
    if (!res.ok) {
      handleUnauthorized(res);
      throw new Error(await readError(res));
    }
    return res.json();
  }

  // Get one saved query by id.
  async function getQuery(id) {
    const res = await fetch(`/api/history/${id}`, { headers: await authHeaders() });
    if (!res.ok) {
      handleUnauthorized(res);
      if (res.status === NOT_FOUND) return null;
      throw new Error(await readError(res));
    }
    return res.json();
  }

  // Partial update a saved query.
  async function updateQuery(id, { name, description, sqlText, tags, favorite, projectId, databaseType, databaseName } = {}) {
    const body = {};
    if (name !== undefined) body.name = name;
    if (description !== undefined) body.description = description;
    if (sqlText !== undefined) body.sqlText = sqlText;
    if (tags !== undefined) body.tags = tags;
    if (favorite !== undefined) body.favorite = favorite;
    if (projectId !== undefined) body.projectId = projectId;
    if (databaseType !== undefined) body.databaseType = databaseType;
    if (databaseName !== undefined) body.databaseName = databaseName;

    const res = await fetch(`/api/history/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      handleUnauthorized(res);
      throw new Error(await readError(res));
    }
    return res.json();
  }

  // Delete a saved query.
  async function deleteQuery(id) {
    const res = await fetch(`/api/history/${id}`, { method: 'DELETE', headers: await authHeaders() });
    if (!res.ok) {
      handleUnauthorized(res);
      throw new Error(await readError(res));
    }
    return true;
  }

  // Toggle favorite on a saved query.
  async function toggleFavorite(id) {
    const res = await fetch(`/api/history/${id}/favorite`, { method: 'POST', headers: await authHeaders() });
    if (!res.ok) {
      handleUnauthorized(res);
      throw new Error(await readError(res));
    }
    return res.json();
  }

  // Create a new version of a saved query.
  async function createVersion(id, sql) {
    const res = await fetch(`/api/history/${id}/version`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
      body: JSON.stringify({ sql }),
    });
    if (!res.ok) {
      handleUnauthorized(res);
      throw new Error(await readError(res));
    }
    return res.json();
  }

  // List versions of a saved query.
  async function listVersions(id) {
    const res = await fetch(`/api/history/${id}/versions`, { headers: await authHeaders() });
    if (!res.ok) {
      handleUnauthorized(res);
      throw new Error(await readError(res));
    }
    return res.json();
  }

  // Search saved queries by name (case-insensitive).
  async function searchQueries(q, { page = 0, size = 20 } = {}) {
    const params = new URLSearchParams({ q, page: String(page), size: String(size) });
    const res = await fetch(`/api/history/search?${params}`, { headers: await authHeaders() });
    if (!res.ok) {
      handleUnauthorized(res);
      throw new Error(await readError(res));
    }
    return res.json();
  }

  // List queries in a project.
  async function listQueriesByProject(projectId, { page = 0, size = 20 } = {}) {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const res = await fetch(`/api/history/project/${projectId}?${params}`, { headers: await authHeaders() });
    if (!res.ok) {
      handleUnauthorized(res);
      throw new Error(await readError(res));
    }
    return res.json();
  }

  // List distinct tags for the current user.
  async function listTags() {
    const res = await fetch('/api/history/tags', { headers: await authHeaders() });
    if (!res.ok) {
      handleUnauthorized(res);
      throw new Error(await readError(res));
    }
    return res.json();
  }

  // Get stats for the current user.
  async function getStats() {
    const res = await fetch('/api/history/stats', { headers: await authHeaders() });
    if (!res.ok) {
      handleUnauthorized(res);
      throw new Error(await readError(res));
    }
    return res.json();
  }

  // Filter queries by tag.
  async function listByTag(tag, { page = 0, size = 20 } = {}) {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const res = await fetch(`/api/history/tag/${encodeURIComponent(tag)}?${params}`, { headers: await authHeaders() });
    if (!res.ok) {
      handleUnauthorized(res);
      throw new Error(await readError(res));
    }
    return res.json();
  }

  // ------------------------------------------------------------------
  // Projects (workspaces)
  // ------------------------------------------------------------------

  async function createProject({ name, description, color, icon }) {
    const body = { name };
    if (description !== undefined) body.description = description;
    if (color !== undefined) body.color = color;
    if (icon !== undefined) body.icon = icon;

    const res = await fetch('/api/projects', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      handleUnauthorized(res);
      throw new Error(await readError(res));
    }
    return res.json();
  }

  async function listProjects({ page = 0, size = 20 } = {}) {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const res = await fetch(`/api/projects?${params}`, { headers: await authHeaders() });
    if (!res.ok) {
      handleUnauthorized(res);
      throw new Error(await readError(res));
    }
    return res.json();
  }

  // Unarchived projects, default first (best for a dropdown).
  async function listProjectsOrdered() {
    const res = await fetch('/api/projects/list', { headers: await authHeaders() });
    if (!res.ok) {
      handleUnauthorized(res);
      throw new Error(await readError(res));
    }
    return res.json();
  }

  async function getProject(id) {
    const res = await fetch(`/api/projects/${id}`, { headers: await authHeaders() });
    if (!res.ok) {
      handleUnauthorized(res);
      if (res.status === NOT_FOUND) return null;
      throw new Error(await readError(res));
    }
    return res.json();
  }

  async function updateProject(id, { name, description, color, icon, archived } = {}) {
    const body = {};
    if (name !== undefined) body.name = name;
    if (description !== undefined) body.description = description;
    if (color !== undefined) body.color = color;
    if (icon !== undefined) body.icon = icon;
    if (archived !== undefined) body.archived = archived;

    const res = await fetch(`/api/projects/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      handleUnauthorized(res);
      throw new Error(await readError(res));
    }
    return res.json();
  }

  async function deleteProject(id) {
    const res = await fetch(`/api/projects/${id}`, { method: 'DELETE', headers: await authHeaders() });
    if (!res.ok) {
      handleUnauthorized(res);
      throw new Error(await readError(res));
    }
    return true;
  }

  async function getDefaultProject() {
    const res = await fetch('/api/projects/default', { headers: await authHeaders() });
    if (!res.ok) {
      handleUnauthorized(res);
      if (res.status === NOT_FOUND) return null;
      throw new Error(await readError(res));
    }
    return res.json();
  }

  async function setDefaultProject(id) {
    const res = await fetch(`/api/projects/${id}/default`, { method: 'POST', headers: await authHeaders() });
    if (!res.ok) {
      handleUnauthorized(res);
      throw new Error(await readError(res));
    }
    return res.json();
  }

  // ------------------------------------------------------------------
  // Auth helpers
  // ------------------------------------------------------------------

  async function authHeaders() {
    const headers = {};
    const token = window.VEILAuth ? window.VEILAuth.getToken() : '';
    if (token) headers.Authorization = `Bearer ${token}`;
    return headers;
  }

  return {
    // Queries
    createQuery,
    listQueries,
    getQuery,
    updateQuery,
    deleteQuery,
    toggleFavorite,
    createVersion,
    listVersions,
    searchQueries,
    listQueriesByProject,
    listTags,
    getStats,
    // Projects
    createProject,
    listProjects,
    listProjectsOrdered,
    getProject,
    updateProject,
    deleteProject,
    getDefaultProject,
    setDefaultProject,
  };

})();
