import { act, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import type { EmailThread } from '../../../components/EmailThreadView';
import { EmailsTabThreadDialog } from './EmailsTabThreadDialog';
import type { EmailsTabReply } from './useEmailsTabReply';

/**
 * Fokusführung des Verlauf-Dialogs, ohne den E-Mail-Reiter drumherum.
 * Das Formular ist ersetzt. Dummy-Daten (DSGVO): Max Mustermann.
 */
type KopfDaten = { betreff: string; gesperrt: string | null } | null;
const formular = vi.hoisted(() => ({ onKopfDaten: undefined as ((daten: KopfDaten) => void) | undefined }));
vi.mock('../../../components/EmailComposeForm', () => ({
    EmailComposeForm: (props: { onKopfDaten?: (daten: KopfDaten) => void }) => {
        formular.onKopfDaten = props.onKopfDaten;
        return <div data-testid="antwort-formular" />;
    },
}));

const VERLAUF: EmailThread = {
    rootEmailId: 7, focusedEmailId: 7, emails: [{
        id: 7, subject: 'Anfrage Geländer', fromAddress: 'Max Mustermann <max@example.com>',
        direction: 'IN', sentAt: '2026-10-09T10:05:00', htmlBody: '<p>Bitte um Angebot</p>', attachments: [],
    }],
};

function antwort(isReplying: boolean): EmailsTabReply {
    return {
        isReplying, replyLaedt: false,
        replyToEmail: isReplying ? { id: 7, subject: 'Anfrage Geländer', direction: 'IN' } : null,
        replyToEmailId: isReplying ? 7 : undefined,
        startReply: vi.fn(() => Promise.resolve()), cancelReply: vi.fn(),
        replyQuote: '', replyInitialRecipient: 'max@example.com', replyInitialSubject: 'AW: Anfrage Geländer',
    };
}

function zeige(isReplying: boolean, showReplyButton: boolean) {
    const props = {
        subject: 'Anfrage Geländer', thread: VERLAUF, threadLoading: false, showReplyButton,
        onClose: vi.fn(), onReplySuccess: vi.fn(), onReloadThread: vi.fn(), onOpenDraft: vi.fn(),
    };
    const ansicht = render(<ToastProvider><EmailsTabThreadDialog {...props} reply={antwort(isReplying)} /></ToastProvider>);
    return (weiter: boolean) => ansicht.rerender(<ToastProvider><EmailsTabThreadDialog {...props} reply={antwort(weiter)} /></ToastProvider>);
}

describe('EmailsTabThreadDialog – Fokus', () => {
    it('setzt den Fokus nach dem Zurück auf den Titel, wenn es keinen „Antworten“-Knopf gibt', async () => {
        const umschalten = zeige(true, false);
        umschalten(false);
        const titel = screen.getByRole('heading', { name: 'Anfrage Geländer' });
        await waitFor(() => expect(document.activeElement).toBe(titel));
        expect(titel).toHaveAttribute('tabindex', '-1');
    });

    it('holt den Fokus auf den Pfeil zurück, wenn er während der Sperre verloren ging', () => {
        zeige(true, true);
        const pfeil = screen.getByRole('button', { name: 'Zurück zum Verlauf' });
        // Im Browser fällt der Fokus beim Sperren an <body>; jsdom macht das nicht selbst.
        act(() => { (document.activeElement as HTMLElement | null)?.blur(); });
        expect(document.activeElement).toBe(document.body);
        act(() => formular.onKopfDaten?.({ betreff: 'AW: Anfrage Geländer', gesperrt: 'Wird gespeichert…' }));
        expect(pfeil).toBeDisabled();

        act(() => formular.onKopfDaten?.({ betreff: 'AW: Anfrage Geländer', gesperrt: null }));
        expect(pfeil).toBeEnabled();
        expect(document.activeElement).toBe(pfeil);
    });
});
