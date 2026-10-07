import './style.css';
import { api, initCsrf, login, logout, setUnauthorizedHandler } from './api';
import { setNumberLocale } from './ui';
import { renderDashboard } from './views/dashboard';
import { renderAccounts } from './views/accounts';
import { renderTransactions } from './views/transactions';
import { renderTransfers } from './views/transfers';
import { renderBudgets } from './views/budgets';
import { renderRecurring } from './views/recurring';
import { renderCategories } from './views/categories';
import { renderReports } from './views/reports';
import { renderSettings } from './views/settings';

type View = (root: HTMLElement) => void | Promise<void>;

const routes: Record<string, View> = {
  '#/': renderDashboard,
  '#/dashboard': renderDashboard,
  '#/accounts': renderAccounts,
  '#/transactions': renderTransactions,
  '#/transfers': renderTransfers,
  '#/budgets': renderBudgets,
  '#/recurring': renderRecurring,
  '#/categories': renderCategories,
  '#/reports': renderReports,
  '#/settings': renderSettings
};

let appMounted = false;

function renderRouteIfMounted(): void {
  if (!appMounted) {
    return;
  }
  const viewRoot = document.querySelector<HTMLElement>('#view-root');
  if (!viewRoot) {
    return;
  }
  const hash = window.location.hash || '#/';
  const view = routes[hash] ?? routes['#/'];
  void view(viewRoot);
}

function appShell(root: HTMLElement): void {
  root.innerHTML = `
    <div id="app-root">
      <aside class="sidebar">
        <h2>Personal Finance</h2>
        <nav>
          <a href="#/">Dashboard</a>
          <a href="#/accounts">Accounts</a>
          <a href="#/transactions">Transactions</a>
          <a href="#/transfers">Transfers</a>
          <a href="#/budgets">Budgets</a>
          <a href="#/recurring">Recurring</a>
          <a href="#/categories">Categories</a>
          <a href="#/reports">Reports</a>
          <a href="#/settings">Settings</a>
        </nav>
        <button id="logout-btn" class="secondary" style="margin-top:1rem;width:100%">Logout</button>
      </aside>
      <main class="content" id="view-root"></main>
    </div>`;

  root.querySelector<HTMLButtonElement>('#logout-btn')!.addEventListener('click', async () => {
    try {
      await logout();
    } finally {
      await mountAuth(root);
    }
  });

  markActiveNav();
}

function markActiveNav(): void {
  const hash = window.location.hash || '#/';
  document.querySelectorAll<HTMLAnchorElement>('.sidebar nav a').forEach((link) => {
    link.classList.toggle('active', link.getAttribute('href') === hash);
  });
}

async function applySettings(): Promise<void> {
  try {
    const settings = await api.get<Record<string, string>>('/api/settings');
    setNumberLocale(settings.locale);
  } catch {
    // keep the default locale when settings cannot be loaded
  }
}

async function mountApp(root: HTMLElement): Promise<void> {
  appMounted = true;
  appShell(root);
  await applySettings();
  renderRouteIfMounted();
}

async function mountAuth(root: HTMLElement): Promise<void> {
  appMounted = false;
  setUnauthorizedHandler(() => undefined);

  root.innerHTML = `<div id="auth-root"></div>`;
  const authRoot = root.querySelector<HTMLElement>('#auth-root')!;
  authRoot.innerHTML = `
    <div class="auth-card">
      <h1 id="auth-title">Sign in</h1>
      <form id="auth-form">
        <label>Username
          <input name="username" required autocomplete="username" />
        </label>
        <label>Password
          <input name="password" type="password" required autocomplete="current-password" />
        </label>
        <div id="register-fields" class="hidden">
          <label>Display name
            <input name="displayName" />
          </label>
        </div>
        <button type="submit" id="auth-submit">Sign in</button>
      </form>
      <button id="toggle-mode" class="secondary" style="width:100%;margin-top:0.5rem">Create account</button>
      <div class="auth-error" id="auth-error"></div>
    </div>`;

  let registerMode = false;
  const form = authRoot.querySelector<HTMLFormElement>('#auth-form')!;
  const title = authRoot.querySelector<HTMLElement>('#auth-title')!;
  const submit = authRoot.querySelector<HTMLButtonElement>('#auth-submit')!;
  const error = authRoot.querySelector<HTMLElement>('#auth-error')!;
  const registerFields = authRoot.querySelector<HTMLElement>('#register-fields')!;
  const toggle = authRoot.querySelector<HTMLButtonElement>('#toggle-mode')!;

  const refreshMode = (): void => {
    title.textContent = registerMode ? 'Create account' : 'Sign in';
    submit.textContent = registerMode ? 'Register' : 'Sign in';
    registerFields.classList.toggle('hidden', !registerMode);
    toggle.textContent = registerMode ? 'Back to sign in' : 'Create account';
  };

  toggle.addEventListener('click', () => {
    registerMode = !registerMode;
    refreshMode();
  });
  refreshMode();

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    error.textContent = '';
    const data = new FormData(form);
    const username = String(data.get('username') ?? '').trim();
    const password = String(data.get('password') ?? '');
    try {
      if (registerMode) {
        await api.post('/api/auth/register', {
          username,
          password,
          displayName: String(data.get('displayName') ?? '')
        });
      }
      await login(username, password);
      await initCsrf();
      await mountApp(root);
    } catch (err) {
      error.textContent = err instanceof Error ? err.message : 'Authentication failed';
    }
  });
}

async function bootstrap(): Promise<void> {
  const root = document.querySelector<HTMLElement>('#app')!;
  setUnauthorizedHandler(async () => {
    await mountAuth(root);
  });
  await initCsrf();
  try {
    await api.get('/api/auth/me');
    await mountApp(root);
  } catch {
    await mountAuth(root);
  }
}

window.addEventListener('hashchange', () => {
  markActiveNav();
  renderRouteIfMounted();
});

void bootstrap();
