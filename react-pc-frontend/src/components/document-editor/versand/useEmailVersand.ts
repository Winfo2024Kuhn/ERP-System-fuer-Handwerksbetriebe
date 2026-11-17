import { useState } from 'react';
import { useToast } from '../../ui/toast';
import { useConfirm } from '../../ui/confirm-dialog';
import { abloesungsFrage, brauchtAnnahmeLinkAbfrage, istAngebotsTyp, type AbzuloesendeFreigabe } from '../helpers';
import { fehlermeldungAusAntwort } from '../eRechnungPruefung';
import type { PdfFormat } from '../EmailFormatDialog';
import type { MailVorbereitung } from './useMailfenster';
import type { VersandKontext } from './versandTypen';

/**
 * Dialogfolge vor dem Mailfenster: Formatwahl (mit Datum), bei Angeboten die
 * Ablösung alter Online-Links, „online annehmen?“ und die Gültigkeit.
 * `entwurf`: PDF mit Wasserzeichen, nichts gebucht, Datum bleibt.
 */
export function useEmailVersand(k: VersandKontext, vorbereiten: (v: MailVorbereitung) => Promise<boolean>) {
    const toast = useToast();
    const confirm = useConfirm();
    const [entwurf, setEntwurf] = useState(false);
    const [formatOffen, setFormatOffen] = useState(false);
    const [annahmeOffen, setAnnahmeOffen] = useState(false);
    const [gueltigkeitOffen, setGueltigkeitOffen] = useState(false);
    const [wartendesFormat, setWartendesFormat] = useState<PdfFormat | null>(null);
    const [gueltigkeitTage, setGueltigkeitTage] = useState<number | null>(null);
    const [laedt, setLaedt] = useState(false);
    /** Angebot: bestätigt, dass offene Links anderer Angebote beim Senden ungültig werden. */
    const [abloesenFuerDokumentId, setAbloesenFuerDokumentId] = useState<number | null>(null);

    const starte = (alsEntwurf: boolean) => {
        setEntwurf(alsEntwurf);
        k.versandDatum.beginne(alsEntwurf);
        setFormatOffen(true);
    };

    const oeffneMail = async (format: PdfFormat, tage: number | null, mitFreigabe = true) => {
        setLaedt(true);
        try {
            if (await vorbereiten({ format, gueltigkeitTage: tage, mitFreigabe, entwurf })) setFormatOffen(false);
        } finally {
            setLaedt(false);
        }
    };

    /** @returns false, wenn der Versand abgebrochen werden soll. */
    const pruefeAbloesungAlterLinks = async (): Promise<boolean> => {
        setAbloesenFuerDokumentId(null);
        // Auch Nachtragsangebote: ihr eigener älterer Link wird beim erneuten Versand abgelöst.
        if (!istAngebotsTyp(k.dokumentTyp) || entwurf) return true;
        const dokumentId = k.dokument?.id ?? (await k.handleSave())?.id ?? null;
        if (!dokumentId) return true;
        let offeneLinks: AbzuloesendeFreigabe[];
        try {
            const res = await fetch(`/api/ausgangs-dokumente/${dokumentId}/abzuloesende-freigaben`);
            if (!res.ok) throw new Error(await fehlermeldungAusAntwort(res, 'Prüfung fehlgeschlagen'));
            offeneLinks = await res.json() as AbzuloesendeFreigabe[];
        } catch (err) {
            console.error('Offene Online-Links konnten nicht geprüft werden:', err);
            toast.error('Offene Online-Links anderer Angebote konnten nicht geprüft werden. Bitte erneut versuchen.');
            return false;
        }
        if (offeneLinks.length === 0) return true;
        // Formatauswahl schließen, sonst liegt sie unter der Rückfrage und überdeckt sie.
        setFormatOffen(false);
        const fortfahren = await confirm({
            title: 'Alter Online-Link wird ungültig',
            message: abloesungsFrage(offeneLinks, k.dokumentTyp === 'NACHTRAGSANGEBOT' ? 'Nachtragsangebot' : 'Angebot'),
            confirmLabel: 'Trotzdem fortfahren',
            variant: 'warning',
        });
        if (!fortfahren) return false;
        setAbloesenFuerDokumentId(dokumentId);
        return true;
    };

    const formatGewaehlt = async (format: PdfFormat) => {
        setLaedt(true);
        let weiter: boolean;
        try {
            weiter = await pruefeAbloesungAlterLinks();
        } finally {
            setLaedt(false);
        }
        if (!weiter) {
            setFormatOffen(false);
            setEntwurf(false);
            return;
        }
        // Angebote: vorher fragen, ob der Kunde online annehmen können soll (nur wenn ein Link entstehen kann).
        if (brauchtAnnahmeLinkAbfrage(k.dokumentTyp, entwurf, k.dokument?.digitalAngenommen)) {
            setWartendesFormat(format);
            setFormatOffen(false);
            setAnnahmeOffen(true);
            return;
        }
        void oeffneMail(format, null);
    };

    const annahmeGewaehlt = (mitOnlineAnnahme: boolean) => {
        setAnnahmeOffen(false);
        if (mitOnlineAnnahme) {
            setGueltigkeitOffen(true);
            return;
        }
        // Ohne Link braucht es keine Gültigkeit: Sie gilt nur für den Annahme-Link.
        setGueltigkeitTage(null);
        if (wartendesFormat) void oeffneMail(wartendesFormat, null, false);
    };

    const gueltigkeitBestaetigt = (tage: number) => {
        setGueltigkeitTage(tage);
        setGueltigkeitOffen(false);
        if (wartendesFormat) void oeffneMail(wartendesFormat, tage);
    };

    /** Bricht vor dem Mailfenster ab: nichts gebucht, kein Link abgelöst. */
    const abbrechen = () => {
        setFormatOffen(false);
        setAnnahmeOffen(false);
        setGueltigkeitOffen(false);
        setWartendesFormat(null);
        setLaedt(false);
        setEntwurf(false);
        setAbloesenFuerDokumentId(null);
    };

    /** Nach dem Senden bzw. Schließen des Mailfensters. */
    const zuruecksetzen = (gesendet: boolean) => {
        setEntwurf(false);
        setAbloesenFuerDokumentId(null);
        if (gesendet) {
            setGueltigkeitTage(null);
            setWartendesFormat(null);
        }
    };

    return {
        entwurf, formatOffen, annahmeOffen, gueltigkeitOffen, gueltigkeitTage, laedt, abloesenFuerDokumentId,
        starte, formatGewaehlt, annahmeGewaehlt, gueltigkeitBestaetigt, abbrechen, zuruecksetzen,
    };
}
