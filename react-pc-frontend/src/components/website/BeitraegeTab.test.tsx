import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ConfirmProvider } from '../ui/confirm-dialog';
import { ToastProvider } from '../ui/toast';
import { BeitraegeTab, type BeitraegeTabProps } from './BeitraegeTab';

vi.mock('./BeitragRichtextEditor', () => ({
    BeitragRichtextEditor: ({ html, onChange }: { html: string; onChange: (h: string) => void }) => (
        <textarea
            aria-label="Text"
            value={html}
            onChange={e => onChange(e.target.value)}
        />
    ),
}));

// Die drei Bausteine des Bild-hinzufuegen-Dialogs sind schon einzeln getestet
// (ProjektSearchModal, SchrittBilder, bildRendern) und bekommen hier nur
// einfache Stellvertreter -- genau wie in BeitragAssistent.test.tsx, das
// dieselben Bausteine fuer den Assistenten verwendet.
// Bildet den echten ProjektSearchModal nach: handleSelect ruft dort erst
// onSelect und direkt danach onClose auf. Fehlt das onClose hier, bleibt
// unbemerkt, dass der Dialog sich beim Waehlen selbst schliesst.
vi.mock('../ProjektSearchModal', () => ({
    ProjektSearchModal: ({ isOpen, onSelect, onClose }: {
        isOpen: boolean;
        onSelect: (p: { id: number; bauvorhaben: string }) => void;
        onClose: () => void;
    }) => isOpen ? (
        <button onClick={() => { onSelect({ id: 5, bauvorhaben: 'Balkonanlage' }); onClose(); }}>
            Projekt wählen
        </button>
    ) : null,
}));

vi.mock('./schritte/SchrittBilder', () => ({
    SchrittBilder: ({ onAuswahlAendern }: { onAuswahlAendern: (a: unknown[]) => void }) => (
        <button onClick={() => onAuswahlAendern([{
            bild: {
                schluessel: 'notiz-1', quelle: 'bautagebuch',
                url: '/api/dokumente/tor.jpg', thumbnailUrl: '/api/dokumente/tor.jpg/thumbnail',
                originalDateiname: 'tor.jpg', datum: null, hinweis: null,
            },
            bearbeitung: { zuschnitt: null, drehung: 0, spiegelnX: false, spiegelnY: false, helligkeit: 100, kontrast: 100 },
        }])}>
            Bild wählen
        </button>
    ),
}));

// Das Rendern eines Bildes braucht Canvas, das jsdom nicht hat.
vi.mock('./bildRendern', () => ({
    rendereBlob: vi.fn(() => Promise.resolve(new Blob(['x'], { type: 'image/jpeg' }))),
}));

const liste = [
    {
        id: 1, slug: 'neues-tor', title: 'Neues Tor', excerpt: 'Kurz.',
        status: 'draft', publishedAt: null, coverImagePath: null,
    },
    {
        id: 2, slug: 'gelaender', title: 'Geländer montiert', excerpt: 'Auch kurz.',
        status: 'published', publishedAt: '2026-08-01 09:00:00', coverImagePath: 'g.webp',
    },
];

const detail = {
    ...liste[0],
    content: '<p>Wir haben ein Schiebetor gesetzt.</p>',
    images: [
        { id: 10, postId: 1, path: 'a.webp', altText: 'Tor', sortOrder: 0, isCover: true },
        { id: 11, postId: 1, path: 'b.webp', altText: null, sortOrder: 1, isCover: false },
    ],
};

/** Antwort nach einem erfolgreichen Bild-Upload: ein drittes Bild kommt dazu. */
const detailMitDrittemBild = {
    ...detail,
    images: [...detail.images, { id: 12, postId: 1, path: 'c.webp', altText: null, sortOrder: 2, isCover: false }],
};

let fetchMock: ReturnType<typeof vi.fn>;

/** Antwortet je nach URL und Methode. Letzter Eintrag gewinnt. */
function serverMit(overrides: Record<string, unknown> = {}) {
    return vi.fn((url: string, opt?: RequestInit) => {
        const methode = opt?.method ?? 'GET';
        const schluessel = `${methode} ${url}`;
        if (schluessel in overrides) {
            return Promise.resolve(overrides[schluessel]);
        }
        if (url === '/api/beitraege' && methode === 'GET') {
            return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(liste) });
        }
        if (url === '/api/beitraege/1') {
            return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(detail) });
        }
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(detail) });
    });
}

/**
 * BeitraegeTab braucht sowohl den Bestaetigungsdialog als auch die Toasts
 * (Speichern/Fehler) ueber Context-Hooks. Im echten Programm stellt App.tsx
 * beide global bereit, hier deshalb von Hand.
 */
function baum(props: Partial<BeitraegeTabProps> = {}) {
    return (
        <ConfirmProvider>
            <ToastProvider>
                <BeitraegeTab {...props} />
            </ToastProvider>
        </ConfirmProvider>
    );
}

function zeige(props: Partial<BeitraegeTabProps> = {}) {
    return render(baum(props));
}

/** Zaehlt, wie oft die Liste per GET neu vom Server geholt wurde. */
function anzahlListenAbrufe() {
    return fetchMock.mock.calls.filter(
        (c: unknown[]) => c[0] === '/api/beitraege'
            && ((c[1] as RequestInit | undefined)?.method ?? 'GET') === 'GET'
    ).length;
}

describe('BeitraegeTab', () => {
    beforeEach(() => {
        fetchMock = serverMit();
        vi.stubGlobal('fetch', fetchMock);
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it('listet die Beiträge mit ihrem Status in deutscher Sprache', async () => {
        zeige();

        expect(await screen.findByText('Neues Tor')).toBeInTheDocument();
        expect(screen.getByText('Geländer montiert')).toBeInTheDocument();
        expect(screen.getByText('Entwurf')).toBeInTheDocument();
        expect(screen.getByText('Veröffentlicht')).toBeInTheDocument();
    });

    it('öffnet einen Beitrag im Editor', async () => {
        const user = userEvent.setup();
        zeige();

        await user.click(await screen.findByText('Neues Tor'));

        expect(await screen.findByDisplayValue('Neues Tor')).toBeInTheDocument();
        expect(screen.getByLabelText('Text')).toHaveValue('<p>Wir haben ein Schiebetor gesetzt.</p>');
    });

    it('warnt, dass sich die Web-Adresse später nicht mehr ändert', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByText('Neues Tor'));

        expect(await screen.findByText(/Adresse.*ändert sich später nicht/i)).toBeInTheDocument();
    });

    it('übernimmt die Kurzbeschreibung aus dem Text', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByText('Neues Tor'));
        await screen.findByDisplayValue('Neues Tor');

        await user.click(screen.getByRole('button', { name: 'Aus dem Text übernehmen' }));

        expect(screen.getByLabelText('Kurzbeschreibung'))
            .toHaveValue('Wir haben ein Schiebetor gesetzt.');
    });

    it('speichert Titel, Kurzbeschreibung und Text zusammen per PATCH', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByText('Neues Tor'));
        const titel = await screen.findByDisplayValue('Neues Tor');

        await user.clear(titel);
        await user.type(titel, 'Neues Hallentor');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => {
            const patch = fetchMock.mock.calls.find(
                (c: unknown[]) => (c[1] as RequestInit)?.method === 'PATCH'
                    && (c[0] as string) === '/api/beitraege/1');
            expect(patch).toBeDefined();
            expect(JSON.parse((patch![1] as RequestInit).body as string).title)
                .toBe('Neues Hallentor');
        });
    });

    it('fragt vor dem Veröffentlichen nach', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByText('Neues Tor'));
        await screen.findByDisplayValue('Neues Tor');

        await user.click(screen.getByRole('button', { name: 'Veröffentlichen' }));

        expect(await screen.findByText(/wirklich veröffentlichen/i)).toBeInTheDocument();
    });

    it('setzt ein Titelbild', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByText('Neues Tor'));
        await screen.findByDisplayValue('Neues Tor');

        await user.click(screen.getByRole('button', { name: 'Als Titelbild setzen' }));

        await waitFor(() => {
            const ruf = fetchMock.mock.calls.find(
                (c: unknown[]) => (c[0] as string) === '/api/beitraege/1/titelbild');
            expect(ruf).toBeDefined();
            expect(JSON.parse((ruf![1] as RequestInit).body as string).imageId).toBe(11);
        });
    });

    it('zeigt die Vorschau auf Knopfdruck', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByText('Neues Tor'));
        await screen.findByDisplayValue('Neues Tor');

        await user.click(screen.getByRole('button', { name: 'Vorschau' }));

        expect(screen.getByText('Aktuelles')).toBeInTheDocument();
    });

    it('meldet eine nicht erreichbare Website verständlich', async () => {
        vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({
            ok: false, status: 502, json: () => Promise.resolve({ message: 'Website nicht erreichbar.' }),
        })));

        zeige();

        expect(await screen.findByText(/Website ist gerade nicht erreichbar/i)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Erneut versuchen' })).toBeInTheDocument();
    });

    it('zeigt einen Leerzustand, wenn es noch keinen Beitrag gibt', async () => {
        vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({
            ok: true, status: 200, json: () => Promise.resolve([]),
        })));

        zeige();

        expect(await screen.findByText(/Noch kein Beitrag angelegt/i)).toBeInTheDocument();
    });

    it('sperrt den Knopf "Neuer Beitrag", solange keine Funktion dafür übergeben wurde', async () => {
        zeige();

        const knopf = await screen.findByRole('button', { name: 'Neuer Beitrag' });
        expect(knopf).toBeDisabled();
        expect(knopf).toHaveAttribute('title', 'Diese Funktion ist noch nicht fertig.');
    });

    it('lässt den Knopf "Neuer Beitrag" klicken und ruft onNeuerBeitrag auf, wenn er übergeben wurde', async () => {
        const user = userEvent.setup();
        const onNeuerBeitrag = vi.fn();
        zeige({ onNeuerBeitrag });

        const knopf = await screen.findByRole('button', { name: 'Neuer Beitrag' });
        expect(knopf).not.toBeDisabled();

        await user.click(knopf);

        expect(onNeuerBeitrag).toHaveBeenCalledTimes(1);
    });

    it('lädt die Liste neu, wenn neuLadenSignal hochgezählt wird', async () => {
        const { rerender } = zeige({ neuLadenSignal: 0 });
        await screen.findByText('Neues Tor');
        const rufeVorher = anzahlListenAbrufe();

        rerender(baum({ neuLadenSignal: 1 }));

        await waitFor(() => {
            expect(anzahlListenAbrufe()).toBeGreaterThan(rufeVorher);
        });
    });

    it('öffnet über "Bild hinzufügen" zuerst die Projektsuche', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByText('Neues Tor'));
        await screen.findByDisplayValue('Neues Tor');

        await user.click(screen.getByRole('button', { name: 'Bild hinzufügen' }));

        expect(screen.getByRole('button', { name: 'Projekt wählen' })).toBeInTheDocument();
    });

    it('lädt ein ausgewähltes Bild hoch und zeigt den aufgefrischten Beitrag', async () => {
        const user = userEvent.setup();
        fetchMock = serverMit({
            'POST /api/beitraege/1/bilder': {
                ok: true, status: 201, json: () => Promise.resolve(detailMitDrittemBild),
            },
        });
        vi.stubGlobal('fetch', fetchMock);
        zeige();
        await user.click(await screen.findByText('Neues Tor'));
        await screen.findByDisplayValue('Neues Tor');

        await user.click(screen.getByRole('button', { name: 'Bild hinzufügen' }));
        await user.click(screen.getByRole('button', { name: 'Projekt wählen' }));
        await user.click(await screen.findByRole('button', { name: 'Bild wählen' }));
        await user.click(await screen.findByRole('button', { name: /^Hinzufügen/ }));

        await waitFor(() => {
            const ruf = fetchMock.mock.calls.find(
                (c: unknown[]) => (c[0] as string) === '/api/beitraege/1/bilder'
                    && (c[1] as RequestInit)?.method === 'POST');
            expect(ruf).toBeDefined();
        });
        expect(await screen.findByText('Bilder (3)')).toBeInTheDocument();
        // Der Dialog schließt sich nach dem Erfolg wieder von selbst.
        expect(screen.queryByRole('button', { name: 'Projekt wählen' })).not.toBeInTheDocument();
    });

    it('bricht die Bildauswahl ab, ohne etwas hochzuladen', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByText('Neues Tor'));
        await screen.findByDisplayValue('Neues Tor');

        await user.click(screen.getByRole('button', { name: 'Bild hinzufügen' }));
        await user.click(screen.getByRole('button', { name: 'Projekt wählen' }));
        await user.click(await screen.findByRole('button', { name: 'Bild wählen' }));
        await user.click(screen.getByRole('button', { name: 'Abbrechen' }));

        expect(screen.queryByRole('button', { name: 'Projekt wählen' })).not.toBeInTheDocument();
        expect(fetchMock.mock.calls.some(
            (c: unknown[]) => (c[0] as string) === '/api/beitraege/1/bilder')).toBe(false);
    });

    it('beschriftet die Bildfelder verständlich statt mit roher Datenbank-Kennung', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByText('Neues Tor'));
        await screen.findByDisplayValue('Neues Tor');

        expect(screen.getByLabelText('Bildbeschreibung für Bild 1 von 2')).toBeInTheDocument();
        expect(screen.getByLabelText('Bildbeschreibung für Bild 2 von 2')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Bild 1 von 2 löschen' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Bild 2 von 2 löschen' })).toBeInTheDocument();
    });

    // Frueher stand hier fuer jeden Beitrag mit Titelbild nur ein graues
    // Sinnbild. Der Handwerker erkennt seine Baustelle aber am Foto viel
    // schneller als am Titel.
    it('zeigt das Titelbild eines Beitrags in der Liste', async () => {
        zeige();

        const bild = await screen.findByAltText('Titelbild von Geländer montiert');
        // Geht ueber die Durchreiche des ERP, nicht direkt zur Website.
        expect(bild).toHaveAttribute('src', '/api/beitraege/bild/g.webp');
        expect(bild).toHaveAttribute('loading', 'lazy');
    });

    it('zeigt kein Bild bei einem Beitrag ohne Titelbild', async () => {
        zeige();

        await screen.findByText('Neues Tor');
        expect(screen.queryByAltText('Titelbild von Neues Tor')).not.toBeInTheDocument();
    });

    it('warnt, statt eine geleerte Bildbeschreibung stumm zu verwerfen', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByText('Neues Tor'));
        await screen.findByDisplayValue('Neues Tor');

        const feld = screen.getByLabelText('Bildbeschreibung für Bild 1 von 2');
        await user.clear(feld);
        await user.tab();

        expect(await screen.findByText('Die Bildbeschreibung darf nicht leer sein.')).toBeInTheDocument();
        expect(fetchMock.mock.calls.some((c: unknown[]) =>
            (c[0] as string) === '/api/beitraege/1/bilder/10'
            && (c[1] as RequestInit)?.method === 'PATCH')).toBe(false);
    });
});
