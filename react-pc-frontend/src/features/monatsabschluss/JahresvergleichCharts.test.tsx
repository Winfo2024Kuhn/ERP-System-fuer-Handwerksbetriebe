import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { JahresvergleichCharts } from './JahresvergleichCharts';
import type { Jahresvergleich } from './types';

vi.mock('react-chartjs-2', () => ({
    Line: ({ data }: { data: { datasets: { label: string }[] } }) => (
        <div data-testid="mock-line-chart">
            {data.datasets.map(d => d.label).join(', ')}
        </div>
    ),
}));

const mockDaten: Jahresvergleich = {
    jahr: 2026,
    vorjahr: 2025,
    aktuellesJahr: [
        { monat: 1, arbeitsstunden: 160.5, krankheitstage: 2, urlaubstage: 0 },
        { monat: 2, arbeitsstunden: 150.0, krankheitstage: 0, urlaubstage: 1 },
        { monat: 3, arbeitsstunden: 170.0, krankheitstage: 1.5, urlaubstage: 5 },
    ],
    vorjahrDaten: [
        { monat: 1, arbeitsstunden: 150.0, krankheitstage: 1, urlaubstage: 0 },
        { monat: 2, arbeitsstunden: 150.0, krankheitstage: 1, urlaubstage: 2 },
        { monat: 3, arbeitsstunden: 160.0, krankheitstage: 0, urlaubstage: 3 },
    ],
};

describe('JahresvergleichCharts', () => {
    it('rendert die Kopfzeile und alle 3 Charts in der Standardansicht', () => {
        render(<JahresvergleichCharts daten={mockDaten} jahr={2026} />);

        expect(screen.getByText('Jahresvergleich 2026 vs. 2025')).toBeVisible();
        expect(screen.getByRole('heading', { name: 'Arbeitsstunden im Jahresverlauf' })).toBeVisible();
        expect(screen.getByRole('heading', { name: 'Krankheitstage' })).toBeVisible();
        expect(screen.getByRole('heading', { name: 'Urlaubstage' })).toBeVisible();

        const charts = screen.getAllByTestId('mock-line-chart');
        expect(charts).toHaveLength(3);
    });

    it('zeigt die aggregierten Jahres-Kennzahlen mit deutscher Formatierung', () => {
        render(<JahresvergleichCharts daten={mockDaten} jahr={2026} />);

        // Arbeitsstunden: 160.5 + 150 + 170 = 480.50 h
        expect(screen.getByText('480,50 h')).toBeVisible();
        // Vorjahr: 150 + 150 + 160 = 460.00 h
        expect(screen.getByText(/Vorjahr: 460,00 h/)).toBeVisible();
        // Diff: +20.50 h
        expect(screen.getByText(/\+20,50 h/)).toBeVisible();

        // Krankheitstage: 2 + 0 + 1.5 = 3.5 Tage
        expect(screen.getByText('3,5 Tage')).toBeVisible();
        // Vorjahr: 1 + 1 + 0 = 2.0 Tage
        expect(screen.getByText(/Vorjahr: 2,0 Tage/)).toBeVisible();

        // Urlaubstage: 0 + 1 + 5 = 6.0 Tage
        expect(screen.getByText('6,0 Tage')).toBeVisible();
    });

    it('wechselt auf Detailansicht bei Klick auf Filter oder KPI-Kachel', () => {
        render(<JahresvergleichCharts daten={mockDaten} jahr={2026} />);

        // Klick auf Tab Arbeitsstunden
        fireEvent.click(screen.getByRole('button', { name: 'Arbeitsstunden', exact: true }));

        expect(screen.getByText('Arbeitsstunden im Detail (2026 vs. 2025)')).toBeVisible();
        // Monatstabelle vorhanden
        expect(screen.getByRole('table')).toBeVisible();
        expect(screen.getByText('Januar')).toBeVisible();
        expect(screen.getByText('160,50 h')).toBeVisible();
        expect(screen.getAllByText('150,00 h').length).toBeGreaterThanOrEqual(1);
        expect(screen.getByText('+10,50 h')).toBeVisible();

        // Nur 1 Chart in der Detailansicht
        expect(screen.getAllByTestId('mock-line-chart')).toHaveLength(1);

        // Zurück auf Alle anzeigen
        fireEvent.click(screen.getByRole('button', { name: 'Alle anzeigen' }));
        expect(screen.getAllByTestId('mock-line-chart')).toHaveLength(3);
    });

    it('fängt Ladezustand und leere Daten sicher ab', () => {
        const { rerender } = render(<JahresvergleichCharts daten={null} jahr={2026} laedt={true} />);
        expect(screen.queryByText('Jahresvergleich 2026 vs. 2025')).not.toBeInTheDocument();

        rerender(<JahresvergleichCharts daten={null} jahr={2026} laedt={false} />);
        expect(screen.getByText('Jahresvergleich 2026 vs. 2025')).toBeVisible();
        expect(screen.getByText('0,00 h')).toBeVisible();
    });
});
