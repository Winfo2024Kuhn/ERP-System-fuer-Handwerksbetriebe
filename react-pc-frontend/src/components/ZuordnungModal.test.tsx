import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ZuordnungModal } from './ZuordnungModal';

vi.mock('./ui/PdfCanvasViewer', () => ({
    PdfCanvasViewer: () => <div data-testid="pdf-viewer" />,
}));
vi.mock('./ui/toast', () => ({
    useToast: () => ({ error: vi.fn() }),
}));
vi.mock('./ProjectSelectModal', () => ({
    ProjectSelectModal: () => null,
}));
vi.mock('./KostenstelleSelectModal', () => ({
    KostenstelleSelectModal: () => null,
}));

const mockFetch = vi.fn();
global.fetch = mockFetch;

const makeGeschaeftsdatenResponse = (betragNetto: number, betragBrutto: number) => ({
    id: 1,
    dokumentNummer: 'TEST-001',
    dokumentDatum: '2026-04-02',
    betragNetto,
    betragBrutto,
    mwstSatz: 0.19,
});

const defaultProps = {
    geschaeftsdokumentId: 1,
    onClose: vi.fn(),
    onSuccess: vi.fn(),
};

describe('ZuordnungModal – Netto-Berechnung Regression', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    /**
     * Regression Bug 2: Beim Laden aus der DB wurden brutto-basierte Altdaten-Beträge
     * direkt übernommen statt aus prozentanteil × betragNetto neu berechnet.
     * Folge: "Zugeordnet" zeigte Brutto-Summe, Rest war negativ.
     */
    it('berechnet betrag beim Laden aus prozentanteil mal betragNetto nicht aus gespeichertem Brutto-Wert', async () => {
        mockFetch.mockImplementation((url: string) => {
            if (url.includes('/geschaeftsdaten/')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve(makeGeschaeftsdatenResponse(100, 119)) });
            }
            if (url.includes('/zuordnungen/')) {
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve([
                        // betrag=119 ist brutto-basiert (100% von 119), soll auf 100,00 normiert werden
                        { projektId: 10, projektName: 'Max Mustermann Projekt', prozentanteil: 100, betrag: 119.00, beschreibung: '' },
                    ]),
                });
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        });

        render(<ZuordnungModal {...defaultProps} />);

        await waitFor(() => expect(screen.getByText('Zugeordnet:')).toBeInTheDocument());

        // "Zugeordnet"-Wert soll 100,00 (Netto) zeigen – "(100.0 %)" erscheint nur in dieser Zeile
        // Bug wäre: "119,00 € (100.0 %)" statt "100,00 € (100.0 %)"
        expect(screen.getByText(/100,00.*100\.0.*%/)).toBeInTheDocument();
    });

    /**
     * Regression Bug 1: sumBetrag nutzte gespeicherten a.betrag des letzten Eintrags
     * statt den auto-berechneten Rest → Rest zeigte falschen Wert (z.B. -21,98 €).
     */
    it('zeigt Rest 0 wenn zwei Anteile zusammen 100 Prozent ergeben', async () => {
        mockFetch.mockImplementation((url: string) => {
            if (url.includes('/geschaeftsdaten/')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve(makeGeschaeftsdatenResponse(100, 119)) });
            }
            if (url.includes('/zuordnungen/')) {
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve([
                        // Altdaten: beide Beträge brutto-basiert (75% + 25% von 119)
                        { projektId: 10, projektName: 'Max Mustermann Projekt', prozentanteil: 75, betrag: 89.25, beschreibung: '' },
                        { projektId: 20, projektName: 'Musterstraße Baustelle', prozentanteil: 25, betrag: 29.75, beschreibung: '' },
                    ]),
                });
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        });

        render(<ZuordnungModal {...defaultProps} />);

        await waitFor(() => expect(screen.getByText('Rest:')).toBeInTheDocument());

        // Rest muss 0,00 € sein – nicht negativ wie beim Bug (z.B. -21,98 €)
        expect(screen.getByText('0,00 €')).toBeInTheDocument();
    });

    /**
     * Regression Bug 1 (Eingabe): Nach manueller Änderung eines Anteils bleibt
     * der berechnete Rest korrekt 0 (letzter Eintrag = Rest).
     */
    it('Rest bleibt 0 nachdem der erste Anteil manuell geaendert wird', async () => {
        mockFetch.mockImplementation((url: string) => {
            if (url.includes('/geschaeftsdaten/')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve(makeGeschaeftsdatenResponse(100, 119)) });
            }
            if (url.includes('/zuordnungen/')) {
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve([
                        { projektId: 10, projektName: 'Projekt A', prozentanteil: 75, betrag: 75.00, beschreibung: '' },
                        { projektId: 20, projektName: 'Projekt B', prozentanteil: 25, betrag: 25.00, beschreibung: '' },
                    ]),
                });
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        });

        render(<ZuordnungModal {...defaultProps} />);

        await waitFor(() => expect(screen.getByText('Rest:')).toBeInTheDocument());

        // Ersten Eingabewert auf 60% ändern → letzter Eintrag (Projekt B) wird auto-Rest 40%
        const inputs = screen.getAllByRole('spinbutton');
        fireEvent.change(inputs[0], { target: { value: '60' } });

        // Rest muss weiterhin 0,00 sein – Bug war: sumBetrag nutzte alten gespeicherten
        // betrag (25) statt auto-berechneten Rest (40) → rest = 100 - 60 - 25 = 15
        await waitFor(() => {
            expect(screen.getByText('0,00 €')).toBeInTheDocument();
        });
    });
});
