export interface Zuschnitt {
    /** Alle Werte in Pixeln des Originalbildes. */
    x: number;
    y: number;
    breite: number;
    hoehe: number;
}

export interface Bildbearbeitung {
    /** null bedeutet: das ganze Bild. */
    zuschnitt: Zuschnitt | null;
    drehung: 0 | 90 | 180 | 270;
    spiegelnX: boolean;
    spiegelnY: boolean;
    /** 100 ist unveraendert. */
    helligkeit: number;
    /** 100 ist unveraendert. */
    kontrast: number;
}

export const STANDARD_BEARBEITUNG: Bildbearbeitung = {
    zuschnitt: null,
    drehung: 0,
    spiegelnX: false,
    spiegelnY: false,
    helligkeit: 100,
    kontrast: 100,
};

/** Fuer die Website. Deren imageUpload.ts verkleinert ohnehin auf 1600 px. */
export const MAX_BREITE_UPLOAD = 1600;

/** Fuer den KI-Kontext. Haelt die Anfrage klein, ohne dass die KI weniger erkennt. */
export const MAX_BREITE_KI = 1024;

interface Masse {
    breite: number;
    hoehe: number;
}

/**
 * Rechnet aus, wie gross das fertige Bild wird.
 *
 * Reihenfolge: erst zuschneiden, dann verkleinern, dann drehen. Der
 * Verkleinerungsfaktor bezieht sich immer auf die Breite vor der Drehung,
 * damit er unabhaengig davon ist, ob anschliessend gedreht wird. Bei 90 und
 * 270 Grad tauschen Breite und Hoehe danach. Kleine Bilder werden nie
 * vergroessert, das braechte nur Unschaerfe.
 */
export function berechneAusgabeMasse(
    quelle: Masse,
    bearbeitung: Bildbearbeitung,
    maxBreite: number,
): Masse {
    const zugeschnitten = bearbeitung.zuschnitt
        ? { breite: bearbeitung.zuschnitt.breite, hoehe: bearbeitung.zuschnitt.hoehe }
        : quelle;

    const verkleinert = zugeschnitten.breite > maxBreite
        ? {
            breite: maxBreite,
            hoehe: (zugeschnitten.hoehe * maxBreite) / zugeschnitten.breite,
        }
        : zugeschnitten;

    const mussTauschen = bearbeitung.drehung === 90 || bearbeitung.drehung === 270;
    return mussTauschen
        ? { breite: Math.round(verkleinert.hoehe), hoehe: Math.round(verkleinert.breite) }
        : { breite: Math.round(verkleinert.breite), hoehe: Math.round(verkleinert.hoehe) };
}

/**
 * Zeichnet das bearbeitete Bild in den uebergebenen Kontext.
 *
 * Es wird immer vom Originalbild aus gerechnet, nie vom vorherigen Ergebnis.
 * Nur so bleibt mehrfaches Bearbeiten verlustfrei: wer dreimal um 90 Grad
 * dreht und wieder zuruecknimmt, bekommt exakt das Original.
 */
export function zeichne(
    ctx: CanvasRenderingContext2D,
    bild: HTMLImageElement,
    bearbeitung: Bildbearbeitung,
    maxBreite: number,
): void {
    const quelle = { breite: bild.width, hoehe: bild.height };
    const ziel = berechneAusgabeMasse(quelle, bearbeitung, maxBreite);

    ctx.canvas.width = ziel.breite;
    ctx.canvas.height = ziel.hoehe;

    const ausschnitt = bearbeitung.zuschnitt ?? { x: 0, y: 0, breite: quelle.breite, hoehe: quelle.hoehe };

    // Bei 90 und 270 Grad ist die gezeichnete Flaeche quer zur Leinwand.
    const querFormat = bearbeitung.drehung === 90 || bearbeitung.drehung === 270;
    const flaecheBreite = querFormat ? ziel.hoehe : ziel.breite;
    const flaecheHoehe = querFormat ? ziel.breite : ziel.hoehe;

    ctx.save();
    ctx.clearRect(0, 0, ziel.breite, ziel.hoehe);
    ctx.filter = `brightness(${bearbeitung.helligkeit}%) contrast(${bearbeitung.kontrast}%)`;

    // Ursprung in die Mitte legen, damit Drehen und Spiegeln um die Mitte laufen.
    ctx.translate(ziel.breite / 2, ziel.hoehe / 2);
    if (bearbeitung.drehung !== 0) {
        ctx.rotate((bearbeitung.drehung * Math.PI) / 180);
    }
    if (bearbeitung.spiegelnX) ctx.scale(-1, 1);
    if (bearbeitung.spiegelnY) ctx.scale(1, -1);

    ctx.drawImage(
        bild,
        ausschnitt.x, ausschnitt.y, ausschnitt.breite, ausschnitt.hoehe,
        -flaecheBreite / 2, -flaecheHoehe / 2, flaecheBreite, flaecheHoehe,
    );
    ctx.restore();
}
