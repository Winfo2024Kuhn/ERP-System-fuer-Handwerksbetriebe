import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ConfirmProvider } from './ui/confirm-dialog';
import { KundeNotizenTab } from './KundeNotizenTab';

// Debounce-Wartezeit der Suche plus etwas Puffer.
const DEBOUNCE_MS = 300;

let fetchMock: ReturnType<typeof vi.fn>;

/** Alle URLs, mit denen Notizen nachgeladen wurden. */
function notizenRequests(): string[] {
    return fetchMock.mock.calls
        .map((call: unknown[]) => call[0])
        .filter((url: unknown): url is string => typeof url === 'string' && url.includes('/notizen?'));
}

function renderNotizenTab() {
    // DSGVO: Dummy-Kunde ohne echte Personendaten.
    return render(
        <ConfirmProvider>
            <KundeNotizenTab kundeId={1} notizen={[]} />
        </ConfirmProvider>
    );
}

describe('KundeNotizenTab – Suche', () => {
    beforeEach(() => {
        fetchMock = vi.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve([]) }));
        // stubGlobal statt direkter Zuweisung: nur so stellt unstubAllGlobals das
        // echte `fetch` wieder her und die Testdatei beeinflusst keine andere.
        vi.stubGlobal('fetch', fetchMock);
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it('zeigt keinen Suchen-Button, weil live gesucht wird', () => {
        renderNotizenTab();

        expect(screen.queryByRole('button', { name: 'Suchen' })).not.toBeInTheDocument();
    });

    it('lädt die Notizen beim Tippen nach', async () => {
        const user = userEvent.setup();
        renderNotizenTab();

        await user.type(screen.getByPlaceholderText('Notizen durchsuchen...'), 'rueckruf');

        await waitFor(() => {
            expect(notizenRequests().at(-1)).toContain('q=rueckruf');
        });
    });

    it('lädt beim Öffnen nichts nach, weil die Notizen als Prop kommen', async () => {
        renderNotizenTab();

        await new Promise(resolve => setTimeout(resolve, DEBOUNCE_MS * 2));

        expect(notizenRequests()).toHaveLength(0);
    });
});
