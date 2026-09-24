/**
 * Vitest-Suite fuer AlternativGruppeBox – der gezeichnete Auswahl-Kasten.
 *
 * Der Kasten ist rein visuell: Die Zusammengehoerigkeit steckt im
 * `alternativGruppe`-Feld der einzelnen Bloecke, nicht in einem Container-Block.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { AlternativGruppeBox } from './AlternativGruppeBox';

describe('AlternativGruppeBox', () => {
    it('zeigt Name und Pflichtwahl-Hinweis', () => {
        render(
            <AlternativGruppeBox name="Geländer" isLocked={false} onUmbenennen={vi.fn()} onAufloesen={vi.fn()}>
                <div data-testid="variante" />
            </AlternativGruppeBox>,
        );

        expect(screen.getByText('Geländer')).toBeInTheDocument();
        expect(screen.getByText(/wählt genau eines/i)).toBeInTheDocument();
        expect(screen.getByTestId('variante')).toBeInTheDocument();
    });

    it('meldet das Aufloesen', () => {
        const onAufloesen = vi.fn();
        render(
            <AlternativGruppeBox name="Geländer" isLocked={false} onUmbenennen={vi.fn()} onAufloesen={onAufloesen}>
                <div />
            </AlternativGruppeBox>,
        );
        fireEvent.click(screen.getByRole('button', { name: /auswahl auflösen/i }));

        expect(onAufloesen).toHaveBeenCalledWith('Geländer');
    });
});
