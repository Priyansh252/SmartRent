document.addEventListener('DOMContentLoaded', async () => {
  const form = SR.$('#pricing-form'), msg = SR.$('#pricing-msg'), peaksEl = SR.$('#peaks'), pform = SR.$('#peak-form');
  const num = ['weekendSurcharge', 'demandBaseline', 'demandWeight', 'maxDemandSurcharge', 'maxDemandDiscount', 'availabilityWeight', 'availabilityThreshold', 'discount7Days', 'discount14Days', 'discount30Days', 'minMultiplier', 'maxMultiplier', 'lateCancelFeeRate'];
  const ints = ['demandWindowDays', 'freeCancelDays'];
  await SR.loadMeta().catch(() => {});
  async function loadConfig() {
    try {
      const c = await SR.api('/api/admin/pricing/config');
      [...num, ...ints].forEach((k) => { form.elements[k].value = c[k]; });
      SR.$('#updated').textContent = 'Last updated ' + SR.fmtDateTime(c.updatedAt) + ' by ' + c.updatedBy;
    } catch (e) { SR.fail(e); }
  }
  form.addEventListener('submit', async (e) => {
    e.preventDefault(); msg.innerHTML = '';
    const body = {}; num.forEach((k) => { body[k] = Number(form.elements[k].value); }); ints.forEach((k) => { body[k] = parseInt(form.elements[k].value, 10); });
    try { await SR.api('/api/admin/pricing/config', { method: 'PUT', body }); SR.toast('Pricing rules saved. They apply to new quotes; confirmed bookings keep their agreed price.', 'ok'); loadConfig(); }
    catch (err) { SR.showFieldErrors(form, err); msg.innerHTML = `<div class="alert bad">${SR.esc(err.message)}</div>`; }
  });
  async function loadPeaks() {
    SR.loading(peaksEl);
    try {
      const list = await SR.api('/api/admin/pricing/peaks');
      peaksEl.innerHTML = list.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>Name</th><th>From</th><th>To (inclusive)</th><th>Surcharge</th><th>Active</th><th></th></tr></thead><tbody>` + list.map((p) => `<tr><td>${SR.esc(p.name)}</td><td>${SR.fmtDate(p.startDate)}</td><td>${SR.fmtDate(p.endDate)}</td><td>+${(Number(p.surcharge) * 100).toFixed(0)}%</td><td>${p.active ? 'Yes' : 'No'}</td><td class="right nowrap"><button class="btn btn-ghost btn-sm" data-toggle="${p.id}" data-p='${SR.esc(JSON.stringify(p))}'>${p.active ? 'Deactivate' : 'Activate'}</button> <button class="btn btn-ghost btn-sm" data-del="${p.id}">Delete</button></td></tr>`).join('') + '</tbody></table></div>' : '<div class="state">No peak periods configured.</div>';
      SR.$$('[data-del]', peaksEl).forEach((b) => b.addEventListener('click', async () => { if (!confirm('Delete this peak period?')) return; try { await SR.api('/api/admin/pricing/peaks/' + b.dataset.del, { method: 'DELETE' }); loadPeaks(); } catch (e) { SR.fail(e); } }));
      SR.$$('[data-toggle]', peaksEl).forEach((b) => b.addEventListener('click', async () => { const p = JSON.parse(b.dataset.p); p.active = !p.active; try { await SR.api('/api/admin/pricing/peaks/' + p.id, { method: 'PUT', body: p }); loadPeaks(); } catch (e) { SR.fail(e); } }));
    } catch (e) { SR.error(peaksEl, e); }
  }
  pform.addEventListener('submit', async (e) => {
    e.preventDefault(); const f = pform.elements;
    try { await SR.api('/api/admin/pricing/peaks', { method: 'POST', body: { name: f.name.value, startDate: f.startDate.value, endDate: f.endDate.value, surcharge: Number(f.surcharge.value), active: true } }); pform.reset(); SR.toast('Peak period added', 'ok'); loadPeaks(); }
    catch (err) { SR.showFieldErrors(pform, err); SR.toast(err.message, 'bad'); }
  });
  // live explainer: pick a vehicle and dates, see exactly how the saved rules price it
  const qv = SR.$('#try-vehicle'), qf = SR.$('#try-form'), qout = SR.$('#try-out');
  try { const d = await SR.api('/api/vehicles?size=50'); SR.fillSelect(qv, d.content, null, (i) => i.vehicle.id, (i) => i.vehicle.make + ' ' + i.vehicle.model + ' (' + SR.money(i.vehicle.dailyRate) + '/day)'); } catch (e) { SR.fail(e); }
  qf.elements.pickupDate.value = SR.addDays(SR.today(), 3); qf.elements.returnDate.value = SR.addDays(SR.today(), 8);
  qf.addEventListener('submit', async (e) => {
    e.preventDefault(); SR.loading(qout);
    try { const q = await SR.api('/api/vehicles/' + qv.value + '/quote' + SR.qs({ pickupDate: qf.elements.pickupDate.value, returnDate: qf.elements.returnDate.value })); qout.innerHTML = SR.breakdownHtml(q.price); }
    catch (err) { qout.innerHTML = `<div class="alert bad">${SR.esc(err.message)}</div>`; }
  });
  loadConfig(); loadPeaks();
});
