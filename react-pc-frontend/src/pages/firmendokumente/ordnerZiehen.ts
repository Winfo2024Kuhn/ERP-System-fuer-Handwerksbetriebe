import type { Baum } from '../../lib/ordnerBaum';
import { DOKUMENT_HERKUNFT_PRAEFIX, ziehtDateien, ziehtDokument, zieleFuerOrdner } from './firmendokumenteLogik';
import type { FirmenOrdner } from './typen';

/**
 * Reine Logik zum Ziehen von Ordnern und zum Ablegen auf Ordnern (Baum und
 * rechter Bereich): was gerade gezogen wird und was ein Ziel annimmt.
 * Ohne React und ohne Netzwerk.
 */

/** Eigener Datentyp beim Ziehen eines Ordners – vermischt sich nicht mit Dateien oder Dokumenten. */
export const ORDNER_DATENTYP = 'application/x-firmendokument-ordner';

/**
 * Während des Ziehens verrät der Browser nur die Datentypen, nicht die Daten.
 * Damit Ziele schon beim Darüberfahren wissen, welcher Ordner kommt (und sich
 * bei ungültigen Zielen gar nicht erst markieren), steckt die ID zusätzlich im
 * Namen eines zweiten Typs.
 */
const ID_TYP_PRAEFIX = `${ORDNER_DATENTYP}-id-`;

/** Was ein Ziel gerade annehmen würde – `null`: nichts. */
export type AblageArt = 'dateien' | 'dokument' | 'ordner';

type Typen = ArrayLike<string> | readonly string[];

/** Legt den Ordner beim Ziehstart in den `dataTransfer` (plus Name als Text für andere Programme); Ziehen heißt Verschieben. */
export function schreibeGezogenenOrdner(ziel: { setData: (typ: string, wert: string) => void; effectAllowed?: string }, ordner: { id: number; name: string }): void {
    ziel.effectAllowed = 'move';
    ziel.setData(ORDNER_DATENTYP, String(ordner.id));
    ziel.setData(`${ID_TYP_PRAEFIX}${ordner.id}`, String(ordner.id));
    ziel.setData('text/plain', ordner.name);
}

/** Wird gerade ein Ordner aus dem Baum oder dem rechten Bereich gezogen? */
export function ziehtOrdner(event: { dataTransfer: { types: Typen } | null }): boolean {
    return Array.from(event.dataTransfer?.types ?? []).includes(ORDNER_DATENTYP);
}

/** Zahl aus dem Namen des Datentyps mit diesem Präfix – `null`, wenn es keinen (gültigen) gibt. */
function idAusTypen(typen: Typen, praefix: string): number | null {
    const typ = Array.from(typen).find((t) => t.startsWith(praefix));
    if (!typ) return null;
    const roh = typ.slice(praefix.length);
    return /^\d+$/.test(roh) ? Number(roh) : null;
}

/** ID des gezogenen Ordners aus den Datentypen – `null`, wenn kein (gültiger) Ordner gezogen wird. */
export function gezogeneOrdnerId(typen: Typen): number | null {
    return idAusTypen(typen, ID_TYP_PRAEFIX);
}

/** Ordner, aus dem ein gezogenes Dokument kommt – `null`, wenn das nicht mitkommt (z. B. ältere Zieh-Daten). */
export function gezogeneDokumentHerkunft(typen: Typen): number | null {
    return idAusTypen(typen, DOKUMENT_HERKUNFT_PRAEFIX);
}

/** Gesperrte Ziele je Baum und Ordner – beim Ziehen wird oft gefragt, gerechnet wird einmal. */
const sperrenCache = new WeakMap<Baum<FirmenOrdner>, Map<number, Map<number | null, string | null>>>();

function sperrenFuer(baum: Baum<FirmenOrdner>, ordnerId: number): Map<number | null, string | null> {
    let jeOrdner = sperrenCache.get(baum);
    if (!jeOrdner) {
        jeOrdner = new Map();
        sperrenCache.set(baum, jeOrdner);
    }
    let sperren = jeOrdner.get(ordnerId);
    if (!sperren) {
        // Die Admin-Regel prüft erst das Ablegen (mit Meldung) – deshalb hier wie für Admins.
        sperren = new Map(zieleFuerOrdner(baum, ordnerId, true).map((z) => [z.id, z.gesperrt]));
        jeOrdner.set(ordnerId, sperren);
    }
    return sperren;
}

/**
 * Darf der Ordner nach `zielId` (`null` = Hauptordner)? Nicht in sich selbst,
 * nicht in seine Unterordner, nicht dorthin, wo er schon liegt; „Ändern“ am
 * Ordner und am Ziel. Ob er einen eingeschränkten Zweig verlassen darf, prüft
 * zusätzlich `hauptordnerWechselGesperrt` beim Ablegen.
 */
export function ordnerDarfNach(baum: Baum<FirmenOrdner>, ordnerId: number, zielId: number | null): boolean {
    if (!baum.byId.get(ordnerId)?.darfAendern) return false;
    if (zielId !== null && !baum.byId.get(zielId)?.darfAendern) return false;
    const sperren = sperrenFuer(baum, ordnerId);
    return sperren.has(zielId) && sperren.get(zielId) === null;
}

/**
 * Was nimmt das Ziel `zielId` gerade an? Ein Ordner mit „Ändern“ nimmt
 * Dateien, Dokumente aus anderen Ordnern und passende Ordner; die Kopfzeile „Ordner“ (`null`)
 * nimmt nur Ordner, die dadurch zum Hauptordner werden.
 */
export function ablageArt(baum: Baum<FirmenOrdner>, zielId: number | null, typen: Typen): AblageArt | null {
    const event = { dataTransfer: { types: typen } };
    if (ziehtOrdner(event)) {
        const ordnerId = gezogeneOrdnerId(typen);
        return ordnerId !== null && ordnerDarfNach(baum, ordnerId, zielId) ? 'ordner' : null;
    }
    if (zielId === null || !baum.byId.get(zielId)?.darfAendern) return null;
    if (ziehtDateien(event)) return 'dateien';
    // Der eigene Ordner nimmt ein Dokument nicht an. Die Admin-Sperre meldet sich erst beim Ablegen.
    if (ziehtDokument(event)) return gezogeneDokumentHerkunft(typen) === zielId ? null : 'dokument';
    return null;
}
