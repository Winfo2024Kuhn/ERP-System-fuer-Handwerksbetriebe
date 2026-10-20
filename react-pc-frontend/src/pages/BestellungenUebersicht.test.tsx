import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import BestellungenUebersicht from './BestellungenUebersicht';

vi.mock('../components/ui/PdfCanvasViewer', () => ({
    PdfCanvasViewer: ({ url }: { url: string }) => <div data-testid="pdf">{url}</div>,
}));

const heute = new Date();
const vorTagen = (tage: number) => {
    const d = new Date(heute);
    d.setDate(d.getDate() - tage);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

function dok(id: number, typ: string, nummer: string, datum: string, extra: Record<string, unknown> = {}) {
    return {
        id, typ, dokumentNummer: nummer, dokumentDatum: datum, betragBrutto: null, betragNetto: null,
        liefertermin: null, dateiname: `${nummer}.pdf`, pdfUrl: `/pdf/${nummer}.pdf`, ausgeblendet: false, ...extra,
    };
}

const uebersicht = {
    offeneAnfragen: [],
    laufendeBestellungen: [{
        id: 'k1', lieferantId: 1, lieferantName: 'Max Mustermann GmbH',
        dokumente: [
            dok(1, 'AUFTRAGSBESTAETIGUNG', 'AB-1', vorTagen(10)),
            dok(2, 'LIEFERSCHEIN', 'LS-1', vorTagen(5)),
            dok(3, 'LIEFERSCHEIN', 'LS-2', vorTagen(4)),
        ],
        verbindungen: [{ vonId: 2, zuId: 1 }, { vonId: 3, zuId: 1 }],
        rechnungsVorschlag: null,
    }],
    abgeschlossen: [{
        id: 'k4', lieferantId: 4, lieferantName: 'Muster Metallbau GmbH',
        dokumente: [
            dok(20, 'AUFTRAGSBESTAETIGUNG', 'AB-20', vorTagen(20)),
            dok(21, 'RECHNUNG', 'RE-4711', vorTagen(9), { betragBrutto: 1200, zugeordnet: true }),
            dok(22, 'RECHNUNG', 'RE-4712', vorTagen(6), { betragBrutto: 300, zugeordnet: false }),
            // ältere Antwort ohne die neuen Felder = offen
            dok(23, 'RECHNUNG', 'RE-4713', vorTagen(3), { betragBrutto: 80 }),
            dok(24, 'GUTSCHRIFT', 'GS-1', vorTagen(2), { betragBrutto: -20 }),
        ],
        verbindungen: [{ vonId: 21, zuId: 20 }, { vonId: 22, zuId: 20 }, { vonId: 23, zuId: 20 }],
    }],
    zugeordnet: [{
        id: 'k3', lieferantId: 3, lieferantName: 'Stahlhandel Beispiel GmbH',
        dokumente: [
            dok(13, 'RECHNUNG', 'R-901', vorTagen(3), { betragBrutto: 50 }),
            dok(12, 'RECHNUNG', 'R-900', vorTagen(5), { betragBrutto: 1250 }),
            dok(11, 'WERKSTOFFZEUGNIS', '4107891', null, { eingangsDatum: vorTagen(8) }),
            dok(10, 'LIEFERSCHEIN', 'LS-20', vorTagen(6)),
            dok(9, 'LIEFERSCHEIN', 'LS-10', vorTagen(8)),
            dok(8, 'AUFTRAGSBESTAETIGUNG', 'AB-55', vorTagen(12)),
        ],
        verbindungen: [{ vonId: 9, zuId: 8 }, { vonId: 10, zuId: 8 }, { vonId: 11, zuId: 9 }, { vonId: 12, zuId: 9 }, { vonId: 13, zuId: 10 }],
    }],
    ausgeblendet: [{
        id: 'k2', lieferantId: 2, lieferantName: 'Erika Musterfrau KG',
        dokumente: [
            dok(4, 'LIEFERSCHEIN', 'LS-9', vorTagen(30)),
            dok(5, 'RECHNUNG', 'RE-9', vorTagen(20), { ausgeblendet: true, betragBrutto: 119 }),
        ],
        verbindungen: [{ vonId: 5, zuId: 4 }],
    }],
};

/** Vorschläge für „Dokument hinzufügen“ – je Test setzbar. */
let kettenVorschlaege: unknown[] = [];

function antwortFuer(url: string): Response {
    if (url.startsWith('/api/bestellungen-uebersicht/geschaeftsdaten/')) {
        return { ok: true, json: async () => ({ id: 22, dokumentNummer: null, betragNetto: 252.1, betragBrutto: 300 }) } as Response;
    }
    if (url.startsWith('/api/bestellungen-uebersicht/zuordnungen/')) return { ok: true, json: async () => [] } as Response;
    if (url.startsWith('/api/bestellungen-uebersicht?')) return { ok: true, json: async () => uebersicht } as Response;
    if (url.startsWith('/api/bestellungen-uebersicht/belege-offen')) return { ok: true, json: async () => [] } as Response;
    if (url.startsWith('/api/bestellungen-uebersicht/ketten-vorschlaege')) return { ok: true, json: async () => kettenVorschlaege } as Response;
    if (url === '/api/bestellungen-uebersicht/ketten-verknuepfen') return { ok: true, json: async () => ({ success: true }) } as Response;
    if (url === '/api/bestellungen-uebersicht/abhaengen') return { ok: true, json: async () => ({ geloest: 1 }) } as Response;
    return { ok: false, json: async () => ({}) } as Response;
}

/** Aufrufe der Übersicht selbst (ohne Unterpfade wie /belege-offen). */
function uebersichtAufrufe(): URL[] {
    return vi.mocked(fetch).mock.calls
        .map(c => String(c[0]))
        .filter(u => u.startsWith('/api/bestellungen-uebersicht?'))
        .map(u => new URL(u, 'http://localhost'));
}

function zeige() {
    render(
        <ToastProvider>
            <ConfirmProvider>
                <BestellungenUebersicht />
            </ConfirmProvider>
        </ToastProvider>,
    );
}

describe('BestellungenUebersicht – Kette als Linie', () => {
    beforeEach(() => {
        kettenVorschlaege = [];
        vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL) => antwortFuer(String(url))));
    });
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt laufende Bestellungen als Linie mit offenem Ende und Suche', async () => {
        zeige();
        const liste = await screen.findByRole('list', { name: 'Belege der Bestellung' });
        expect(within(liste).getAllByRole('listitem')).toHaveLength(4);
        expect(within(liste).getByText('Rechnung fehlt noch')).toBeInTheDocument();
        // Karten im Mauerwerk statt im Raster
        expect(liste.closest('.columns-1')).not.toBeNull();

        await userEvent.click(within(liste).getByRole('button', { name: 'Suchen' }));
        expect(await screen.findByRole('dialog', { name: /Rechnung suchen – Max Mustermann GmbH/ })).toBeInTheDocument();
        await screen.findByText('Von Max Mustermann GmbH ist noch keine Rechnung da.');
        const vorschlagsAufruf = vi.mocked(fetch).mock.calls.map(c => String(c[0])).find(u => u.includes('ketten-vorschlaege'));
        expect(vorschlagsAufruf).toContain('dokumentIds=1&dokumentIds=2&dokumentIds=3&alleLieferanten=false&typ=RECHNUNG');
    });

    it('„Dokument hinzufügen“ in der Fußzeile sucht alle Arten und ordnet zu', async () => {
        kettenVorschlaege = [{
            dokument: dok(9, 'WERKSTOFFZEUGNIS', 'WZ-9', vorTagen(4)),
            lieferantName: 'Max Mustermann GmbH', kettenDokumentId: 2, kettenDokumentTyp: 'LIEFERSCHEIN', kettenDokumentNummer: 'LS-1',
            trefferquote: 90, sicher: true, eindeutig: true, gruende: ['Belegnummer wird genannt'], gehoertSchonZu: null,
        }];
        zeige();
        await screen.findByRole('list', { name: 'Belege der Bestellung' });
        await userEvent.click(screen.getByRole('button', { name: 'Dokument hinzufügen' }));

        const dialog = await screen.findByRole('dialog', { name: /Dokument zur Kette hinzufügen – Max Mustermann GmbH/ });
        await within(dialog).findByText('Belegnummer wird genannt');
        const vorschlagsAufruf = vi.mocked(fetch).mock.calls.map(c => String(c[0])).find(u => u.includes('ketten-vorschlaege'));
        expect(vorschlagsAufruf).toBe('/api/bestellungen-uebersicht/ketten-vorschlaege?dokumentIds=1&dokumentIds=2&dokumentIds=3&alleLieferanten=false');
        expect(within(dialog).getByRole('button', { name: 'Alle' })).toHaveAttribute('aria-pressed', 'true');

        await userEvent.click(within(dialog).getByRole('button', { name: 'Werkstoffzeugnis WZ-9 gehört dazu' }));
        await waitFor(() => expect(screen.queryByRole('dialog', { name: /Dokument zur Kette hinzufügen/ })).not.toBeInTheDocument());
        const post = vi.mocked(fetch).mock.calls.find(c => String(c[0]) === '/api/bestellungen-uebersicht/ketten-verknuepfen');
        expect(JSON.parse((post?.[1] as RequestInit).body as string)).toEqual({ kettenDokumentId: 2, dokumentId: 9 });
        // danach wird still neu geladen
        await waitFor(() => expect(uebersichtAufrufe()).toHaveLength(2));
    });

    it('zeigt „Dokument hinzufügen“ nicht bei ausgeblendeten Bestellungen', async () => {
        zeige();
        await screen.findByRole('list', { name: 'Belege der Bestellung' });
        expect(screen.getByRole('button', { name: 'Dokument hinzufügen' })).toBeInTheDocument();
        await userEvent.click(screen.getByRole('tab', { name: /Ausgeblendet/ }));
        await screen.findByText('Erika Musterfrau KG');
        expect(screen.queryByRole('button', { name: 'Dokument hinzufügen' })).not.toBeInTheDocument();
    });

    it('öffnet einen Beleg per Zeilenklick in der Vorschau', async () => {
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: /^Lieferschein LS-2 \d/ }));
        expect(await screen.findByTestId('pdf')).toHaveTextContent('/pdf/LS-2.pdf');
    });

    it('zeigt ausgeblendete Rechnungen gedämpft, öffnet sie und hängt nach Rückfrage ab', async () => {
        zeige();
        await screen.findByRole('list', { name: 'Belege der Bestellung' });
        await userEvent.click(screen.getByRole('tab', { name: /Ausgeblendet/ }));
        const karte = await screen.findByText('Erika Musterfrau KG');
        const liste = within(karte.closest('div.p-4') as HTMLElement).getByRole('list', { name: 'Belege der Bestellung' });
        expect(within(liste).getByText('ausgeblendet')).toBeInTheDocument();
        expect(within(liste).queryByText('Rechnung fehlt noch')).not.toBeInTheDocument();

        await userEvent.click(within(liste).getByRole('button', { name: 'Rechnung RE-9 von der Bestellung abhängen' }));
        await userEvent.click(within(await screen.findByRole('dialog', { name: 'Beleg abhängen?' })).getByRole('button', { name: 'Abhängen' }));
        await waitFor(() => expect(vi.mocked(fetch).mock.calls.some(c => String(c[0]) === '/api/bestellungen-uebersicht/abhaengen')).toBe(true));
        // danach wird still neu geladen
        await waitFor(() => expect(uebersichtAufrufe()).toHaveLength(2));
    });

    it('zeigt mehrere Rechnungen auf einer Linie ohne Kästen, das Zeugnis unter seinem Lieferschein', async () => {
        zeige();
        await screen.findByRole('list', { name: 'Belege der Bestellung' });
        await userEvent.click(screen.getByRole('tab', { name: /Erledigt/ }));
        const karte = await screen.findByText('Stahlhandel Beispiel GmbH');
        const listen = within(karte.closest('div.p-4') as HTMLElement).getAllByRole('list', { name: 'Belege der Bestellung' });
        expect(listen).toHaveLength(1);
        expect(within(listen[0]).getAllByRole('listitem').map(z => z.textContent)).toEqual([
            expect.stringContaining('AB-55'),
            expect.stringContaining('LS-10'),
            expect.stringContaining('4107891'),
            expect.stringContaining('LS-20'),
            expect.stringContaining('R-900'),
            expect.stringContaining('R-901'),
        ]);
        expect(within(listen[0]).queryByText('Rechnung fehlt noch')).not.toBeInTheDocument();
        expect(screen.queryByRole('region', { name: /Rechnung R-90/ })).not.toBeInTheDocument();
    });
});

describe('BestellungenUebersicht – mehrere Rechnungen zuordnen', () => {
    beforeEach(() => {
        vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL) => antwortFuer(String(url))));
    });
    afterEach(() => vi.unstubAllGlobals());

    it('nennt auf der Karte die offenen Rechnungen und öffnet die erste offene mit Leiste', async () => {
        zeige();
        await screen.findByRole('list', { name: 'Belege der Bestellung' });
        await userEvent.click(screen.getByRole('tab', { name: /Rechnung zuordnen/ }));

        await userEvent.click(await screen.findByRole('button', { name: 'Projekten zuordnen (2 offen)' }));
        expect(await screen.findByRole('heading', { name: 'Rechnung RE-4712' })).toBeInTheDocument();
        const leiste = screen.getByRole('group', { name: 'Rechnungen dieser Bestellung' });
        // nur Rechnungen, keine Gutschrift
        expect(within(leiste).getAllByRole('button').map(b => b.textContent)).toEqual([
            expect.stringMatching(/^RE-4711.*zugeordnet$/),
            expect.stringMatching(/^RE-4712.*offen$/),
            expect.stringMatching(/^RE-4713.*offen$/),
        ]);
        expect(within(leiste).getByRole('button', { name: /^RE-4712/ })).toHaveAttribute('aria-pressed', 'true');

        // Schließen ohne Speichern lädt nicht neu
        await userEvent.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(screen.queryByRole('group', { name: 'Rechnungen dieser Bestellung' })).toBeNull();
        expect(uebersichtAufrufe()).toHaveLength(1);
    });
});

describe('BestellungenUebersicht – nur 25 laden, Suche über alle', () => {
    /** Laufende Ketten Nr. 1…n, neueste zuerst. */
    const laufendeKetten = (n: number, name = (i: number) => `Lieferant ${i}`) => Array.from({ length: n }, (_, i) => ({
        id: `l${i + 1}`, lieferantId: i + 1, lieferantName: name(i + 1),
        dokumente: [dok(1000 + i, 'AUFTRAGSBESTAETIGUNG', `AB-${i + 1}`, vorTagen(1))],
        verbindungen: [], rechnungsVorschlag: null,
    }));
    const antwort = (laufend: unknown[], gesamtLaufend: number, extra: Record<string, number> = {}) => ({
        offeneAnfragen: [], laufendeBestellungen: laufend, abgeschlossen: [], zugeordnet: [], ausgeblendet: [],
        gesamt: {
            offeneAnfragen: 0, offeneAnfragenAktuell: 0, laufendeBestellungen: gesamtLaufend, laufendeBestellungenAktuell: gesamtLaufend,
            abgeschlossen: 0, zugeordnet: 0, ausgeblendet: 0, ...extra,
        },
    });

    /** Antwort je Anfrage-URL; ein Promise erlaubt, eine Antwort künstlich zu verzögern. */
    let liefere: (url: URL) => unknown | Promise<unknown>;

    beforeEach(() => {
        vi.stubGlobal('fetch', vi.fn(async (eingabe: RequestInfo | URL) => {
            const url = String(eingabe);
            if (url.startsWith('/api/bestellungen-uebersicht?')) {
                const daten = await liefere(new URL(url, 'http://localhost'));
                return { ok: true, json: async () => daten } as Response;
            }
            return antwortFuer(url);
        }));
    });
    afterEach(() => vi.unstubAllGlobals());

    it('lädt 25, zählt aus „gesamt“ und holt mit „Mehr laden“ die nächsten 25', async () => {
        liefere = url => {
            const anzahl = Number(url.searchParams.get('anzahl'));
            return antwort(laufendeKetten(Math.min(anzahl, 30)), 30, { offeneAnfragen: 9, offeneAnfragenAktuell: 4, zugeordnet: 7 });
        };
        zeige();
        expect(await screen.findByText('25 von 30 angezeigt')).toBeInTheDocument();
        expect(uebersichtAufrufe()[0].searchParams.get('anzahl')).toBe('25');
        expect(uebersichtAufrufe()[0].searchParams.has('suche')).toBe(false);
        // Zähler am Reiter aus „gesamt“; „Angebote“ zählt nur die aktuellen
        expect(screen.getByRole('tab', { name: /Bestellt/ })).toHaveTextContent('30');
        expect(screen.getByRole('tab', { name: /Angebote/ })).toHaveTextContent('4');
        expect(screen.getByRole('tab', { name: /Erledigt/ })).toHaveTextContent('7');
        expect(screen.getAllByText(/^Lieferant \d+$/)).toHaveLength(25);

        await userEvent.click(screen.getByRole('button', { name: 'Mehr laden' }));
        await waitFor(() => expect(screen.getAllByText(/^Lieferant \d+$/)).toHaveLength(30));
        expect(uebersichtAufrufe().at(-1)?.searchParams.get('anzahl')).toBe('50');
        // alles da: kein Knopf mehr
        expect(screen.queryByRole('button', { name: 'Mehr laden' })).not.toBeInTheDocument();
        expect(screen.queryByText(/angezeigt$/)).not.toBeInTheDocument();
        // Info-Knopf verschwindet mit
        expect(screen.queryByRole('button', { name: /Mehr zu: Mehr laden/ })).not.toBeInTheDocument();
    });

    it('zeigt „Mehr laden“, solange bei den überfälligen Bestellungen noch welche fehlen', async () => {
        const alt = Array.from({ length: 25 }, (_, i) => ({
            id: `alt${i}`, lieferantId: 900 + i, lieferantName: `Alt ${i}`,
            dokumente: [dok(2000 + i, 'AUFTRAGSBESTAETIGUNG', `AB-ALT-${i}`, vorTagen(100 + i))],
            verbindungen: [], rechnungsVorschlag: null,
        }));
        liefere = () => ({
            ...antwort([...laufendeKetten(5), ...alt], 65),
            gesamt: { ...antwort([], 65).gesamt, laufendeBestellungenAktuell: 5 },
        });
        zeige();
        expect(await screen.findByText('30 von 65 angezeigt')).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: /Seit über 2 Monaten keine Rechnung \(25 von 60\)/ })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Mehr laden' })).toBeInTheDocument();
    });

    it('zeigt den Info-Knopf neben „Mehr laden“', async () => {
        liefere = () => antwort(laufendeKetten(25), 140);
        zeige();
        await screen.findByText('25 von 140 angezeigt');
        await userEvent.click(screen.getByRole('button', { name: /Mehr zu: Mehr laden/ }));
        expect(await screen.findByText(/lädt sie pro Reiter zuerst nur die 25 neuesten Bestellungen/)).toBeInTheDocument();
        expect(screen.getByText(/ältere Bestellungen sind nur noch nicht geladen/)).toBeInTheDocument();
    });

    it('fragt bei der Suche den Server entprellt und setzt die Anzahl zurück', async () => {
        liefere = url => {
            const suche = url.searchParams.get('suche');
            if (suche) return antwort(laufendeKetten(2, i => `Treffer ${i}`), 2);
            return antwort(laufendeKetten(25), 140);
        };
        zeige();
        await screen.findByText('25 von 140 angezeigt');
        await userEvent.click(screen.getByRole('button', { name: 'Mehr laden' }));
        await waitFor(() => expect(uebersichtAufrufe().at(-1)?.searchParams.get('anzahl')).toBe('50'));

        await userEvent.type(screen.getByRole('textbox', { name: 'Bestellungen durchsuchen' }), 'Muster 4711');
        expect(await screen.findByText('Treffer 1')).toBeInTheDocument();
        const suchAufrufe = uebersichtAufrufe().filter(u => u.searchParams.has('suche'));
        // entprellt: nur ein Aufruf für das ganze Wort, nicht pro Tastendruck
        expect(suchAufrufe).toHaveLength(1);
        expect(suchAufrufe[0].searchParams.get('suche')).toBe('Muster 4711');
        expect(suchAufrufe[0].searchParams.get('anzahl')).toBe('25');
        expect(screen.getByRole('tab', { name: /Bestellt/ })).toHaveTextContent('2');
        expect(screen.queryByRole('button', { name: 'Mehr laden' })).not.toBeInTheDocument();

        // Löschen lädt sofort wieder ohne Suche
        await userEvent.click(screen.getByRole('button', { name: 'Suche löschen' }));
        await screen.findByText('25 von 140 angezeigt');
        const letzter = uebersichtAufrufe().at(-1);
        expect(letzter?.searchParams.has('suche')).toBe(false);
        expect(letzter?.searchParams.get('anzahl')).toBe('25');
    });

    it('fragt erst ab 2 Zeichen und zeigt bis dahin einen Hinweis', async () => {
        liefere = () => antwort(laufendeKetten(3), 3);
        zeige();
        await screen.findByText('Lieferant 1');
        const feld = screen.getByRole('textbox', { name: 'Bestellungen durchsuchen' });
        await userEvent.type(feld, 'M');
        expect(screen.getByText('Bitte mindestens 2 Zeichen eingeben.')).toBeInTheDocument();
        expect(feld).toHaveAccessibleDescription('Bitte mindestens 2 Zeichen eingeben.');
        await new Promise(r => setTimeout(r, 500));
        expect(uebersichtAufrufe()).toHaveLength(1);

        await userEvent.type(feld, 'a');
        expect(screen.queryByText('Bitte mindestens 2 Zeichen eingeben.')).not.toBeInTheDocument();
        await waitFor(() => expect(uebersichtAufrufe().at(-1)?.searchParams.get('suche')).toBe('Ma'));
    });

    it('meldet keinen Fehler, wenn die Anfrage beim Verlassen der Seite abgebrochen wird', async () => {
        vi.mocked(fetch).mockImplementation(async (eingabe: RequestInfo | URL, init?: RequestInit) => {
            const url = String(eingabe);
            if (!url.startsWith('/api/bestellungen-uebersicht?')) return antwortFuer(url);
            if (!url.includes('suche=')) return { ok: true, json: async () => antwort(laufendeKetten(3), 3) } as Response;
            // die Suche hängt, bis sie abgebrochen wird
            return new Promise<Response>((_, reject) => {
                init?.signal?.addEventListener('abort', () => reject(new DOMException('signal is aborted without reason', 'AbortError')));
            });
        });
        function Rahmen() {
            const [sichtbar, setSichtbar] = useState(true);
            return (
                <ToastProvider>
                    <ConfirmProvider>
                        <button type="button" onClick={() => setSichtbar(false)}>Seite verlassen</button>
                        {sichtbar && <BestellungenUebersicht />}
                    </ConfirmProvider>
                </ToastProvider>
            );
        }
        render(<Rahmen />);
        await screen.findByText('Lieferant 1');
        await userEvent.type(screen.getByRole('textbox', { name: 'Bestellungen durchsuchen' }), 'Muster');
        await waitFor(() => expect(uebersichtAufrufe().some(u => u.searchParams.has('suche'))).toBe(true));
        await userEvent.click(screen.getByRole('button', { name: 'Seite verlassen' }));
        await new Promise(r => setTimeout(r, 20));
        expect(screen.queryByText(/abort/i)).not.toBeInTheDocument();
        expect(screen.queryByText(/konnten nicht geladen werden/)).not.toBeInTheDocument();
    });

    it('verwirft eine langsame alte Antwort, wenn schon eine neuere Suche läuft', async () => {
        let alteFreigeben: () => void = () => {};
        liefere = url => {
            const suche = url.searchParams.get('suche');
            if (suche === 'alt') {
                return new Promise(resolve => { alteFreigeben = () => resolve(antwort(laufendeKetten(1, () => 'Veraltet GmbH'), 1)); });
            }
            if (suche === 'neu') return antwort(laufendeKetten(1, () => 'Aktuell GmbH'), 1);
            return antwort(laufendeKetten(3), 3);
        };
        zeige();
        await screen.findByText('Lieferant 1');
        const feld = screen.getByRole('textbox', { name: 'Bestellungen durchsuchen' });
        await userEvent.type(feld, 'alt');
        await waitFor(() => expect(uebersichtAufrufe().some(u => u.searchParams.get('suche') === 'alt')).toBe(true));
        await userEvent.clear(feld);
        await userEvent.type(feld, 'neu');
        expect(await screen.findByText('Aktuell GmbH')).toBeInTheDocument();

        alteFreigeben();
        await new Promise(r => setTimeout(r, 20));
        expect(screen.queryByText('Veraltet GmbH')).not.toBeInTheDocument();
        expect(screen.getByText('Aktuell GmbH')).toBeInTheDocument();
    });

    it('nennt Treffer in anderen Reitern aus „gesamt“ und erklärt, wenn nichts passt', async () => {
        liefere = url => {
            const suche = url.searchParams.get('suche');
            if (suche === 'erledigt') {
                return { ...antwort([], 0, { zugeordnet: 12 }), zugeordnet: laufendeKetten(1, () => 'Erledigt GmbH') };
            }
            if (suche) return antwort([], 0);
            return antwort(laufendeKetten(1), 1);
        };
        zeige();
        await screen.findByText('Lieferant 1');
        const feld = screen.getByRole('textbox', { name: 'Bestellungen durchsuchen' });
        await userEvent.type(feld, 'erledigt');
        expect(await screen.findByRole('button', { name: 'Erledigt (12)' })).toBeInTheDocument();

        await userEvent.clear(feld);
        await userEvent.type(feld, 'gibtsnicht');
        expect(await screen.findByText('Keine Bestellung passt zu „gibtsnicht“.')).toBeInTheDocument();
    });

    it('zieht die Zähler beim Ausblenden sofort mit', async () => {
        let ausgeblendet = false;
        liefere = () => ausgeblendet
            ? antwort(laufendeKetten(25).slice(1), 139, { ausgeblendet: 1 })
            : antwort(laufendeKetten(25), 140);
        vi.mocked(fetch).mockImplementation(async (eingabe: RequestInfo | URL) => {
            const url = String(eingabe);
            if (url === '/api/bestellungen-uebersicht/ausblenden') {
                ausgeblendet = true;
                return { ok: true, json: async () => ({}) } as Response;
            }
            if (url.startsWith('/api/bestellungen-uebersicht?')) {
                const daten = await liefere(new URL(url, 'http://localhost'));
                return { ok: true, json: async () => daten } as Response;
            }
            return antwortFuer(url);
        });
        zeige();
        await screen.findByText('25 von 140 angezeigt');
        await userEvent.click(screen.getAllByRole('button', { name: 'Ausblenden' })[0]);
        await waitFor(() => expect(screen.getByRole('tab', { name: /Bestellt/ })).toHaveTextContent('139'));
        expect(screen.getByRole('tab', { name: /Ausgeblendet/ })).toHaveTextContent('1');
        // danach still neu geladen – mit derselben Anzahl
        await waitFor(() => expect(uebersichtAufrufe()).toHaveLength(2));
        expect(uebersichtAufrufe()[1].searchParams.get('anzahl')).toBe('25');
    });
});
