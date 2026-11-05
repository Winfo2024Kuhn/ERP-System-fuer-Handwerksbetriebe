import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../components/ui/toast';
import ReportCentre from './ReportCentre';

const starteDownload = vi.hoisted(() => vi.fn());
vi.mock('../lib/herunterladen', () => ({ starteDownload }));

const ZEITKONTEN = [
    { mitarbeiterId: 1, mitarbeiterName: 'Max Mustermann', fuehrtZeitkonto: true, istGeschaeftsfuehrer: false, aktuell: {} },
];

function antwort(status: number, inhalt: unknown, kopf: Record<string, string> = {}) {
    return Promise.resolve({
        ok: status < 300, status, headers: new Headers(kopf),
        json: () => Promise.resolve(inhalt),
        blob: () => Promise.resolve(new Blob(['%PDF-1.7'], { type: 'application/pdf' })),
    });
}

beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:zeitbericht');
    URL.revokeObjectURL = vi.fn();
    global.fetch = vi.fn((url: string) => {
        if (url === '/api/zeitverwaltung/zeitkonten') return antwort(200, ZEITKONTEN);
        if (url.startsWith('/api/zeitverwaltung/berichte/mitarbeiter/pdf')) {
            return antwort(200, null, { 'Content-Disposition': 'attachment; filename="Zeitbericht_2026-10-01_bis_2026-10-31.pdf"' });
        }
        return antwort(404, null);
    }) as unknown as typeof fetch;
});
afterEach(() => vi.restoreAllMocks());

const seite = () => render(<ToastProvider><ReportCentre /></ToastProvider>);

describe('ReportCentre', () => {
    it('zeigt Überschrift, Knopf und einen freundlichen Leerzustand', () => {
        seite();
        expect(screen.getByRole('heading', { name: /Report Centre/i })).toBeInTheDocument();
        expect(screen.getByText('Berichte für Steuerberater und Mitarbeiter als PDF erstellen')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Neuer Bericht' })).toBeInTheDocument();
        expect(screen.getByText('Noch kein Bericht erstellt')).toBeInTheDocument();
    });

    it('nimmt ein erstelltes PDF in „Zuletzt erstellt“ auf und gibt es beim Verlassen frei', async () => {
        const { unmount } = seite();
        fireEvent.click(screen.getByRole('button', { name: 'Neuer Bericht' }));
        fireEvent.click(screen.getByRole('button', { name: /Bericht für Mitarbeiter/ }));
        fireEvent.click(screen.getByRole('button', { name: 'Weiter' }));
        fireEvent.click(await screen.findByRole('checkbox', { name: 'Max Mustermann' }));
        fireEvent.click(screen.getByRole('button', { name: 'Weiter' }));
        fireEvent.click(screen.getByRole('button', { name: 'PDF erstellen' }));

        const name = 'Zeitbericht_2026-10-01_bis_2026-10-31.pdf';
        await waitFor(() => expect(starteDownload).toHaveBeenCalledWith('blob:zeitbericht', name));
        fireEvent.keyDown(screen.getByRole('dialog', { name: 'Neuer Bericht' }), { key: 'Escape' });

        expect(screen.queryByText('Noch kein Bericht erstellt')).not.toBeInTheDocument();
        expect(screen.getByText(name)).toBeInTheDocument();
        expect(screen.getByText(/Bericht für Mitarbeiter · 1 KB/)).toBeInTheDocument();

        const oeffnen = vi.spyOn(window, 'open').mockReturnValue(null);
        fireEvent.click(screen.getByRole('button', { name: `${name} öffnen` }));
        expect(oeffnen).toHaveBeenCalledWith('blob:zeitbericht', '_blank', 'noopener');
        starteDownload.mockClear();
        fireEvent.click(screen.getByRole('button', { name: `${name} herunterladen` }));
        expect(starteDownload).toHaveBeenCalledWith('blob:zeitbericht', name);

        unmount();
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:zeitbericht');
    });
});
