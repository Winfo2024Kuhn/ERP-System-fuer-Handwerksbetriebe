import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup, waitFor } from '@testing-library/react';
import DocumentManager from './DocumentManager';
import { ToastProvider } from './ui/toast';
import { ConfirmProvider } from './ui/confirm-dialog';

const HOCHGELADEN = {
    id: 41,
    originalDateiname: 'lieferschein-muster.pdf',
    dateityp: 'application/pdf',
    url: '/api/dokumente/41',
    dokumentGruppe: 'DIVERSE_DOKUMENTE',
};

/** Wie der Server: nach einem erfolgreichen Upload liefert die Dokumentliste die neue Datei mit. */
function stubbeFetch({ uploadOk = true }: { uploadOk?: boolean } = {}) {
    const gespeichert: typeof HOCHGELADEN[] = [];
    const fetchMock = vi.fn(async (_url: string, init?: RequestInit) => {
        if (init?.method === 'POST') {
            if (uploadOk) gespeichert.push(HOCHGELADEN);
            return { ok: uploadOk, status: uploadOk ? 200 : 500, json: async () => (uploadOk ? [HOCHGELADEN] : {}) };
        }
        return { ok: true, status: 200, json: async () => [...gespeichert] };
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

const uploadAufruf = (fetchMock: ReturnType<typeof stubbeFetch>) =>
    fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');

const lieferantenAufrufe = (fetchMock: ReturnType<typeof stubbeFetch>) =>
    fetchMock.mock.calls.filter(([url]) => String(url).startsWith('/api/lieferanten'));

function zeigeAn(props: { projektId?: number; anfrageId?: number }) {
    return render(
        <ToastProvider>
            <ConfirmProvider>
                <DocumentManager {...props} />
            </ConfirmProvider>
        </ToastProvider>
    );
}

/** Klappt „Diverse Dokumente" auf und legt eine Dummy-Datei ab. */
async function legeDateiInDiverseDokumente() {
    fireEvent.click(await screen.findByRole('button', { name: /Diverse Dokumente/ }));
    const eingaben = document.querySelectorAll<HTMLInputElement>('input[type=file]');
    expect(eingaben.length).toBeGreaterThan(0);
    fireEvent.change(eingaben[eingaben.length - 1], {
        target: { files: [new File(['dummy'], 'lieferschein-muster.pdf', { type: 'application/pdf' })] },
    });
}

afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('DocumentManager – Hochladen in "Diverse Dokumente"', () => {
    it('lädt im Projekt direkt hoch, ohne nach einem Lieferanten zu fragen', async () => {
        const fetchMock = stubbeFetch();
        zeigeAn({ projektId: 7 });
        await legeDateiInDiverseDokumente();

        await waitFor(() => expect(uploadAufruf(fetchMock)).toBeDefined());
        const [uploadUrl, uploadInit] = uploadAufruf(fetchMock)!;
        expect(uploadUrl).toBe('/api/projekte/7/dokumente?gruppe=DIVERSE_DOKUMENTE');
        const uploadHeader = (uploadInit?.headers ?? {}) as Record<string, string>;
        expect(uploadHeader).not.toHaveProperty('X-Lieferant-Id');
        // Den Hochlader ermittelt der Server aus der Anmeldung – keine Identität vom Client.
        expect(uploadHeader).not.toHaveProperty('X-User-Profile-Id');
        expect(screen.queryByRole('dialog')).toBeNull();
        expect(screen.queryByText('Dokument hochladen')).toBeNull();

        // Die hochgeladene Datei erscheint sofort in ihrer Gruppe.
        expect(await screen.findByRole('button', { name: /Diverse Dokumente\s*\(1\)/ })).toBeInTheDocument();
    });

    it('fragt die Lieferantenliste gar nicht erst ab', async () => {
        const fetchMock = stubbeFetch();
        zeigeAn({ projektId: 7 });
        await legeDateiInDiverseDokumente();
        await waitFor(() => expect(uploadAufruf(fetchMock)).toBeDefined());

        expect(lieferantenAufrufe(fetchMock)).toHaveLength(0);
    });

    it('lädt bei einer Anfrage genauso direkt hoch', async () => {
        const fetchMock = stubbeFetch();
        zeigeAn({ anfrageId: 3 });
        await legeDateiInDiverseDokumente();

        await waitFor(() => expect(uploadAufruf(fetchMock)).toBeDefined());
        expect(uploadAufruf(fetchMock)![0]).toBe('/api/anfragen/3/dokumente?gruppe=DIVERSE_DOKUMENTE');
        expect(lieferantenAufrufe(fetchMock)).toHaveLength(0);
    });

    it('meldet einen fehlgeschlagenen Upload per Toast', async () => {
        stubbeFetch({ uploadOk: false });
        zeigeAn({ projektId: 7 });
        await legeDateiInDiverseDokumente();

        expect(await screen.findByRole('alert')).toHaveTextContent('Fehler beim Hochladen der Dateien');
    });
});
