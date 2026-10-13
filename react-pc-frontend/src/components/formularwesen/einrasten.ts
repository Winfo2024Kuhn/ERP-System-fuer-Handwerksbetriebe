import type { FormBlock } from '../../types';
import { PAGE_FORMATS } from './constants';
import { normMasse, rechteckInPt, type Briefform } from './normMasse';

/**
 * Magnetisches Einrasten beim Verschieben: Kante oder Mitte eines Bausteins
 * springt an Seitenränder, Seitenmitte, andere Bausteine und – wenn die
 * Norm-Hilfslinien an sind – an Anschriftzone und Infoblock.
 */

export interface Fuehrungslinien {
    /** x-Werte senkrechter Linien (pt). */
    senkrecht: number[];
    /** y-Werte waagerechter Linien (pt). */
    waagerecht: number[];
}

export interface EinrastErgebnis {
    x: number;
    y: number;
    /** Linien, an denen der Baustein gerade eingerastet ist – zum Anzeigen. */
    linien: Fuehrungslinien;
}

const SEITE = PAGE_FORMATS.A4;

/** Alle Linien, an denen ein Baustein einrasten kann (ohne den Baustein selbst). */
export function einrastZiele(items: FormBlock[], blockId: string, seite: number, normForm: Briefform | null): Fuehrungslinien {
    const senkrecht = [0, SEITE.width / 2, SEITE.width];
    const waagerecht = [0, SEITE.height / 2, SEITE.height];
    for (const o of items) {
        if (o.id === blockId || o.page !== seite) continue;
        senkrecht.push(o.x, o.x + o.width / 2, o.x + o.width);
        waagerecht.push(o.y, o.y + o.height / 2, o.y + o.height);
    }
    if (normForm && seite === 1) {
        const masse = normMasse(normForm);
        for (const r of [masse.anschriftzone, masse.anschriftfeld, masse.infoblock].map(rechteckInPt)) {
            senkrecht.push(r.x, r.x + r.width);
            waagerecht.push(r.y, r.y + r.height);
        }
    }
    return { senkrecht, waagerecht };
}

/** Nächstgelegene Linie für Anfang, Mitte oder Ende eines Bausteins entlang einer Achse. */
function rasteAchse(start: number, laenge: number, ziele: number[], schwelle: number): { wert: number; linie: number | null } {
    let besteVerschiebung = 0;
    let besteLinie: number | null = null;
    let besterAbstand = schwelle;
    for (const kante of [start, start + laenge / 2, start + laenge]) {
        for (const ziel of ziele) {
            const abstand = Math.abs(ziel - kante);
            if (abstand < besterAbstand) {
                besterAbstand = abstand;
                besteVerschiebung = ziel - kante;
                besteLinie = ziel;
            }
        }
    }
    // Frei verschoben: ganze pt. Eingerastet: genau auf die Linie (Norm-Linien liegen nicht auf ganzen pt).
    if (besteLinie === null) return { wert: Math.round(start), linie: null };
    return { wert: Math.round((start + besteVerschiebung) * 100) / 100, linie: besteLinie };
}

/**
 * @param schwelle Fangbereich in pt – der Aufrufer rechnet ihn aus Bildschirm-
 *   pixeln und Zoom um, damit sich das Einrasten bei jedem Zoom gleich anfühlt.
 */
export function rasteEin(
    x: number, y: number, width: number, height: number,
    ziele: Fuehrungslinien, schwelle: number,
): EinrastErgebnis {
    const sx = rasteAchse(x, width, ziele.senkrecht, schwelle);
    const sy = rasteAchse(y, height, ziele.waagerecht, schwelle);
    return {
        x: sx.wert,
        y: sy.wert,
        linien: {
            senkrecht: sx.linie === null ? [] : [sx.linie],
            waagerecht: sy.linie === null ? [] : [sy.linie],
        },
    };
}

/** Eine einzelne Kante (beim Größe-Ändern) an die nächste Linie ziehen. */
export function rasteWert(wert: number, ziele: number[], schwelle: number): { wert: number; linie: number | null } {
    let beste: number | null = null;
    let besterAbstand = schwelle;
    for (const ziel of ziele) {
        const abstand = Math.abs(ziel - wert);
        if (abstand < besterAbstand) {
            besterAbstand = abstand;
            beste = ziel;
        }
    }
    return beste === null ? { wert: Math.round(wert), linie: null } : { wert: Math.round(beste * 100) / 100, linie: beste };
}
