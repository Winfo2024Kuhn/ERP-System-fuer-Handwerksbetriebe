import { dataUriBytes, istErlaubtesBildDataUri } from './signaturSicherheit';

/**
 * Hochgeladene Bilder werden direkt im Browser verkleinert und als
 * Base64-Data-URI in die Signatur eingebettet – es geht nichts extra an den
 * Server. Damit die Signatur klein bleibt (jede E-Mail trägt sie mit),
 * rechnen wir auf eine feste Höchstbreite und ein Ziel von 300 KB herunter.
 */

export type BildArt = 'foto' | 'logo' | 'banner';

/** Höchstbreite in Pixeln (doppelte Anzeigebreite, damit es auf scharfen Bildschirmen gut aussieht). */
export const BILD_MAX_BREITE: Record<BildArt, number> = { foto: 300, logo: 300, banner: 600 };

export const BILD_ZIEL_BYTES = 300 * 1024;
export const BILD_MAX_EINGABE_BYTES = 15 * 1024 * 1024;
export const ERLAUBTE_BILD_TYPEN = ['image/png', 'image/jpeg', 'image/gif', 'image/webp'];

const MIN_BREITE = 60;

export interface VerkleinertesBild {
    dataUri: string;
    bytes: number;
    breite: number;
    hoehe: number;
}

/** Prüft die Datei vor dem Verkleinern. Gibt eine Fehlermeldung oder `null` zurück. */
export function pruefeBildDatei(datei: Pick<File, 'type' | 'size'>): string | null {
    if (!ERLAUBTE_BILD_TYPEN.includes(datei.type)) {
        return 'Bitte ein Bild im Format PNG, JPG, GIF oder WebP wählen.';
    }
    if (datei.size <= 0) return 'Die Bilddatei ist leer.';
    if (datei.size > BILD_MAX_EINGABE_BYTES) {
        return 'Das Bild ist zu groß (höchstens 15 MB). Bitte ein kleineres Bild wählen.';
    }
    return null;
}

/** Zielgröße: höchstens `maxBreite` breit, Seitenverhältnis bleibt, nie größer als das Original. */
export function zielGroesse(breite: number, hoehe: number, maxBreite: number): { breite: number; hoehe: number } {
    if (breite <= 0 || hoehe <= 0) return { breite: 0, hoehe: 0 };
    const neueBreite = Math.min(breite, maxBreite);
    return { breite: Math.round(neueBreite), hoehe: Math.max(1, Math.round((hoehe * neueBreite) / breite)) };
}

function ladeBild(datei: File): Promise<HTMLImageElement> {
    return new Promise((resolve, reject) => {
        const url = URL.createObjectURL(datei);
        const bild = new Image();
        bild.onload = () => {
            URL.revokeObjectURL(url);
            resolve(bild);
        };
        bild.onerror = () => {
            URL.revokeObjectURL(url);
            reject(new Error('Das Bild konnte nicht gelesen werden. Ist die Datei beschädigt?'));
        };
        bild.src = url;
    });
}

function zeichne(bild: HTMLImageElement, breite: number, hoehe: number, weisserHintergrund: boolean) {
    const canvas = document.createElement('canvas');
    canvas.width = breite;
    canvas.height = hoehe;
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('Ihr Browser kann das Bild nicht verkleinern.');
    if (weisserHintergrund) {
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, breite, hoehe);
    }
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(bild, 0, 0, breite, hoehe);
    return { canvas, ctx };
}

function hatTransparenz(ctx: CanvasRenderingContext2D, breite: number, hoehe: number): boolean {
    const pixel = ctx.getImageData(0, 0, breite, hoehe).data;
    for (let i = 3; i < pixel.length; i += 4) {
        if (pixel[i] < 255) return true;
    }
    return false;
}

/**
 * Verkleinert ein Bild auf die Höchstbreite der Bildart. Bilder mit
 * durchsichtigen Stellen (z. B. Logos) bleiben PNG, alles andere wird JPEG.
 * Ist das Ergebnis noch über 300 KB, wird die Qualität und notfalls die
 * Größe schrittweise reduziert.
 */
export async function bildVerkleinern(datei: File, art: BildArt): Promise<VerkleinertesBild> {
    const fehler = pruefeBildDatei(datei);
    if (fehler) throw new Error(fehler);

    const bild = await ladeBild(datei);
    let { breite, hoehe } = zielGroesse(bild.naturalWidth || bild.width, bild.naturalHeight || bild.height, BILD_MAX_BREITE[art]);
    if (breite === 0) throw new Error('Das Bild konnte nicht gelesen werden. Ist die Datei beschädigt?');

    const pruefung = zeichne(bild, breite, hoehe, false);
    const transparent = datei.type !== 'image/jpeg' && hatTransparenz(pruefung.ctx, breite, hoehe);

    for (;;) {
        // JPEG kennt keine Transparenz – dort kommt ein weißer Hintergrund darunter.
        const { canvas } = zeichne(bild, breite, hoehe, !transparent);
        if (transparent) {
            const png = canvas.toDataURL('image/png');
            if (dataUriBytes(png) <= BILD_ZIEL_BYTES || breite <= MIN_BREITE) return fertig(png, breite, hoehe);
        } else {
            for (const qualitaet of [0.9, 0.8, 0.7, 0.6]) {
                const jpeg = canvas.toDataURL('image/jpeg', qualitaet);
                if (dataUriBytes(jpeg) <= BILD_ZIEL_BYTES) return fertig(jpeg, breite, hoehe);
            }
            if (breite <= MIN_BREITE) return fertig(canvas.toDataURL('image/jpeg', 0.6), breite, hoehe);
        }
        ({ breite, hoehe } = zielGroesse(breite, hoehe, Math.max(MIN_BREITE, Math.floor(breite * 0.8))));
    }
}

function fertig(dataUri: string, breite: number, hoehe: number): VerkleinertesBild {
    if (!istErlaubtesBildDataUri(dataUri)) {
        throw new Error('Das Bild ist auch verkleinert noch zu groß. Bitte ein kleineres Bild wählen.');
    }
    return { dataUri, bytes: dataUriBytes(dataUri), breite, hoehe };
}

/** „84 KB“ / „1,2 MB“ für die Anzeige. */
export function formatBytes(bytes: number): string {
    if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`;
}
