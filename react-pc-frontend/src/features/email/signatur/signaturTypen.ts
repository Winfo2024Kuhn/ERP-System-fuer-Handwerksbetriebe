/**
 * Typen und Grundwerte für das Signatur-Studio.
 *
 * `SignaturDaten` ist der Formularzustand des Studios. Er wird als JSON-String
 * im Feld `vorlageDaten` gespeichert (das Backend speichert ihn unverändert),
 * damit eine Signatur später wieder im Formular bearbeitet werden kann.
 */

export const SIGNATUR_DATEN_VERSION = 1;

export type VorlagenId = 'klassisch-foto' | 'modern-balken' | 'kompakt' | 'banner' | 'schlicht';

export type Schriftart = 'Arial' | 'Helvetica' | 'Georgia' | 'Verdana' | 'Tahoma' | 'Trebuchet MS';

export interface SozialeNetzwerke {
    facebook: string;
    instagram: string;
    linkedin: string;
    whatsapp: string;
}

export interface SignaturDaten {
    version: number;
    vorlage: VorlagenId;
    name: string;
    position: string;
    abteilung: string;
    firma: string;
    telefon: string;
    mobil: string;
    fax: string;
    email: string;
    website: string;
    adresse: string;
    slogan: string;
    /** Kleingedrucktes, z. B. Handelsregister und Geschäftsführer. */
    zusatz: string;
    fotoUrl: string;
    logoUrl: string;
    bannerUrl: string;
    bannerLink: string;
    social: SozialeNetzwerke;
    akzentfarbe: string;
    schriftart: Schriftart;
}

/** Signatur, wie das Backend sie liefert (`SignaturDto`). */
export interface SignaturDto {
    id: number;
    name: string;
    html: string;
    vorlageDaten: string | null;
    defaultSignature: boolean;
    isSystemDefault: boolean;
    besitzerId: number | null;
    besitzerName: string | null;
    eigene: boolean;
    createdAt: string;
    updatedAt: string;
    images?: { id: number; cid: string; originalFilename: string; contentType: string; sizeBytes: number }[];
}

/** Body für `POST /api/email/signatures`. */
export interface SignaturSpeichernBody {
    id: number | null;
    name: string;
    html: string;
    vorlageDaten: string | null;
    besitzerId?: number | null;
    /** Nur Admins: `besitzerId` übernehmen (sonst bleibt der Besitzer, wie er ist). */
    besitzerFestlegen?: boolean;
    alsStandard: boolean;
}

export const STANDARD_AKZENTFARBE = '#500010';

export const SCHRIFTARTEN: { wert: Schriftart; css: string }[] = [
    { wert: 'Arial', css: 'Arial, Helvetica, sans-serif' },
    { wert: 'Helvetica', css: 'Helvetica, Arial, sans-serif' },
    { wert: 'Georgia', css: "Georgia, 'Times New Roman', serif" },
    { wert: 'Verdana', css: 'Verdana, Geneva, sans-serif' },
    { wert: 'Tahoma', css: 'Tahoma, Geneva, sans-serif' },
    { wert: 'Trebuchet MS', css: "'Trebuchet MS', Helvetica, sans-serif" },
];

export interface VorlagenInfo {
    id: VorlagenId;
    titel: string;
    beschreibung: string;
    /** Welche Bilder die Vorlage zeigt – nur diese Felder bietet das Studio an. */
    bilder: { foto: boolean; logo: boolean; banner: boolean };
}

export const VORLAGEN: VorlagenInfo[] = [
    { id: 'klassisch-foto', titel: 'Klassisch mit Foto', beschreibung: 'Foto und Logo links, Kontakt rechts', bilder: { foto: true, logo: true, banner: false } },
    { id: 'modern-balken', titel: 'Modern mit Farbbalken', beschreibung: 'Farbiger Balken, rundes Foto', bilder: { foto: true, logo: true, banner: false } },
    { id: 'kompakt', titel: 'Kompakt', beschreibung: 'Wenige Zeilen, wenig Platz', bilder: { foto: false, logo: true, banner: false } },
    { id: 'banner', titel: 'Mit Banner', beschreibung: 'Werbebanner unter dem Kontakt', bilder: { foto: false, logo: true, banner: true } },
    { id: 'schlicht', titel: 'Schlicht', beschreibung: 'Nur Text, ohne Schnickschnack', bilder: { foto: false, logo: true, banner: false } },
];

const VORLAGEN_IDS = new Set<string>(VORLAGEN.map((v) => v.id));
const SCHRIFTART_WERTE = new Set<string>(SCHRIFTARTEN.map((s) => s.wert));

export function vorlagenInfo(id: VorlagenId): VorlagenInfo {
    return VORLAGEN.find((v) => v.id === id) ?? VORLAGEN[0];
}

export function schriftartCss(schriftart: Schriftart): string {
    return SCHRIFTARTEN.find((s) => s.wert === schriftart)?.css ?? SCHRIFTARTEN[0].css;
}

export function leereSignaturDaten(): SignaturDaten {
    return {
        version: SIGNATUR_DATEN_VERSION,
        vorlage: 'klassisch-foto',
        name: '',
        position: '',
        abteilung: '',
        firma: '',
        telefon: '',
        mobil: '',
        fax: '',
        email: '',
        website: '',
        adresse: '',
        slogan: '',
        zusatz: '',
        fotoUrl: '',
        logoUrl: '',
        bannerUrl: '',
        bannerLink: '',
        social: { facebook: '', instagram: '', linkedin: '', whatsapp: '' },
        akzentfarbe: STANDARD_AKZENTFARBE,
        schriftart: 'Arial',
    };
}

type TextFeld = Exclude<keyof SignaturDaten, 'version' | 'vorlage' | 'social' | 'schriftart'>;

const TEXT_FELDER: TextFeld[] = [
    'name', 'position', 'abteilung', 'firma', 'telefon', 'mobil', 'fax', 'email', 'website', 'adresse',
    'slogan', 'zusatz', 'fotoUrl', 'logoUrl', 'bannerUrl', 'bannerLink', 'akzentfarbe',
];

const textOder = (wert: unknown, standard: string): string => (typeof wert === 'string' ? wert : standard);

/**
 * Liest gespeicherte `vorlageDaten` defensiv ein. Unbekannte oder kaputte
 * Felder fallen auf den Grundwert zurück; ist der Text gar kein brauchbares
 * JSON-Objekt, kommt `null` zurück (dann öffnet das Studio im HTML-Modus).
 */
export function leseVorlageDaten(json: string | null | undefined): SignaturDaten | null {
    if (!json) return null;
    let roh: unknown;
    try {
        roh = JSON.parse(json);
    } catch {
        return null;
    }
    if (!roh || typeof roh !== 'object' || Array.isArray(roh)) return null;
    const quelle = roh as Record<string, unknown>;
    const ergebnis = leereSignaturDaten();
    for (const feld of TEXT_FELDER) {
        ergebnis[feld] = textOder(quelle[feld], ergebnis[feld]);
    }
    if (typeof quelle.vorlage === 'string' && VORLAGEN_IDS.has(quelle.vorlage)) {
        ergebnis.vorlage = quelle.vorlage as VorlagenId;
    }
    if (typeof quelle.schriftart === 'string' && SCHRIFTART_WERTE.has(quelle.schriftart)) {
        ergebnis.schriftart = quelle.schriftart as Schriftart;
    }
    const social = quelle.social;
    if (social && typeof social === 'object' && !Array.isArray(social)) {
        const s = social as Record<string, unknown>;
        ergebnis.social = {
            facebook: textOder(s.facebook, ''),
            instagram: textOder(s.instagram, ''),
            linkedin: textOder(s.linkedin, ''),
            whatsapp: textOder(s.whatsapp, ''),
        };
    }
    return ergebnis;
}
