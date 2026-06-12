(function () {
  const root = document.documentElement;
  const themeToggle = document.getElementById('themeToggle');
  const refreshAllButton = document.getElementById('refreshAllButton');
  const applyConfigButton = document.getElementById('applyConfigButton');
  const resetConfigButton = document.getElementById('resetConfigButton');
  const apiBaseUrlInput = document.getElementById('apiBaseUrl');
  const activeBaseUrlHint = document.getElementById('activeBaseUrlHint');
  const tabButtons = Array.from(document.querySelectorAll('.tab-button'));
  const panels = {
    appointments: document.getElementById('panel-appointments'),
    logs: document.getElementById('panel-logs')
  };

  const appointmentsContent = document.getElementById('appointmentsContent');
  const logsContent = document.getElementById('logsContent');
  const appointmentsCount = document.getElementById('appointmentsCount');
  const appointmentsBookedCount = document.getElementById('appointmentsBookedCount');
  const logsCount = document.getElementById('logsCount');
  const logsDurationTotal = document.getElementById('logsDurationTotal');

  let currentTheme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  let apiBaseUrl = normalizeBaseUrl(window.RUNTIME_ENV && window.RUNTIME_ENV.API_BASE_URL ? window.RUNTIME_ENV.API_BASE_URL : '');

  root.setAttribute('data-theme', currentTheme);
  apiBaseUrlInput.value = apiBaseUrl;
  updateBaseUrlHint();

  themeToggle.addEventListener('click', function () {
    currentTheme = currentTheme === 'dark' ? 'light' : 'dark';
    root.setAttribute('data-theme', currentTheme);
  });

  tabButtons.forEach(function (button) {
    button.addEventListener('click', function () {
      const tab = button.getAttribute('data-tab');
      tabButtons.forEach(btn => btn.classList.toggle('active', btn === button));
      Object.entries(panels).forEach(([key, panel]) => panel.classList.toggle('active', key === tab));
    });
  });

  applyConfigButton.addEventListener('click', function () {
    apiBaseUrl = normalizeBaseUrl(apiBaseUrlInput.value);
    updateBaseUrlHint();
    loadAll();
  });

  resetConfigButton.addEventListener('click', function () {
    apiBaseUrl = normalizeBaseUrl(window.RUNTIME_ENV && window.RUNTIME_ENV.API_BASE_URL ? window.RUNTIME_ENV.API_BASE_URL : '');
    apiBaseUrlInput.value = apiBaseUrl;
    updateBaseUrlHint();
    loadAll();
  });

  refreshAllButton.addEventListener('click', loadAll);

  function normalizeBaseUrl(value) {
    return (value || '').trim().replace(/\/$/, '');
  }

  function updateBaseUrlHint() {
    activeBaseUrlHint.textContent = apiBaseUrl
      ? `Host attivo: ${apiBaseUrl}`
      : 'Host attivo: percorsi relativi sullo stesso dominio del frontend.';
  }

  function buildUrl(path) {
    return apiBaseUrl ? `${apiBaseUrl}${path}` : path;
  }

  async function loadAll() {
    appointmentsContent.className = 'loading-state';
    appointmentsContent.textContent = 'Caricamento appuntamenti in corso...';
    logsContent.className = 'loading-state';
    logsContent.textContent = 'Caricamento call logs in corso...';

    await Promise.all([loadAppointments(), loadLogs()]);
  }

  async function loadAppointments() {
    try {
      const response = await fetch(buildUrl('/api/appointments/all'));
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const data = await response.json();
      renderAppointments(Array.isArray(data) ? data : []);
    } catch (error) {
      appointmentsCount.textContent = '-';
      appointmentsBookedCount.textContent = '-';
      appointmentsContent.className = 'error-state';
      appointmentsContent.textContent = `Errore nel caricamento degli appuntamenti: ${error.message}`;
    }
  }

  async function loadLogs() {
    try {
      const response = await fetch(buildUrl('/api/call-logs?limit=200'));
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const data = await response.json();
      renderLogs(Array.isArray(data) ? data : []);
    } catch (error) {
      logsCount.textContent = '-';
      logsDurationTotal.textContent = '-';
      logsContent.className = 'error-state';
      logsContent.textContent = `Errore nel caricamento dei call logs: ${error.message}`;
    }
  }

  function renderAppointments(items) {
    appointmentsCount.textContent = String(items.length);
    appointmentsBookedCount.textContent = String(items.filter(item => item.status === 'BOOKED').length);

    if (!items.length) {
      appointmentsContent.className = 'empty-state';
      appointmentsContent.textContent = 'Nessun appuntamento disponibile.';
      return;
    }

    const rows = items.map(function (item) {
      const status = item.status || 'UNKNOWN';
      const badgeClass = status === 'BOOKED' ? 'booked' : status === 'CANCELLED' ? 'cancelled' : 'other';
      return `
        <tr>
          <td>${escapeHtml(item.citizenName || '-')}</td>
          <td>${escapeHtml(item.citizenEmail || '-')}</td>
          <td>${escapeHtml(item.serviceTypeCode || '-')}</td>
          <td>${formatDate(item.appointmentDate)}</td>
          <td>${formatAppointmentTime(item.appointmentDate, item.appointmentTime)}</td>
          <td><span class="badge ${badgeClass}">${escapeHtml(status)}</span></td>
          <td>${item.deleted ? 'Yes' : 'No'}</td>
          <td>${escapeHtml(item.notes || '-')}</td>
          <td>${formatDateTime(item.createdAt)}</td>
          <td>${formatDateTime(item.updatedAt)}</td>
        </tr>
      `;
    }).join('');

    appointmentsContent.className = '';
    appointmentsContent.innerHTML = `
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Citizen</th>
              <th>Email</th>
              <th>Service</th>
              <th>Date</th>
              <th>Time</th>
              <th>Status</th>
              <th>Deleted</th>
              <th>Notes</th>
              <th>Created at</th>
              <th>Updated at</th>
            </tr>
          </thead>
          <tbody>${rows}</tbody>
        </table>
      </div>
    `;
  }

  function renderLogs(items) {
    logsCount.textContent = String(items.length);
    const totalSeconds = items.reduce((sum, item) => sum + (Number(item.durationSeconds) || 0), 0);
    logsDurationTotal.textContent = formatDuration(totalSeconds);

    if (!items.length) {
      logsContent.className = 'empty-state';
      logsContent.textContent = 'Nessun call log disponibile.';
      return;
    }

    const cards = items.map(function (item, index) {
      const transcriptId = `transcript-${index}`;
      const messages = parseTranscript(item.transcript || '');
      const messagesHtml = messages.length
        ? messages.map(renderMessage).join('')
        : '<p>Nessun messaggio nel transcript.</p>';

      return `
        <article class="log-card">
          <div class="log-meta">
            <div class="meta-item">
              <span class="meta-label">Call ID</span>
              <span class="meta-value">${escapeHtml(item.vapiCallId || '-')}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Created at</span>
              <span class="meta-value">${formatDateTime(item.createdAt)}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Started at</span>
              <span class="meta-value">${formatDateTime(item.startedAt)}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Duration</span>
              <span class="meta-value">${formatDuration(item.durationSeconds)}</span>
            </div>
          </div>

          <div class="summary-box">
            <strong>Summary</strong>
            <p>${escapeHtml(item.summary || 'Nessun summary disponibile.')}</p>
          </div>

          <div class="transcript-toggle">
            <button class="button" type="button" data-transcript-target="${transcriptId}">Mostra transcript</button>
          </div>

          <div class="transcript" id="${transcriptId}">
            <div class="chat-thread">
              ${messagesHtml}
            </div>
          </div>
        </article>
      `;
    }).join('');

    logsContent.className = 'logs-list';
    logsContent.innerHTML = cards;

    logsContent.querySelectorAll('[data-transcript-target]').forEach(function (button) {
      button.addEventListener('click', function () {
        const targetId = button.getAttribute('data-transcript-target');
        const target = document.getElementById(targetId);
        const isOpen = target.classList.toggle('open');
        button.textContent = isOpen ? 'Nascondi transcript' : 'Mostra transcript';
      });
    });
  }

  function parseTranscript(transcript) {
    const lines = String(transcript || '')
      .split('\n')
      .map(line => line.trim())
      .filter(Boolean);

    const messages = [];

    lines.forEach(function (line) {
      let speaker = null;
      let text = line;

      if (line.startsWith('User:')) {
        speaker = 'User';
        text = line.slice(5).trim();
      } else if (line.startsWith('AI:')) {
        speaker = 'AI';
        text = line.slice(3).trim();
      } else {
        if (messages.length) {
          messages[messages.length - 1].text += `\n${line}`;
        }
        return;
      }

      const last = messages[messages.length - 1];
      if (last && last.speaker === speaker) {
        last.text += `\n${text}`;
      } else {
        messages.push({ speaker, text });
      }
    });

    return messages;
  }

  function renderMessage(message) {
    const speakerClass = message.speaker === 'User' ? 'user' : 'ai';
    return `
      <div class="message-row ${speakerClass}">
        <div class="bubble">
          <span class="speaker">${escapeHtml(message.speaker)}</span>
          <div>${escapeHtml(message.text)}</div>
        </div>
      </div>
    `;
  }

  function formatDate(value) {
    if (!value) return '-';
    const match = String(value).match(/^(\d{4})-(\d{2})-(\d{2})$/);
    if (!match) return escapeHtml(String(value));
    const [, year, month, day] = match;
    return `${day}/${month}/${year}`;
  }

  function formatTime(value) {
    if (!value) return '-';
    return String(value).slice(0, 5);
  }

  function formatAppointmentTime(dateValue, timeValue) {
    if (!timeValue) return '-';
    return String(timeValue).slice(0, 5);
  }

  function formatDateTime(value) {
    if (!value) return '-';

    const match = String(value).match(
      /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/
    );

    if (!match) return escapeHtml(String(value));

    const [, year, month, day, hour, minute] = match;
    return `${day}/${month}/${year}, ${hour}:${minute}`;
  }

  function formatDuration(seconds) {
    const total = Number(seconds);
    if (!Number.isFinite(total) || total <= 0) return '-';
    const mins = Math.floor(total / 60);
    const secs = total % 60;
    if (mins === 0) return `${secs}s`;
    return `${mins}m ${secs}s`;
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  loadAll();
})();