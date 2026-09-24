import { randomUUID } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync, renameSync, mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import type { IdsCart, IdsDraft, IdsProjektZuordnung } from '../src/types/ids';

/** Optional private file keeps the local preview's real imports across Vite restarts. */
export function createIdsDraftStore(file?: string) {
  let drafts: IdsDraft[] = file && existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : [];
  const persist = () => {
    if (!file) return;
    mkdirSync(dirname(file), { recursive: true, mode: 0o700 });
    writeFileSync(`${file}.tmp`, JSON.stringify(drafts), { mode: 0o600 });
    renameSync(`${file}.tmp`, file);
  };
  return {
    all: () => structuredClone(drafts),
    get: (id: string) => structuredClone(drafts.find(draft => draft.id === id)),
    /** Nur die Warenkörbe, die an diesem Projekt geparkt sind. */
    byProjekt: (projektId: number) => structuredClone(drafts.filter(draft => draft.projektId === projektId)),
    /** Ohne neue Zuordnung behält ein bestehender Warenkorb sein Projekt. */
    save(cart: IdsCart, id?: string, projekt?: IdsProjektZuordnung): IdsDraft {
      const existing = drafts.find(draft => draft.id === id);
      if (id && !existing) throw new Error('Warenkorb nicht gefunden.');
      if (existing?.ordered) throw new Error('Dieser Warenkorb ist bereits bestellt.');
      const year = new Date().getFullYear();
      const sequence = Math.max(0, ...drafts.filter(d => d.number.startsWith(`B-${year}-`)).map(d => Number(d.number.split('-').at(-1)) || 0)) + 1;
      const zuordnung = projekt ?? (existing?.projektId != null ? { projektId: existing.projektId, projektName: existing.projektName } : undefined);
      const draft: IdsDraft = { ...cart, id: existing?.id ?? randomUUID(), number: existing?.number ?? `B-${year}-${String(sequence).padStart(6, '0')}`, updatedAt: new Date().toISOString(),
        ...(zuordnung ? { projektId: zuordnung.projektId, ...(zuordnung.projektName ? { projektName: zuordnung.projektName } : {}) } : {}) };
      const previous = drafts;
      drafts = [...drafts.filter(d => d.id !== draft.id), draft];
      try { persist(); } catch (error) { drafts = previous; throw error; }
      return structuredClone(draft);
    },
    clear() { drafts = []; persist(); },
  };
}
