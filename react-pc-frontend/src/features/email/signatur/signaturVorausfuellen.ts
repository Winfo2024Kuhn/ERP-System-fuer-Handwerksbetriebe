import type { FirmenDaten } from './signaturApi';
import { istHexFarbe, sichereFarbe } from './signaturSicherheit';
import { STANDARD_AKZENTFARBE, type SignaturDaten } from './signaturTypen';

/** Füllt nur leere Felder mit den Firmendaten – Eingaben des Benutzers bleiben unangetastet. */
export function mitFirmenDaten(daten: SignaturDaten, firma: FirmenDaten): SignaturDaten {
    const ergaenzt = { ...daten };
    const fuelle = (feld: 'firma' | 'telefon' | 'fax' | 'email' | 'website' | 'adresse', wert: string | null) => {
        if (wert && !ergaenzt[feld].trim()) ergaenzt[feld] = wert;
    };
    fuelle('firma', firma.firmenname);
    fuelle('telefon', firma.telefon);
    fuelle('fax', firma.fax);
    fuelle('email', firma.email);
    fuelle('website', firma.website);
    const ortZeile = [firma.plz, firma.ort].filter(Boolean).join(' ');
    fuelle('adresse', [firma.strasse, ortZeile].filter(Boolean).join(', ') || null);
    if (firma.firmenfarbe && istHexFarbe(firma.firmenfarbe) && ergaenzt.akzentfarbe === STANDARD_AKZENTFARBE) {
        ergaenzt.akzentfarbe = sichereFarbe(firma.firmenfarbe);
    }
    return ergaenzt;
}
