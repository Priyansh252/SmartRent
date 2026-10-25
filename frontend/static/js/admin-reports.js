document.addEventListener('DOMContentLoaded', async () => {
  const form = SR.$('#report-form'), hist = SR.$('#history'), msg = SR.$('#report-msg'), f = form.elements;
  SR.fillSelect(f.type, ['REVENUE_SUMMARY', 'BOOKING_COUNTS', 'VEHICLE_USAGE', 'FLEET_UTILIZATION', 'FLEET_AVAILABILITY']);
  f.from.value = SR.addDays(SR.today(), -30); f.to.value = SR.addDays(SR.today(), 1);
  async function load(page = 0) {
    SR.loading(hist);
    try {
      const d = await SR.api('/api/admin/reports' + SR.qs({ page, size: 10 }));
      if (!d.content.length) { SR.empty(hist, 'No reports generated yet. Run one above, or wait for the scheduled job.'); SR.$('#hist-pager').innerHTML = ''; return; }
      hist.innerHTML = `<div class="table-wrap"><table class="table"><thead><tr><th>#</th><th>Report</th><th>Range (to exclusive)</th><th>Status</th><th>Trigger</th><th>Started</th><th>Rows</th><th></th></tr></thead><tbody>` + d.content.map((r) => `<tr><td>${r.id}</td><td>${SR.esc(SR.label(r.reportType))}</td><td class="nowrap">${SR.fmtDate(r.fromDate)} &rarr; ${SR.fmtDate(r.toDate)}</td><td>${SR.pill(r.status)}${r.errorMessage ? `<div class="muted small">${SR.esc(r.errorMessage)}</div>` : ''}</td><td>${SR.esc(r.triggerSource)}</td><td class="nowrap">${SR.fmtDateTime(r.startedAt)}</td><td>${r.rowCount ?? '-'}</td><td class="right">${r.downloadable ? `<a class="btn btn-ghost btn-sm" href="/api/admin/reports/${r.id}/download">Download CSV</a>` : ''}</td></tr>`).join('') + '</tbody></table></div>';
      SR.pager(SR.$('#hist-pager'), d, load);
    } catch (e) { SR.error(hist, e); }
  }
  form.addEventListener('submit', async (e) => {
    e.preventDefault(); msg.innerHTML = ''; const btn = SR.$('button[type=submit]', form); btn.disabled = true; btn.textContent = 'Running Spring Batch job...';
    try { const r = await SR.api('/api/admin/reports', { method: 'POST', body: { type: f.type.value, from: f.from.value, to: f.to.value } }); SR.toast('Report ' + r.status.toLowerCase() + (r.rowCount != null ? ' - ' + r.rowCount + ' rows' : ''), r.status === 'COMPLETED' ? 'ok' : 'bad'); load(0); }
    catch (err) { msg.innerHTML = `<div class="alert bad">${SR.esc(err.message)}</div>`; }
    finally { btn.disabled = false; btn.textContent = 'Generate report'; }
  });
  load();
});
