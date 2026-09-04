import type { ComponentProps, Ref } from 'react';
import { createRef } from 'react';
import { render, screen, waitFor, fireEvent, act, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter, useSearchParams } from 'react-router-dom';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';
import { useDatensatzLock } from '../lock/useDatensatzLock';
import DocumentEditor from './index';
import type { DocumentEditorHandle } from './types';

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

/** Wie renderEditor, aber mit einem Ref auf das imperative Handle (Task 7a). */
function renderEditorMitRef(
    ref: Ref<DocumentEditorHandle>,
    props: Partial<ComponentProps<typeof DocumentEditor>> = {}
) {
    return render(
        <MemoryRouter initialEntries={['/dokumente/1']}>
            <ConfirmProvider>
                <ToastProvider>
                    <DocumentEditor ref={ref} dokumentId={1} onClose={() => { }} {...props} />
                </ToastProvider>
            </ConfirmProvider>
        </MemoryRouter>
    );
}

/**
 * Der X-Knopf in der Kopfzeile (DocumentEditorHeader.tsx). Traegt seit
 * Design-Review Abschnitt 7-2 (Befund 4) `aria-label="Editor schließen"` --
 * vorher war er nur ueber `container.querySelector('button')` (erster Button
 * im Baum) erreichbar, siehe Kontext-Log fuer die Begruendung.
 */
function xKnopf(container: HTMLElement): HTMLElement {
    return within(container).getByRole('button', { name: 'Editor schließen' });
}

/** Alle Requests an die Datensatz-Lock-Endpunkte. */
function lockAufrufe(fetchMock: ReturnType<typeof mockFetch>): string[] {
    return fetchMock.mock.calls
        .map(call => call[0] as string)
        .filter(url => typeof url === 'string' && url.includes('/datensatz-locks/'));
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

/**
 * Mini-Nachbau der fuer Nachbesserung 1 relevanten Verdrahtung aus
 * DocumentEditorPage.tsx (gehoert Abschnitt 7a, hier NICHT importiert, nur
 * nachgebaut -- die echte Seite zieht Router/Toast/Lock-Bausteine mit, die
 * fuer DIESEN Test nicht noetig sind): liest dokumentId aus der URL und
 * haelt darueber das Seiten-Lock per useDatensatzLock. Damit laesst sich
 * pruefen, ob der Editor der Seite eine neu angelegte Id tatsaechlich ueber
 * den Router mitteilt -- nur dann sieht dieser Wrapper sie und akquiriert.
 *
 * Abweichung Design-/Code-Review Abschnitt 7-2 (Befund 5): frueher auf den
 * VORGAENGER-Hook (aeltere, mittlerweile abgeloeste Sperr-Route) nachgebaut
 * -- der wird zusammen mit seinem Modal in Abschnitt 8 komplett geloescht,
 * dieser Test haette dann sofort gebrochen. Jetzt auf useDatensatzLock
 * (`/api/datensatz-locks/...`) umgestellt, exakt dieselbe Aussage.
 */
function SeiteMitSeitenLock(props: Omit<ComponentProps<typeof DocumentEditor>, 'dokumentId'>) {
    const [searchParams] = useSearchParams();
    const roh = searchParams.get('dokumentId');
    const dokumentId = roh ? Number(roh) : undefined;
    useDatensatzLock('AUSGANG', dokumentId);
    return <DocumentEditor {...props} dokumentId={dokumentId} />;
}

function renderNeuesDokumentMitSeitenLock(props: Partial<ComponentProps<typeof DocumentEditor>> = {}) {
    return render(
        <MemoryRouter initialEntries={['/dokument-editor']}>
            <ConfirmProvider>
                <ToastProvider>
                    <SeiteMitSeitenLock onClose={() => { }} {...props} />
                </ToastProvider>
            </ConfirmProvider>
        </MemoryRouter>
    );
}

/**
 * Stubbt das Anlegen eines neuen Dokuments (id 42) samt einem serverseitig
 * PLAUSIBLEN Lock-Verfall: bleiben Acquire/Heartbeat laenger als
 * STALE_AFTER_MS aus, antwortet der naechste PUT mit 409 -- genau das
 * Verhalten von DatensatzLockService. Nur so beweist der Test etwas: eine
 * Stub, die PUT immer erlaubt, wuerde den eigentlichen Fehler (Seite pingt
 * nie, weil sie die neue Id nie sieht) nicht aufdecken.
 */
function mockFetchNeuesDokument() {
    const STALE_AFTER_MS = 90_000;
    let dokument: Record<string, unknown> | null = null;
    let letzterHeartbeatMs: number | null = null;
    const pingen = () => { letzterHeartbeatMs = Date.now(); };

    return vi.fn((url: string, init?: RequestInit) => {
        if (url === '/api/ausgangs-dokumente' && init?.method === 'POST') {
            const dto = JSON.parse(init.body as string);
            dokument = {
                id: 42,
                typ: dto.typ,
                dokumentNummer: 'RE-2026/09/00042',
                datum: dto.datum,
                betreff: dto.betreff,
                betragNetto: dto.betragNetto,
                htmlInhalt: dto.htmlInhalt,
                positionenJson: dto.positionenJson,
                gebucht: false,
                storniert: false,
            };
            // Backend haelt das Lock beim Anlegen schon fuer den Ersteller,
            // siehe AusgangsGeschaeftsDokumentController.create.
            pingen();
            return jsonAntwort(dokument);
        }
        if (url === '/api/datensatz-locks/AUSGANG/42/acquire' && init?.method === 'POST') {
            pingen();
            return jsonAntwort({
                status: 'ACQUIRED', holderUserId: 1, holderDisplayName: 'Max Mustermann',
                acquiredAt: new Date().toISOString(), lastHeartbeatAt: new Date().toISOString(),
            });
        }
        if (url === '/api/datensatz-locks/AUSGANG/42/heartbeat' && init?.method === 'POST') {
            pingen();
            return jsonAntwort({
                status: 'ACQUIRED', holderUserId: 1, holderDisplayName: 'Max Mustermann',
                acquiredAt: new Date().toISOString(), lastHeartbeatAt: new Date().toISOString(),
            });
        }
        if (url === '/api/ausgangs-dokumente/42' && init?.method === 'PUT') {
            const veraltetSeitMs = letzterHeartbeatMs == null ? Infinity : Date.now() - letzterHeartbeatMs;
            if (veraltetSeitMs > STALE_AFTER_MS) {
                return Promise.resolve({
                    ok: false,
                    status: 409,
                    statusText: 'Conflict',
                    headers: new Headers({ 'content-type': 'text/plain' }),
                    text: () => Promise.resolve('Dokument wird gerade von einem anderen Benutzer bearbeitet.'),
                });
            }
            const gesendet = JSON.parse(init.body as string);
            dokument = { ...dokument, ...gesendet };
            return jsonAntwort(dokument);
        }
        if (url === '/api/ausgangs-dokumente/42') {
            return jsonAntwort(dokument ?? {});
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
        return jsonAntwort([]);
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
    // Heartbeat auf der alten, mittlerweile abgeloesten Sperr-Route UND ein
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

    it('sendet nach Mount und Unmount keinen Request an einen Datensatz-Lock-Endpunkt', async () => {
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

describe('DocumentEditor – "Gebucht"-Badge (Design-Review Abschnitt 7-2, Befund 1)', () => {
    // Regression: das Badge haengte bisher an isLocked, das AUCH bei
    // Fremdsperre/eigenem "Fertig"/Sperrfehler true ist -- "Gebucht" heisst
    // in diesem Produkt "in der Buchhaltung erfasst" und stand faelschlich
    // auf einem Dokument mit gebucht=false (dokumentAntwort.gebucht ist
    // ueberall in dieser Datei false, siehe oben).
    let fetchMock: ReturnType<typeof mockFetch>;

    beforeEach(() => {
        global.URL.createObjectURL = vi.fn(() => `blob:vorschau-${Math.random()}`);
        global.URL.revokeObjectURL = vi.fn();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('zeigt "Gebucht" NICHT bei Fremdsperre (readOnly=true), obwohl isLocked dadurch true wird', async () => {
        fetchMock = mockFetch();
        global.fetch = fetchMock as unknown as typeof fetch;
        renderEditor({ readOnly: true });

        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        expect(screen.queryByText('Gebucht')).not.toBeInTheDocument();
    });

    it('zeigt "Gebucht" weiterhin fuer eine tatsaechlich gebuchte Rechnung', async () => {
        fetchMock = mockFetch({ gebucht: true });
        global.fetch = fetchMock as unknown as typeof fetch;
        renderEditor();

        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        expect(screen.getByText('Gebucht')).toBeInTheDocument();
    });

    it('zeigt "Gebucht" NICHT fuer ein storniertes Dokument (eigenes Badge "Storniert")', async () => {
        fetchMock = mockFetch({ storniert: true });
        global.fetch = fetchMock as unknown as typeof fetch;
        renderEditor();

        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        expect(screen.queryByText('Gebucht')).not.toBeInTheDocument();
        expect(screen.getByText('Storniert')).toBeInTheDocument();
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
            /können diesen Tab jetzt schließen/,
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

    it('raeumt den 150ms-Warte-Timer beim Unmount auf', async () => {
        // Nachbesserung 1 (Kontext-Log): ohne clearTimeout im Cleanup laeuft
        // ein verwaister Timer weiter und versucht spaeter, State auf einer
        // laengst ungemounteten Komponente zu setzen. Ueber die exakte
        // Timer-Id geprueft (nicht nur "clearTimeout wurde irgendwann
        // aufgerufen") -- sonst wuerde ein voellig unabhaengiger
        // clearTimeout-Aufruf anderswo im Baum den Test faelschlich gruen
        // machen, ohne dass DIESER Timer je abgeraeumt wurde.
        const user = userEvent.setup();
        const onLockFreigeben = vi.fn().mockResolvedValue(undefined);
        vi.spyOn(window, 'close').mockImplementation(() => { });
        const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
        const clearTimeoutSpy = vi.spyOn(window, 'clearTimeout');

        const { container, unmount } = renderEditor({ onLockFreigeben });
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });

        // Keine ungespeicherten Aenderungen: das X loest tabSchliessen()
        // direkt aus, der 150ms-Timer wird geplant.
        await user.click(xKnopf(container));

        const timerAufrufe150ms = setTimeoutSpy.mock.calls
            .map((call, index) => ({ verzoegerungMs: call[1], id: setTimeoutSpy.mock.results[index].value }))
            .filter(aufruf => aufruf.verzoegerungMs === 150);
        expect(timerAufrufe150ms.length).toBeGreaterThan(0);
        const tabSchliessenTimerId = timerAufrufe150ms.at(-1)!.id;

        // Sofort unmounten, deutlich vor den 150ms.
        unmount();

        expect(clearTimeoutSpy).toHaveBeenCalledWith(tabSchliessenTimerId);
    });
});

describe('DocumentEditor – 409 beim Speichern: zwei verschiedene Ursachen', () => {
    // Nachbesserung 1 (Kontext-Log): eine Fremdsperre (Text-Body vom
    // Lock-Check in update()) und ein Versionskonflikt (JSON-Body vom
    // RestExceptionHandler) landen beide mit Status 409 beim Client -- vorher
    // zeigte handleSave in BEIDEN Faellen res.text() im Toast, beim
    // Versionskonflikt also den rohen JSON-String.
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

    it('zeigt bei einem Versionskonflikt (JSON-Body) die Neu-laden-Meldung statt rohem JSON', async () => {
        const user = userEvent.setup();
        const basis = mockFetch();
        fetchMock = vi.fn((url: string, init?: RequestInit) => {
            if (url === '/api/ausgangs-dokumente/1' && init?.method === 'PUT') {
                return Promise.resolve({
                    ok: false,
                    status: 409,
                    statusText: 'Conflict',
                    headers: new Headers({ 'content-type': 'application/json' }),
                    json: () => Promise.resolve({
                        status: 409,
                        message: 'Jemand anders hat diese Daten gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden.',
                        field: null, fields: [], details: null,
                    }),
                    text: () => Promise.resolve('{"status":409,"message":"..."}'),
                });
            }
            return basis(url, init);
        });
        global.fetch = fetchMock as unknown as typeof fetch;

        renderEditor();
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        await user.click(screen.getByRole('button', { name: /Speichern/i }));

        expect(await screen.findByText('Nicht gespeichert')).toBeInTheDocument();
        expect(screen.getByText(/Jemand anders hat dieses Dokument gerade gespeichert/)).toBeInTheDocument();
        expect(screen.queryByText(/"status":409/)).not.toBeInTheDocument();
    });

    it('zeigt bei einer Fremdsperre (Text-Body) weiterhin die einfache Warnmeldung, keinen Neu-laden-Dialog', async () => {
        const user = userEvent.setup();
        const basis = mockFetch();
        fetchMock = vi.fn((url: string, init?: RequestInit) => {
            if (url === '/api/ausgangs-dokumente/1' && init?.method === 'PUT') {
                return Promise.resolve({
                    ok: false,
                    status: 409,
                    statusText: 'Conflict',
                    headers: new Headers({ 'content-type': 'text/plain' }),
                    text: () => Promise.resolve('Dokument wird gerade von einem anderen Benutzer bearbeitet.'),
                });
            }
            return basis(url, init);
        });
        global.fetch = fetchMock as unknown as typeof fetch;

        renderEditor();
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });
        await user.click(screen.getByRole('button', { name: /Speichern/i }));

        expect(await screen.findByText(/wird gerade von einem anderen Benutzer bearbeitet/)).toBeInTheDocument();
        expect(screen.queryByText('Nicht gespeichert')).not.toBeInTheDocument();
    });
});

describe('DocumentEditor – Sperre nach Neuanlage bleibt am Leben', () => {
    // Nachbesserung 1 (Kontext-Log): ein neu angelegtes Dokument schrieb
    // seine Id frueher per window.history.replaceState in die URL -- die
    // Seite (liest dokumentId ueber react-router aus denselben
    // Suchparametern) bekam davon nichts mit, ihr Lock-Hook blieb auf 'idle'
    // und pingte nie. Nach STALE_AFTER (90s) scheiterte jeder weitere PUT
    // (auch der 10s-Autosave) mit 409, ohne dass der Editor das noch
    // reparieren konnte (das alte tryAcquireLock-Retry ist ja weg).
    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('kann ein frisch angelegtes Dokument auch nach mehr als 90s noch speichern', async () => {
        vi.useFakeTimers();
        const fetchMock = mockFetchNeuesDokument();
        global.fetch = fetchMock as unknown as typeof fetch;
        global.URL.createObjectURL = vi.fn(() => `blob:vorschau-${Math.random()}`);
        global.URL.revokeObjectURL = vi.fn();

        renderNeuesDokumentMitSeitenLock();
        await act(async () => { await vi.advanceTimersByTimeAsync(0); });

        // Neues Dokument: kein Ladevorgang, der Speichern-Knopf ist sofort da.
        fireEvent.click(screen.getByRole('button', { name: /Speichern/i }));
        // Mehrfach in kleinen Schritten flushen: das Anlegen stoesst eine
        // ganze Kette an (POST -> setSearchParams -> Seite re-rendert ->
        // Lock-Hook-Effekt -> Acquire-Fetch) ueber mehrere React-Commits
        // hinweg, die ein einzelner 0ms-Flush nicht immer vollstaendig
        // abbildet.
        for (let i = 0; i < 10; i++) {
            await act(async () => { await vi.advanceTimersByTimeAsync(10); });
        }

        const angelegt = fetchMock.mock.calls.some(
            call => call[0] === '/api/ausgangs-dokumente' && (call[1] as RequestInit)?.method === 'POST'
        );
        expect(angelegt).toBe(true);

        // Die Seite muss die neue Id uebernommen und ihr Lock akquiriert
        // haben -- sonst pingt sie nie, und genau das ist der Fehler.
        const akquiriert = fetchMock.mock.calls.some(
            call => call[0] === '/api/datensatz-locks/AUSGANG/42/acquire'
        );
        expect(akquiriert).toBe(true);

        // Mehr als STALE_AFTER (90s) vergehen -- der Seiten-Heartbeat (alle
        // 30s) muss das Lock in der Zwischenzeit am Leben gehalten haben.
        await act(async () => { await vi.advanceTimersByTimeAsync(91_000); });

        fireEvent.click(screen.getByRole('button', { name: /Speichern/i }));
        await act(async () => { await vi.advanceTimersByTimeAsync(0); });

        expect(screen.getByRole('button', { name: 'Gespeichert' })).toBeInTheDocument();
    });
});

describe('DocumentEditor – imperatives Handle speichernFuerFreigabe (Task 7a)', () => {
    // Abweichung vom Plan (Kontext-Log Abschnitt 7a): DocumentEditor bot bisher
    // keinen Weg von aussen, einen Speichervorgang auszuloesen -- die Seite
    // braucht das aber fuer den Untaetigkeits-Timer (erst speichern, dann die
    // Sperre freigeben). Kleinstmoegliche Ergaenzung: forwardRef +
    // useImperativeHandle mit genau einer Methode, siehe types.ts.
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

    it('speichert ueber das Ref, wenn ungespeicherte Aenderungen vorliegen', async () => {
        const ref = createRef<DocumentEditorHandle>();
        renderEditorMitRef(ref);
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });

        await adresseAendern(userEvent.setup(), NEUE_ADRESSE);

        await act(async () => {
            await ref.current!.speichernFuerFreigabe();
        });

        const aufrufe = speicherAufrufe(fetchMock);
        expect(aufrufe.length).toBeGreaterThanOrEqual(1);
        expect(aufrufe.at(-1)?.rechnungsadresseOverride).toBe(NEUE_ADRESSE);
    });

    it('ist ein No-op ohne ungespeicherte Aenderungen -- kein Leerlauf-Speichern bei jedem Idle-Ablauf', async () => {
        const ref = createRef<DocumentEditorHandle>();
        renderEditorMitRef(ref);
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });

        await act(async () => {
            await ref.current!.speichernFuerFreigabe();
        });

        expect(speicherAufrufe(fetchMock).length).toBe(0);
    });

    it('speichert nicht ueber ein gesperrtes (readOnly) Dokument', async () => {
        const ref = createRef<DocumentEditorHandle>();
        renderEditorMitRef(ref, { readOnly: true });
        await waitFor(() => expect(screen.getByText(/Musterweg 1/)).toBeInTheDocument(), { timeout: 3000 });

        await act(async () => {
            await ref.current!.speichernFuerFreigabe();
        });

        expect(speicherAufrufe(fetchMock).length).toBe(0);
    });
});
