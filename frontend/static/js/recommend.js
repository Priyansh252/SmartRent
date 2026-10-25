document.addEventListener('DOMContentLoaded', async () => {
  const form = SR.$('#rec-form'), out = SR.$('#rec-results'), f = form.elements;
  const meta = await SR.loadMeta().catch(SR.fail);
  if (meta) { SR.fillSelect(f.locationId, meta.locations, 'No preference', (l) => l.id, (l) => l.name); SR.fillSelect(f.type, meta.vehicleTypes, 'No preference'); SR.fillSelect(f.preferredFuel, meta.fuelTypes, 'No preference'); SR.fillSelect(f.preferredTransmission, meta.transmissions, 'No preference'); }
  f.pickupDate.min = SR.today(); f.pickupDate.value = SR.addDays(SR.today(), 2); f.returnDate.value = SR.addDays(SR.today(), 5);
  f.pickupDate.addEventListener('change', () => { if (f.returnDate.value <= f.pickupDate.value) f.returnDate.value = SR.addDays(f.pickupDate.value, 1); });

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    SR.loading(out, 'Ranking available vehicles...');
    const body = { pickupDate: f.pickupDate.value, returnDate: f.returnDate.value, seats: Number(f.seats.value) || 1,
      maxDailyBudget: f.maxDailyBudget.value ? Number(f.maxDailyBudget.value) : null, type: f.type.value || null, locationId: f.locationId.value ? Number(f.locationId.value) : null,
      sameLocationOnly: f.sameLocationOnly.checked, preferredFuel: f.preferredFuel.value || null, preferredTransmission: f.preferredTransmission.value || null };
    try {
      const r = await SR.api('/api/recommendations', { method: 'POST', body });
      const w = Object.entries(r.weights).map(([k, v]) => `${k} ${Math.round(v * 100)}%`).join(' · ');
      let html = `<div class="alert ${r.recommendations.length ? 'info' : 'warn'}">${SR.esc(r.message)}</div>`;
      html += r.recommendations.map((i) => {
        const v = i.vehicle;
        const q = SR.qs({ pickupDate: body.pickupDate, returnDate: body.returnDate });
        const parts = Object.entries(i.componentScores).map(([k, s]) => `<div class="hbar"><span>${SR.esc(SR.label(k))}</span><div class="track"><div class="fill" style="width:${Math.round(s * 100)}%"></div></div><span>${Math.round(s * 100)}%</span></div>`).join('');
        return `<div class="card" style="margin-bottom:16px"><div class="grid" style="grid-template-columns:90px 1.1fr 1.4fr;align-items:start">
          <div><div class="rank">#${i.rank}</div><span class="score">${i.score}</span><div class="muted small">match score</div></div>
          <div><img src="${SR.esc(v.imageUrl)}" alt="" style="width:100%;max-width:260px" onerror="this.src='/images/cars/sedan.svg'"><h3>${SR.esc(v.make)} ${SR.esc(v.model)}</h3>
            <div class="chips"><span class="chip">${v.seats} seats</span><span class="chip">${SR.esc(SR.label(v.vehicleType))}</span><span class="chip">${SR.esc(SR.label(v.fuelType))}</span><span class="chip">${SR.esc(v.location.name)}</span></div>
            <p style="margin-top:10px"><b style="font:700 1.6rem var(--font-head)">${SR.money(i.totalPrice)}</b> <span class="muted">total · avg ${SR.money(i.averageDailyPrice)}/day${i.distanceKm != null ? ' · ' + i.distanceKm + ' km away' : ''}</span></p>
            <a class="btn" href="/vehicles/${v.id}${q}">Review &amp; book</a></div>
          <div><h4>Why this vehicle</h4><ul style="padding-left:18px;margin:0 0 10px">${i.reasons.map((x) => `<li>${SR.esc(x)}</li>`).join('')}</ul>${parts}</div></div></div>`;
      }).join('');
      html += `<p class="muted small">Weights used: ${SR.esc(w)}. Recommendations never reserve a vehicle; availability and price are re-checked when you book.</p>`;
      out.innerHTML = html;
    } catch (err) { out.innerHTML = `<div class="alert bad">${SR.esc(err.message)}</div>`; }
  });
});
