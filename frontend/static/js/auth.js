document.addEventListener('DOMContentLoaded', () => {
  const form = SR.$('#register-form');
  if (!form) return;
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = form.elements, box = SR.$('#form-msg');
    box.innerHTML = '';
    if (f.password.value !== f.confirm.value) { box.innerHTML = '<div class="alert bad">Passwords do not match.</div>'; return; }
    const btn = SR.$('button[type=submit]', form); btn.disabled = true;
    try {
      await SR.api('/api/auth/register', { method: 'POST', body: { fullName: f.fullName.value, email: f.email.value, password: f.password.value, phone: f.phone.value || null } });
      location.href = '/login?registered';
    } catch (err) { SR.showFieldErrors(form, err); box.innerHTML = `<div class="alert bad">${SR.esc(err.message)}</div>`; btn.disabled = false; }
  });
});
