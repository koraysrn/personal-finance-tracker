import { api } from '../api';
import { errorBanner, escapeHtml, successBanner } from '../ui';
import type { Category, Tag } from '../types';

export async function renderCategories(root: HTMLElement): Promise<void> {
  root.innerHTML = `
    <div class="page-header"><h1>Categories</h1></div>
    <div class="panel">
      <h3>New category</h3>
      <div class="form-row">
        <label>Name <input id="c-name" maxlength="128" /></label>
        <label>Type <select id="c-type"><option>INCOME</option><option>EXPENSE</option></select></label>
        <label>Parent (optional) <select id="c-parent"><option value="">None</option></select></label>
        <label>Icon <input id="c-icon" maxlength="64" /></label>
        <label>Color <input id="c-color" maxlength="16" placeholder="#22c55e" /></label>
      </div>
      <button id="c-save">Add category</button>
      <div id="c-msg"></div>
    </div>
    <div class="panel" id="c-list">Loading...</div>
    <div class="panel">
      <h3>Tags</h3>
      <div class="form-row">
        <label>Tag name <input id="t-name" maxlength="64" /></label>
      </div>
      <button id="t-save">Add tag</button>
      <div id="t-list" style="margin-top:0.75rem"></div>
    </div>`;

  const msg = root.querySelector<HTMLElement>('#c-msg')!;
  const typeSelect = root.querySelector<HTMLSelectElement>('#c-type')!;
  const parentSelect = root.querySelector<HTMLSelectElement>('#c-parent')!;
  const listEl = root.querySelector<HTMLElement>('#c-list')!;
  const tagList = root.querySelector<HTMLElement>('#t-list')!;

  let categories: Category[] = [];

  const refreshParentOptions = (): void => {
    const wantedType = typeSelect.value;
    parentSelect.innerHTML = `<option value="">None</option>` + categories
      .filter((c) => c.type === wantedType && !c.archived)
      .map((c) => `<option value="${c.id}">${escapeHtml(c.name)}</option>`)
      .join('');
  };

  typeSelect.addEventListener('change', refreshParentOptions);

  const loadCategories = async (): Promise<void> => {
    try {
      categories = await api.get<Category[]>('/api/categories');
      refreshParentOptions();
      listEl.innerHTML = `
        <table>
          <thead><tr><th>ID</th><th>Name</th><th>Type</th><th>Parent</th><th></th></tr></thead>
          <tbody>
            ${categories
              .map(
                (c) => `<tr>
                  <td>${c.id}</td>
                  <td>${escapeHtml(c.name)}${c.archived ? ' (archived)' : ''}</td>
                  <td>${escapeHtml(c.type)}</td>
                  <td>${c.parentId == null ? '' : escapeHtml(parentName(c.parentId))}</td>
                  <td>
                    <button class="small danger" data-delete="${c.id}">Delete</button>
                    <button class="small secondary" data-merge="${c.id}">Merge into...</button>
                  </td>
                </tr>`
              )
              .join('')}
          </tbody>
        </table>`;

      listEl.querySelectorAll<HTMLButtonElement>('button[data-delete]').forEach((button) => {
        button.addEventListener('click', async () => {
          try {
            await api.delete(`/api/categories/${button.dataset.delete}`);
            msg.innerHTML = successBanner('Category deleted');
            await loadCategories();
          } catch (err) {
            msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to delete category');
          }
        });
      });
      listEl.querySelectorAll<HTMLButtonElement>('button[data-merge]').forEach((button) => {
        button.addEventListener('click', async () => {
          const targetId = window.prompt('Enter the target category ID to merge into:');
          if (targetId == null || targetId.trim() === '') {
            return;
          }
          try {
            await api.delete(`/api/categories/${button.dataset.merge}?mergeToId=${Number(targetId)}`);
            msg.innerHTML = successBanner('Category merged');
            await loadCategories();
          } catch (err) {
            msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to merge category');
          }
        });
      });
    } catch (err) {
      listEl.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to load categories');
    }
  };

  const parentName = (parentId: number): string => {
    const parent = categories.find((c) => c.id === parentId);
    return parent ? parent.name : '';
  };

  const loadTags = async (): Promise<void> => {
    try {
      const tags = await api.get<Tag[]>('/api/tags');
      tagList.innerHTML = tags
        .map(
          (tag) => `<span style="margin-right:0.5rem;background:#e5e7eb;border-radius:999px;padding:0.2rem 0.6rem">
            ${escapeHtml(tag.name)}
            <button class="small danger" data-tag-delete="${tag.id}">x</button>
          </span>`
        )
        .join('');
      tagList.querySelectorAll<HTMLButtonElement>('button[data-tag-delete]').forEach((button) => {
        button.addEventListener('click', async () => {
          try {
            await api.delete(`/api/tags/${button.dataset.tagDelete}`);
            await loadTags();
          } catch (err) {
            msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to delete tag');
          }
        });
      });
    } catch (err) {
      tagList.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to load tags');
    }
  };

  root.querySelector<HTMLButtonElement>('#c-save')!.addEventListener('click', async () => {
    msg.innerHTML = '';
    const payload = {
      name: (root.querySelector<HTMLInputElement>('#c-name')!).value,
      type: typeSelect.value,
      parentId: parentSelect.value ? Number(parentSelect.value) : null,
      icon: (root.querySelector<HTMLInputElement>('#c-icon')!).value || null,
      color: (root.querySelector<HTMLInputElement>('#c-color')!).value || null
    };
    try {
      await api.post('/api/categories', payload);
      msg.innerHTML = successBanner('Category saved');
      (root.querySelector<HTMLInputElement>('#c-name')!).value = '';
      await loadCategories();
    } catch (err) {
      msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to save category');
    }
  });

  root.querySelector<HTMLButtonElement>('#t-save')!.addEventListener('click', async () => {
    msg.innerHTML = '';
    const name = (root.querySelector<HTMLInputElement>('#t-name')!).value;
    try {
      await api.post('/api/tags', { name });
      (root.querySelector<HTMLInputElement>('#t-name')!).value = '';
      await loadTags();
    } catch (err) {
      msg.innerHTML = errorBanner(err instanceof Error ? err.message : 'Failed to save tag');
    }
  });

  await Promise.all([loadCategories(), loadTags()]);
}
