import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../ui/toast';
import { OpenFileLauncherKarte } from './OpenFileLauncherKarte';
import { DateiOrdnerSection } from './DateiOrdnerSection';

const ADRESSE = '\\\\server\\zeichnungen';
const FREIGABE = '\\\\erp-dateien.example.ts.net\\CADdrawings';
const WINDOWS = 'Für Windows herunterladen';
const MAC = 'Befehl für Mac erstellen';
const MAC_NEU = 'Neuen Befehl erstellen';
const BEFEHL = `/bin/bash -c "$(curl -fSsL 'https://erp.example.test/openfile-launcher/mac/AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcd')"`;
const BEFEHL_URL = `/api/openfile-launcher/einrichtung/mac/befehl?adresse=${encodeURIComponent(window.location.origin)}`;

function zeigeKarte(gespeicherteAdresse: string, ungespeichert = false, containerFreigabe?: string | null) {
    return render(
        <ToastProvider>
            <OpenFileLauncherKarte
                gespeicherteAdresse={gespeicherteAdresse}
                ungespeichert={ungespeichert}
                containerFreigabe={containerFreigabe}
            />
        </ToastProvider>,
    );
}

function befehlAntwort(befehl = BEFEHL, gueltigMinuten = 10) {
    return new Response(JSON.stringify({ befehl, gueltigMinuten }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
    });
}

function dateiAntwort(body: string, kopf?: string) {
    return new Response(body, {
        status: 200,
        headers: kopf ? { 'Content-Disposition': kopf } : {},
    });
}

describe('OpenFileLauncherKarte', () => {
    let fetchMock: ReturnType<typeof vi.fn>;
    let klickMock: ReturnType<typeof vi.spyOn>;
    let downloadName: string | null;

    beforeEach(() => {
        fetchMock = vi.fn();
        vi.stubGlobal('fetch', fetchMock);
        downloadName = null;
        URL.createObjectURL = vi.fn(() => 'blob:test');
        URL.revokeObjectURL = vi.fn();
        klickMock = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
            downloadName = this.download;
        });
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        klickMock.mockRestore();
    });

    it('sperrt beide Knöpfe ohne Netzwerk-Adresse und sagt, was zu tun ist', () => {
        zeigeKarte('');

        expect(screen.getByRole('button', { name: WINDOWS })).toBeDisabled();
        expect(screen.getByRole('button', { name: MAC })).toBeDisabled();
        expect(screen.getByText(/Tragen Sie oben zuerst die Netzwerk-Adresse ein \(z\. B\. \\\\server\\zeichnungen\) und speichern Sie\./)).toBeInTheDocument();
    });

    it('sperrt die Knöpfe auch bei einem Ordner ohne Netzwerk-Adresse (z. B. C:\\Zeichnungen)', () => {
        zeigeKarte('C:\\Zeichnungen');

        expect(screen.getByRole('button', { name: WINDOWS })).toBeDisabled();
        expect(screen.getByText(/Tragen Sie oben zuerst die Netzwerk-Adresse ein/)).toBeInTheDocument();
    });

    it('gibt die Knöpfe frei, sobald eine Netzwerk-Adresse gespeichert ist', () => {
        zeigeKarte(ADRESSE);

        expect(screen.getByRole('button', { name: WINDOWS })).toBeEnabled();
        expect(screen.getByRole('button', { name: MAC })).toBeEnabled();
        expect(screen.queryByRole('status')).not.toBeInTheDocument();
    });

    it('weist über http darauf hin, dass sich das Hilfsprogramm so nicht selbst aktualisiert', () => {
        expect(window.location.protocol).toBe('http:');
        zeigeKarte(ADRESSE);

        expect(screen.getByText(/Sie nutzen das Programm gerade über http\./)).toBeInTheDocument();
        expect(screen.getByText(/den Befehl für den Mac gibt es nur über https\. Richten Sie es über die\s+https-Adresse ein \(z\. B\. die Tailscale-Adresse\)/)).toBeInTheDocument();
    });

    it('zeigt den http-Hinweis nicht, wenn das Programm über https läuft', () => {
        vi.stubGlobal('location', { ...window.location, protocol: 'https:', origin: 'https://erp.example.test' });
        zeigeKarte(ADRESSE);

        expect(screen.queryByText(/Sie nutzen das Programm gerade über http/)).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: WINDOWS })).toBeEnabled();
    });

    it('lädt über https mit der https-Adresse dieses Programms herunter', async () => {
        vi.stubGlobal('location', { ...window.location, protocol: 'https:', origin: 'https://erp.example.test' });
        fetchMock.mockResolvedValue(dateiAntwort('@echo off'));
        zeigeKarte(ADRESSE);

        fireEvent.click(screen.getByRole('button', { name: WINDOWS }));

        await waitFor(() => expect(klickMock).toHaveBeenCalledTimes(1));
        expect(fetchMock.mock.calls[0][0]).toBe(
            `/api/openfile-launcher/einrichtung/windows?adresse=${encodeURIComponent('https://erp.example.test')}`,
        );
    });

    it('sagt bei gespeicherter Adresse der Ordner-Freigabe, dass die Zugangsdaten schon in der Einrichtung stecken', () => {
        // Groß-/Kleinschreibung und Leerzeichen zählen nicht.
        zeigeKarte(' \\\\ERP-DATEIEN.example.ts.net\\CADdrawings ', false, FREIGABE);

        expect(screen.getByText(
            'Benutzer und Passwort für den Ordner stecken schon in der Einrichtung – am PC muss niemand etwas eintippen.',
        )).toBeInTheDocument();
        expect(screen.queryByText(/Erst oben die Adresse der Ordner-Freigabe/)).not.toBeInTheDocument();
    });

    it('bittet erst um die Adresse der Ordner-Freigabe, solange eine andere gespeichert ist', () => {
        zeigeKarte(ADRESSE, false, FREIGABE);

        expect(screen.getByText(
            'Erst oben die Adresse der Ordner-Freigabe eintragen und speichern – dann bringt die Einrichtung Benutzer und Passwort gleich mit.',
        )).toBeInTheDocument();
        expect(screen.queryByText(/Benutzer und Passwort für den Ordner stecken/)).not.toBeInTheDocument();
    });

    it('schweigt zu Zugangsdaten ohne Ordner-Freigabe des ERP', () => {
        zeigeKarte(ADRESSE);
        expect(screen.queryByText(/Benutzer und Passwort/)).not.toBeInTheDocument();

        zeigeKarte(ADRESSE, false, null);
        expect(screen.queryByText(/Benutzer und Passwort/)).not.toBeInTheDocument();
    });

    it('sagt bei Windows, dass sich die Datei selbst löscht und nicht weitergegeben wird', () => {
        zeigeKarte(ADRESSE);

        expect(screen.getByText(
            /Die Datei löscht sich nach der Einrichtung selbst – für\s+jeden PC neu herunterladen und nicht weitergeben\./,
        )).toBeInTheDocument();
    });

    it('weist auf eine ungespeicherte Änderung hin', () => {
        zeigeKarte(ADRESSE, true);

        expect(screen.getByText('Bitte erst speichern – das Hilfsprogramm bekommt die gespeicherte Adresse.')).toBeInTheDocument();
    });

    it('lädt die Windows-Datei mit der Adresse dieses Programms herunter', async () => {
        fetchMock.mockResolvedValue(dateiAntwort('@echo off', 'attachment; filename="ERP-Dateien-oeffnen-Windows.bat"'));
        zeigeKarte(ADRESSE);

        fireEvent.click(screen.getByRole('button', { name: WINDOWS }));

        await waitFor(() => expect(klickMock).toHaveBeenCalledTimes(1));
        expect(fetchMock).toHaveBeenCalledWith(
            `/api/openfile-launcher/einrichtung/windows?adresse=${encodeURIComponent(window.location.origin)}`,
        );
        expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:test');
        expect(downloadName).toBe('ERP-Dateien-oeffnen-Windows.bat');
        await waitFor(() => expect(screen.getByRole('button', { name: WINDOWS })).toBeEnabled());
    });

    it('übernimmt keinen fremden Dateinamen mit Pfad oder falscher Endung', async () => {
        fetchMock.mockResolvedValue(dateiAntwort('@echo off', 'attachment; filename="../../boese.exe"'));
        zeigeKarte(ADRESSE);

        fireEvent.click(screen.getByRole('button', { name: WINDOWS }));

        await waitFor(() => expect(klickMock).toHaveBeenCalledTimes(1));
        expect(downloadName).toBe('ERP-Dateien-oeffnen-Windows.bat');
    });

    it('sperrt beide Knöpfe, solange eine Datei geladen wird', async () => {
        let antworte: (res: Response) => void = () => undefined;
        fetchMock.mockReturnValue(new Promise<Response>((resolve) => { antworte = resolve; }));
        zeigeKarte(ADRESSE);

        fireEvent.click(screen.getByRole('button', { name: WINDOWS }));

        await waitFor(() => expect(screen.getByRole('button', { name: WINDOWS })).toBeDisabled());
        expect(screen.getByRole('button', { name: WINDOWS })).toHaveAttribute('aria-busy', 'true');
        expect(screen.getByRole('button', { name: MAC })).toBeDisabled();

        antworte(dateiAntwort('@echo off'));
        await waitFor(() => expect(screen.getByRole('button', { name: WINDOWS })).toBeEnabled());
    });

    it('zeigt die Meldung des Servers als Fehler, wenn er die Datei ablehnt', async () => {
        fetchMock.mockResolvedValue(new Response(
            JSON.stringify({ status: 400, message: 'Bitte zuerst die Netzwerk-Adresse des Datei-Ordners eintragen und speichern.' }),
            { status: 400, headers: { 'Content-Type': 'application/json' } },
        ));
        zeigeKarte(ADRESSE);

        fireEvent.click(screen.getByRole('button', { name: WINDOWS }));

        expect(await screen.findByText('Bitte zuerst die Netzwerk-Adresse des Datei-Ordners eintragen und speichern.')).toBeInTheDocument();
        expect(klickMock).not.toHaveBeenCalled();
        expect(URL.createObjectURL).not.toHaveBeenCalled();
    });

    it('meldet einen Fehler, wenn der Server für die Windows-Datei nicht erreichbar ist', async () => {
        fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));
        zeigeKarte(ADRESSE);

        fireEvent.click(screen.getByRole('button', { name: WINDOWS }));

        expect(await screen.findByText(/Das Hilfsprogramm konnte nicht heruntergeladen werden/)).toBeInTheDocument();
        expect(klickMock).not.toHaveBeenCalled();
    });
});

describe('OpenFileLauncherKarte – Befehl für den Mac', () => {
    let fetchMock: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        fetchMock = vi.fn();
        vi.stubGlobal('fetch', fetchMock);
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.useRealTimers();
        Reflect.deleteProperty(navigator, 'clipboard');
    });

    /** jsdom kennt keine Zwischenablage: für den Test eine einsetzen (oder ausdrücklich keine). */
    function setzeZwischenablage(zwischenablage: { writeText: (text: string) => Promise<void> } | undefined) {
        Object.defineProperty(navigator, 'clipboard', { value: zwischenablage, configurable: true });
    }

    async function erstelleBefehl() {
        fetchMock.mockResolvedValue(befehlAntwort());
        zeigeKarte(ADRESSE);
        fireEvent.click(screen.getByRole('button', { name: MAC }));
        return screen.findByLabelText('Befehl für das Terminal');
    }

    it('erklärt vor dem Erstellen, was der Befehl tut, und lädt nichts herunter', () => {
        zeigeKarte(ADRESSE);

        expect(screen.getByText(/ohne Download und ohne Warnung von macOS/)).toBeInTheDocument();
        expect(screen.getByText(/HiCAD und Tenado gibt es nicht für den Mac/)).toBeInTheDocument();
        expect(screen.queryByLabelText('Befehl für das Terminal')).not.toBeInTheDocument();
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('erstellt den Befehl per POST mit der Adresse dieses Programms und zeigt ihn unverändert an', async () => {
        const feld = await erstelleBefehl();

        expect(fetchMock).toHaveBeenCalledTimes(1);
        expect(fetchMock).toHaveBeenCalledWith(BEFEHL_URL, { method: 'POST' });
        expect(feld).toHaveValue(BEFEHL);
        expect(feld).toHaveAttribute('readonly');
        expect(screen.getByText('Am Mac das Programm „Terminal“ öffnen: ⌘ + Leertaste drücken, „Terminal“ tippen, Enter.')).toBeInTheDocument();
        expect(screen.getByText('Befehl einfügen (⌘ + V) und Enter drücken.')).toBeInTheDocument();
        expect(screen.getByText('Warten, bis „Fertig!“ erscheint – dann kann das Fenster zu.')).toBeInTheDocument();
        expect(screen.getByText(/^Der Befehl gilt 10 Minuten und nur ein einziges Mal\. Danach – oder wenn das Terminal\s+„error: 404“ meldet – einfach einen neuen erstellen\.$/)).toBeInTheDocument();
        expect(screen.queryByText(/ohne Download und ohne Warnung von macOS/)).not.toBeInTheDocument();
        await waitFor(() => expect(screen.getByRole('button', { name: MAC_NEU })).toBeEnabled());
        expect(screen.queryByRole('button', { name: MAC })).not.toBeInTheDocument();
    });

    it('zeigt die Gültigkeit so, wie der Server sie meldet', async () => {
        fetchMock.mockResolvedValue(befehlAntwort(BEFEHL, 5));
        zeigeKarte(ADRESSE);

        fireEvent.click(screen.getByRole('button', { name: MAC }));

        expect(await screen.findByText(/Der Befehl gilt 5 Minuten/)).toBeInTheDocument();
    });

    it('ersetzt den Befehl durch einen neuen, wenn man ihn erneut erstellt', async () => {
        await erstelleBefehl();
        const neuerBefehl = BEFEHL.replace('abcd', 'wxyz');
        fetchMock.mockResolvedValue(befehlAntwort(neuerBefehl));

        fireEvent.click(await screen.findByRole('button', { name: MAC_NEU }));

        await waitFor(() => expect(screen.getByLabelText('Befehl für das Terminal')).toHaveValue(neuerBefehl));
        expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    it('kopiert genau den Befehl und meldet kurz „Kopiert“', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        const writeText = vi.fn().mockResolvedValue(undefined);
        setzeZwischenablage({ writeText });
        await erstelleBefehl();

        fireEvent.click(screen.getByRole('button', { name: 'Kopieren' }));

        expect(await screen.findByRole('button', { name: 'Kopiert' })).toBeInTheDocument();
        expect(writeText).toHaveBeenCalledTimes(1);
        expect(writeText).toHaveBeenCalledWith(BEFEHL);
        expect(screen.queryByText('Bitte mit ⌘ + C kopieren.')).not.toBeInTheDocument();
        // Nach kurzer Zeit steht wieder „Kopieren“ da.
        await act(() => vi.advanceTimersByTimeAsync(1900));
        expect(screen.getByRole('button', { name: 'Kopiert' })).toBeInTheDocument();
        await act(() => vi.advanceTimersByTimeAsync(200));
        expect(screen.getByRole('button', { name: 'Kopieren' })).toBeInTheDocument();
    });

    it('markiert den Befehl und erklärt ⌘ + C, wenn es keine Zwischenablage gibt (z. B. über http)', async () => {
        setzeZwischenablage(undefined);
        const feld = await erstelleBefehl() as HTMLTextAreaElement;

        fireEvent.click(screen.getByRole('button', { name: 'Kopieren' }));

        expect(await screen.findByText('Bitte mit ⌘ + C kopieren.')).toBeInTheDocument();
        expect(feld).toHaveFocus();
        expect(feld.selectionStart).toBe(0);
        expect(feld.selectionEnd).toBe(BEFEHL.length);
        expect(screen.queryByRole('button', { name: 'Kopiert' })).not.toBeInTheDocument();
    });

    it('fällt auch aufs Markieren zurück, wenn der Browser das Kopieren ablehnt', async () => {
        const writeText = vi.fn().mockRejectedValue(new DOMException('Nicht erlaubt', 'NotAllowedError'));
        setzeZwischenablage({ writeText });
        await erstelleBefehl();

        fireEvent.click(screen.getByRole('button', { name: 'Kopieren' }));

        expect(await screen.findByText('Bitte mit ⌘ + C kopieren.')).toBeInTheDocument();
        expect(writeText).toHaveBeenCalledWith(BEFEHL);
    });

    it('markiert den ganzen Befehl beim Klick ins Feld', async () => {
        const feld = await erstelleBefehl() as HTMLTextAreaElement;

        fireEvent.click(feld);

        expect(feld.selectionStart).toBe(0);
        expect(feld.selectionEnd).toBe(BEFEHL.length);
    });

    it('sperrt beide Knöpfe, solange der Befehl erstellt wird', async () => {
        let antworte: (res: Response) => void = () => undefined;
        fetchMock.mockReturnValue(new Promise<Response>((resolve) => { antworte = resolve; }));
        zeigeKarte(ADRESSE);

        fireEvent.click(screen.getByRole('button', { name: MAC }));

        await waitFor(() => expect(screen.getByRole('button', { name: MAC })).toBeDisabled());
        expect(screen.getByRole('button', { name: MAC })).toHaveAttribute('aria-busy', 'true');
        expect(screen.getByRole('button', { name: WINDOWS })).toBeDisabled();

        antworte(befehlAntwort());
        await waitFor(() => expect(screen.getByRole('button', { name: WINDOWS })).toBeEnabled());
    });

    it('zeigt die Meldung des Servers, wenn er den Befehl ablehnt', async () => {
        fetchMock.mockResolvedValue(new Response(
            JSON.stringify({ status: 400, message: 'Bitte zuerst die Netzwerk-Adresse des Datei-Ordners eintragen und speichern.' }),
            { status: 400, headers: { 'Content-Type': 'application/json' } },
        ));
        zeigeKarte(ADRESSE);

        fireEvent.click(screen.getByRole('button', { name: MAC }));

        expect(await screen.findByText('Bitte zuerst die Netzwerk-Adresse des Datei-Ordners eintragen und speichern.')).toBeInTheDocument();
        expect(screen.queryByLabelText('Befehl für das Terminal')).not.toBeInTheDocument();
        await waitFor(() => expect(screen.getByRole('button', { name: MAC })).toBeEnabled());
    });

    it('meldet einen Fehler, wenn die Antwort keinen Befehl enthält', async () => {
        fetchMock.mockResolvedValue(new Response(JSON.stringify({ gueltigMinuten: 10 }), { status: 200 }));
        zeigeKarte(ADRESSE);

        fireEvent.click(screen.getByRole('button', { name: MAC }));

        expect(await screen.findByText('Der Befehl konnte nicht erstellt werden.')).toBeInTheDocument();
        expect(screen.queryByLabelText('Befehl für das Terminal')).not.toBeInTheDocument();
    });

    it('meldet einen Fehler, wenn der Server nicht erreichbar ist', async () => {
        fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));
        zeigeKarte(ADRESSE);

        fireEvent.click(screen.getByRole('button', { name: MAC }));

        expect(await screen.findByText('Der Befehl konnte nicht erstellt werden – Server nicht erreichbar.')).toBeInTheDocument();
        expect(screen.queryByLabelText('Befehl für das Terminal')).not.toBeInTheDocument();
    });

    it('erklärt am Info-Knopf neben „Mac“, warum es ein Befehl ist', () => {
        zeigeKarte(ADRESSE);

        fireEvent.click(screen.getByRole('button', { name: 'Mehr zu: Warum ein Befehl statt einer Datei?' }));

        const erklaerung = screen.getByRole('dialog', { name: 'Warum ein Befehl statt einer Datei?' });
        expect(erklaerung).toHaveTextContent('Der Link gilt nur 10 Minuten und funktioniert genau einmal');
        expect(erklaerung).toHaveTextContent('Über das Terminal gibt es diese Sperre nicht.');
    });
});

describe('DateiOrdnerSection mit Hilfsprogramm-Karte', () => {
    let gespeichert: { pfad: string; networkUrl: string };

    beforeEach(() => {
        gespeichert = { pfad: 'C:\\Zeichnungen', networkUrl: '' };
        vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
            if (url === '/api/settings/datei-ordner' && init?.method === 'PUT') {
                gespeichert = JSON.parse(String(init.body));
            }
            if (url === '/api/settings/datei-ordner') {
                return new Response(JSON.stringify({ ...gespeichert, konfiguriert: true }), { status: 200 });
            }
            return new Response('{}', { status: 404 });
        }));
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('gibt die Knöpfe erst frei, wenn die getippte Netzwerk-Adresse gespeichert ist', async () => {
        render(<ToastProvider><DateiOrdnerSection /></ToastProvider>);

        const feld = await screen.findByLabelText('Netzwerk-Adresse für Kollegen (optional)');
        expect(screen.getByRole('button', { name: WINDOWS })).toBeDisabled();

        fireEvent.change(feld, { target: { value: ADRESSE } });
        expect(screen.getByText('Bitte erst speichern – das Hilfsprogramm bekommt die gespeicherte Adresse.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: WINDOWS })).toBeDisabled();

        fireEvent.click(screen.getByRole('button', { name: /Datei-Ordner speichern/ }));

        await waitFor(() => expect(screen.getByRole('button', { name: WINDOWS })).toBeEnabled());
        expect(screen.queryByText(/Bitte erst speichern/)).not.toBeInTheDocument();
        expect(gespeichert.networkUrl).toBe(ADRESSE);
    });

    it('übernimmt eine schon gespeicherte Netzwerk-Adresse beim Laden', async () => {
        gespeichert = { pfad: 'C:\\Zeichnungen', networkUrl: ADRESSE };
        render(<ToastProvider><DateiOrdnerSection /></ToastProvider>);

        expect(await screen.findByRole('button', { name: MAC })).toBeEnabled();
        expect(screen.queryByText(/Bitte erst speichern/)).not.toBeInTheDocument();
    });
});
