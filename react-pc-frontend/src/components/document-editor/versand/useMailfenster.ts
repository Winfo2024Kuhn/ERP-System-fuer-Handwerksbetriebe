import { useRef, useState } from 'react';
import { AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN } from '../../../types';
import { notifyDokumentChanged } from '../../../lib/dokumentChannel';
import { useToast } from '../../ui/toast';
import type { VorDemSendenErgebnis } from '../../../features/email/compose/emailComposeTypen';
import { betragGeaendert, brauchtBuchungsWarnung } from '../helpers';
import { pruefeFirmendatenFuerERechnung } from '../eRechnungPruefung';
import type { PdfFormat } from '../EmailFormatDialog';
import { erzeugePdf, legePdfAb, pdfEndpunkt } from './pdfAblage';
import { ladeMailText } from './mailText';
import { legtPdfBeimMailenAb, mailPdfDateiname } from './versandRegeln';
import type { VersandKontext } from './versandTypen';

/** Dateiname aus der Antwort von pdf-speichern; unlesbare Antwort hält den Versand nicht auf. */
async function dateinameAusAntwort(res: Response): Promise<string | null> {
    try {
        return ((await res.json()) as { dateiname?: string }).dateiname ?? null;
    } catch (e) {
        console.warn('Antwort der PDF-Ablage nicht lesbar:', e);
        return null;
    }
}

export interface MailVorbereitung {
    format: PdfFormat;
    gueltigkeitTage: number | null;
    mitFreigabe: boolean;
    entwurf: boolean;
}

/**
 * Mailfenster des Dokumenteditors: PDF erzeugen und anhängen, Text aus der
 * Vorlage holen, Rechnungen erst beim Senden buchen, danach Versand vermerken.
 */
export function useMailfenster(k: VersandKontext) {
    const toast = useToast();
    const [offen, setOffen] = useState(false);
    const [anhaenge, setAnhaenge] = useState<File[]>([]);
    const [text, setText] = useState('');
    /** Rechnung: gebucht wird erst beim Klick auf „E-Mail senden“. */
    const [buchungBeimSenden, setBuchungBeimSenden] = useState(false);
    const buchungsPdfRef = useRef<Blob | null>(null);
    /** Nettobetrag laut Server beim Erzeugen dieses PDFs – Vergleich nach dem Buchen. */
    const buchungsPdfNettoRef = useRef<number | null>(null);
    // Ref statt State: muss synchron zwischen „PDF erzeugt“ und „Mail gesendet“ halten.
    // Nur ohne Entwurf darf der Versand vermerkt werden.
    const warEntwurfRef = useRef(false);

    /** @returns true, wenn das Mailfenster offen ist. Fehler zeigt ein Toast. */
    const vorbereiten = async ({ format, gueltigkeitTage, mitFreigabe, entwurf }: MailVorbereitung): Promise<boolean> => {
        try {
            if (format === 'zugferd') await pruefeFirmendatenFuerERechnung();
            if (!entwurf) k.versandDatum.uebernehmen();
            // Speichern liefert die aktuelle ID (React-State ist async).
            let dokumentId = k.dokument?.id ?? null;
            let stand = k.dokument;
            if (!k.dokument?.id || k.hasUnsavedChanges || !entwurf) {
                const gespeichert = await k.handleSave();
                if (gespeichert?.id) dokumentId = gespeichert.id;
                if (gespeichert) stand = gespeichert;
            }
            // Rechnungen werden erst beim Senden gebucht (bucheVorDemSenden). Das PDF passt
            // trotzdem: Datum und Nummer stehen nach dem Speichern fest.
            const buchtBeimSenden = !entwurf && brauchtBuchungsWarnung(k.istRechnung, k.dokument);
            const anfrage = await k.createPdfRequest(false, entwurf);
            if (!anfrage) throw new Error('Konnte PDF-Anfrage nicht erstellen');
            const pdf = await erzeugePdf(pdfEndpunkt(format), anfrage);

            buchungsPdfRef.current = buchtBeimSenden ? pdf : null;
            buchungsPdfNettoRef.current = buchtBeimSenden ? stand?.betragNetto ?? null : null;
            let pdfDateiname: string | null = null;
            if (dokumentId && legtPdfBeimMailenAb({ entwurf, buchtBeimSenden, dokumentTyp: k.dokumentTyp, istRechnung: k.istRechnung })) {
                const res = await legePdfAb(dokumentId, pdf);
                if (res?.ok) pdfDateiname = await dateinameAusAntwort(res);
            }

            const typLabel = AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === k.dokumentTyp)?.label || k.dokumentTyp;
            const dateiname = mailPdfDateiname({ typLabel, nummer: k.dokumentNummer, format, entwurf });
            const mailText = await ladeMailText(k, { dokumentId, pdfDateiname, gueltigkeitTage, mitFreigabe });

            warEntwurfRef.current = entwurf;
            setBuchungBeimSenden(buchtBeimSenden);
            setAnhaenge([new File([pdf], dateiname, { type: 'application/pdf' })]);
            setText(mailText);
            setOffen(true);
            return true;
        } catch (err) {
            console.error('Fehler beim E-Mail-Versand vorbereiten:', err);
            toast.error('E-Mail konnte nicht vorbereitet werden: ' + (err instanceof Error ? err.message : ''));
            return false;
        }
    };

    /**
     * Läuft nach dem Klick auf „E-Mail senden“: Rechnung buchen. Scheitert das, geht
     * keine Mail raus. Danach wird das PDF für die Offenen Posten abgelegt.
     */
    const bucheVorDemSenden = async (): Promise<VorDemSendenErgebnis> => {
        const gebucht = await k.bucheDokument();
        if (!gebucht) return { weiter: false };
        // Schlussrechnung: buchen() übernimmt den Betrag zum Buchungszeitpunkt. Weicht er vom
        // PDF ab (z. B. Abschlag inzwischen storniert), passt der Anhang nicht mehr.
        if (betragGeaendert(buchungsPdfNettoRef.current, gebucht.betragNetto)) {
            buchungsPdfRef.current = null;
            toast.error('Der Betrag der Rechnung hat sich beim Buchen geändert (zum Beispiel, weil inzwischen '
                + 'eine Abschlagsrechnung storniert wurde). Die Rechnung ist jetzt gebucht, die E-Mail wurde aber '
                + 'nicht gesendet: Das angehängte PDF zeigt noch den alten Betrag. Bitte das Mailfenster schließen, '
                + 'die Seite neu laden und die E-Mail neu öffnen.');
            return { weiter: false };
        }
        const pdf = buchungsPdfRef.current;
        if (pdf && gebucht.id) {
            const res = await legePdfAb(gebucht.id, pdf);
            if (res?.ok) {
                buchungsPdfRef.current = null;
            } else {
                // Kein Grund, die Mail aufzuhalten: nur der Verweis in den Offenen Posten fehlt.
                toast.warning('Die Rechnung ist gebucht, das PDF konnte aber nicht bei den Offenen Posten '
                    + 'abgelegt werden. Die E-Mail wird trotzdem gesendet.');
            }
        }
        return { weiter: true, hinweisBeiSendefehler: 'Die Rechnung ist bereits gebucht. Bitte die E-Mail erneut senden.' };
    };

    const schliessen = () => {
        setOffen(false);
        setAnhaenge([]);
        setText('');
        warEntwurfRef.current = false;
        setBuchungBeimSenden(false);
        buchungsPdfRef.current = null;
    };

    /** Nach dem Senden: Versand vermerken (nicht beim Entwurf) und Verlauf neu starten. */
    const gesendet = async () => {
        const warEntwurf = warEntwurfRef.current;
        if (!warEntwurf && k.dokument?.id) {
            try {
                const res = await fetch(`/api/ausgangs-dokumente/${k.dokument.id}/email-versendet`, { method: 'POST' });
                if (res.ok) {
                    const aktualisiert = await res.json();
                    k.setDokument(aktualisiert);
                    notifyDokumentChanged({ projektId: k.projektId, anfrageId: k.anfrageId, dokumentId: aktualisiert.id });
                }
            } catch (err) {
                console.error('Fehler beim Setzen des Versanddatums:', err);
            }
        }
        if (!warEntwurf) k.verlaufLeeren();
        schliessen();
    };

    return { offen, anhaenge, text, buchungBeimSenden, vorbereiten, bucheVorDemSenden, schliessen, gesendet };
}
