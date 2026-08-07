import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';
import DocumentEditor from './index';

// DSGVO: ausschliesslich Dummy-Daten.
const ALTE_ADRESSE = 'Max Mustermann\nMusterweg 1\n12345 Musterstadt';
const NEUE_ADRESSE = 'Max Mustermann\nNeue Gasse 7\n54321 Beispielstadt';

// Bewusst "heute": Bei einem noch nicht versendeten Dokument laesst der Editor
// das Datum mit heute mitlaufen. Ein fest verdrahtetes Datum waere ab dem
// Folgetag Vergangenheit, der Editor wuerde es bumpen und das Dokument allein
// deshalb als "Ungespeichert" melden — die Adress-Assertions unten haetten
// nichts damit zu tun.
const HEUTE_ISO = new Date().toISOString().split('T')[0];

const dokumentAntwort = {
    id: 1,
    typ: 'RECHNUNG',
    dokumentNummer: 'RE-2026/08/00001',
    datum: HEUTE_ISO,
    betreff: 'Dachsanierung',
    betragNetto: 1000,
    htmlInhalt: '',
    // Bewusst ohne Bloecke: der Test zielt auf die Kopfdaten, und ohne
    // Textbloecke braucht es keine Tiptap-Instanzen in jsdom.
    positionenJson: JSON.stringify({ blocks: [], globalRabatt: 0 }),
    kundenName: 'Max Mustermann',
    kundennummer: 'K-4711',
    rechnungsadresse: ALTE_ADRESSE,
    rechnungsadresseOverride: ALTE_ADRESSE,
    gebucht: false,
    storniert: false,
    zahlungszielTage: 14,
};

/** Alle PUT-Bodies, mit denen das Dokument gespeichert wurde. */
function speicherAufrufe(fetchMock: ReturnType<typeof mockFetch>): Array<Record<string, unknown>> {
    return fetchMock.mock.calls
        .filter(call => {
            const url = call[0] as string;
            const init = call[1] as RequestInit | undefined;
            return typeof url === 'string'
                && url === '/api/ausgangs-dokumente/1'
                && init?.method === 'PUT';
        })
        .map(call => JSON.parse((call[1] as RequestInit).body as string));
}

/** Anzahl der angeforderten Vorschau-PDFs. */
function vorschauAufrufe(fetchMock: ReturnType<typeof mockFetch>): number {
    return fetchMock.mock.calls.filter(call => call[0] === '/api/dokument-generator/preview').length;
}

function jsonAntwort(body: unknown) {
    return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve(body),
        text: () => Promise.resolve(JSON.stringify(body)),
    });
}

function mockFetch() {
    return vi.fn((url: string, init?: RequestInit) => {
        if (url === '/api/ausgangs-dokumente/1' && init?.method === 'PUT') {
            const gesendet = JSON.parse(init.body as string);
            // Server antwortet mit dem gespeicherten Stand.
            return jsonAntwort({ ...dokumentAntwort, ...gesendet });
        }
        if (url === '/api/ausgangs-dokumente/1') {
            return jsonAntwort(dokumentAntwort);
        }
        if (url === '/api/dokument-generator/preview') {
            return Promise.resolve({
                ok: true,
                status: 200,
                blob: () => Promise.resolve(new Blob(['%PDF-1.4'], { type: 'application/pdf' })),
            });
        }
        if (url.startsWith('/api/formulare/templates/selection')) {
            return jsonAntwort({ templateName: null });
        }
        if (url.startsWith('/api/dokument-locks/')) {
            return jsonAntwort({ ok: true });
        }
        // Textbausteine, Leistungen, Arbeitszeitarten, Abrechnungsverlauf, ...
        return jsonAntwort([]);
    });
}

function renderEditor() {
    return render(
        <MemoryRouter initialEntries={['/dokumente/1']}>
            <ConfirmProvider>
                <ToastProvider>
                    <DocumentEditor dokumentId={1} onClose={() => { }} />
                </ToastProvider>
            </ConfirmProvider>
        </MemoryRouter>
    );
}

/** Adresse ueber den Inline-Editor aendern und bestaetigen. */
async function adresseAendern(user: ReturnType<typeof userEvent.setup>, neueAdresse: string) {
    await user.click(screen.getByTitle('Rechnungsadresse für dieses Dokument bearbeiten'));
    const textarea = await screen.findByRole('textbox', { name: /Rechnungsadresse bearbeiten/i });
    await user.clear(textarea);
    await user.paste(neueAdresse);
    await user.click(screen.getByRole('button', { name: /Übernehmen/i }));
}

describe('DocumentEditor – Rechnungsadresse', () => {
    let fetchMock: ReturnType<typeof mockFetch>;

    beforeEach(() => {
        fetchMock = mockFetch();
        global.fetch = fetchMock as unknown as typeof fetch;
        // jsdom kennt keine Blob-URLs.
        global.URL.createObjectURL = vi.fn(() => `blob:vorschau-${Math.random()}`);
        global.URL.revokeObjectURL = vi.fn();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('speichert die geänderte Adresse und nicht den Stand davor', async () => {
        // Regression: handleSave las `kontextDaten` aus einer eingefrorenen
        // useCallback-Closure. Da eine reine Adressänderung keine der Deps
        // berührt, ging die Adresse VOR der Änderung ans Backend.
        const user = userEvent.setup();
        renderEditor();

        // Grosszuegiger Timeout: der Initial-Load laeuft ueber mehrere
        // sequentielle Fetch-Roundtrips.
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });

        await adresseAendern(user, NEUE_ADRESSE);
        await user.click(screen.getByRole('button', { name: /Speichern/i }));

        await waitFor(() => expect(speicherAufrufe(fetchMock).length).toBeGreaterThan(0));
        const letzterBody = speicherAufrufe(fetchMock).at(-1)!;
        expect(letzterBody.rechnungsadresseOverride).toBe(NEUE_ADRESSE);
    });

    it('rendert die Vorschau nach dem Speichern neu', async () => {
        const user = userEvent.setup();
        renderEditor();

        // Grosszuegiger Timeout: der Initial-Load laeuft ueber mehrere
        // sequentielle Fetch-Roundtrips.
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        await waitFor(() => expect(vorschauAufrufe(fetchMock)).toBeGreaterThan(0));

        const vorDemSpeichern = vorschauAufrufe(fetchMock);

        await adresseAendern(user, NEUE_ADRESSE);
        await user.click(screen.getByRole('button', { name: /Speichern/i }));

        await waitFor(() => expect(vorschauAufrufe(fetchMock)).toBeGreaterThan(vorDemSpeichern));
    });

    it('meldet eine unbestätigte Adressänderung als ungespeichert', async () => {
        const user = userEvent.setup();
        renderEditor();

        // Grosszuegiger Timeout: der Initial-Load laeuft ueber mehrere
        // sequentielle Fetch-Roundtrips.
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        await adresseAendern(user, NEUE_ADRESSE);

        expect(await screen.findByText(/^Ungespeichert$/)).toBeInTheDocument();
    });

    it('löst bei unveränderter Adresse kein Speichern aus', async () => {
        // "Übernehmen" ohne echte Änderung darf die Adressvererbung vom
        // Vorgang nicht kappen und kein Dirty-Flag setzen.
        const user = userEvent.setup();
        renderEditor();

        // Grosszuegiger Timeout: der Initial-Load laeuft ueber mehrere
        // sequentielle Fetch-Roundtrips.
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        await adresseAendern(user, ALTE_ADRESSE);

        // Positiv-Anker zuerst, damit die Negativ-Assertion nicht bloss zu
        // frueh greift: die Adresse steht unveraendert da.
        expect(await screen.findByText(/Musterweg 1/)).toBeInTheDocument();
        expect(screen.queryByText(/^Ungespeichert$/)).not.toBeInTheDocument();
        expect(speicherAufrufe(fetchMock)).toHaveLength(0);
    });
});
