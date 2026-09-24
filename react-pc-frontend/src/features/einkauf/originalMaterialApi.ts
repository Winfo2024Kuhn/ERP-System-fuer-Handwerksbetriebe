import { einkaufApi } from './api';
import { nutztEchtesBackend } from './originalBedarfApi';
import { materialbedarfPayload, materialPositionAusBedarf, neueMaterialPosition, type MaterialPosition } from './materialbedarfAdapter';
import { formatDecimalInput } from '../../lib/numberInput';
import { validateNumberDrafts } from '../../lib/numberDrafts';
import type { BedarfResponse, Einheit } from './types';

export interface OriginalMaterialPosition extends MaterialPosition {
    originalId?: number; bedarf?: BedarfResponse;
    schnittbildId: number | null; schnittAchseId: number | null; schnittAchseBildUrl?: string | null;
    zeugnisVomSystem: string;
    perProjektId?: number | null; perProjektName?: string | null; perProjektNummer?: string | null;
    perKundenName?: string | null; perExcKlasse?: string | null; perLieferantId?: number | null; perLieferantName?: string | null;
    exportiertAm?: string | null;
}
export const neueOriginalPosition = (): OriginalMaterialPosition => ({ ...neueMaterialPosition(), schnittbildId: null, schnittAchseId: null, zeugnisVomSystem: '' });
export function originalBeschaffungsdetails(bedarf?: BedarfResponse) {
    return (bedarf?.position as (BedarfResponse['position'] & { beschaffungsdetails?: {
        lieferantId?: number | null; kategorieId?: number | null; schnittbildId?: number | null; schnittAchseId?: number | null; externeArtikelnummer?: string | null;
    } | null }) | undefined)?.beschaffungsdetails;
}
export function originalPositionAusBedarf(bedarf: BedarfResponse): OriginalMaterialPosition {
    const details = originalBeschaffungsdetails(bedarf);
    return { ...neueOriginalPosition(), ...materialPositionAusBedarf(bedarf), bedarf, originalId: bedarf.id,
        kategorieId: details?.kategorieId ?? null, schnittbildId: details?.schnittbildId ?? null, schnittAchseId: details?.schnittAchseId ?? null,
        externeArtikelnummer: details?.externeArtikelnummer ?? undefined,
        sonderzuschnitt: !!bedarf.position.schnittForm || details?.schnittbildId != null,
    };
}
export function originalEinheit(value?: string | null): Einheit {
    const units: Record<string, Einheit> = { 'Stück': 'STUECK', m: 'METER', kg: 'KILOGRAMM', t: 'TONNE', 'm²': 'QUADRATMETER', STUECK: 'STUECK', METER: 'METER', KILOGRAMM: 'KILOGRAMM', TONNE: 'TONNE', QUADRATMETER: 'QUADRATMETER' };
    if (!value) return 'STUECK';
    const result = units[value];
    if (!result) throw new Error(`Die Einheit „${value}“ wird nicht unterstützt. Bitte eine gültige Einheit wählen.`);
    return result;
}
export const originalZeugnis = (value?: string | null): string => ({ WZ_2_1: 'ZEUGNIS_2_1', WZ_2_2: 'ZEUGNIS_2_2', APZ_3_1: 'ZEUGNIS_3_1', APZ_3_2: 'ZEUGNIS_3_2', CE_KONFORMITAET: 'CE_NACHWEIS' }[value ?? ''] ?? value ?? '');

/** Validate every row before the first write; retain the original version and entire technical snapshot. */
export function originalMaterialPayload(row: OriginalMaterialPosition, projektId: number | null, lieferantId: number | null) {
    if (nutztEchtesBackend && row.originalId != null && !row.bedarf) throw new Error('Bitte den Bedarf erneut laden, bevor Sie ihn bearbeiten.');
    const winkel = validateNumberDrafts({ links: row.sonderzuschnitt ? row.winkelLinks : '', rechts: row.sonderzuschnitt ? row.winkelRechts : '' }, {
        links: { label: 'Winkel links', min: -360, max: 360, maxDecimalPlaces: 2 }, rechts: { label: 'Winkel rechts', min: -360, max: 360, maxDecimalPlaces: 2 },
    });
    if (!winkel.valid) throw new Error(winkel.message);
    if (row.sonderzuschnitt && !row.schnittForm && !row.schnittbildId) throw new Error('Bitte ein Schnittbild auswählen.');
    const payload = materialbedarfPayload({ ...row, sonderzuschnitt: row.sonderzuschnitt && !!row.schnittForm }, projektId, row.bedarf);
    const unveraenderteWinkel = !!row.bedarf && row.winkelLinks === materialPositionAusBedarf(row.bedarf).winkelLinks
        && row.winkelRechts === materialPositionAusBedarf(row.bedarf).winkelRechts;
    const position = { ...row.bedarf?.position, ...payload.position,
        ...(row.sonderzuschnitt && !row.schnittForm && !unveraenderteWinkel ? { winkelLinks: formatDecimalInput(winkel.values.links ?? 90), winkelRechts: formatDecimalInput(winkel.values.rechts ?? 90) } : {}),
        beschaffungsdetails: { lieferantId, kategorieId: row.kategorieId, schnittbildId: row.sonderzuschnitt ? row.schnittbildId : null,
            schnittAchseId: row.sonderzuschnitt ? row.schnittAchseId : null, externeArtikelnummer: row.externeArtikelnummer?.trim() || null },
    };
    if (nutztEchtesBackend) return { ...payload, position };
    const unitNames = { STUECK: 'Stück', METER: 'm', KILOGRAMM: 'kg', TONNE: 't', QUADRATMETER: 'm²' };
    const certificates: Record<string, string> = { ZEUGNIS_2_1: 'WZ_2_1', ZEUGNIS_2_2: 'WZ_2_2', ZEUGNIS_3_1: 'APZ_3_1', ZEUGNIS_3_2: 'APZ_3_2', CE_NACHWEIS: 'CE_KONFORMITAET' };
    return { projektId, lieferantId, kategorieId: row.kategorieId, artikelId: row.artikelId, produktname: row.produktname.trim(), produkttext: row.produkttext.trim() || null,
        werkstoffName: row.werkstoffName || null, menge: position.basis?.menge, einheit: unitNames[row.einheit], fixmassMm: position.basis?.einzelLaengeMm,
        schnittbildId: row.sonderzuschnitt ? row.schnittbildId : null, schnittAchseId: row.sonderzuschnitt ? row.schnittAchseId : null,
        anschnittWinkelLinks: row.sonderzuschnitt ? winkel.values.links ?? 90 : null, anschnittWinkelRechts: row.sonderzuschnitt ? winkel.values.rechts ?? 90 : null,
        zeugnisAnforderung: (certificates[row.zeugnis] ?? row.zeugnis) || null, kommentar: row.kommentar.trim() || null };
}
export async function speichereOriginalMaterial(row: OriginalMaterialPosition, payload: ReturnType<typeof originalMaterialPayload>): Promise<void> {
    if (nutztEchtesBackend) {
        if (row.bedarf) await einkaufApi.put(`/api/einkauf/bedarf/${row.bedarf.id}`, payload);
        else await einkaufApi.post('/api/einkauf/bedarf', payload);
    } else {
        if (row.originalId != null) await einkaufApi.put(`/api/bestellungen/${row.originalId}`, payload);
        else await einkaufApi.post('/api/bestellungen/manuell', payload);
    }
}
