import { api } from '../api';
import { CURRENCIES } from '../currencies';
import { errorBanner, escapeHtml, setNumberLocale, successBanner } from '../ui';
import type { ImportResult, RatesResponse } from '../types';

const LOCALES = [
  'en-US', 'en-GB', 'tr-TR', 'de-DE', 'fr-FR', 'es-ES',
  'it-IT', 'nl-NL', 'pt-BR', 'ja-JP', 'zh-CN', 'ko-KR'
];
const DATE_FORMATS = ['yyyy-MM-dd', 'dd/MM/yyyy', 'MM/dd/yyyy'];

export async function renderSettings(root: HTMLElement): Promise<void> {
  root.innerHTML = `
    <div class="page-header"><h1>Settings</h1></div>
    <div class="panel">
      <h3>Change password</h3>
      <div class="form-row">
        <label>Current password <input id="s-old" type="password" /></label>
        <label>New password <input id="s-new" type="password" /></label>
      </div>
      <button id="s-password">Change password</button>
      <div id="s-pwd-msg"></div>
    </div>
    <div class="panel">
      <h3>Preferences</h3>
      <div class="form-row">
        <label>Base currency <select id="s-base-currency"></select></label>
        <label>Number / date locale
          <select id="s-locale">${LOCALES.map((l) => `<option>${l}</option>`).join('')}</select>
        </label>
        <label>Date format
          <select id="s-date-format">${DATE_FORMATS.map((d) => `<option>${d}</option>`).join('')}</select>
        </label>
      </div>
      <button id="s-preferences">Save preferences</button>
      <div id="s-pref-msg"></div>
    </div>
    <div class="panel">
      <h3>Backup</h3>
      <p><a href="/api/backup/export" target="_blank">Download full JSON backup</a></p>
      <div class="form-row">
        <label>Restore JSON backup (overwrites data)
          <input id="s-json-file" type="file" accept="application/json" />
        </label>
        <label>Replace existing data
          <input id="s-replace" type="checkbox" />
        </label>
      </div>
      <button id="s-json-import" class="danger">Restore</button>
      <div class="form-row" style="margin-top:0.75rem">
        <label>Import CSV transactions
          <input id="s-csv-file" type="file" accept=".csv,text/csv" />
        </label>
      </div>
      <button id="s-csv-import">Import CSV</button>
      <div id="s-backup-msg"></div>
    </div>`;

  const pwdMsg = root.querySelector<HTMLElement>('#s-pwd-msg')!;
  const prefMsg = root.querySelector<HTMLElement>('#s-pref-msg')!;
  const backupMsg = root.querySelector<HTMLElement>('#s-backup-msg')!;
  const baseCurrencySelect = root.querySelector<HTMLSelectElement>('#s-base-currency')!;
  const localeSelect = root.querySelector<HTMLSelectElement>('#s-locale')!;
  const dateFormatSelect = root.querySelector<HTMLSelectElement>('#s-date-format')!;

  baseCurrencySelect.innerHTML = CURRENCIES.map((c) => `<option>${c}</option>`).join('');

  // Preferences
  const loadPreferences = async (): Promise<void> => {
    try {
      const [settings, rates] = await Promise.all([
        api.get<Record<string, string>>('/api/settings'),
        api.get<RatesResponse>('/api/rates')
      ]);
      localeSelect.value = settings.locale ?? 'en-US';
      dateFormatSelect.value = settings.dateFormat ?? 'yyyy-MM-dd';
      baseCurrencySelect.value = rates.base;
      setNumberLocale(settings.locale);
    } catch (err) {
      prefMsg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to load preferences');
    }
  };

  root.querySelector<HTMLButtonElement>('#s-preferences')!.addEventListener('click', async () => {
    prefMsg.innerHTML = '';
    const locale = localeSelect.value;
    try {
      await api.put('/api/settings', { locale, dateFormat: dateFormatSelect.value });
      await api.put('/api/rates/base', { currency: baseCurrencySelect.value });
      setNumberLocale(locale);
      prefMsg.innerHTML = successBanner('Preferences saved');
    } catch (err) {
      prefMsg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to save preferences');
    }
  });

  // Password
  root.querySelector<HTMLButtonElement>('#s-password')!.addEventListener('click', async () => {
    pwdMsg.innerHTML = '';
    const payload = {
      oldPassword: (root.querySelector<HTMLInputElement>('#s-old')!).value,
      newPassword: (root.querySelector<HTMLInputElement>('#s-new')!).value
    };
    try {
      await api.post('/api/auth/password', payload);
      pwdMsg.innerHTML = successBanner('Password changed');
      (root.querySelector<HTMLInputElement>('#s-old')!).value = '';
      (root.querySelector<HTMLInputElement>('#s-new')!).value = '';
    } catch (err) {
      pwdMsg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to change password');
    }
  });

  // Backup
  root.querySelector<HTMLButtonElement>('#s-json-import')!.addEventListener('click', async () => {
    backupMsg.innerHTML = '';
    const file = (root.querySelector<HTMLInputElement>('#s-json-file')!).files?.[0];
    if (!file) {
      backupMsg.innerHTML = errorBanner('Choose a JSON backup file first');
      return;
    }
    try {
      const text = await file.text();
      const data = JSON.parse(text);
      const replace = (root.querySelector<HTMLInputElement>('#s-replace')!).checked;
      await api.post(`/api/backup/import?replace=${replace}`, data);
      backupMsg.innerHTML = successBanner('Backup restored');
    } catch (err) {
      backupMsg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Restore failed');
    }
  });

  root.querySelector<HTMLButtonElement>('#s-csv-import')!.addEventListener('click', async () => {
    backupMsg.innerHTML = '';
    const file = (root.querySelector<HTMLInputElement>('#s-csv-file')!).files?.[0];
    if (!file) {
      backupMsg.innerHTML = errorBanner('Choose a CSV file first');
      return;
    }
    try {
      const form = new FormData();
      form.append('file', file);
      const headers: Record<string, string> = { 'X-XSRF-TOKEN': csrfTokenForImport() };
      const res = await fetch('/api/backup/import/csv', { method: 'POST', credentials: 'same-origin', headers, body: form });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.detail ?? 'CSV import failed');
      }
      const result = (await res.json()) as ImportResult;
      backupMsg.innerHTML = successBanner(`Imported ${result.imported} rows` + (result.errors.length ? `, ${result.errors.length} errors` : ''));
      if (result.errors.length) {
        const errorLines = result.errors.map((e) => `Row ${e.row}: ${e.message}`).join('<br>');
        backupMsg.innerHTML += `<div class="error-banner">${errorLines}</div>`;
      }
    } catch (err) {
      backupMsg.innerHTML = errorBanner(err instanceof Error ? err.message : 'CSV import failed');
    }
  });

  await loadPreferences();
}

/**
 * The CSRF token is read fresh from the cookie written by the backend so the
 * multipart CSV import can include it without exposing it to this module.
 */
function csrfTokenForImport(): string {
  const cookie = document.cookie
    .split('; ')
    .find((row) => row.startsWith('XSRF-TOKEN='));
  return cookie ? decodeURIComponent(cookie.split('=')[1]) : '';
}
