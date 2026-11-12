import { useEffect, useState } from 'react';
import { useToast } from '../../../components/ui/toast';
import { parseErrorMessage } from '../../../components/settings/settingsApi';
import type { EmailZuordnung } from '../../../components/EmailZuordnungSearchModal';
import { aktuellesFrontendProfil } from '../../../lib/frontendProfil';
import { brauchtAufmerksamkeit, formatEinzelversandErgebnis, parseEinzelversandErgebnis } from '../einzelversand';
import type { useEmailDraft } from '../useEmailDraft';
import type { UploadedFile } from './anhangGrenze';
import { baueVersandDto } from './emailVersandDto';
import { SENDEFEHLER, VersandFehler, neueAdresseZumSpeichern, versandHindernis } from './emailVersandPruefung';
import type { EmailComposeFormProps } from './emailComposeTypen';
import type { Empfaenger } from './useEmpfaenger';

interface VersandOptionen extends Pick<EmailComposeFormProps,
    'kundeId' | 'vorDemSenden' | 'zuordnungWaehlbar' | 'pflichtanhangHinweis' | 'freigabenAbloesenFuerDokumentId'> {
    /** Nach erfolgreichem Versand: Aufrufer benachrichtigen und Formular schließen. */
    fertig: () => void;
    empfaenger: Empfaenger;
    subject: string;
    editorHtml: () => string;
    uploadedFiles: UploadedFile[];
    anhaengeZuGross: boolean;
    pflichtanhangFehlt: boolean;
    zuordnung: EmailZuordnung | null;
    replyEmailId?: number;
    weitergeleitetVonEmailId?: number;
    weiterleitungsAnhangIds: number[];
    geschaeftsdokument: boolean;
    /** Frei gewählter Absender; `null` bei festem Absender. */
    postfachId: number | null;
    bekannteAdressen: string[];
    draft: ReturnType<typeof useEmailDraft>;
    erfolgsMeldung: string;
    setError: (meldung: string | null) => void;
    /** Neue Adresse nach dem Senden zum Speichern anbieten (Formular bleibt bis dahin offen). */
    adresseAnbieten: (adresse: string) => void;
}

/**
 * Senden des Formulars: Pflichtangaben prüfen, bei fehlender Zuordnung einmal
 * nachfragen, Entwurf anhalten, vor dem Versand den Aufrufer fragen (z. B.
 * Rechnung buchen), senden und das Ergebnis zeigen.
 */
export function useEmailVersand(o: VersandOptionen) {
    const toast = useToast();
    const [sending, setSending] = useState(false);
    const [zuordnungWarnung, setZuordnungWarnung] = useState(false);
    const { empfaenger, draft, setError } = o;

    // Escape schließt die Rückfrage vor dem Senden
    useEffect(() => {
        if (!zuordnungWarnung) return;
        const beiTaste = (event: KeyboardEvent) => { if (event.key === 'Escape') setZuordnungWarnung(false); };
        document.addEventListener('keydown', beiTaste);
        return () => document.removeEventListener('keydown', beiTaste);
    }, [zuordnungWarnung]);

    const { fertig } = o;

    const nachEinzelversand = async (res: Response) => {
        const ergebnis = parseEinzelversandErgebnis(await res.json().catch(() => null));
        if (brauchtAufmerksamkeit(ergebnis)) {
            // Teilfehler oder nicht abgelegt: Ergebnis stehen lassen, bis der Anwender es gelesen hat.
            empfaenger.setEinzelversandErgebnis(ergebnis);
            if (ergebnis.fehlgeschlagen.length > 0) toast.error(formatEinzelversandErgebnis(ergebnis));
            else toast.warning(formatEinzelversandErgebnis(ergebnis));
            return;
        }
        toast.success(`${formatEinzelversandErgebnis(ergebnis)} – jeder Empfänger hat eine eigene E-Mail bekommen.`);
        fertig();
    };

    const nachVersand = (empfaengerText: string) => {
        toast.success(o.erfolgsMeldung);
        const adresse = neueAdresseZumSpeichern(empfaengerText, o.bekannteAdressen);
        if (adresse && (o.kundeId || o.zuordnung)) o.adresseAnbieten(adresse);
        else fertig();
    };

    const sendeEmail = async () => {
        const einzeln = empfaenger.einzelversandAktiv;
        setSending(true);
        setError(null);
        empfaenger.setEinzelversandErgebnis(null);
        let hinweisBeiSendefehler: string | undefined;
        try {
            await draft.pause();
            if (o.vorDemSenden) {
                const vorbereitung = await o.vorDemSenden();
                if (!vorbereitung.weiter) { draft.resume(); return; }
                hinweisBeiSendefehler = vorbereitung.hinweisBeiSendefehler;
            }
            const dto = baueVersandDto({
                draftId: draft.getDraftId(), recipient: empfaenger.recipient, anEmpfaenger: empfaenger.anEmpfaenger,
                ccRecipients: empfaenger.ccRecipients, einzelversand: einzeln, subject: o.subject, editorHtml: o.editorHtml(),
                benutzer: aktuellesFrontendProfil(), zuordnung: o.zuordnung, geschaeftsdokument: o.geschaeftsdokument,
                postfachId: o.postfachId, weitergeleitetVonEmailId: o.weitergeleitetVonEmailId,
                weiterleitungsAnhangIds: o.weiterleitungsAnhangIds, freigabenAbloesenFuerDokumentId: o.freigabenAbloesenFuerDokumentId,
            });
            const formData = new FormData();
            formData.append('dto', new Blob([JSON.stringify(dto)], { type: 'application/json' }));
            o.uploadedFiles.forEach(eintrag => formData.append('attachments', eintrag.file));
            // Bei Antworten den Reply-Endpoint nutzen (setzt parentEmail sowie In-Reply-To/References)
            const apiUrl = o.replyEmailId ? `/api/emails/${o.replyEmailId}/reply` : '/api/emails/send';
            const res = await fetch(apiUrl, { method: 'POST', body: formData });
            if (!res.ok) {
                if (einzeln && res.status === 502) {
                    // Keine einzige Mail ging raus: Ergebnis mit Gründen zeigen, Formular bleibt offen.
                    const daten = await res.json().catch(() => null) as { message?: string } | null;
                    empfaenger.setEinzelversandErgebnis(parseEinzelversandErgebnis(daten));
                    throw new VersandFehler(daten?.message || 'Keine der E-Mails konnte verschickt werden.');
                }
                throw new VersandFehler(await parseErrorMessage(res, SENDEFEHLER));
            }
            // The backend already deleted the draft immediately after successful SMTP delivery.
            await draft.complete();
            if (einzeln) await nachEinzelversand(res);
            else nachVersand(empfaenger.recipient.trim());
        } catch (err) {
            console.error('E-Mail senden fehlgeschlagen:', err);
            const grund = err instanceof VersandFehler && err.message ? err.message : SENDEFEHLER;
            const meldung = hinweisBeiSendefehler ? `${grund} ${hinweisBeiSendefehler}` : grund;
            setError(meldung);
            toast.error(meldung);
            draft.resume();
        } finally {
            setSending(false);
        }
    };

    // Senden – prüft vorher, ob eine Zuordnung fehlt
    const handleSend = async () => {
        const problem = versandHindernis({
            empfaenger: empfaenger.recipient, betreff: o.subject, anhaengeZuGross: o.anhaengeZuGross,
            zuVieleEmpfaenger: empfaenger.zuVieleEmpfaenger, pflichtanhangFehlt: o.pflichtanhangFehlt,
            pflichtanhangHinweis: o.pflichtanhangHinweis,
        });
        if (problem !== undefined) { setError(problem); return; }
        // Ohne Projekt/Anfrage landet die E-Mail nirgendwo – einmal nachfragen. Nur dort, wo der
        // Vorgang wählbar ist, und nicht beim Antworten: die Antwort erbt die Zuordnung.
        if (o.zuordnungWaehlbar && !o.zuordnung && !o.replyEmailId) { setZuordnungWarnung(true); return; }
        await sendeEmail();
    };

    return { sending, handleSend, sendeEmail, zuordnungWarnung, setZuordnungWarnung };
}
