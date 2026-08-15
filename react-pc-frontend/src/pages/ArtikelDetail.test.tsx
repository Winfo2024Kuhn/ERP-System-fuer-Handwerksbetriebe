import type { ReactNode } from 'react';
import { render, screen, waitFor, within, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import ArtikelDetail from './ArtikelDetail';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';

// Der echte Tiptap-Editor braucht Browser-APIs, die jsdom nicht bereitstellt
// (z. B. document.createRange). Fuer diese Seite reicht ein steuerbarer Stub -
// ein Textfeld, ueber das Tests den HTML-Inhalt direkt setzen koennen, ohne
// den echten Rich-Text-Editor zu benoetigen.
vi.mock('../components/TiptapEditor', () => ({
    TiptapEditor: ({ value, onChange }: { value: string; onChange: (value: string) => void }) => (
        <textarea data-testid="tiptap" value={value} onChange={(e) => onChange(e.target.value)} />
    ),
}));

/**
 * Testdaten mit Dummy-Lieferanten (DSGVO: keine echten Firmennamen).
 * Der Artikel entspricht einem Gelaenderrohr 42,4 x 2 aus Baustahl.
 */
const detailAntwort = {
    artikel: {
        id: 1,
        artikelnummer: 'ST-RR-42.4-2',
        produktname: '42.4x2',
        werkstoffName: 'Baustahl S235JR',
        kategoriePfad: 'Werkstoffe > Hohlprofile > Rundrohr',
        massnorm: 'EN 10219-2',
        werkstoffnorm: 'EN 10025-2',
        abmessung: '42,4 x 2',
        durchmesser: 42.4,
        wandstaerke: 2,
        kgProMeter: 1.9926,
        mantelflaeche: 0.1332,
        standardlaenge: 6000,
        verzinkungsgeeignet: true,
        pulverbeschichtungsgeeignet: true,
        beschichtungshinweis: 'Feuerverzinken und Pulverbeschichten moeglich.',
        profilform: { name: 'RUNDROHR', anzeigename: 'Rundrohr' },
        herstellverfahren: { name: 'GESCHWEISST', anzeigename: 'geschweisst', erklaerung: 'Aus Band gerollt.' },
        fertigungszustand: { name: 'KALTGEFERTIGT', anzeigename: 'kaltgefertigt', erklaerung: 'Bei Raumtemperatur geformt.' },
    },
    lieferanten: [
        {
            preisId: 10, lieferantId: 1, lieferantName: 'Muster Stahlhandel',
            externeArtikelnummer: 'M-1000', preis: 10, preisDatum: '2026-07-01T00:00:00Z',
            quelle: { name: 'MANUELL', anzeigename: 'Von Hand eingetragen' },
            guenstigster: true,
        },
        {
            preisId: 11, lieferantId: 2, lieferantName: 'Beispiel Metall GmbH',
            externeArtikelnummer: 'B-2000', preis: 12.5, preisDatum: '2026-07-15T00:00:00Z',
            quelle: { name: 'ANGEBOT_EMAIL', anzeigename: 'Aus Angebots-Mail uebernommen' },
            guenstigster: false, aufschlagProzent: 25,
        },
    ],
    preisverlauf: [
        { preisId: 11, lieferantId: 2, lieferantName: 'Beispiel Metall GmbH', preis: 12.5, preisDatum: '2026-07-15T00:00:00Z', aktuell: true },
        { preisId: 10, lieferantId: 1, lieferantName: 'Muster Stahlhandel', preis: 10, preisDatum: '2026-07-01T00:00:00Z', aktuell: true },
        { preisId: 9, lieferantId: 2, lieferantName: 'Beispiel Metall GmbH', preis: 11, preisDatum: '2026-05-01T00:00:00Z', aktuell: false },
    ],
};

function renderSeite() {
    return render(
        <ToastProvider>
            <ConfirmProvider>
                <MemoryRouter initialEntries={['/artikel/1']}>
                    <Routes>
                        <Route path="/artikel/:id" element={<ArtikelDetail />} />
                    </Routes>
                </MemoryRouter>
            </ConfirmProvider>
        </ToastProvider>,
    );
}

/** Platzhalter fuer die Artikelliste, der die aufgerufene Suche sichtbar macht. */
function ListenPlatzhalter() {
    const location = useLocation();
    return <div>Liste{location.search}</div>;
}

/**
 * Rendert die Detailseite so, wie man sie aus der Liste heraus erreicht:
 * Die gefilterte Liste liegt als vorheriger Eintrag in der History.
 */
function renderAusListeHeraus(listenAdresse: string, vonListe = true) {
    return render(
        <ToastProvider>
            <ConfirmProvider>
                <MemoryRouter
                    initialEntries={[listenAdresse, { pathname: '/artikel/1', state: vonListe ? { vonListe: true } : null }]}
                    initialIndex={1}
                >
                    <Routes>
                        <Route path="/artikel" element={<ListenPlatzhalter />} />
                        <Route path="/artikel/:id" element={<ArtikelDetail />} />
                    </Routes>
                </MemoryRouter>
            </ConfirmProvider>
        </ToastProvider>,
    );
}

/** Wrapper fuer render(ui, { wrapper: Wrapper }) - stellt Route/Params/Toast/Confirm bereit. */
function Wrapper({ children }: { children: ReactNode }) {
    return (
        <ToastProvider>
            <ConfirmProvider>
                <MemoryRouter initialEntries={['/artikel/7']}>
                    <Routes>
                        <Route path="/artikel/:id" element={children} />
                    </Routes>
                </MemoryRouter>
            </ConfirmProvider>
        </ToastProvider>
    );
}

/**
 * Fetch-Mock, der die uebergebene Detail-Antwort fuer GET liefert. PATCH auf
 * .../dokumenttexte wird wie beim echten Endpunkt beantwortet: Der Server
 * gibt den vollstaendig aktualisierten Artikel zurueck (Rumpf ueber die
 * unveraenderten Felder gemergt), nicht nur ein leeres Objekt - sonst liesse
 * sich das "Felder zeigen nach dem Speichern den Server-Stand"-Verhalten gar
 * nicht sinnvoll testen.
 */
let fetchMock: ReturnType<typeof vi.fn>;

function mockFetchDetail(antwort: {
    artikel: Record<string, unknown>;
    lieferanten: unknown[];
    preisverlauf: unknown[];
}) {
    fetchMock = vi.fn((url: string, options?: RequestInit) => {
        if (options?.method === 'PATCH') {
            const gesendet = JSON.parse(String(options.body));
            const aktualisiert = { ...antwort.artikel, ...gesendet };
            return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(aktualisiert) });
        }
        // Der Abschnitt "Bilder & Unterlagen" laedt seine eigene, unabhaengige
        // Liste - ohne diese Weiche bekaeme er faelschlich das Detail-Objekt statt
        // eines Arrays und wuerde beim .find()/.filter() crashen.
        if (url.includes('/dokumente')) {
            return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) });
        }
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(antwort) });
    });
    vi.stubGlobal('fetch', fetchMock);
}

describe('ArtikelDetail', () => {
    beforeEach(() => {
        vi.stubGlobal('fetch', vi.fn((url: string) => {
            if (url.includes('/dokumente')) {
                return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) });
            }
            return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(detailAntwort) });
        }));
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('zeigt technische Daten und Normen', async () => {
        renderSeite();
        expect(await screen.findByText('42.4x2')).toBeInTheDocument();
        expect(screen.getByText('EN 10219-2')).toBeInTheDocument();
        expect(screen.getByText('42,4 x 2')).toBeInTheDocument();
        expect(screen.getByText('1,993 kg/m')).toBeInTheDocument();
    });

    it('zeigt das Herstellverfahren im Klartext, nicht nur die Norm', async () => {
        renderSeite();
        // Das ist der Kern: "EN 10219-2" allein sagt einem Handwerker nichts.
        expect(await screen.findByText('geschweisst')).toBeInTheDocument();
        expect(screen.getByText('kaltgefertigt')).toBeInTheDocument();
        expect(screen.getByText('Rundrohr')).toBeInTheDocument();
    });

    it('kennzeichnet die Eignungen mit Text statt nur mit Farbe', async () => {
        renderSeite();
        expect(await screen.findByText('Feuerverzinken')).toBeInTheDocument();
        expect(screen.getByText('Pulverbeschichten')).toBeInTheDocument();
        // Barrierefreiheit: die Aussage darf nicht allein an der Farbe haengen.
        expect(screen.getAllByText('möglich').length).toBe(2);
    });

    it('listet alle Lieferanten mit deren eigener Artikelnummer', async () => {
        renderSeite();
        // Auf die Lieferantentabelle eingrenzen: die Namen stehen zusaetzlich
        // in der Verlaufstabelle, was hier nicht gemeint ist.
        const tabelle = await screen.findByRole('table', {
            name: /Lieferanten mit Artikelnummer und aktuellem Preis/i,
        });
        expect(within(tabelle).getByText('Muster Stahlhandel')).toBeInTheDocument();
        expect(within(tabelle).getByText('M-1000')).toBeInTheDocument();
        expect(within(tabelle).getByText('Beispiel Metall GmbH')).toBeInTheDocument();
        expect(within(tabelle).getByText('B-2000')).toBeInTheDocument();
    });

    it('markiert den guenstigsten Anbieter und zeigt den Aufschlag der anderen', async () => {
        renderSeite();
        expect(await screen.findByText('günstigster')).toBeInTheDocument();
        expect(screen.getByText('+25 %')).toBeInTheDocument();
    });

    it('stellt den Preisverlauf auch als Tabelle bereit', async () => {
        renderSeite();
        // Das Diagramm allein ist nicht vorlesbar - die Tabelle ist die
        // barrierefreie Entsprechung und enthaelt alle Preisstaende.
        const tabelle = await screen.findByRole('table', {
            name: /Alle Preisstände mit Datum, Lieferant und Herkunft/i,
        });
        const zeilen = within(tabelle).getAllByRole('row');
        expect(zeilen.length).toBe(4); // Kopfzeile plus drei Preisstaende
        expect(within(tabelle).getByText('abgelöst')).toBeInTheDocument();
    });

    it('zeigt eine verstaendliche Meldung, wenn der Artikel fehlt', async () => {
        vi.stubGlobal('fetch', vi.fn(() =>
            Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve({}) }),
        ));
        renderSeite();
        expect(await screen.findByText('Dieser Artikel wurde nicht gefunden.')).toBeInTheDocument();
    });

    it('meldet einen Ladefehler, statt eine leere Seite zu zeigen', async () => {
        vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('Netzwerk'))));
        renderSeite();
        await waitFor(() => {
            expect(screen.getByText('Die Artikeldaten konnten nicht geladen werden.')).toBeInTheDocument();
        });
    });

    it('fuehrt "Zurück" auf die Liste mit der urspruenglichen Suche', async () => {
        const user = userEvent.setup();
        renderAusListeHeraus('/artikel?q=rohr&werkstoff=S235JR&page=2');

        await user.click(await screen.findByRole('button', { name: 'Zurück' }));

        // Der Kern: Die Suche darf nicht verloren gehen, sonst muss der Anwender
        // alle Filter erneut setzen, nur weil er einen Artikel angesehen hat.
        expect(await screen.findByText('Liste?q=rohr&werkstoff=S235JR&page=2')).toBeInTheDocument();
    });

    it('fuehrt "Zurück" auch aus der Fehleranzeige auf die urspruengliche Suche', async () => {
        vi.stubGlobal('fetch', vi.fn(() =>
            Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve({}) }),
        ));
        const user = userEvent.setup();
        renderAusListeHeraus('/artikel?q=rohr');

        await user.click(await screen.findByRole('button', { name: /Zurück zur Artikelliste/i }));

        expect(await screen.findByText('Liste?q=rohr')).toBeInTheDocument();
    });

    it('landet ohne Vorgeschichte auf der vollstaendigen Liste', async () => {
        const user = userEvent.setup();
        // Direkter Aufruf per Link oder Lesezeichen: Es gibt keine Suche, zu der
        // man zurueckkehren koennte.
        renderAusListeHeraus('/irgendwo', false);

        await user.click(await screen.findByRole('button', { name: 'Zurück' }));

        expect(await screen.findByText('Liste')).toBeInTheDocument();
    });

    it('zeigt den Abschnitt für Angebote mit den gepflegten Werten', async () => {
        mockFetchDetail({
            artikel: {
                id: 7,
                produktname: 'T-Stahl',
                kurzbeschreibung: 'T-Stahl 40x40 Lager',
                beschreibung: '<p>T-Stahl 40 x 40 x 5 mm</p>',
                verkaufsaufschlagProzent: 40,
                positionsEinheit: 'lfm',
                positionsEinzelpreis: 8.4,
                preisHinweis: 'OK',
            },
            lieferanten: [],
            preisverlauf: [],
        });

        render(<ArtikelDetail />, { wrapper: Wrapper });

        expect(await screen.findByText('Für Angebote und Rechnungen')).toBeInTheDocument();

        // Der Abschnitt steht schon, waehrend die Eingabefelder noch leer sind:
        // ArtikelDetail rendert zuerst mit den geladenen `daten` und belegt die
        // Angebotsfelder erst in einem Folge-Effekt vor. Zwischen beiden Renders
        // liegt ein Commit mit sichtbarer Ueberschrift und leerem Feld - genau
        // den erwischt ein synchrones getBy hier unter Last. Deshalb auf den
        // Wert warten statt ihn sofort zu lesen (gleiche Zusicherung).
        await waitFor(() =>
            expect(screen.getByLabelText('Kurzbeschreibung')).toHaveValue('T-Stahl 40x40 Lager'));
        // Aufschlag und Beschreibung setzt derselbe Effekt im selben Batch -
        // ist die Kurzbeschreibung da, sind sie es auch.
        expect(screen.getByLabelText('Aufschlag auf den Einkaufspreis')).toHaveValue(40);
        expect(screen.getByText(/8,40/)).toBeInTheDocument();
    });

    it('warnt, wenn kein Aufschlag gepflegt ist', async () => {
        mockFetchDetail({
            artikel: {
                id: 8,
                produktname: 'Rundrohr',
                positionsEinheit: 'lfm',
                positionsEinzelpreis: 6,
                preisHinweis: 'KEIN_AUFSCHLAG',
            },
            lieferanten: [],
            preisverlauf: [],
        });

        render(<ArtikelDetail />, { wrapper: Wrapper });

        expect(await screen.findByText(/Kein Aufschlag hinterlegt/)).toBeInTheDocument();
    });

    it('speichert die Angebotsfelder über den PATCH-Endpunkt', async () => {
        mockFetchDetail({
            artikel: { id: 7, produktname: 'T-Stahl', preisHinweis: 'KEIN_AUFSCHLAG' },
            lieferanten: [],
            preisverlauf: [],
        });

        render(<ArtikelDetail />, { wrapper: Wrapper });

        await userEvent.type(await screen.findByLabelText('Kurzbeschreibung'), 'T-Stahl 40x40 Lager');
        await userEvent.click(screen.getByRole('button', { name: /Angebotsfelder speichern/ }));

        await waitFor(() => {
            expect(fetchMock).toHaveBeenCalledWith(
                '/api/artikel/7/dokumenttexte',
                expect.objectContaining({ method: 'PATCH' }),
            );
        });
    });

    it('sendet gepflegte Angebotsfelder mit ihren tatsächlichen Werten', async () => {
        mockFetchDetail({
            artikel: { id: 7, produktname: 'T-Stahl', preisHinweis: 'KEIN_AUFSCHLAG' },
            lieferanten: [],
            preisverlauf: [],
        });

        render(<ArtikelDetail />, { wrapper: Wrapper });

        await userEvent.type(await screen.findByLabelText('Kurzbeschreibung'), 'T-Stahl 40x40 Lager');
        fireEvent.change(screen.getByTestId('tiptap'), { target: { value: '<p>T-Stahl 40 x 40 x 5 mm</p>' } });
        fireEvent.change(screen.getByLabelText('Aufschlag auf den Einkaufspreis'), { target: { value: '40' } });

        await userEvent.click(screen.getByRole('button', { name: /Angebotsfelder speichern/ }));

        await waitFor(() => expect(fetchMock).toHaveBeenCalled());

        const patchAufruf = fetchMock.mock.calls.find(([, options]) => options?.method === 'PATCH');
        const gesendet = JSON.parse(String(patchAufruf?.[1]?.body));
        expect(gesendet).toEqual({
            kurzbeschreibung: 'T-Stahl 40x40 Lager',
            beschreibung: '<p>T-Stahl 40 x 40 x 5 mm</p>',
            verkaufsaufschlagProzent: 40,
        });
    });

    it('sendet ein geleertes Beschreibungsfeld als null, nicht als leeren Absatz', async () => {
        mockFetchDetail({
            artikel: { id: 7, produktname: 'T-Stahl', beschreibung: '<p>Alter Text</p>', preisHinweis: 'KEIN_AUFSCHLAG' },
            lieferanten: [],
            preisverlauf: [],
        });

        render(<ArtikelDetail />, { wrapper: Wrapper });

        // Tiptap liefert beim Leeren typischerweise einen leeren Absatz statt
        // eines leeren Strings.
        const editor = await screen.findByTestId('tiptap');
        // Das Feld existiert schon, bevor der Vorbelege-Effekt gelaufen ist.
        // Wer jetzt schreibt, dessen Eingabe wird gleich darauf vom Server-Stand
        // ueberschrieben - dann ginge '<p>Alter Text</p>' statt null ans Backend.
        // Also erst den vorbelegten Stand abwarten.
        await waitFor(() => expect(editor).toHaveValue('<p>Alter Text</p>'));
        fireEvent.change(editor, { target: { value: '<p>&nbsp;</p>' } });

        await userEvent.click(screen.getByRole('button', { name: /Angebotsfelder speichern/ }));

        await waitFor(() => expect(fetchMock).toHaveBeenCalled());

        const patchAufruf = fetchMock.mock.calls.find(([, options]) => options?.method === 'PATCH');
        const gesendet = JSON.parse(String(patchAufruf?.[1]?.body));
        expect(gesendet.beschreibung).toBeNull();
    });

    it('behält eine getippte Kurzbeschreibung, wenn die Artikeldaten neu laden', async () => {
        mockFetchDetail({
            artikel: { id: 7, produktname: 'T-Stahl', preisHinweis: 'KEIN_AUFSCHLAG' },
            lieferanten: [],
            preisverlauf: [],
        });

        render(<ArtikelDetail />, { wrapper: Wrapper });

        await userEvent.type(await screen.findByLabelText('Kurzbeschreibung'), 'T-Stahl 40x40 Lager');

        // Simuliert ein Neuladen der Artikeldaten, wie es z. B. nach dem
        // Nachtragen eines Lieferantenpreises passiert - der
        // "Aktualisieren"-Knopf ruft dieselbe laden()-Funktion auf.
        await userEvent.click(screen.getByRole('button', { name: 'Aktualisieren' }));

        await waitFor(() => {
            const getAufrufe = fetchMock.mock.calls.filter(([, options]) => options?.method === undefined);
            expect(getAufrufe.length).toBeGreaterThanOrEqual(2);
        });

        expect(await screen.findByLabelText('Kurzbeschreibung')).toHaveValue('T-Stahl 40x40 Lager');
    });

    it('übernimmt nach dem Speichern den vom Server bestätigten Stand', async () => {
        const patchAntwort = {
            id: 7,
            produktname: 'T-Stahl',
            kurzbeschreibung: 'T-Stahl 40x40 Lager (Server)',
            preisHinweis: 'KEIN_AUFSCHLAG',
        };
        fetchMock = vi.fn((url: string, options?: RequestInit) => {
            if (options?.method === 'PATCH') {
                return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(patchAntwort) });
            }
            if (url.includes('/dokumente')) {
                return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) });
            }
            return Promise.resolve({
                ok: true,
                status: 200,
                json: () => Promise.resolve({
                    artikel: { id: 7, produktname: 'T-Stahl', preisHinweis: 'KEIN_AUFSCHLAG' },
                    lieferanten: [],
                    preisverlauf: [],
                }),
            });
        });
        vi.stubGlobal('fetch', fetchMock);

        render(<ArtikelDetail />, { wrapper: Wrapper });

        await userEvent.type(await screen.findByLabelText('Kurzbeschreibung'), 'T-Stahl 40x40 Lager');
        await userEvent.click(screen.getByRole('button', { name: /Angebotsfelder speichern/ }));

        // Das Feld übernimmt den Wert aus der PATCH-Antwort - nicht bloß das,
        // was zuvor eingetippt wurde. Bestätigt, dass tatsächlich die Antwort
        // des Servers verwendet wird, auch wenn sie (z. B. durch serverseitige
        // Normalisierung) vom eingetippten Text abweicht.
        expect(await screen.findByLabelText('Kurzbeschreibung')).toHaveValue('T-Stahl 40x40 Lager (Server)');
    });

    it('sendet ein mit einer echten nbsp-Zeichenfolge geleertes Beschreibungsfeld ebenfalls als null', async () => {
        mockFetchDetail({
            artikel: { id: 7, produktname: 'T-Stahl', beschreibung: '<p>Alter Text</p>', preisHinweis: 'KEIN_AUFSCHLAG' },
            lieferanten: [],
            preisverlauf: [],
        });

        render(<ArtikelDetail />, { wrapper: Wrapper });

        // Anders als die Entity "&nbsp;" oben: hier steckt das echte
        // Unicode-Zeichen U+00A0 im HTML, wie es manche Editoren erzeugen.
        const editor = await screen.findByTestId('tiptap');
        // Gleiches Rennen wie oben: erst den vorbelegten Server-Stand abwarten,
        // sonst ueberschreibt der Vorbelege-Effekt die Leerung wieder.
        await waitFor(() => expect(editor).toHaveValue('<p>Alter Text</p>'));
        fireEvent.change(editor, { target: { value: '<p> </p>' } });

        await userEvent.click(screen.getByRole('button', { name: /Angebotsfelder speichern/ }));

        await waitFor(() => expect(fetchMock).toHaveBeenCalled());

        const patchAufruf = fetchMock.mock.calls.find(([, options]) => options?.method === 'PATCH');
        const gesendet = JSON.parse(String(patchAufruf?.[1]?.body));
        expect(gesendet.beschreibung).toBeNull();
    });
});
