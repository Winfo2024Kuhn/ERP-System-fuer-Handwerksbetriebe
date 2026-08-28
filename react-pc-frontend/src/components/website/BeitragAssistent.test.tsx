import { useEffect } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { BeitragAssistent } from './BeitragAssistent';

// Praefix "mock" ist hier Pflicht: vi.mock-Fabriken werden vor die Imports
// gehoben, nur "mock"-praefigierte Variablen duerfen darin referenziert werden.
const mockSchrittTextMounts = vi.fn();

vi.mock('../ProjektSearchModal', () => ({
    ProjektSearchModal: ({ isOpen, onSelect }: {
        isOpen: boolean; onSelect: (p: { id: number; bauvorhaben: string }) => void;
    }) => isOpen ? (
        <button onClick={() => onSelect({ id: 1, bauvorhaben: 'Balkonanlage' })}>
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

vi.mock('./schritte/SchrittText', () => ({
    // useEffect mit leeren Abhaengigkeiten feuert genau einmal je Mount.
    // So laesst sich beweisen, dass ein Schrittwechsel die Komponente nicht
    // neu einhaengt (siehe Test "mountet SchrittText nicht erneut...").
    SchrittText: ({ onStandAendern }: { onStandAendern: (s: unknown) => void }) => {
        useEffect(() => { mockSchrittTextMounts(); }, []);
        return (
            <button onClick={() => onStandAendern({
                titel: 'Balkonanlage erweitert',
                kurzbeschreibung: 'Kurz gefasst.',
                textHtml: '<p>Neuer Belag.</p>',
            })}>
                Text setzen
            </button>
        );
    },
}));

// Das Rendern eines Bildes braucht Canvas, das jsdom nicht hat.
// Der Assistent faellt bei fehlendem Kontext auf einen leeren Blob zurueck.
vi.mock('./bildRendern', () => ({
    rendereBlob: vi.fn(() => Promise.resolve(new Blob(['x'], { type: 'image/jpeg' }))),
}));

let fetchMock: ReturnType<typeof vi.fn>;

function server() {
    return vi.fn((url: string, opt?: RequestInit) => {
        const methode = opt?.method ?? 'GET';
        if (url === '/api/beitraege' && methode === 'POST') {
            return Promise.resolve({
                ok: true, status: 201,
                json: () => Promise.resolve({ id: 42, images: [], status: 'draft' }),
            });
        }
        if (url === '/api/beitraege/42/bilder') {
            return Promise.resolve({
                ok: true, status: 201,
                json: () => Promise.resolve({
                    id: 42, status: 'draft',
                    images: [{ id: 99, postId: 42, path: 'a.webp', altText: null, sortOrder: 0, isCover: false }],
                }),
            });
        }
        return Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve({ id: 42, images: [], status: 'draft' }),
        });
    });
}

/** Klickt den Assistenten bis zum Speichern durch. */
async function durchlaufen(user: ReturnType<typeof userEvent.setup>) {
    await user.click(screen.getByRole('button', { name: 'Projekt wählen' }));
    await user.click(await screen.findByRole('button', { name: 'Bild wählen' }));
    await user.click(screen.getByRole('button', { name: 'Weiter' }));
    await user.click(await screen.findByRole('button', { name: 'Selbst schreiben' }));
    await user.click(await screen.findByRole('button', { name: 'Text setzen' }));
}

beforeEach(() => {
    fetchMock = server();
    vi.stubGlobal('fetch', fetchMock);
    mockSchrittTextMounts.mockClear();
});

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('BeitragAssistent', () => {
    it('zeigt nichts, solange er geschlossen ist', () => {
        const { container } = render(
            <BeitragAssistent offen={false} onAbbrechen={vi.fn()} onFertig={vi.fn()} />);

        expect(container).toBeEmptyDOMElement();
    });

    it('startet mit der Projektauswahl', () => {
        render(<BeitragAssistent offen onAbbrechen={vi.fn()} onFertig={vi.fn()} />);

        expect(screen.getByRole('button', { name: 'Projekt wählen' })).toBeInTheDocument();
    });

    it('zeigt das gewählte Projekt in der Kopfzeile', async () => {
        const user = userEvent.setup();
        render(<BeitragAssistent offen onAbbrechen={vi.fn()} onFertig={vi.fn()} />);

        await user.click(screen.getByRole('button', { name: 'Projekt wählen' }));

        expect(await screen.findByText('Balkonanlage')).toBeInTheDocument();
    });

    it('hält die Speicherreihenfolge ein', async () => {
        const user = userEvent.setup();
        render(<BeitragAssistent offen onAbbrechen={vi.fn()} onFertig={vi.fn()} />);
        await durchlaufen(user);

        await user.click(screen.getByRole('button', { name: 'Als Entwurf speichern' }));

        await waitFor(() => {
            const rufe = fetchMock.mock.calls.map((c: unknown[]) =>
                `${(c[1] as RequestInit)?.method ?? 'GET'} ${c[0] as string}`);
            expect(rufe[0]).toBe('POST /api/beitraege');
            expect(rufe[1]).toBe('POST /api/beitraege/42/bilder');
        });
    });

    it('meldet den fertigen Beitrag nach oben', async () => {
        const user = userEvent.setup();
        const onFertig = vi.fn();
        render(<BeitragAssistent offen onAbbrechen={vi.fn()} onFertig={onFertig} />);
        await durchlaufen(user);

        await user.click(screen.getByRole('button', { name: 'Als Entwurf speichern' }));

        await waitFor(() => expect(onFertig).toHaveBeenCalledWith(42));
    });

    it('setzt beim Veröffentlichen zusätzlich den Status', async () => {
        const user = userEvent.setup();
        render(<BeitragAssistent offen onAbbrechen={vi.fn()} onFertig={vi.fn()} />);
        await durchlaufen(user);

        await user.click(screen.getByRole('button', { name: 'Veröffentlichen' }));

        await waitFor(() => {
            const rufe = fetchMock.mock.calls.map((c: unknown[]) => c[0] as string);
            expect(rufe).toContain('/api/beitraege/42/status');
        });
    });

    it('meldet einen Abbruch mitten im Bilder-Upload mit Zahl der übertragenen Bilder', async () => {
        const user = userEvent.setup();
        let bilderRufe = 0;
        vi.stubGlobal('fetch', vi.fn((url: string, opt?: RequestInit) => {
            if (url === '/api/beitraege' && opt?.method === 'POST') {
                return Promise.resolve({
                    ok: true, status: 201, json: () => Promise.resolve({ id: 42, images: [], status: 'draft' }),
                });
            }
            if (url === '/api/beitraege/42/bilder') {
                bilderRufe += 1;
                return Promise.resolve({
                    ok: false, status: 502, json: () => Promise.resolve({ message: 'weg' }),
                });
            }
            return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ id: 42, images: [] }) });
        }));

        render(<BeitragAssistent offen onAbbrechen={vi.fn()} onFertig={vi.fn()} />);
        await durchlaufen(user);
        await user.click(screen.getByRole('button', { name: 'Als Entwurf speichern' }));

        expect(await screen.findByText(/als Entwurf angelegt/i)).toBeInTheDocument();
        expect(screen.getByText(/0 von 1 Bild/i)).toBeInTheDocument();
        expect(bilderRufe).toBe(1);
    });

    it('speichert nicht ohne Titel und Text', async () => {
        const user = userEvent.setup();
        render(<BeitragAssistent offen onAbbrechen={vi.fn()} onFertig={vi.fn()} />);
        await user.click(screen.getByRole('button', { name: 'Projekt wählen' }));
        await user.click(await screen.findByRole('button', { name: 'Bild wählen' }));
        await user.click(screen.getByRole('button', { name: 'Weiter' }));
        await user.click(await screen.findByRole('button', { name: 'Selbst schreiben' }));

        expect(screen.getByRole('button', { name: 'Als Entwurf speichern' })).toBeDisabled();
    });

    it('mountet SchrittText bei Text -> Bilder -> Text nicht neu, damit die KI nicht doppelt startet', async () => {
        const user = userEvent.setup();
        render(<BeitragAssistent offen onAbbrechen={vi.fn()} onFertig={vi.fn()} />);

        await user.click(screen.getByRole('button', { name: 'Projekt wählen' }));
        await user.click(await screen.findByRole('button', { name: 'Bild wählen' }));
        await user.click(screen.getByRole('button', { name: 'Weiter' }));
        await user.click(await screen.findByRole('button', { name: 'Von der KI vorschlagen lassen' }));
        await screen.findByRole('button', { name: 'Text setzen' });

        expect(mockSchrittTextMounts).toHaveBeenCalledTimes(1);

        // Text -> Weg -> Bilder, dann wieder vor bis Text.
        await user.click(screen.getByRole('button', { name: 'Zurück' }));
        await user.click(screen.getByRole('button', { name: 'Zurück' }));
        await user.click(screen.getByRole('button', { name: 'Weiter' }));
        await user.click(await screen.findByRole('button', { name: 'Von der KI vorschlagen lassen' }));
        await screen.findByRole('button', { name: 'Text setzen' });

        // Weiterhin genau einmal gemountet: SchrittText blieb im Baum, nur
        // versteckt, sonst haette der automatische KI-Lauf ein zweites Mal
        // gefeuert und von Hand geschriebenen Text ueberschrieben.
        expect(mockSchrittTextMounts).toHaveBeenCalledTimes(1);
    });
});
