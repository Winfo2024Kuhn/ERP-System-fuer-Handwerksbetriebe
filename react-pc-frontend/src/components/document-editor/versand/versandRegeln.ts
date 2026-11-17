import type { AusgangsGeschaeftsDokumentTyp } from '../../../types';
import { isoDatum, parseIsoDatum } from '../../../lib/datum';
import type { PdfFormat } from '../EmailFormatDialog';

/** Reine Regeln für Drucken, Herunterladen und E-Mail-Versand. */

/** Datum plus Zahlungsziel als `YYYY-MM-DD` in lokaler Zeit (für {{FAELLIGKEITSDATUM}}). */
export function faelligkeitIso(datumIso: string, zahlungszielTage: number): string {
    const datum = parseIsoDatum(datumIso);
    if (!datum) return datumIso;
    datum.setDate(datum.getDate() + zahlungszielTage);
    return isoDatum(datum);
}

export function exportDateiname(dokumentTyp: AusgangsGeschaeftsDokumentTyp, nummer: string, format: PdfFormat): string {
    const prefix = format === 'zugferd' ? 'zugferd_' : '';
    return `${prefix}${dokumentTyp.toLowerCase()}_${nummer || 'neu'}.pdf`;
}

export function mailPdfDateiname({ typLabel, nummer, format, entwurf }: {
    typLabel: string; nummer: string; format: PdfFormat; entwurf: boolean;
}): string {
    const entwurfPrefix = entwurf ? 'ENTWURF_' : '';
    const formatPrefix = format === 'zugferd' ? 'ZUGFeRD_' : '';
    return `${entwurfPrefix}${formatPrefix}${typLabel}_${nummer || 'Entwurf'}.pdf`;
}

const MIT_FREIGABE_LINK: AusgangsGeschaeftsDokumentTyp[] = ['ANGEBOT', 'NACHTRAGSANGEBOT', 'AUFTRAGSBESTAETIGUNG'];

/**
 * Wird das Mail-PDF schon beim Öffnen des Mailfensters beim Dokument abgelegt?
 * Angebote/AB immer (Freigabe-Link zeigt darauf), Rechnungen nur, wenn sie nicht
 * erst beim Senden gebucht werden. Entwürfe nie: ein Wasserzeichen-PDF darf nicht
 * mit dem endgültigen verwechselt werden.
 */
export function legtPdfBeimMailenAb({ entwurf, buchtBeimSenden, dokumentTyp, istRechnung }: {
    entwurf: boolean; buchtBeimSenden: boolean; dokumentTyp: AusgangsGeschaeftsDokumentTyp; istRechnung: boolean;
}): boolean {
    return !entwurf && !buchtBeimSenden && (MIT_FREIGABE_LINK.includes(dokumentTyp) || istRechnung);
}
