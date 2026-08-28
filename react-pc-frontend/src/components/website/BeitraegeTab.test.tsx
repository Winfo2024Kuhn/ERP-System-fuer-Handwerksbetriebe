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
});
