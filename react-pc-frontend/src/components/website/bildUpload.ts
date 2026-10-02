import type { GewaehltesBild } from './schritte/SchrittBilder';
import { STANDARD_BEARBEITUNG } from './bildbearbeitung';

/** Formate, die der Browser sicher darstellt und die Website verarbeiten kann. */
export const ERLAUBTE_BILDTYPEN = ['image/jpeg', 'image/png', 'image/webp'];
export const MAX_DATEIGROESSE_MB = 15;
const MAX_DATEIGROESSE = MAX_DATEIGROESSE_MB * 1024 * 1024;

export interface PruefErgebnis {
    gueltig: File[];
    abgelehnt: string[];
}

/**
 * Trennt brauchbare Bilder von allem anderen und nennt zu jeder abgelehnten
 * Datei den Grund in Handwerker-Sprache.
 */
export function pruefeBilddateien(dateien: File[]): PruefErgebnis {
    const gueltig: File[] = [];
    const abgelehnt: string[] = [];
    for (const datei of dateien) {
        if (!ERLAUBTE_BILDTYPEN.includes(datei.type)) {
            abgelehnt.push(`„${datei.name}“ ist kein Bild im Format JPG, PNG oder WebP.`);
        } else if (datei.size > MAX_DATEIGROESSE) {
            abgelehnt.push(`„${datei.name}“ ist größer als ${MAX_DATEIGROESSE_MB} MB.`);
        } else {
            gueltig.push(datei);
        }
    }
    return { gueltig, abgelehnt };
}

/** Macht aus einer Datei von der Festplatte einen auswählbaren Eintrag. */
export function eintragAusDatei(datei: File): GewaehltesBild {
    const url = URL.createObjectURL(datei);
    return {
        bild: {
            schluessel: `upload-${datei.name}-${datei.size}-${datei.lastModified}-${url.slice(-12)}`,
            quelle: 'upload',
            url,
            thumbnailUrl: url,
            originalDateiname: datei.name,
            datum: null,
            hinweis: null,
        },
        bearbeitung: STANDARD_BEARBEITUNG,
    };
}

/** Gibt die Speicherplätze der hochgeladenen Bilder frei, wenn die Auswahl verworfen wird. */
export function gibUploadsFrei(auswahl: GewaehltesBild[]) {
    auswahl.filter(a => a.bild.quelle === 'upload').forEach(a => URL.revokeObjectURL(a.bild.url));
}
