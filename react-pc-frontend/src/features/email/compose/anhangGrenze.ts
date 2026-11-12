/**
 * Größte Nachricht, die unser Mailserver (T-Online) noch annimmt.
 *
 * <p>Bewusst mit den dezimalen 20.000.000 Bytes gerechnet und nicht mit 20 MiB:
 * Wo genau der Server die Grenze zieht, ist nicht dokumentiert, und die
 * vorsichtigere Zahl kostet uns nur ein knappes Megabyte.</p>
 */
const MAX_NACHRICHT_BYTES = 20_000_000;

/**
 * Platz für alles, was neben den Anhängen in der Nachricht steckt: Kopfzeilen,
 * HTML-Text, Signatur, das zitierte Original einer Antwort und die
 * MIME-Trennzeilen zwischen den Teilen.
 */
const RESERVE_FUER_NACHRICHT_BYTES = 1024 * 1024;

/**
 * Anhänge reisen in E-Mails Base64-kodiert: Aus je 3 Bytes werden 4 (Faktor
 * 1,333), dazu kommt alle 76 Zeichen ein Zeilenumbruch (rund 1,4 %).
 */
const BASE64_FAKTOR = 1.353;

/**
 * Obergrenze für alle Anhänge einer E-Mail zusammen – gemessen an den rohen
 * Dateigrößen, wie sie hier im Formular stehen.
 *
 * <p>Früher standen hier schlicht 20 MB. Damit meldete das Formular "passt",
 * während die kodierte Nachricht schon bei 27 MB lag und T-Online den Versand
 * ablehnte. Der Fehler fiel dem Nutzer erst beim Senden auf die Füße.</p>
 */
export const MAX_ATTACHMENT_BYTES =
    Math.floor((MAX_NACHRICHT_BYTES - RESERVE_FUER_NACHRICHT_BYTES) / BASE64_FAKTOR);

export const formatFileSize = (bytes: number): string => {
    if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

export const isImageAttachment = (file: File): boolean =>
    file.type.startsWith('image/') || /\.(jpe?g|png|gif|webp|bmp)$/i.test(file.name);

export const isPdfAttachment = (file: File): boolean =>
    file.type === 'application/pdf' || /\.pdf$/i.test(file.name);

/** Hochgeladene Datei im Formular; `sourceDokumentId` bei Dateien aus Projekt/Anfrage. */
export interface UploadedFile {
    /** Feste Kennung für die Liste (Schlüssel, Entfernen) – nie der Index. */
    id: string;
    file: File;
    sourceDokumentId?: number;
}

let laufendeNummer = 0;

/** Neuer Eintrag mit fester Kennung. */
export function neuerAnhang(file: File, sourceDokumentId?: number): UploadedFile {
    laufendeNummer += 1;
    const eintrag: UploadedFile = { id: `anhang-${laufendeNummer}`, file };
    if (sourceDokumentId !== undefined) eintrag.sourceDokumentId = sourceDokumentId;
    return eintrag;
}

/** Meldung, wenn die Anhänge zusammen zu groß werden. */
export const ANHANG_ZU_GROSS = `Anhänge dürfen zusammen höchstens ${formatFileSize(MAX_ATTACHMENT_BYTES)} groß sein.`;
