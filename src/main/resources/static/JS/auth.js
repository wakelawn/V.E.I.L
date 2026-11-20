// ============================================================ VEIL — Simulated Authentication (frontend demo only) ============================================================ NOTE: This is a client-side simulation for UI/prototype purposes. - No real backend, no password hashing, no secure sessions. - A single demo credential is accepted: demo@veil.app / veil123 - Session is stored in sessionStorage (cleared when tab closes). ============================================================

(function () {
  const STORAGE_KEY = 'veil_session';
  const DEMO_USER = { email: 'demo@veil.app', password: 'veil123', name: 'Demo User', role: 'Analyst' };

  const VEILAuth = {
// Attempt login. On success stores a session and redirects to index.html.
    login: function (email, password, remember) {
      const errorBox = document.getElementById('loginError');
      if (errorBox) errorBox.style.display = 'none';

      if (!email || !password) {
        if (errorBox) { errorBox.textContent = 'Please enter both email and password.'; errorBox.style.display = 'block'; }
        return;
      }

      if (email === DEMO_USER.email && password === DEMO_USER.password) {
        const session = {
          user: DEMO_USER,
          loggedInAt: Date.now(),
          remember: !!remember
        };
// "Remember me" -> localStorage (survives closing the tab); otherwise sessionStorage (cleared when the tab closes)
        const store = remember ? localStorage : sessionStorage;
        store.setItem(STORAGE_KEY, JSON.stringify(session));
        window.location.href = 'index.html';
      } else {
        if (errorBox) { errorBox.textContent = 'Invalid email or password.'; errorBox.style.display = 'block'; }
      }
    },

// Check if a valid session exists. Returns the session object or null.
    getSession: function () {
// sessions may live in either store, remember-me first
      try {
        let raw = localStorage.getItem(STORAGE_KEY);
        if (!raw) raw = sessionStorage.getItem(STORAGE_KEY);
        if (!raw) return null;
        const session = JSON.parse(raw);
        return session && session.user ? session : null;
      } catch (e) {
        return null;
      }
    },

// Clear the session and redirect to login.html.
    logout: function () {
      localStorage.removeItem(STORAGE_KEY);
      sessionStorage.removeItem(STORAGE_KEY);
      window.location.href = 'login.html';
    },

// Require a valid session; redirect to login if none.
    requireAuth: function () {
      if (!this.getSession()) {
        window.location.href = 'login.html';
      }
    },

// Ensure login page redirects to dashboard if already logged in.
    redirectIfAuthed: function () {
      if (this.getSession()) {
        window.location.href = 'index.html';
      }
    },

// Get the current user object.
    getUser: function () {
      const s = this.getSession();
      return s ? s.user : null;
    }
  };

  window.VEILAuth = VEILAuth;
})();