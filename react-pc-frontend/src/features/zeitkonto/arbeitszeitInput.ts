import type { Arbeitszeit } from '../../types/zeitkonto';
import { formatDecimalInput, validateDecimalInput } from '../../lib/numberInput';
import { validateTimeInput } from '../../components/ui/time-input';

export type StundenKey = Exclude<keyof Arbeitszeit, 'buchungStartZeit' | 'buchungEndeZeit'>;
export type ArbeitszeitEntwurf = Record<keyof Arbeitszeit, string>;
export const WOCHENTAGE: ReadonlyArray<{ key: StundenKey; label: string }> = [
    { key: 'montagStunden', label: 'Montag' },
    { key: 'dienstagStunden', label: 'Dienstag' },
    { key: 'mittwochStunden', label: 'Mittwoch' },
    { key: 'donnerstagStunden', label: 'Donnerstag' },
    { key: 'freitagStunden', label: 'Freitag' },
    { key: 'samstagStunden', label: 'Samstag' },
    { key: 'sonntagStunden', label: 'Sonntag' },
];
export interface ZeitfensterBeschriftung { start: string; ende: string; reihenfolgeFehler: string }
export const ZEITFENSTER_LABELS: ZeitfensterBeschriftung = {
    start: 'Frühester Start', ende: 'Spätestes Ende',
    reihenfolgeFehler: 'Spätestes Ende muss nach dem frühesten Start liegen.',
};
export const BUCHUNGSZEIT_LABELS: ZeitfensterBeschriftung = {
    start: 'Früheste Buchung', ende: 'Späteste Buchung',
    reihenfolgeFehler: 'Späteste Buchung muss nach der frühesten Buchung liegen.',
};
export function leereArbeitszeit(): Arbeitszeit {
    return { montagStunden: 0, dienstagStunden: 0, mittwochStunden: 0, donnerstagStunden: 0,
        freitagStunden: 0, samstagStunden: 0, sonntagStunden: 0,
        buchungStartZeit: null, buchungEndeZeit: null };
}
export function zeitEntwurf(arbeitszeit: Arbeitszeit): ArbeitszeitEntwurf {
    return {
        ...Object.fromEntries(WOCHENTAGE.map(tag => [tag.key, formatDecimalInput(arbeitszeit[tag.key])])) as Record<StundenKey, string>,
        buchungStartZeit: arbeitszeit.buchungStartZeit?.slice(0, 5) ?? '',
        buchungEndeZeit: arbeitszeit.buchungEndeZeit?.slice(0, 5) ?? '',
    };
}
/** Erst bei Vorschau/Speichern übernehmen; leere Zwischenstände bleiben im Entwurf erhalten. */
export function pruefeArbeitszeit(entwurf: ArbeitszeitEntwurf, labels = ZEITFENSTER_LABELS): Arbeitszeit {
    const result = leereArbeitszeit();
    for (const tag of WOCHENTAGE) {
        const parsed = validateDecimalInput(entwurf[tag.key], { label: `${tag.label} Stunden`, required: true, min: 0, max: 24 });
        if (!parsed.valid || parsed.value === null) throw new Error(!parsed.valid ? parsed.message : `Bitte ${tag.label} Stunden eingeben.`);
        result[tag.key] = parsed.value;
    }
    const rawStart = (entwurf.buchungStartZeit ?? '').trim();
    const rawEnde = (entwurf.buchungEndeZeit ?? '').trim();
    const strictTime = /^([01]\d|2[0-3]):[0-5]\d$/;
    const invalidTimeMessage = (label: string) => `Bitte ${label} als gültige Uhrzeit von 00:00 bis 23:59 eingeben (HH:mm).`;
    const start = validateTimeInput(entwurf.buchungStartZeit, { label: labels.start });
    const ende = validateTimeInput(entwurf.buchungEndeZeit, { label: labels.ende });
    if (rawStart && !strictTime.test(rawStart)) throw new Error(invalidTimeMessage(labels.start));
    if (rawEnde && !strictTime.test(rawEnde)) throw new Error(invalidTimeMessage(labels.ende));
    if (!start.valid) throw new Error(start.message);
    if (!ende.valid) throw new Error(ende.message);
    if (start.value && ende.value && start.value >= ende.value) throw new Error(labels.reihenfolgeFehler);
    return { ...result, buchungStartZeit: start.value, buchungEndeZeit: ende.value };
}
