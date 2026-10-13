import { useEffect, type ReactNode } from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KiHilfeChat } from './KiHilfeChat';
import { ToastProvider, useToast } from './ui/toast';

const KI_VERBINDUNGSFEHLER_TEXT = 'Verbindung zur KI fehlgeschlagen. Ist der Server erreichbar?';
const SCHRITT_1 = 'Passende Stellen im Programm gesucht';
const SCHRITT_2 = 'Schaut nach: Wie man eine Rechnung storniert';
const ANTWORT = 'Öffne die Rechnung und klicke auf **Stornieren**.';

/** Antwort mit einem Ereignisstrom, den der Test Stück für Stück schickt. */
function steuerbarerStrom() {
    const kodierer = new TextEncoder();
    let steuerung!: ReadableStreamDefaultController<Uint8Array>;
    const body = new ReadableStream<Uint8Array>({ start(c) { steuerung = c; } });
    return {
        antwort: new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } }),
        sende: (ereignis: string, daten: unknown) => steuerung.enqueue(kodierer.encode(`event:${ereignis}\ndata:${JSON.stringify(daten)}\n\n`)),
        ende: () => steuerung.close(),
        abbrechen: () => steuerung.error(new DOMException('Abgebrochen', 'AbortError')),
    };
}

/** fetch, das wie der Browser bei einem Abbruch den Strom abreißen lässt. */
function stubbeFetch(antwort: Response, beiAbbruch?: () => void) {
    const fetchMock = vi.fn((_url: string, init: RequestInit) => {
        init.signal?.addEventListener('abort', () => beiAbbruch?.());
        return Promise.resolve(antwort);
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

function zeige(seitenInhalt?: ReactNode) {
    return render(
        <ToastProvider>
            <MemoryRouter initialEntries={['/rechnungsuebersicht']}>
                <main><h1>Rechnungen</h1>{seitenInhalt}</main>
                <KiHilfeChat />
            </MemoryRouter>
        </ToastProvider>,
    );
}

/** Eine Seite, die beim Laden selbst einen Fehler-Toast zeigt. */
function SeitenToast({ meldung }: { meldung: string }) {
    const toast = useToast();
    useEffect(() => toast.error(meldung), [toast, meldung]);
    return null;
}

/** Fehlermeldungen der Seite, die mit der n-ten Frage an die KI gingen. */
function gesendeteSeitenfehler(fetchMock: ReturnType<typeof vi.fn>, n: number): unknown {
    return JSON.parse((fetchMock.mock.calls[n][1] as RequestInit).body as string).context.errorMessages;
}

function frage(text: string) {
    fireEvent.change(screen.getByPlaceholderText('Frage stellen...'), { target: { value: text } });
    fireEvent.click(screen.getByRole('button', { name: 'Frage senden' }));
}

const liveListe = () => screen.queryByRole('list', { name: 'Was die KI gerade tut' });
const unterhaltung = () => screen.getByRole('log', { name: 'Unterhaltung' });

/** Fehler stehen im Chat und zusätzlich als Toast (der allein sie ansagt). */
async function erwarteFehler(meldung: string) {
    const toast = await screen.findByRole('alert');
    expect(toast).toHaveTextContent(meldung);
    expect(screen.getByRole('region', { name: 'Meldungen' })).toContainElement(toast);
    expect(screen.getAllByText(meldung)).toHaveLength(2);
    expect(screen.getAllByRole('alert')).toHaveLength(1);
}

describe('KiHilfeChat', () => {
    beforeEach(() => {
        // Der Chat merkt sich „offen“ je Benutzer im localStorage.
        localStorage.setItem('frontendUserSelection', JSON.stringify({ id: 1 }));
        localStorage.setItem('ki-hilfe-open-1', 'true');
        Element.prototype.scrollIntoView = vi.fn();
    });
    afterEach(() => {
        vi.unstubAllGlobals();
        localStorage.clear();
    });

    it('zeigt live, wo die KI nachschaut, und danach die Antwort mit aufklappbaren Schritten', async () => {
        const strom = steuerbarerStrom();
        const fetchMock = stubbeFetch(strom.antwort);
        zeige();

        frage('Wie storniere ich eine Rechnung?');

        // Vor dem ersten Schritt: „Denkt nach …“ in der Live-Liste.
        expect(within(liveListe()!).getByText('Denkt nach …')).toBeInTheDocument();
        expect(liveListe()).toHaveAttribute('aria-live', 'polite');
        // Nichts doppelt ansagen: die Schritte liegen nicht in der Log-Region der Unterhaltung.
        expect(unterhaltung()).not.toContainElement(liveListe());
        const [, init] = fetchMock.mock.calls[0];
        const gesendet = JSON.parse(init.body as string);
        expect(gesendet.messages).toEqual([{ role: 'user', text: 'Wie storniere ich eine Rechnung?' }]);
        expect(gesendet.context).toMatchObject({ route: '/rechnungsuebersicht', pageTitle: 'Rechnungen' });

        strom.sende('schritt', { text: SCHRITT_1 });
        expect(await screen.findByText(SCHRITT_1)).toBeInTheDocument();
        expect(screen.queryByText('Denkt nach …')).not.toBeInTheDocument();
        expect(screen.getByText(SCHRITT_1).closest('li')).toHaveAttribute('aria-current', 'step');

        strom.sende('schritt', { text: SCHRITT_2 });
        expect(await screen.findByText(SCHRITT_2)).toBeInTheDocument();
        // Der erste Schritt ist erledigt, der zweite läuft.
        expect(screen.getByText(SCHRITT_1).closest('li')).not.toHaveAttribute('aria-current');
        expect(screen.getByText(SCHRITT_2).closest('li')).toHaveAttribute('aria-current', 'step');

        strom.sende('antwort', { reply: ANTWORT, sources: [], schritte: [SCHRITT_1, SCHRITT_2] });
        strom.ende();

        expect(await screen.findByText('Stornieren')).toBeInTheDocument();
        // Die fertige Antwort steht in der Log-Region und wird vorgelesen.
        expect(unterhaltung()).toHaveAttribute('aria-live', 'polite');
        expect(unterhaltung()).toContainElement(screen.getByText('Stornieren'));
        expect(liveListe()).not.toBeInTheDocument();
        expect(screen.queryByText(SCHRITT_1)).not.toBeInTheDocument();
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();

        const aufklapper = screen.getByRole('button', { name: 'So habe ich nachgeschaut (2)' });
        expect(aufklapper).toHaveAttribute('aria-expanded', 'false');
        fireEvent.click(aufklapper);
        expect(aufklapper).toHaveAttribute('aria-expanded', 'true');
        const liste = document.getElementById(aufklapper.getAttribute('aria-controls')!)!;
        expect(within(liste).getAllByRole('listitem').map((li) => li.textContent)).toEqual([SCHRITT_1, SCHRITT_2]);
        fireEvent.click(aufklapper);
        expect(screen.queryByText(SCHRITT_1)).not.toBeInTheDocument();

        // Die Schritte werden mit der Nachricht gespeichert.
        const gespeichert = JSON.parse(localStorage.getItem('ki-hilfe-messages-1')!);
        expect(gespeichert[1]).toEqual({ role: 'assistant', text: ANTWORT, sources: [], schritte: [SCHRITT_1, SCHRITT_2] });
        // Eingabe ist wieder frei.
        expect(screen.getByPlaceholderText('Frage stellen...')).toBeEnabled();
    });

    it('zeigt bei Antworten ohne Schritte und bei älteren Nachrichten keine Nachschlage-Zeile', async () => {
        localStorage.setItem('ki-hilfe-messages-1', JSON.stringify([
            { role: 'user', text: 'Alte Frage' },
            { role: 'assistant', text: 'Alte Antwort ohne Schritte' },
        ]));
        const strom = steuerbarerStrom();
        stubbeFetch(strom.antwort);
        zeige();
        expect(screen.getByText('Alte Antwort ohne Schritte')).toBeInTheDocument();

        frage('Neue Frage');
        strom.sende('antwort', { reply: 'Direkte Antwort', sources: [], schritte: [] });
        strom.ende();

        expect(await screen.findByText('Direkte Antwort')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /So habe ich nachgeschaut/ })).not.toBeInTheDocument();
    });

    it('zeigt die Meldung aus einem fehler-Ereignis', async () => {
        const strom = steuerbarerStrom();
        stubbeFetch(strom.antwort);
        zeige();

        frage('Wie storniere ich eine Rechnung?');
        strom.sende('schritt', { text: SCHRITT_1 });
        strom.sende('fehler', { error: 'KI-Hilfe ist gerade nicht verfügbar. Bitte versuche es später erneut.' });
        strom.ende();

        await erwarteFehler('KI-Hilfe ist gerade nicht verfügbar. Bitte versuche es später erneut.');
        expect(liveListe()).not.toBeInTheDocument();
    });

    it('zeigt die Meldung, wenn der Server die Frage ablehnt (400 mit message)', async () => {
        stubbeFetch(new Response(JSON.stringify({ status: 400, message: 'Leere Nachricht' }), {
            status: 400, headers: { 'Content-Type': 'application/json' },
        }));
        zeige();

        frage('Hallo');

        await erwarteFehler('Leere Nachricht');
    });

    it('zeigt die Meldung, wenn die KI-Hilfe ausgelastet ist (429)', async () => {
        stubbeFetch(new Response(JSON.stringify({
            status: 429, message: 'Die KI-Hilfe ist gerade ausgelastet. Bitte versuche es gleich noch einmal.',
        }), { status: 429, headers: { 'Content-Type': 'application/json' } }));
        zeige();

        frage('Hallo');

        await erwarteFehler('Die KI-Hilfe ist gerade ausgelastet. Bitte versuche es gleich noch einmal.');
    });

    it('schickt einen vorherigen Fehler der KI-Hilfe bei der nächsten Frage nicht als Seitenfehler mit', async () => {
        const strom = steuerbarerStrom();
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(new Response(JSON.stringify({ status: 429, message: 'Die KI-Hilfe ist gerade ausgelastet.' }), {
                status: 429, headers: { 'Content-Type': 'application/json' },
            }))
            .mockResolvedValueOnce(strom.antwort);
        vi.stubGlobal('fetch', fetchMock);
        zeige();

        frage('Erste Frage');
        await erwarteFehler('Die KI-Hilfe ist gerade ausgelastet.');
        // Toast und Kästchen im Chat stehen noch, während die nächste Frage rausgeht.
        frage('Zweite Frage');

        expect(fetchMock).toHaveBeenCalledTimes(2);
        expect(gesendeteSeitenfehler(fetchMock, 1)).toBeUndefined();
    });

    it('schickt echte Fehlermeldungen der Seite weiter mit, auch als Toast', async () => {
        const strom = steuerbarerStrom();
        const fetchMock = stubbeFetch(strom.antwort);
        zeige(<>
            <div role="alert">Rechnung konnte nicht gespeichert werden.</div>
            <SeitenToast meldung="Kunde fehlt." />
        </>);
        expect(await screen.findByText('Kunde fehlt.')).toBeInTheDocument();

        frage('Warum klappt das Speichern nicht?');

        expect(gesendeteSeitenfehler(fetchMock, 0)).toBe('Kunde fehlt.; Rechnung konnte nicht gespeichert werden.');
    });

    it('meldet einen Verbindungsfehler, wenn der Server nicht erreichbar ist', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
        zeige();

        frage('Hallo');

        await erwarteFehler('Verbindung zur KI fehlgeschlagen. Ist der Server erreichbar?');
    });

    it('bricht beim Leeren des Chats die laufende Frage still ab', async () => {
        const strom = steuerbarerStrom();
        const fetchMock = stubbeFetch(strom.antwort, strom.abbrechen);
        zeige();

        frage('Wie storniere ich eine Rechnung?');
        strom.sende('schritt', { text: SCHRITT_1 });
        expect(await screen.findByText(SCHRITT_1)).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: 'Chat leeren' }));

        const signal = (fetchMock.mock.calls[0][1] as RequestInit).signal!;
        expect(signal.aborted).toBe(true);
        expect(liveListe()).not.toBeInTheDocument();
        expect(screen.getByText('Wie kann ich dir helfen?')).toBeInTheDocument();
        expect(screen.getByPlaceholderText('Frage stellen...')).toBeEnabled();
        // Auch nachdem der abgebrochene Strom ausgelaufen ist: keine Fehlermeldung, keine Antwort.
        await waitFor(() => expect(strom.antwort.bodyUsed).toBe(true));
        await new Promise((fertig) => setTimeout(fertig, 0));
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
        expect(JSON.parse(localStorage.getItem('ki-hilfe-messages-1')!)).toEqual([]);
    });

    it('„Abbrechen“ nimmt die offene Frage zurück ins Eingabefeld, ohne Fehler und ohne Toast', async () => {
        localStorage.setItem('ki-hilfe-messages-1', JSON.stringify([
            { role: 'user', text: 'Wo finde ich die Zeiterfassung?' },
            { role: 'assistant', text: 'Oben im Menü unter Zeiterfassung.' },
        ]));
        const strom = steuerbarerStrom();
        const fetchMock = stubbeFetch(strom.antwort, strom.abbrechen);
        zeige();

        frage('Wie storniere ich eine Rechnung?');
        strom.sende('schritt', { text: SCHRITT_1 });
        expect(await screen.findByText(SCHRITT_1)).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }));

        expect((fetchMock.mock.calls[0][1] as RequestInit).signal!.aborted).toBe(true);
        expect(liveListe()).not.toBeInTheDocument();
        const eingabe = screen.getByPlaceholderText('Frage stellen...');
        expect(eingabe).toHaveValue('Wie storniere ich eine Rechnung?');
        expect(eingabe).toBeEnabled();
        expect(eingabe).toHaveFocus();
        // Im Verlauf bleibt keine unbeantwortete Frage stehen, der alte Verlauf bleibt.
        expect(within(unterhaltung()).queryByText('Wie storniere ich eine Rechnung?')).not.toBeInTheDocument();
        expect(within(unterhaltung()).getByText('Oben im Menü unter Zeiterfassung.')).toBeInTheDocument();
        await waitFor(() => expect(JSON.parse(localStorage.getItem('ki-hilfe-messages-1')!)).toHaveLength(2));
        // Auch nachdem der abgebrochene Strom ausgelaufen ist: kein Fehler, kein Toast.
        await new Promise((fertig) => setTimeout(fertig, 0));
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
        expect(screen.queryByText(KI_VERBINDUNGSFEHLER_TEXT)).not.toBeInTheDocument();

        // Die geänderte Frage lässt sich normal neu stellen.
        const zweiterStrom = steuerbarerStrom();
        const zweitesFetch = stubbeFetch(zweiterStrom.antwort);
        fireEvent.click(screen.getByRole('button', { name: 'Frage senden' }));
        const gesendet = JSON.parse((zweitesFetch.mock.calls[0][1] as RequestInit).body as string);
        expect(gesendet.messages.map((m: { role: string }) => m.role)).toEqual(['user', 'assistant', 'user']);
    });

    it('bricht die laufende Frage ab, wenn der Chat verschwindet', async () => {
        const strom = steuerbarerStrom();
        const fetchMock = stubbeFetch(strom.antwort, strom.abbrechen);
        const { unmount } = zeige();

        frage('Wie storniere ich eine Rechnung?');
        unmount();

        expect((fetchMock.mock.calls[0][1] as RequestInit).signal!.aborted).toBe(true);
    });

    it('erklärt im Info-Knopf am Kopf, wie die KI-Hilfe arbeitet', () => {
        zeige();

        expect(screen.getByText('Gemini · Programmhilfe')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Mehr zu: So arbeitet die KI-Hilfe' }));
        const info = screen.getByRole('dialog', { name: 'So arbeitet die KI-Hilfe' });
        expect(info).toHaveTextContent('Was passiert im Hintergrund?');
        expect(info).toHaveTextContent(
            'Deine Frage, die bisherigen Nachrichten in diesem Chat und ein Auszug von dem, was gerade auf dem '
            + 'Bildschirm steht, gehen an Google Gemini. Die KI schaut zuerst in der Anleitung und im Aufbau der '
            + 'Seiten nach. Findet sie nicht sofort eine sichere Antwort, sucht sie selbst weiter, mit anderen '
            + 'Begriffen und in höchstens vier Runden. Fachfragen wie Normen, Vorschriften, Wetter oder Preise beantwortet sie '
            + 'mit der Google-Suche. Hast du den Standort freigegeben, geht er mit jeder Frage mit, damit die KI '
            + 'Wetter- und Ortsfragen beantworten kann.',
        );
        expect(info).toHaveTextContent('Was bewirkt das?');
        expect(info).toHaveTextContent(
            'Einfache Fragen sind in wenigen Sekunden beantwortet, schwierige dauern etwas länger. Während die KI '
            + 'sucht, siehst du, wo sie gerade nachschaut. Sie liest und erklärt nur und ändert nichts im Programm. '
            + 'Antworten können trotzdem einmal falsch sein, bei wichtigen Dingen lieber kurz nachprüfen.',
        );
        // Die Überschrift bleibt „KI-Hilfe“ – der Info-Knopf steht daneben, nicht darin.
        expect(screen.getByRole('heading', { name: 'KI-Hilfe' })).toBeInTheDocument();
        // Das Chatfenster schwebt über der Seite, ohne sie zu sperren.
        expect(screen.getByRole('dialog', { name: 'KI-Hilfe' })).toHaveAttribute('aria-modal', 'false');
    });

    it('bietet im leeren Chat passende Vorschläge an', () => {
        zeige();

        fireEvent.click(screen.getByRole('button', { name: 'Wie lege ich eine neue Anfrage an?' }));
        expect(screen.getByPlaceholderText('Frage stellen...')).toHaveValue('Wie lege ich eine neue Anfrage an?');
        expect(screen.getByRole('button', { name: 'Wo finde ich die Zeiterfassung?' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Wie storniere ich eine Rechnung?' })).toBeInTheDocument();
    });
});
