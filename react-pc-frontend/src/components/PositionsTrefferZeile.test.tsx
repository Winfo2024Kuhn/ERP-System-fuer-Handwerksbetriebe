import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { PositionsTrefferZeile } from './PositionsTrefferZeile';

describe('PositionsTrefferZeile', () => {
    it('hebt den Suchbegriff hervor und nennt weitere Treffer', () => {
        const { container } = render(
            <PositionsTrefferZeile trefferText="Flachstahl 50x5 · S235JR · Charge 123456" weitereTreffer={2} suchbegriff="s235" />,
        );
        const markiert = container.querySelectorAll('mark');
        expect(markiert).toHaveLength(1);
        expect(markiert[0]).toHaveTextContent('S235');
        expect(screen.getByTestId('positions-treffer')).toHaveTextContent('Gefunden in Position: Flachstahl 50x5 · S235JR · Charge 123456 und 2 weitere');
    });

    it('zeigt Text aus dem Server nur als Text, nie als HTML', () => {
        const { container } = render(
            <PositionsTrefferZeile trefferText={'<img src=x onerror="alert(1)"> Flachstahl'} suchbegriff="flachstahl" />,
        );
        expect(container.querySelector('img')).toBeNull();
        expect(screen.getByTestId('positions-treffer')).toHaveTextContent('<img src=x onerror="alert(1)"> Flachstahl');
        expect(screen.queryByText(/weitere/)).toBeNull();
    });
});
