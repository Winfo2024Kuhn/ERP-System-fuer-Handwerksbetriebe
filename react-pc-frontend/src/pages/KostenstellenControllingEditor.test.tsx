import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';
import KostenstellenControllingEditor from './KostenstellenControllingEditor';

/** Seite Kostenstellen: Knöpfe nur mit dem Recht „Kostenstellen verwalten“, Gelöschte wiederherstellen. Nur Dummy-Daten. */

const aktiv = {
    id: 1, bezeichnung: 'Werkstatt', typ: 'GEMEINKOSTEN', beschreibung: 'Strom und Miete',
    istFixkosten: true, istInvestition: false, summeDiesesJahr: 1200, summeVorjahr: 1000, anzahlDiesesJahr: 3,
};
const musterlager = {
    id: 7, bezeichnung: 'Musterlager', typ: 'LAGER', beschreibung: 'Material auf Vorrat',
    istFixkosten: false, istInvestition: true, aktiv: false, sortierung: 2,
};

interface Stand { darf: boolean; geloescht: typeof musterlager[]; postAntwort?: Response }

function stubbe(stand: Stand) {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const pfad = String(input).split('?')[0];
        const methode = init?.method ?? 'GET';
        if (pfad === '/api/firma/kostenstellen/berechtigung') return new Response(JSON.stringify({ darfAendern: stand.darf }));
        if (pfad === '/api/bestellungen-uebersicht/kostenstellen/auswertung') return new Response(JSON.stringify([aktiv]));
        if (pfad === '/api/firma/kostenstellen/geloescht') return new Response(JSON.stringify(stand.geloescht));
        if (pfad === '/api/firma/kostenstellen/7/wiederherstellen' && methode === 'POST') {
            stand.geloescht = [];
            return new Response(JSON.stringify({ ...musterlager, aktiv: true }));
        }
        if (pfad === '/api/firma/kostenstellen' && methode === 'POST') {
            return stand.postAntwort ?? new Response(JSON.stringify({ ...musterlager, aktiv: true }));
        }
        return new Response('[]');
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

function zeige() {
    render(
        <MemoryRouter>
            <ToastProvider><ConfirmProvider><KostenstellenControllingEditor /></ConfirmProvider></ToastProvider>
        </MemoryRouter>,
    );
}

const aufrufe = (fetchMock: ReturnType<typeof stubbe>, pfad: string) =>
    fetchMock.mock.calls.filter(([url]) => String(url).split('?')[0] === pfad);

beforeEach(() => vi.unstubAllGlobals());

describe('KostenstellenControllingEditor', () => {
    it('zeigt ohne Recht die Kosten, aber keine Knöpfe zum Ändern und fragt keine gelöschten ab', async () => {
        const fetchMock = stubbe({ darf: false, geloescht: [musterlager] });
        zeige();

        expect(await screen.findByText('Werkstatt')).toBeInTheDocument();
        await waitFor(() => expect(aufrufe(fetchMock, '/api/firma/kostenstellen/berechtigung')).toHaveLength(1));
        expect(screen.queryByRole('button', { name: /Neue Kostenstelle/ })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Standard anlegen/ })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Werkstatt bearbeiten' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Werkstatt löschen' })).not.toBeInTheDocument();
        expect(screen.queryByText('Gelöschte Kostenstellen')).not.toBeInTheDocument();
        expect(aufrufe(fetchMock, '/api/firma/kostenstellen/geloescht')).toHaveLength(0);
    });

    it('holt mit Recht eine gelöschte Kostenstelle zurück und lädt beide Listen neu', async () => {
        const fetchMock = stubbe({ darf: true, geloescht: [musterlager] });
        zeige();

        expect(await screen.findByRole('button', { name: /Neue Kostenstelle/ })).toBeInTheDocument();
        expect(await screen.findByRole('button', { name: 'Werkstatt bearbeiten' })).toBeInTheDocument();
        const bereich = await screen.findByTestId('geloeschte-kostenstellen');
        const umschalter = within(bereich).getByRole('button', { name: /^Gelöschte Kostenstellen/ });
        expect(umschalter).toHaveAttribute('aria-expanded', 'false');
        expect(umschalter).toHaveTextContent('1');
        expect(within(bereich).queryByText('Musterlager')).not.toBeInTheDocument();

        fireEvent.click(umschalter);
        expect(within(bereich).getByText('Musterlager')).toBeInTheDocument();
        expect(within(bereich).getByText('Lager (Investitionen)')).toBeInTheDocument();
        expect(within(bereich).getByText('Material auf Vorrat')).toBeInTheDocument();

        const auswertungVorher = aufrufe(fetchMock, '/api/bestellungen-uebersicht/kostenstellen/auswertung').length;
        fireEvent.click(within(bereich).getByRole('button', { name: 'Musterlager wiederherstellen' }));

        expect(await screen.findByText('Kostenstelle wiederhergestellt')).toBeInTheDocument();
        expect(aufrufe(fetchMock, '/api/firma/kostenstellen/7/wiederherstellen')[0][1]).toMatchObject({ method: 'POST' });
        await waitFor(() => expect(screen.queryByTestId('geloeschte-kostenstellen')).not.toBeInTheDocument());
        expect(aufrufe(fetchMock, '/api/bestellungen-uebersicht/kostenstellen/auswertung').length).toBeGreaterThan(auswertungVorher);
        expect(aufrufe(fetchMock, '/api/firma/kostenstellen/geloescht').length).toBeGreaterThanOrEqual(2);
    });

    it('zeigt die Meldung des Servers, wenn der Name schon vergeben ist, und lässt den Dialog offen', async () => {
        stubbe({
            darf: true, geloescht: [],
            postAntwort: new Response(JSON.stringify({ status: 409, message: 'Eine Kostenstelle mit diesem Namen gibt es schon.' }), { status: 409 }),
        });
        zeige();

        fireEvent.click(await screen.findByRole('button', { name: /Neue Kostenstelle/ }));
        fireEvent.change(screen.getByLabelText('Bezeichnung *'), { target: { value: 'Werkstatt' } });
        fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));

        expect(await screen.findByText('Eine Kostenstelle mit diesem Namen gibt es schon.')).toBeInTheDocument();
        expect(screen.getByRole('dialog')).toBeInTheDocument();
        expect(screen.queryByText('Fehler beim Speichern')).not.toBeInTheDocument();
    });

    it('nimmt den Ersatztext, wenn der Server keine Meldung mitschickt', async () => {
        stubbe({ darf: true, geloescht: [], postAntwort: new Response('kaputt', { status: 500 }) });
        zeige();

        fireEvent.click(await screen.findByRole('button', { name: /Neue Kostenstelle/ }));
        fireEvent.change(screen.getByLabelText('Bezeichnung *'), { target: { value: 'Musterlager' } });
        fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));

        expect(await screen.findByText('Speichern hat nicht geklappt.')).toBeInTheDocument();
    });

    it('sendet beim Anlegen nur die Felder der Kostenstelle und bestätigt mit Toast', async () => {
        const fetchMock = stubbe({ darf: true, geloescht: [] });
        zeige();

        fireEvent.click(await screen.findByRole('button', { name: /Neue Kostenstelle/ }));
        fireEvent.change(screen.getByLabelText('Bezeichnung *'), { target: { value: '  Musterlager ' } });
        fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));

        expect(await screen.findByText('Kostenstelle gespeichert')).toBeInTheDocument();
        const [, init] = aufrufe(fetchMock, '/api/firma/kostenstellen').find(([, i]) => i?.method === 'POST')!;
        expect(JSON.parse(String(init!.body))).toEqual({
            bezeichnung: 'Musterlager', typ: 'GEMEINKOSTEN', beschreibung: null,
            istFixkosten: true, istInvestition: false, aktiv: true, sortierung: 2,
        });
    });
});
