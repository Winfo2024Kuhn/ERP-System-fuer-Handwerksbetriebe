import { AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN, type AusgangsGeschaeftsDokument } from '../../types';
import { buildBezugsdokumentKontext } from './helpers';
import {
    dokumentKontextAusAnfrage,
    dokumentKontextAusKunde,
    dokumentKontextAusProjekt,
    kontextAusDokumentSelbst,
    type AnfrageKontextAntwort,
    type KundeKontextAntwort,
    type ProjektKontextAntwort,
} from './dokumentKontextAbbildung';
import type { KontextDaten } from './types';

/**
 * Ladefunktionen für den Kontext eines gespeicherten Dokuments. Sie setzen
 * keinen State, sondern liefern eine Änderung `vorher => nachher` (oder `null`
 * für „nichts ändern“), die `useDokumentKontext` anwendet.
 */
export type KontextAenderung = (vorher: KontextDaten) => KontextDaten;

/** JSON einer Antwort; `null`, wenn der Server ablehnt. Netzwerkfehler werfen weiter. */
export function holeJson<T>(url: string): Promise<T | null> {
    return fetch(url).then(res => (res.ok ? res.json() as Promise<T> : null));
}

/**
 * Kunde, Projekt oder Anfrage eines gespeicherten Dokuments. Gebuchte oder
 * nicht verknüpfte Dokumente nehmen nur ihre eigenen Werte.
 * `adresseBehalten` wird erst beim Anwenden gelesen (Nutzer kann inzwischen
 * getippt haben). Netzwerkfehler gehen an den Aufrufer.
 */
export function ladeStammdatenKontext(
    data: AusgangsGeschaeftsDokument,
    adresseBehalten: () => boolean,
): Promise<KontextAenderung | null> {
    if (data.gebucht || !(data.projektId || data.anfrageId || data.kundeId)) {
        return Promise.resolve(() => kontextAusDokumentSelbst(data));
    }
    if (data.projektId) {
        return holeJson<ProjektKontextAntwort>(`/api/projekte/${data.projektId}`)
            .then(projekt => projekt && (() => dokumentKontextAusProjekt(data, projekt)));
    }
    if (data.anfrageId) {
        return holeJson<AnfrageKontextAntwort>(`/api/anfragen/${data.anfrageId}`)
            .then(anfrage => anfrage && (() => dokumentKontextAusAnfrage(data, anfrage)));
    }
    return holeJson<KundeKontextAntwort>(`/api/kunden/${data.kundeId}`)
        .then(kunde => kunde && (vorher => dokumentKontextAusKunde(vorher, data, kunde, adresseBehalten())));
}

/**
 * Ein expliziter Vorgänger ist die verbindliche Quelle für Bezugsnummer, -typ
 * und -datum. Fehler beim Vorgänger werden nur gemeldet, nicht weitergegeben.
 * Folgedokumente einer Anfrage ohne Vorgänger beziehen sich auf ein Angebot.
 */
export function ladeBezugKontext(data: AusgangsGeschaeftsDokument): Promise<KontextAenderung | null> {
    if (data.vorgaengerId) {
        return holeJson<AusgangsGeschaeftsDokument>(`/api/ausgangs-dokumente/${data.vorgaengerId}`)
            .then((vorgaenger): KontextAenderung | null => {
                if (!vorgaenger) return null;
                const typLabel = AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === vorgaenger.typ)?.label || vorgaenger.typ;
                return vorher => ({ ...vorher, ...buildBezugsdokumentKontext(vorgaenger, typLabel) });
            })
            .catch(err => {
                console.error('Fehler beim Laden des Bezugsdokuments:', err);
                return null;
            });
    }
    if (data.anfrageId && data.typ !== 'ANGEBOT') {
        return Promise.resolve(vorher => ({ ...vorher, bezugsdokumentTyp: 'Angebot' }));
    }
    return Promise.resolve(null);
}
