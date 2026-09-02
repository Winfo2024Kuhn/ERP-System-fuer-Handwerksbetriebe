/**
 * Verkleinert Fotos, bevor sie an eine E-Mail gehängt werden.
 *
 * <p>Handyfotos aus der Reklamations-Erfassung sind 4–8 MB groß. Drei davon
 * plus Lieferschein sprengen das Limit unseres Mailservers (T-Online nimmt
 * Nachrichten bis 20 MB an), und der Versand scheitert erst im letzten Moment.
 * Angezeigt werden die Bilder beim Empfänger ohnehin bildschirmgroß.</p>
 *
 * <p>Bewusst moderat: 2000 px lange Kante und JPEG-Qualität 82 sind auf einem
 * Monitor vom Original nicht zu unterscheiden und reichen für einen Ausdruck in
 * A5. Ein Mangel auf dem Foto bleibt damit klar erkennbar – darum geht es bei
 * einer Reklamation.</p>
 */

/** Längste Kante des komprimierten Bildes in Pixeln. */
export const MAX_KANTE_PX = 2000;

/** JPEG-Qualität: sichtbar verlustfrei, spart trotzdem den Großteil der Bytes. */
export const JPEG_QUALITAET = 0.82;

/**
 * Bilder darunter bleiben unangetastet. Neu zu kodieren kostet hier nur
 * Qualität und bringt kaum Bytes.
 */
export const MINDESTGROESSE_BYTES = 500 * 1024;

/**
 * Formate, die jeder Browser über Canvas neu kodieren kann.
 *
 * <p>GIF und SVG fehlen absichtlich: Ein GIF würde seine Animation verlieren,
 * ein SVG ist als Vektor schon klein und würde durch die Umwandlung in ein
 * Pixelbild sogar größer.</p>
 */
const KOMPRIMIERBARE_TYPEN = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp', 'image/bmp'];

const KOMPRIMIERBARE_ENDUNGEN = /\.(jpe?g|png|webp|bmp)$/i;

/**
 * Bereits von uns verkleinerte Bilder.
 *
 * <p>Ein Anhang läuft auf seinem Weg ins Formular an mehreren Stellen vorbei
 * (Reklamations-Tab, Dokument-Picker, Datei-Upload). Ohne dieses Gedächtnis
 * würde dasselbe Foto zweimal durch die JPEG-Kodierung laufen und dabei ein
 * zweites Mal Qualität verlieren. Ein `WeakSet` hält die Dateien nicht am
 * Leben – sobald der Anhang entfernt ist, verschwindet auch der Eintrag.</p>
 */
const bereitsKomprimiert = new WeakSet<File>();

/**
 * Prüft, ob sich diese Datei sinnvoll komprimieren lässt.
 *
 * <p>Der Dateityp aus dem Browser ist bei Downloads gelegentlich leer oder
 * `application/octet-stream`; dann entscheidet die Dateiendung.</p>
 */
export function istKomprimierbaresBild(datei: File): boolean {
    if (bereitsKomprimiert.has(datei)) return false;
    if (datei.size < MINDESTGROESSE_BYTES) return false;
    const typ = (datei.type || '').toLowerCase();
    if (typ) return KOMPRIMIERBARE_TYPEN.includes(typ);
    return KOMPRIMIERBARE_ENDUNGEN.test(datei.name);
}

/**
 * Rechnet die Zielmaße aus, sodass die längere Kante höchstens {@link MAX_KANTE_PX}
 * misst und das Seitenverhältnis erhalten bleibt.
 *
 * <p>Kleinere Bilder werden nicht hochskaliert – das brächte nur Bytes ohne
 * einen einzigen zusätzlichen Bildpunkt.</p>
 */
export function berechneZielmasse(
    breite: number,
    hoehe: number,
    maxKante: number = MAX_KANTE_PX,
): { breite: number; hoehe: number } {
    const laengsteKante = Math.max(breite, hoehe);
    if (laengsteKante <= maxKante) return { breite, hoehe };

    const faktor = maxKante / laengsteKante;
    return {
        breite: Math.max(1, Math.round(breite * faktor)),
        hoehe: Math.max(1, Math.round(hoehe * faktor)),
    };
}

/**
 * Tauscht die Dateiendung gegen `.jpg`, weil das Ergebnis immer ein JPEG ist.
 * Ohne das hieße ein neu kodiertes Bild weiter `foto.png` und manche
 * Mailprogramme zeigen es dann gar nicht erst an.
 */
export function jpegDateiname(name: string): string {
    const ohneEndung = name.replace(/\.[^./\\]+$/, '');
    return `${ohneEndung || 'bild'}.jpg`;
}

/** Lädt die Datei als Bitmap – mit der Drehung aus dem EXIF-Header bereits eingerechnet. */
async function dekodiere(datei: File): Promise<ImageBitmap> {
    // Handyfotos liegen in der Datei quer und tragen ihre Lage nur als Vermerk im
    // EXIF-Header. Der geht beim Neukodieren verloren, deshalb muss die Drehung
    // hier fest ins Bild gerechnet werden – sonst liegt das Foto in der Mail quer.
    return await createImageBitmap(datei, { imageOrientation: 'from-image' });
}

/**
 * Komprimiert ein Foto für den E-Mail-Versand.
 *
 * <p>Gibt die Originaldatei unverändert zurück, wenn sie kein komprimierbares
 * Bild ist, ohnehin klein genug ist, das Ergebnis nicht kleiner wäre oder die
 * Umwandlung fehlschlägt. Ein Anhang, den der Empfänger braucht, geht so
 * niemals wegen eines Kodierfehlers verloren.</p>
 */
export async function komprimiereBildFuerEmail(datei: File): Promise<File> {
    if (!istKomprimierbaresBild(datei)) return datei;

    let bitmap: ImageBitmap | null = null;
    try {
        bitmap = await dekodiere(datei);
        const { breite, hoehe } = berechneZielmasse(bitmap.width, bitmap.height);

        const canvas = document.createElement('canvas');
        canvas.width = breite;
        canvas.height = hoehe;
        const kontext = canvas.getContext('2d');
        if (!kontext) return datei;

        // Weiß hinterlegen: PNGs koennen transparent sein, JPEG kann das nicht.
        // Ohne den Hintergrund wuerden transparente Stellen sonst schwarz.
        kontext.fillStyle = '#ffffff';
        kontext.fillRect(0, 0, breite, hoehe);
        kontext.drawImage(bitmap, 0, 0, breite, hoehe);

        const blob = await new Promise<Blob | null>(aufloesen =>
            canvas.toBlob(aufloesen, 'image/jpeg', JPEG_QUALITAET));
        if (!blob || blob.size >= datei.size) return datei;

        const verkleinert = new File([blob], jpegDateiname(datei.name), {
            type: 'image/jpeg',
            lastModified: datei.lastModified,
        });
        bereitsKomprimiert.add(verkleinert);
        return verkleinert;
    } catch (fehler) {
        console.warn(`Bild "${datei.name}" konnte nicht verkleinert werden, wird im Original angehängt:`, fehler);
        return datei;
    } finally {
        bitmap?.close();
    }
}

/**
 * Komprimiert mehrere Bilder nacheinander; Nicht-Bilder gehen unverändert durch.
 *
 * <p>Bewusst nacheinander statt mit `Promise.all`: Ein 12-Megapixel-Foto belegt
 * entpackt rund 48 MB Arbeitsspeicher. Acht Fotos einer Reklamation gleichzeitig
 * zu dekodieren, hat auf einem älteren Büro-PC den Browser-Tab abgeschossen.
 * Der Zeitunterschied liegt bei wenigen Zehntelsekunden.</p>
 */
export async function komprimiereBilderFuerEmail(dateien: File[]): Promise<File[]> {
    const ergebnis: File[] = [];
    for (const datei of dateien) {
        ergebnis.push(await komprimiereBildFuerEmail(datei));
    }
    return ergebnis;
}
