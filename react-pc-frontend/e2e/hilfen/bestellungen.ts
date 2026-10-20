import { istAlt, passtZurSuche, type Kette } from '../../src/features/bestellungen/bestellungenListe';

/**
 * Nachbau von `GET /api/bestellungen-uebersicht?anzahl=&suche=` für die Specs:
 * filtert jede Gruppe mit derselben Suche wie der Server (unter 2 Zeichen = keine
 * Suche), kürzt auf `anzahl` (Standard 25) und zählt in `gesamt`, wie viele es
 * vor dem Kürzen waren. Angebote und laufende Bestellungen kürzt er wie der
 * Server je Alter getrennt: bis zu `anzahl` aktuelle und bis zu `anzahl` alte.
 * Die Ketten müssen schon „neueste zuerst“ sortiert übergeben werden.
 */
/** Locker typisiert: Specs schreiben die Dokumentart oft als einfachen String. */
type SpecKette = { lieferantName: string | null; dokumente: unknown[] };

export interface UebersichtDaten<T extends SpecKette> {
    offeneAnfragen: T[];
    laufendeBestellungen: T[];
    abgeschlossen: T[];
    zugeordnet: T[];
    ausgeblendet: T[];
}

export function bestellungenAntwort<T extends SpecKette>(daten: UebersichtDaten<T>, url: URL, heute: Date) {
    const anzahl = Math.min(1000, Math.max(1, Number(url.searchParams.get('anzahl') ?? 25) || 25));
    const rohSuche = (url.searchParams.get('suche') ?? '').trim();
    const suche = rohSuche.length >= 2 ? rohSuche : '';
    const filter = (liste: T[]) => liste.filter(k => passtZurSuche(k as unknown as Kette, suche));
    const alt = (k: T) => istAlt(k as unknown as Kette, heute);
    /** Je Alter getrennt kürzen, Reihenfolge (neueste zuerst) bleibt. */
    const jeAlterKuerzen = (liste: T[]) => {
        let aktuell = 0;
        let aelter = 0;
        return liste.filter(k => (alt(k) ? ++aelter : ++aktuell) <= anzahl);
    };
    const offen = filter(daten.offeneAnfragen);
    const laufend = filter(daten.laufendeBestellungen);
    const abgeschlossen = filter(daten.abgeschlossen);
    const zugeordnet = filter(daten.zugeordnet);
    const ausgeblendet = filter(daten.ausgeblendet);
    return {
        offeneAnfragen: jeAlterKuerzen(offen),
        laufendeBestellungen: jeAlterKuerzen(laufend),
        abgeschlossen: abgeschlossen.slice(0, anzahl),
        zugeordnet: zugeordnet.slice(0, anzahl),
        ausgeblendet: ausgeblendet.slice(0, anzahl),
        gesamt: {
            offeneAnfragen: offen.length,
            offeneAnfragenAktuell: offen.filter(k => !alt(k)).length,
            laufendeBestellungen: laufend.length,
            laufendeBestellungenAktuell: laufend.filter(k => !alt(k)).length,
            abgeschlossen: abgeschlossen.length,
            zugeordnet: zugeordnet.length,
            ausgeblendet: ausgeblendet.length,
        },
    };
}
