import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { setzeTelefonBerechtigungZurueck } from '../telefon/useTelefonBerechtigung';
import { LieferantAnsprechpartnerTab } from './LieferantAnsprechpartnerTab';
import type { LieferantAnsprechpartner } from './ansprechpartnerEntwurf';

/** Dummy-Daten (DSGVO): Max Mustermann, Erika Musterfrau. */
const leer = { anrede: null, vorname: null, aufgabe: null, telefon: null, mobiltelefon: null, email: null, notiz: null };
const MAX: LieferantAnsprechpartner = {
    ...leer, id: 1, anrede: 'HERR', vorname: 'Max', nachname: 'Mustermann', aufgabe: 'Außendienst',
    mobiltelefon: '0171 1234567', email: 'max@muster.de', notiz: 'Montags nicht erreichbar',
};
const ERIKA: LieferantAnsprechpartner = { ...leer, id: 2, vorname: 'Erika', nachname: 'Musterfrau', telefon: '0511 123-12' };

function antwort(body: unknown, status = 200): Response {
    return { ok: status < 400, status, json: () => Promise.resolve(body) } as Response;
}

function stubbe(liste: LieferantAnsprechpartner[] | 'fehler', darfTelefon = false) {
    const fetchMock = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
        if (url === '/api/telefon/berechtigung') return antwort({ darfTelefonSehen: darfTelefon });
        if (init?.method === 'POST') return antwort({ ...leer, id: 9, ...JSON.parse(String(init.body)) }, 201);
        if (init?.method === 'DELETE') return antwort(null, 204);
        if (liste === 'fehler') return antwort({ message: 'Lieferant nicht gefunden.' }, 404);
        return antwort(liste);
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

function zeige() {
    const onAnzahl = vi.fn();
    render(
        <ToastProvider>
            <ConfirmProvider>
                <LieferantAnsprechpartnerTab lieferantId={7} onAnzahl={onAnzahl} />
            </ConfirmProvider>
        </ToastProvider>
    );
    return onAnzahl;
}

beforeEach(() => setzeTelefonBerechtigungZurueck());
afterEach(() => vi.unstubAllGlobals());

describe('LieferantAnsprechpartnerTab', () => {
    it('zeigt erst den Ladezustand, dann den Leer-Zustand mit Knopf zum Anlegen', async () => {
        stubbe([]);
        const onAnzahl = zeige();
        expect(screen.getByText('Ansprechpartner werden geladen…')).toBeInTheDocument();
        expect(await screen.findByText('Noch keine Ansprechpartner.')).toBeInTheDocument();
        expect(screen.getAllByRole('button', { name: 'Ansprechpartner hinzufügen' })).toHaveLength(1);
        expect(onAnzahl).toHaveBeenLastCalledWith(0);
    });

    it('zeigt Name, Aufgabe, Nummern, E-Mail und Notiz je Person', async () => {
        stubbe([MAX, ERIKA]);
        zeige();
        const max = await screen.findByRole('listitem', { name: 'Herr Max Mustermann' });
        const m = within(max);
        expect(m.getByText('Außendienst')).toBeInTheDocument();
        expect(m.getByRole('link', { name: '0171 1234567' })).toHaveAttribute('href', 'tel:0171 1234567');
        expect(m.getByRole('link', { name: 'max@muster.de' })).toHaveAttribute('href', 'mailto:max@muster.de');
        expect(m.getByText('Montags nicht erreichbar')).toBeInTheDocument();
        expect(m.getByRole('button', { name: 'Max Mustermann bearbeiten' })).toHaveAttribute('title', 'Ansprechpartner bearbeiten');
        expect(m.getByRole('button', { name: 'Max Mustermann löschen' })).toHaveAttribute('title', 'Ansprechpartner löschen');
        expect(within(screen.getByRole('listitem', { name: 'Erika Musterfrau' })).getByRole('link', { name: '0511 123-12' })).toBeInTheDocument();
    });

    it('bietet mit Telefon-Recht das Anrufen über die FRITZ!Box an', async () => {
        stubbe([MAX], true);
        zeige();
        expect(await screen.findByRole('button', { name: 'Max Mustermann anrufen' })).toBeInTheDocument();
    });

    it('erklärt per Info-Knopf, was mit den Nummern passiert', async () => {
        const user = userEvent.setup();
        stubbe([MAX]);
        zeige();
        await user.click(await screen.findByRole('button', { name: 'Mehr zu: Ansprechpartner' }));
        expect(await screen.findByText(/kennt auch die Anruferkennung/)).toBeInTheDocument();
        expect(screen.getByText(/Frühere Anrufe bleiben beim Lieferanten/)).toBeInTheDocument();
    });

    it('legt an und zählt den Reiter hoch', async () => {
        const user = userEvent.setup();
        const fetchMock = stubbe([MAX]);
        const onAnzahl = zeige();
        await screen.findByRole('listitem', { name: 'Herr Max Mustermann' });
        expect(onAnzahl).toHaveBeenLastCalledWith(1);

        await user.click(screen.getByRole('button', { name: 'Ansprechpartner hinzufügen' }));
        const dialog = screen.getByRole('dialog', { name: 'Ansprechpartner hinzufügen' });
        await user.type(within(dialog).getByLabelText('Nachname *'), 'Musterfrau');
        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        expect(await screen.findByRole('listitem', { name: 'Musterfrau' })).toBeInTheDocument();
        await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
        expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(true);
        expect(onAnzahl).toHaveBeenLastCalledWith(2);
    });

    it('löscht nach Rückfrage und zählt herunter', async () => {
        const user = userEvent.setup();
        stubbe([MAX]);
        const onAnzahl = zeige();
        await user.click(await screen.findByRole('button', { name: 'Max Mustermann löschen' }));
        await user.click(await screen.findByRole('button', { name: 'Ansprechpartner löschen' }));

        expect(await screen.findByText('Noch keine Ansprechpartner.')).toBeInTheDocument();
        expect(onAnzahl).toHaveBeenLastCalledWith(0);
    });

    it('zeigt beim Ladefehler einen eigenen Zustand mit „Erneut laden“', async () => {
        const user = userEvent.setup();
        const fetchMock = stubbe('fehler');
        zeige();
        expect(await screen.findByText('Ansprechpartner konnten nicht geladen werden.')).toBeInTheDocument();
        expect(screen.getByText('Lieferant nicht gefunden.')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Erneut laden' }));
        await waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => url === '/api/lieferanten/7/ansprechpartner')).toHaveLength(2));
    });
});
