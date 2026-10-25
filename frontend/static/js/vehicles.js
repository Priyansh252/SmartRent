document.addEventListener('DOMContentLoaded', async () => {
  const form = SR.$('#filters'), results = SR.$('#results'), pagerEl = SR.$('#pager'), summary = SR.$('#summary');
  const f = form.elements;
  const meta = await SR.loadMeta().catch(SR.fail);
  if (meta) {
    SR.fillSelect(f.locationId, meta.locations, 'Any location', (l) => l.id, (l) => l.name + ' - ' + l.city);
    SR.fillSelect(f.type, meta.vehicleTypes, 'Any type');
    SR.fillSelect(f.fuelType, meta.fuelTypes, 'Any fuel');
    SR.fillSelect(f.transmission, meta.transmissions, 'Any');
  }
  const params = new URLSearchParams(location.search);
  ['pickupDate', 'returnDate', 'locationId', 'type', 'minSeats', 'minPrice', 'maxPrice', 'fuelType', 'transmission', 'sort', 'direction'].forEach((k) => { if (params.get(k) && f[k]) f[k].value = params.get(k); });
  f.pickupDate.min = SR.today();
  let page = 0;

  async function load() {
    SR.loading(results, 'Searching the fleet...');
    const q = { pickupDate: f.pickupDate.value, returnDate: f.returnDate.value, type: f.type.value, minSeats: f.minSeats.value, locationId: f.locationId.value, fuelType: f.fuelType.value, transmission: f.transmission.value, minPrice: f.minPrice.value, maxPrice: f.maxPrice.value, sort: f.sort.value, direction: f.direction.value, page, size: 9 };
    history.replaceState(null, '', '/vehicles' + SR.qs({ ...q, page: '', size: '' }));
    try {
      const data = await SR.api('/api/vehicles' + SR.qs(q));
      const dated = q.pickupDate && q.returnDate;
      summary.textContent = data.totalElements + ' vehicle' + (data.totalElements === 1 ? '' : 's') + (dated ? ' free ' + SR.fmtDate(q.pickupDate) + ' to ' + SR.fmtDate(q.returnDate) : ' available');
      if (!data.content.length) { SR.empty(results, 'No vehicles match these filters. Try other dates, a wider price range or fewer requirements.'); pagerEl.innerHTML = ''; return; }
      const extra = dated ? SR.qs({ pickupDate: q.pickupDate, returnDate: q.returnDate }) : '';
      results.innerHTML = '<div class="grid g3">' + data.content.map((i) => SR.vehicleCard(i, extra)).join('') + '</div>';
      SR.pager(pagerEl, data, (p) => { page = p; load(); window.scrollTo({ top: 0, behavior: 'smooth' }); });
    } catch (e) {
      results.innerHTML = '';
      const a = document.createElement('div'); a.className = 'alert bad'; a.textContent = e.message; results.appendChild(a); pagerEl.innerHTML = ''; summary.textContent = '';
    }
  }
  form.addEventListener('submit', (e) => { e.preventDefault(); page = 0; load(); });
  SR.$('#reset').addEventListener('click', () => { form.reset(); page = 0; load(); });
  load();
});
