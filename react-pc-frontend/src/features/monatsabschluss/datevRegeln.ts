import type { Konfiguration, Stundenart } from './types';

/**
 * Gemeinsame Regeln der DATEV-Übergabe.
 *
 * <p>Eingerichtet wird DATEV unter Einstellungen → Zeiterfassung, exportiert
 * im Monatsabschluss. Beide Stellen brauchen dieselben Kategorien und
 * dieselbe Nummernprüfung – deshalb stehen sie hier und nicht doppelt in
 * zwei Komponenten.</p>
 */

/** Die Stundenarten des Monatsabschlusses, die als eigene Lohnart ans Steuerbüro gehen können. */
export const DATEV_KATEGORIEN = [
    ['ARBEIT', 'Arbeit'],
    ['FEIERTAG', 'Feiertage'],
    ['URLAUB', 'Urlaub'],
    ['KRANKHEIT', 'Krankheit'],
    ['FORTBILDUNG', 'Fortbildung'],
    ['ZEITAUSGLEICH', 'Zeitausgleich'],
    ['KRANKENGELD', 'Krankengeld'],
    ['WIEDEREINGLIEDERUNG', 'Wiedereingliederung'],
] as const;

/** Lesbarer Name einer Kategorie, z. B. „Feiertage“ für `FEIERTAG`. */
export function kategorieName(kategorie: string): string | undefined {
    return DATEV_KATEGORIEN.find(([schluessel]) => schluessel === kategorie)?.[1];
}

/** Kurz- und Langtext der beiden Stundenarten – an einer Stelle, damit Einstellungen und Export dasselbe sagen. */
export const STUNDENART_TEXT: Record<Stundenart, { kurz: string; lang: string }> = {
    SOLL: { kurz: 'Soll-Stunden', lang: 'Soll-Stunden (laut Arbeitszeitkonto)' },
    IST: { kurz: 'Ist-Stunden', lang: 'Ist-Stunden (tatsächlich gearbeitet)' },
};

/**
 * Prüft eine Kennnummer (Beraternummer, Lohnart, Personalnummer …).
 *
 * <p>Kennnummern bleiben Ziffernstrings mit führenden Nullen, müssen aber
 * eine Nummer größer als 0 ergeben. Leer ist erlaubt – fehlende Angaben
 * meldet erst die Vorprüfung vor dem Export.</p>
 *
 * @return die Fehlermeldung oder `''`, wenn alles passt
 */
export function nummerFehler(value: string, min: number, max: number, label: string): string {
    if (!value) return '';
    const nurZiffern = new RegExp(`^\\d{${min},${max}}$`).test(value);
    if (nurZiffern && /[1-9]/.test(value)) return '';
    return `${label}: Bitte ${min === max ? min : `${min} bis ${max}`} Ziffern und eine Nummer größer als 0 eingeben.`;
}

/**
 * Bringt eine Konfiguration vom Server in die Form des Formulars: alle
 * Kategorien in fester Reihenfolge, leere Texte statt `null` und eine
 * Stundenart je Personalnummer.
 */
export function normalisiereKonfiguration(c: Konfiguration): Konfiguration {
    return {
        ...c,
        beraterNr: c.beraterNr ?? '',
        mandantenNr: c.mandantenNr ?? '',
        stundenart: c.stundenart === 'IST' ? 'IST' : 'SOLL',
        zuordnungen: DATEV_KATEGORIEN.map(([kategorie]) => {
            const z = c.zuordnungen.find(row => row.kategorie === kategorie);
            return { kategorie, ausgeschlossen: z?.ausgeschlossen ?? false, lohnart: z?.lohnart ?? '' };
        }),
        personalnummern: c.personalnummern.map(p => ({ ...p, stundenart: p.stundenart ?? null })),
    };
}

/**
 * Trägt eine Personalnummer ein. Wird sie geleert, fällt die Stunden-Auswahl
 * des Mitarbeiters auf „wie Betrieb“ zurück – ohne Personalnummer speichert
 * der Server keine eigene Stundenart.
 */
export function mitPersonalnummer(c: Konfiguration, mitarbeiterId: number, personalnummer: string): Konfiguration {
    const bisher = c.personalnummern.find(p => p.mitarbeiterId === mitarbeiterId);
    const stundenart = personalnummer === '' ? null : bisher?.stundenart ?? null;
    return {
        ...c,
        personalnummern: [...c.personalnummern.filter(p => p.mitarbeiterId !== mitarbeiterId), { mitarbeiterId, personalnummer, stundenart }]
            .sort((a, b) => a.mitarbeiterId - b.mitarbeiterId),
    };
}

/** Setzt die Stundenart eines Mitarbeiters (`null` = wie Betrieb). Ohne Personalnummer bleibt alles, wie es ist. */
export function mitStundenart(c: Konfiguration, mitarbeiterId: number, stundenart: Stundenart | null): Konfiguration {
    if (!c.personalnummern.some(p => p.mitarbeiterId === mitarbeiterId && p.personalnummer !== '')) return c;
    return { ...c, personalnummern: c.personalnummern.map(p => p.mitarbeiterId === mitarbeiterId ? { ...p, stundenart } : p) };
}

/**
 * Prüft das Formular vollständig, bevor gespeichert wird.
 *
 * @param nameVon liefert den Namen zur Mitarbeiter-ID für verständliche Meldungen
 * @return die erste Fehlermeldung oder `''`
 */
export function pruefeKonfiguration(c: Konfiguration, nameVon: (mitarbeiterId: number) => string): string {
    const nummern = c.personalnummern.filter(p => p.personalnummer !== '');
    const doppelt = new Set(nummern.map(p => p.personalnummer.replace(/^0+/, ''))).size !== nummern.length;
    return nummerFehler(c.beraterNr ?? '', 4, 7, 'Beraternummer')
        || nummerFehler(c.mandantenNr ?? '', 1, 5, 'Mandantennummer')
        || c.zuordnungen.map(z => nummerFehler(z.lohnart ?? '', 1, 4, `Lohnart für ${kategorieName(z.kategorie) ?? z.kategorie}`)).find(Boolean)
        || nummern.map(p => nummerFehler(p.personalnummer, 1, 5, `Personalnummer für ${nameVon(p.mitarbeiterId)}`)).find(Boolean)
        || (doppelt ? 'Eine Personalnummer ist mehrfach vergeben. Bitte für jeden Mitarbeiter eine eigene Nummer eintragen.' : '');
}

/** Was an den Server geht: leere Personalnummern werden wie bisher nicht mitgeschickt. */
export function zumSpeichern(c: Konfiguration): Konfiguration {
    return { ...c, personalnummern: c.personalnummern.filter(p => p.personalnummer !== '') };
}

/** Sind Berater- und Mandantennummer eingetragen? Ohne beide kann DATEV keine Datei annehmen. */
export function zugangEingetragen(c: Pick<Konfiguration, 'beraterNr' | 'mandantenNr'>): boolean {
    return !!c.beraterNr && !!c.mandantenNr;
}
