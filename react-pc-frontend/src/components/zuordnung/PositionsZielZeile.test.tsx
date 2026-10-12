import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { DndContext } from '@dnd-kit/core';
import { PositionsZielZeile } from './PositionsZielZeile';
import { zielFarbe } from './zielFarben';
import type { VorschauZiel } from './positionen';

function zeile(nebenkostenMitBezug: number) {
    const vorschau: VorschauZiel = {
        projektId: 1, kostenstelleId: null, warenwert: 500, anteilProzent: 50,
        betragNetto: 450, betragBrutto: 535.5, betrag: 535.5, nebenkostenMitBezug,
    };
    render(
        <DndContext>
            <PositionsZielZeile schluessel="p:1" name="Carport Musterfrau" istKostenstelle={false} farbe={zielFarbe(0)}
                anzahlPositionen={1} vorschau={vorschau} beschreibung="" streckungJahre={1} rechnungsJahr={2026}
                onBeschreibung={vi.fn()} onStreckung={vi.fn()} onEntfernen={vi.fn()} />
        </DndContext>,
    );
}

describe('PositionsZielZeile', () => {
    it('zeigt einen Rabatt mit Bezug ohne Minuszeichen', () => {
        zeile(-50);
        expect(screen.getByText('inkl. 50,00 € Rabatt für eigene Positionen')).toBeInTheDocument();
    });

    it('zeigt Zuschläge mit Bezug', () => {
        zeile(30);
        expect(screen.getByText('inkl. 30,00 € Zuschläge für eigene Positionen')).toBeInTheDocument();
    });

    it('ohne Bezug kein Zusatz', () => {
        zeile(0);
        expect(screen.queryByText(/für eigene Positionen/)).toBeNull();
    });
});
