import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ZeiterfassungAuswertung from './ZeiterfassungAuswertung';

const { toast } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn(), warning: vi.fn(), info: vi.fn() } }));
vi.mock('../components/ui/toast', () => ({ useToast: () => toast }));
// Projektauswahl direkt bestätigen – das Auswahlfenster selbst ist nicht Gegenstand dieses Tests.
vi.mock('../components/ProjectSelectModal', () => ({
    ProjectSelectModal: ({ onSelect }: { onSelect: (p: { id: number; bauvorhaben: string }) => void }) => (
        <button type="button" onClick={() => onSelect({ id: 12, bauvorhaben: 'Geländer Musterstraße' })}>Testprojekt übernehmen</button>
    ),
}));

const auswertung = {
    gesamtStunden: 7.5,
    anzahlBuchungen: 1,
    taetigkeiten: [{
        arbeitsgang: 'Montage',
        gesamtStunden: 7.5,
        anzahlBuchungen: 1,
        buchungen: [{ id: 1, mitarbeiterName: 'Max Mustermann', startDateTime: '2026-08-03T07:00:00', startZeit: '07:00', endeZeit: '14:30', dauerMinuten: 450 }],
    }],
};

const fetchMock = vi.fn();

beforeEach(() => {
    vi.clearAllMocks();
    fetchMock.mockImplementation(async (input: string) => {
        if (input === '/api/mitarbeiter') {
            return { ok: true, json: async () => [
                { id: 7, vorname: 'Max', nachname: 'Mustermann', aktiv: true },
                { id: 8, vorname: 'Erika', nachname: 'Ehemalig', aktiv: false },
            ] };
        }
        if (input.startsWith('/api/zeitverwaltung/auswertung/projekt/')) return { ok: true, json: async () => auswertung };
        return { ok: true, json: async () => [] };
    });
    vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => { vi.unstubAllGlobals(); });

async function projektWaehlen(user: ReturnType<typeof userEvent.setup>) {
    await user.click(screen.getByText('Projekt wählen...'));
    await user.click(screen.getByRole('button', { name: 'Testprojekt übernehmen' }));
}

async function datumSetzen(user: ReturnType<typeof userEvent.setup>, feld: string, datum: string) {
    await user.click(screen.getByRole('button', { name: feld }));
    await user.type(screen.getByRole('textbox', { name: 'Datum eingeben' }), `${datum}{Enter}`);
}

describe('Projektauswertung', () => {
    it('lädt ohne Filter alle Buchungen und zeigt Stunden mit Komma', async () => {
        const user = userEvent.setup();
        render(<ZeiterfassungAuswertung />);
        await projektWaehlen(user);
        await user.click(screen.getByRole('button', { name: /Auswertung laden/ }));

        await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/zeitverwaltung/auswertung/projekt/12'));
        // Gesamt, Gruppensumme und Zeile: 7,50 statt 7.50
        expect(await screen.findAllByText(/7,50h/)).toHaveLength(3);
        expect(screen.queryByText(/7\.50/)).not.toBeInTheDocument();
    });

    it('schickt Zeitraum und Mitarbeiter an Anzeige und PDF und bietet nur aktive Mitarbeiter an', async () => {
        const open = vi.spyOn(window, 'open').mockImplementation(() => null);
        const user = userEvent.setup();
        render(<ZeiterfassungAuswertung />);
        await projektWaehlen(user);

        await datumSetzen(user, 'Zeitraum von', '01.08.2026');
        await datumSetzen(user, 'Zeitraum bis', '31.08.2026');
        await user.click(screen.getByRole('combobox', { name: 'Mitarbeiter' }));
        expect(screen.queryByRole('option', { name: 'Erika Ehemalig' })).not.toBeInTheDocument();
        await user.click(await screen.findByRole('option', { name: 'Max Mustermann' }));

        await user.click(screen.getByRole('button', { name: /Auswertung laden/ }));
        await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/zeitverwaltung/auswertung/projekt/12?von=2026-08-01&bis=2026-08-31&mitarbeiterId=7'));

        await user.click(await screen.findByRole('button', { name: /PDF Export/ }));
        await user.click(screen.getByRole('button', { name: /PDF erstellen/ }));
        expect(open).toHaveBeenCalledWith(
            '/api/zeitverwaltung/auswertung/projekt/12/pdf?von=2026-08-01&bis=2026-08-31&mitarbeiterId=7&sortField=datum&sortDir=asc&groupBy=arbeitsgang',
            '_blank',
        );
        open.mockRestore();
    });

    it('lehnt einen Zeitraum ab, dessen Ende vor dem Anfang liegt', async () => {
        const user = userEvent.setup();
        render(<ZeiterfassungAuswertung />);
        await projektWaehlen(user);
        await datumSetzen(user, 'Zeitraum von', '31.08.2026');
        await datumSetzen(user, 'Zeitraum bis', '01.08.2026');
        await user.click(screen.getByRole('button', { name: /Auswertung laden/ }));

        expect(toast.error).toHaveBeenCalledWith('Das Ende des Zeitraums liegt vor dem Anfang.');
        expect(fetchMock.mock.calls.some(([url]) => String(url).startsWith('/api/zeitverwaltung/auswertung'))).toBe(false);
    });

    it('zeigt die Server-Meldung, wenn die Auswertung nicht geladen werden kann', async () => {
        fetchMock.mockImplementation(async (input: string) => input.startsWith('/api/zeitverwaltung/auswertung/')
            ? { ok: false, status: 400, json: async () => ({ status: 400, message: 'Ungültiger Zeitraum.' }) }
            : { ok: true, json: async () => [] });
        const user = userEvent.setup();
        render(<ZeiterfassungAuswertung />);
        await projektWaehlen(user);
        await user.click(screen.getByRole('button', { name: /Auswertung laden/ }));
        await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Ungültiger Zeitraum.'));
    });
});
