import { einkaufApi } from './api';
import { ladeAlleBedarfe, materialGewicht, pruefeVorhanden, vorhandenesMaximum } from './bedarfApi';
import type { BedarfResponse } from './types';
import { formatDecimalInput } from '../../lib/numberInput';

export interface ProjektKurz { id: number; bauvorhaben?: string; auftragsnummer?: string; kunde?: string }
export const nutztEchtesBackend = import.meta.env.VITE_EN1090_API_MODE !== 'mock';
const einheiten = { STUECK: 'Stück', METER: 'm', KILOGRAMM: 'kg', TONNE: 't', QUADRATMETER: 'm²' };

/** Original layout, backed exclusively by persisted purchasing needs. Keep the full snapshot for edits. */
export function bedarfszeile(bedarf: BedarfResponse, projekt?: ProjektKurz) {
    const p = bedarf.position;
    const details = (p as typeof p & { beschaffungsdetails?: { lieferantId?: number; kategorieId?: number; schnittbildId?: number; schnittAchseId?: number; externeArtikelnummer?: string } | null }).beschaffungsdetails;
    return {
        id: bedarf.id, version: bedarf.version, bedarf,
        artikelId: p.artikelId ?? undefined, externeArtikelnummer: details?.externeArtikelnummer ?? undefined,
        lieferantId: details?.lieferantId, lieferantName: undefined as string | undefined, kategorieId: details?.kategorieId, kategorieName: undefined as string | undefined,
        schnittbildId: details?.schnittbildId, schnittAchseId: details?.schnittAchseId,
        produktname: p.bezeichnung ?? undefined, produkttext: p.abmessung ?? undefined,
        werkstoffName: p.werkstoff ?? undefined, kommentar: p.bearbeitung ?? undefined,
        projektId: bedarf.liefergruppe.projektId ?? undefined,
        projektName: projekt?.bauvorhaben, projektNummer: projekt?.auftragsnummer, kundenName: projekt?.kunde,
        menge: p.basis?.menge ?? bedarf.mengen.bedarf ?? 0, stueckzahl: p.basis?.stueckzahl ?? 0,
        einheit: p.basis?.einheit ? einheiten[p.basis.einheit] : '',
        kilogramm: materialGewicht(bedarf) ?? undefined, fixmassMm: p.basis?.einzelLaengeMm ?? null,
        positionsnummer: p.positionsnummer ?? null, mantelflaecheM2: p.basis?.mantelflaecheM2 ?? null,
        schnittForm: p.schnittForm ?? undefined,
        anschnittWinkelLinks: p.winkelLinks ?? undefined, anschnittWinkelRechts: p.winkelRechts ?? undefined,
        zeugnisAnforderung: p.dokumente[0]?.art ?? null,
        bestellt: (bedarf.mengen.bestellt ?? 0) > 0 && (bedarf.mengen.disponierbar ?? 0) <= 0, freiePosition: p.art === 'FREITEXT',
        vorhanden: bedarf.mengen.lagergedeckt ?? 0, bestellen: bedarf.mengen.disponierbar ?? 0,
        werkstattMaximum: vorhandenesMaximum(bedarf),
    };
}
export type Bedarfszeile = ReturnType<typeof bedarfszeile>;

export async function ladeBedarfszeilen(projektId?: number | null): Promise<Bedarfszeile[]> {
    if (!nutztEchtesBackend) {
        const zeilen = await einkaufApi.get<Bedarfszeile[]>('/api/bestellungen/offen');
        return projektId === undefined ? zeilen : zeilen.filter(z => projektId === null ? z.projektId == null : z.projektId === projektId);
    }
    const [bedarfe, projekte] = await Promise.all([
        ladeAlleBedarfe(projektId), einkaufApi.get<ProjektKurz[]>('/api/projekte/simple?size=500'),
    ]);
    const byId = new Map(projekte.map(p => [p.id, p]));
    // The compact project list is limited to 500. Resolve selected needs outside that window explicitly.
    // Names are only decoration: a deleted or unknown project/supplier (404) must not hide the whole list.
    const fehlendeProjekte = [...new Set(bedarfe.map(b => b.liefergruppe.projektId)
        .filter((id): id is number => id != null && !byId.has(id)))];
    for (const [id, projekt] of await ladeNamenEinzeln(fehlendeProjekte, id => einkaufApi.get<ProjektKurz>(`/api/projekte/${id}`))) {
        byId.set(id, projekt);
    }
    const zeilen = bedarfe.map(b => bedarfszeile(b, b.liefergruppe.projektId == null ? undefined : byId.get(b.liefergruppe.projektId)));
    const lieferantIds = [...new Set(zeilen.map(z => z.lieferantId).filter((id): id is number => id != null))];
    const lieferanten = new Map([...await ladeNamenEinzeln(lieferantIds,
        id => einkaufApi.get<{ lieferantenname: string }>(`/api/lieferanten/${id}`))].map(([id, l]) => [id, l.lieferantenname]));
    const kategorien = zeilen.some(z => z.kategorieId != null)
        ? await einkaufApi.get<Array<{ id: number; bezeichnung: string }>>('/api/artikel/kategorien/alle') : [];
    return zeilen.map(z => ({ ...z, lieferantName: z.lieferantId == null ? undefined : lieferanten.get(z.lieferantId),
        kategorieName: kategorien.find(k => k.id === z.kategorieId)?.bezeichnung }));
}

/** Loads each id on its own; failed lookups are skipped so the caller keeps an empty name instead of failing. */
async function ladeNamenEinzeln<T>(ids: number[], lade: (id: number) => Promise<T>): Promise<Map<number, T>> {
    const ergebnisse = await Promise.allSettled(ids.map(lade));
    const gefunden = new Map<number, T>();
    ergebnisse.forEach((ergebnis, index) => { if (ergebnis.status === 'fulfilled') gefunden.set(ids[index], ergebnis.value); });
    return gefunden;
}

export async function speichereWerkstatt(zeilen: Array<{ bedarf: BedarfResponse }>, mengen: Record<number, number>): Promise<void> {
    const positionen = zeilen.filter(z => mengen[z.bedarf.id] !== undefined && mengen[z.bedarf.id] !== (z.bedarf.mengen.lagergedeckt ?? 0)).map(({ bedarf }) => {
        const checked = pruefeVorhanden(bedarf, formatDecimalInput(mengen[bedarf.id]));
        if (!checked.valid) throw new Error(checked.message);
        return { bedarfId: bedarf.id, version: bedarf.version, vorhanden: checked.value };
    });
    if (positionen.length) await einkaufApi.put('/api/einkauf/bedarf/werkstattpruefung', { positionen });
}

/**
 * Grund, warum ein Bedarf nicht mehr gelöscht werden kann – oder null, wenn er noch frei ist.
 * Spiegelt die Mengen, die die Liste kennt; das Backend prüft zusätzlich alle Belege und antwortet sonst mit 409.
 */
export function loeschSperrgrund(bedarf: BedarfResponse | undefined): string | null {
    if (!bedarf) return null;
    const m = bedarf.mengen;
    const mehrAlsNull = (wert: number | null) => (wert ?? 0) > 0;
    if (mehrAlsNull(m.bestellt) || mehrAlsNull(m.geliefert) || mehrAlsNull(m.storniert)) return 'Bereits bestellt – nicht mehr löschbar';
    if (mehrAlsNull(m.angefragt)) return 'Steht in einer Preisanfrage – nicht mehr löschbar';
    if (mehrAlsNull(m.reserviert)) return 'Menge ist reserviert – nicht mehr löschbar';
    if (mehrAlsNull(m.lagergedeckt)) return 'Vorhandene Menge eingetragen – zuerst „Vorhanden“ auf 0 setzen';
    return null;
}

/** Löscht einen noch nicht weiterverarbeiteten Bedarf; die Version schützt vor zwischenzeitlichen Änderungen. */
export async function loescheBedarf(bedarf: Pick<BedarfResponse, 'id' | 'version'>): Promise<void> {
    await einkaufApi.deleteVoid(`/api/einkauf/bedarf/${encodeURIComponent(bedarf.id)}?version=${encodeURIComponent(bedarf.version)}`);
}

/** Download via POST avoids URL limits for larger printed lists. */
export async function druckeBedarfsliste(ids: number[]): Promise<void> {
    const response = await fetch('/api/einkauf/bedarf/pdf', { method: 'POST',
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ bedarfIds: ids }) });
    if (!response.ok) throw new Error('Bedarfsliste konnte nicht erstellt werden.');
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement('a'); link.href = url; link.download = 'Bedarfsliste.pdf'; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 30_000);
}
