import { heuteIso } from '../../../lib/datum';
import { DEFAULT_ZAHLUNGSZIEL_TAGE } from '../helpers';
import { faelligkeitIso } from './versandRegeln';
import type { VersandKontext } from './versandTypen';

type MailTextKontext = Pick<VersandKontext,
    'dokumentTyp' | 'dokumentNummer' | 'betreff' | 'kontextDaten' | 'nettosumme' | 'anfrageId' | 'leseDatum'>;

interface MailTextAngaben {
    dokumentId: number | null;
    pdfDateiname: string | null;
    gueltigkeitTage: number | null;
    mitFreigabe: boolean;
}

/**
 * Lässt den E-Mail-Text aus der Vorlage des Backends erzeugen. Rechnungsdatum
 * und Fälligkeit kommen aus dem Datum oben auf dem Dokument. Klappt das nicht,
 * bleibt der Text leer und der Anwender schreibt selbst.
 */
export async function ladeMailText(k: MailTextKontext, angaben: MailTextAngaben): Promise<string> {
    const rechnungsdatum = k.leseDatum() || heuteIso();
    const zahlungszielTage = k.kontextDaten.zahlungsziel ?? DEFAULT_ZAHLUNGSZIEL_TAGE;
    try {
        const res = await fetch('/api/email/template', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                dokumentTyp: k.dokumentTyp,
                anrede: k.kontextDaten.anrede || null,
                kundenName: k.kontextDaten.kundenName || '',
                ansprechpartner: k.kontextDaten.ansprechpartner || '',
                bauvorhaben: k.kontextDaten.projektBauvorhaben || k.betreff || '',
                projektnummer: k.kontextDaten.projektnummer || '',
                dokumentnummer: k.dokumentNummer || '',
                rechnungsdatum,
                faelligkeitsdatum: faelligkeitIso(rechnungsdatum, zahlungszielTage),
                betrag: k.nettosumme
                    ? `${(k.nettosumme * 1.19).toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} €`
                    : undefined,
                dokumentId: angaben.dokumentId,
                isAnfrage: !!k.anfrageId,
                recipient: k.kontextDaten.kundenEmails?.[0] ?? null,
                pdfDateiname: angaben.pdfDateiname,
                gueltigkeitTage: angaben.gueltigkeitTage,
                mitFreigabe: angaben.mitFreigabe,
            }),
        });
        if (!res.ok) return '';
        const vorlage = await res.json() as { body?: string };
        return vorlage.body || '';
    } catch (e) {
        console.warn('E-Mail-Template konnte nicht geladen werden:', e);
        return '';
    }
}
