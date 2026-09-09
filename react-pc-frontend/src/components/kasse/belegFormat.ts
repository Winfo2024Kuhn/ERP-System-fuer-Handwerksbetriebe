import type { BelegKategorie, KiStatus, Sachkonto, SachkontoTyp, Zahlungsart } from '../../types';

// ===================== Formatierung & Optionen fuer Belege & Kasse =====================
//
// Task 9 (reine Verschiebung, kein Verhalten geaendert): aus
// BelegeKasseEditor.tsx (Zeilen 185-288, 1893-1909, 1999, 2006) und
// KasseShortcuts.tsx (eigenes formatEuro, eigenes inputCls) herausgezogen.
// Alle Kasse-Komponenten importieren ab jetzt von hier statt eigener Kopien.

export const KATEGORIE_LABELS: Record<BelegKategorie, string> = {
    UNZUGEORDNET: 'Noch nicht zugeordnet',
    KASSE_EINNAHME: 'Kasse – Einnahme',
    KASSE_AUSGABE: 'Kasse – Ausgabe',
    PRIVATENTNAHME: 'Privatentnahme',
    PRIVATEINLAGE: 'Privateinlage',
    BANK: 'Bank',
    KREDITKARTE: 'Kreditkarte',
    SONSTIGER_BELEG: 'Sonstiger Beleg',
};

export const KATEGORIE_FARBE: Record<BelegKategorie, string> = {
    UNZUGEORDNET: 'bg-slate-100 text-slate-600',
    KASSE_EINNAHME: 'bg-emerald-100 text-emerald-700',
    KASSE_AUSGABE: 'bg-amber-100 text-amber-700',
    PRIVATENTNAHME: 'bg-fuchsia-100 text-fuchsia-700',
    PRIVATEINLAGE: 'bg-lime-100 text-lime-700',
    BANK: 'bg-sky-100 text-sky-700',
    KREDITKARTE: 'bg-violet-100 text-violet-700',
    SONSTIGER_BELEG: 'bg-slate-100 text-slate-700',
};

export const formatEuro = (v: number | null | undefined): string =>
    v == null || !Number.isFinite(v)
        ? '–'
        : new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v);

/**
 * Formatiert ein Datum als YYYY-MM-DD in LOKALER Zeit.
 *
 * Nicht durch `toISOString()` ersetzen: das rechnet nach UTC um. In
 * Deutschland (UTC+1/+2) wird aus dem 1. Januar 00:00 Ortszeit der
 * 31. Dezember des Vorjahres — der Zeitraum-Filter griffe dann daneben.
 */
export const isoDatum = (d: Date): string => {
    const zweistellig = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${zweistellig(d.getMonth() + 1)}-${zweistellig(d.getDate())}`;
};

export const formatDate = (iso?: string | null): string => {
    if (!iso) return '–';
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '–' : d.toLocaleDateString('de-DE');
};

export const formatDateTime = (iso?: string | null): string => {
    if (!iso) return '–';
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '–' : d.toLocaleString('de-DE');
};

export const SACHKONTO_TYP_LABEL: Record<SachkontoTyp, string> = {
    AUFWAND: 'Aufwand', ERTRAG: 'Ertrag', PRIVAT: 'Privat', NEUTRAL: 'Neutral',
};

// Flacht Sachkonten in Optionen ab und gruppiert sie ueber ein Praefix im Label,
// damit die Pflicht-<Select>-Komponente (ohne optgroup-Support) verwendet werden
// kann. Reihenfolge: Aufwand, Ertrag, Privat, Neutral – innerhalb sortiert nach
// `sortierung`. Der fuehrende Leer-Eintrag erlaubt das aktive Abwaehlen eines
// Kontos.
export function buildSachkontoOptions(sachkonten: Sachkonto[]): { value: string; label: string }[] {
    const order: SachkontoTyp[] = ['AUFWAND', 'ERTRAG', 'PRIVAT', 'NEUTRAL'];
    const grouped = order.flatMap(typ =>
        sachkonten
            .filter(s => s.kontoTyp === typ)
            .sort((a, b) => a.sortierung - b.sortierung)
            .map(s => ({
                value: String(s.id),
                label: `${SACHKONTO_TYP_LABEL[typ]} · ${s.nummer ? `${s.nummer} ` : ''}${s.bezeichnung}`,
            }))
    );
    return [{ value: '', label: '– kein Konto –' }, ...grouped];
}

// Baut die Optionen fuer den Zahlungsart-<Select> aus den Stammdaten. Der
// fuehrende Leer-Eintrag erlaubt das aktive Loeschen der Zahlungsart bei
// bestehenden Belegen. `bestehenderWert` wird zusaetzlich aufgenommen, falls
// ein Altbeleg eine Bezeichnung enthaelt, die (noch) nicht in den Stammdaten
// steht — sonst waere der Select-Wert "verschwunden".
export function buildZahlungsartOptions(
    zahlungsarten: Zahlungsart[],
    bestehenderWert?: string | null,
): { value: string; label: string }[] {
    const sorted = [...zahlungsarten]
        .sort((a, b) => a.sortierung - b.sortierung || a.bezeichnung.localeCompare(b.bezeichnung));
    const opts: { value: string; label: string }[] = [
        { value: '', label: '– keine Angabe –' },
        ...sorted.map(z => ({ value: z.bezeichnung, label: z.bezeichnung })),
    ];
    if (bestehenderWert && !opts.some(o => o.value === bestehenderWert)) {
        opts.push({ value: bestehenderWert, label: `${bestehenderWert} (nicht im Stamm)` });
    }
    return opts;
}

export const KI_LABEL: Record<KiStatus, { label: string; cls: string }> = {
    PENDING: { label: 'KI wartet…', cls: 'bg-slate-100 text-slate-500' },
    LAEUFT: { label: 'KI analysiert…', cls: 'bg-sky-100 text-sky-700' },
    DONE: { label: 'KI fertig', cls: 'bg-emerald-100 text-emerald-700' },
    FAILED: { label: 'KI-Fehler', cls: 'bg-red-100 text-red-700' },
};

export const inputCls = 'w-full p-2.5 border border-slate-200 rounded-lg bg-white text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500';

/**
 * Zusatzklassen fuer Felder, die nach der Festschreibung gesperrt sind.
 * Bewusst nicht ausgegraut bis zur Unlesbarkeit: der Wert soll weiter gut
 * lesbar bleiben, nur eben erkennbar nicht mehr bedienbar.
 */
export const gesperrtCls = 'disabled:bg-slate-100 disabled:text-slate-600 disabled:cursor-not-allowed';

/**
 * Eigenes Input-Feld-Aussehen der Kasse-Shortcut-Modale (bisher `inputCls` in
 * KasseShortcuts.tsx, Zeile 645). Bewusst NICHT dasselbe wie `inputCls` oben
 * (anderes Padding/Rundung) -- beide Zeichenketten bleiben unveraendert
 * bestehen, damit sich an keiner der beiden Stellen optisch etwas aendert.
 */
export const modalInputCls = 'w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500';

/**
 * Uebersetzt den Confidence-Wert der KI (0..1) in Handwerker-Sprache. Die Zahl
 * allein sagt niemandem etwas — und Farbe darf die Aussage nicht alleine
 * tragen (Accessibility: "color-not-only"), deshalb immer zusaetzlich Text.
 *
 * Die Schwellen spiegeln die Confidence-Skala aus der System-Instruktion des
 * Agenten (siehe BelegKiKostenkontoService.SYSTEM_INSTRUCTION).
 */
export function sicherheitsText(confidence: number | null | undefined): { text: string; cls: string } {
    if (confidence == null || !Number.isFinite(confidence)) {
        return { text: 'Sicherheit unbekannt', cls: 'text-slate-600' };
    }
    if (confidence >= 0.95) return { text: 'sehr sicher', cls: 'text-emerald-700' };
    if (confidence >= 0.70) return { text: 'ziemlich sicher', cls: 'text-slate-700' };
    if (confidence >= 0.30) return { text: 'eher unsicher – bitte prüfen', cls: 'text-amber-700' };
    return { text: 'sehr unsicher – bitte prüfen', cls: 'text-amber-700' };
}
