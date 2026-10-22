import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WerkstoffPflege } from './WerkstoffPflege';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';

/** Nur Dummy-Daten. */
const BAUSTAHL = { id: 1, name: 'S235JR', anzeigename: 'Baustahl', werkstattname: 'Schwarzstahl', dichte: 7.85, werkstoffnorm: 'EN 10025', verzinkungsgeeignet: true, pulverbeschichtungsgeeignet: true, beschichtungshinweis: null, artikelAnzahl: 12 };
const EDELSTAHL = { id: 2, name: '1.4301', anzeigename: null, werkstattname: 'V2A', dichte: null, werkstoffnorm: null, verzinkungsgeeignet: false, pulverbeschichtungsgeeignet: false, beschichtungshinweis: 'Wird nicht verzinkt', artikelAnzahl: 0 };

let liste: typeof BAUSTAHL[];
let gesendet: { methode: string; url: string; body?: Record<string, unknown> }[];
let serverFehler: { status: number; message: string } | null;

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

beforeEach(() => {
    liste = [BAUSTAHL, EDELSTAHL as unknown as typeof BAUSTAHL];
    gesendet = [];
    serverFehler = null;
    vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
        const methode = init?.method ?? 'GET';
        if (methode !== 'GET') {
            gesendet.push({ methode, url: String(url), body: init?.body ? JSON.parse(String(init.body)) : undefined });
            if (serverFehler) return json({ message: serverFehler.message }, serverFehler.status);
            return methode === 'DELETE' ? new Response(null, { status: 204 }) : json(BAUSTAHL, methode === 'POST' ? 201 : 200);
        }
        return json(liste);
    }));
});
afterEach(() => { vi.unstubAllGlobals(); });

const rendern = () => render(<ToastProvider><ConfirmProvider><WerkstoffPflege /></ConfirmProvider></ToastProvider>);

describe('Werkstoffe pflegen', { timeout: 30000 }, () => {
    it('zeigt die Werkstoffe mit Werkstattname, Dichte, Oberfläche und Zahl der Artikel; Suche findet auch über den Werkstattnamen', async () => {
        const user = userEvent.setup();
        rendern();

        const tabelle = await screen.findByTestId('werkstoff-liste');
        const zeilen = within(tabelle).getAllByRole('row');
        expect(zeilen).toHaveLength(3);
        expect(within(zeilen[1]).getByText('Baustahl')).toBeVisible();
        expect(within(zeilen[1]).getByText('7,85 kg/dm³')).toBeVisible();
        expect(within(zeilen[1]).getByText('verzinken, pulvern')).toBeVisible();
        expect(within(zeilen[1]).getByText('12')).toBeVisible();
        expect(within(zeilen[2]).getByText('V2A')).toBeVisible();

        await user.type(screen.getByRole('textbox', { name: 'Werkstoff suchen' }), 'v2a');
        expect(within(screen.getByTestId('werkstoff-liste')).getAllByRole('row')).toHaveLength(2);
        expect(screen.queryByText('Baustahl')).toBeNull();
    });

    it('legt einen neuen Werkstoff an; Name ist Pflicht, die Dichte wird als Zahl gesendet', async () => {
        const user = userEvent.setup();
        rendern();
        await screen.findByTestId('werkstoff-liste');

        await user.click(screen.getByRole('button', { name: /Neuer Werkstoff/ }));
        const speichern = screen.getByRole('button', { name: 'Speichern' });
        expect(speichern).toBeDisabled();
        await user.type(screen.getByLabelText('Name (technisch) *'), 'EN AW-6060');
        await user.type(screen.getByLabelText('Werkstattname'), 'Alu');
        await user.type(screen.getByLabelText('Dichte (kg/dm³)'), '2,7');
        await user.click(screen.getByLabelText('Pulverbeschichten geht'));
        await user.click(speichern);

        await waitFor(() => expect(gesendet).toHaveLength(1));
        expect(gesendet[0]).toMatchObject({ methode: 'POST', url: '/api/werkstoffe' });
        expect(gesendet[0].body).toMatchObject({ name: 'EN AW-6060', werkstattname: 'Alu', dichte: 2.7, verzinkungsgeeignet: false, pulverbeschichtungsgeeignet: true });
        expect(await screen.findByText('Der Werkstoff wurde angelegt.')).toBeVisible();
        expect(screen.queryByTestId('werkstoff-formular')).toBeNull();
    });

    it('ändert einen Werkstoff mit seinen bisherigen Werten; ein Fehler des Servers steht im Formular', async () => {
        const user = userEvent.setup();
        rendern();
        await screen.findByTestId('werkstoff-liste');

        await user.click(screen.getByRole('button', { name: 'Baustahl ändern' }));
        expect(screen.getByLabelText('Name (technisch) *')).toHaveValue('S235JR');
        expect(screen.getByLabelText('Dichte (kg/dm³)')).toHaveValue('7,85');
        expect(screen.getByLabelText('Feuerverzinken geht')).toBeChecked();
        serverFehler = { status: 409, message: 'Einen Werkstoff mit dem Namen „S355“ gibt es schon.' };
        const name = screen.getByLabelText('Name (technisch) *');
        await user.clear(name);
        await user.type(name, 'S355');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        // Der Grund steht im Formular und zusätzlich als Meldung oben rechts
        const meldungen = await screen.findAllByRole('alert');
        expect(meldungen.length).toBeGreaterThanOrEqual(2);
        meldungen.forEach(m => expect(m).toHaveTextContent('Einen Werkstoff mit dem Namen „S355“ gibt es schon.'));
        expect(gesendet[0]).toMatchObject({ methode: 'PUT', url: '/api/werkstoffe/1' });
        expect(screen.getByTestId('werkstoff-formular')).toBeVisible();
    });

    it('löscht erst nach Rückfrage; ein benutzter Werkstoff meldet den Grund im Klartext', async () => {
        const user = userEvent.setup();
        rendern();
        await screen.findByTestId('werkstoff-liste');

        await user.click(screen.getByRole('button', { name: '1.4301 löschen' }));
        await user.click(await screen.findByRole('button', { name: 'Abbrechen' }));
        expect(gesendet).toHaveLength(0);

        serverFehler = { status: 409, message: 'Dieser Werkstoff wird von 12 Artikeln benutzt und kann nicht gelöscht werden.' };
        await user.click(screen.getByRole('button', { name: 'Baustahl löschen' }));
        await user.click(await screen.findByRole('button', { name: 'Löschen' }));

        expect(await screen.findByText(/wird von 12 Artikeln benutzt/)).toBeVisible();
        expect(gesendet).toEqual([{ methode: 'DELETE', url: '/api/werkstoffe/1', body: undefined }]);
    });

    it('kann die Liste nicht geladen werden, steht der Grund da', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json({ message: 'Nur für Administratoren.' }, 403)));
        rendern();

        const meldungen = await screen.findAllByRole('alert');
        meldungen.forEach(m => expect(m).toHaveTextContent('Nur für Administratoren.'));
    });

    it('die Dichte steht in kg/dm³ da; ein Info-Button erklärt, dass das Programm damit noch nichts rechnet', async () => {
        const user = userEvent.setup();
        rendern();
        expect(await screen.findByText('7,85 kg/dm³')).toBeVisible();

        await user.click(screen.getByRole('button', { name: 'Neuer Werkstoff' }));
        await user.click(await screen.findByRole('button', { name: /Mehr zu: Dichte/ }));

        expect(await screen.findByText('Was passiert im Hintergrund?')).toBeVisible();
        expect(screen.getByText(/noch nichts aus/)).toBeVisible();
    });

    it('eine unmögliche Dichte wird vor dem Senden abgelehnt', async () => {
        const user = userEvent.setup();
        rendern();
        await user.click(await screen.findByRole('button', { name: 'Neuer Werkstoff' }));
        await user.type(screen.getByLabelText('Name (technisch) *'), 'X');
        await user.type(screen.getByLabelText('Dichte (kg/dm³)'), '78,5');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        expect((await screen.findAllByRole('alert'))[0]).toHaveTextContent(/Dichte/);
        expect(gesendet).toHaveLength(0);
    });

    it('Sonderzeichen im Namen gehen unverändert als Text an den Server', async () => {
        const user = userEvent.setup();
        rendern();
        await user.click(await screen.findByRole('button', { name: 'Neuer Werkstoff' }));
        await user.click(screen.getByLabelText('Name (technisch) *'));
        await user.paste('<img src=x onerror=alert(1)>');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(gesendet).toHaveLength(1));
        expect(gesendet[0].body?.name).toBe('<img src=x onerror=alert(1)>');
    });

    it('meldet der Umgebung nach Anlegen, Speichern und Löschen, dass sich etwas geändert hat, aber nicht beim bloßen Ansehen', async () => {
        const user = userEvent.setup();
        const onGeaendert = vi.fn();
        render(<ToastProvider><ConfirmProvider><WerkstoffPflege onGeaendert={onGeaendert} /></ConfirmProvider></ToastProvider>);
        await screen.findByText('Baustahl');
        expect(onGeaendert).not.toHaveBeenCalled();

        await user.click(screen.getByRole('button', { name: 'Neuer Werkstoff' }));
        await user.type(screen.getByLabelText('Name (technisch) *'), 'X');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(onGeaendert).toHaveBeenCalledTimes(1));

        await user.click(await screen.findByRole('button', { name: '1.4301 löschen' }));
        await user.click(await screen.findByRole('button', { name: 'Löschen', exact: true }));
        await waitFor(() => expect(onGeaendert).toHaveBeenCalledTimes(2));
    });

    it('bei offenem Formular gibt es genau einen Primär-Knopf; der gesperrte Speichern-Knopf nennt den Grund', async () => {
        const user = userEvent.setup();
        rendern();
        await user.click(await screen.findByRole('button', { name: 'Neuer Werkstoff' }));

        expect(screen.queryByRole('button', { name: 'Neuer Werkstoff' })).toBeNull();
        const speichern = screen.getByRole('button', { name: 'Speichern' });
        expect(speichern).toBeDisabled();
        expect(speichern).toHaveAttribute('title', 'Bitte zuerst einen Namen eingeben');

        await user.type(screen.getByLabelText('Name (technisch) *'), 'X');
        expect(speichern).toBeEnabled();
        expect(speichern).not.toHaveAttribute('title');
    });

    it('ein Info-Button erklärt, was die Eignung für die Oberfläche bewirkt', async () => {
        const user = userEvent.setup();
        rendern();
        await user.click(await screen.findByRole('button', { name: 'Neuer Werkstoff' }));
        await user.click(screen.getByRole('button', { name: /Mehr zu: Eignung für die Oberfläche/ }));

        expect(await screen.findByText(/Filtern „verzinkbar“ und „pulverbeschichtbar“/)).toBeVisible();
    });

    it('eine unmögliche Dichte erscheint auch als Meldung oben rechts', async () => {
        const user = userEvent.setup();
        rendern();
        await user.click(await screen.findByRole('button', { name: 'Neuer Werkstoff' }));
        await user.type(screen.getByLabelText('Name (technisch) *'), 'X');
        await user.type(screen.getByLabelText('Dichte (kg/dm³)'), '78,5');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        const meldungen = await screen.findAllByRole('alert');
        expect(meldungen.length).toBeGreaterThanOrEqual(2);
    });
});
