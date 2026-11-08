import { formatDecimalInput, validateDecimalInput } from '../../lib/numberInput';
import type { FirmenEntwurf, FirmenZahlenFeld, Firmeninformation } from './firmaTypen';

/** Formularstand aus den gespeicherten Firmendaten: Zahlen werden zu Eingabetexten. */
export const firmenEntwurf = (firma: Firmeninformation): FirmenEntwurf => ({
    ...firma,
    tageBisZahlungserinnerung: formatDecimalInput(firma.tageBisZahlungserinnerung ?? 7),
    tageBisErsteMahnung: formatDecimalInput(firma.tageBisErsteMahnung ?? 7),
    tageBisZweiteMahnung: formatDecimalInput(firma.tageBisZweiteMahnung ?? 7),
    mahnverfahrenNeuesZahlungszielTage: formatDecimalInput(firma.mahnverfahrenNeuesZahlungszielTage ?? 7),
    bgSatzOverride: firma.bgSatzOverride == null ? '' : formatDecimalInput(firma.bgSatzOverride),
});

const ZAHLEN_LABELS: Record<FirmenZahlenFeld, string> = {
    tageBisZahlungserinnerung: 'Tage nach Fälligkeit',
    tageBisErsteMahnung: 'Tage nach der Zahlungserinnerung',
    tageBisZweiteMahnung: 'Tage nach der 1. Mahnung',
    mahnverfahrenNeuesZahlungszielTage: 'Neues Zahlungsziel',
    bgSatzOverride: 'Tatsächlicher BG-Satz',
};

const FIRMENFARBE = /^#(?:[0-9a-f]{3}|[0-9a-f]{6})$/i;

export type FirmenPruefung =
    | { ok: true; payload: Partial<Firmeninformation> }
    | { ok: false; meldung: string };

/**
 * Prüft den Entwurf vollständig vor dem Speichern (erst die Zahlenfelder, dann
 * die Firmenfarbe) und baut daraus die Daten für `PUT /api/firma`.
 * Mahntage: Pflicht, ganze Zahl ab 1. BG-Satz: optional, 0 bis 100, leer = null.
 */
export function pruefeFirmenEntwurf(entwurf: FirmenEntwurf): FirmenPruefung {
    const { tageBisZahlungserinnerung, tageBisErsteMahnung, tageBisZweiteMahnung, mahnverfahrenNeuesZahlungszielTage, bgSatzOverride, ...rest } = entwurf;
    const zahlen: Record<FirmenZahlenFeld, string> = { tageBisZahlungserinnerung, tageBisErsteMahnung, tageBisZweiteMahnung, mahnverfahrenNeuesZahlungszielTage, bgSatzOverride };
    const payload: Partial<Firmeninformation> = { ...rest };
    for (const key of Object.keys(zahlen) as FirmenZahlenFeld[]) {
        const optional = key === 'bgSatzOverride';
        const result = validateDecimalInput(zahlen[key], { label: ZAHLEN_LABELS[key], required: !optional, integer: !optional, min: optional ? 0 : 1, ...(optional ? { max: 100 } : {}) });
        if (!result.valid) return { ok: false, meldung: result.message };
        if (key === 'bgSatzOverride') payload[key] = result.value;
        else if (result.value !== null) payload[key] = result.value;
    }
    if (entwurf.firmenfarbe && !FIRMENFARBE.test(entwurf.firmenfarbe)) {
        return { ok: false, meldung: 'Bitte eine gültige Firmenfarbe eingeben, z. B. #500010.' };
    }
    return { ok: true, payload };
}
