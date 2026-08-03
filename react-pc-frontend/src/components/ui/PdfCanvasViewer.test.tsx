import { render, screen, waitFor, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { PdfCanvasViewer } from './PdfCanvasViewer';

const pdfBlob = (inhalt: string) => new Blob([inhalt], { type: 'application/pdf' });

/** Zählt die von der Komponente an den Body gehängten Druck-iframes. */
function druckFrames(): HTMLIFrameElement[] {
    return Array.from(document.body.querySelectorAll('iframe[aria-hidden="true"]'));
}

/**
 * Ohne window.pdfjsLib läuft die Komponente im iframe-Fallback. Für den Canvas-Pfad
 * (und damit für die Prüfung "nie das falsche PDF drucken") muss PDF.js gestubbt werden.
 */
function stubPdfjs() {
    vi.stubGlobal('pdfjsLib', {
        getDocument: () => ({
            promise: Promise.resolve({
                numPages: 1,
                getPage: async () => ({
                    getViewport: () => ({ width: 100, height: 140 }),
                    render: () => ({ promise: Promise.resolve() }),
                }),
                destroy: () => { },
            }),
        }),
    });
}

describe('PdfCanvasViewer', () => {
    let createObjectURL: ReturnType<typeof vi.spyOn>;
    let revokeObjectURL: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        let zaehler = 0;
        vi.stubGlobal('fetch', vi.fn(async () => ({
            ok: true,
            status: 200,
            blob: async () => pdfBlob('%PDF-1.4 test'),
        })));
        createObjectURL = vi.spyOn(URL, 'createObjectURL').mockImplementation(() => `blob:test/${++zaehler}`);
        revokeObjectURL = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => { });
    });

    afterEach(() => {
        cleanup();
        druckFrames().forEach(frame => frame.remove());
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    /** Wartet, bis die Bytes geladen sind und der Knopf freigeschaltet ist. */
    async function druckKnopf(): Promise<HTMLElement> {
        const knopf = await screen.findByRole('button', { name: 'Drucken' });
        await waitFor(() => expect(knopf).toBeEnabled());
        return knopf;
    }

    it('bietet standardmäßig einen Drucken-Knopf an', async () => {
        render(<PdfCanvasViewer url="/api/projekte/export-pdf" />);

        expect(await screen.findByRole('button', { name: 'Drucken' })).toBeInTheDocument();
    });

    it('blendet den Drucken-Knopf aus, wenn er nicht gewünscht ist', async () => {
        render(<PdfCanvasViewer url="/api/projekte/export-pdf" showPrintButton={false} />);

        await waitFor(() => expect(fetch).toHaveBeenCalled());
        expect(screen.queryByRole('button', { name: 'Drucken' })).not.toBeInTheDocument();
    });

    it('sperrt den Drucken-Knopf, solange das Dokument noch nicht geladen ist', async () => {
        // Der Fetch bleibt offen – es gibt also noch keine Bytes zum Drucken.
        vi.stubGlobal('fetch', vi.fn(() => new Promise(() => { })));
        render(<PdfCanvasViewer url="/api/projekte/export-pdf" />);

        expect(await screen.findByRole('button', { name: 'Drucken' })).toBeDisabled();
        expect(druckFrames()).toHaveLength(0);
    });

    it('hängt beim Klick genau ein unsichtbares Druck-iframe an die Seite', async () => {
        const user = userEvent.setup();
        render(<PdfCanvasViewer url="/api/projekte/export-pdf" />);

        await user.click(await druckKnopf());

        const frames = druckFrames();
        expect(frames).toHaveLength(1);
        expect(frames[0].style.visibility).toBe('hidden');
    });

    it('behält das Druck-iframe, wenn die Vorschau während des Drucks geschlossen wird', async () => {
        const user = userEvent.setup();
        const { unmount } = render(<PdfCanvasViewer url="/api/projekte/export-pdf" />);

        await user.click(await druckKnopf());
        unmount();

        // Würde das iframe beim Schließen mit entfernt, bräche der laufende Druck ab.
        expect(druckFrames()).toHaveLength(1);
    });

    it('druckt aus den geladenen Bytes statt aus der Quell-URL', async () => {
        stubPdfjs();
        const user = userEvent.setup();
        render(<PdfCanvasViewer url="/api/projekte/export-pdf" />);

        await user.click(await druckKnopf());

        // Eine eigens erzeugte Object-URL landet im Frame – nicht die rohe Server-URL.
        expect(createObjectURL).toHaveBeenCalled();
        expect(druckFrames()[0].getAttribute('src')).toMatch(/^blob:/);
    });

    it('druckt nach einem gescheiterten Dokumentwechsel nicht mehr das vorherige PDF', async () => {
        stubPdfjs();
        const ersterBlob = pdfBlob('%PDF-1.4 dokument-A');
        let aufruf = 0;
        vi.stubGlobal('fetch', vi.fn(async () => {
            aufruf += 1;
            if (aufruf === 1) return { ok: true, status: 200, blob: async () => ersterBlob };
            throw new Error('Netzwerkfehler');
        }));

        const { rerender } = render(<PdfCanvasViewer url="/dokument-a.pdf" />);
        await druckKnopf();

        rerender(<PdfCanvasViewer url="/dokument-b.pdf" />);

        // Dokument B ließ sich nicht laden – dann darf der Knopf NICHT einfach
        // weiter das noch im Speicher liegende Dokument A ausdrucken.
        await waitFor(() =>
            expect(screen.getByRole('button', { name: 'Drucken' })).toBeDisabled()
        );
        expect(druckFrames()).toHaveLength(0);
    });

    it('gibt die Object-URL des Drucks wieder frei, sobald der Druckdialog endet', async () => {
        stubPdfjs();
        const user = userEvent.setup();
        render(<PdfCanvasViewer url="/api/projekte/export-pdf" />);

        await user.click(await druckKnopf());
        const frame = druckFrames()[0];
        const genutzteUrl = frame.getAttribute('src');

        // jsdom lädt blob:-URLs nicht, deshalb den Ladevorgang selbst auslösen.
        frame.dispatchEvent(new Event('load'));
        frame.contentWindow?.dispatchEvent(new Event('afterprint'));

        await waitFor(() => expect(revokeObjectURL).toHaveBeenCalledWith(genutzteUrl));
        expect(druckFrames()).toHaveLength(0);
    });
});
