import { EmailComposeForm } from './EmailComposeForm';
import type { VorDemSendenErgebnis } from '../features/email/compose/emailComposeTypen';
import type { ProjektDetail } from '../types';

interface EmailComposeModalProps {
    isOpen: boolean;
    onClose: () => void;
    // For projects
    projektId?: number;
    projekt?: ProjektDetail;
    // For offers (Anfragen)
    anfrageId?: number;
    anfrage?: {
        bauvorhaben: string;
        kundenName?: string;
        kundenEmails?: string[];
        kundenAnrede?: string;
        kundenAnsprechpartner?: string;
    };
    /** Customer ID for saving new email addresses */
    kundeId?: number;
    /** Pre-attached files (e.g. generated PDF from DocumentEditor) */
    initialAttachments?: File[];
    /**
     * Markiert den Versand als Ausgangsgeschäftsdokument. Ist ein eigenes
     * Postfach dafür eingerichtet, geht die Mail darüber raus und der Absender
     * ist fest vorgegeben.
     */
    geschaeftsdokument?: boolean;
    initialRecipient?: string;
    initialSubject?: string;
    initialBody?: string;
    /** Optional callback after successful email send */
    onSuccess?: () => void;
    /** Siehe EmailComposeForm: läuft nach Klick auf „Senden“, vor dem Versand (z. B. Buchen). */
    vorDemSenden?: () => Promise<VorDemSendenErgebnis>;
    /** Siehe EmailComposeForm: Vorschau/Download der angehängten Datei sperren, Hinweis zeigen. */
    anhangVorschauGesperrt?: string;
    /** Siehe EmailComposeForm: `false` = kein automatischer Entwurf. */
    entwurfSpeichern?: boolean;
    /** Siehe EmailComposeForm: offene Online-Links anderer Angebote beim Senden ablösen. */
    freigabenAbloesenFuerDokumentId?: number;
    /** Siehe EmailComposeForm: automatisch angehängte Dateien sind Pflicht. */
    pflichtanhangHinweis?: string;
    /** Siehe EmailComposeForm: Text der Erfolgsmeldung nach dem Senden. */
    erfolgsMeldung?: string;
}

export default function EmailComposeModal({
    isOpen,
    onClose,
    projektId,
    projekt,
    anfrageId,
    anfrage,
    kundeId,
    initialAttachments,
    geschaeftsdokument,
    initialRecipient,
    initialSubject,
    initialBody,
    onSuccess,
    vorDemSenden,
    anhangVorschauGesperrt,
    entwurfSpeichern,
    freigabenAbloesenFuerDokumentId,
    pflichtanhangHinweis,
    erfolgsMeldung,
}: EmailComposeModalProps) {
    if (!isOpen) return null;

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
        >
            <div
                className="bg-white rounded-2xl shadow-2xl w-full max-w-6xl mx-4 h-[95vh] overflow-hidden flex flex-col"
                onClick={(e) => e.stopPropagation()}
            >
                <EmailComposeForm
                    onClose={onClose}
                    projektId={projektId}
                    projekt={projekt}
                    anfrageId={anfrageId}
                    anfrage={anfrage}
                    kundeId={kundeId}
                    initialAttachments={initialAttachments}
                    geschaeftsdokument={geschaeftsdokument}
                    initialRecipient={initialRecipient}
                    initialSubject={initialSubject}
                    initialBody={initialBody}
                    onSuccess={onSuccess || (() => window.location.reload())}
                    vorDemSenden={vorDemSenden}
                    anhangVorschauGesperrt={anhangVorschauGesperrt}
                    entwurfSpeichern={entwurfSpeichern}
                    freigabenAbloesenFuerDokumentId={freigabenAbloesenFuerDokumentId}
                    pflichtanhangHinweis={pflichtanhangHinweis}
                    erfolgsMeldung={erfolgsMeldung}
                    variant="modal"
                />
            </div>
        </div>
    );
}

export { EmailComposeModal };
