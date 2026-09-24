import { validateDecimalInput, type InputValidation } from '../../lib/numberInput';
import { einkaufApi } from './api';
import type { BedarfResponse, Page } from './types';

/** undefined: alle Bedarfe; null: Werkstatt/Vorrat; Zahl: genau dieses Projekt. */
export async function ladeAlleBedarfe(projektId?: number | null): Promise<BedarfResponse[]> {
  const result: BedarfResponse[] = [];
  let page = 0;
  let totalPages = 1;
  do {
    const query = new URLSearchParams({ page: String(page), size: '100', sort: 'id,asc' });
    if (projektId === null) query.set('ohneProjekt', 'true');
    else if (projektId !== undefined) query.set('projektId', String(projektId));
    const response = await einkaufApi.get<Page<BedarfResponse>>(`/api/einkauf/bedarf?${query}`);
    result.push(...response.content);
    totalPages = response.totalPages;
    page++;
  } while (page < totalPages);
  return result;
}

export function vorhandenesMaximum(bedarf: BedarfResponse): number {
  return Math.max(0, Number(((bedarf.mengen.bedarf ?? 0) - (bedarf.mengen.bestellt ?? 0) - (bedarf.mengen.reserviert ?? 0)).toFixed(6)));
}

export function fehlmenge(bedarf: BedarfResponse, vorhanden: number): number {
  return Math.max(0, Number((vorhandenesMaximum(bedarf) - vorhanden).toFixed(6)));
}

export function pruefeVorhanden(bedarf: BedarfResponse, entwurf: string): InputValidation<number> {
  const result = validateDecimalInput(entwurf, {
    label: `vorhandene Menge für ${bedarf.position.bezeichnung ?? 'Material'}`, required: true,
    min: 0, max: vorhandenesMaximum(bedarf), integer: bedarf.position.basis?.einheit === 'STUECK',
  });
  if (!result.valid) return result;
  if ((entwurf.trim().split(',')[1]?.length ?? 0) > 6) {
    return { valid: false, message: 'Die vorhandene Menge darf höchstens 6 Nachkommastellen haben.' };
  }
  return result;
}

export function materialGewicht(bedarf: BedarfResponse): number | null {
  const basis = bedarf.position.basis;
  if (basis?.menge == null) return null;
  if (basis.einheit === 'KILOGRAMM') return basis.menge;
  if (basis.einheit === 'TONNE') return basis.menge * 1000;
  if (basis.einheit === 'METER' && basis.kgJeMeter != null) return basis.menge * basis.kgJeMeter;
  return null;
}
