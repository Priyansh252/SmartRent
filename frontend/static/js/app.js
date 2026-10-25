/* SmartRent shared helpers. Every page script talks to the real REST API through SR.api(). */
const SR = (() => {
  const state = { currency: 'INR' };

  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
  const cookie = (name) => (document.cookie.split('; ').find((r) => r.startsWith(name + '=')) || '').split('=')[1];

  class ApiError extends Error {
    constructor(status, body) {
      super((body && body.message) || 'Request failed (' + status + ')');
      this.status = status;
      this.code = body && body.code;
      this.fieldErrors = (body && body.fieldErrors) || [];
    }
  }

  async function api(path, { method = 'GET', body, headers = {} } = {}) {
    const opts = { method, credentials: 'same-origin', headers: { Accept: 'application/json', ...headers } };
    if (method !== 'GET') {
      const token = cookie('XSRF-TOKEN');
      if (token) opts.headers['X-XSRF-TOKEN'] = decodeURIComponent(token);
    }
    if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    const res = await fetch(path, opts);
    if (res.status === 204) return null;
    const ct = res.headers.get('content-type') || '';
    const data = ct.includes('json') ? await res.json().catch(() => null) : null;
    if (!res.ok) {
      if (res.status === 401 && !path.startsWith('/api/auth/register')) {
        location.href = '/login?next=' + encodeURIComponent(location.pathname + location.search);
      }
      throw new ApiError(res.status, data);
    }
    return data;
  }

  function toast(msg, kind = '') {
    let box = $('#toasts');
    if (!box) { box = document.createElement('div'); box.id = 'toasts'; box.setAttribute('role', 'status'); document.body.appendChild(box); }
    const t = document.createElement('div');
    t.className = 'toast ' + kind;
    t.textContent = msg;
    box.appendChild(t);
    setTimeout(() => t.remove(), 5200);
  }
  const fail = (e) => toast(e && e.message ? e.message : 'Something went wrong', 'bad');

  const money = (n) => {
    if (n === null || n === undefined) return '-';
    const v = Number(n);
    return new Intl.NumberFormat('en-IN', { style: 'currency', currency: state.currency, minimumFractionDigits: Number.isInteger(v) ? 0 : 2, maximumFractionDigits: 2 }).format(v);
  };
  const fmtDate = (iso) => iso ? new Date(iso.length === 10 ? iso + 'T00:00:00' : iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }) : '-';
  const fmtDateTime = (iso) => iso ? new Date(iso).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '-';
  const iso = (d) => { const z = new Date(d.getTime() - d.getTimezoneOffset() * 60000); return z.toISOString().slice(0, 10); };
  const today = () => iso(new Date());
  const addDays = (s, n) => { const d = new Date(s + 'T00:00:00'); d.setDate(d.getDate() + n); return iso(d); };
  const qs = (o) => { const p = new URLSearchParams(); Object.entries(o).forEach(([k, v]) => { if (v !== '' && v !== null && v !== undefined) p.set(k, v); }); const s = p.toString(); return s ? '?' + s : ''; };
  const label = (s) => String(s || '').replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
  const pill = (s) => `<span class="pill ${esc(s)}">${esc(label(s))}</span>`;
  const loading = (el, msg = 'Loading...') => { el.innerHTML = `<div class="state"><div class="spinner"></div>${esc(msg)}</div>`; };
  const empty = (el, msg) => { el.innerHTML = `<div class="state">${esc(msg)}</div>`; };
  const error = (el, e) => { el.innerHTML = `<div class="alert bad">${esc(e && e.message || 'Could not load data')}</div>`; };

  async function loadMeta() {
    if (state.meta) return state.meta;
    state.meta = await api('/api/meta');
    state.currency = state.meta.currency || 'INR';
    return state.meta;
  }
  function fillSelect(sel, items, first, valueFn = (x) => x, textFn = label) {
    sel.innerHTML = (first ? `<option value="">${esc(first)}</option>` : '') + items.map((x) => `<option value="${esc(valueFn(x))}">${esc(textFn(x))}</option>`).join('');
  }

  function vehicleCard(item, extraQuery = '') {
    const v = item.vehicle || item;
    const total = item.estimatedTotal;
    return `<article class="vcard"><a class="pic" href="/vehicles/${v.id}${extraQuery}"><img src="${esc(v.imageUrl || '/images/cars/' + v.vehicleType.toLowerCase() + '.svg')}" alt="${esc(v.make + ' ' + v.model)}" onerror="this.onerror=null;this.src='/images/cars/sedan.svg'"></a>
      <div class="body"><div><h3>${esc(v.make)} ${esc(v.model)}</h3><div class="muted small">${esc(v.location.name)}</div></div>
      <div class="chips"><span class="chip">${esc(label(v.vehicleType))}</span><span class="chip">${v.seats} seats</span><span class="chip">${esc(label(v.fuelType))}</span><span class="chip">${esc(label(v.transmission))}</span></div>
      <div class="price"><div>${total != null ? `<b>${money(total)}</b><small> total</small><div class="muted small">avg ${money(item.estimatedDailyAverage)}/day</div>` : `<small>from</small> <b>${money(v.dailyRate)}</b><small>/day</small>`}</div>
      <a class="btn btn-sm" href="/vehicles/${v.id}${extraQuery}">View</a></div></div></article>`;
  }

  function breakdownHtml(pb) {
    const rows = pb.lines.map((l) => `<tr><td>${esc(l.label)}<small>${esc(l.explanation)}</small></td><td class="${Number(l.amount) < 0 ? 'neg' : ''}">${Number(l.amount) < 0 ? '-' : ''}${money(Math.abs(l.amount))}</td></tr>`).join('');
    return `<table class="breakdown">${rows}<tr class="total"><td>Total (${pb.days} ${pb.days === 1 ? 'day' : 'days'})</td><td>${money(pb.totalPrice)}</td></tr></table>
      <p class="muted small">Multiplier ${esc(pb.appliedMultiplier)}${pb.multiplierClamped ? ' (limited from ' + esc(pb.rawMultiplier) + ')' : ''}. Calculated by the server; the agreed price is locked when you book.</p>`;
  }

  function pager(el, page, onPage) {
    if (!page || page.totalPages <= 1) { el.innerHTML = ''; return; }
    el.innerHTML = `<button class="btn btn-ghost btn-sm" ${page.page === 0 ? 'disabled' : ''} data-p="${page.page - 1}">Previous</button><span class="muted small">Page ${page.page + 1} of ${page.totalPages} (${page.totalElements} total)</span><button class="btn btn-ghost btn-sm" ${page.page + 1 >= page.totalPages ? 'disabled' : ''} data-p="${page.page + 1}">Next</button>`;
    $$('button', el).forEach((b) => b.addEventListener('click', () => onPage(Number(b.dataset.p))));
  }

  /** Show server-side field errors next to inputs named like the field. */
  function showFieldErrors(form, e) {
    $$('.field', form).forEach((f) => { f.classList.remove('invalid'); const m = $('.err', f); if (m) m.textContent = ''; });
    (e.fieldErrors || []).forEach((fe) => {
      const input = form.elements[fe.field];
      const field = input && input.closest('.field');
      if (field) { field.classList.add('invalid'); const m = $('.err', field); if (m) m.textContent = fe.message; }
    });
  }

  function tabs(root) {
    const btns = $$('.tabs button', root);
    const show = (name) => { btns.forEach((b) => b.classList.toggle('active', b.dataset.tab === name)); $$('.panel', root).forEach((p) => { p.hidden = p.dataset.panel !== name; }); document.dispatchEvent(new CustomEvent('tab', { detail: name })); };
    btns.forEach((b) => b.addEventListener('click', () => show(b.dataset.tab)));
    show(btns[0].dataset.tab);
    return show;
  }

  document.addEventListener('DOMContentLoaded', () => {
    const btn = $('.menu-btn'); const nav = $('.nav');
    if (btn && nav) btn.addEventListener('click', () => nav.classList.toggle('open'));
    $$('.nav a').forEach((a) => { if (a.getAttribute('href') === location.pathname) a.classList.add('active'); });
    loadMeta().catch(() => {});
  });

  return { api, ApiError, esc, $, $$, toast, fail, money, fmtDate, fmtDateTime, today, addDays, qs, label, pill, loading, empty, error, loadMeta, fillSelect, vehicleCard, breakdownHtml, pager, showFieldErrors, tabs, state };
})();
