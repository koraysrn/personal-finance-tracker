import { api } from '../api';
import { CURRENCIES } from '../currencies';
import { errorBanner, escapeHtml, money, successBanner } from '../ui';
import type { Account } from '../types';

const TYPES = ['CHECKING', 'SAVINGS', 'CREDIT_CARD', 'CASH', 'INVESTMENT', 'DEBT'];

let editingId: number | null = null;

export async function renderAccounts(root: HTMLElement): Promise<void> {
  editingId = null;
  root.innerHTML = `
    <div class="page-header"><h1>Accounts</h1></div>
    <div class="panel">
      <h3 id="account-form-title">New account</h3>
      <div class="form-row">
        <label>Name <input id="acc-name" maxlength="128" /></label>
        <label>Type <select id="acc-type">${TYPES.map((t) => `<option>${t}</option>`).join('')}</select></label>
        <label>Currency <select id="acc-currency">${CURRENCIES.map((c) => `<option>${c}</option>`).join('')}</select></label>
        <label>Opening balance <input id="acc-opening" type="number" step="0.01" value="0" /></label>
        <label>Credit limit <input id="acc-limit" type="number" step="0.01" value="0" /></label>
      </div>
      <button id="acc-save">Add account</button>
      <button id="acc-cancel" class="secondary hidden">Cancel</button>
      <div id="acc-msg"></div>
    </div>
    <div class="panel" id="account-list">Loading...</div>`;

  const msg = root.querySelector<HTMLElement>('#acc-msg')!;
  const formTitle = root.querySelector<HTMLElement>('#account-form-title')!;
  const saveBtn = root.querySelector<HTMLButtonElement>('#acc-save')!;
  const cancelBtn = root.querySelector<HTMLButtonElement>('#acc-cancel')!;
  const listEl = root.querySelector<HTMLElement>('#account-list')!;

  const loadAccounts = async (): Promise<void> => {
    try {
      const accounts = await api.get<Account[]>('/api/accounts');
      listEl.innerHTML = `
        <table>
          <thead><tr><th>Name</th><th>Type</th><th>Currency</th><th>Balance</th><th></th></tr></thead>
          <tbody>
            ${accounts
              .map(
                (account) => `<tr>
                  <td>${escapeHtml(account.name)}${account.archived ? ' (archived)' : ''}</td>
                  <td>${escapeHtml(account.type)}</td>
                  <td>${escapeHtml(account.currency)}</td>
                  <td>${money(account.balance)}</td>
                  <td>
                    <button class="small" data-action="edit" data-id="${account.id}">Edit</button>
                    <button class="small secondary" data-action="archive" data-id="${account.id}" data-archived="${account.archived}">${account.archived ? 'Unarchive' : 'Archive'}</button>
                    <button class="small danger" data-action="delete" data-id="${account.id}">Delete</button>
                  </td>
                </tr>`
              )
              .join('')}
          </tbody>
        </table>`;

      listEl.querySelectorAll<HTMLButtonElement>('button[data-action]').forEach((button) => {
        button.addEventListener('click', () => void handleAction(button.dataset.action!, Number(button.dataset.id), button.dataset.archived === 'true'));
      });
    } catch (err) {
      listEl.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to load accounts');
    }
  };

  const handleAction = async (action: string, id: number, archived: boolean): Promise<void> => {
    msg.innerHTML = '';
    try {
      if (action === 'delete') {
        await api.delete(`/api/accounts/${id}`);
        msg.innerHTML = successBanner('Account deleted');
      } else if (action === 'archive') {
        await api.put(`/api/accounts/${id}`, { archived: !archived });
        msg.innerHTML = successBanner('Account updated');
      } else if (action === 'edit') {
        const accounts = await api.get<Account[]>('/api/accounts');
        const account = accounts.find((a) => a.id === id);
        if (account) {
          fillForm(account);
        }
      }
      await loadAccounts();
    } catch (err) {
      msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Action failed');
    }
  };

  const fillForm = (account: Account): void => {
    editingId = account.id;
    formTitle.textContent = 'Edit account';
    saveBtn.textContent = 'Update account';
    cancelBtn.classList.remove('hidden');
    (root.querySelector<HTMLInputElement>('#acc-name')!).value = account.name;
    (root.querySelector<HTMLSelectElement>('#acc-type')!).value = account.type;
    (root.querySelector<HTMLSelectElement>('#acc-currency')!).value = account.currency;
    (root.querySelector<HTMLInputElement>('#acc-opening')!).value = String(account.openingBalance);
    (root.querySelector<HTMLInputElement>('#acc-limit')!).value = String(account.creditLimit);
  };

  const resetForm = (): void => {
    editingId = null;
    formTitle.textContent = 'New account';
    saveBtn.textContent = 'Add account';
    cancelBtn.classList.add('hidden');
    (root.querySelector<HTMLInputElement>('#acc-name')!).value = '';
    (root.querySelector<HTMLSelectElement>('#acc-type')!).value = 'CHECKING';
    (root.querySelector<HTMLSelectElement>('#acc-currency')!).value = 'USD';
    (root.querySelector<HTMLInputElement>('#acc-opening')!).value = '0';
    (root.querySelector<HTMLInputElement>('#acc-limit')!).value = '0';
  };

  saveBtn.addEventListener('click', async () => {
    msg.innerHTML = '';
    const payload = {
      name: (root.querySelector<HTMLInputElement>('#acc-name')!).value,
      type: (root.querySelector<HTMLSelectElement>('#acc-type')!).value,
      currency: (root.querySelector<HTMLSelectElement>('#acc-currency')!).value,
      openingBalance: Number((root.querySelector<HTMLInputElement>('#acc-opening')!).value || '0'),
      creditLimit: Number((root.querySelector<HTMLInputElement>('#acc-limit')!).value || '0')
    };
    try {
      if (editingId == null) {
        await api.post('/api/accounts', payload);
      } else {
        await api.put(`/api/accounts/${editingId}`, payload);
      }
      msg.innerHTML = successBanner('Account saved');
      resetForm();
      await loadAccounts();
    } catch (err) {
      msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to save account');
    }
  });

  cancelBtn.addEventListener('click', resetForm);
  await loadAccounts();
}
