/**
 * Reine Hilfsfunktionen für die Rechnungs-Leiste im Zuordnungsdialog: Eine
 * Bestellung (Kette) kann mehrere Rechnungen haben, z. B. Teilrechnungen. Jede
 * wird für sich zugeordnet; der Dialog springt nach dem Speichern zur nächsten
 * offenen Rechnung.
 *
 * Gutschriften bleiben außen vor – es zählt nur der Typ RECHNUNG.
 */

import { parseIsoDatum, type KettenDokumentTyp } from './bestellungenListe';

export interface LeistenDokument {
    id: number;
    typ: KettenDokumentTyp;
    dokumentNummer: string | null;
    dokumentDatum: string | null;
    eingangsDatum?: string | null;
    betragBrutto: number | null;
    betragNetto?: number | null;
    dateiname: string;
    /** Hat schon Projekt- oder Kostenstellen-Anteile (auch die automatische Standard-Kostenstelle). Fehlt bei älteren Antworten. */
    zugeordnet?: boolean;
    /** Als Lagerbestellung markiert. Fehlt bei älteren Antworten. */
    lagerbestellung?: boolean;
    ausgeblendet?: boolean;
}

export type RechnungsStatus = 'offen' | 'zugeordnet' | 'lager' | 'ausgeblendet';

/** Status für die Anzeige; ausgeblendet geht vor Lager, Lager vor zugeordnet. */
export function rechnungsStatus(dokument: LeistenDokument): RechnungsStatus {
    if (dokument.ausgeblendet) return 'ausgeblendet';
    if (dokument.lagerbestellung) return 'lager';
    if (dokument.zugeordnet) return 'zugeordnet';
    return 'offen';
}

/** Offen = Rechnung, noch nicht zugeordnet, keine Lagerbestellung, nicht ausgeblendet. */
export function istOffeneRechnung(dokument: LeistenDokument): boolean {
    return dokument.typ === 'RECHNUNG' && rechnungsStatus(dokument) === 'offen';
}

function datumVon(dokument: LeistenDokument): number | null {
    return (parseIsoDatum(dokument.dokumentDatum) ?? parseIsoDatum(dokument.eingangsDatum))?.getTime() ?? null;
}

/** Die Rechnungen der Kette, nach Datum (ohne Datum zuletzt), bei Gleichstand nach ID. */
export function rechnungenDerKette<T extends LeistenDokument>(dokumente: readonly T[]): T[] {
    return dokumente
        .filter(d => d.typ === 'RECHNUNG')
        .sort((a, b) => {
            const da = datumVon(a);
            const db = datumVon(b);
            if (da !== db) {
                if (da == null) return 1;
                if (db == null) return -1;
                return da - db;
            }
            return a.id - b.id;
        });
}

export function anzahlOffeneRechnungen(dokumente: readonly LeistenDokument[]): number {
    return dokumente.filter(istOffeneRechnung).length;
}

/** Womit der Dialog startet: die erste offene Rechnung, sonst die erste überhaupt. */
export function startRechnungId(rechnungen: readonly LeistenDokument[]): number | null {
    return (rechnungen.find(istOffeneRechnung) ?? rechnungen[0])?.id ?? null;
}

/**
 * Nächste offene Rechnung nach der aktuellen (am Ende geht es vorne weiter);
 * die aktuelle selbst zählt nie. `null` = keine offene mehr übrig.
 */
export function naechsteOffeneRechnungId(rechnungen: readonly LeistenDokument[], aktuelleId: number): number | null {
    const start = rechnungen.findIndex(r => r.id === aktuelleId);
    for (let schritt = 1; schritt <= rechnungen.length; schritt++) {
        const kandidat = rechnungen[(start + schritt + rechnungen.length) % rechnungen.length];
        if (kandidat.id !== aktuelleId && istOffeneRechnung(kandidat)) return kandidat.id;
    }
    return null;
}

/** Gerade gespeicherte Rechnungen sofort als zugeordnet zeigen, ohne auf das Neuladen zu warten. */
export function mitLokalZugeordnet<T extends LeistenDokument>(rechnungen: readonly T[], ids: ReadonlySet<number>): T[] {
    return rechnungen.map(r => (ids.has(r.id) && !r.zugeordnet ? { ...r, zugeordnet: true } : r));
}
