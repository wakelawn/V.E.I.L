// Backend-backed authentication. The JWT lives in sessionStorage (or
// localStorage with "remember me") and is sent as a Bearer token.

(function () {
  const STORAGE_KEY = 'veil_session';

  function backendBase() {
    try {
      if (window.VEILApi && typeof window.VEILApi.getBaseUrl === 'function') {
        const configured = window.VEILApi.getBaseUrl();
        if (configured) return configured.replace(/\/$/, '');
      }
    } catch { /* api.js may not have loaded yet */ }

    const saved = localStorage.getItem('veil_java_backend_url');
    if (saved) return saved.replace(/\/$/, '');

    // Local split hosting (frontend on :5500, backend on :8080).
    return window.location.port === '5500' ? 'http://localhost:8080' : '';
  }

  function clearStoredSession() {
    try {
      localStorage.removeItem(STORAGE_KEY);
      sessionStorage.removeItem(STORAGE_KEY);
    } catch { /* storage can be disabled */ }
  }

  function readSession() {
    let raw = null;

    try {
      raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) raw = sessionStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }

    if (!raw) return null;

    try {
      const session = JSON.parse(raw);
      if (!session || !session.token || !session.user) {
        clearStoredSession();
        return null;
      }

      // Drop tokens the server would reject anyway.
      const payload = JSON.parse(atob(session.token.split('.')[1]
        .replace(/-/g, '+').replace(/_/g, '/')));

      if (payload.exp && payload.exp * 1000 <= Date.now()) {
        clearStoredSession();
        return null;
      }

      return session;
    } catch {
      clearStoredSession();
      return null;
    }
  }

  function showError(message) {
    const box = document.getElementById('loginError');
    if (!box) return;
    box.textContent = message;
    box.style.display = message ? 'block' : 'none';
  }

  const VEILAuth = {
    // Ask the backend for a token. Returns true when the redirect starts.
    login: async function (email, password, remember) {
      showError('');

      if (!email || !password) {
        showError('Please enter both email and password.');
        return false;
      }

      try {
        const response = await fetch(`${backendBase()}/api/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email, password }),
        });

        if (!response.ok) {
          showError(response.status === 429
            ? 'Too many attempts. Please wait a minute.'
            : 'Invalid email or password.');
          return false;
        }

        const data = await response.json();
        const session = {
          token: data.token,
          user: { email: data.email || email, name: data.email || email, role: 'Analyst' },
          loggedInAt: Date.now(),
        };

        // "Remember me" survives closing the tab; otherwise the tab only.
        clearStoredSession();
        (remember ? localStorage : sessionStorage).setItem(STORAGE_KEY, JSON.stringify(session));

        window.location.href = 'index.html';
        return true;

      } catch (error) {
        showError('Cannot reach the VEIL backend. Check the server URL.');
        return false;
      }
    },

    getSession: function () {
      return readSession();
    },

    getToken: function () {
      const session = readSession();
      return session ? session.token : '';
    },

    clearSession: clearStoredSession,

    logout: function () {
      clearStoredSession();
      try { sessionStorage.setItem('veil_logged_out', '1'); } catch { /* ignore */ }
      window.location.href = 'login.html';
    },

    // Send unauthenticated visitors to the sign-in page.
    requireAuth: function () {
      if (!readSession()) {
        window.location.href = 'login.html';
        return false;
      }
      return true;
    },

    redirectIfAuthed: function () {
      if (readSession()) {
        window.location.href = 'index.html';
      }
    },

    getUser: function () {
      const session = readSession();
      return session ? session.user : null;
    },
  };

  window.VEILAuth = VEILAuth;
})();
