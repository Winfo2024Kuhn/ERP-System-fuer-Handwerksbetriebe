import { ClipboardList, Send } from 'lucide-react';
import { mitarbeiterPdfUrl } from './api';
import type { BerichtDefinition } from './berichtTypen';
import { grundwerte, zeitraumAus } from './berichtWerte';
import { INFO_RESTURLAUB, INFO_STUNDENKONTO, INFO_UNTERSCHRIFT } from './infoTexte';
import { PdfAbschluss } from './PdfAbschluss';
import { SteuerberaterAbschluss } from './SteuerberaterAbschluss';

/** Höchstzahl Mitarbeiter je Auswahl (so prüft es auch der Server); beim Steuerberater gilt „alle“ immer. */
export const MAX_MITARBEITER_JE_BERICHT = 50;

const zweistellig = (zahl: number) => String(zahl).padStart(2, '0');

/**
 * Alle Berichte im Report Centre. Ein neuer Bericht braucht nur einen neuen
 * Eintrag hier: Auswahlkarte, Filter und Abschluss ergeben sich daraus.
 */
export const BERICHT_DEFINITIONEN: BerichtDefinition[] = [
    {
        id: 'steuerberater',
        titel: 'An Steuerberater schicken',
        beschreibung: 'Stundenaufstellung aller Mitarbeiter für einen Monat, als PDF per E-Mail.',
        icon: Send,
        filterFelder: [
            { typ: 'monat' },
            { typ: 'mitarbeiter', min: 1, max: MAX_MITARBEITER_JE_BERICHT, alleVorauswaehlen: true, alleErlaubt: true },
            { typ: 'schalter', schluessel: 'mitResturlaub', beschriftung: 'Resturlaub zeigen', info: INFO_RESTURLAUB },
            { typ: 'schalter', schluessel: 'mitStundenkonto', beschriftung: 'Stundenkonto zeigen', info: INFO_STUNDENKONTO },
        ],
        standardwerte: heute => grundwerte(heute, { mitResturlaub: true, mitStundenkonto: true }),
        // Keine pdfUrl: Das PDF braucht die Stundenart aus der Vorschau, das baut SteuerberaterAbschluss.
        ersatzDateiname: werte => `Stundenaufstellung_${werte.jahr}-${zweistellig(werte.monat)}.pdf`,
        Abschluss: SteuerberaterAbschluss,
    },
    {
        id: 'mitarbeiter',
        titel: 'Bericht für Mitarbeiter',
        beschreibung: 'Stundenzettel mit Stundenkonto und Resturlaub, zum Ausdrucken oder Unterschreiben.',
        icon: ClipboardList,
        filterFelder: [
            { typ: 'mitarbeiter', min: 1, max: MAX_MITARBEITER_JE_BERICHT, alleVorauswaehlen: false },
            { typ: 'zeitraum' },
            { typ: 'schalter', schluessel: 'mitTagesliste', beschriftung: 'Tagesliste' },
            { typ: 'schalter', schluessel: 'mitUnterschrift', beschriftung: 'Unterschriftszeile', info: INFO_UNTERSCHRIFT },
        ],
        standardwerte: heute => grundwerte(heute, { mitTagesliste: true, mitUnterschrift: true }),
        pdfUrl: (werte, mitarbeiterIds) => mitarbeiterPdfUrl({
            mitarbeiterIds,
            ...zeitraumAus(werte),
            mitTagesliste: !!werte.schalter.mitTagesliste,
            mitUnterschrift: !!werte.schalter.mitUnterschrift,
        }),
        ersatzDateiname: (werte) => {
            const { von, bis } = zeitraumAus(werte);
            return `Zeitbericht_${von}_bis_${bis}.pdf`;
        },
        Abschluss: PdfAbschluss,
    },
];
