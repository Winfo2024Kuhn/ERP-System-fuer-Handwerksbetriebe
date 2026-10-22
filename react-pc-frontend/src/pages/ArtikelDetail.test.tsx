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
            quelle: { name: 'ANGEBOT_EMAIL', anzeigename: 'Aus einem Angebot uebernommen' },
            guenstigster: false, aufschlagProzent: 25,
        },
    ],
    preisverlauf: [
        { preisId: 11, lieferantId: 2, lieferantName: 'Beispiel Metall GmbH', preis: 12.5, preisDatum: '2026-07-15T00:00:00Z', aktuell: true },
        { preisId: 10, lieferantId: 1, lieferantName: 'Muster Stahlhandel', preis: 10, preisDatum: '2026-07-01T00:00:00Z', aktuell: true },
        { preisId: 9, lieferantId: 2, lieferantName: 'Beispiel Metall GmbH', preis: 11, preisDatum: '2026-05-01T00:00:00Z', aktuell: false },
    ],
};

/** Zeigt die aktuelle Adresse samt Anker, damit Tests den offenen Reiter in der URL pruefen koennen. */
function Adresse() {
    const location = useLocation();
    return <output data-testid="adresse">{location.pathname + location.hash}</output>;
}

function renderSeite(anker = '') {
    return render(
        <ToastProvider>
            <ConfirmProvider>
                <MemoryRouter initialEntries={[`/artikel/1${anker}`]}>
                    <Routes>
                        <Route path="/artikel/:id" element={<><ArtikelDetail /><Adresse /></>} />
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

/**
 * Wrapper fuer render(ui, { wrapper: Wrapper }) - stellt Route/Params/Toast/Confirm bereit
 * und oeffnet gleich den Reiter "Fuer Angebote", um den es in diesen Tests geht.
 */
function Wrapper({ children }: { children: ReactNode }) {
    return (
        <ToastProvider>
            <ConfirmProvider>
                <MemoryRouter initialEntries={['/artikel/7#angebote']}>
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
        renderSeite('#angaben');
        expect(await screen.findByText('42.4x2')).toBeInTheDocument();
        expect(screen.getByText('EN 10219-2')).toBeInTheDocument();
        expect(screen.getByText('42,4 x 2')).toBeInTheDocument();
        expect(screen.getByText('1,993 kg/m')).toBeInTheDocument();
    });

    it('zeigt das Herstellverfahren im Klartext, nicht nur die Norm', async () => {
        renderSeite('#angaben');
        // Das ist der Kern: "EN 10219-2" allein sagt einem Handwerker nichts.
        expect(await screen.findByText('geschweisst')).toBeInTheDocument();
        expect(screen.getByText('kaltgefertigt')).toBeInTheDocument();
        expect(screen.getByText('Rundrohr')).toBeInTheDocument();
    });

    it('kennzeichnet die Eignungen mit Text statt nur mit Farbe', async () => {
        renderSeite('#angaben');
        expect(await screen.findByText('Feuerverzinken')).toBeInTheDocument();
        expect(screen.getByText('Pulverbeschichten')).toBeInTheDocument();
        // Barrierefreiheit: die Aussage darf nicht allein an der Farbe haengen.
        expect(screen.getAllByText('möglich').length).toBe(2);
    });

    it('listet alle Lieferanten mit deren eigener Artikelnummer', async () => {
        renderSeite('#preise');
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
        renderSeite('#preise');
        expect(await screen.findByText('günstigster')).toBeInTheDocument();
        expect(screen.getByText('+25 %')).toBeInTheDocument();
    });

    it('stellt den Preisverlauf auch als Tabelle bereit', async () => {
        renderSeite('#preise');
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
        expect(screen.getByLabelText('Aufschlag auf den Einkaufspreis')).toHaveValue('40');
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

    it('erklärt bei leerem Aufschlag am Artikel, welcher stattdessen gilt', async () => {
        mockFetchDetail({
            artikel: { id: 9, produktname: 'Rundrohr', positionsEinheit: 'lfm', positionsEinzelpreis: 12.5, preisHinweis: 'OK',
                wirksamProzent: 25, wirksamQuelle: 'KATEGORIE', wirksamVon: 'Stahl' },
            lieferanten: [],
            preisverlauf: [],
        });

        render(<ArtikelDetail />, { wrapper: Wrapper });

        expect(await screen.findByTestId('aufschlag-wirksam')).toHaveTextContent('Leer: es gilt 25 % von „Stahl“.');
    });

    it('nennt den allgemeinen Aufschlag und schweigt, wenn der Artikel einen eigenen hat', async () => {
        mockFetchDetail({
            artikel: { id: 9, produktname: 'Rundrohr', preisHinweis: 'OK', wirksamProzent: 20, wirksamQuelle: 'ALLGEMEIN' },
            lieferanten: [],
            preisverlauf: [],
        });
        const { unmount } = render(<ArtikelDetail />, { wrapper: Wrapper });
        expect(await screen.findByTestId('aufschlag-wirksam')).toHaveTextContent('Leer: es gilt der allgemeine Aufschlag von 20 %.');
        unmount();

        mockFetchDetail({
            artikel: { id: 9, produktname: 'Rundrohr', preisHinweis: 'OK', verkaufsaufschlagProzent: 40, wirksamProzent: 40, wirksamQuelle: 'ARTIKEL' },
            lieferanten: [],
            preisverlauf: [],
        });
        render(<ArtikelDetail />, { wrapper: Wrapper });
        const feld = await screen.findByLabelText('Aufschlag auf den Einkaufspreis');
        await waitFor(() => expect(feld).toHaveValue('40'));
        expect(screen.queryByTestId('aufschlag-wirksam')).not.toBeInTheDocument();
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

    it('behält ungespeicherte Angebotsfelder beim Reiterwechsel und kennzeichnet den Reiter', async () => {
        const user = userEvent.setup();
        mockFetchDetail({
            artikel: { id: 7, produktname: 'T-Stahl', preisHinweis: 'KEIN_AUFSCHLAG' },
            lieferanten: [],
            preisverlauf: [],
        });

        render(<ArtikelDetail />, { wrapper: Wrapper });

        await user.type(await screen.findByLabelText('Kurzbeschreibung'), 'T-Stahl 40x40 Lager');
        expect(screen.getByRole('tab', { name: /Für Angebote/ })).toHaveTextContent('nicht gespeichert');

        await user.click(screen.getByRole('tab', { name: /Übersicht/ }));
        expect(screen.queryByLabelText('Kurzbeschreibung')).toBeNull();
        await user.click(screen.getByRole('tab', { name: /Für Angebote/ }));
        expect(screen.getByLabelText('Kurzbeschreibung')).toHaveValue('T-Stahl 40x40 Lager');
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
    describe('Preis direkt in der Zeile ändern', () => {
        /** Fetch-Mock mit zwei Lieferanten; merkt sich die POSTs auf .../artikelpreise. */
        function mockMitPreisPost(postAntwort?: { ok: boolean; status: number; body: unknown }) {
            fetchMock = vi.fn((url: string, options?: RequestInit) => {
                if (options?.method === 'POST' && url.includes('/artikelpreise')) {
                    const a = postAntwort ?? { ok: true, status: 200, body: {} };
                    return Promise.resolve({ ok: a.ok, status: a.status, json: () => Promise.resolve(a.body) });
                }
                if (url.includes('/dokumente')) {
                    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) });
                }
                if (url.startsWith('/api/lieferanten')) {
                    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ lieferanten: [
                        { id: 2, lieferantenname: 'Beispiel Metall GmbH' },
                        { id: 3, lieferantenname: 'Muster Blechhandel' },
                    ] }) });
                }
                if (url.includes('/effektive-rollen')) {
                    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) });
                }
                return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(detailAntwort) });
            });
            vi.stubGlobal('fetch', fetchMock);
        }

        const postBodies = () => fetchMock.mock.calls
            .filter(([url, o]) => o?.method === 'POST' && String(url).includes('/artikelpreise'))
            .map(([url, o]) => ({ url: String(url), body: JSON.parse(String(o?.body)) }));
        const detailAbrufe = () => fetchMock.mock.calls.filter(([url, o]) => !o?.method && String(url).endsWith('/detail')).length;

        it('speichert mit Enter und liest "1.234,50" richtig - die Nummer bleibt unangetastet', async () => {
            const user = userEvent.setup();
            mockMitPreisPost();
            renderSeite('#preise');

            await user.click(await screen.findByRole('button', { name: /^Preis ändern bei Muster Stahlhandel/ }));
            const feld = screen.getByLabelText('Neuer Preis in Euro bei Muster Stahlhandel');
            // Der bisherige Preis steht schon drin und ist markiert - einfach drübertippen
            expect(feld).toHaveValue('10');
            expect(feld).toHaveFocus();
            await user.clear(feld);
            await user.type(feld, '1.234,50{Enter}');

            await waitFor(() => expect(postBodies()).toHaveLength(1));
            expect(postBodies()[0]).toEqual({
                url: '/api/lieferanten/1/artikelpreise',
                body: { artikelId: 1, preis: 1234.5, externeArtikelnummer: null },
            });
            expect(await screen.findByText('Preis gespeichert. Der bisherige Preis bleibt im Verlauf erhalten.')).toBeInTheDocument();
            // Danach neu geladen, Feld zu, Fokus wieder auf dem Preis
            await waitFor(() => expect(detailAbrufe()).toBe(2));
            await waitFor(() => expect(screen.getByRole('button', { name: /^Preis ändern bei Muster Stahlhandel/ })).toHaveFocus());
            expect(screen.queryByLabelText('Neuer Preis in Euro bei Muster Stahlhandel')).toBeNull();
        });

        it('bricht mit Escape ab, ohne zu speichern', async () => {
            const user = userEvent.setup();
            mockMitPreisPost();
            renderSeite('#preise');

            await user.click(await screen.findByRole('button', { name: /^Preis ändern bei Beispiel Metall GmbH/ }));
            await user.type(screen.getByLabelText('Neuer Preis in Euro bei Beispiel Metall GmbH'), '99{Escape}');

            expect(screen.queryByLabelText('Neuer Preis in Euro bei Beispiel Metall GmbH')).toBeNull();
            await waitFor(() => expect(screen.getByRole('button', { name: /^Preis ändern bei Beispiel Metall GmbH/ })).toHaveFocus());
            expect(postBodies()).toHaveLength(0);
        });

        it('zeigt die Meldung des Servers und lässt das Feld offen', async () => {
            const user = userEvent.setup();
            mockMitPreisPost({ ok: false, status: 400, body: { status: 400, message: 'Der Preis darf nicht negativ sein.' } });
            renderSeite('#preise');

            await user.click(await screen.findByRole('button', { name: /^Preis ändern bei Muster Stahlhandel/ }));
            const feld = screen.getByLabelText('Neuer Preis in Euro bei Muster Stahlhandel');
            await user.clear(feld);
            await user.type(feld, '5');
            await user.click(screen.getByRole('button', { name: 'Preis speichern' }));

            expect(await screen.findByText('Der Preis darf nicht negativ sein.')).toBeInTheDocument();
            expect(screen.getByLabelText('Neuer Preis in Euro bei Muster Stahlhandel')).toHaveValue('5');
        });

        it('lehnt einen unlesbaren Preis ab, ohne zu speichern', async () => {
            const user = userEvent.setup();
            mockMitPreisPost();
            renderSeite('#preise');

            await user.click(await screen.findByRole('button', { name: /^Preis ändern bei Muster Stahlhandel/ }));
            const feld = screen.getByLabelText('Neuer Preis in Euro bei Muster Stahlhandel');
            await user.clear(feld);
            await user.type(feld, 'abc{Enter}');

            expect(await screen.findByText('Bitte einen gültigen Preis eingeben, z. B. 12,50.')).toBeInTheDocument();
            expect(postBodies()).toHaveLength(0);
        });

        it('schickt bei geänderter Lieferanten-Nummer den aktuellen Preis mit', async () => {
            const user = userEvent.setup();
            mockMitPreisPost();
            renderSeite('#preise');

            await user.click(await screen.findByRole('button', { name: /^Nummer ändern bei Beispiel Metall GmbH/ }));
            const feld = screen.getByLabelText('Neue Nummer bei Beispiel Metall GmbH');
            expect(feld).toHaveValue('B-2000');
            await user.clear(feld);
            await user.type(feld, 'B-2001{Enter}');

            await waitFor(() => expect(postBodies()).toHaveLength(1));
            expect(postBodies()[0]).toEqual({
                url: '/api/lieferanten/2/artikelpreise',
                body: { artikelId: 1, preis: 12.5, externeArtikelnummer: 'B-2001' },
            });
        });

        it('ändert den Preis auch direkt in der Übersicht', async () => {
            const user = userEvent.setup();
            mockMitPreisPost();
            renderSeite();

            const tabelle = await screen.findByRole('table', { name: /Lieferanten mit aktuellem Preis/ });
            await user.click(within(tabelle).getByRole('button', { name: /^Preis ändern bei Beispiel Metall GmbH/ }));
            const feld = screen.getByLabelText('Neuer Preis in Euro bei Beispiel Metall GmbH');
            await user.clear(feld);
            await user.type(feld, '11,90{Enter}');

            await waitFor(() => expect(postBodies()).toHaveLength(1));
            expect(postBodies()[0].body).toEqual({ artikelId: 1, preis: 11.9, externeArtikelnummer: null });
        });

        it('trägt den Preis eines weiteren Lieferanten ein', async () => {
            const user = userEvent.setup();
            mockMitPreisPost();
            renderSeite('#preise');

            await user.click(await screen.findByRole('button', { name: /Preis von weiterem Lieferanten eintragen/ }));
            await user.click(await screen.findByText('Muster Blechhandel'));
            expect(screen.getByRole('button', { name: 'Lieferant' })).toHaveTextContent('Muster Blechhandel');
            await user.type(screen.getByLabelText('Dessen Artikelnummer'), 'MB-77');
            await user.type(screen.getByLabelText('Preis in Euro'), '9,90');
            await user.click(screen.getByRole('button', { name: 'Preis speichern' }));

            await waitFor(() => expect(postBodies()).toHaveLength(1));
            expect(postBodies()[0]).toEqual({
                url: '/api/lieferanten/3/artikelpreise',
                body: { artikelId: 1, preis: 9.9, externeArtikelnummer: 'MB-77' },
            });
        });

        it('belegt beim bekannten Lieferanten dessen Nummer vor und schickt sie unverändert nicht mit', async () => {
            const user = userEvent.setup();
            mockMitPreisPost();
            renderSeite('#preise');

            await user.click(await screen.findByRole('button', { name: /Preis von weiterem Lieferanten eintragen/ }));
            await user.click(await screen.findByText('Beispiel Metall GmbH', { selector: 'span.truncate' }));
            expect(screen.getByLabelText('Dessen Artikelnummer')).toHaveValue('B-2000');
            await waitFor(() => expect(screen.getByLabelText('Preis in Euro')).toHaveFocus());
            await user.type(screen.getByLabelText('Preis in Euro'), '12{Enter}');

            await waitFor(() => expect(postBodies()).toHaveLength(1));
            expect(postBodies()[0].body).toEqual({ artikelId: 1, preis: 12, externeArtikelnummer: null });
        });
    });

    describe('Reiter', () => {
        it('öffnet ohne Anker die Übersicht und baut nur den offenen Reiter', async () => {
            renderSeite();

            const uebersicht = await screen.findByRole('tab', { name: /Übersicht/ });
            expect(uebersicht).toHaveAttribute('aria-selected', 'true');
            expect(screen.getAllByRole('tabpanel')).toHaveLength(1);
            expect(screen.getByTestId('bester-preis')).toHaveTextContent('10,00 €');
            expect(screen.getByTestId('bester-preis')).toHaveTextContent('Muster Stahlhandel');
            // Inhalte anderer Reiter stehen gar nicht erst im Dokument
            expect(screen.queryByText('Preisverlauf')).toBeNull();
            expect(screen.queryByText('Für Angebote und Rechnungen')).toBeNull();
            // Anzahl der Lieferanten am Preise-Reiter
            expect(screen.getByRole('tab', { name: /Preise & Lieferanten/ })).toHaveTextContent('2');
        });

        it('schreibt den offenen Reiter als Anker in die Adresse', async () => {
            const user = userEvent.setup();
            renderSeite();

            await user.click(await screen.findByRole('tab', { name: /Preise & Lieferanten/ }));
            expect(screen.getByTestId('adresse')).toHaveTextContent('/artikel/1#preise');
            expect(screen.getByRole('heading', { name: 'Preisverlauf' })).toBeInTheDocument();

            await user.click(screen.getByRole('tab', { name: /Übersicht/ }));
            expect(screen.getByTestId('adresse').textContent).toBe('/artikel/1');
        });

        it('öffnet den Reiter aus dem Anker der Adresse', async () => {
            renderSeite('#bilder');
            expect(await screen.findByRole('tab', { name: /Bilder & Unterlagen/ })).toHaveAttribute('aria-selected', 'true');
            expect(await screen.findByRole('heading', { name: 'Bilder & Unterlagen' })).toBeInTheDocument();
        });

        it('führt vom Bild-Platzhalter in den Reiter „Bilder & Unterlagen“', async () => {
            const user = userEvent.setup();
            renderSeite();
            await user.click(await screen.findByRole('button', { name: 'Bild hinzufügen' }));
            expect(screen.getByTestId('adresse')).toHaveTextContent('/artikel/1#bilder');
        });

        it('zeigt das Vorschaubild in der Übersicht, wenn eines hinterlegt ist', async () => {
            vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve({
                ok: true, status: 200,
                json: () => Promise.resolve(url.includes('/dokumente') ? [] : {
                    ...detailAntwort,
                    artikel: { ...detailAntwort.artikel, vorschaubildUrl: '/api/artikel/dokumente/5/datei' },
                }),
            })));
            renderSeite();
            expect(await screen.findByRole('img', { name: 'Vorschaubild: 42.4x2' })).toHaveAttribute('src', '/api/artikel/dokumente/5/datei');
            expect(screen.queryByRole('button', { name: 'Bild hinzufügen' })).toBeNull();
        });

        it('wandert per Pfeiltaste durch die Reiter', async () => {
            const user = userEvent.setup();
            renderSeite();
            await user.click(await screen.findByRole('tab', { name: /Übersicht/ }));
            await user.keyboard('{ArrowRight}');
            expect(screen.getByRole('tab', { name: /Preise & Lieferanten/ })).toHaveFocus();
            expect(screen.getByTestId('adresse')).toHaveTextContent('#preise');
        });
    });

    it('liest den Aufschlag im deutschen Zahlenformat', async () => {
        mockFetchDetail({
            artikel: { id: 7, produktname: 'T-Stahl', preisHinweis: 'KEIN_AUFSCHLAG' },
            lieferanten: [],
            preisverlauf: [],
        });
        render(<ArtikelDetail />, { wrapper: Wrapper });

        await userEvent.type(await screen.findByLabelText('Aufschlag auf den Einkaufspreis'), '12,5');
        await userEvent.click(screen.getByRole('button', { name: /Angebotsfelder speichern/ }));

        await waitFor(() => expect(fetchMock.mock.calls.some(([, o]) => o?.method === 'PATCH')).toBe(true));
        const patch = fetchMock.mock.calls.find(([, o]) => o?.method === 'PATCH');
        expect(JSON.parse(String(patch?.[1]?.body)).verkaufsaufschlagProzent).toBe(12.5);
    });

    it('zeigt Artikelnummer und EAN bei den Angaben zum Teil', async () => {
        vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve(url.includes('/dokumente') ? [] : { ...detailAntwort, artikel: { ...detailAntwort.artikel, ean: '4006381333931' } }),
        })));
        renderSeite('#angaben');

        expect(await screen.findByText('EAN / Strichcode')).toBeVisible();
        expect(screen.getByText('4006381333931')).toBeVisible();
    });

    it('öffnet und schließt die Maske zum Bearbeiten der Stammdaten', async () => {
        const user = userEvent.setup();
        renderSeite('#angaben');

        await user.click(await screen.findByRole('button', { name: /Stammdaten bearbeiten/ }));
        const maske = await screen.findByTestId('artikel-stammdaten-form');
        expect(within(maske).getByLabelText('Produktname *')).toHaveValue('42.4x2');
        // Solange die Maske offen ist, gibt es den Knopf nicht ein zweites Mal
        expect(screen.queryByRole('button', { name: /Stammdaten bearbeiten/ })).toBeNull();

        await user.click(within(maske).getByRole('button', { name: 'Abbrechen' }));
        expect(screen.queryByTestId('artikel-stammdaten-form')).toBeNull();
        expect(screen.getByRole('button', { name: /Stammdaten bearbeiten/ })).toBeVisible();
    });

    it('öffnet die Bearbeiten-Maske aus der Übersicht und behält Eingaben beim Reiterwechsel', async () => {
        const user = userEvent.setup();
        renderSeite();

        await user.click(await screen.findByRole('button', { name: 'Angaben ändern' }));
        expect(screen.getByRole('tab', { name: /Angaben zum Teil/ })).toHaveAttribute('aria-selected', 'true');
        const name = within(await screen.findByTestId('artikel-stammdaten-form')).getByLabelText('Produktname *');
        await user.clear(name);
        await user.type(name, 'Geländerrohr 42,4');

        // Kurz in die Preise schauen und zurück: die angefangene Eingabe ist noch da
        await user.click(screen.getByRole('tab', { name: /Preise & Lieferanten/ }));
        expect(screen.getByRole('tab', { name: /Angaben zum Teil/ })).toHaveTextContent('wird gerade bearbeitet');
        await user.click(screen.getByRole('tab', { name: /Angaben zum Teil/ }));
        expect(within(screen.getByTestId('artikel-stammdaten-form')).getByLabelText('Produktname *')).toHaveValue('Geländerrohr 42,4');
    });

    it('zeigt bei der Eignung, ob sie vom Werkstoff kommt oder für den Artikel festgelegt ist', async () => {
        vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve(url.includes('/dokumente') ? [] : {
                ...detailAntwort,
                artikel: { ...detailAntwort.artikel, verzinkungsgeeignet: false, verzinkungsgeeignetEigen: false, pulverbeschichtungsgeeignet: true, pulverbeschichtungsgeeignetEigen: null },
            }),
        })));
        renderSeite('#angaben');

        const quellen = await screen.findAllByTestId('eignung-quelle');
        expect(quellen.map(q => q.textContent?.trim())).toEqual(['(für diesen Artikel festgelegt)', '(vom Werkstoff)']);
        expect(screen.getByText('nicht möglich')).toBeVisible();
    });
});
