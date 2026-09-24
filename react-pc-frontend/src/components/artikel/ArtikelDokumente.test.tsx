import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { ArtikelDokumente } from './ArtikelDokumente';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';
import type { ArtikelDokument } from '../../types';

/**
 * Dummy-Dokumente (DSGVO: keine echten Firmen-/Personendaten). Entsprechen
 * einem Zukaufteil wie einem Handlaufhalter: ein Vorschaubild plus eine
 * Zulassung als PDF.
 */
const VORSCHAUBILD: ArtikelDokument = {
    id: 1,
    originalDateiname: 'handlaufhalter.jpg',
    typ: 'VORSCHAUBILD',
    erstelltAm: '2026-08-01T10:00:00',
    url: '/api/artikel/dokumente/1/datei',
};

const ZULASSUNG: ArtikelDokument = {
    id: 2,
    originalDateiname: 'zulassung.pdf',
    typ: 'ZULASSUNG',
    erstelltAm: '2026-08-02T10:00:00',
    url: '/api/artikel/dokumente/2/datei',
};

function renderSektion(artikelId = 7) {
    return render(
        <ToastProvider>
            <ConfirmProvider>
                <ArtikelDokumente artikelId={artikelId} />
            </ConfirmProvider>
        </ToastProvider>,
    );
}

/** Baut einen fetch-Mock, der GET/POST/DELETE auf die vier Dokumente-Endpoints beantwortet. */
function mockFetch(anfangsDokumente: ArtikelDokument[], overrides?: {
    postResponse?: () => { ok: boolean; status: number; json: () => Promise<unknown> };
    deleteResponse?: () => { ok: boolean; status: number; json: () => Promise<unknown> };
}) {
    let dokumente = [...anfangsDokumente];
    const fetchMock = vi.fn((url: string, options?: RequestInit) => {
        const methode = options?.method ?? 'GET';
        if (methode === 'GET' && url.includes('/dokumente')) {
            return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(dokumente) });
        }
        if (methode === 'POST' && url.includes('/dokumente')) {
            if (overrides?.postResponse) return Promise.resolve(overrides.postResponse());
            const neu: ArtikelDokument = {
                id: 99,
                originalDateiname: 'neu.pdf',
                typ: 'ZULASSUNG',
                erstelltAm: '2026-08-15T10:00:00',
                url: '/api/artikel/dokumente/99/datei',
            };
            dokumente = [...dokumente, neu];
            return Promise.resolve({ ok: true, status: 201, json: () => Promise.resolve(neu) });
        }
        if (methode === 'DELETE') {
            if (overrides?.deleteResponse) return Promise.resolve(overrides.deleteResponse());
            const id = Number(url.split('/').pop());
            dokumente = dokumente.filter((d) => d.id !== id);
            return Promise.resolve({ ok: true, status: 204, json: () => Promise.resolve({}) });
        }
        // Datei-Endpoint (Bild-<img>-Ladeversuch oder PDF-Fallback-Fetch) - fuer die
        // hier getesteten Fälle reicht eine leere, nicht-blockierende Antwort.
        return Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve({}) });
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

describe('ArtikelDokumente', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('zeigt den leeren Zustand mit Hinweis und Upload-Knopf, wenn nichts hinterlegt ist', async () => {
        mockFetch([]);
        renderSektion();

        expect(await screen.findByText('Noch kein Bild hinterlegt')).toBeInTheDocument();
        expect(screen.getByText('Noch keine Zulassung, Zeichnung oder Anleitung hinterlegt.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Bild hochladen/ })).toBeInTheDocument();
    });

    it('zeigt Vorschaubild und Zusatzunterlagen mit Typ, Dateiname und Datum', async () => {
        mockFetch([VORSCHAUBILD, ZULASSUNG]);
        renderSektion();

        expect(await screen.findByAltText('Vorschaubild: handlaufhalter.jpg')).toBeInTheDocument();
        // Auf die Zeile eingrenzen: "Zulassung" steht als Typ-Badge in der Zeile UND
        // als vorbelegter Wert im Typ-Select des Upload-Formulars darunter.
        const zeile = screen.getByText('zulassung.pdf').closest('li')!;
        expect(within(zeile).getByText('Zulassung')).toBeInTheDocument();
        expect(within(zeile).getByText('2.8.2026')).toBeInTheDocument();
        // Ist schon ein Bild da, heisst der Knopf "ersetzen" statt "hochladen".
        expect(screen.getByRole('button', { name: /Bild ersetzen/ })).toBeInTheDocument();
    });

    it('öffnet den ImageViewer beim Klick auf das Vorschaubild', async () => {
        mockFetch([VORSCHAUBILD]);
        const user = userEvent.setup();
        renderSektion();

        await user.click(await screen.findByTitle('Bild groß anzeigen'));

        expect(await screen.findByTitle('Schließen (ESC)')).toBeInTheDocument();
    });

    it('öffnet die PDF-Vorschau beim Klick auf eine Unterlage', async () => {
        mockFetch([ZULASSUNG]);
        const user = userEvent.setup();
        renderSektion();

        await user.click(await screen.findByText('zulassung.pdf'));

        // Der Modal-Header hat eigene Knöpfe, die es in der Listenzeile nicht gibt.
        expect(await screen.findByRole('button', { name: /Herunterladen/ })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Neuer Tab/ })).toBeInTheDocument();
    });

    it('lädt eine neue Unterlage hoch und zeigt danach die Liste erneut', async () => {
        const fetchMock = mockFetch([]);
        const user = userEvent.setup();
        renderSektion();

        await screen.findByText('Noch keine Zulassung, Zeichnung oder Anleitung hinterlegt.');

        const datei = new File(['Inhalt'], 'zulassung.pdf', { type: 'application/pdf' });
        await user.upload(screen.getByLabelText('Datei auswählen'), datei);
        await user.click(screen.getByRole('button', { name: /^Hochladen$/ }));

        await waitFor(() => {
            const postAufruf = fetchMock.mock.calls.find(([, options]) => options?.method === 'POST');
            expect(postAufruf).toBeTruthy();
            const formData = postAufruf![1]!.body as FormData;
            expect((formData.get('datei') as File).name).toBe('zulassung.pdf');
            expect(formData.get('typ')).toBe('ZULASSUNG');
        });

        expect(await screen.findByText('Die Unterlage ist gespeichert.')).toBeInTheDocument();
        // Nach dem Hochladen wird neu geladen - die Liste zeigt jetzt die neue Datei.
        expect(await screen.findByText('neu.pdf')).toBeInTheDocument();
    });

    it('zeigt die Serverfehlermeldung wörtlich an, wenn der Dateityp abgelehnt wird', async () => {
        mockFetch([], {
            postResponse: () => ({
                ok: false,
                status: 400,
                json: () => Promise.resolve({
                    message: 'Dieser Dateityp wird nicht unterstützt. Erlaubt sind PDF, PNG, JPG, JPEG, WEBP und GIF.',
                }),
            }),
        });
        // applyAccept: false - das accept-Attribut filtert im echten Browser nur den
        // Standard-Dateidialog; ueber "Alle Dateien" laesst sich trotzdem eine falsche
        // Endung waehlen, und genau dann muss die Server-Meldung sichtbar werden.
        const user = userEvent.setup({ applyAccept: false });
        renderSektion();

        await screen.findByText('Noch keine Zulassung, Zeichnung oder Anleitung hinterlegt.');

        const datei = new File(['x'], 'schadcode.exe', { type: 'application/octet-stream' });
        await user.upload(screen.getByLabelText('Datei auswählen'), datei);
        await user.click(screen.getByRole('button', { name: /^Hochladen$/ }));

        // Exakt die Server-Meldung mit echten Umlauten - kein eigener Text, kein Statuscode.
        expect(await screen.findByText(
            'Dieser Dateityp wird nicht unterstützt. Erlaubt sind PDF, PNG, JPG, JPEG, WEBP und GIF.',
        )).toBeInTheDocument();
    });

    it('lädt ein neues Vorschaubild hoch, ohne das als Fehler zu behandeln', async () => {
        mockFetch([VORSCHAUBILD]);
        const user = userEvent.setup();
        renderSektion();

        await screen.findByRole('button', { name: /Bild ersetzen/ });

        const neuesBild = new File(['Bild'], 'neues-foto.jpg', { type: 'image/jpeg' });
        await user.upload(screen.getByLabelText('Bild ersetzen'), neuesBild);

        // Ein Ersetzen ist der Normalfall (serverseitig erlaubt) - keine Fehlermeldung,
        // sondern dieselbe Erfolgsmeldung wie beim ersten Hochladen.
        expect(await screen.findByText('Das Bild ist gespeichert.')).toBeInTheDocument();
        expect(screen.queryByText(/konnte nicht/)).not.toBeInTheDocument();
    });

    it('fragt vor dem Löschen nach und löscht erst nach Bestätigung', async () => {
        const fetchMock = mockFetch([ZULASSUNG]);
        const user = userEvent.setup();
        renderSektion();

        await screen.findByText('zulassung.pdf');
        await user.click(screen.getByRole('button', { name: 'zulassung.pdf löschen' }));

        const dialog = await screen.findByText('Datei löschen?');
        expect(dialog).toBeInTheDocument();
        expect(screen.getByText(/zulassung\.pdf.*wirklich löschen/)).toBeInTheDocument();

        // Abbrechen: nichts wird gelöscht.
        await user.click(screen.getByText('Abbrechen'));
        expect(fetchMock.mock.calls.some(([, options]) => options?.method === 'DELETE')).toBe(false);
        expect(screen.getByText('zulassung.pdf')).toBeInTheDocument();

        // Erneut löschen, diesmal bestätigen.
        await user.click(screen.getByRole('button', { name: 'zulassung.pdf löschen' }));
        await user.click(await screen.findByText('Ja, löschen'));

        await waitFor(() => {
            expect(fetchMock).toHaveBeenCalledWith(
                '/api/artikel/dokumente/2',
                expect.objectContaining({ method: 'DELETE' }),
            );
        });
        expect(await screen.findByText('Die Datei ist gelöscht.')).toBeInTheDocument();
        expect(screen.queryByText('zulassung.pdf')).not.toBeInTheDocument();
    });

    it('fragt vor dem Löschen des Vorschaubilds nach und leert die Anzeige erst nach Bestätigung', async () => {
        const fetchMock = mockFetch([VORSCHAUBILD]);
        const user = userEvent.setup();
        renderSektion();

        await screen.findByRole('button', { name: /Bild ersetzen/ });
        await user.click(screen.getByRole('button', { name: 'Vorschaubild löschen' }));

        const dialog = await screen.findByText('Datei löschen?');
        expect(dialog).toBeInTheDocument();
        expect(screen.getByText(/handlaufhalter\.jpg.*wirklich löschen/)).toBeInTheDocument();

        // Abbrechen: das Bild bleibt hinterlegt.
        await user.click(screen.getByText('Abbrechen'));
        expect(fetchMock.mock.calls.some(([, options]) => options?.method === 'DELETE')).toBe(false);
        expect(screen.getByRole('button', { name: /Bild ersetzen/ })).toBeInTheDocument();

        // Erneut löschen, diesmal bestätigen.
        await user.click(screen.getByRole('button', { name: 'Vorschaubild löschen' }));
        await user.click(await screen.findByText('Ja, löschen'));

        await waitFor(() => {
            expect(fetchMock).toHaveBeenCalledWith(
                '/api/artikel/dokumente/1',
                expect.objectContaining({ method: 'DELETE' }),
            );
        });
        expect(await screen.findByText('Die Datei ist gelöscht.')).toBeInTheDocument();
        // Zurueck in den leeren Zustand - ohne dass die Seite neu geladen werden muss.
        expect(await screen.findByText('Noch kein Bild hinterlegt')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /^Bild hochladen$/ })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Vorschaubild löschen' })).not.toBeInTheDocument();
    });

    it('zeigt die Serverfehlermeldung an, wenn das Löschen fehlschlägt', async () => {
        mockFetch([ZULASSUNG], {
            deleteResponse: () => ({
                ok: false,
                status: 404,
                json: () => Promise.resolve({ message: 'Dieses Dokument gibt es nicht.' }),
            }),
        });
        const user = userEvent.setup();
        renderSektion();

        await screen.findByText('zulassung.pdf');
        await user.click(screen.getByRole('button', { name: 'zulassung.pdf löschen' }));
        await user.click(await screen.findByText('Ja, löschen'));

        expect(await screen.findByText('Dieses Dokument gibt es nicht.')).toBeInTheDocument();
        // Ohne erfolgreiche Antwort bleibt der Eintrag sichtbar.
        expect(screen.getByText('zulassung.pdf')).toBeInTheDocument();
    });

    it('meldet einen Ladefehler, statt eine leere Fläche zu zeigen', async () => {
        vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('Netzwerk'))));
        renderSektion();

        expect(await screen.findByText('Die Bilder und Unterlagen konnten nicht geladen werden.')).toBeInTheDocument();
    });
});
