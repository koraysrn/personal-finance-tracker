export interface Account {
  id: number;
  name: string;
  type: string;
  currency: string;
  openingBalance: number;
  creditLimit: number;
  archived: boolean;
  sortOrder: number;
  balance: number;
}

export interface Category {
  id: number;
  name: string;
  type: 'INCOME' | 'EXPENSE';
  parentId: number | null;
  icon: string | null;
  color: string | null;
  archived: boolean;
}

export interface Tag {
  id: number;
  name: string;
}

export interface Transaction {
  id: number;
  accountId: number;
  accountName: string;
  categoryId: number | null;
  categoryName: string | null;
  type: string;
  amount: number;
  date: string;
  note: string | null;
  linkedTxId: number | null;
  source: string;
  tags: string[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface Budget {
  id: number;
  categoryId: number;
  categoryName: string;
  year: number;
  month: number;
  amount: number;
}

export interface BudgetProgress {
  budgetId: number;
  categoryId: number;
  categoryName: string;
  year: number;
  month: number;
  amount: number;
  spent: number;
  percentage: number;
  status: string;
}

export interface RecurringTransaction {
  id: number;
  accountId: number;
  accountName: string;
  categoryId: number;
  categoryName: string;
  type: string;
  amount: number;
  frequency: string;
  dayOfMonth: number | null;
  dayOfWeek: number | null;
  note: string | null;
  active: boolean;
  startDate: string;
  endDate: string | null;
  nextRun: string;
  lastRun: string | null;
}

export interface MonthlySummary {
  year: number;
  month: number;
  income: number;
  expense: number;
  net: number;
}

export interface CategoryTotal {
  categoryId: number;
  categoryName: string;
  total: number;
}

export interface AccountBalance {
  accountId: number;
  accountName: string;
  type: string;
  currency: string;
  balance: number;
  baseBalance: number;
}

export interface NetWorth {
  baseCurrency: string;
  total: number;
  accounts: AccountBalance[];
}

export interface TrendPoint {
  month: number;
  income: number;
  expense: number;
  net: number;
}

export interface ImportResult {
  imported: number;
  errors: { row: number; message: string }[];
}

export interface TransferResult {
  sourceTxId: number;
  targetTxId: number;
  sourceAmount: number;
  targetAmount: number;
  sourceCurrency: string;
  targetCurrency: string;
  date: string;
}

export interface RatesResponse {
  base: string;
  rates: Record<string, number>;
}
