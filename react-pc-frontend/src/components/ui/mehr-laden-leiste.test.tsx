import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MehrLadenLeiste } from './mehr-laden-leiste';

describe('MehrLadenLeiste', () => {
    it('zeigt den Zähler und „Mehr laden“, solange nicht alles geladen ist', () => {
        const onMehrLaden = vi.fn();
        render(<MehrLadenLeiste angezeigt={25} gesamt={140} laedtMehr={false} onMehrLaden={onMehrLaden} />);
        const zaehler = screen.getByText('25 von 140 angezeigt');
        expect(zaehler).toHaveClass('tabular-nums');
        expect(zaehler).toHaveAttribute('aria-live', 'polite');
        fireEvent.click(screen.getByRole('button', { name: 'Mehr laden' }));
        expect(onMehrLaden).toHaveBeenCalledTimes(1);
    });

    it('sperrt den Knopf beim Nachladen und zeigt den Spinner', () => {
        const { container } = render(<MehrLadenLeiste angezeigt={25} gesamt={140} laedtMehr onMehrLaden={vi.fn()} />);
        const knopf = screen.getByRole('button', { name: 'Mehr laden' });
        expect(knopf).toBeDisabled();
        expect(knopf).toHaveAttribute('aria-busy', 'true');
        expect(container.querySelector('.motion-safe\\:animate-spin')).not.toBeNull();
    });

    it('zeigt ohne weitere Einträge nur den Zähler', () => {
        render(<MehrLadenLeiste angezeigt={140} gesamt={140} laedtMehr={false} onMehrLaden={vi.fn()} />);
        expect(screen.getByText('140 von 140 angezeigt')).toBeInTheDocument();
        expect(screen.queryByRole('button')).not.toBeInTheDocument();
    });

    it('nimmt eigene Texte und eine eigene Regel, ob es mehr gibt', () => {
        render(
            <MehrLadenLeiste
                angezeigt={50} gesamt={50} hatMehr laedtMehr={false} onMehrLaden={vi.fn()}
                zaehlerText="50 von 50 Anrufen" knopfText="Weitere Anrufe laden"
            />,
        );
        expect(screen.getByText('50 von 50 Anrufen')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Weitere Anrufe laden' })).toBeEnabled();
    });
});
