document.addEventListener('DOMContentLoaded', async () => {
  const form = SR.$('#search-form');
  const pick = form.elements.pickupDate, ret = form.elements.returnDate;
  pick.min = SR.today(); pick.value = SR.addDays(SR.today(), 1); ret.min = SR.addDays(SR.today(), 2); ret.value = SR.addDays(SR.today(), 4);
  pick.addEventListener('change', () => { ret.min = SR.addDays(pick.value, 1); if (ret.value <= pick.value) ret.value = SR.addDays(pick.value, 1); });
  try {
    const meta = await SR.loadMeta();
    SR.fillSelect(form.elements.locationId, meta.locations, 'Any location', (l) => l.id, (l) => l.name);
    SR.fillSelect(form.elements.type, meta.vehicleTypes, 'Any type');
  } catch (e) { SR.fail(e); }
  form.addEventListener('submit', (ev) => {
    ev.preventDefault();
    const f = form.elements;
    location.href = '/vehicles' + SR.qs({ pickupDate: f.pickupDate.value, returnDate: f.returnDate.value, locationId: f.locationId.value, type: f.type.value, minSeats: f.minSeats.value });
  });
  // live headline numbers and featured cars come from the API
  try {
    const page = await SR.api('/api/vehicles?size=4&sort=price&direction=desc');
    const meta = await SR.loadMeta();
    SR.$('#stat-vehicles').textContent = page.totalElements;
    SR.$('#stat-locations').textContent = meta.locations.length;
    SR.$('#stat-types').textContent = meta.vehicleTypes.length;
    SR.$('#featured').innerHTML = page.content.map((i) => SR.vehicleCard(i)).join('') || '';
    if (!page.content.length) SR.empty(SR.$('#featured'), 'No vehicles are available right now.');
  } catch (e) { SR.error(SR.$('#featured'), e); }
});
