import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../ui/toast';
import { ConfirmProvider } from '../../ui/confirm-dialog';
import { FesteTexteBereich } from './FesteTexteBereich';
import type { RechtstextDetail, RechtstextFassung, RechtstextFassungKurz } from './rechtstextApi';

// PDF.js gibt es in jsdom nicht – die Vorschau zeigt hier nur, welche Adresse sie bekäme.
vi.mock('../../ui/PdfCanvasViewer', () => ({
    PdfCanvasViewer: ({ url }: { url: string }) => <div data-testid="pdf-anzeige">{url}</div>,
}));

const BASIS = '/api/formulare/rechtstexte';

const PLATZHALTER = [
    {
        token: '{{FIRMA_KONTAKT}}',
        beschreibung: 'Name, Anschrift, Telefon und E-Mail in einer Zeile',
        beispiel: 'Max Mustermann GmbH, Musterstraße 1, 12345 Musterstadt',
    },
    { token: '{{FIRMENNAME}}', beschreibung: 'Firmenname', beispiel: 'Max Mustermann GmbH' },
];

function detail(teil: Partial<RechtstextDetail> = {}): RechtstextDetail {
    return {
        art: 'WIDERRUFSBELEHRUNG', bezeichnung: 'Widerrufsbelehrung', fassung: 0, stand: '2026-10-13',
        standardtext: true, geaendertAm: null, html: '<h3>Widerrufsrecht</h3><p>Sie haben das Recht.</p>',
        platzhalter: PLATZHALTER, ...teil,
    };
}

interface Server {
    aktuell: RechtstextDetail;
    fassungen: RechtstextFassungKurz[];
    alteFassungen: Record<number, RechtstextFassung>;
    /** Antwort auf PUT – Standard: neue Fassung mit Nummer + 1. */
    put?: (html: string) => Response;
    aufrufe: { methode: string; pfad: string; body: unknown }[];
}

function json(body: unknown, status = 200) {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function starteServer(teil: Partial<Server> = {}): Server {
    const server: Server = { aktuell: detail(), fassungen: [], alteFassungen: {}, aufrufe: [], ...teil };
    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
        const methode = init?.method ?? 'GET';
        const body = typeof init?.body === 'string' ? JSON.parse(init.body) : null;
        server.aufrufe.push({ methode, pfad: url, body });
        const a = server.aktuell;
        const uebersicht = { art: a.art, bezeichnung: a.bezeichnung, fassung: a.fassung, stand: a.stand, standardtext: a.standardtext, geaendertAm: a.geaendertAm };
        if (url === BASIS) return json([uebersicht]);
        if (url === `${BASIS}/WIDERRUFSBELEHRUNG` && methode === 'GET') return json(a);
        if (url === `${BASIS}/WIDERRUFSBELEHRUNG` && methode === 'PUT') {
            if (server.put) return server.put(body.html);
            server.aktuell = { ...a, html: body.html, fassung: a.fassung + 1, stand: '2026-10-14', standardtext: false, geaendertAm: '2026-10-14T09:15:00' };
            return json(server.aktuell);
        }
        if (url === `${BASIS}/WIDERRUFSBELEHRUNG/standard`) {
            server.aktuell = { ...a, html: '<p>Standardtext nach Muster.</p>', fassung: a.fassung + 1, stand: '2026-10-14', standardtext: true, geaendertAm: '2026-10-14T09:20:00' };
            return json(server.aktuell);
        }
        if (url === `${BASIS}/WIDERRUFSBELEHRUNG/fassungen`) return json(server.fassungen);
        const alt = /\/fassungen\/(\d+)$/.exec(url);
        if (alt) return server.alteFassungen[Number(alt[1])] ? json(server.alteFassungen[Number(alt[1])]) : json({ message: 'Unbekannte Fassung.' }, 404);
        if (url === `${BASIS}/WIDERRUFSBELEHRUNG/vorschau`) return new Response('%PDF-1.4', { headers: { 'Content-Type': 'application/pdf' } });
        return json({ message: 'Nicht gefunden' }, 404);
    }));
    return server;
}

function zeige() {
    render(<ToastProvider><ConfirmProvider><FesteTexteBereich /></ConfirmProvider></ToastProvider>);
}

async function oeffneDialog(user: ReturnType<typeof userEvent.setup>) {
    await user.click(await screen.findByRole('button', { name: 'Widerrufsbelehrung bearbeiten' }));
    const dialog = await screen.findByRole('dialog', { name: 'Widerrufsbelehrung' });
    await within(dialog).findByRole('textbox', { name: 'Text der Widerrufsbelehrung' });
    return dialog;
}

const editor = (dialog: HTMLElement) => within(dialog).getByRole('textbox', { name: 'Text der Widerrufsbelehrung' });

async function tippeAmEnde(user: ReturnType<typeof userEvent.setup>, dialog: HTMLElement, text: string) {
    await user.click(within(dialog).getByText('Sie haben das Recht.'));
    await user.keyboard(text);
}

const putBodies = (server: Server) => server.aufrufe.filter(a => a.methode === 'PUT').map(a => a.body as { html: string });

describe('FesteTexteBereich', () => {
    beforeEach(() => { vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:http://localhost/pdf-1'), revokeObjectURL: vi.fn() })); });
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt die Karte mit Standardtext bzw. gespeicherter Fassung und Info-Button', async () => {
        starteServer();
        const { unmount } = render(<ToastProvider><ConfirmProvider><FesteTexteBereich /></ConfirmProvider></ToastProvider>);

        expect(screen.getByRole('heading', { name: 'Feste Texte' })).toBeInTheDocument();
        expect(await screen.findByText('Stand 13.10.2026 · Standardtext nach gesetzlichem Muster')).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Widerrufsbelehrung' })).toBeInTheDocument();

        const user = userEvent.setup();
        await user.click(screen.getByRole('button', { name: 'Mehr zu: Widerrufsbelehrung' }));
        const info = screen.getByRole('dialog', { name: 'Widerrufsbelehrung' });
        expect(info).toHaveTextContent('Was passiert im Hintergrund?');
        expect(info).toHaveTextContent('Seite /widerruf/');
        unmount();

        starteServer({ aktuell: detail({ fassung: 3, standardtext: false, geaendertAm: '2026-10-13T14:05:00' }) });
        zeige();
        expect(await screen.findByText('Stand 13.10.2026 · Fassung 3')).toBeInTheDocument();
    });

    it('zeigt einen Ladefehler mit „Erneut versuchen“', async () => {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(json({ message: 'Server nicht erreichbar.' }, 503))
            .mockResolvedValueOnce(json([{ art: 'WIDERRUFSBELEHRUNG', bezeichnung: 'Widerrufsbelehrung', fassung: 2, stand: '2026-10-01', standardtext: false, geaendertAm: null }]));
        vi.stubGlobal('fetch', fetchMock);
        zeige();

        expect(await screen.findAllByText('Server nicht erreichbar.')).not.toHaveLength(0);
        await userEvent.setup().click(screen.getByRole('button', { name: 'Erneut versuchen' }));
        expect(await screen.findByText('Stand 01.10.2026 · Fassung 2')).toBeInTheDocument();
    });

    it('fügt einen Platzhalter ein und speichert den Text als neue Fassung', async () => {
        const server = starteServer();
        const user = userEvent.setup();
        zeige();
        const dialog = await oeffneDialog(user);

        // Chips kommen aus der Antwort, nicht fest eingebaut.
        const chip = within(dialog).getByRole('button', { name: /Name, Anschrift, Telefon und E-Mail in einer Zeile/ });
        expect(chip).toHaveAttribute('title', 'Beispiel: Max Mustermann GmbH, Musterstraße 1, 12345 Musterstadt');
        expect(within(dialog).getByRole('button', { name: /Firmenname/ })).toBeInTheDocument();

        await tippeAmEnde(user, dialog, ' Kontakt: ');
        await user.click(chip);
        expect(editor(dialog)).toHaveTextContent('{{FIRMA_KONTAKT}}');

        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Widerrufsbelehrung' })).not.toBeInTheDocument());
        const [body] = putBodies(server);
        expect(body.html).toContain('Widerrufsrecht');
        expect(body.html).toContain('Kontakt: {{FIRMA_KONTAKT}}');
        expect(await screen.findByText('Gespeichert – gilt ab sofort als Fassung 1, Stand 14.10.2026.')).toBeInTheDocument();
        expect(screen.getByText('Stand 14.10.2026 · Fassung 1')).toBeInTheDocument();
    });

    it('meldet „Keine Änderung“, wenn das Backend die bisherige Fassung zurückgibt', async () => {
        const server = starteServer({ aktuell: detail({ fassung: 2, standardtext: false }) });
        server.put = () => json(server.aktuell);
        const user = userEvent.setup();
        zeige();
        const dialog = await oeffneDialog(user);

        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        expect(await screen.findByText('Keine Änderung – nichts Neues gespeichert.')).toBeInTheDocument();
        expect(screen.getByText('Stand 13.10.2026 · Fassung 2')).toBeInTheDocument();
    });

    it('zeigt eine 400-Meldung verständlich im Dialog und lässt ihn offen', async () => {
        const server = starteServer();
        server.put = () => json({ message: 'Der Text ist zu lang.' }, 400);
        const user = userEvent.setup();
        zeige();
        const dialog = await oeffneDialog(user);

        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        expect(await within(dialog).findByRole('alert')).toHaveTextContent('Der Text ist zu lang.');
        expect(screen.getByRole('dialog', { name: 'Widerrufsbelehrung' })).toBeInTheDocument();
    });

    it('lehnt einen leeren Text schon vor dem Speichern ab', async () => {
        const server = starteServer({ aktuell: detail({ html: '<p></p>' }) });
        const user = userEvent.setup();
        zeige();
        const dialog = await oeffneDialog(user);

        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        expect(await within(dialog).findByRole('alert')).toHaveTextContent('Der Text ist leer.');
        expect(putBodies(server)).toHaveLength(0);
    });

    it('setzt nach Bestätigung auf den Standardtext zurück', async () => {
        const server = starteServer({ aktuell: detail({ fassung: 2, standardtext: false }) });
        const user = userEvent.setup();
        zeige();
        const dialog = await oeffneDialog(user);

        await user.click(within(dialog).getByRole('button', { name: 'Auf Standardtext zurücksetzen' }));
        // Erst abbrechen: nichts passiert.
        const frage = await screen.findByRole('dialog', { name: 'Auf Standardtext zurücksetzen?' });
        expect(frage).toHaveTextContent('Ihre bisherigen Fassungen bleiben erhalten.');
        await user.click(within(frage).getByRole('button', { name: 'Abbrechen' }));
        expect(server.aufrufe.some(a => a.pfad.endsWith('/standard'))).toBe(false);

        await user.click(within(dialog).getByRole('button', { name: 'Auf Standardtext zurücksetzen' }));
        await user.click(within(await screen.findByRole('dialog', { name: 'Auf Standardtext zurücksetzen?' })).getByRole('button', { name: 'Zurücksetzen' }));

        expect(await screen.findByText('Standardtext gilt ab sofort als Fassung 3, Stand 14.10.2026.')).toBeInTheDocument();
        expect(editor(dialog)).toHaveTextContent('Standardtext nach Muster.');
        expect(within(dialog).getByText('Stand 14.10.2026 · Fassung 3')).toBeInTheDocument();
    });

    it('zeigt eine frühere Fassung bereinigt an und übernimmt sie in den Editor', async () => {
        const boese = '<h3>Alte Fassung</h3><p>Alter Text<img src=x onerror="window.__xss=1"></p><script>window.__xss=2</script>';
        starteServer({
            aktuell: detail({ fassung: 2, standardtext: false }),
            fassungen: [
                { fassung: 2, stand: '2026-10-13', geaendertAm: '2026-10-13T14:05:00' },
                { fassung: 1, stand: '2026-10-01', geaendertAm: '2026-10-01T08:00:00' },
            ],
            alteFassungen: { 1: { fassung: 1, stand: '2026-10-01', geaendertAm: '2026-10-01T08:00:00', html: boese } },
        });
        const user = userEvent.setup();
        zeige();
        const dialog = await oeffneDialog(user);

        await user.click(await within(dialog).findByRole('button', { name: /Fassung 1 · Stand 01\.10\.2026/ }));

        const ansicht = await within(dialog).findByTestId('fruehere-fassung');
        expect(ansicht).toHaveTextContent('Alter Text');
        expect(ansicht.querySelector('img, script')).toBeNull();
        expect(ansicht.innerHTML).not.toContain('onerror');
        expect((window as unknown as { __xss?: number }).__xss).toBeUndefined();

        await user.click(within(dialog).getByRole('button', { name: 'In den Editor übernehmen' }));

        expect(within(dialog).queryByTestId('fruehere-fassung')).not.toBeInTheDocument();
        expect(editor(dialog)).toHaveTextContent('Alter Text');
        expect(editor(dialog).querySelector('img, script')).toBeNull();
        expect(await screen.findByText('Fassung 1 steht jetzt im Editor – noch nicht gespeichert.')).toBeInTheDocument();
    });

    it('fügt den Satz zum Online-Widerruf unten an', async () => {
        const server = starteServer();
        const user = userEvent.setup();
        zeige();
        const dialog = await oeffneDialog(user);

        await user.click(within(dialog).getByRole('button', { name: 'Mehr zu: Vorschlag: Online-Widerruf' }));
        expect(screen.getByRole('dialog', { name: 'Vorschlag: Online-Widerruf' })).toHaveTextContent('§ 356a BGB');
        await user.keyboard('{Escape}');

        await user.click(within(dialog).getByRole('button', { name: 'Satz einfügen' }));
        expect(editor(dialog)).toHaveTextContent('Sie können Ihr Widerrufsrecht auch online unter [Internet-Adresse eintragen] ausüben.');

        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(putBodies(server)).toHaveLength(1));
        expect(putBodies(server)[0].html).toMatch(/<p>Sie haben das Recht\.<\/p><p>Sie können Ihr Widerrufsrecht auch online/);
    });

    it('fragt beim Abbrechen mit ungespeicherten Änderungen nach', async () => {
        starteServer();
        const user = userEvent.setup();
        zeige();
        const dialog = await oeffneDialog(user);
        await tippeAmEnde(user, dialog, ' Neu');

        await user.click(within(dialog).getByRole('button', { name: 'Abbrechen' }));
        const frage = await screen.findByRole('dialog', { name: 'Änderungen verwerfen?' });
        await user.click(within(frage).getByRole('button', { name: 'Weiter bearbeiten' }));
        expect(screen.getByRole('dialog', { name: 'Widerrufsbelehrung' })).toBeInTheDocument();

        await user.click(within(dialog).getByRole('button', { name: 'Abbrechen' }));
        await user.click(within(await screen.findByRole('dialog', { name: 'Änderungen verwerfen?' })).getByRole('button', { name: 'Verwerfen' }));
        await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Widerrufsbelehrung' })).not.toBeInTheDocument());
    });

    it('schließt ohne Rückfrage, wenn nichts geändert wurde', async () => {
        starteServer();
        const user = userEvent.setup();
        zeige();
        const dialog = await oeffneDialog(user);

        await user.click(within(dialog).getByRole('button', { name: 'Abbrechen' }));

        expect(screen.queryByRole('dialog', { name: 'Änderungen verwerfen?' })).not.toBeInTheDocument();
        await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Widerrufsbelehrung' })).not.toBeInTheDocument());
    });

    it('zeigt die PDF-Vorschau des aktuellen Editorinhalts; Escape schließt nur die Vorschau', async () => {
        const server = starteServer();
        const user = userEvent.setup();
        zeige();
        const dialog = await oeffneDialog(user);
        await tippeAmEnde(user, dialog, ' Neu');

        await user.click(within(dialog).getByRole('button', { name: 'Vorschau (PDF)' }));

        expect(await screen.findByRole('heading', { name: 'Vorschau: Widerrufsbelehrung' })).toBeInTheDocument();
        expect(screen.getByTestId('pdf-anzeige')).toHaveTextContent('blob:http://localhost/pdf-1');
        expect(screen.getByText('Die Vorschau zeigt den Text aus dem Editor – gespeichert ist damit noch nichts.')).toBeInTheDocument();
        const anfrage = server.aufrufe.find(a => a.pfad.endsWith('/vorschau'));
        expect((anfrage?.body as { html: string }).html).toContain('Neu');

        await user.keyboard('{Escape}');

        expect(screen.queryByRole('heading', { name: 'Vorschau: Widerrufsbelehrung' })).not.toBeInTheDocument();
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:http://localhost/pdf-1');
        // Der Bearbeiten-Dialog bleibt offen, ohne Rückfrage, mit dem Entwurf.
        expect(screen.getByRole('dialog', { name: 'Widerrufsbelehrung' })).toBeInTheDocument();
        expect(screen.queryByRole('dialog', { name: 'Änderungen verwerfen?' })).not.toBeInTheDocument();
        expect(editor(dialog)).toHaveTextContent('Neu');
    });
});
