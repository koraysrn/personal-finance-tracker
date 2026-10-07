import { api } from '../api';
import { errorBanner, escapeHtml, money, todayIso } from '../ui';
import type { Account, Category, Page, Tag, Transaction } from '../types';

let currentPage = 0;

export async function renderTransactions(root: HTMLElement): Promise<void> {
  currentPage = 0;
  root.innerHTML = `
    <div class="page-header"><h1>Transactions</h1></div>
    <div class="panel">
      <h3>New transaction</h3>
      <div class="form-row">
        <label>Account <select id="tx-account"></select></label>
        <label>Type <select id="tx-type"><option>INCOME</option><option>EXPENSE</option></select></label>
        <label>Category <select id="tx-category"></select></label>
        <label>Amount <input id="tx-amount" type="number" step="0.01" min="0.01" /></label>
        <label>Date <input id="tx-date" type="date" value="${todayIso()}" /></label>
      </div>
      <label>Note <input id="tx-note" maxlength="1024" style="width:100%" /></label>
      <div style="margin-top:0.75rem">
        <button id="tx-save">Add transaction</button>
      </div>
      <div id="tx-msg"></div>
    </div>
    <div class="panel">
      <h3>Filter</h3>
      <div class="form-row">
        <label>Account <select id="f-account"><option value="">All</option></select></label>
        <label>Category <select id="f-category"><option value="">All</option></select></label>
        <label>Type <select id="f-type"><option value="">All</option><option>INCOME</option><option>EXPENSE</option></select></label>
        <label>From <input id="f-start" type="date" /></label>
        <label>To <input id="f-end" type="date" /></label>
        <label>Search <input id="f-search" placeholder="note text" /></label>
      </div>
      <button id="tx-search">Search</button>
      <button id="tx-reset" class="secondary">Reset</button>
    </div>
    <div class="panel" id="tx-list">Loading...</div>`;

  const msg = root.querySelector<HTMLElement>('#tx-msg')!;
  const txAccount = root.querySelector<HTMLSelectElement>('#tx-account')!;
  const txType = root.querySelector<HTMLSelectElement>('#tx-type')!;
  const txCategory = root.querySelector<HTMLSelectElement>('#tx-category')!;
  const listEl = root.querySelector<HTMLElement>('#tx-list')!;

  let accounts: Account[] = [];
  let categories: Category[] = [];

  const refreshCategoryOptions = (): void => {
    const wantedType = txType.value === 'INCOME' ? 'INCOME' : 'EXPENSE';
    txCategory.innerHTML = categories
      .filter((c) => c.type === wantedType && !c.archived)
      .map((c) => `<option value="${c.id}">${escapeHtml(c.name)}</option>`)
      .join('');
  };

  const loadOptions = async (): Promise<void> => {
    [accounts, categories] = await Promise.all([
      api.get<Account[]>('/api/accounts'),
      api.get<Category[]>('/api/categories')
    ]);
    txAccount.innerHTML = accounts
      .filter((a) => !a.archived)
      .map((a) => `<option value="${a.id}">${escapeHtml(a.name)}</option>`)
      .join('');
    refreshCategoryOptions();
    const fAccount = root.querySelector<HTMLSelectElement>('#f-account')!;
    const fCategory = root.querySelector<HTMLSelectElement>('#f-category')!;
    fAccount.innerHTML = `<option value="">All</option>` + accounts.map((a) => `<option value="${a.id}">${escapeHtml(a.name)}</option>`).join('');
    fCategory.innerHTML = `<option value="">All</option>` + categories.map((c) => `<option value="${c.id}">${escapeHtml(c.name)}</option>`).join('');
  };

  txType.addEventListener('change', refreshCategoryOptions);

  const loadList = async (): Promise<void> => {
    const params = new URLSearchParams();
    params.set('page', String(currentPage));
    params.set('size', '50');
    const accountId = (root.querySelector<HTMLSelectElement>('#f-account')!).value;
    const categoryId = (root.querySelector<HTMLSelectElement>('#f-category')!).value;
    const type = (root.querySelector<HTMLSelectElement>('#f-type')!).value;
    const start = (root.querySelector<HTMLInputElement>('#f-start')!).value;
    const end = (root.querySelector<HTMLInputElement>('#f-end')!).value;
    const search = (root.querySelector<HTMLInputElement>('#f-search')!).value;
    if (accountId) params.set('accountId', accountId);
    if (categoryId) params.set('categoryId', categoryId);
    if (type) params.set('type', type);
    if (start) params.set('startDate', start);
    if (end) params.set('endDate', end);
    if (search) params.set('search', search);

    try {
      const page = await api.get<Page<Transaction>>('/api/transactions?' + params.toString());
      listEl.innerHTML = `
        <table>
          <thead><tr><th>Date</th><th>Account</th><th>Category</th><th>Type</th><th>Amount</th><th>Note</th><th></th></tr></thead>
          <tbody>
            ${page.content
              .map(
                (tx) => `<tr>
                  <td>${escapeHtml(tx.date)}</td>
                  <td>${escapeHtml(tx.accountName)}</td>
                  <td>${escapeHtml(tx.categoryName)}</td>
                  <td>${escapeHtml(tx.type)}</td>
                  <td class="${tx.type === 'EXPENSE' || tx.type === 'TRANSFER_OUT' ? 'expense' : 'income'}">${money(tx.amount)}</td>
                  <td>${escapeHtml(tx.note)}</td>
                  <td><button class="small danger" data-delete="${tx.id}">Delete</button></td>
                </tr>`
              )
              .join('')}
          </tbody>
        </table>
        <div style="margin-top:0.75rem">
          <button class="small secondary" id="tx-prev" ${page.page === 0 ? 'disabled' : ''}>Prev</button>
          <span>Page ${page.page + 1} / ${Math.max(page.totalPages, 1)} (${page.totalElements} rows)</span>
          <button class="small secondary" id="tx-next" ${page.page + 1 >= page.totalPages ? 'disabled' : ''}>Next</button>
        </div>`;

      listEl.querySelectorAll<HTMLButtonElement>('button[data-delete]').forEach((button) => {
        button.addEventListener('click', () => void deleteTx(Number(button.dataset.delete)));
      });
      const prev = listEl.querySelector<HTMLButtonElement>('#tx-prev');
      const next = listEl.querySelector<HTMLButtonElement>('#tx-next');
      prev?.addEventListener('click', () => {
        if (currentPage > 0) {
          currentPage -= 1;
          void loadList();
        }
      });
      next?.addEventListener('click', () => {
        currentPage += 1;
        void loadList();
      });
    } catch (err) {
      listEl.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to load transactions');
    }
  };

  const deleteTx = async (id: number): Promise<void> => {
    try {
      await api.delete(`/api/transactions/${id}`);
      await loadList();
    } catch (err) {
      msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to delete transaction');
    }
  };

  root.querySelector<HTMLButtonElement>('#tx-save')!.addEventListener('click', async () => {
    msg.innerHTML = '';
    const idempotencyKey = crypto.randomUUID();
    const payload = {
      accountId: Number(txAccount.value),
      categoryId: Number(txCategory.value),
      type: txType.value,
      amount: Number((root.querySelector<HTMLInputElement>('#tx-amount')!).value),
      date: (root.querySelector<HTMLInputElement>('#tx-date')!).value,
      note: (root.querySelector<HTMLInputElement>('#tx-note')!).value || null,
      idempotencyKey
    };
    try {
      await api.post('/api/transactions', payload);
      msg.innerHTML = `<div class="success-banner">Transaction saved</div>`;
      (root.querySelector<HTMLInputElement>('#tx-amount')!).value = '';
      (root.querySelector<HTMLInputElement>('#tx-note')!).value = '';
      await loadList();
    } catch (err) {
      msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to save transaction');
    }
  });

  root.querySelector<HTMLButtonElement>('#tx-search')!.addEventListener('click', () => {
    currentPage = 0;
    void loadList();
  });
  root.querySelector<HTMLButtonElement>('#tx-reset')!.addEventListener('click', () => {
    (root.querySelector<HTMLSelectElement>('#f-account')!).value = '';
    (root.querySelector<HTMLSelectElement>('#f-category')!).value = '';
    (root.querySelector<HTMLSelectElement>('#f-type')!).value = '';
    (root.querySelector<HTMLInputElement>('#f-start')!).value = '';
    (root.querySelector<HTMLInputElement>('#f-end')!).value = '';
    (root.querySelector<HTMLInputElement>('#f-search')!).value = '';
    currentPage = 0;
    void loadList();
  });

  try {
    await loadOptions();
    await loadList();
  } catch (err) {
    listEl.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to initialize');
  }
}
