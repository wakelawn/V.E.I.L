// VEIL — SQL Editor Module Manages syntax highlighting, synchronized scrolling, line numbers, formatting, and keyboard shortcuts.

window.VEILEditor = (() => {
  const $ = (s, r = document) => r.querySelector(s);
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  const KW = 'SELECT|FROM|WHERE|JOIN|LEFT|RIGHT|INNER|OUTER|FULL|CROSS|ON|AND|OR|NOT|IN|IS|NULL|LIKE|BETWEEN|AS|GROUP|BY|ORDER|HAVING|LIMIT|OFFSET|DISTINCT|UNION|ALL|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CASE|WHEN|THEN|ELSE|END|DESC|ASC|WITH|COUNT|SUM|AVG|MIN|MAX';
  const tokenRe = () => new RegExp(`(--[^\\n]*)|('(?:[^']|'')*')|\\b(\\d+(?:\\.\\d+)?)\\b|\\b(${KW})\\b`, 'gi');

  let inputEl, hlEl, numsEl, errorEl;
  let onAnalyzeCallback = null;

  function highlight(src) {
    let out = '', last = 0, m;
    const re = tokenRe();
    while ((m = re.exec(src))) {
      out += esc(src.slice(last, m.index));
      const cls = m[1] ? 'tok-com' : m[2] ? 'tok-str' : m[3] ? 'tok-num' : 'tok-kw';
      out += `<span class="${cls}">${esc(m[0])}</span>`;
      last = re.lastIndex;
    }
    return out + esc(src.slice(last)) + '\n';
  }

  function syncScroll() {
    if (!hlEl || !inputEl) return;
    hlEl.scrollTop = inputEl.scrollTop;
    hlEl.scrollLeft = inputEl.scrollLeft;
    if (numsEl) numsEl.scrollTop = inputEl.scrollTop;
  }

  function updateCursorStatus() {
    if (!inputEl) return;
    const pos = inputEl.selectionStart || 0;
    const textBefore = inputEl.value.slice(0, pos);
    const lines = textBefore.split('\n');
    const lineNo = lines.length;
    const colNo = lines[lines.length - 1].length + 1;
    const totalLines = inputEl.value.split('\n').length;
    const posEl = document.getElementById('cursorPos');
    const countEl = document.getElementById('lineCount');
    if (posEl) posEl.textContent = `Ln ${lineNo}, Col ${colNo}`;
    if (countEl) countEl.textContent = `${totalLines} line${totalLines === 1 ? '' : 's'}`;
  }

  function refresh() {
    if (!inputEl) return;
    const val = inputEl.value;
    if (hlEl) hlEl.innerHTML = val ? highlight(val) : '';
    const lineCount = val.split('\n').length;
    if (numsEl) {
      numsEl.textContent = Array.from({ length: lineCount }, (_, i) => i + 1).join('\n');
    }
    syncScroll();
    updateCursorStatus();
  }

  function formatSQL(sql) {
    let s = (sql || '').replace(/\s+/g, ' ').trim();
    s = s.replace(tokenRe(), (m, c, str, num, kw) => (kw ? m.toUpperCase() : m));
    s = s.replace(/\s(FROM|WHERE|GROUP BY|HAVING|ORDER BY|LIMIT)\b/g, '\n$1')
         .replace(/\s((?:LEFT |RIGHT |INNER |FULL |CROSS )?JOIN)\b/g, '\n$1')
         .replace(/\s(AND|OR)\s/g, '\n  $1 ');
    return s;
  }

  return {
    init({ inputSelector, highlightSelector, lineNumsSelector, errorBoxSelector, onAnalyze }) {
      inputEl = $(inputSelector);
      hlEl = $(highlightSelector);
      numsEl = $(lineNumsSelector);
      errorEl = $(errorBoxSelector);
      onAnalyzeCallback = onAnalyze;

      if (!inputEl) return;

      inputEl.addEventListener('input', refresh);
      inputEl.addEventListener('scroll', syncScroll);
      inputEl.addEventListener('click', updateCursorStatus);
      inputEl.addEventListener('keyup', updateCursorStatus);
      inputEl.addEventListener('keydown', (e) => {
        if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
          e.preventDefault();
          if (onAnalyzeCallback) onAnalyzeCallback();
        }
      });

      refresh();
    },

    getValue() {
      return inputEl ? inputEl.value : '';
    },

    setValue(val) {
      if (!inputEl) return;
      inputEl.value = val;
      refresh();
    },

    format() {
      if (!inputEl) return;
      inputEl.value = formatSQL(inputEl.value);
      refresh();
    },

    clear() {
      if (!inputEl) return;
      inputEl.value = '';
      this.hideError();
      refresh();
      inputEl.focus();
    },

    insertAtCursor(text) {
      if (!inputEl) return;
      const start = inputEl.selectionStart;
      const end = inputEl.selectionEnd;
      inputEl.value = inputEl.value.slice(0, start) + text + inputEl.value.slice(end);
      inputEl.selectionStart = inputEl.selectionEnd = start + text.length;
      inputEl.focus();
      refresh();
    },

    showError(msg) {
      if (!errorEl) return;
      errorEl.textContent = msg;
      errorEl.hidden = false;
    },

    hideError() {
      if (!errorEl) return;
      errorEl.hidden = true;
    },

    highlight(src) {
      return highlight(src);
    }
  };
})();
