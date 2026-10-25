document.addEventListener('DOMContentLoaded', async () => {
  const id = SR.$('#vehicle-root').dataset.id;
  const authed = SR.$('#vehicle-root').dataset.authed === 'true';
  const role = SR.$('#vehicle-root').dataset.role;
  const info = SR.$('#info'), quoteBox = SR.$('#quote'), form = SR.$('#book-form'), msg = SR.$('#book-msg');
  const f = form.elements;
  const p = new URLSearchParams(location.search);
  f.pickupDate.min = SR.today();
  f.pickupDate.value = p.get('pickupDate') || SR.addDays(SR.today(), 1);
  f.returnDate.value = p.get('returnDate') || SR.addDays(f.pickupDate.value, 3);
  let lastQuote = null;
  const idemKey = (crypto.randomUUID && crypto.randomUUID()) || String(Date.now()) + Math.random();

  try {
    await SR.loadMeta();
    const v = await SR.api('/api/vehicles/' + id);
    document.title = v.make + ' ' + v.model + ' - SmartRent';
    SR.$('#pic').src = v.imageUrl || '/images/cars/' + v.vehicleType.toLowerCase() + '.svg';
    info.innerHTML = `<h1 style="font-size:2.6rem">${SR.esc(v.make)} ${SR.esc(v.model)}</h1><p class="muted">${SR.esc(v.description || '')}</p>
      <div class="chips" style="margin:10px 0">${[SR.label(v.vehicleType), v.seats + ' seats', SR.label(v.fuelType), SR.label(v.transmission), v.modelYear, v.mileageKm.toLocaleString('en-IN') + ' km'].map((c) => `<span class="chip">${SR.esc(c)}</span>`).join('')}</div>
      <p><strong>Pickup &amp; return:</strong> ${SR.esc(v.location.name)}<br><span class="muted small">${SR.esc(v.location.address || '')}, ${SR.esc(v.location.city)}</span></p>
      <p>Base rate <strong>${SR.money(v.dailyRate)}</strong> per day. The final price depends on dates, demand and availability and is shown below.</p>
      <p>${SR.pill(v.status)}</p>`;
    const ranges = await SR.api('/api/vehicles/' + id + '/booked-ranges');
    SR.$('#booked').innerHTML = ranges.length ? ranges.map((r) => `<span class="chip">${SR.fmtDate(r.pickupDate)} &rarr; ${SR.fmtDate(r.returnDate)}</span>`).join(' ') : '<span class="muted">No upcoming bookings - wide open.</span>';
  } catch (e) { SR.error(info, e); return; }

  async function refreshQuote() {
    msg.innerHTML = '';
    if (!f.pickupDate.value || !f.returnDate.value) return;
    SR.loading(quoteBox, 'Calculating price...');
    try {
      const q = await SR.api('/api/vehicles/' + id + '/quote' + SR.qs({ pickupDate: f.pickupDate.value, returnDate: f.returnDate.value }));
      lastQuote = q;
      quoteBox.innerHTML = SR.breakdownHtml(q.price) + `<div class="alert ${q.available ? 'ok' : 'bad'}">${SR.esc(q.message)}</div>`;
      SR.$('#book-btn').disabled = !q.available;
    } catch (e) { lastQuote = null; SR.$('#book-btn').disabled = true; quoteBox.innerHTML = `<div class="alert bad">${SR.esc(e.message)}</div>`; }
  }
  f.pickupDate.addEventListener('change', () => { f.returnDate.min = SR.addDays(f.pickupDate.value, 1); if (f.returnDate.value <= f.pickupDate.value) f.returnDate.value = SR.addDays(f.pickupDate.value, 1); refreshQuote(); });
  f.returnDate.addEventListener('change', refreshQuote);
  refreshQuote();

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!authed) { location.href = '/login?next=' + encodeURIComponent(location.pathname + location.search); return; }
    if (role !== 'CUSTOMER') { msg.innerHTML = '<div class="alert warn">Only customer accounts can create bookings.</div>'; return; }
    const btn = SR.$('#book-btn'); btn.disabled = true;
    try {
      const b = await SR.api('/api/bookings', { method: 'POST', headers: { 'Idempotency-Key': idemKey },
        body: { vehicleId: Number(id), pickupDate: f.pickupDate.value, returnDate: f.returnDate.value, expectedTotal: lastQuote ? lastQuote.price.totalPrice : null } });
      location.href = '/account/bookings/' + b.id + '?new=1';
    } catch (err) {
      msg.innerHTML = `<div class="alert bad">${SR.esc(err.message)}${err.code === 'PRICE_CHANGED' ? ' The price was refreshed below.' : ''}</div>`;
      refreshQuote().then(() => { msg.innerHTML = `<div class="alert bad">${SR.esc(err.message)}</div>`; });
    }
  });
});
