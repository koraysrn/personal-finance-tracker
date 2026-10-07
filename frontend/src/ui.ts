/**
 * Small DOM and formatting helpers shared by all views.
 */

let numberLocale = 'en-US';

export function setNumberLocale(locale: string | null | undefined): void {
  numberLocale = locale && locale.trim() !== '' ? locale.trim() : 'en-US';
}

export function escapeHtml(value: string | null | undefined): string {
  if (value == null) {
    return '';
  }
  return value
    .replaceAll('&', '\u0026amp;')
    .replaceAll('<', '\u0026lt;')
    .replaceAll('>', '\u0026gt;')
    .replaceAll('"', '\u0026quot;')
    .replaceAll("'", '\u0026#39;');
}

export function money(value: number | null | undefined): string {
  if (value == null) {
    return '';
  }
  return value.toLocaleString(numberLocale, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function todayIso(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  attrs: Record<string, string> = {},
  children: Array<Node | string> = []
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    node.setAttribute(key, value);
  }
  for (const child of children) {
    node.append(child);
  }
  return node;
}

export function errorBanner(message: string): string {
  return `<div class="error-banner">${escapeHtml(message)}</div>`;
}

export function successBanner(message: string): string {
  return `<div class="success-banner">${escapeHtml(message)}</div>`;
}
