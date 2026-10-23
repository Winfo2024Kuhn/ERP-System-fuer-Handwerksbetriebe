import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';
import ArbeitsgangEditor from './ArbeitsgangEditor';

/** Abteilungen werden nur noch unter Einstellungen → Abteilungen & Rechte gepflegt. Nur Dummy-Daten. */

function stubbe(abteilungen: { id: number; name: string }[]) {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url === '/api/abteilungen') return new Response(JSON.stringify(abteilungen), { status: 200 });
        if (url === '/api/arbeitsgaenge') {
            return new Response(JSON.stringify([
                { id: 7, beschreibung: 'Montage Metallfassade', abteilungId: 4, stundensatz: 65, stundensatzJahr: new Date().getFullYear() },
            ]), { status: 200 });
        }
        return new Response('[]', { status: 200 });
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

function zeige() {
    render(
        <MemoryRouter>
            <ToastProvider><ConfirmProvider><ArbeitsgangEditor /></ConfirmProvider></ToastProvider>
        </MemoryRouter>,
    );
}

beforeEach(() => vi.unstubAllGlobals());

describe('ArbeitsgangEditor – Abteilungen nur noch zur Auswahl', () => {
    it('bietet kein Anlegen oder Löschen von Abteilungen mehr an, sondern verweist auf die Einstellungen', async () => {
        const fetchMock = stubbe([{ id: 4, name: 'Montage' }]);
        zeige();

        expect(await screen.findByText('Montage Metallfassade')).toBeInTheDocument();
        expect(screen.queryByPlaceholderText(/Neue Abteilung/)).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Abteilung anlegen/ })).not.toBeInTheDocument();
        expect(screen.queryByTitle('Abteilung löschen')).not.toBeInTheDocument();

        const link = screen.getByRole('link', { name: 'Einstellungen → Abteilungen & Rechte' });
        expect(link).toHaveAttribute('href', '/einstellungen#berechtigungen');
        expect(link.closest('p')).toHaveTextContent('Abteilungen legen Sie unter Einstellungen → Abteilungen & Rechte an, benennen sie dort um oder löschen sie.');

        // Arbeitsgänge anlegen bleibt auf der Seite.
        expect(screen.getByRole('textbox', { name: 'Bezeichnung' })).toBeInTheDocument();
        expect(fetchMock.mock.calls.every(([, init]) => (init?.method ?? 'GET') === 'GET')).toBe(true);
    });

    it('verlinkt auch im leeren Zustand auf die Einstellungen', async () => {
        stubbe([]);
        zeige();

        const leer = (await screen.findByText('Keine Abteilungen vorhanden')).parentElement!;
        expect(within(leer).getByRole('link', { name: 'Einstellungen → Abteilungen & Rechte' }))
            .toHaveAttribute('href', '/einstellungen#berechtigungen');
    });
});
