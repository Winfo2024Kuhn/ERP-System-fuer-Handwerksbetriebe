import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { EmailComposeModal } from './EmailComposeModal';
import type { EmailThreadEntry } from './EmailThreadView';
import type { ProjektDetail } from '../types';
import { useEmailsTabListe } from '../features/email/emailsTab/useEmailsTabListe';
import { useEmailsTabThread } from '../features/email/emailsTab/useEmailsTabThread';
import { useEmailsTabReply } from '../features/email/emailsTab/useEmailsTabReply';
import { EmailsTabToolbar } from '../features/email/emailsTab/EmailsTabToolbar';
import { EmailsTabMailliste } from '../features/email/emailsTab/EmailsTabMailliste';
import { EmailsTabKundeInfo } from '../features/email/emailsTab/EmailsTabKundeInfo';
import { EmailsTabThreadDialog } from '../features/email/emailsTab/EmailsTabThreadDialog';
import type { EmailsTabBezug, EmailsTabEintrag } from '../features/email/emailsTab/emailsTabTypes';

export type { EmailsTabBezug };

export interface EmailsTabProps {
    /** Wo der Reiter hängt; die Mailliste lädt er selbst seitenweise vom Server. */
    bezug: EmailsTabBezug;
    /** Kennung von Projekt, Anfrage, Kunde bzw. Lieferant. */
    bezugId: number;

    // Kontext für „Neue E-Mail“ und Antworten (Projekt oder Anfrage)
    projektId?: number;
    anfrageId?: number;

    // Optional: Projekt detail object for compose modal
    projekt?: {
        id: number;
        bauvorhaben?: string;
        kunde?: string;
        kundenEmails?: string[];
        kundeDto?: { kundenEmails?: string[] };
        bezahlt?: boolean;
    };

    // Optional: Anfrage detail object for compose modal  
    anfrage?: {
        bauvorhaben: string;
        kundenName?: string;
        kundenEmails?: string[];
        kundenAnrede?: string;
        kundenAnsprechpartner?: string;
    };

    /** Nach gesendeter Antwort oder neuer E-Mail, z. B. damit der Editor die Zahl am Reiter auffrischt. */
    onEmailSent?: () => void;

    // Optional: Show/hide compose button (defaults to true)
    showComposeButton?: boolean;

    // Optional: Show/hide reply button in thread view (defaults to true)
    showReplyButton?: boolean;
}

/**
 * E-Mail-Reiter in Kunde, Projekt, Anfrage und Lieferant: Mailliste nach Verläufen,
 * seitenweise vom Server („Mehr laden“), Suche und Filter beim Server, Verlauf im Dialog
 * mit Antwort, Anhang-Vorschau und Entwurf löschen (mit „Rückgängig“), neue E-Mail.
 * Bausteine und Logik liegen unter `features/email/emailsTab/`.
 */
export const EmailsTab: React.FC<EmailsTabProps> = ({
    bezug,
    bezugId,
    projektId,
    anfrageId,
    projekt,
    anfrage,
    onEmailSent,
    showComposeButton = true,
    showReplyButton = true,
}) => {
    const navigate = useNavigate();

    const liste = useEmailsTabListe(bezug, bezugId);
    const { neuLaden } = liste;
    const [showComposeModal, setShowComposeModal] = useState(false);
    // Betreff beim Öffnen merken: Nach einer Antwort lädt die Liste neu, der Verlauf zeigt dann eine neuere Mail.
    const [selected, setSelected] = useState<{ id: number; subject: string } | null>(null);
    const selectedEmailId = selected?.id ?? null;

    const { thread, threadLoading, reloadThread } = useEmailsTabThread(selectedEmailId);
    const reply = useEmailsTabReply();
    const { cancelReply } = reply;

    const handleEmailClick = (email: EmailsTabEintrag) => {
        setSelected({ id: email.id, subject: email.subject || '(kein Betreff)' });
        cancelReply();
    };

    const closeThreadView = () => {
        setSelected(null);
        cancelReply();
    };

    const handleReplySuccess = useCallback(() => {
        cancelReply();
        reloadThread();
        neuLaden();
        if (onEmailSent) onEmailSent();
    }, [cancelReply, reloadThread, neuLaden, onEmailSent]);

    const handleOpenDraft = (entry: EmailThreadEntry) => {
        if (entry.draftId) {
            navigate(`/emails/drafts?draft=${encodeURIComponent(entry.draftId)}`);
        }
    };

    const handleComposeSent = () => {
        setShowComposeModal(false);
        neuLaden();
        if (onEmailSent) onEmailSent();
    };

    return (
        <div className="space-y-4">
            {selected && (
                <EmailsTabThreadDialog
                    // Je Mail eine eigene Instanz: Lösch-Aufträge und Vorschau gehören genau zu diesem Verlauf.
                    key={selected.id}
                    subject={selected.subject}
                    thread={thread}
                    threadLoading={threadLoading}
                    showReplyButton={showReplyButton}
                    reply={reply}
                    projektId={projektId}
                    anfrageId={anfrageId}
                    onClose={closeThreadView}
                    onReplySuccess={handleReplySuccess}
                    onReloadThread={reloadThread}
                    onOpenDraft={handleOpenDraft}
                />
            )}

            <EmailsTabToolbar
                search={liste.suchtext}
                onSearchChange={liste.setSuchtext}
                filter={liste.filter}
                onFilterChange={liste.setFilter}
                showCompose={showComposeButton && !!(projektId || anfrageId)}
                onCompose={() => setShowComposeModal(true)}
                info={bezug === 'kunde' ? <EmailsTabKundeInfo /> : undefined}
            />

            <EmailsTabMailliste
                eintraege={liste.eintraege}
                gesamt={liste.gesamt}
                laedt={liste.laedt}
                laedtMehr={liste.laedtMehr}
                fehler={liste.fehler}
                gefiltert={liste.gefiltert}
                leerHinweis={bezug === 'kunde'
                    ? 'E-Mails erscheinen hier, sobald sie einem Projekt oder einer Anfrage dieses Kunden zugeordnet sind.'
                    : undefined}
                onFilterZuruecksetzen={liste.filterZuruecksetzen}
                onMehrLaden={liste.mehrLaden}
                onErneut={neuLaden}
                onOpen={handleEmailClick}
            />

            {showComposeModal && (
                <EmailComposeModal
                    isOpen={showComposeModal}
                    onClose={() => setShowComposeModal(false)}
                    projektId={projektId}
                    projekt={projekt as unknown as ProjektDetail}
                    anfrageId={anfrageId}
                    anfrage={anfrage}
                    onSuccess={handleComposeSent}
                />
            )}
        </div>
    );
};

export default EmailsTab;
