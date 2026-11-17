import type { ComponentProps } from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';
import DocumentEditor from './index';
import type { StandDetail } from '../dokument/staende/staendeTypen';

/**
 * Stände im Dokumenteditor: Knopf „Verlauf“ (speichert vorher) und die
 * schreibgeschützte Ansicht eines Stands (Überlagerung, kein Datum-auf-heute,
 * keine Export- und Versandknöpfe, kein automatisches Speichern).
 * Nur Dummy-Daten (DSGVO).
 */

vi.mock('../../lib/scrollInDenBlick', () => ({ scrolleMittigInDenBlick: vi.fn() }));

const ADRESSE = 'Max Mustermann\nMusterweg 1\n12345 Musterstadt';
const ALTE_ADRESSE = 'Max Mustermann\nAlter Weg 3\n12345 Musterstadt';

const dokument = {
    id: 1,
    typ: 'ANGEBOT',
    dokumentNummer: 'AG-2026/11/00001',
    // Alt und nicht versendet: Der normale Editor würde auf heute setzen.
    datum: '2026-01-05',
    betreff: 'Geländer neu',
    betragNetto: 1000,
    htmlInhalt: '',
    positionenJson: JSON.stringify({ blocks: [], globalRabatt: 0 }),
    kundenName: 'Max Mustermann',
    rechnungsadresse: ADRESSE,
    rechnungsadresseOverride: ADRESSE,
    gebucht: false,
    storniert: false,
    bearbeitbar: true,
    zahlungszielTage: 14,
};

const STAND: StandDetail = {
    id: 3, nummer: 3, art: 'EXPORT', festgehaltenAm: '2026-01-05T14:32:00', festgehaltenVon: 'Max Mustermann',
    bearbeitetVon: [], betragNetto: 950, aenderungen: ['Erste Fassung'], wiederhergestelltAusNummer: null,
    aktuell: false, pdfVorhanden: true, betreff: 'Geländer Edelstahl', datum: '2026-01-05', mwstSatz: 19,
    zahlungszielTage: 21, rechnungsadresseOverride: ALTE_ADRESSE,
    positionenJson: JSON.stringify({ blocks: [], globalRabatt: 0, standardTextbausteineErneuern: true }),
    htmlInhalt: '', dokumentBearbeitbar: true,
};

function jsonAntwort(body: unknown) {
    return Promise.resolve({
        ok: true, status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: () => Promise.resolve(body),
        text: () => Promise.resolve(JSON.stringify(body)),
    });
}

const RESET_MELDUNG = 'Dieses Dokument wurde inzwischen auf einen älteren Stand zurückgesetzt. Bitte neu laden.';

function mockFetch(festhalten: 'ok' | 'fehler' = 'ok', extra: { dokument?: Record<string, unknown>; put409?: boolean } = {}) {
    const geladen = { ...dokument, ...extra.dokument };
    return vi.fn((url: string, init?: RequestInit) => {
        if (url === '/api/ausgangs-dokumente/1' && init?.method === 'PUT' && extra.put409) {
            return Promise.resolve({
                ok: false, status: 409, statusText: 'Conflict',
                headers: new Headers({ 'content-type': 'application/json' }),
                json: () => Promise.resolve({ status: 409, message: RESET_MELDUNG }),
                text: () => Promise.resolve(JSON.stringify({ status: 409, message: RESET_MELDUNG })),
            });
        }
        if (url === '/api/ausgangs-dokumente/1/staende/festhalten') {
            return festhalten === 'ok'
                ? jsonAntwort({ festgehalten: true, stand: null })
                : Promise.resolve({ ok: false, status: 500, headers: new Headers(), json: () => Promise.resolve({}), text: () => Promise.resolve('') });
        }
        if (url === '/api/ausgangs-dokumente/1' && init?.method === 'PUT') {
            return jsonAntwort({ ...geladen, ...JSON.parse(init.body as string) });
        }
        if (url === '/api/ausgangs-dokumente/1') return jsonAntwort(geladen);
        if (url.startsWith('/api/ausgangs-dokumente/1/staende?')) {
            return jsonAntwort({ staende: [], gesamt: 0, seite: 0, seitenGroesse: 25, hatMehr: false, offeneAenderungen: null });
        }
        if (url.startsWith('/api/dokument-generator')) {
            return Promise.resolve({ ok: true, status: 200, blob: () => Promise.resolve(new Blob(['%PDF-1.4'], { type: 'application/pdf' })) });
        }
        if (url.startsWith('/api/formulare/templates/selection')) return jsonAntwort({ templateName: null });
        return jsonAntwort([]);
    });
}

function renderEditor(props: Partial<ComponentProps<typeof DocumentEditor>> = {}) {
    return render(
        <MemoryRouter initialEntries={['/dokument-editor?dokumentId=1']}>
            <ConfirmProvider>
                <ToastProvider>
                    <DocumentEditor dokumentId={1} onClose={() => { }} {...props} />
                </ToastProvider>
            </ConfirmProvider>
        </MemoryRouter>,
    );
}

const aufrufe = (fetchMock: ReturnType<typeof mockFetch>, teil: string) =>
    fetchMock.mock.calls.filter(([url]) => String(url).includes(teil));

const putAufrufe = (fetchMock: ReturnType<typeof mockFetch>) =>
    fetchMock.mock.calls.filter(([url, init]) => url === '/api/ausgangs-dokumente/1' && (init as RequestInit | undefined)?.method === 'PUT');

let fetchMock: ReturnType<typeof mockFetch>;
beforeEach(() => {
    fetchMock = mockFetch();
    global.fetch = fetchMock as unknown as typeof fetch;
    global.URL.createObjectURL = vi.fn(() => `blob:vorschau-${Math.random()}`);
    global.URL.revokeObjectURL = vi.fn();
});
afterEach(() => {
    vi.restoreAllMocks();
});

describe('DocumentEditor – Knopf „Verlauf“', () => {
    it('speichert ungespeicherte Änderungen und öffnet dann den Verlauf mit den Ständen', async () => {
        const user = userEvent.setup();
        renderEditor();
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });

        await user.click(screen.getByTitle('Rechnungsadresse für dieses Dokument bearbeiten'));
        const feld = await screen.findByRole('textbox', { name: /Rechnungsadresse bearbeiten/i });
        await user.clear(feld);
        await user.paste('Max Mustermann\nNeue Gasse 7');
        await user.click(screen.getByRole('button', { name: /Übernehmen/i }));
        expect(putAufrufe(fetchMock)).toHaveLength(0);

        await user.click(screen.getByRole('button', { name: 'Verlauf' }));

        const dialog = await screen.findByRole('dialog', { name: /Verlauf/ });
        expect(putAufrufe(fetchMock)).toHaveLength(1);
        expect(within(dialog).getByRole('tab', { name: 'Stände' })).toHaveAttribute('aria-selected', 'true');
        expect(await within(dialog).findByText(/Noch kein Stand\./)).toBeInTheDocument();
    });
});

describe('DocumentEditor – Ansicht eines Stands', () => {
    it('zeigt den Inhalt des Stands schreibgeschützt, ohne Export, Versand und Verlauf', async () => {
        renderEditor({ readOnly: true, standAnsicht: STAND });

        expect(await screen.findByText(/Alter Weg 3/, {}, { timeout: 3000 })).toBeInTheDocument();
        expect(screen.queryByText(/Musterweg 1/)).not.toBeInTheDocument();
        for (const name of ['PDF', 'E-Mail', 'Entwurf', 'Drucken', 'Verlauf']) {
            expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
        }
    });

    it('setzt das Datum nicht auf heute und speichert nichts', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        try {
            renderEditor({ readOnly: true, standAnsicht: STAND });
            await screen.findByText(/Alter Weg 3/, {}, { timeout: 3000 });
            await vi.advanceTimersByTimeAsync(12_000);
            expect(putAufrufe(fetchMock)).toHaveLength(0);
            const vorschauen = fetchMock.mock.calls.filter(([url]) => url === '/api/dokument-generator/preview');
            expect(vorschauen.length).toBeGreaterThan(0);
            const { kopfdaten } = JSON.parse((vorschauen.at(-1)![1] as RequestInit).body as string);
            expect(kopfdaten.rechnungsDatum).toBe('2026-01-05');
            expect(kopfdaten.betreff).toBe('Geländer Edelstahl');
            expect(kopfdaten.zahlungszielTage).toBe(21);
        } finally {
            vi.useRealTimers();
        }
    });
});

describe('DocumentEditor – Export hält einen Stand fest', () => {
    it('PDF-Download eines Angebots hält einen Stand fest (ohne PDF-Ablage)', async () => {
        const user = userEvent.setup();
        renderEditor();
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });

        await user.click(screen.getByRole('button', { name: 'PDF', exact: true }));
        await user.click(await screen.findByText('Standard PDF-Datei herunterladen'));

        await waitFor(() => expect(aufrufe(fetchMock, '/staende/festhalten')).toHaveLength(1));
        expect((aufrufe(fetchMock, '/staende/festhalten')[0][1] as RequestInit).method).toBe('POST');
        expect(aufrufe(fetchMock, '/pdf-speichern')).toHaveLength(0);
    });

    it('Drucken eines Angebots hält einen Stand fest', async () => {
        const user = userEvent.setup();
        renderEditor();
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });

        await user.click(screen.getByRole('button', { name: 'Drucken' }));
        const knoepfe = await screen.findAllByRole('button', { name: 'Drucken' });
        await user.click(knoepfe.at(-1)!);

        await waitFor(() => expect(aufrufe(fetchMock, '/staende/festhalten')).toHaveLength(1));
        expect(aufrufe(fetchMock, '/pdf-speichern')).toHaveLength(0);
    });

    it('warnt, wenn der Stand nicht festgehalten werden kann; der Download läuft trotzdem', async () => {
        fetchMock = mockFetch('fehler');
        global.fetch = fetchMock as unknown as typeof fetch;
        const user = userEvent.setup();
        renderEditor();
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        const vorher = vi.mocked(global.URL.createObjectURL).mock.calls.length;

        await user.click(screen.getByRole('button', { name: 'PDF', exact: true }));
        await user.click(await screen.findByText('Standard PDF-Datei herunterladen'));

        expect(await screen.findByText('Der Stand für den Verlauf konnte nicht festgehalten werden.')).toBeInTheDocument();
        await waitFor(() => expect(vi.mocked(global.URL.createObjectURL).mock.calls.length).toBeGreaterThan(vorher));
    });
});

async function adresseAendern(user: ReturnType<typeof userEvent.setup>) {
    await user.click(screen.getByTitle('Rechnungsadresse für dieses Dokument bearbeiten'));
    const feld = await screen.findByRole('textbox', { name: /Rechnungsadresse bearbeiten/i });
    await user.clear(feld);
    await user.paste('Max Mustermann\nNeue Gasse 7');
    await user.click(screen.getByRole('button', { name: /Übernehmen/i }));
}

const putBodies = (fetchMock: ReturnType<typeof mockFetch>) =>
    putAufrufe(fetchMock).map(([, init]) => JSON.parse((init as RequestInit).body as string));

describe('DocumentEditor – Zähler der Wiederherstellungen', () => {
    it('schickt den Zähler aus dem geladenen Dokument beim Speichern mit', async () => {
        fetchMock = mockFetch('ok', { dokument: { wiederherstellungsZaehler: 2 } });
        global.fetch = fetchMock as unknown as typeof fetch;
        const user = userEvent.setup();
        renderEditor();
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });

        await adresseAendern(user);
        await user.click(screen.getByRole('button', { name: /Speichern/i }));

        await waitFor(() => expect(putBodies(fetchMock)).toHaveLength(1));
        expect(putBodies(fetchMock)[0].wiederherstellungsZaehler).toBe(2);
    });

    it('lässt das Feld weg, wenn das Dokument keinen Zähler hat', async () => {
        const user = userEvent.setup();
        renderEditor();
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });

        await adresseAendern(user);
        await user.click(screen.getByRole('button', { name: /Speichern/i }));

        await waitFor(() => expect(putBodies(fetchMock)).toHaveLength(1));
        expect('wiederherstellungsZaehler' in putBodies(fetchMock)[0]).toBe(false);
    });

    it('zeigt die Meldung des Servers bei inzwischen zurückgesetztem Dokument und speichert danach nicht endlos weiter', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        try {
            fetchMock = mockFetch('ok', { dokument: { wiederherstellungsZaehler: 1 }, put409: true });
            global.fetch = fetchMock as unknown as typeof fetch;
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
            renderEditor();
            await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });

            await adresseAendern(user);
            await user.click(screen.getByRole('button', { name: /Speichern/i }));

            const meldung = await screen.findByText(RESET_MELDUNG);
            await user.click(within(meldung.closest('[role="dialog"]') as HTMLElement).getByRole('button', { name: 'Abbrechen' }));
            await vi.advanceTimersByTimeAsync(35_000);

            expect(putAufrufe(fetchMock)).toHaveLength(1);
        } finally {
            vi.useRealTimers();
        }
    });
});

describe('DocumentEditor – erst speichern, dann Stand festhalten', () => {
    const index = (fetchMock: ReturnType<typeof mockFetch>, pruefe: (url: string, init?: RequestInit) => boolean) =>
        fetchMock.mock.calls.findIndex(([url, init]) => pruefe(String(url), init as RequestInit | undefined));

    it('PDF-Download speichert ungespeicherte Änderungen vor dem Festhalten', async () => {
        const user = userEvent.setup();
        renderEditor();
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        await adresseAendern(user);

        await user.click(screen.getByRole('button', { name: 'PDF', exact: true }));
        await user.click(await screen.findByText('Standard PDF-Datei herunterladen'));

        await waitFor(() => expect(aufrufe(fetchMock, '/staende/festhalten')).toHaveLength(1));
        const put = index(fetchMock, (u, i) => u === '/api/ausgangs-dokumente/1' && i?.method === 'PUT');
        const fest = index(fetchMock, (u) => u.endsWith('/staende/festhalten'));
        expect(put).toBeGreaterThanOrEqual(0);
        expect(put).toBeLessThan(fest);
    });

    it('Drucken speichert ungespeicherte Änderungen vor dem Festhalten', async () => {
        const user = userEvent.setup();
        renderEditor();
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        await adresseAendern(user);

        await user.click(screen.getByRole('button', { name: 'Drucken' }));
        await user.click((await screen.findAllByRole('button', { name: 'Drucken' })).at(-1)!);

        await waitFor(() => expect(aufrufe(fetchMock, '/staende/festhalten')).toHaveLength(1));
        const put = index(fetchMock, (u, i) => u === '/api/ausgangs-dokumente/1' && i?.method === 'PUT');
        expect(put).toBeGreaterThanOrEqual(0);
        expect(put).toBeLessThan(index(fetchMock, (u) => u.endsWith('/staende/festhalten')));
    });
});
