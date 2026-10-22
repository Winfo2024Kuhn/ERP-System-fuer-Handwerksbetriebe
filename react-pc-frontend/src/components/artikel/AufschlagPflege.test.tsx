import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AufschlagPflege } from './AufschlagPflege';
import { ToastProvider } from '../ui/toast';

/** Nur Dummy-Daten. */
const UEBERSICHT = {
    allgemein: 20,
    kategorien: [
        { id: 1, oberkategorieId: null, pfad: 'Stahl', prozent: 25, wirksamProzent: 25, wirksamQuelle: 'KATEGORIE', wirksamVon: 'Stahl' },
        { id: 2, oberkategorieId: 1, pfad: 'Stahl › Rohre', prozent: null, wirksamProzent: 25, wirksamQuelle: 'KATEGORIE', wirksamVon: 'Stahl' },
        { id: 3, oberkategorieId: null, pfad: 'Alu', prozent: null, wirksamProzent: 20, wirksamQuelle: 'ALLGEMEIN', wirksamVon: null },
    ],
};

let gesendet: { url: string; body: Record<string, unknown> }[];
let serverFehler: string | null;

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

beforeEach(() => {
    gesendet = [];
    serverFehler = null;
    vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
        if (init?.method === 'PUT') {
            gesendet.push({ url: String(url), body: JSON.parse(String(init.body)) });
            return serverFehler ? json({ message: serverFehler }, 400) : json(UEBERSICHT);
        }
        return json(UEBERSICHT);
    }));
});
afterEach(() => { vi.unstubAllGlobals(); });

const rendern = () => render(<ToastProvider><AufschlagPflege /></ToastProvider>);

describe('Aufschläge einstellen (Fenster in der Artikelübersicht)', { timeout: 30000 }, () => {
    it('lässt eine gespeicherte 0 beim Anklicken stehen: 0 Prozent ist ein eigener Wert, leer heißt „nicht eingestellt“', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json({ ...UEBERSICHT, allgemein: 0 })));
        const user = userEvent.setup();
        rendern();
        const feld = await screen.findByLabelText('Aufschlag in Prozent');
        expect(feld).toHaveValue('0');

        await user.click(feld);

        expect(feld).toHaveValue('0');
        expect(screen.getByRole('button', { name: 'Allgemeinen Aufschlag speichern' })).toBeDisabled();
    });

    it('zeigt den allgemeinen Aufschlag und je Kategorie den eigenen und was für ihre Artikel gilt', async () => {
        rendern();

        expect(await screen.findByLabelText('Aufschlag in Prozent')).toHaveValue('20');
        expect(within(screen.getByTestId('aufschlag-kategorie-1')).getByText('25 %')).toBeVisible();
        expect(within(screen.getByTestId('aufschlag-kategorie-2')).getByText('25 % (von „Stahl“)')).toBeVisible();
        expect(within(screen.getByTestId('aufschlag-kategorie-3')).getByText('20 % (allgemein)')).toBeVisible();
        expect(screen.getByLabelText('Aufschlag für Stahl › Rohre')).toHaveValue('');
    });

    it('speichert den allgemeinen Aufschlag mit Komma als Zahl; Speichern ist erst nach einer Änderung möglich', async () => {
        const user = userEvent.setup();
        rendern();
        const feld = await screen.findByLabelText('Aufschlag in Prozent');
        const speichern = screen.getByRole('button', { name: 'Allgemeinen Aufschlag speichern' });
        expect(speichern).toBeDisabled();

        await user.clear(feld);
        await user.type(feld, '12,5');
        await user.click(speichern);

        await waitFor(() => expect(gesendet).toHaveLength(1));
        expect(gesendet[0]).toEqual({ url: '/api/aufschlaege/allgemein', body: { prozent: 12.5 } });
        expect(await screen.findByText('Allgemeiner Aufschlag: 12,5 % gespeichert.')).toBeVisible();
    });

    it('speichert den Aufschlag einer Kategorie; leer heißt: kein eigener Aufschlag', async () => {
        const user = userEvent.setup();
        rendern();
        await screen.findByTestId('aufschlag-kategorien');

        const rohre = screen.getByLabelText('Aufschlag für Stahl › Rohre');
        await user.type(rohre, '30');
        await user.click(screen.getByRole('button', { name: 'Aufschlag für Stahl › Rohre speichern' }));
        await waitFor(() => expect(gesendet).toHaveLength(1));
        expect(gesendet[0]).toEqual({ url: '/api/aufschlaege/kategorien/2', body: { prozent: 30 } });

        const stahl = screen.getByLabelText('Aufschlag für Stahl');
        await user.clear(stahl);
        await user.click(screen.getByRole('button', { name: 'Aufschlag für Stahl speichern' }));
        await waitFor(() => expect(gesendet).toHaveLength(2));
        expect(gesendet[1]).toEqual({ url: '/api/aufschlaege/kategorien/1', body: { prozent: null } });
    });

    it('ein unmöglicher Wert wird nicht gesendet; ein Fehler des Servers steht im Klartext da', async () => {
        const user = userEvent.setup();
        rendern();
        const feld = await screen.findByLabelText('Aufschlag in Prozent');

        await user.clear(feld);
        await user.type(feld, '1000');
        await user.click(screen.getByRole('button', { name: 'Allgemeinen Aufschlag speichern' }));
        expect(gesendet).toHaveLength(0);

        await user.clear(feld);
        await user.type(feld, '50');
        serverFehler = 'Der Aufschlag muss zwischen 0 und 999,99 Prozent liegen.';
        await user.click(screen.getByRole('button', { name: 'Allgemeinen Aufschlag speichern' }));
        expect(await screen.findByText('Der Aufschlag muss zwischen 0 und 999,99 Prozent liegen.')).toBeVisible();
    });

    it('kann die Übersicht nicht geladen werden, steht der Grund da', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json({ message: 'Keine Berechtigung.' }, 403)));
        rendern();

        const meldungen = await screen.findAllByRole('alert');
        meldungen.forEach(m => expect(m).toHaveTextContent('Keine Berechtigung.'));
    });

    it('die Grenze ist 999,99 Prozent wie beim Aufschlag am Artikel', async () => {
        const user = userEvent.setup();
        rendern();
        const feld = await screen.findByLabelText('Aufschlag in Prozent');

        await user.clear(feld);
        await user.type(feld, '999,99');
        await user.click(screen.getByRole('button', { name: 'Allgemeinen Aufschlag speichern' }));

        await waitFor(() => expect(gesendet).toHaveLength(1));
        expect(gesendet[0].body).toEqual({ prozent: 999.99 });
    });

    it('der gesperrte Speichern-Knopf nennt den Grund', async () => {
        rendern();
        const speichern = await screen.findByRole('button', { name: 'Allgemeinen Aufschlag speichern' });

        expect(speichern).toBeDisabled();
        expect(speichern).toHaveAttribute('title', 'Nichts geändert');
    });

    it('ein Info-Button erklärt, was die Aufschläge im Hintergrund bewirken', async () => {
        const user = userEvent.setup();
        rendern();
        await user.click(await screen.findByRole('button', { name: /Mehr zu: Aufschläge/ }));

        expect(await screen.findByText(/erst der am Artikel, dann der der Kategorie, zuletzt der allgemeine/)).toBeVisible();
        expect(screen.getByText(/Bereits erstellte Dokumente und Aufschläge, die direkt am Artikel stehen, bleiben/)).toBeVisible();
    });
});
