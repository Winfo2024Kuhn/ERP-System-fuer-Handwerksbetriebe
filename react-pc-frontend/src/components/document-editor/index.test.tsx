import type { ComponentProps } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';
import DocumentEditor from './index';

// DSGVO: ausschliesslich Dummy-Daten.
const ALTE_ADRESSE = 'Max Mustermann\nMusterweg 1\n12345 Musterstadt';
const NEUE_ADRESSE = 'Max Mustermann\nNeue Gasse 7\n54321 Beispielstadt';

// Bewusst "heute": Bei einem noch nicht versendeten Dokument laesst der Editor
// das Datum mit heute mitlaufen. Ein fest verdrahtetes Datum waere ab dem
// Folgetag Vergangenheit, der Editor wuerde es bumpen und das Dokument allein
// deshalb als "Ungespeichert" melden — die Adress-Assertions unten haetten
// nichts damit zu tun.
const HEUTE_ISO = new Date().toISOString().split('T')[0];

const dokumentAntwort = {
    id: 1,
    typ: 'RECHNUNG',
    dokumentNummer: 'RE-2026/08/00001',
    datum: HEUTE_ISO,
    betreff: 'Dachsanierung',
    betragNetto: 1000,
    htmlInhalt: '',
    // Bewusst ohne Bloecke: der Test zielt auf die Kopfdaten, und ohne
    // Textbloecke braucht es keine Tiptap-Instanzen in jsdom.
    positionenJson: JSON.stringify({ blocks: [], globalRabatt: 0 }),
    kundenName: 'Max Mustermann',
    kundennummer: 'K-4711',
    rechnungsadresse: ALTE_ADRESSE,
    rechnungsadresseOverride: ALTE_ADRESSE,
    gebucht: false,
    storniert: false,
    zahlungszielTage: 14,
};

// DSGVO: Dummy-Artikeldaten, keine Personenbezuege.
const ARTIKEL_TREFFER = [
    {
        id: 7, produktname: 'T-Stahl', abmessung: '40 x 40 x 5',
        kurzbeschreibung: 'T-Stahl 40x40 Lager',
        beschreibung: '<p>T-Stahl 40 x 40 x 5 mm, verzinkt</p>',
        positionsEinheit: 'lfm', positionsEinzelpreis: 8.4, preisHinweis: 'OK',
    },
    {
        id: 8, produktname: 'Vierkantrohr', abmessung: '40 x 40 x 2',
        positionsEinheit: 'lfm', preisHinweis: 'KEIN_PREIS',
    },
];

/** Alle PUT-Bodies, mit denen das Dokument gespeichert wurde. */
function speicherAufrufe(fetchMock: ReturnType<typeof mockFetch>): Array<Record<string, unknown>> {
    return fetchMock.mock.calls
        .filter(call => {
            const url = call[0] as string;
            const init = call[1] as RequestInit | undefined;
            return typeof url === 'string'
                && url === '/api/ausgangs-dokumente/1'
                && init?.method === 'PUT';
        })
        .map(call => JSON.parse((call[1] as RequestInit).body as string));
}

/** Anzahl der angeforderten Vorschau-PDFs. */
function vorschauAufrufe(fetchMock: ReturnType<typeof mockFetch>): number {
    return fetchMock.mock.calls.filter(call => call[0] === '/api/dokument-generator/preview').length;
}

function jsonAntwort(body: unknown) {
    return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve(body),
        text: () => Promise.resolve(JSON.stringify(body)),
    });
}

/**
 * `dokumentAbweichung` erlaubt Tests, das geladene Dokument punktuell zu
 * veraendern (z.B. `digitalAngenommen: true` fuer den gesperrten Zustand),
 * ohne den restlichen Mock-Aufbau zu duplizieren.
 */
function mockFetch(dokumentAbweichung: Record<string, unknown> = {}) {
    const dokument = { ...dokumentAntwort, ...dokumentAbweichung };
    return vi.fn((url: string, init?: RequestInit) => {
        if (url === '/api/ausgangs-dokumente/1' && init?.method === 'PUT') {
            const gesendet = JSON.parse(init.body as string);
            // Server antwortet mit dem gespeicherten Stand.
            return jsonAntwort({ ...dokument, ...gesendet });
        }
        if (url === '/api/ausgangs-dokumente/1') {
            return jsonAntwort(dokument);
        }
        if (url === '/api/dokument-generator/preview') {
            return Promise.resolve({
                ok: true,
                status: 200,
                blob: () => Promise.resolve(new Blob(['%PDF-1.4'], { type: 'application/pdf' })),
            });
        }
        if (url.startsWith('/api/formulare/templates/selection')) {
            return jsonAntwort({ templateName: null });
        }
        if (url.startsWith('/api/artikel/filteroptionen')) {
            // Contract von ArtikelSuche.tsx: herstellverfahren/fertigungszustand,
            // nicht produktlinien/werkstoffe/profilformen.
            return jsonAntwort({ herstellverfahren: [], fertigungszustand: [] });
        }
        if (url.startsWith('/api/artikel/werkstoffe')) {
            // Eigenes Array-Ergebnis, damit es nicht versehentlich vom
            // /api/artikel-Catch-all (Paging-Objekt) verschluckt wird.
            return jsonAntwort([]);
        }
        if (url.startsWith('/api/artikel')) {
            return jsonAntwort({ artikel: ARTIKEL_TREFFER, gesamt: 2, seite: 0, seitenGroesse: 20 });
        }
        // Textbausteine, Leistungen, Arbeitszeitarten, Abrechnungsverlauf, ...
        return jsonAntwort([]);
    });
}

function renderEditor(props: Partial<ComponentProps<typeof DocumentEditor>> = {}) {
    return render(
        <MemoryRouter initialEntries={['/dokumente/1']}>
            <ConfirmProvider>
                <ToastProvider>
                    <DocumentEditor dokumentId={1} onClose={() => { }} {...props} />
                </ToastProvider>
            </ConfirmProvider>
        </MemoryRouter>
    );
}

/**
 * Der X-Knopf in der Kopfzeile (DocumentEditorHeader.tsx, nicht Teil dieses
 * Tasks) hat kein aria-label -- er ist damit ohne Testing-Library-Rolle
 * erreichbar. Er ist zuverlaessig der erste Button im gerenderten Baum.
 */
function xKnopf(container: HTMLElement): HTMLElement {
    const knopf = container.querySelector('button');
    if (!knopf) throw new Error('X-Knopf nicht gefunden');
    return knopf;
}

/** Alle Requests an die alten (dokument-locks) oder neuen (datensatz-locks) Lock-Endpunkte. */
function lockAufrufe(fetchMock: ReturnType<typeof mockFetch>): string[] {
    return fetchMock.mock.calls
        .map(call => call[0] as string)
        .filter(url => typeof url === 'string' && (url.includes('/dokument-locks/') || url.includes('/datensatz-locks/')));
}

/** Wie mockFetch, aber der PUT ans Dokument schlaegt serverseitig fehl. */
function mockFetchMitFehlschlagendemSpeichern() {
    const basis = mockFetch();
    return vi.fn((url: string, init?: RequestInit) => {
        if (url === '/api/ausgangs-dokumente/1' && init?.method === 'PUT') {
            return Promise.resolve({
                ok: false,
                status: 500,
                statusText: 'Internal Server Error',
                text: () => Promise.resolve('Serverfehler beim Speichern.'),
                json: () => Promise.resolve({}),
            });
        }
        return basis(url, init);
    });
}

/** Adresse ueber den Inline-Editor aendern und bestaetigen. */
async function adresseAendern(user: ReturnType<typeof userEvent.setup>, neueAdresse: string) {
    await user.click(screen.getByTitle('Rechnungsadresse für dieses Dokument bearbeiten'));
    const textarea = await screen.findByRole('textbox', { name: /Rechnungsadresse bearbeiten/i });
    await user.clear(textarea);
    await user.paste(neueAdresse);
    await user.click(screen.getByRole('button', { name: /Übernehmen/i }));
}

describe('DocumentEditor – Rechnungsadresse', () => {
    let fetchMock: ReturnType<typeof mockFetch>;

    beforeEach(() => {
        fetchMock = mockFetch();
        global.fetch = fetchMock as unknown as typeof fetch;
        // jsdom kennt keine Blob-URLs.
        global.URL.createObjectURL = vi.fn(() => `blob:vorschau-${Math.random()}`);
        global.URL.revokeObjectURL = vi.fn();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('speichert die geänderte Adresse und nicht den Stand davor', async () => {
        // Regression: handleSave las `kontextDaten` aus einer eingefrorenen
        // useCallback-Closure. Da eine reine Adressänderung keine der Deps
        // berührt, ging die Adresse VOR der Änderung ans Backend.
        const user = userEvent.setup();
        renderEditor();

        // Grosszuegiger Timeout: der Initial-Load laeuft ueber mehrere
        // sequentielle Fetch-Roundtrips.
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });

        await adresseAendern(user, NEUE_ADRESSE);
        await user.click(screen.getByRole('button', { name: /Speichern/i }));

        await waitFor(() => expect(speicherAufrufe(fetchMock).length).toBeGreaterThan(0));
        const letzterBody = speicherAufrufe(fetchMock).at(-1)!;
        expect(letzterBody.rechnungsadresseOverride).toBe(NEUE_ADRESSE);
    });

    it('rendert die Vorschau nach dem Speichern neu', async () => {
        const user = userEvent.setup();
        renderEditor();

        // Grosszuegiger Timeout: der Initial-Load laeuft ueber mehrere
        // sequentielle Fetch-Roundtrips.
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        await waitFor(() => expect(vorschauAufrufe(fetchMock)).toBeGreaterThan(0));

        const vorDemSpeichern = vorschauAufrufe(fetchMock);

        await adresseAendern(user, NEUE_ADRESSE);
        await user.click(screen.getByRole('button', { name: /Speichern/i }));

        await waitFor(() => expect(vorschauAufrufe(fetchMock)).toBeGreaterThan(vorDemSpeichern));
    });

    it('meldet eine unbestätigte Adressänderung als ungespeichert', async () => {
        const user = userEvent.setup();
        renderEditor();

        // Grosszuegiger Timeout: der Initial-Load laeuft ueber mehrere
        // sequentielle Fetch-Roundtrips.
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        await adresseAendern(user, NEUE_ADRESSE);

        expect(await screen.findByText(/^Ungespeichert$/)).toBeInTheDocument();
    });

    it('löst bei unveränderter Adresse kein Speichern aus', async () => {
        // "Übernehmen" ohne echte Änderung darf die Adressvererbung vom
        // Vorgang nicht kappen und kein Dirty-Flag setzen.
        const user = userEvent.setup();
        renderEditor();

        // Grosszuegiger Timeout: der Initial-Load laeuft ueber mehrere
        // sequentielle Fetch-Roundtrips.
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        await adresseAendern(user, ALTE_ADRESSE);

        // Positiv-Anker zuerst, damit die Negativ-Assertion nicht bloss zu
        // frueh greift: die Adresse steht unveraendert da.
        expect(await screen.findByText(/Musterweg 1/)).toBeInTheDocument();
        expect(screen.queryByText(/^Ungespeichert$/)).not.toBeInTheDocument();
        expect(speicherAufrufe(fetchMock)).toHaveLength(0);
    });
});

describe('DocumentEditor – Material einfügen', () => {
    let fetchMock: ReturnType<typeof mockFetch>;

    beforeEach(() => {
        fetchMock = mockFetch();
        global.fetch = fetchMock as unknown as typeof fetch;
        // jsdom kennt keine Blob-URLs.
        global.URL.createObjectURL = vi.fn(() => `blob:vorschau-${Math.random()}`);
        global.URL.revokeObjectURL = vi.fn();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    /** Oeffnet das Material-Auswahlfenster ueber den Knopf in der Kopfzeile. */
    async function materialFensterOeffnen(user: ReturnType<typeof userEvent.setup>) {
        await user.click(await screen.findByRole('button', { name: 'Material' }));
    }

    it('öffnet über den Material-Knopf das Auswahlfenster', async () => {
        const user = userEvent.setup();
        renderEditor();

        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        await materialFensterOeffnen(user);

        expect(await screen.findByText('Material auswählen')).toBeInTheDocument();
    });

    it('speichert die Materialauswahl als SERVICE-Block mit artikelId', async () => {
        const user = userEvent.setup();
        renderEditor();

        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        await materialFensterOeffnen(user);

        await user.click(await screen.findByLabelText('T-Stahl auswählen'));
        await user.clear(screen.getByLabelText('Menge für T-Stahl'));
        await user.type(screen.getByLabelText('Menge für T-Stahl'), '12');
        await user.click(screen.getByRole('button', { name: /Übernehmen/i }));

        await user.click(screen.getByRole('button', { name: /Speichern/i }));

        await waitFor(() => expect(speicherAufrufe(fetchMock).length).toBeGreaterThan(0));
        const gespeichert = speicherAufrufe(fetchMock).at(-1)!;
        const bloecke = JSON.parse(gespeichert.positionenJson as string).blocks;
        const material = bloecke.find((b: Record<string, unknown>) => b.artikelId === 7);

        expect(material).toMatchObject({
            type: 'SERVICE',
            title: 'T-Stahl 40x40 Lager',
            description: '<p>T-Stahl 40 x 40 x 5 mm, verzinkt</p>',
            quantity: 12,
            unit: 'lfm',
            price: 8.4,
            artikelId: 7,
        });
    });

    it('übernimmt einen Artikel ohne ermittelbaren Preis mit price 0', async () => {
        // ARTIKEL_TREFFER[1] (id 8) hat bewusst kein positionsEinzelpreis -
        // deckt den Fallback in ArtikelAuswahlDialog.tsx ab, der einen
        // fehlenden Preis zu 0 macht statt die Position zu verwerfen.
        const user = userEvent.setup();
        renderEditor();

        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        await materialFensterOeffnen(user);

        await user.click(await screen.findByLabelText('Vierkantrohr auswählen'));
        await user.click(screen.getByRole('button', { name: /Übernehmen/i }));

        await user.click(screen.getByRole('button', { name: /Speichern/i }));

        await waitFor(() => expect(speicherAufrufe(fetchMock).length).toBeGreaterThan(0));
        const gespeichert = speicherAufrufe(fetchMock).at(-1)!;
        const bloecke = JSON.parse(gespeichert.positionenJson as string).blocks;
        const material = bloecke.find((b: Record<string, unknown>) => b.artikelId === 8);

        expect(material).toMatchObject({
            type: 'SERVICE',
            title: 'Vierkantrohr 40 x 40 x 2',
            // Ohne gepflegten Kundentext baut das Auswahlfenster einen aus den
            // Stammdaten - sonst druckte das PDF ersatzweise den title, also die
            // Innensicht (siehe kundentext.ts).
            description: '<p>Vierkantrohr, 40 x 40 x 2 mm</p>',
            unit: 'lfm',
            price: 0,
            artikelId: 8,
        });
    });

    it('übernimmt mehrere Artikel in einem Rutsch, fortlaufend in Auswahlreihenfolge', async () => {
        // Regression-Schutz fuer die im Code begruendete Entscheidung, ALLE
        // Bloecke ueber einen einzigen insertBlocksBeforeClosure-Aufruf
        // einzufuegen statt wiederholt addBlock aufzurufen: Ein wiederholter
        // addBlock-Aufruf wuerde bei jedem Schritt auf einem veralteten
        // blocks-State rechnen und die Positionsnummern durcheinanderbringen.
        const user = userEvent.setup();
        renderEditor();

        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        await materialFensterOeffnen(user);

        await user.click(await screen.findByLabelText('T-Stahl auswählen'));
        await user.click(screen.getByLabelText('Vierkantrohr auswählen'));
        await user.click(screen.getByRole('button', { name: /Übernehmen/i }));

        await user.click(screen.getByRole('button', { name: /Speichern/i }));

        await waitFor(() => expect(speicherAufrufe(fetchMock).length).toBeGreaterThan(0));
        const gespeichert = speicherAufrufe(fetchMock).at(-1)!;
        const bloecke = JSON.parse(gespeichert.positionenJson as string).blocks;
        const materialArtikelIds = bloecke
            .filter((b: Record<string, unknown>) => b.type === 'SERVICE' && b.artikelId != null)
            .map((b: Record<string, unknown>) => b.artikelId);

        expect(materialArtikelIds).toEqual([7, 8]);
    });

    it('schließt das Fenster nach dem Übernehmen', async () => {
        const user = userEvent.setup();
        renderEditor();

        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        await materialFensterOeffnen(user);
        await user.click(await screen.findByLabelText('T-Stahl auswählen'));
        await user.click(screen.getByRole('button', { name: /Übernehmen/i }));

        await waitFor(() => expect(screen.queryByText('Material auswählen')).not.toBeInTheDocument());
    });

    it('zeigt den Material-Knopf bei digital angenommenem Dokument nicht', async () => {
        fetchMock = mockFetch({ digitalAngenommen: true });
        global.fetch = fetchMock as unknown as typeof fetch;

        renderEditor();

        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        expect(screen.queryByRole('button', { name: 'Material' })).not.toBeInTheDocument();
    });
});

describe('DocumentEditor – kein eigenes Lock mehr', () => {
    // Regression: der Editor hatte frueher einen zweiten, nie gestoppten
    // Heartbeat (auf /api/dokument-locks/AUSGANG/.../heartbeat) UND ein
    // eigenes Acquire, zusaetzlich zum Lock-Hook der Seite. Beides ist raus
    // -- der Editor haelt und beruehrt gar kein Lock mehr, das macht
    // ausschliesslich die Seite (useDatensatzLock, anderer Task).
    let fetchMock: ReturnType<typeof mockFetch>;

    beforeEach(() => {
        fetchMock = mockFetch();
        global.fetch = fetchMock as unknown as typeof fetch;
        global.URL.createObjectURL = vi.fn(() => `blob:vorschau-${Math.random()}`);
        global.URL.revokeObjectURL = vi.fn();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('sendet nach Mount und Unmount keinen Request an /api/dokument-locks/ oder /api/datensatz-locks/', async () => {
        const { unmount } = renderEditor();
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });

        // Kurze Pause: ein sofortiger Heartbeat-Ping direkt nach dem Mount
        // (so verhielt sich die fruehere zweite Lock-Schleife) haette hier
        // laengst gefeuert.
        await new Promise(resolve => setTimeout(resolve, 50));
        unmount();

        expect(lockAufrufe(fetchMock)).toEqual([]);
    });
});

describe('DocumentEditor – readOnly-Prop', () => {
    let fetchMock: ReturnType<typeof mockFetch>;

    beforeEach(() => {
        fetchMock = mockFetch();
        global.fetch = fetchMock as unknown as typeof fetch;
        global.URL.createObjectURL = vi.fn(() => `blob:vorschau-${Math.random()}`);
        global.URL.revokeObjectURL = vi.fn();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('verhaelt sich wie ein gesperrtes Dokument (kein Material-Knopf, kein Speichern)', async () => {
        renderEditor({ readOnly: true });

        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        expect(screen.queryByRole('button', { name: 'Material' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Speichern/i })).not.toBeInTheDocument();
    });
});

describe('DocumentEditor – Tab schließen (X-Button-Ablauf)', () => {
    let fetchMock: ReturnType<typeof mockFetch>;

    beforeEach(() => {
        fetchMock = mockFetch();
        global.fetch = fetchMock as unknown as typeof fetch;
        global.URL.createObjectURL = vi.fn(() => `blob:vorschau-${Math.random()}`);
        global.URL.revokeObjectURL = vi.fn();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('haelt die Reihenfolge speichern -> onLockFreigeben -> window.close ein', async () => {
        const user = userEvent.setup();
        const reihenfolge: string[] = [];

        const basisFetch = mockFetch();
        fetchMock = vi.fn((url: string, init?: RequestInit) => {
            if (url === '/api/ausgangs-dokumente/1' && init?.method === 'PUT') {
                reihenfolge.push('speichern');
            }
            return basisFetch(url, init);
        });
        global.fetch = fetchMock as unknown as typeof fetch;

        const onLockFreigeben = vi.fn(async () => { reihenfolge.push('onLockFreigeben'); });
        vi.spyOn(window, 'close').mockImplementation(() => { reihenfolge.push('window.close'); });

        const { container } = renderEditor({ onLockFreigeben });
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        await adresseAendern(user, NEUE_ADRESSE);

        await user.click(xKnopf(container));
        await user.click(await screen.findByRole('button', { name: /Speichern & Schließen/ }));

        await waitFor(() => expect(reihenfolge).toContain('window.close'));
        expect(reihenfolge).toEqual(['speichern', 'onLockFreigeben', 'window.close']);
    });

    it('zeigt den Tab-Schließen-Hinweis, wenn window.close wirkungslos bleibt', async () => {
        // Simuliert genau den gemeldeten Fehler (macOS Safari): window.close()
        // tut nichts, weil der Browser es nicht zulaesst.
        const user = userEvent.setup();
        vi.spyOn(window, 'close').mockImplementation(() => { /* wirkungslos */ });
        const onLockFreigeben = vi.fn().mockResolvedValue(undefined);

        const { container } = renderEditor({ onLockFreigeben });
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });

        // Keine ungespeicherten Aenderungen: das X schliesst direkt, ohne
        // den Warn-Dialog.
        await user.click(xKnopf(container));

        // Ueber den Text suchen, nicht ueber die Rolle: @dnd-kit/core rendert
        // waehrend der ganzen Editor-Lebensdauer selbst eine unsichtbare
        // role="status"-Ankuendigung (Drag&Drop-Screenreader-Live-Region) ohne
        // eigenen aria-label -- getByRole('status', {name}) faende die nie
        // (kein "Name aus Inhalt" fuer die Rolle status), aber ein blosses
        // getByRole('status') faende IMMER zuerst die von dnd-kit.
        const hinweis = await screen.findByText(
            /kannst diesen Tab jetzt schließen/,
            undefined,
            { timeout: 2000 },
        );
        expect(hinweis).toHaveAttribute('role', 'status');
        expect(onLockFreigeben).toHaveBeenCalledTimes(1);
    });

    it('bricht bei fehlgeschlagenem Speichern ab: kein onLockFreigeben, kein window.close, Toast, Editor bleibt offen', async () => {
        const user = userEvent.setup();
        fetchMock = mockFetchMitFehlschlagendemSpeichern();
        global.fetch = fetchMock as unknown as typeof fetch;

        const onLockFreigeben = vi.fn().mockResolvedValue(undefined);
        const closeSpy = vi.spyOn(window, 'close').mockImplementation(() => { });

        const { container } = renderEditor({ onLockFreigeben });
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        await adresseAendern(user, NEUE_ADRESSE);

        await user.click(xKnopf(container));
        await user.click(await screen.findByRole('button', { name: /Speichern & Schließen/ }));

        expect(await screen.findByText(/Speichern fehlgeschlagen/)).toBeInTheDocument();
        expect(onLockFreigeben).not.toHaveBeenCalled();
        expect(closeSpy).not.toHaveBeenCalled();
        // Editor (samt Warn-Dialog) bleibt offen -- kein TabSchliessenHinweis.
        expect(screen.getByRole('button', { name: /Speichern & Schließen/ })).toBeInTheDocument();
    });
});
