import { api } from '../api';
import { errorBanner, escapeHtml, money, successBanner } from '../ui';
import type { BudgetProgress, Category } from '../types';

export async function renderBudgets(root: HTMLElement): Promise<void> {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth() + 1;

  root.innerHTML = `
    <div class="page-header"><h1>Budgets</h1></div>
    <div class="panel">
      <h3>New budget</h3>
      <div class="form-row">
        <label>Category <select id="b-cat"></select></label>
        <label>Year <input id="b-year" type="number" value="${year}" min="2000" max="2100" /></label>
        <label>Month <input id="b-month" type="number" value="${month}" min="1" max="12" /></label>
        <label>Amount <input id="b-amount" type="number" step="0.01" min="0.01" /></label>
      </div>
      <button id="b-save">Add budget</button>
      <div id="b-msg"></div>
    </div>
    <div class="panel">
      <h3>Progress</h3>
      <div class="form-row">
        <label>Year <input id="p-year" type="number" value="${year}" min="2000" max="2100" /></label>
        <label>Month <input id="p-month" type="number" value="${month}" min="1" max="12" /></label>
      </div>
      <button id="p-load">Show</button>
      <div id="p-list" style="margin-top:0.75rem"></div>
    </div>`;

  const msg = root.querySelector<HTMLElement>('#b-msg')!;
  const catSelect = root.querySelector<HTMLSelectElement>('#b-cat')!;
  const pList = root.querySelector<HTMLElement>('#p-list')!;

  try {
    const categories = await api.get<Category[]>('/api/categories');
    catSelect.innerHTML = categories
      .filter((c) => c.type === 'EXPENSE' && !c.archived)
      .map((c) => `<option value="${c.id}">${escapeHtml(c.name)}</option>`)
      .join('');
  } catch (err) {
    msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to load categories');
  }

  const loadProgress = async (): Promise<void> => {
    const y = Number((root.querySelector<HTMLInputElement>('#p-year')!).value);
    const m = Number((root.querySelector<HTMLInputElement>('#p-month')!).value);
    try {
      const progress = await api.get<BudgetProgress[]>(`/api/budgets/progress?year=${y}&month=${m}`);
      pList.innerHTML = `
        <table>
          <thead><tr><th>Category</th><th>Budget</th><th>Spent</th><th>%</th><th>Status</th><th></th></tr></thead>
          <tbody>
            ${progress
              .map(
                (p) => `<tr>
                  <td>${escapeHtml(p.categoryName)}</td>
                  <td>${money(p.amount)}</td>
                  <td>${money(p.spent)}</td>
                  <td>${money(p.percentage)}%</td>
                  <td><span class="badge ${statusClass(p.status)}">${escapeHtml(p.status)}</span></td>
                  <td><button class="small danger" data-delete="${p.budgetId}">Delete</button></td>
                </tr>`
              )
              .join('')}
          </tbody>
        </table>`;
      pList.querySelectorAll<HTMLButtonElement>('button[data-delete]').forEach((button) => {
        button.addEventListener('click', async () => {
          try {
            await api.delete(`/api/budgets/${button.dataset.delete}`);
            await loadProgress();
          } catch (err) {
            msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to delete budget');
          }
        });
      });
    } catch (err) {
      pList.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to load progress');
    }
  };

  root.querySelector<HTMLButtonElement>('#p-load')!.addEventListener('click', () => void loadProgress());

  root.querySelector<HTMLButtonElement>('#b-save')!.addEventListener('click', async () => {
    msg.innerHTML = '';
    const payload = {
      categoryId: Number(catSelect.value),
      year: Number((root.querySelector<HTMLInputElement>('#b-year')!).value),
      month: Number((root.querySelector<HTMLInputElement>('#b-month')!).value),
      amount: Number((root.querySelector<HTMLInputElement>('#b-amount')!).value)
    };
    try {
      await api.post('/api/budgets', payload);
      msg.innerHTML = successBanner('Budget saved');
      (root.querySelector<HTMLInputElement>('#b-amount')!).value = '';
      await loadProgress();
    } catch (err) {
      msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to save budget');
    }
  });

  await loadProgress();
}

function statusClass(status: string): string {
  switch (status) {
    case 'CRITICAL':
      return 'critical';
    case 'OVER':
      return 'over';
    case 'WARNING':
      return 'warning';
    default:
      return 'ok';
  }
}
