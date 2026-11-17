import { forwardRef, useEffect, useImperativeHandle } from 'react';
import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import * as kanal from '../lib/dokumentChannel';
import { notifyInhaltZurueckgeholt } from '../lib/dokumentChannel';
import DocumentEditorPage from './DocumentEditorPage';

/**
 * Stände auf der Editor-Seite: Ansicht eines Stands im eigenen Tab (ohne Sperre,
 * schreibgeschützt, Band mit Wiederherstellen) und Neuladen eines offenen Editors,
 * wenn ein Stand zurückgeholt wurde. Der Editor selbst ist gemockt (wie in
 * DocumentEditorPage.test.tsx). Nur Dummy-Daten (DSGVO).
 */

const zustand = vi.hoisted(() => ({ mounts: 0, ungespeichert: false }));

vi.mock('../components/DocumentEditor', () => ({
    default: forwardRef(function MockDocumentEditor(props: Record<string, unknown>, ref: unknown) {
        useImperativeHandle(ref as never, () => ({
            speichernFuerFreigabe: async () => {},
            hatUngespeicherteAenderungen: () => zustand.ungespeichert,
        }));
        useEffect(() => {
            zustand.mounts += 1;
        }, []);
        const stand = props.standAnsicht as { nummer: number } | undefined;
        return (
            <div
                data-testid="mock-editor"
                data-readonly={String(!!props.readOnly)}
                data-stand={stand ? String(stand.nummer) : ''}
            />
        );
    }),
}));

const STAND = {
    id: 3, nummer: 3, art: 'EXPORT', festgehaltenAm: '2026-11-12T14:32:00', festgehaltenVon: 'Max Mustermann',
    bearbeitetVon: [], betragNetto: 950, aenderungen: ['Erste Fassung'], wiederhergestelltAusNummer: null,
    aktuell: false, pdfVorhanden: true, betreff: 'Geländer Edelstahl', datum: '2026-11-12', mwstSatz: 19,
    zahlungszielTage: 14, rechnungsadresseOverride: null, positionenJson: '{"blocks":[]}', htmlInhalt: '',
    dokumentBearbeitbar: true,
};
const DOKUMENT = { id: 1, dokumentNummer: 'AG-2026/11/00001', typ: 'ANGEBOT', gebucht: false, storniert: false, bearbeitbar: true, projektId: 5 };

function json(status: number, body: unknown) {
    return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));
}

function lockOk() {
    return json(200, {
        status: 'ACQUIRED', holderUserId: 1, holderDisplayName: 'Max Mustermann',
        acquiredAt: new Date().toISOString(), lastHeartbeatAt: new Date().toISOString(),
    });
}

interface Szenario { stand?: number; standTeil?: Record<string, unknown>; wiederherstellen?: number }

function stubFetch(s: Szenario = {}) {
    const mock = vi.fn((url: string, init?: RequestInit) => {
        const methode = init?.method ?? 'GET';
        if (url.includes('/api/datensatz-locks/')) return methode === 'DELETE' ? Promise.resolve(new Response(null, { status: 204 })) : lockOk();
        if (url === '/api/ausgangs-dokumente/1/staende/3/wiederherstellen') {
            return (s.wiederherstellen ?? 200) === 200
                ? json(200, DOKUMENT)
                : json(s.wiederherstellen!, { message: 'Erika Musterfrau bearbeitet das Dokument gerade.' });
        }
        if (url === '/api/ausgangs-dokumente/1/staende/3') {
            return (s.stand ?? 200) === 200 ? json(200, { ...STAND, ...s.standTeil }) : json(s.stand!, { message: 'Diesen Stand gibt es nicht.' });
        }
        if (url.startsWith('/api/ausgangs-dokumente/1/staende?')) {
            return json(200, { staende: [], gesamt: 1, seite: 0, seitenGroesse: 1, hatMehr: true, offeneAenderungen: [] });
        }
        if (url === '/api/ausgangs-dokumente/1') return json(200, DOKUMENT);
        return Promise.resolve(new Response(null, { status: 404 }));
    });
    vi.stubGlobal('fetch', mock);
    return mock;
}

function Adresse() {
    const ort = useLocation();
    return <div data-testid="adresse">{ort.pathname + ort.search}</div>;
}

function zeige(pfad: string) {
    return render(
        <MemoryRouter initialEntries={[pfad]}>
            <ToastProvider>
                <ConfirmProvider>
                    <DocumentEditorPage />
                    <Adresse />
                </ConfirmProvider>
            </ToastProvider>
        </MemoryRouter>,
    );
}

const lockAufrufe = (mock: ReturnType<typeof stubFetch>) => mock.mock.calls.filter(([u]) => String(u).includes('/api/datensatz-locks/')).length;

beforeEach(() => {
    zustand.mounts = 0;
    zustand.ungespeichert = false;
});
afterEach(() => {
    // Erst abbauen (gibt die Sperre frei), dann fetch zurücksetzen.
    cleanup();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('DocumentEditorPage – Ansicht eines Stands', () => {
    it('zeigt den Stand schreibgeschützt mit Band und holt keine Sperre', async () => {
        const mock = stubFetch();
        zeige('/dokument-editor?dokumentId=1&stand=3');

        const editor = await screen.findByTestId('mock-editor');
        expect(editor).toHaveAttribute('data-readonly', 'true');
        expect(editor).toHaveAttribute('data-stand', '3');
        expect(screen.getByText('Sie sehen Stand 3 vom 12.11.2026, 14:32 Uhr, exportiert von Max Mustermann. Dieser Stand ist eingefroren.')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Zum aktuellen Stand' })).toHaveAttribute('href', '/dokument-editor?dokumentId=1');
        expect(screen.getByRole('button', { name: 'Stand 3 wiederherstellen' })).toBeEnabled();
        expect(lockAufrufe(mock)).toBe(0);
        // Ob wiederhergestellt werden darf, steht im Stand selbst: kein eigenes Laden des Dokuments.
        expect(mock.mock.calls.some(([u]) => u === '/api/ausgangs-dokumente/1')).toBe(false);
    });

    it('meldet einen fehlenden Stand per Toast und im Band', async () => {
        stubFetch({ stand: 404 });
        zeige('/dokument-editor?dokumentId=1&stand=3');

        expect(await screen.findAllByText('Diesen Stand gibt es nicht.')).toHaveLength(2);
        expect(screen.queryByTestId('mock-editor')).not.toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Zum aktuellen Stand' })).toBeInTheDocument();
    });

    it('sperrt Wiederherstellen, wenn das Dokument nicht mehr bearbeitbar ist', async () => {
        stubFetch({ standTeil: { dokumentBearbeitbar: false } });
        zeige('/dokument-editor?dokumentId=1&stand=3');
        const knopf = await screen.findByRole('button', { name: 'Stand 3 wiederherstellen' });
        await waitFor(() => expect(knopf).toBeDisabled());
        expect(knopf).toHaveAccessibleDescription('Das Dokument lässt sich nicht mehr ändern (gebucht, angenommen oder storniert). Ältere Stände können Sie nur ansehen.');
    });

    it('stellt nach Bestätigung wieder her und wechselt zum aktuellen Stand', async () => {
        const meldung = vi.spyOn(kanal, 'notifyInhaltZurueckgeholt');
        const mock = stubFetch();
        zeige('/dokument-editor?dokumentId=1&stand=3');
        await userEvent.click(await screen.findByRole('button', { name: 'Stand 3 wiederherstellen' }));
        const dialog = await screen.findByRole('dialog');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Stand wiederherstellen' }));

        await waitFor(() => expect(screen.getByTestId('adresse')).toHaveTextContent('/dokument-editor?dokumentId=1'));
        expect(screen.getByTestId('adresse').textContent).not.toContain('stand=');
        expect(await screen.findByText('Stand 3 wurde wiederhergestellt. Das Dokument ist neu geladen.')).toBeInTheDocument();
        // Projekt aus der Antwort des Servers, damit die Projektseite in anderen Tabs neu lädt.
        expect(meldung).toHaveBeenCalledWith({ dokumentId: 1, projektId: 5, anfrageId: undefined, standNummer: 3 });
        await waitFor(() => expect(screen.getByTestId('mock-editor')).toHaveAttribute('data-stand', ''));
        await waitFor(() => expect(lockAufrufe(mock)).toBeGreaterThan(0));
    });

    it('bleibt bei einem Fehler des Servers in der Ansicht und zeigt die Meldung', async () => {
        stubFetch({ wiederherstellen: 409 });
        zeige('/dokument-editor?dokumentId=1&stand=3');
        await userEvent.click(await screen.findByRole('button', { name: 'Stand 3 wiederherstellen' }));
        await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Stand wiederherstellen' }));

        expect(await screen.findByText('Erika Musterfrau bearbeitet das Dokument gerade.')).toBeInTheDocument();
        expect(screen.getByTestId('adresse')).toHaveTextContent('stand=3');
    });
});

describe('DocumentEditorPage – Stand wurde wiederhergestellt', () => {
    it('lädt den Editor desselben Dokuments neu und sagt das', async () => {
        stubFetch();
        zeige('/dokument-editor?dokumentId=1');
        await screen.findByTestId('mock-editor');
        await waitFor(() => expect(screen.getByTestId('mock-editor')).toHaveAttribute('data-readonly', 'false'));
        const vorher = zustand.mounts;

        act(() => notifyInhaltZurueckgeholt({ dokumentId: 1, standNummer: 3 }));

        expect(await screen.findByText('Stand 3 wurde wiederhergestellt. Das Dokument ist neu geladen.')).toBeInTheDocument();
        expect(zustand.mounts).toBe(vorher + 1);
    });

    it('warnt, wenn dabei ungespeicherte Eingaben in diesem Fenster verloren gehen', async () => {
        stubFetch();
        zeige('/dokument-editor?dokumentId=1');
        await screen.findByTestId('mock-editor');
        zustand.ungespeichert = true;
        const vorher = zustand.mounts;

        act(() => notifyInhaltZurueckgeholt({ dokumentId: 1, standNummer: 3 }));

        expect(await screen.findByText('Stand 3 wurde wiederhergestellt. Ihre letzten, noch nicht gespeicherten Eingaben in diesem Fenster wurden verworfen.')).toBeInTheDocument();
        expect(screen.queryByText('Stand 3 wurde wiederhergestellt. Das Dokument ist neu geladen.')).not.toBeInTheDocument();
        expect(zustand.mounts).toBe(vorher + 1);
    });

    it('lässt Editoren anderer Dokumente in Ruhe', async () => {
        stubFetch();
        zeige('/dokument-editor?dokumentId=1');
        await screen.findByTestId('mock-editor');
        const vorher = zustand.mounts;

        act(() => notifyInhaltZurueckgeholt({ dokumentId: 2, standNummer: 3 }));

        expect(zustand.mounts).toBe(vorher);
        expect(screen.queryByText(/wurde wiederhergestellt/)).not.toBeInTheDocument();
    });
});
