document.addEventListener('DOMContentLoaded', () => {
  const list = SR.$('#bookings'), pagerEl = SR.$('#bk-pager'), notes = SR.$('#notes'), notePager = SR.$('#note-pager');
  const isCustomer = SR.$('#account-root').dataset.role === 'CUSTOMER';
  async function loadBookings(page = 0) {
    if (!isCustomer) { SR.empty(list, 'Staff accounts do not have personal bookings. Use the Staff or Admin dashboard.'); return; }
    SR.loading(list);
    try {
      const d = await SR.api('/api/bookings/mine' + SR.qs({ page, size: 6 }));
      if (!d.content.length) { list.innerHTML = '<div class="state">You have no bookings yet. <a href="/vehicles">Find a car</a> or <a href="/recommend">get a recommendation</a>.</div>'; pagerEl.innerHTML = ''; return; }
      list.innerHTML = `<div class="table-wrap"><table class="table"><thead><tr><th>Reference</th><th>Vehicle</th><th>Dates</th><th>Total</th><th>Status</th><th></th></tr></thead><tbody>` + d.content.map((b) => `<tr><td><a href="/account/bookings/${b.id}">${SR.esc(b.reference)}</a></td><td>${SR.esc(b.vehicle.make)} ${SR.esc(b.vehicle.model)}</td><td class="nowrap">${SR.fmtDate(b.pickupDate)} &rarr; ${SR.fmtDate(b.returnDate)}</td><td>${SR.money(b.totalPrice)}</td><td>${SR.pill(b.status)}</td><td class="right">${b.status === 'CONFIRMED' ? `<button class="btn btn-ghost btn-sm" data-cancel="${b.id}" data-ref="${SR.esc(b.reference)}">Cancel</button>` : ''}</td></tr>`).join('') + '</tbody></table></div>';
      SR.$$('[data-cancel]', list).forEach((btn) => btn.addEventListener('click', () => cancel(btn.dataset.cancel, btn.dataset.ref, page)));
      SR.pager(pagerEl, d, loadBookings);
    } catch (e) { SR.error(list, e); }
  }
  async function cancel(id, ref, page) {
    if (!confirm('Cancel booking ' + ref + '? A late-cancellation fee may apply (see the booking page for the policy).')) return;
    try { const b = await SR.api('/api/bookings/' + id + '/cancel', { method: 'POST', body: {} }); SR.toast('Booking cancelled' + (Number(b.cancellationFee) > 0 ? '. Fee: ' + SR.money(b.cancellationFee) : ' - no fee.'), 'ok'); loadBookings(page); setTimeout(() => loadNotes(0), 1500); } catch (e) { SR.fail(e); }
  }
  async function loadNotes(page = 0) {
    SR.loading(notes);
    try {
      const d = await SR.api('/api/notifications/mine' + SR.qs({ page, size: 6 }));
      if (!d.content.length) { SR.empty(notes, 'No notifications yet. They appear here after the message broker processes your booking events.'); notePager.innerHTML = ''; return; }
      notes.innerHTML = d.content.map((n) => `<div style="padding:10px 0;border-bottom:1px solid var(--line)"><div class="row between"><strong>${SR.esc(n.title)}</strong><span class="muted small">${SR.fmtDateTime(n.processedAt)}</span></div><div class="muted">${SR.esc(n.message)}</div></div>`).join('');
      SR.pager(notePager, d, loadNotes);
    } catch (e) { SR.error(notes, e); }
  }
  loadBookings(); loadNotes();
});
