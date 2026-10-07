import Chart from 'chart.js/auto';
import { api } from '../api';
import { errorBanner, escapeHtml, money, todayIso } from '../ui';
import type { CategoryTotal, MonthlySummary } from '../types';

let breakdownChart: Chart | null = null;

export async function renderReports(root: HTMLElement): Promise<void> {
  const now = new Date();
  const firstDay = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`;

  root.innerHTML = `
    <div class="page-header"><h1>Reports</h1></div>
    <div class="panel">
      <div class="form-row">
        <label>From <input id="rp-start" type="date" value="${firstDay}" /></label>
        <label>To <input id="rp-end" type="date" value="${todayIso()}" /></label>
      </div>
      <button id="rp-load">Load</button>
      <span style="margin-left:1rem">
        <a id="rp-csv" href="#" target="_blank">Download CSV</a> |
        <a id="rp-html" href="#" target="_blank">Download HTML</a> |
        <a href="/api/backup/export" target="_blank">Download JSON backup</a>
      </span>
      <div id="rp-msg"></div>
    </div>
    <div class="panel" id="rp-summary"></div>
    <div class="panel">
      <h3>Expense by category</h3>
      <canvas id="rp-chart" height="90"></canvas>
    </div>`;

  const msg = root.querySelector<HTMLElement>('#rp-msg')!;
  const summaryEl = root.querySelector<HTMLElement>('#rp-summary')!;

  const load = async (): Promise<void> => {
    msg.innerHTML = '';
    const start = (root.querySelector<HTMLInputElement>('#rp-start')!).value;
    const end = (root.querySelector<HTMLInputElement>('#rp-end')!).value;
    if (start > end) {
      msg.innerHTML = errorBanner('Start date must not be after end date');
      return;
    }

    const query = `start=${start}&end=${end}`;
    (root.querySelector<HTMLAnchorElement>('#rp-csv')!).href = `/api/export/csv?${query}`;
    (root.querySelector<HTMLAnchorElement>('#rp-html')!).href = `/api/export/html?${query}`;

    try {
      const [monthly, breakdown] = await Promise.all([
        api.get<MonthlySummary[]>(`/api/reports/monthly?${query}`),
        api.get<CategoryTotal[]>(`/api/reports/category-breakdown?${query}&type=EXPENSE`)
      ]);

      summaryEl.innerHTML = `
        <h3>Monthly summary</h3>
        <table>
          <thead><tr><th>Month</th><th>Income</th><th>Expense</th><th>Net</th></tr></thead>
          <tbody>
            ${monthly
              .map(
                (m) => `<tr>
                  <td>${m.year}-${String(m.month).padStart(2, '0')}</td>
                  <td class="income">${money(m.income)}</td>
                  <td class="expense">${money(m.expense)}</td>
                  <td class="${m.net >= 0 ? 'income' : 'expense'}">${money(m.net)}</td>
                </tr>`
              )
              .join('')}
          </tbody>
        </table>`;

      renderBreakdown(root.querySelector<HTMLCanvasElement>('#rp-chart')!, breakdown);
    } catch (err) {
      msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to load reports');
    }
  };

  root.querySelector<HTMLButtonElement>('#rp-load')!.addEventListener('click', () => void load());
  await load();
}

function renderBreakdown(canvas: HTMLCanvasElement, breakdown: CategoryTotal[]): void {
  if (breakdownChart) {
    breakdownChart.destroy();
  }
  breakdownChart = new Chart(canvas, {
    type: 'pie',
    data: {
      labels: breakdown.map((b) => b.categoryName),
      datasets: [
        {
          data: breakdown.map((b) => b.total),
          backgroundColor: palette(breakdown.length)
        }
      ]
    },
    options: {
      responsive: true,
      plugins: {
        legend: { position: 'right' }
      }
    }
  });
}

function palette(count: number): string[] {
  const base = [
    '#2563eb', '#16a34a', '#dc2626', '#f59e0b', '#8b5cf6',
    '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#14b8a6'
  ];
  const result: string[] = [];
  for (let i = 0; i < count; i += 1) {
    result.push(base[i % base.length]);
  }
  return result;
}
