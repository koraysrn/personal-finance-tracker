import Chart from 'chart.js/auto';
import { api } from '../api';
import { errorBanner, escapeHtml, money } from '../ui';
import type { NetWorth, Page, Transaction, TrendPoint } from '../types';

let trendChart: Chart | null = null;

export async function renderDashboard(root: HTMLElement): Promise<void> {
  root.innerHTML = `
    <div class="page-header"><h1>Dashboard</h1></div>
    <div id="dash-content">Loading...</div>`;

  const content = root.querySelector<HTMLElement>('#dash-content')!;
  try {
    const year = new Date().getFullYear();
    const [netWorth, txs, trend] = await Promise.all([
      api.get<NetWorth>('/api/reports/net-worth'),
      api.get<Page<Transaction>>('/api/transactions?size=10'),
      api.get<TrendPoint[]>('/api/reports/yearly?year=' + year)
    ]);

    const yearIncome = trend.reduce((sum, point) => sum + point.income, 0);
    const yearExpense = trend.reduce((sum, point) => sum + point.expense, 0);

    content.innerHTML = `
      <div class="grid-2">
        <div class="panel">
          <div class="stat">${money(netWorth.total)} ${escapeHtml(netWorth.baseCurrency)}</div>
          <div>Net worth</div>
        </div>
        <div class="panel">
          <div class="stat income">${money(yearIncome)}</div>
          <div>Year income</div>
        </div>
        <div class="panel">
          <div class="stat expense">${money(yearExpense)}</div>
          <div>Year expense</div>
        </div>
      </div>
      <div class="panel">
        <h3>Monthly trend (${year})</h3>
        <canvas id="trend-chart" height="90"></canvas>
      </div>
      <div class="panel">
        <h3>Account balances</h3>
        <table>
          <thead><tr><th>Account</th><th>Type</th><th>Balance</th></tr></thead>
          <tbody>
            ${netWorth.accounts
              .map(
                (account) => `<tr>
                  <td>${escapeHtml(account.accountName)}</td>
                  <td>${escapeHtml(account.type)}</td>
                  <td>${money(account.balance)} ${escapeHtml(account.currency)}</td>
                </tr>`
              )
              .join('')}
          </tbody>
        </table>
      </div>
      <div class="panel">
        <h3>Recent transactions</h3>
        <table>
          <thead><tr><th>Date</th><th>Account</th><th>Category</th><th>Type</th><th>Amount</th></tr></thead>
          <tbody>
            ${txs.content
              .map(
                (tx) => `<tr>
                  <td>${escapeHtml(tx.date)}</td>
                  <td>${escapeHtml(tx.accountName)}</td>
                  <td>${escapeHtml(tx.categoryName)}</td>
                  <td>${escapeHtml(tx.type)}</td>
                  <td class="${tx.type === 'EXPENSE' || tx.type === 'TRANSFER_OUT' ? 'expense' : 'income'}">
                    ${money(tx.amount)}
                  </td>
                </tr>`
              )
              .join('')}
          </tbody>
        </table>
      </div>`;

    renderTrendChart(content.querySelector<HTMLCanvasElement>('#trend-chart')!, trend);
  } catch (err) {
    content.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to load dashboard');
  }
}

function renderTrendChart(canvas: HTMLCanvasElement, trend: TrendPoint[]): void {
  if (trendChart) {
    trendChart.destroy();
  }
  const labels = trend.map((point) => {
    const d = new Date();
    d.setMonth(point.month - 1, 1);
    return d.toLocaleString('en-US', { month: 'short' });
  });
  trendChart = new Chart(canvas, {
    type: 'bar',
    data: {
      labels,
      datasets: [
        { label: 'Income', data: trend.map((p) => p.income), backgroundColor: '#16a34a' },
        { label: 'Expense', data: trend.map((p) => p.expense), backgroundColor: '#dc2626' }
      ]
    },
    options: {
      responsive: true,
      scales: { y: { beginAtZero: true } }
    }
  });
}
