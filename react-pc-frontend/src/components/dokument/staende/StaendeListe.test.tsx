import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../ui/toast';
import { ConfirmProvider } from '../../ui/confirm-dialog';
import { StaendeListe } from './StaendeListe';
import { jsonAntwort, staendeSeite, standEintrag } from './staendeTestdaten';
import * as kanal from '../../../lib/dokumentChannel';
import type { DokumentBezugFuerStaende } from './staendeTypen';

const DOK_ID = 12;
const fetchMock = vi.fn();

function zeige(dokument: DokumentBezugFuerStaende = { id: DOK_ID, projektId: 4 }, imEditor = false, onWiederhergestellt?: () => void) {
    return render(
        <ToastProvider>
            <ConfirmProvider>
                <StaendeListe dokumentId={DOK_ID} dokumentNummer="AG-2026/11/00012" dokument={dokument} imEditor={imEditor} onWiederhergestellt={onWiederhergestellt} />
            </ConfirmProvider>
        </ToastProvider>,
    );
}

beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('StaendeListe', () => {
    it('zeigt beim Laden Platzhalter statt einer leeren Fläche', async () => {
        fetchMock.mockReturnValue(new Promise(() => {}));
        zeige();
        expect(screen.getByLabelText('Stände werden geladen')).toBeInTheDocument();
        expect(fetchMock).toHaveBeenCalledWith(`/api/ausgangs-dokumente/${DOK_ID}/staende?page=0&size=25`, expect.anything());
    });

    it('zeigt den Leer-Zustand, solange es noch gar keinen Stand gibt', async () => {
        fetchMock.mockResolvedValue(jsonAntwort(200, staendeSeite({ staende: [], gesamt: 0, offeneAenderungen: null })));
        zeige();
        expect(await screen.findByText(/Noch kein Stand\. Ein Stand entsteht, sobald Sie das Dokument drucken, als PDF speichern oder per E-Mail verschicken\./)).toBeInTheDocument();
    });

    it('zeigt einen Fehler-Zustand mit „Erneut versuchen“ und einen Toast', async () => {
        fetchMock.mockResolvedValueOnce(jsonAntwort(500, { message: 'Stände konnten nicht geladen werden.' }));
        zeige();
        const fehler = (await screen.findByRole('button', { name: 'Erneut versuchen' })).closest('[role="alert"]') as HTMLElement;
        expect(within(fehler).getByText('Stände konnten nicht geladen werden.')).toBeInTheDocument();
        // Toast zusätzlich
        expect(await screen.findAllByText('Stände konnten nicht geladen werden.')).toHaveLength(2);

        fetchMock.mockResolvedValueOnce(jsonAntwort(200, staendeSeite()));
        await userEvent.click(within(fehler).getByRole('button', { name: 'Erneut versuchen' }));
        expect(await screen.findByText('Stand 3')).toBeInTheDocument();
    });

    it('erklärt Stände und Wiederherstellen je einmal im Kopf, nicht an jeder Karte', async () => {
        fetchMock.mockResolvedValue(jsonAntwort(200, staendeSeite({ staende: [standEintrag(), standEintrag({ id: 2, nummer: 2 })] })));
        zeige();
        await screen.findByText('Stand 2');
        expect(screen.getAllByRole('button', { name: 'Mehr zu: Stände' })).toHaveLength(1);
        expect(screen.getAllByRole('button', { name: 'Mehr zu: Stand wiederherstellen' })).toHaveLength(1);
    });

    it('zeigt offene Änderungen seit dem letzten Export oben', async () => {
        fetchMock.mockResolvedValue(jsonAntwort(200, staendeSeite({ offeneAenderungen: ['Preis Pos. 2: 120,00 € → 95,00 €'] })));
        zeige();
        const kasten = await screen.findByRole('region', { name: 'Seit dem letzten Export geändert' });
        expect(within(kasten).getByText('Preis Pos. 2: 120,00 € → 95,00 €')).toBeInTheDocument();
    });

    it('lädt mit „Mehr laden“ die nächste Seite nach und zeigt den Zähler', async () => {
        const erste = Array.from({ length: 25 }, (_, i) => standEintrag({ id: 100 - i, nummer: 30 - i }));
        fetchMock.mockResolvedValueOnce(jsonAntwort(200, staendeSeite({ staende: erste, gesamt: 30, hatMehr: true })));
        zeige();
        expect(await screen.findByText('25 von 30 Ständen angezeigt')).toBeInTheDocument();

        const zweite = Array.from({ length: 5 }, (_, i) => standEintrag({ id: 75 - i, nummer: 5 - i }));
        fetchMock.mockResolvedValueOnce(jsonAntwort(200, staendeSeite({ staende: zweite, gesamt: 30, seite: 1, hatMehr: false })));
        await userEvent.click(screen.getByRole('button', { name: 'Mehr laden' }));

        expect(await screen.findByText('30 von 30 Ständen angezeigt')).toBeInTheDocument();
        expect(fetchMock).toHaveBeenLastCalledWith(`/api/ausgangs-dokumente/${DOK_ID}/staende?page=1&size=25`, expect.anything());
        expect(screen.queryByRole('button', { name: 'Mehr laden' })).not.toBeInTheDocument();
    });

    it('stellt nach Bestätigung wieder her, meldet es und lädt die Liste neu', async () => {
        const meldung = vi.spyOn(kanal, 'notifyInhaltZurueckgeholt');
        fetchMock.mockImplementation((url: string, init?: RequestInit) => {
            // Antwort = volles Dokument; Projekt und Anfrage daraus gehen an die anderen Tabs.
            if (init?.method === 'POST') return Promise.resolve(jsonAntwort(200, { id: DOK_ID, projektId: 9, anfrageId: 2 }));
            return Promise.resolve(jsonAntwort(200, staendeSeite({ offeneAenderungen: ['Betreff geändert'] })));
        });
        const onWiederhergestellt = vi.fn();
        zeige(undefined, false, onWiederhergestellt);
        await userEvent.click(await screen.findByRole('button', { name: 'Stand 3 wiederherstellen' }));

        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByText(/Stand 3 vom 12\.11\.2026, 14:32 Uhr/)).toBeInTheDocument();
        expect(within(dialog).getByText(/Gespeicherte, noch nicht exportierte Änderungen sichert das Programm vorher als eigenen Stand\./)).toBeInTheDocument();
        await userEvent.click(within(dialog).getByRole('button', { name: 'Stand wiederherstellen' }));

        await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
            `/api/ausgangs-dokumente/${DOK_ID}/staende/3/wiederherstellen`, expect.objectContaining({ method: 'POST' }),
        ));
        expect(await screen.findByText('Stand 3 wurde wiederhergestellt.')).toBeInTheDocument();
        expect(meldung).toHaveBeenCalledWith({ dokumentId: DOK_ID, projektId: 9, anfrageId: 2, standNummer: 3 });
        // Die aufrufende Seite (Projekt) lädt ihre Dokumentliste neu.
        expect(onWiederhergestellt).toHaveBeenCalledTimes(1);
        await waitFor(() => expect(fetchMock.mock.calls.filter(([u]) => String(u).includes('/staende?page=0')).length).toBe(2));
    });

    it('nimmt Projekt und Anfrage aus dem Dokument, wenn die Antwort sie nicht enthält', async () => {
        const meldung = vi.spyOn(kanal, 'notifyInhaltZurueckgeholt');
        fetchMock.mockImplementation((_url: string, init?: RequestInit) =>
            Promise.resolve(init?.method === 'POST' ? jsonAntwort(200, { id: DOK_ID }) : jsonAntwort(200, staendeSeite())));
        zeige({ id: DOK_ID, projektId: 4, anfrageId: 6 });
        await userEvent.click(await screen.findByRole('button', { name: 'Stand 3 wiederherstellen' }));
        await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Stand wiederherstellen' }));

        await waitFor(() => expect(meldung).toHaveBeenCalledWith({ dokumentId: DOK_ID, projektId: 4, anfrageId: 6, standNummer: 3 }));
    });

    it('nennt ohne offene Änderungen keine Sicherung im Dialog; Abbrechen sendet nichts', async () => {
        fetchMock.mockResolvedValue(jsonAntwort(200, staendeSeite({ offeneAenderungen: [] })));
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Stand 3 wiederherstellen' }));
        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).queryByText(/als eigenen Stand/)).not.toBeInTheDocument();
        await userEvent.click(within(dialog).getByRole('button', { name: 'Abbrechen' }));
        expect(fetchMock.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === 'POST')).toBe(false);
    });

    it('zeigt die Meldung des Servers als Toast, wenn jemand anderes gerade bearbeitet (409)', async () => {
        const meldung = vi.spyOn(kanal, 'notifyInhaltZurueckgeholt');
        fetchMock.mockImplementation((_url: string, init?: RequestInit) => {
            if (init?.method === 'POST') {
                return Promise.resolve(jsonAntwort(409, { message: 'Erika Musterfrau bearbeitet das Dokument gerade. Bitte später erneut versuchen.' }));
            }
            return Promise.resolve(jsonAntwort(200, staendeSeite()));
        });
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: 'Stand 3 wiederherstellen' }));
        await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Stand wiederherstellen' }));

        expect(await screen.findByText('Erika Musterfrau bearbeitet das Dokument gerade. Bitte später erneut versuchen.')).toBeInTheDocument();
        expect(meldung).not.toHaveBeenCalled();
    });

    it('im Editor meldet der Editor das Neuladen selbst, die Liste toastet keinen Erfolg', async () => {
        fetchMock.mockImplementation((_url: string, init?: RequestInit) =>
            Promise.resolve(init?.method === 'POST' ? jsonAntwort(200, { id: DOK_ID }) : jsonAntwort(200, staendeSeite())));
        zeige({ id: DOK_ID }, true);
        await userEvent.click(await screen.findByRole('button', { name: 'Stand 3 wiederherstellen' }));
        await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Stand wiederherstellen' }));
        await waitFor(() => expect(fetchMock.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === 'POST')).toBe(true));
        expect(screen.queryByText('Stand 3 wurde wiederhergestellt.')).not.toBeInTheDocument();
    });

    it('sperrt Wiederherstellen, wenn die Liste das Dokument als nicht bearbeitbar meldet', async () => {
        fetchMock.mockResolvedValue(jsonAntwort(200, staendeSeite({ dokumentBearbeitbar: false })));
        zeige({ id: DOK_ID });
        const knopf = await screen.findByRole('button', { name: 'Stand 3 wiederherstellen' });
        expect(knopf).toBeDisabled();
        expect(knopf).toHaveAccessibleDescription('Das Dokument lässt sich nicht mehr ändern (gebucht, angenommen oder storniert). Ältere Stände können Sie nur ansehen.');
    });
});
