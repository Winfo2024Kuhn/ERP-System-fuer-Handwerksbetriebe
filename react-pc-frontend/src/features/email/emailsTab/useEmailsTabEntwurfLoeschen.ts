import { useCallback, useMemo } from 'react';
import type { EmailThread } from '../../../components/EmailThreadView';
import { useVerzoegertesLoeschen } from '../../../hooks/useVerzoegertesLoeschen';
import { entwurfBezeichnung } from './emailsTabFormat';

const schluessel = (draftId: number) => `email-entwurf:${draftId}`;

/**
 * Entwurf im geöffneten Verlauf löschen, mit „Rückgängig“ statt Rückfrage.
 *
 * Der Entwurf verschwindet sofort aus dem Verlauf; das DELETE geht erst nach Ablauf
 * der Frist raus, danach wird der Verlauf neu geladen. Schließt der Verlauf vorher,
 * geht das DELETE sofort raus (Verhalten von {@link useVerzoegertesLoeschen}).
 */
export function useEmailsTabEntwurfLoeschen(thread: EmailThread | null, reloadThread: () => void) {
    const { ausgeblendet, planen } = useVerzoegertesLoeschen();

    const sichtbarerVerlauf = useMemo(() => {
        if (!thread || ausgeblendet.size === 0) return thread;
        return {
            ...thread,
            emails: thread.emails.filter(e => !(e.isDraft && e.draftId && ausgeblendet.has(schluessel(e.draftId)))),
        };
    }, [thread, ausgeblendet]);

    const entwurfLoeschen = useCallback((draftId: number) => {
        const entwurf = thread?.emails.find(e => e.isDraft && e.draftId === draftId);
        planen({
            schluessel: schluessel(draftId),
            url: `/api/emails/drafts/${encodeURIComponent(draftId)}`,
            name: 'Entwurf',
            anzeige: entwurfBezeichnung(entwurf),
            fehlerText: 'Der Entwurf konnte nicht gelöscht werden.',
            onGeloescht: reloadThread,
        });
    }, [planen, reloadThread, thread]);

    return { sichtbarerVerlauf, entwurfLoeschen };
}
