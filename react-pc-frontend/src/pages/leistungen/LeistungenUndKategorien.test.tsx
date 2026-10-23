import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { ToastProvider } from '../../components/ui/toast';
import LeistungenUndKategorien from './LeistungenUndKategorien';

vi.mock('chart.js', () => {
    class Chart { destroy() {} }
    (Chart as unknown as { register: () => void }).register = () => {};
    return { Chart, registerables: [] };
});

const ORDNER = [
    { id: 1, bezeichnung: 'Metallbau', parentId: null, verrechnungseinheit: { name: 'LAUFENDE_METER', anzeigename: 'Laufende Meter' }, beschreibung: '', bildUrl: null, projektAnzahl: 5, leaf: false },
    { id: 2, bezeichnung: 'Geländer', parentId: 1, verrechnungseinheit: 'LAUFENDE_METER', beschreibung: '<p>DIN 18065</p>', bildUrl: null, projektAnzahl: 3, leaf: true },
    { id: 4, bezeichnung: 'Holzbau', parentId: null, verrechnungseinheit: 'QUADRATMETER', beschreibung: '', bildUrl: null, projektAnzahl: 0, leaf: true },
];
const LEISTUNGEN = [
    { id: 10, name: 'Geländer verzinkt', description: '<p>Flachstahl</p>', price: 149, unit: 'LAUFENDE_METER', folderId: 2 },
    { id: 11, name: 'Glasbrüstung', description: '<p>VSG</p>', price: 420, unit: 'LAUFENDE_METER', folderId: 2 },
];
const AUSWERTUNG = [
    { kategorieId: 1, bezeichnung: 'Metallbau', bildUrl: null, verrechnungseinheit: 'Laufende Meter', leaf: false, projektAnzahl: 14, stunden: 312.5, menge: 180, stundenProEinheit: 1.74, leistungAnzahl: 2 },
    { kategorieId: 4, bezeichnung: 'Holzbau', bildUrl: null, verrechnungseinheit: 'Quadratmeter', leaf: true, projektAnzahl: 0, stunden: 0, menge: 0, stundenProEinheit: null, leistungAnzahl: 0 },
];

type Aufruf = { url: string; methode: string; body?: unknown };
let aufrufe: Aufruf[];
let ladefehler: boolean;
let verwendung: unknown;

const antwort = (body: unknown, status = 200) => Promise.resolve({ ok: status < 400, status, json: () => Promise.resolve(body) } as Response);

function fetchStub(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
    const url = String(input);
    const methode = (init?.method ?? 'GET').toUpperCase();
    aufrufe.push({ url, methode, body: init?.body });
    if (ladefehler && url.startsWith('/api/produktkategorien?light')) return antwort({ message: 'Server nicht erreichbar.' }, 503);
    if (url === '/api/produktkategorien?light=true') return antwort(ORDNER);
    if (url === '/api/leistungen' && methode === 'GET') return antwort(LEISTUNGEN);
    if (url.startsWith('/api/produktkategorien/auswertung')) return antwort(url.includes('parentId=1') ? [{ ...AUSWERTUNG[0], kategorieId: 2, bezeichnung: 'Geländer' }] : AUSWERTUNG);
    if (/\/analyse/.test(url)) return antwort({ projektAnzahl: 0, durchschnittlicheZeit: 0, fixzeit: 0, steigung: 0, verrechnungseinheit: 'Laufende Meter', rQuadrat: 0, residualStdAbweichung: 0, datenpunkte: 0, arbeitsgangAnalysen: [], projekte: [] });
    if (url === '/api/produktkategorien/4/loeschpruefung') return antwort({ kategorieId: 4, bezeichnung: 'Holzbau', pfad: 'Holzbau', hatBezuege: false, unterordnerAnzahl: 0, leistungAnzahl: 0, projektAnzahl: 0, zeitbuchungAnzahl: 0, unterordner: [], leistungen: [], projekte: [] });
    if (/\/api\/leistungen\/\d+\/verwendung/.test(url)) return antwort(verwendung);
    if (url === '/api/produktkategorien' && methode === 'POST') return antwort({ ...ORDNER[2], id: 7, bezeichnung: 'Edelstahl', parentId: 2 }, 201);
    if (/\/api\/produktkategorien\/\d+$/.test(url) && methode === 'PUT') {
        const form = init?.body as FormData;
        return antwort({ ...ORDNER[1], bezeichnung: form.get('bezeichnung') });
    }
    if (/verschieben$/.test(url)) return antwort(ORDNER[2]);
    if (methode === 'DELETE') return Promise.resolve({ ok: true, status: 204, json: () => Promise.resolve({}) } as Response);
    return antwort([]);
}

function zeigeSeite(adresse: string) {
    const router = createMemoryRouter([
        { path: '/leistungen', element: <LeistungenUndKategorien /> },
        { path: '/projekte', element: <p>Projekte</p> },
    ], { initialEntries: [adresse] });
    render(<ToastProvider><ConfirmProvider><RouterProvider router={router} /></ConfirmProvider></ToastProvider>);
    return router;
}

const baum = () => screen.getByRole('tree', { name: 'Ordner' });

describe('Seite „Leistungen & Kategorien“', () => {
    beforeEach(() => {
        aufrufe = [];
        ladefehler = false;
        verwendung = { dokumentAnzahl: 0, dokumente: [] };
        vi.stubGlobal('fetch', vi.fn(fetchStub));
        URL.createObjectURL = vi.fn(() => 'blob:http://localhost/x');
        URL.revokeObjectURL = vi.fn();
    });
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt Titel mit Erklärung und die Übersicht; ein Klick führt in die Auswertung des Ordners', async () => {
        const router = zeigeSeite('/leistungen');
        expect(await screen.findByRole('heading', { name: 'Leistungen & Kategorien', level: 1 })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Mehr zu: Leistungen & Kategorien' })).toBeInTheDocument();
        const tabelle = await screen.findByRole('table', { name: 'Auswertung aller Hauptordner' });
        expect(within(tabelle).getByText('312,5')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Mehr zu: Jahr der Auswertung' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Mehr zu: Stunden je Einheit' })).toBeInTheDocument();

        await userEvent.click(within(tabelle).getByRole('button', { name: /Metallbau/ }));
        await waitFor(() => expect(router.state.location.search).toBe('?ordner=1&reiter=auswertung'));
        expect(await screen.findByText('312,5 Std.')).toBeInTheDocument();
        expect(await screen.findByRole('table', { name: 'Unterordner von „Metallbau“' })).toBeInTheDocument();
    });

    it('Ordner-Reiter: Erklärungen an Einheit und Beschreibung, Rückfrage bei ungespeicherten Änderungen, Speichern schickt alle Felder', async () => {
        const router = zeigeSeite('/leistungen?ordner=2&reiter=ordner');
        const name = await screen.findByLabelText('Name *');
        expect(screen.getByRole('button', { name: 'Mehr zu: Einheit des Ordners' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Mehr zu: Beschreibung des Ordners' })).toBeInTheDocument();
        expect(screen.getByText('Diese Kategorie hängt an 3 Projekten.')).toBeInTheDocument();

        await userEvent.clear(name);
        await userEvent.type(name, 'Geländer & Brüstungen');
        await userEvent.click(within(baum()).getByRole('treeitem', { name: /^Holzbau/ }));
        expect(await screen.findByRole('dialog', { name: 'Änderungen verwerfen?' })).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Weiter bearbeiten' }));
        expect(router.state.location.search).toBe('?ordner=2&reiter=ordner');

        await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText('Ordner „Geländer & Brüstungen“ gespeichert.')).toBeInTheDocument();
        const put = aufrufe.find((a) => a.methode === 'PUT')!;
        const form = put.body as FormData;
        expect(form.get('bezeichnung')).toBe('Geländer & Brüstungen');
        expect(form.get('verrechnungseinheit')).toBe('LAUFENDE_METER');
        expect(form.get('beschreibung')).toBe('<p>DIN 18065</p>');

        // Nach dem Speichern geht der Wechsel ohne Rückfrage
        await userEvent.click(within(baum()).getByRole('treeitem', { name: /^Holzbau/ }));
        await waitFor(() => expect(router.state.location.search).toBe('?ordner=4&reiter=ordner'));
    });

    it('legt einen Unterordner in einem Ordner mit Leistungen an – die Leistungen wandern mit', async () => {
        const router = zeigeSeite('/leistungen?ordner=2');
        await userEvent.click(await screen.findByRole('button', { name: 'Unterordner in „Geländer“ anlegen' }));
        expect(screen.getByText('Die 2 Leistungen aus „Geländer“ wandern in den neuen Unterordner.')).toBeInTheDocument();
        await userEvent.type(screen.getByRole('textbox', { name: 'Name des neuen Unterordners in „Geländer“' }), 'Edelstahl{Enter}');
        await waitFor(() => expect(router.state.location.search).toBe('?ordner=7'));
        const post = aufrufe.find((a) => a.methode === 'POST')!;
        const form = post.body as FormData;
        expect(form.get('bezeichnung')).toBe('Edelstahl');
        expect(form.get('parentId')).toBe('2');
        expect(form.get('leistungenMitnehmen')).toBe('true');
        expect(await screen.findByText('Ordner „Edelstahl“ angelegt – 2 Leistungen sind mitgewandert.')).toBeInTheDocument();
    });

    it('Ordner ohne Bezüge verschwindet sofort mit „Rückgängig“ statt Rückfrage', async () => {
        zeigeSeite('/leistungen?ordner=4');
        await userEvent.click((await screen.findAllByRole('button', { name: 'Weitere Aktionen für „Holzbau“' }))[0]);
        await userEvent.click(await screen.findByRole('menuitem', { name: 'Löschen …' }));
        expect(await screen.findByText('„Holzbau“ gelöscht.')).toBeInTheDocument();
        expect(within(baum()).queryByRole('treeitem', { name: /^Holzbau/ })).not.toBeInTheDocument();
        expect(aufrufe.some((a) => a.methode === 'DELETE')).toBe(false);
        await userEvent.click(screen.getByRole('button', { name: 'Rückgängig' }));
        expect(within(baum()).getByRole('treeitem', { name: /^Holzbau/ })).toBeInTheDocument();
    });

    it('Leistung in Dokumenten: Rückfrage mit Liste, „Trotzdem löschen“ schickt DELETE', async () => {
        verwendung = { dokumentAnzahl: 1, dokumente: [{ id: 77, dokumentNummer: 'AN-2026-031', typ: 'ANGEBOT', datum: '2026-03-02', projektId: 9, bauvorhaben: 'Neubau Mustermann' }] };
        zeigeSeite('/leistungen?ordner=2');
        await userEvent.click(await screen.findByRole('button', { name: '„Glasbrüstung“ löschen' }));
        const dialog = await screen.findByRole('dialog', { name: '„Glasbrüstung“ steckt in einem Dokument' });
        expect(within(dialog).getByText('AN-2026-031')).toBeInTheDocument();
        await userEvent.click(within(dialog).getByRole('button', { name: 'Trotzdem löschen' }));
        await waitFor(() => expect(aufrufe.some((a) => a.methode === 'DELETE' && a.url === '/api/leistungen/11')).toBe(true));
        expect(await screen.findByText('„Glasbrüstung“ gelöscht.')).toBeInTheDocument();
    });

    it('„Verschieben nach …“ mit Erklärung schickt den neuen Eltern-Ordner', async () => {
        zeigeSeite('/leistungen?ordner=4');
        await userEvent.click((await screen.findAllByRole('button', { name: 'Weitere Aktionen für „Holzbau“' }))[0]);
        await userEvent.click(await screen.findByRole('menuitem', { name: 'Verschieben nach …' }));
        const dialog = await screen.findByRole('dialog', { name: '„Holzbau“ verschieben' });
        expect(within(dialog).getByRole('button', { name: 'Mehr zu: Ordner verschieben' })).toBeInTheDocument();
        expect(within(dialog).getByRole('radio', { name: /^Geländer/ })).toBeDisabled();
        await userEvent.click(within(dialog).getByRole('radio', { name: /^Metallbau/ }));
        await userEvent.click(within(dialog).getByRole('button', { name: 'Hierhin verschieben' }));
        await waitFor(() => expect(aufrufe.find((a) => a.url === '/api/produktkategorien/4/verschieben')?.body).toBe(JSON.stringify({ parentId: 1 })));
    });

    it('Neue Leistung ist in Ordnern mit Unterordnern gesperrt – mit sichtbarem Grund', async () => {
        zeigeSeite('/leistungen?ordner=1');
        expect(await screen.findByRole('button', { name: 'Neue Leistung' })).toBeDisabled();
        expect(screen.getByText(/Neue Leistungen kommen in den untersten Ordner/)).toBeInTheDocument();
        // Leistungen der Unterordner stehen mit Ordnerpfad da
        expect(screen.getByText(/149,00 € \/ m · Geländer/)).toBeInTheDocument();
    });

    it('zeigt einen Ladefehler mit „Erneut versuchen“', async () => {
        ladefehler = true;
        zeigeSeite('/leistungen');
        expect(await screen.findByText('Server nicht erreichbar.')).toBeInTheDocument();
        ladefehler = false;
        await userEvent.click(screen.getByRole('button', { name: /Erneut versuchen/ }));
        expect(await screen.findByRole('tree', { name: 'Ordner' })).toBeInTheDocument();
    });
});
