'use strict';
window.medicare = {
  csrf() { return document.querySelector('[name=csrfmiddlewaretoken]')?.value || ''; },
  money(value) { return `PKR ${Number(value).toLocaleString('en-PK', {minimumFractionDigits: 2, maximumFractionDigits: 2})}`; },
  async request(url, options = {}) {
    const response = await fetch(url, {credentials: 'same-origin', ...options,
      headers: {'Content-Type': 'application/json', 'X-CSRFToken': this.csrf(), ...(options.headers || {})}});
    let data;
    try { data = await response.json(); } catch { throw Object.assign(new Error('The server returned an unexpected response. Check your connection and sign-in status.'), {status: response.status}); }
    if (!response.ok) {
      const flatten = (v) => typeof v === 'string' ? v : Array.isArray(v) ? v.map(flatten).join(' ') : v && typeof v === 'object' ? Object.entries(v).map(([k, val]) => `${k}: ${flatten(val)}`).join(' · ') : String(v);
      throw Object.assign(new Error(flatten(data.error?.details || data)), {status: response.status});
    }
    return data;
  },
  node(tag, text, className) {
    const el = document.createElement(tag);
    if (text !== undefined) el.textContent = text;
    if (className) el.className = className;
    return el;
  }
};
document.querySelector('#menu-toggle')?.addEventListener('click', () => document.querySelector('#sidebar').classList.toggle('open'));
document.querySelector('#print-receipt')?.addEventListener('click', () => window.print());
document.querySelectorAll('form[data-confirm]').forEach(form => form.addEventListener('submit', event => {
  if (!window.confirm(form.dataset.confirm)) event.preventDefault();
}));
