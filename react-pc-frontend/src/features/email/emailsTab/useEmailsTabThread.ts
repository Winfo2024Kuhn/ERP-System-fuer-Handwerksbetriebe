import { useCallback, useEffect, useState } from 'react';
import type { EmailThread } from '../../../components/EmailThreadView';

/**
 * Lädt den Verlauf der im E-Mail-Reiter geöffneten Mail.
 * Wechselt die Auswahl, wird der alte Verlauf geleert und eine späte Antwort verworfen.
 */
export function useEmailsTabThread(selectedEmailId: number | null) {
    const [thread, setThread] = useState<EmailThread | null>(null);
    const [threadLoading, setThreadLoading] = useState(false);

    useEffect(() => {
        if (!selectedEmailId) return;
        let active = true;
        (async () => {
            setThreadLoading(true);
            try {
                const r = await fetch(`/api/emails/${selectedEmailId}/thread`);
                if (!r.ok) throw new Error('Thread not found');
                const data: EmailThread = await r.json();
                if (active) setThread(data);
            } catch {
                if (active) setThread(null);
            } finally {
                if (active) setThreadLoading(false);
            }
        })();
        return () => { active = false; setThread(null); };
    }, [selectedEmailId]);

    /** Nach Antwort oder gelöschtem Entwurf: Verlauf neu holen; bei Netzausfall bleibt der alte stehen. */
    const reloadThread = useCallback(() => {
        if (!selectedEmailId) return;
        setThreadLoading(true);
        fetch(`/api/emails/${selectedEmailId}/thread`)
            .then(r => r.ok ? r.json() : null)
            .then((data: EmailThread | null) => setThread(data))
            .catch(() => {})
            .finally(() => setThreadLoading(false));
    }, [selectedEmailId]);

    return { thread, threadLoading, reloadThread };
}
