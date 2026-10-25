document.addEventListener('DOMContentLoaded', () => {
  const due = SR.$('#due'), active = SR.$('#active'), fleet = SR.$('#fleet'), fleetPager = SR.$('#fleet-pager');
  const show = SR.tabs(SR.$('#staff-root'));
  function row(b, action) {
    return `<tr><td><a href="/account/bookings/${b.id}">${SR.esc(b.reference)}</a></td><td>${SR.esc(b.customer.fullName)}</td><td>${SR.esc(b.vehicle.make)} ${SR.esc(b.vehicle.model)}<div class="muted small">${SR.esc(b.vehicle.registrationNumber)}</div></td><td class="nowrap">${SR.fmtDate(b.pickupDate)} &rarr; ${SR.fmtDate(b.returnDate)}</td><td>${SR.esc(b.vehicle.location.name)}</td><td class="right"><button class="btn btn-sm" data-act="${action}" data-id="${b.id}">${action === 'pickup' ? 'Record pickup' : 'Record return'}</button></td></tr>`;
  }
  const table = (rows) => `<div class="table-wrap"><table class="table"><thead><tr><th>Reference</th><th>Customer</th><th>Vehicle</th><th>Dates</th><th>Location</th><th></th></tr></thead><tbody>${rows}</tbody></table></div>`;
  async function loadBookings() {
    SR.loading(due); SR.loading(active);
    try {
      const d = await SR.api('/api/staff/dashboard');
      SR.$('#kpi-due').textContent = d.dueCount; SR.$('#kpi-active').textContent = d.activeCount;
      due.innerHTML = d.pickupsDue.length ? table(d.pickupsDue.map((b) => row(b, 'pickup')).join('')) : '<div class="state">No pickups are due.</div>';
      active.innerHTML = d.activeRentals.length ? table(d.activeRentals.map((b) => row(b, 'return')).join('')) : '<div class="state">No vehicles are currently rented out.</div>';
      SR.$$('[data-act]').forEach((btn) => btn.addEventListener('click', async () => {
        btn.disabled = true;
        try { await SR.api('/api/staff/bookings/' + btn.dataset.id + '/' + btn.dataset.act, { method: 'POST' }); SR.toast(btn.dataset.act === 'pickup' ? 'Pickup recorded' : 'Return recorded', 'ok'); loadBookings(); loadFleet(0); } catch (e) { SR.fail(e); btn.disabled = false; }
      }));
    } catch (e) { SR.error(due, e); SR.error(active, e); }
  }
  async function loadFleet(page = 0) {
    SR.loading(fleet);
    try {
      const d = await SR.api('/api/staff/vehicles' + SR.qs({ page, size: 12 }));
      fleet.innerHTML = `<div class="table-wrap"><table class="table"><thead><tr><th>Vehicle</th><th>Registration</th><th>Location</th><th>Status</th><th></th></tr></thead><tbody>` + d.content.map((v) => `<tr><td>${SR.esc(v.make)} ${SR.esc(v.model)}</td><td>${SR.esc(v.registrationNumber)}</td><td>${SR.esc(v.location.name)}</td><td>${SR.pill(v.status)}</td><td class="right">${v.status === 'RETIRED' ? '' : `<button class="btn btn-ghost btn-sm" data-vid="${v.id}" data-to="${v.status === 'AVAILABLE' ? 'MAINTENANCE' : 'AVAILABLE'}">${v.status === 'AVAILABLE' ? 'Send to maintenance' : 'Mark available'}</button>`}</td></tr>`).join('') + '</tbody></table></div>';
      SR.$$('[data-vid]', fleet).forEach((b) => b.addEventListener('click', async () => { try { await SR.api('/api/staff/vehicles/' + b.dataset.vid + '/status', { method: 'PATCH', body: { status: b.dataset.to } }); SR.toast('Status updated', 'ok'); loadFleet(page); } catch (e) { SR.fail(e); } }));
      SR.pager(fleetPager, d, loadFleet);
    } catch (e) { SR.error(fleet, e); }
  }
  loadBookings(); loadFleet(0);
});
