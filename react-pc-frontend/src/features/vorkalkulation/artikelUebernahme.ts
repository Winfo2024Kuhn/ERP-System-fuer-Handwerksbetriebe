/**
 * Uebernahme eines Artikels aus dem Materialstamm in eine Kalkulationszeile.
 *
 * Bewusst ein eigenes, React-freies Modul: Die Zuordnung von Verrechnungs-
 * einheit, Preisbezug und Gewicht ist die fehleranfaelligste Stelle der
 * Uebernahme (ein falscher Preisbezug multipliziert den Materialpreis mit dem
 * Gewicht), und nur so laesst sie sich direkt testen.
 */
import { artikelBezeichnung } from '../../components/artikel/artikelBezeichnung';
import type { Artikel } from '../../types';
import { neueMaterialzeile } from './beispieldaten';
import type { MaterialPosition, Mengeneinheit } from './types';

/**
 * Verrechnungseinheit des Materialstamms als Mengeneinheit der Kalkulation.
 * Dieselben vier Werte, nur ein anderer Name fuer den Meter.
 */
export function stammEinheit(artikel: Artikel): Mengeneinheit {
    const roh = typeof artikel.verrechnungseinheit === 'string'
        ? artikel.verrechnungseinheit
        : artikel.verrechnungseinheit?.name;
    if (roh === 'LAUFENDE_METER') return 'METER';
    if (roh === 'QUADRATMETER') return 'QUADRATMETER';
    if (roh === 'KILOGRAMM') return 'KILOGRAMM';
    return 'STUECK';
}

/** Zahl als Eingabe-Entwurf: deutsches Komma, keine Tausenderpunkte. */
export function alsEntwurf(wert: number | undefined | null): string {
    if (wert === undefined || wert === null) return '';
    return wert.toLocaleString('de-DE', { useGrouping: false, maximumFractionDigits: 4 });
}

export function zeileAusArtikel(
    artikel: Artikel,
    menge: string,
    beschaffung: 'LAGER' | 'BESTELLEN',
): MaterialPosition {
    const einheit = stammEinheit(artikel);
    // Bei Blech haengt das Gewicht an der Flaeche, sonst an der Laenge.
    const kgJeEinheit = einheit === 'QUADRATMETER' ? artikel.kgProQm : artikel.kgProMeter;
    const verzinkbar = artikel.verzinkungsgeeignet === true;
    const pulverbeschichtbar = artikel.pulverbeschichtungsgeeignet === true;

    return neueMaterialzeile({
        artikelId: artikel.id,
        artikelnummer: artikel.artikelnummer ?? artikel.externeArtikelnummer ?? '',
        bezeichnung: artikelBezeichnung(artikel),
        menge,
        einheit,
        preis: alsEntwurf(artikel.guenstigsterPreis ?? artikel.preis),
        /**
         * Der Stammpreis ist IMMER der Preis je Verrechnungseinheit:
         * `ArtikelPositionsPreisService.umrechnungsfaktor` gibt fuer alles
         * ausser KILOGRAMM den Faktor 1 zurueck, und die Artikelsuche schreibt
         * ihn als "X EUR / Laufende Meter" an die Zeile. Bei Kilogramm-Ware ist
         * die Zeileneinheit selbst kg — auch dort passt "je Einheit".
         *
         * Der kg-Umschalter bleibt den Handzeilen vorbehalten, wo der Bediener
         * bewusst einen Kilopreis eintraegt (so wie in der Excel-Mappe).
         */
        preisbezug: 'EINHEIT',
        kgJeEinheit: alsEntwurf(kgJeEinheit),
        qmJeEinheit: alsEntwurf(artikel.mantelflaeche),
        verzinkbar,
        pulverbeschichtbar,
        // Die Eignung aus den Stammdaten ist nur der Vorschlag — entschieden
        // wird pro Kalkulation an der Zeile.
        verzinken: verzinkbar,
        beschaffung,
    });
}
