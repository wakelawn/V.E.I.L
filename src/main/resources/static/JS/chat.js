// VEIL — Interactive AI Chat Module Manages conversation history, real-time message streaming, markdown formatting for SQL, and pre-set optimization prompts.

window.VEILChat = (() => {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  let chatHistory = [];
  let isSending = false;

// Simple Markdown renderer for chat responses (code blocks, bold, lists, inline code)
  function renderMarkdown(md) {
    if (!md) return '';
    let out = esc(md);

// Code blocks with syntax copy button
    out = out.replace(/```(?:sql)?([\s\S]*?)```/gi, (match, code) => {
      const cleanCode = code.trim();
      return `
        <div class="chat-code-block">
          <div class="chat-code-header">
            <span>SQL</span>
            <button class="chat-copy-code" type="button" data-code="${esc(cleanCode)}">Copy</button>
          </div>
          <pre><code>${cleanCode}</code></pre>
        </div>`;
    });

// Bold **text**
    out = out.replace(/\*\*(.*?)\*\*/g, '<b>$1</b>');

// Inline `code`
    out = out.replace(/`([^`]+)`/g, '<code class="chat-inline-code">$1</code>');

// Line breaks
    out = out.replace(/\n\n/g, '<br><br>').replace(/\n/g, '<br>');

    return out;
  }

  function appendMessage(role, text) {
    const listEl = $('#chatMessages');
    if (!listEl) return;

    const isUser = role === 'user';
    const msgDiv = document.createElement('div');
    msgDiv.className = `chat-msg ${isUser ? 'user-msg' : 'ai-msg'}`;

    if (isUser) {
      msgDiv.innerHTML = `
        <div class="msg-bubble user-bubble">
          ${esc(text).replace(/\n/g, '<br>')}
        </div>`;
    } else {
      msgDiv.innerHTML = `
        <div class="msg-avatar">&#10022;</div>
        <div class="msg-bubble ai-bubble">
          ${renderMarkdown(text)}
        </div>`;
    }

    listEl.appendChild(msgDiv);
    listEl.scrollTop = listEl.scrollHeight;

// Attach copy listeners to code blocks
    msgDiv.querySelectorAll('.chat-copy-code').forEach((btn) => {
      btn.addEventListener('click', () => {
        navigator.clipboard.writeText(btn.dataset.code).then(() => {
          btn.textContent = 'Copied!';
          setTimeout(() => { btn.textContent = 'Copy'; }, 2000);
        });
      });
    });
  }

  function setTyping(loading) {
    let typingEl = $('#chatTyping');
    const listEl = $('#chatMessages');
    if (!listEl) return;

    if (loading) {
      if (!typingEl) {
        typingEl = document.createElement('div');
        typingEl.id = 'chatTyping';
        typingEl.className = 'chat-msg ai-msg typing-indicator';
        typingEl.innerHTML = `
          <div class="msg-avatar">&#10022;</div>
          <div class="msg-bubble ai-bubble typing-dots">
            <span></span><span></span><span></span>
          </div>`;
        listEl.appendChild(typingEl);
        listEl.scrollTop = listEl.scrollHeight;
      }
    } else {
      if (typingEl) typingEl.remove();
    }
  }

  async function sendMessage(text) {
    const message = (text || '').trim();
    if (!message || isSending) return;

    isSending = true;
    appendMessage('user', message);
    chatHistory.push({ role: 'user', text: message });

    const input = $('#chatInput');
    if (input) input.value = '';

    setTyping(true);

    try {
      const context = {
        sql: window.VEILEditor ? window.VEILEditor.getValue() : '',
        plan: window.VEILPlanViewer ? window.VEILPlanViewer.getCurrentPlan() : null,
        schema: window.VEILSchemaViewer ? window.VEILSchemaViewer.getSchemaCache() : null,
      };

      const responseText = await window.VEILAIEngine.chat(message, chatHistory, context);
      setTyping(false);
      appendMessage('model', responseText);
      chatHistory.push({ role: 'model', text: responseText });
    } catch (err) {
      setTyping(false);
      appendMessage('model', `I encountered an error analyzing your request: ${err.message}`);
    } finally {
      isSending = false;
    }
  }

  return {
    init() {
      const sendBtn = $('#chatSendBtn');
      const input = $('#chatInput');

      if (sendBtn && input) {
        sendBtn.addEventListener('click', () => sendMessage(input.value));
        input.addEventListener('keydown', (e) => {
          if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage(input.value);
          }
        });
      }

// Quick prompt suggestion chips
      $$('.prompt-chip').forEach((chip) => {
        chip.addEventListener('click', () => {
          const prompt = chip.dataset.prompt || chip.textContent.trim();
          sendMessage(prompt);
        });
      });

// Populate initial AI welcoming message if empty
      const listEl = $('#chatMessages');
      if (listEl && listEl.children.length === 0) {
        this.resetWelcomeMessage();
      }
    },

    resetWelcomeMessage() {
      const listEl = $('#chatMessages');
      if (!listEl) return;
      listEl.innerHTML = '';
      chatHistory = [];

      const activeQuery = window.VEILEditor ? window.VEILEditor.getValue().trim() : '';
      appendMessage('model', `👋 **Welcome to VEIL AI Assistant!**\n\nI have pre-loaded your active PostgreSQL query and execution plan into context. You can ask me to explain bottlenecks, write migration indexes, rewrite queries, or tune memory settings.`);
    },

    send(prompt) {
      sendMessage(prompt);
    }
  };
})();
