import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../ui/toast';
import { OpenFileLauncherKarte } from './OpenFileLauncherKarte';
import { DateiOrdnerSection } from './DateiOrdnerSection';

const ADRESSE = '\\\\server\\zeichnungen';
const WINDOWS = 'Für Windows herunterladen';
const MAC = 'Für Mac herunterladen';

function zeigeKarte(gespeicherteAdresse: string, ungespeichert = false) {
    return render(
        <ToastProvider>
            <OpenFileLauncherKarte gespeicherteAdresse={gespeicherteAdresse} ungespeichert={ungespeichert} />
        </ToastProvider>,
    );
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
        expect(screen.getByText(/Laden Sie es über die https-Adresse herunter \(z\. B\. die Tailscale-Adresse\)/)).toBeInTheDocument();
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

    it('nimmt für den Mac den Standard-Dateinamen, wenn der Server keinen schickt', async () => {
        fetchMock.mockResolvedValue(dateiAntwort('PK'));
        zeigeKarte(ADRESSE);

        fireEvent.click(screen.getByRole('button', { name: MAC }));

        await waitFor(() => expect(klickMock).toHaveBeenCalledTimes(1));
        expect(fetchMock.mock.calls[0][0]).toBe(
            `/api/openfile-launcher/einrichtung/mac?adresse=${encodeURIComponent(window.location.origin)}`,
        );
        expect(downloadName).toBe('ERP-Dateien-oeffnen-Mac.zip');
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

    it('meldet einen Fehler, wenn der Server nicht erreichbar ist', async () => {
        fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));
        zeigeKarte(ADRESSE);

        fireEvent.click(screen.getByRole('button', { name: MAC }));

        expect(await screen.findByText(/Das Hilfsprogramm konnte nicht heruntergeladen werden/)).toBeInTheDocument();
        expect(klickMock).not.toHaveBeenCalled();
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
