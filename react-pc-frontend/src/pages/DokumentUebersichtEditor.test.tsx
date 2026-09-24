import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import DokumentUebersichtEditor from './DokumentUebersichtEditor';

/** Testdaten mit Dummy-Kunde (DSGVO: kein echter Name). */
const ausgangsDokument = {
    id: 42,
    dokumentNummer: '2026/01/00001',
    typ: 'RECHNUNG',
    datum: '2026-01-05T00:00:00Z',
    betreff: 'Rechnung',
    betragNetto: 100,
    betragBrutto: 119,
    gebucht: false,
    storniert: false,
    digitalAngenommen: false,
    kundeId: 1,
    kundenName: 'Max Mustermann',
    projektId: null,
    projektAuftragsnummer: null,
};

function mockFetch() {
    vi.stubGlobal('fetch', vi.fn((url: string) => {
        if (url.includes('/ausgang')) {
            return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([ausgangsDokument]) });
        }
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) });
    }));
}

describe('DokumentUebersichtEditor', () => {
    beforeEach(() => {
        mockFetch();
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('öffnet den Dokument-Editor ohne noopener, damit sich der Tab selbst schließen kann', async () => {
        const openMock = vi.fn();
        vi.stubGlobal('open', openMock);
        const user = userEvent.setup();

        render(<DokumentUebersichtEditor />);

        const öffnenButton = await screen.findByTitle('Im Dokument-Editor öffnen', {}, { timeout: 2000 });
        await user.click(öffnenButton);

        expect(openMock).toHaveBeenCalledTimes(1);
        // window.open darf hier NICHT mit 'noopener' aufgerufen werden - der
        // Editor-Tab muss sich per window.close() selbst schließen können,
        // was 'noopener' unterbindet.
        expect(openMock.mock.calls[0]).toHaveLength(2);
    });
});
