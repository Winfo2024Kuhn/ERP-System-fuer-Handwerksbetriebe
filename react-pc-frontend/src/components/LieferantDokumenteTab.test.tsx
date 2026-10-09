import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LieferantDokumenteTab, { POSITIONSSUCHE_FEHLER_TEXT } from './LieferantDokumenteTab';
import { ToastProvider } from './ui/toast';
import { ConfirmProvider } from './ui/confirm-dialog';
import type { LieferantDokument, LieferantDokumentTyp } from '../types';

vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ isAdmin: false }) }));
vi.mock('./ui/PdfCanvasViewer', () => ({
    PdfCanvasViewer: ({ url }: { url: string }) => <div data-testid="pdf">{url}</div>,
}));

/**
 * Lieferanten-Reiter „Dokumente“: Typ-Filter Werkstoffzeugnis und die
 * Positionssuche (Material, Charge …) zusätzlich zur Suche im Browser.
 * DSGVO: nur Fantasie-Daten (Musterstahl GmbH).
 */

const LIEFERANT_ID = 7;

function dokument(id: number, typ: LieferantDokumentTyp, nummer: string): LieferantDokument {
    return {
        id,
        typ,
        originalDateiname: `${nummer}.pdf`,
        uploadDatum: '2026-09-01T10:00:00',
        geschaeftsdaten: { dokumentNummer: nummer, dokumentDatum: '2026-09-01' },
        projektAnteile: [],
        verknuepfteDokumente: [],
    };
}

const DOKUMENTE = [
    dokument(1, 'LIEFERSCHEIN', 'LS-1001'),
    dokument(2, 'WERKSTOFFZEUGNIS', 'WZ-2002'),
    dokument(3, 'RECHNUNG', 'RE-3003'),
];

function antwort(body: unknown, status = 200) {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function rendere(dokumente = DOKUMENTE) {
    return render(
        <MemoryRouter>
            <ToastProvider>
                <ConfirmProvider>
                    <LieferantDokumenteTab lieferantId={LIEFERANT_ID} lieferantName="Musterstahl GmbH" dokumente={dokumente} />
                </ConfirmProvider>
            </ToastProvider>
        </MemoryRouter>,
    );
}

function suchfeld() {
    return screen.getByRole('textbox', { name: 'Dokumente durchsuchen' });
}

function sichtbareNummern() {
    return screen.queryAllByRole('heading', { level: 4 }).map(h => h.textContent);
}

function positionssucheAufrufe(fetchMock: ReturnType<typeof vi.fn>) {
    return fetchMock.mock.calls.filter(([url]) => String(url).includes('/positionssuche'));
}

describe('LieferantDokumenteTab – Werkstoffzeugnis und Positionssuche', () => {
    let fetchMock: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        fetchMock = vi.fn((url: string) => {
            if (url.includes('/positionssuche')) {
                return Promise.resolve(antwort([{ dokumentId: 2, trefferText: 'Flachstahl 50x5 · S235JR · Charge 123456', weitereTreffer: 2 }]));
            }
            return Promise.resolve(antwort([]));
        });
        globalThis.fetch = fetchMock as unknown as typeof fetch;
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('beschriftet das Zeugnis und bietet den Platzhalter für Material und Charge', () => {
        rendere();
        expect(suchfeld()).toHaveAttribute('placeholder', 'Nummer, Material, Charge, Kommission …');
        expect(screen.getAllByText('Werkstoffzeugnis').length).toBeGreaterThan(0);
        expect(sichtbareNummern()).toEqual(expect.arrayContaining(['LS-1001', 'WZ-2002', 'RE-3003']));
    });

    it('filtert auf Werkstoffzeugnisse', async () => {
        rendere();
        fireEvent.click(screen.getByRole('combobox', { name: 'Dokumenttyp' }));
        fireEvent.click(await screen.findByRole('option', { name: 'Werkstoffzeugnisse' }));
        expect(sichtbareNummern()).toEqual(['WZ-2002']);
    });

    it('fragt die Positionen erst nach 300 ms ab und vereinigt mit den Browser-Treffern', async () => {
        rendere();
        fireEvent.change(suchfeld(), { target: { value: 'LS' } });

        // Browser-Suche wirkt sofort
        expect(sichtbareNummern()).toEqual(['LS-1001']);
        await act(async () => { vi.advanceTimersByTime(299); });
        expect(positionssucheAufrufe(fetchMock)).toHaveLength(0);

        fireEvent.change(suchfeld(), { target: { value: 'S235' } });
        await act(async () => { vi.advanceTimersByTime(300); });
        expect(positionssucheAufrufe(fetchMock)).toHaveLength(1);
        expect(positionssucheAufrufe(fetchMock)[0][0]).toBe(`/api/lieferanten/${LIEFERANT_ID}/dokumente/positionssuche?q=S235`);

        // Zeugnis kommt über die Positionen dazu, mit Trefferzeile
        expect(await screen.findByText('WZ-2002')).toBeInTheDocument();
        expect(sichtbareNummern()).toEqual(['WZ-2002']);
        const zeile = screen.getByTestId('positions-treffer');
        expect(zeile).toHaveTextContent('Flachstahl 50x5 · S235JR · Charge 123456 und 2 weitere');
        expect(within(zeile).getByText('S235')).toBeInstanceOf(HTMLElement);
        expect(within(zeile).getByText('S235').tagName).toBe('MARK');
    });

    it('zeigt Browser-Treffer und Positionstreffer zusammen', async () => {
        fetchMock.mockImplementation((url: string) => Promise.resolve(
            url.includes('/positionssuche')
                ? antwort([{ dokumentId: 2, trefferText: 'Rundrohr RE 33,7', weitereTreffer: 0 }])
                : antwort([]),
        ));
        rendere();
        fireEvent.change(suchfeld(), { target: { value: 'RE' } });
        expect(sichtbareNummern()).toEqual(['RE-3003']);
        await act(async () => { vi.advanceTimersByTime(300); });
        expect(await screen.findByText('WZ-2002')).toBeInTheDocument();
        expect(sichtbareNummern()).toEqual(expect.arrayContaining(['RE-3003', 'WZ-2002']));
        expect(screen.getByText(/von 3 Dokumenten gefunden/).textContent).toContain('2');
    });

    it('schickt unter zwei Zeichen keine Positionssuche', async () => {
        rendere();
        fireEvent.change(suchfeld(), { target: { value: ' S ' } });
        await act(async () => { vi.advanceTimersByTime(1000); });
        expect(positionssucheAufrufe(fetchMock)).toHaveLength(0);
    });

    it('bricht eine laufende Anfrage ab, wenn weiter getippt wird', async () => {
        const signale: AbortSignal[] = [];
        fetchMock.mockImplementation((url: string, init?: RequestInit) => {
            if (!url.includes('/positionssuche')) return Promise.resolve(antwort([]));
            if (init?.signal) signale.push(init.signal);
            return new Promise<Response>((_resolve, reject) => {
                init?.signal?.addEventListener('abort', () => reject(new DOMException('abgebrochen', 'AbortError')));
            });
        });
        rendere();
        fireEvent.change(suchfeld(), { target: { value: 'Flach' } });
        await act(async () => { vi.advanceTimersByTime(300); });
        expect(signale).toHaveLength(1);
        fireEvent.change(suchfeld(), { target: { value: 'Flachstahl' } });
        expect(signale[0].aborted).toBe(true);
    });

    it('meldet einen Fehler der Positionssuche genau einmal – die Browser-Treffer bleiben', async () => {
        fetchMock.mockImplementation((url: string) => Promise.resolve(
            url.includes('/positionssuche') ? new Response(null, { status: 500 }) : antwort([]),
        ));
        rendere();
        fireEvent.change(suchfeld(), { target: { value: 'LS-1001' } });
        await act(async () => { vi.advanceTimersByTime(300); });
        expect(positionssucheAufrufe(fetchMock)).toHaveLength(1);
        expect(sichtbareNummern()).toEqual(['LS-1001']);
        expect(screen.queryByTestId('positions-treffer')).toBeNull();
        expect(await screen.findByText(POSITIONSSUCHE_FEHLER_TEXT)).toBeInTheDocument();

        // Weitere Fehler im selben Reiter: kein zweiter Toast
        fireEvent.change(suchfeld(), { target: { value: 'LS-100' } });
        await act(async () => { vi.advanceTimersByTime(300); });
        expect(positionssucheAufrufe(fetchMock)).toHaveLength(2);
        await act(async () => { await Promise.resolve(); });
        expect(screen.getAllByText(POSITIONSSUCHE_FEHLER_TEXT)).toHaveLength(1);
    });

    it('meldet auch ohne Anmeldung (401) einmal', async () => {
        fetchMock.mockImplementation((url: string) => (url.includes('/positionssuche')
            ? Promise.resolve(new Response(null, { status: 401 }))
            : Promise.resolve(antwort([]))));
        rendere();
        fireEvent.change(suchfeld(), { target: { value: 'S235' } });
        await act(async () => { vi.advanceTimersByTime(300); });
        expect(await screen.findByText(POSITIONSSUCHE_FEHLER_TEXT)).toBeInTheDocument();
    });

    it('meldet einen Netzwerkfehler einmal', async () => {
        fetchMock.mockImplementation((url: string) => (url.includes('/positionssuche')
            ? Promise.reject(new TypeError('Failed to fetch'))
            : Promise.resolve(antwort([]))));
        rendere();
        fireEvent.change(suchfeld(), { target: { value: 'S235' } });
        await act(async () => { vi.advanceTimersByTime(300); });
        expect(await screen.findByText(POSITIONSSUCHE_FEHLER_TEXT)).toBeInTheDocument();
    });

    it('wertet eine abgebrochene Anfrage nicht als Fehler', async () => {
        fetchMock.mockImplementation((url: string, init?: RequestInit) => {
            if (!url.includes('/positionssuche')) return Promise.resolve(antwort([]));
            return new Promise<Response>((_resolve, reject) => {
                init?.signal?.addEventListener('abort', () => reject(new DOMException('abgebrochen', 'AbortError')));
            });
        });
        rendere();
        fireEvent.change(suchfeld(), { target: { value: 'Flach' } });
        await act(async () => { vi.advanceTimersByTime(300); });
        fireEvent.change(suchfeld(), { target: { value: '' } });
        await act(async () => { await Promise.resolve(); await Promise.resolve(); });
        expect(screen.queryByText(POSITIONSSUCHE_FEHLER_TEXT)).toBeNull();
    });

    it('zeigt den Treffer auch für ein Zeugnis in einer Dokumentenkette', async () => {
        const ls = { ...dokument(1, 'LIEFERSCHEIN', 'LS-1001'), verknuepfteDokumente: [{ id: 2, typ: 'WERKSTOFFZEUGNIS' as const }] };
        const zeugnis = { ...dokument(2, 'WERKSTOFFZEUGNIS', 'WZ-2002'), verknuepfteDokumente: [{ id: 1, typ: 'LIEFERSCHEIN' as const }] };
        fetchMock.mockImplementation((url: string) => Promise.resolve(
            url.includes('/positionssuche')
                ? antwort([
                    { dokumentId: 1, trefferText: 'Flachstahl 50x5', weitereTreffer: 0 },
                    { dokumentId: 2, trefferText: 'Flachstahl 50x5 · S235JR', weitereTreffer: 0 },
                ])
                : antwort([]),
        ));
        rendere([ls, zeugnis]);
        fireEvent.change(suchfeld(), { target: { value: 'flachstahl' } });
        await act(async () => { vi.advanceTimersByTime(300); });
        expect(await screen.findByText(/Dokumentenketten \(1\)/)).toBeInTheDocument();
        expect(await screen.findAllByTestId('positions-treffer')).toHaveLength(2);
        expect(screen.getByText('Werkstoffzeugnis WZ-2002')).toBeInTheDocument();
    });
});

describe('LieferantDokumenteTab – Dokument einer Kette zuordnen', () => {
    let fetchMock: ReturnType<typeof vi.fn>;
    const zeugnisVorschlag = {
        dokument: {
            id: 2, typ: 'WERKSTOFFZEUGNIS', dokumentNummer: 'WZ-2002', dokumentDatum: '2026-09-01', betragBrutto: null, betragNetto: null,
            liefertermin: null, dateiname: 'WZ-2002.pdf', pdfUrl: '/pdf/WZ-2002',
        },
        lieferantName: 'Musterstahl GmbH', kettenDokumentId: 1, kettenDokumentTyp: 'LIEFERSCHEIN', kettenDokumentNummer: 'LS-1001',
        trefferquote: 88, sicher: true, eindeutig: true, gruende: ['Belegnummer wird genannt'], gehoertSchonZu: null,
    };

    beforeEach(() => {
        fetchMock = vi.fn((url: string) => {
            if (url.includes('/ketten-vorschlaege')) return Promise.resolve(antwort([zeugnisVorschlag]));
            if (url.includes('/ketten-verknuepfen')) return Promise.resolve(antwort({ success: true }));
            return Promise.resolve(antwort([]));
        });
        globalThis.fetch = fetchMock as unknown as typeof fetch;
    });

    afterEach(() => vi.restoreAllMocks());

    const aufrufe = (teil: string) => fetchMock.mock.calls.filter(([url]) => String(url).includes(teil));

    it('bietet „Zu Kette zuordnen“ an Einzeldokumenten, nicht bei Sonstigem', () => {
        rendere([...DOKUMENTE, dokument(4, 'SONSTIG', 'SO-4004')]);
        expect(screen.getByRole('button', { name: 'Werkstoffzeugnis WZ-2002 zu Kette zuordnen' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Lieferschein LS-1001 zu Kette zuordnen' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /SO-4004 zu Kette zuordnen/ })).not.toBeInTheDocument();
    });

    it('ordnet ein Einzeldokument zu und lädt die Dokumente neu', async () => {
        rendere();
        await userEvent.click(screen.getByRole('button', { name: 'Werkstoffzeugnis WZ-2002 zu Kette zuordnen' }));
        // Aus Sicht des Nutzers sucht dieses Dokument seine Bestellung
        const dialog = await screen.findByRole('dialog', { name: /Passende Bestellung suchen – Musterstahl GmbH/ });
        expect(within(dialog).getByRole('region', { name: 'Dieses Dokument' })).toBeInTheDocument();
        await within(dialog).findByText('Belegnummer wird genannt');
        expect(aufrufe('/ketten-vorschlaege')[0][0]).toBe('/api/bestellungen-uebersicht/ketten-vorschlaege?dokumentIds=2&alleLieferanten=false');
        // Vorschau des eigenen Dokuments über den Download des Lieferanten
        expect(within(dialog).getAllByTestId('pdf')[0]).toHaveTextContent(`/api/lieferanten/${LIEFERANT_ID}/dokumente/2/download`);

        await userEvent.click(within(dialog).getByRole('button', { name: 'Werkstoffzeugnis WZ-2002 gehört dazu' }));
        await waitFor(() => expect(aufrufe('/ketten-verknuepfen')).toHaveLength(1));
        expect(JSON.parse((aufrufe('/ketten-verknuepfen')[0][1] as RequestInit).body as string)).toEqual({ kettenDokumentId: 1, dokumentId: 2 });
        await waitFor(() => expect(screen.queryByRole('dialog', { name: /Passende Bestellung suchen/ })).not.toBeInTheDocument());
        await waitFor(() => expect(aufrufe(`/api/lieferanten/${LIEFERANT_ID}/dokumente`).length).toBeGreaterThan(0));
    });

    it('öffnet an einer Dokumentenkette die Suche mit allen Dokumenten der Kette', async () => {
        const ls = { ...dokument(1, 'LIEFERSCHEIN', 'LS-1001'), verknuepfteDokumente: [{ id: 3, typ: 'RECHNUNG' as const }] };
        const re = { ...dokument(3, 'RECHNUNG', 'RE-3003'), verknuepfteDokumente: [{ id: 1, typ: 'LIEFERSCHEIN' as const }] };
        rendere([ls, re, dokument(2, 'WERKSTOFFZEUGNIS', 'WZ-2002')]);
        await userEvent.click(screen.getByRole('button', { name: 'Dokument zur Kette RE-3003 hinzufügen' }));
        await screen.findByRole('dialog', { name: /Dokument zur Kette hinzufügen/ });
        await waitFor(() => expect(aufrufe('/ketten-vorschlaege')).toHaveLength(1));
        expect(aufrufe('/ketten-vorschlaege')[0][0]).toBe('/api/bestellungen-uebersicht/ketten-vorschlaege?dokumentIds=1&dokumentIds=3&alleLieferanten=false');

        await userEvent.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(screen.queryByRole('dialog', { name: /Dokument zur Kette hinzufügen/ })).not.toBeInTheDocument();
        expect(aufrufe('/ketten-verknuepfen')).toHaveLength(0);
    });
});

describe('LieferantDokumenteTab – Kette als gerade Linie', () => {
    beforeEach(() => {
        globalThis.fetch = vi.fn(() => Promise.resolve(antwort([]))) as unknown as typeof fetch;
    });

    afterEach(() => vi.restoreAllMocks());

    function mitDatum(id: number, typ: LieferantDokumentTyp, nummer: string, datum: string | undefined, verknuepft: Array<[number, LieferantDokumentTyp]>, extra: Partial<LieferantDokument> = {}): LieferantDokument {
        const basis = dokument(id, typ, nummer);
        return {
            ...basis,
            geschaeftsdaten: { ...basis.geschaeftsdaten, dokumentDatum: datum },
            verknuepfteDokumente: verknuepft.map(([refId, refTyp]) => ({ id: refId, typ: refTyp })),
            ...extra,
        };
    }

    it('zeigt alle Belege untereinander, das Zeugnis unter seinem Lieferschein, und öffnet per Klick die Details', async () => {
        const ab = mitDatum(10, 'AUFTRAGSBESTAETIGUNG', 'AB-55', '2026-03-14', []);
        const ls1 = mitDatum(11, 'LIEFERSCHEIN', 'LS-1', '2026-03-20', [[10, 'AUFTRAGSBESTAETIGUNG']], { uploadedByName: 'Max Mustermann' });
        const ls2 = mitDatum(12, 'LIEFERSCHEIN', 'LS-2', '2026-03-27', [[10, 'AUFTRAGSBESTAETIGUNG']]);
        // Zeugnis ohne Datum, Verknüpfung nur vom Zeugnis zum Lieferschein
        const zeugnis = mitDatum(13, 'WERKSTOFFZEUGNIS', '4107891', undefined, [[11, 'LIEFERSCHEIN']]);
        const re = mitDatum(14, 'RECHNUNG', 'R-900', '2026-04-02', [[12, 'LIEFERSCHEIN']], {
            geschaeftsdaten: { dokumentNummer: 'R-900', dokumentDatum: '2026-04-02', betragBrutto: 1250, referenzNummer: 'REF-77' },
        });
        rendere([re, zeugnis, ls2, ab, ls1]);

        const liste = screen.getByRole('list', { name: 'Belege der Kette R-900' });
        expect(within(liste).getAllByRole('listitem').map(z => z.textContent)).toEqual([
            expect.stringContaining('AB-55'),
            expect.stringContaining('LS-1'),
            expect.stringContaining('4107891'),
            expect.stringContaining('LS-2'),
            expect.stringContaining('R-900'),
        ]);
        // Keine waagrechten Kästen mehr, eine Linie mit fünf Punkten
        expect(liste.querySelectorAll('[data-punkt]')).toHaveLength(5);
        // Zeugnis ohne Belegdatum: Eingangsdatum mit Hinweis
        expect(within(liste).getByText('Eingang')).toBeInTheDocument();
        // Zusatzzeile mit Referenz und Erfasser
        expect(within(liste).getByText('Ref: REF-77')).toBeInTheDocument();
        expect(within(liste).getByText('Max Mustermann')).toBeInTheDocument();

        await userEvent.click(within(liste).getByRole('button', { name: /^Lieferschein LS-2/ }));
        expect(await screen.findByRole('dialog', { name: 'Dokument bearbeiten' })).toBeInTheDocument();
    });
});
