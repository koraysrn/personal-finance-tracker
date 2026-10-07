import { api } from '../api';
import { errorBanner, escapeHtml, money, successBanner, todayIso } from '../ui';
import type { Account, Category, RecurringTransaction } from '../types';

export async function renderRecurring(root: HTMLElement): Promise<void> {
  root.innerHTML = `
    <div class="page-header"><h1>Recurring transactions</h1></div>
    <div class="panel">
      <h3>New rule</h3>
      <div class="form-row">
        <label>Account <select id="r-account"></select></label>
        <label>Type <select id="r-type"><option>INCOME</option><option>EXPENSE</option></select></label>
        <label>Category <select id="r-category"></select></label>
        <label>Amount <input id="r-amount" type="number" step="0.01" min="0.01" /></label>
      </div>
      <div class="form-row">
        <label>Frequency
          <select id="r-frequency">
            <option>MONTHLY</option><option>WEEKLY</option><option>DAILY</option><option>YEARLY</option>
          </select>
        </label>
        <label>Day of month <input id="r-day" type="number" min="1" max="31" value="1" /></label>
        <label>Start date <input id="r-start" type="date" value="${todayIso()}" /></label>
        <label>End date <input id="r-end" type="date" /></label>
      </div>
      <label>Note <input id="r-note" maxlength="1024" style="width:100%" /></label>
      <div style="margin-top:0.75rem"><button id="r-save">Add rule</button></div>
      <div id="r-msg"></div>
    </div>
    <div class="panel" id="r-list">Loading...</div>`;

  const msg = root.querySelector<HTMLElement>('#r-msg')!;
  const accountSelect = root.querySelector<HTMLSelectElement>('#r-account')!;
  const typeSelect = root.querySelector<HTMLSelectElement>('#r-type')!;
  const categorySelect = root.querySelector<HTMLSelectElement>('#r-category')!;
  const listEl = root.querySelector<HTMLElement>('#r-list')!;

  let categories: Category[] = [];

  const refreshCategories = (): void => {
    const wantedType = typeSelect.value === 'INCOME' ? 'INCOME' : 'EXPENSE';
    categorySelect.innerHTML = categories
      .filter((c) => c.type === wantedType && !c.archived)
      .map((c) => `<option value="${c.id}">${escapeHtml(c.name)}</option>`)
      .join('');
  };

  try {
    const [accounts, cats] = await Promise.all([
      api.get<Account[]>('/api/accounts'),
      api.get<Category[]>('/api/categories')
    ]);
    categories = cats;
    accountSelect.innerHTML = accounts
      .filter((a) => !a.archived)
      .map((a) => `<option value="${a.id}">${escapeHtml(a.name)}</option>`)
      .join('');
    refreshCategories();
  } catch (err) {
    msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to load options');
  }

  typeSelect.addEventListener('change', refreshCategories);

  const loadList = async (): Promise<void> => {
    try {
      const rules = await api.get<RecurringTransaction[]>('/api/recurring');
      listEl.innerHTML = `
        <table>
          <thead><tr><th>Account</th><th>Category</th><th>Type</th><th>Amount</th><th>Frequency</th><th>Next run</th><th>Active</th><th></th></tr></thead>
          <tbody>
            ${rules
              .map(
                (rule) => `<tr>
                  <td>${escapeHtml(rule.accountName)}</td>
                  <td>${escapeHtml(rule.categoryName)}</td>
                  <td>${escapeHtml(rule.type)}</td>
                  <td>${money(rule.amount)}</td>
                  <td>${escapeHtml(rule.frequency)}${rule.dayOfMonth ? ` (day ${rule.dayOfMonth})` : ''}</td>
                  <td>${escapeHtml(rule.nextRun)}</td>
                  <td>${rule.active ? 'yes' : 'no'}</td>
                  <td>
                    <button class="small" data-toggle="${rule.id}" data-active="${rule.active}">${rule.active ? 'Pause' : 'Resume'}</button>
                    <button class="small danger" data-delete="${rule.id}">Delete</button>
                  </td>
                </tr>`
              )
              .join('')}
          </tbody>
        </table>`;

      listEl.querySelectorAll<HTMLButtonElement>('button[data-toggle]').forEach((button) => {
        button.addEventListener('click', async () => {
          const id = Number(button.dataset.toggle);
          const nextActive = button.dataset.active !== 'true';
          try {
            await api.put(`/api/recurring/${id}/active?active=${nextActive}`);
            await loadList();
          } catch (err) {
            msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to update rule');
          }
        });
      });
      listEl.querySelectorAll<HTMLButtonElement>('button[data-delete]').forEach((button) => {
        button.addEventListener('click', async () => {
          try {
            await api.delete(`/api/recurring/${button.dataset.delete}`);
            await loadList();
          } catch (err) {
            msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to delete rule');
          }
        });
      });
    } catch (err) {
      listEl.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to load rules');
    }
  };

  root.querySelector<HTMLButtonElement>('#r-save')!.addEventListener('click', async () => {
    msg.innerHTML = '';
    const frequency = (root.querySelector<HTMLSelectElement>('#r-frequency')!).value;
    const dayOfMonth = (root.querySelector<HTMLInputElement>('#r-day')!).value
      ? Number((root.querySelector<HTMLInputElement>('#r-day')!).value)
      : null;
    const payload = {
      accountId: Number(accountSelect.value),
      categoryId: Number(categorySelect.value),
      type: typeSelect.value,
      amount: Number((root.querySelector<HTMLInputElement>('#r-amount')!).value),
      frequency,
      dayOfMonth,
      note: (root.querySelector<HTMLInputElement>('#r-note')!).value || null,
      startDate: (root.querySelector<HTMLInputElement>('#r-start')!).value,
      endDate: (root.querySelector<HTMLInputElement>('#r-end')!).value || null
    };
    try {
      await api.post('/api/recurring', payload);
      msg.innerHTML = successBanner('Rule saved');
      await loadList();
    } catch (err) {
      msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to save rule');
    }
  });

  await loadList();
}
