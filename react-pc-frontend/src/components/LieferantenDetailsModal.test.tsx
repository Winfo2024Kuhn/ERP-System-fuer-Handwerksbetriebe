import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { LieferantenDetailsModal, type LieferantUmsatz } from './LieferantenDetailsModal';

const mockFetch = vi.fn();
global.fetch = mockFetch as unknown as typeof fetch;

/**
 * Zwölf Dummy-Lieferanten – genug, damit es Plätze hinter den Top 10 gibt.
 * Absteigend nach Umsatz, so wie die API sie liefert.
 */
const lieferanten: LieferantUmsatz[] = [
    { name: 'Muster Stahlhandel GmbH', bestellungen: 12, netto: 5000 },
    { name: 'Beispiel Blechbau KG', bestellungen: 9, netto: 4000 },
    { name: 'Dummy Verzinkerei', bestellungen: 7, netto: 3000 },
    { name: 'Testbau Beschläge', bestellungen: 6, netto: 2000 },
    { name: 'Muster Farben AG', bestellungen: 5, netto: 1500 },
    { name: 'Beispiel Schrauben', bestellungen: 4, netto: 1200 },
    { name: 'Dummy Werkzeug', bestellungen: 3, netto: 1000 },
    { name: 'Testglas GmbH', bestellungen: 3, netto: 800 },
    { name: 'Muster Torantriebe', bestellungen: 2, netto: 600 },
    { name: 'Beispiel Energie', bestellungen: 1, netto: 500 },
    { name: 'Dummy Kleinteile', bestellungen: 1, netto: 300 },
    { name: 'Testservice Punkt', bestellungen: 0, netto: 100 },
];

const renderModal = (props: Partial<React.ComponentProps<typeof LieferantenDetailsModal>> = {}) => {
    const onClose = vi.fn();
    const onHervorheben = vi.fn();
    render(
        <LieferantenDetailsModal
            isOpen
            onClose={onClose}
            lieferanten={lieferanten}
            zeitraum="2026"
            hervorgehobenerRang={null}
            onHervorheben={onHervorheben}
            {...props}
        />
    );
    return { onClose, onHervorheben };
};

/** Antwort der Stammdaten-Suche (`/api/lieferanten`) für einen einzelnen Treffer. */
const stammdatenTreffer = (name: string) => ({
    ok: true,
    json: async () => ({
        lieferanten: [{ id: 1, lieferantenname: name, istAktiv: true }],
        gesamt: 1,
    }),
});

describe('LieferantenDetailsModal', () => {
    beforeEach(() => {
        mockFetch.mockReset();
        mockFetch.mockResolvedValue({ ok: true, json: async () => ({ lieferanten: [], gesamt: 0 }) });
    });

    it('zeigt alle Lieferanten, nicht nur die zehn aus der Grafik', () => {
        renderModal();

        expect(screen.getByText('Muster Stahlhandel GmbH')).toBeInTheDocument();
        // Platz 12 – in der Grafik nicht sichtbar, hier schon
        expect(screen.getByText('Testservice Punkt')).toBeInTheDocument();
        expect(screen.getByText('12 Lieferanten')).toBeInTheDocument();
    });

    it('markiert, welche Lieferanten bereits in der Grafik stehen', () => {
        renderModal();
        // Genau die ersten zehn stehen in der Grafik
        expect(screen.getAllByText('steht in der Grafik')).toHaveLength(10);
    });

    it('summiert das gesamte Einkaufsvolumen', () => {
        renderModal();
        // 5000+4000+3000+2000+1500+1200+1000+800+600+500+300+100 = 20000
        expect(screen.getByText(/Gesamt:.*20\.000/)).toBeInTheDocument();
    });

    it('rechnet den Anteil je Lieferant in deutscher Schreibweise aus', () => {
        renderModal();
        // 5000 von 20000 = 25,0 %
        expect(screen.getByText('25,0 %')).toBeInTheDocument();
    });

    it('filtert die Liste über das Suchfeld', async () => {
        const user = userEvent.setup();
        renderModal();

        await user.type(screen.getByLabelText('Lieferant in der Liste suchen'), 'Verzink');

        expect(screen.getByText('Dummy Verzinkerei')).toBeInTheDocument();
        expect(screen.queryByText('Muster Stahlhandel GmbH')).not.toBeInTheDocument();
        expect(screen.getByText('1 von 12 Lieferanten')).toBeInTheDocument();
    });

    it('behält die Platzierung bei, auch wenn gefiltert wird', async () => {
        const user = userEvent.setup();
        renderModal();

        await user.type(screen.getByLabelText('Lieferant in der Liste suchen'), 'Testservice');

        const zeile = screen.getByText('Testservice Punkt').closest('tr');
        expect(zeile).not.toBeNull();
        // Platz 12 bleibt Platz 12 und wird nicht zu Platz 1
        expect(within(zeile!).getByText('12')).toBeInTheDocument();
    });

    it('sortiert nach Name um', async () => {
        const user = userEvent.setup();
        renderModal();

        await user.click(screen.getByRole('button', { name: 'Lieferant' }));

        const zeilen = screen.getAllByRole('row').slice(1); // Kopfzeile überspringen
        const ersterName = within(zeilen[0]).getAllByRole('cell')[1].textContent;
        expect(ersterName).toContain('Testservice Punkt');
    });

    it('bietet nur für Lieferanten hinter Platz 10 das Einblenden in die Grafik an', async () => {
        const user = userEvent.setup();
        const { onHervorheben } = renderModal();

        // 12 Lieferanten, 10 davon schon in der Grafik -> 2 Buttons
        const buttons = screen.getAllByRole('button', { name: /in Grafik zeigen$/ });
        expect(buttons).toHaveLength(2);

        await user.click(screen.getByRole('button', { name: 'Dummy Kleinteile in Grafik zeigen' }));
        // Der Platz identifiziert den Lieferanten, nicht der Name (Namensdubletten im Bestand)
        expect(onHervorheben).toHaveBeenCalledWith(11);
    });

    it('erlaubt das Entfernen des eingeblendeten Lieferanten', async () => {
        const user = userEvent.setup();
        const { onHervorheben } = renderModal({ hervorgehobenerRang: 11 });

        await user.click(screen.getByRole('button', { name: 'Dummy Kleinteile aus Grafik entfernen' }));

        expect(onHervorheben).toHaveBeenCalledWith(null);
    });

    it('erklärt eine leere Trefferliste', async () => {
        const user = userEvent.setup();
        renderModal();

        await user.type(screen.getByLabelText('Lieferant in der Liste suchen'), 'GibtEsNicht');

        expect(screen.getByText('Kein Lieferant gefunden')).toBeInTheDocument();
    });

    it('weist auf einen Zeitraum ohne Einkäufe hin', () => {
        renderModal({ lieferanten: [] });
        expect(screen.getByText('Keine Einkäufe in 2026')).toBeInTheDocument();
    });

    it('schließt per Escape und per Button', async () => {
        const user = userEvent.setup();
        const { onClose } = renderModal();

        await user.keyboard('{Escape}');
        expect(onClose).toHaveBeenCalledTimes(1);

        await user.click(screen.getByRole('button', { name: 'Schließen' }));
        expect(onClose).toHaveBeenCalledTimes(2);
    });

    it('rendert nichts, solange es geschlossen ist', () => {
        renderModal({ isOpen: false });
        expect(screen.queryByText('Alle Lieferanten')).not.toBeInTheDocument();
    });

    it('springt zum Lieferanten, wenn er über die Stammdaten-Suche gewählt wird', async () => {
        const user = userEvent.setup();
        // Platz 3 – seine Zeile hat keinen "In Grafik zeigen"-Button, der Name ist also eindeutig
        mockFetch.mockResolvedValue(stammdatenTreffer('Dummy Verzinkerei'));
        renderModal();

        await user.click(screen.getByRole('button', { name: 'Aus Lieferantenliste' }));
        await user.click(await screen.findByRole('button', { name: /Dummy Verzinkerei/ }));

        // Die Liste ist auf den gewählten Lieferanten eingeschränkt
        expect(await screen.findByText('1 von 12 Lieferanten')).toBeInTheDocument();
        expect(screen.getByText('Dummy Verzinkerei')).toBeInTheDocument();
        expect(screen.queryByText('Muster Stahlhandel GmbH')).not.toBeInTheDocument();
    });

    it('sagt Bescheid, wenn der gewählte Lieferant im Zeitraum nichts geliefert hat', async () => {
        const user = userEvent.setup();
        mockFetch.mockResolvedValue(stammdatenTreffer('Beispiel Ohne Umsatz GmbH'));
        renderModal();

        await user.click(screen.getByRole('button', { name: 'Aus Lieferantenliste' }));
        await user.click(await screen.findByRole('button', { name: /Beispiel Ohne Umsatz GmbH/ }));

        const hinweis = await screen.findByText(/haben Sie 2026 nichts eingekauft/);
        expect(hinweis).toBeInTheDocument();
        // Die Liste bleibt vollständig – der Lieferant taucht darin ja nicht auf
        expect(screen.getByText('12 Lieferanten')).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: 'Hinweis ausblenden' }));
        expect(screen.queryByText(/haben Sie 2026 nichts eingekauft/)).not.toBeInTheDocument();
    });
});
