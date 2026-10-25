document.addEventListener('DOMContentLoaded', async () => {
  const root = SR.$('#admin-root');
  const meta = await SR.loadMeta().catch(SR.fail);
  const loaded = {};
  SR.tabs(root);
  document.addEventListener('tab', (e) => { const t = e.detail; if (loaded[t]) return; loaded[t] = true; ({ overview, fleet, bookings, users, events, audit }[t] || (() => {}))(); });

  // ---------------------------------------------------------------- overview
  async function overview() {
    const kpis = SR.$('#kpis'), charts = SR.$('#charts');
    SR.loading(kpis);
    try {
      const [d, c] = await Promise.all([SR.api('/api/admin/dashboard'), SR.api('/api/admin/charts?days=30')]);
      const k = (v, l) => `<div class="card kpi"><b>${v}</b><span>${l}</span></div>`;
      kpis.innerHTML = '<div class="grid g4">' + k(d.bookableVehicles + ' / ' + d.totalVehicles, 'Bookable vehicles') + k(SR.money(d.revenueLast30Days), 'Revenue, last 30 days') + k(d.utilizationLast30DaysPercent + '%', 'Fleet utilization, 30 days') + k(d.bookingsByStatus.CONFIRMED + d.bookingsByStatus.PICKED_UP, 'Active & upcoming bookings') + k(d.vehiclesInMaintenance, 'In maintenance') + k(d.notificationsProcessed, 'Notifications processed (JMS)') + k(d.pendingOutboxEvents, 'Events waiting for broker') + k(d.failedOutboxEvents, 'Failed events') + '</div>';
      const max = Math.max(1, ...c.dailyRevenue.map((x) => Number(x.revenue)));
      charts.innerHTML = `<div class="grid g2"><div class="card"><h3>Revenue by pickup day</h3><div class="bars" role="img" aria-label="Daily revenue bars">${c.dailyRevenue.map((x) => `<div class="bar" style="height:${Math.round(100 * Number(x.revenue) / max)}%" title="${SR.fmtDate(x.date)}: ${SR.money(x.revenue)} (${x.bookings} bookings)"></div>`).join('')}</div><div class="row between muted small"><span>${SR.fmtDate(c.dailyRevenue[0].date)}</span><span>${SR.fmtDate(c.dailyRevenue[c.dailyRevenue.length - 1].date)}</span></div></div>
        <div class="card"><h3>Utilization by vehicle type (${c.windowDays} days)</h3>${c.utilizationByType.map((u) => `<div class="hbar"><span>${SR.esc(SR.label(u.type))}</span><div class="track"><div class="fill" style="width:${Math.min(100, u.utilizationPercent)}%"></div></div><span>${u.utilizationPercent}%</span></div>`).join('')}<p class="muted small">Rented vehicle-days / available vehicle-days, from stored bookings.</p></div></div>`;
    } catch (e) { SR.error(kpis, e); }
  }
  overview(); loaded.overview = true;

  // ---------------------------------------------------------------- fleet
  const fleetEl = SR.$('#fleet'), form = SR.$('#vehicle-form'), formCard = SR.$('#vehicle-form-card'), fmsg = SR.$('#vehicle-msg');
  if (meta) { SR.fillSelect(form.elements.vehicleType, meta.vehicleTypes); SR.fillSelect(form.elements.fuelType, meta.fuelTypes); SR.fillSelect(form.elements.transmission, meta.transmissions); SR.fillSelect(form.elements.locationId, meta.locations, null, (l) => l.id, (l) => l.name); }
  async function fleet(page = 0) {
    SR.loading(fleetEl);
    try {
      const d = await SR.api('/api/staff/vehicles' + SR.qs({ page, size: 10 }));
      fleetEl.innerHTML = `<div class="table-wrap"><table class="table"><thead><tr><th>Vehicle</th><th>Reg.</th><th>Type</th><th>Seats</th><th>Rate/day</th><th>Location</th><th>Status</th><th></th></tr></thead><tbody>` + d.content.map((v) => `<tr><td>${SR.esc(v.make)} ${SR.esc(v.model)}</td><td>${SR.esc(v.registrationNumber)}</td><td>${SR.esc(SR.label(v.vehicleType))}</td><td>${v.seats}</td><td>${SR.money(v.dailyRate)}</td><td>${SR.esc(v.location.name)}</td><td>${SR.pill(v.status)}</td><td class="right nowrap"><button class="btn btn-ghost btn-sm" data-edit='${SR.esc(JSON.stringify(v))}'>Edit</button> ${v.status !== 'RETIRED' ? `<button class="btn btn-ghost btn-sm" data-retire="${v.id}">Retire</button>` : ''}</td></tr>`).join('') + '</tbody></table></div>';
      SR.$$('[data-edit]', fleetEl).forEach((b) => b.addEventListener('click', () => edit(JSON.parse(b.dataset.edit))));
      SR.$$('[data-retire]', fleetEl).forEach((b) => b.addEventListener('click', async () => { if (!confirm('Retire this vehicle? It stays in booking history but can no longer be booked.')) return; try { await SR.api('/api/admin/vehicles/' + b.dataset.retire, { method: 'DELETE' }); SR.toast('Vehicle retired', 'ok'); fleet(page); } catch (e) { SR.fail(e); } }));
      SR.pager(SR.$('#fleet-pager'), d, fleet);
    } catch (e) { SR.error(fleetEl, e); }
  }
  function edit(v) {
    const f = form.elements;
    f.id.value = v ? v.id : '';
    f.registrationNumber.value = v ? v.registrationNumber : ''; f.make.value = v ? v.make : ''; f.model.value = v ? v.model : ''; f.modelYear.value = v ? v.modelYear : new Date().getFullYear();
    f.vehicleType.value = v ? v.vehicleType : meta.vehicleTypes[0]; f.fuelType.value = v ? v.fuelType : meta.fuelTypes[0]; f.transmission.value = v ? v.transmission : meta.transmissions[0];
    f.seats.value = v ? v.seats : 5; f.dailyRate.value = v ? v.dailyRate : ''; f.locationId.value = v ? v.location.id : meta.locations[0].id; f.mileageKm.value = v ? v.mileageKm : 0; f.description.value = v ? (v.description || '') : '';
    f.status.value = v ? v.status : 'AVAILABLE';
    SR.$('#vehicle-form-title').textContent = v ? 'Edit vehicle' : 'Add vehicle';
    fmsg.innerHTML = ''; formCard.hidden = false; formCard.scrollIntoView({ behavior: 'smooth' });
  }
  SR.$('#add-vehicle').addEventListener('click', () => edit(null));
  SR.$('#cancel-vehicle').addEventListener('click', () => { formCard.hidden = true; });
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = form.elements, id = f.id.value;
    const body = { registrationNumber: f.registrationNumber.value, make: f.make.value, model: f.model.value, modelYear: Number(f.modelYear.value), vehicleType: f.vehicleType.value, fuelType: f.fuelType.value, transmission: f.transmission.value, seats: Number(f.seats.value), dailyRate: Number(f.dailyRate.value), status: f.status.value, locationId: Number(f.locationId.value), imageUrl: null, description: f.description.value || null, mileageKm: Number(f.mileageKm.value) };
    try { await SR.api(id ? '/api/admin/vehicles/' + id : '/api/admin/vehicles', { method: id ? 'PUT' : 'POST', body }); SR.toast('Vehicle saved', 'ok'); formCard.hidden = true; fleet(0); }
    catch (err) { SR.showFieldErrors(form, err); fmsg.innerHTML = `<div class="alert bad">${SR.esc(err.message)}</div>`; }
  });

  // ---------------------------------------------------------------- bookings
  const bkEl = SR.$('#bookings'), bkStatus = SR.$('#bk-status');
  SR.fillSelect(bkStatus, ['CONFIRMED', 'PICKED_UP', 'RETURNED', 'CANCELLED'], 'All statuses');
  bkStatus.addEventListener('change', () => bookings(0));
  async function bookings(page = 0) {
    SR.loading(bkEl);
    try {
      const d = await SR.api('/api/admin/bookings' + SR.qs({ status: bkStatus.value, page, size: 12 }));
      if (!d.content.length) { SR.empty(bkEl, 'No bookings with this status.'); SR.$('#bk-pager').innerHTML = ''; return; }
      bkEl.innerHTML = `<div class="table-wrap"><table class="table"><thead><tr><th>Reference</th><th>Customer</th><th>Vehicle</th><th>Dates</th><th>Total</th><th>Status</th><th></th></tr></thead><tbody>` + d.content.map((b) => `<tr><td><a href="/account/bookings/${b.id}">${SR.esc(b.reference)}</a></td><td>${SR.esc(b.customer.fullName)}</td><td>${SR.esc(b.vehicle.make)} ${SR.esc(b.vehicle.model)}</td><td class="nowrap">${SR.fmtDate(b.pickupDate)} &rarr; ${SR.fmtDate(b.returnDate)}</td><td>${SR.money(b.totalPrice)}</td><td>${SR.pill(b.status)}</td><td class="right">${b.status === 'CONFIRMED' ? `<button class="btn btn-ghost btn-sm" data-cancel="${b.id}">Cancel</button>` : ''}</td></tr>`).join('') + '</tbody></table></div>';
      SR.$$('[data-cancel]', bkEl).forEach((b) => b.addEventListener('click', async () => { if (!confirm('Cancel this booking on behalf of the customer?')) return; try { await SR.api('/api/bookings/' + b.dataset.cancel + '/cancel', { method: 'POST', body: { reason: 'Cancelled by administrator' } }); SR.toast('Booking cancelled', 'ok'); bookings(page); } catch (e) { SR.fail(e); } }));
      SR.pager(SR.$('#bk-pager'), d, bookings);
    } catch (e) { SR.error(bkEl, e); }
  }

  // ---------------------------------------------------------------- users
  const usersEl = SR.$('#users'), uform = SR.$('#user-form'), umsg = SR.$('#user-msg');
  async function users(page = 0) {
    SR.loading(usersEl);
    try {
      const d = await SR.api('/api/admin/users' + SR.qs({ page, size: 12 }));
      usersEl.innerHTML = `<div class="table-wrap"><table class="table"><thead><tr><th>Name</th><th>E-mail</th><th>Role</th><th>Active</th><th></th></tr></thead><tbody>` + d.content.map((u) => `<tr><td>${SR.esc(u.fullName)}</td><td>${SR.esc(u.email)}</td><td>${SR.esc(SR.label(u.role))}</td><td>${u.active ? 'Yes' : '<span class="pill CANCELLED">Disabled</span>'}</td><td class="right"><button class="btn btn-ghost btn-sm" data-uid="${u.id}" data-active="${!u.active}">${u.active ? 'Disable' : 'Enable'}</button></td></tr>`).join('') + '</tbody></table></div>';
      SR.$$('[data-uid]', usersEl).forEach((b) => b.addEventListener('click', async () => { try { await SR.api('/api/admin/users/' + b.dataset.uid + '/active', { method: 'PATCH', body: { active: b.dataset.active === 'true' } }); SR.toast('User updated', 'ok'); users(page); } catch (e) { SR.fail(e); } }));
      SR.pager(SR.$('#user-pager'), d, users);
    } catch (e) { SR.error(usersEl, e); }
  }
  uform.addEventListener('submit', async (e) => {
    e.preventDefault(); const f = uform.elements;
    try { await SR.api('/api/admin/users', { method: 'POST', body: { fullName: f.fullName.value, email: f.email.value, password: f.password.value, phone: f.phone.value || null, role: f.role.value } }); SR.toast('User created', 'ok'); uform.reset(); umsg.innerHTML = ''; users(0); }
    catch (err) { SR.showFieldErrors(uform, err); umsg.innerHTML = `<div class="alert bad">${SR.esc(err.message)}</div>`; }
  });

  // ---------------------------------------------------------------- JMS events
  async function events() {
    const n = SR.$('#notif-list'), o = SR.$('#outbox-list');
    SR.loading(n); SR.loading(o);
    try {
      const [nd, od] = await Promise.all([SR.api('/api/admin/notifications?size=10'), SR.api('/api/admin/outbox?size=10')]);
      n.innerHTML = `<div class="table-wrap"><table class="table"><thead><tr><th>Processed</th><th>Event</th><th>Booking</th><th>User</th><th>Message</th></tr></thead><tbody>` + nd.content.map((x) => `<tr><td class="nowrap">${SR.fmtDateTime(x.processedAt)}</td><td>${SR.pill(x.eventType)}</td><td>${x.bookingId}</td><td>${x.userId}</td><td>${SR.esc(x.message)}</td></tr>`).join('') + '</tbody></table></div>';
      o.innerHTML = `<div class="table-wrap"><table class="table"><thead><tr><th>Created</th><th>Event</th><th>Booking</th><th>Status</th><th>Attempts</th><th>Last error</th></tr></thead><tbody>` + od.content.map((x) => `<tr><td class="nowrap">${SR.fmtDateTime(x.createdAt)}</td><td>${SR.pill(x.eventType)}</td><td>${x.bookingId}</td><td>${SR.pill(x.status)}</td><td>${x.attempts}</td><td>${SR.esc(x.lastError || '')}</td></tr>`).join('') + '</tbody></table></div>';
    } catch (e) { SR.error(n, e); o.innerHTML = ''; }
  }
  async function audit() {
    const el = SR.$('#audit-list'); SR.loading(el);
    try {
      const d = await SR.api('/api/admin/audit?size=25');
      el.innerHTML = d.content.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>When</th><th>Actor</th><th>Action</th><th>Entity</th><th>Details</th></tr></thead><tbody>` + d.content.map((a) => `<tr><td class="nowrap">${SR.fmtDateTime(a.createdAt)}</td><td>${SR.esc(a.actor)}</td><td>${SR.esc(a.action)}</td><td>${SR.esc(a.entityType)} ${SR.esc(a.entityId || '')}</td><td>${SR.esc(a.details || '')}</td></tr>`).join('') + '</tbody></table></div>' : '<div class="state">No administrative actions recorded yet.</div>';
    } catch (e) { SR.error(el, e); }
  }
});
