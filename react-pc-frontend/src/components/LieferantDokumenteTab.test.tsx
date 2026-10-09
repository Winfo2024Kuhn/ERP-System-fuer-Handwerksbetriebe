import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LieferantDokumenteTab, { POSITIONSSUCHE_FEHLER_TEXT } from './LieferantDokumenteTab';
import { ToastProvider } from './ui/toast';
import { ConfirmProvider } from './ui/confirm-dialog';
import type { LieferantDokument, LieferantDokumentTyp } from '../types';

vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ isAdmin: false }) }));

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
