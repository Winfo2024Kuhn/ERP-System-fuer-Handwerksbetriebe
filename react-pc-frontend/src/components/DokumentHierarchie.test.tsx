import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { DokumentHierarchie } from './DokumentHierarchie';
import { ToastProvider } from './ui/toast';
import type { AusgangsGeschaeftsDokument, AusgangsGeschaeftsDokumentTyp } from '../types';

const mockFetch = vi.fn();
global.fetch = mockFetch as unknown as typeof fetch;

function dok(
    id: number,
    typ: AusgangsGeschaeftsDokumentTyp,
    dokumentNummer: string,
    vorgaengerId?: number
): AusgangsGeschaeftsDokument {
    return {
        id,
        dokumentNummer,
        typ,
        datum: '2026-01-15',
        betreff: 'Carport Musterstraße',
        betragNetto: 10000,
        gebucht: false,
        storniert: false,
        bearbeitbar: true,
        vorgaengerId,
    } as AusgangsGeschaeftsDokument;
}

function renderBaum(
    dokumente: AusgangsGeschaeftsDokument[],
    kontext: { projektId?: number; anfrageId?: number } = { projektId: 158 }
) {
    return render(
        <ToastProvider>
            <DokumentHierarchie
                ausgangsDokumente={dokumente}
                {...kontext}
                onRefresh={vi.fn()}
                toast={{ error: vi.fn(), success: vi.fn() }}
            />
        </ToastProvider>
    );
}

/** Öffnet das Aktionsmenü des Dokuments über einen Klick auf dessen Karte. */
async function oeffneAktionsmenue(dokumentNummer: string) {
    fireEvent.click(screen.getByText(dokumentNummer));
    await waitFor(() => expect(screen.getByText('Öffnen (neuer Tab)')).toBeInTheDocument());
}

/**
 * Abgerechnet wird immer am untersten Dokument eines Vorgangs. Hängt unter einem
 * Angebot (oder Nachtragsangebot) bereits eine Auftragsbestätigung, entstehen die
 * Rechnungen dort — sonst liefen zwei Abrechnungsstände nebeneinander und der
 * Restbetrag stimmte in keinem von beiden.
 */
describe('DokumentHierarchie – Rechnungen nur am untersten Dokument', () => {
    beforeEach(() => {
        mockFetch.mockReset();
        // Freigabe-Status-Abfrage im useEffect
        mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve({}) } as unknown as Response);
    });

    it('sperrt "Rechnung erstellen" am Angebot, sobald eine Auftragsbestätigung darunter hängt', async () => {
        renderBaum([
            dok(1, 'ANGEBOT', '2026/01/00001'),
            dok(2, 'AUFTRAGSBESTAETIGUNG', '2026/01/00002', 1),
        ]);

        await oeffneAktionsmenue('2026/01/00001');

        expect(screen.getByText('→ Rechnung erstellen').closest('button')).toBeDisabled();
        expect(screen.getByText('Rechnung bitte an der Auftragsbestätigung anlegen')).toBeInTheDocument();
    });

    it('sperrt nicht, wenn am Angebot trotz Auftragsbestätigung bereits abgerechnet wurde', async () => {
        // Bestandsvorgang: Der Abrechnungsverlauf ist am Angebot vollständig,
        // an der AB fehlten die 3.000 EUR — also bleibt es am Angebot.
        renderBaum([
            dok(1, 'ANGEBOT', '2026/01/00001'),
            dok(2, 'ABSCHLAGSRECHNUNG', '2026/01/00002', 1),
            dok(3, 'AUFTRAGSBESTAETIGUNG', '2026/01/00003', 1),
        ]);

        await oeffneAktionsmenue('2026/01/00001');

        expect(screen.getByText('→ Rechnung erstellen').closest('button')).toBeEnabled();
    });

    it('sperrt "Rechnung erstellen" auch am Nachtragsangebot mit Auftragsbestätigung', async () => {
        renderBaum([
            dok(1, 'ANGEBOT', '2026/01/00001'),
            dok(3, 'NACHTRAGSANGEBOT', 'NA-2026/01/00001'),
            dok(4, 'AUFTRAGSBESTAETIGUNG', '2026/01/00003', 3),
        ]);

        await oeffneAktionsmenue('NA-2026/01/00001');

        expect(screen.getByText('→ Rechnung erstellen').closest('button')).toBeDisabled();
    });

    it('erlaubt "Rechnung erstellen" am Angebot ohne Auftragsbestätigung', async () => {
        renderBaum([dok(1, 'ANGEBOT', '2026/01/00001')]);

        await oeffneAktionsmenue('2026/01/00001');

        expect(screen.getByText('→ Rechnung erstellen').closest('button')).toBeEnabled();
        expect(screen.queryByText('Rechnung bitte an der Auftragsbestätigung anlegen')).not.toBeInTheDocument();
    });

    it('bietet gar keine Auftragsbestätigung an', async () => {
        // Eine AB bestätigt einen Auftrag — den gibt es erst, wenn aus der
        // Anfrage ein Projekt geworden ist. Sie entsteht deshalb nur im
        // Projekt-Editor oder automatisch bei digitaler Angebotsannahme.
        renderBaum([dok(1, 'ANGEBOT', '2026/01/00001')], { anfrageId: 42 });

        await oeffneAktionsmenue('2026/01/00001');

        expect(screen.queryByText('→ Auftragsbestätigung')).not.toBeInTheDocument();
    });

    it('erlaubt "Rechnung erstellen" an der Auftragsbestätigung selbst', async () => {
        renderBaum([
            dok(1, 'ANGEBOT', '2026/01/00001'),
            dok(2, 'AUFTRAGSBESTAETIGUNG', '2026/01/00002', 1),
        ]);

        await oeffneAktionsmenue('2026/01/00002');

        expect(screen.getByText('→ Rechnung erstellen').closest('button')).toBeEnabled();
    });
});

/**
 * Löschen gibt es nur für Entwürfe. Ein digital angenommenes Dokument ist verbindlich –
 * auch eine AB, deren automatischer Versand gescheitert ist (dann fehlt das Versanddatum).
 */
describe('DokumentHierarchie – Löschen nur bei Entwürfen', () => {
    beforeEach(() => {
        mockFetch.mockReset();
        mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve({}) } as unknown as Response);
    });

    it('bietet Löschen beim Angebots-Entwurf an', async () => {
        renderBaum([dok(1, 'ANGEBOT', '2026/01/00001')]);

        await oeffneAktionsmenue('2026/01/00001');

        expect(screen.getByText('Löschen')).toBeInTheDocument();
    });

    it('bietet kein Löschen bei digital angenommener Auftragsbestätigung ohne Versanddatum', async () => {
        renderBaum([
            dok(1, 'ANGEBOT', '2026/01/00001'),
            { ...dok(2, 'AUFTRAGSBESTAETIGUNG', '2026/01/00002', 1), digitalAngenommen: true },
        ]);

        await oeffneAktionsmenue('2026/01/00002');

        expect(screen.queryByText('Löschen')).not.toBeInTheDocument();
    });

    it('bietet kein Löschen bei versandtem Angebot', async () => {
        renderBaum([{ ...dok(1, 'ANGEBOT', '2026/01/00001'), versandDatum: '2026-01-16' }]);

        await oeffneAktionsmenue('2026/01/00001');

        expect(screen.queryByText('Löschen')).not.toBeInTheDocument();
    });
});

/**
 * Issue #262: Eine direkt angelegte, später stornierte Rechnung darf den Vorgang nicht
 * blockieren — sonst lässt sich keine neue Rechnung mehr anlegen.
 */
describe('DokumentHierarchie – stornierte Basisdokumente', () => {
    beforeEach(() => {
        mockFetch.mockReset();
        mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve({}) } as unknown as Response);
    });

    it('lässt "Dokument erstellen" nach Storno der einzigen Rechnung wieder zu', () => {
        const rechnung = { ...dok(1, 'RECHNUNG', '2026/01/00001'), gebucht: true, storniert: true };
        renderBaum([rechnung, { ...dok(2, 'STORNO', 'ST-2026/01/00002', 1), gebucht: true }]);

        expect(screen.getByRole('button', { name: /Dokument erstellen/ })).toBeEnabled();
    });

    it('sperrt "Dokument erstellen", solange eine aktive Rechnung ohne Vorgänger existiert', () => {
        renderBaum([{ ...dok(1, 'RECHNUNG', '2026/01/00001'), gebucht: true }]);

        expect(screen.getByRole('button', { name: /Dokument erstellen/ })).toBeDisabled();
    });
});

describe('DokumentHierarchie – Info zum Rechnungstyp', () => {
    beforeEach(() => {
        mockFetch.mockReset();
        mockFetch.mockImplementation((url: string) => {
            if (url === '/api/ausgangs-dokumente/1/abrechnungsverlauf') {
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve({
                        basisdokumentId: 1, basisdokumentNummer: '2026/01/00001', basisdokumentTyp: 'ANGEBOT',
                        basisdokumentBetragNetto: 10000, positionen: [], bereitsAbgerechnet: 0, restbetrag: 10000,
                    }),
                } as unknown as Response);
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) } as unknown as Response);
        });
    });

    it('zeigt neben "Rechnungstyp" ein (i) ohne Absatz zur einfachen Rechnung', async () => {
        renderBaum([dok(1, 'ANGEBOT', '2026/01/00001')]);
        await oeffneAktionsmenue('2026/01/00001');
        fireEvent.click(screen.getByText('→ Rechnung erstellen'));

        fireEvent.click(await screen.findByRole('button', { name: 'Mehr zu: Welche Rechnung passt?' }));

        const popover = screen.getByRole('dialog', { name: 'Welche Rechnung passt?' });
        expect(popover).toHaveTextContent('Abschlagsrechnung');
        expect(popover).toHaveTextContent('Schlussrechnung');
        expect(popover).not.toHaveTextContent('Einfache Rechnung');
    });
});

describe('DokumentHierarchie – Rechnungs-Dialog bei abgeschlossenem Auftrag', () => {
    function verlaufMock(abschliessendeRechnungNummer: string | null) {
        mockFetch.mockReset();
        mockFetch.mockImplementation((url: string) => {
            if (url === '/api/ausgangs-dokumente/1/abrechnungsverlauf') {
                return Promise.resolve({
                    ok: true,
                    json: () => Promise.resolve({
                        basisdokumentId: 1, basisdokumentNummer: '2026/01/00001', basisdokumentTyp: 'ANGEBOT',
                        basisdokumentBetragNetto: 10000, positionen: [],
                        bereitsAbgerechnet: abschliessendeRechnungNummer ? 10000 : 0,
                        restbetrag: abschliessendeRechnungNummer ? 0 : 10000,
                        abschliessendeRechnungNummer,
                    }),
                } as unknown as Response);
            }
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) } as unknown as Response);
        });
    }

    async function oeffneDialog() {
        renderBaum([dok(1, 'ANGEBOT', '2026/01/00001')]);
        await oeffneAktionsmenue('2026/01/00001');
        fireEvent.click(screen.getByText('→ Rechnung erstellen'));
        return screen.findByRole('heading', { name: 'Rechnung erstellen' });
    }

    it('zeigt den Hinweis mit Rechnungsnummer, keine Auswahl, Erstellen gesperrt', async () => {
        verlaufMock('RE-2026-0042');
        await oeffneDialog();

        expect(await screen.findByRole('status')).toHaveTextContent('Dieser Auftrag ist mit Rechnung RE-2026-0042 abgeschlossen.');
        expect(screen.queryByText('Rechnungstyp')).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Abschlagsrechnung/ })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Rechnung erstellen' })).toBeDisabled();
    });

    it('bietet ohne abschliessende Rechnung die Auswahl wie bisher an', async () => {
        verlaufMock(null);
        await oeffneDialog();

        expect(await screen.findByRole('button', { name: /Abschlagsrechnung/ })).toBeEnabled();
        expect(screen.queryByRole('status')).not.toBeInTheDocument();
        expect(screen.getByText('Rechnungstyp')).toBeInTheDocument();
    });
});
