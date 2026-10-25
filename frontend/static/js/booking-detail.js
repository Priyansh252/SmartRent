document.addEventListener('DOMContentLoaded', async () => {
  const root = SR.$('#booking-root'), id = root.dataset.id, body = SR.$('#booking-body');
  if (new URLSearchParams(location.search).has('new')) SR.$('#success').hidden = false;
  async function load() {
    SR.loading(body);
    try {
      await SR.loadMeta();
      const [b, tl] = await Promise.all([SR.api('/api/bookings/' + id), SR.api('/api/bookings/' + id + '/timeline')]);
      const canCancel = b.status === 'CONFIRMED' && root.dataset.role !== 'EMPLOYEE';
      body.innerHTML = `<div class="grid g2" style="align-items:start">
        <div class="card"><div class="row between"><h3 style="margin:0">${SR.esc(b.reference)}</h3>${SR.pill(b.status)}</div>
          <div class="gallery-pic" style="margin:14px 0"><img src="${SR.esc(b.vehicle.imageUrl)}" alt="" style="width:100%" onerror="this.src='/images/cars/sedan.svg'"></div>
          <p><strong>${SR.esc(b.vehicle.make)} ${SR.esc(b.vehicle.model)}</strong> &middot; ${SR.esc(b.vehicle.registrationNumber)}<br><span class="muted">${SR.esc(b.vehicle.location.name)}</span></p>
          <p>${SR.fmtDate(b.pickupDate)} &rarr; ${SR.fmtDate(b.returnDate)} (${b.days} ${b.days === 1 ? 'day' : 'days'})<br><span class="muted small">Pickup date inclusive, return date exclusive.</span></p>
          <p class="muted small">Customer: ${SR.esc(b.customer.fullName)} (${SR.esc(b.customer.email)})</p>
          ${b.cancellationFee != null ? `<div class="alert ${Number(b.cancellationFee) > 0 ? 'warn' : 'info'}">Cancellation fee: ${SR.money(b.cancellationFee)}</div>` : ''}
          ${canCancel ? '<button id="cancel-btn" class="btn btn-ghost">Cancel booking</button>' : ''}</div>
        <div><div class="card" style="margin-bottom:16px"><h3>Agreed price</h3>${b.priceBreakdown ? SR.breakdownHtml(b.priceBreakdown) : `<p><b>${SR.money(b.totalPrice)}</b></p>`}<p class="muted small">This amount is locked for the life of the booking.</p></div>
        <div class="card"><h3>Status timeline</h3><ul class="timeline">${tl.map((t) => `<li><strong>${SR.esc(SR.label(t.toStatus))}</strong><div class="muted small">${SR.fmtDateTime(t.changedAt)} &middot; ${SR.esc(t.changedBy)}</div><div class="small">${SR.esc(t.note || '')}</div></li>`).join('')}</ul></div></div></div>`;
      const cb = SR.$('#cancel-btn');
      if (cb) cb.addEventListener('click', async () => { if (!confirm('Cancel this booking?')) return; try { await SR.api('/api/bookings/' + id + '/cancel', { method: 'POST', body: {} }); SR.toast('Booking cancelled', 'ok'); load(); } catch (e) { SR.fail(e); } });
    } catch (e) { SR.error(body, e); }
  }
  load();
});
