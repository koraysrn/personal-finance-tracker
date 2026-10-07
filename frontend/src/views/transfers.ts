import { api } from '../api';
import { errorBanner, escapeHtml, money, successBanner, todayIso } from '../ui';
import type { Account, TransferResult } from '../types';

export async function renderTransfers(root: HTMLElement): Promise<void> {
  root.innerHTML = `
    <div class="page-header"><h1>Transfers</h1></div>
    <div class="panel">
      <h3>New transfer</h3>
      <div class="form-row">
        <label>From <select id="tf-source"></select></label>
        <label>To <select id="tf-target"></select></label>
        <label>Amount <input id="tf-amount" type="number" step="0.01" min="0.01" /></label>
        <label>Date <input id="tf-date" type="date" value="${todayIso()}" /></label>
      </div>
      <label>Note <input id="tf-note" maxlength="1024" style="width:100%" /></label>
      <div style="margin-top:0.75rem"><button id="tf-save">Transfer</button></div>
      <div id="tf-msg"></div>
    </div>`;

  const msg = root.querySelector<HTMLElement>('#tf-msg')!;
  const source = root.querySelector<HTMLSelectElement>('#tf-source')!;
  const target = root.querySelector<HTMLSelectElement>('#tf-target')!;

  try {
    const accounts = await api.get<Account[]>('/api/accounts');
    const active = accounts.filter((a) => !a.archived);
    const options = active.map((a) => `<option value="${a.id}">${escapeHtml(a.name)} (${escapeHtml(a.currency)})</option>`).join('');
    source.innerHTML = options;
    target.innerHTML = options;
  } catch (err) {
    msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to load accounts');
    return;
  }

  root.querySelector<HTMLButtonElement>('#tf-save')!.addEventListener('click', async () => {
    msg.innerHTML = '';
    const payload = {
      sourceAccountId: Number(source.value),
      targetAccountId: Number(target.value),
      amount: Number((root.querySelector<HTMLInputElement>('#tf-amount')!).value),
      date: (root.querySelector<HTMLInputElement>('#tf-date')!).value,
      note: (root.querySelector<HTMLInputElement>('#tf-note')!).value || null,
      idempotencyKey: crypto.randomUUID()
    };
    try {
      const result = await api.post<TransferResult>('/api/transfers', payload);
      msg.innerHTML = successBanner(
        `Transfer saved: ${money(result.sourceAmount)} ${escapeHtml(result.sourceCurrency)} to ${money(result.targetAmount)} ${escapeHtml(result.targetCurrency)}`
      );
      (root.querySelector<HTMLInputElement>('#tf-amount')!).value = '';
      (root.querySelector<HTMLInputElement>('#tf-note')!).value = '';
    } catch (err) {
      msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Transfer failed');
    }
  });
}
