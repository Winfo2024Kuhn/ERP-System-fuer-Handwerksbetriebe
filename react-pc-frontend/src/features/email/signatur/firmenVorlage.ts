import { leereSignaturDaten, leseVorlageDaten, type SignaturDaten, type SignaturDto } from './signaturTypen';

/**
 * Firmen-Vorlagen: Der Admin legt das Aussehen und die Firmenangaben fest,
 * Mitarbeiter ohne Admin-Rechte tragen nur ihre persönlichen Angaben ein.
 *
 * Gesperrt (kommt immer aus der Firmen-Vorlage): Aufbau, Farbe, Schrift,
 * Logo, Banner, Firmenangaben, Slogan, Kleingedrucktes und soziale Netzwerke.
 * Frei: Name, Position, Abteilung, Telefon (Durchwahl), Mobil, E-Mail, Foto.
 */
export const GESPERRTE_FELDER = [
    'vorlage', 'akzentfarbe', 'schriftart', 'logoUrl', 'bannerUrl', 'bannerLink',
    'firma', 'fax', 'website', 'adresse', 'slogan', 'zusatz', 'social',
] as const satisfies readonly (keyof SignaturDaten)[];

export type GesperrtesFeld = (typeof GESPERRTE_FELDER)[number];

const GESPERRT = new Set<keyof SignaturDaten>(GESPERRTE_FELDER);

export function istGesperrtesFeld(feld: keyof SignaturDaten): boolean {
    return GESPERRT.has(feld);
}

/** Formulardaten einer Firmen-Vorlage; ohne lesbare Formulardaten gelten die Grundwerte. */
export function firmenVorlagenDaten(vorlage: Pick<SignaturDto, 'vorlageDaten'>): SignaturDaten {
    return leseVorlageDaten(vorlage.vorlageDaten) ?? leereSignaturDaten();
}

/** Übernimmt nur die gesperrten Felder aus der Firmen-Vorlage – persönliche Angaben bleiben, wie sie sind. */
export function mitFirmenVorlage(daten: SignaturDaten, vorlage: SignaturDaten): SignaturDaten {
    return {
        ...daten,
        vorlage: vorlage.vorlage,
        akzentfarbe: vorlage.akzentfarbe,
        schriftart: vorlage.schriftart,
        logoUrl: vorlage.logoUrl,
        bannerUrl: vorlage.bannerUrl,
        bannerLink: vorlage.bannerLink,
        firma: vorlage.firma,
        fax: vorlage.fax,
        website: vorlage.website,
        adresse: vorlage.adresse,
        slogan: vorlage.slogan,
        zusatz: vorlage.zusatz,
        social: { ...vorlage.social },
    };
}

/** Bestehende Angaben auf eine Firmen-Vorlage umstellen. Ein leeres Telefon wird mit der Nummer aus der Vorlage vorbelegt. */
export function aufFirmenVorlageUmstellen(daten: SignaturDaten, vorlage: SignaturDaten): SignaturDaten {
    return mitFirmenVorlage({ ...daten, telefon: daten.telefon.trim() ? daten.telefon : vorlage.telefon }, vorlage);
}

/** Neue Signatur aus einer Firmen-Vorlage: nur der eigene Name und die Telefonnummer der Vorlage sind vorbelegt. */
export function neuAusFirmenVorlage(vorlage: SignaturDaten, name: string): SignaturDaten {
    return aufFirmenVorlageUmstellen({ ...leereSignaturDaten(), name }, vorlage);
}

/**
 * Wechsel zu einer anderen Firmen-Vorlage. Die Telefonnummer wandert mit,
 * solange der Benutzer sie nicht selbst geändert hat.
 */
export function wechsleFirmenVorlage(daten: SignaturDaten, alt: SignaturDaten, neu: SignaturDaten): SignaturDaten {
    const unveraendert = !daten.telefon.trim() || daten.telefon === alt.telefon;
    return mitFirmenVorlage({ ...daten, telefon: unveraendert ? neu.telefon : daten.telefon }, neu);
}
