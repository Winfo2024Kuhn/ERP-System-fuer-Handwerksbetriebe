import { fireEvent, render, screen } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { EmailComposeFusszeile } from './EmailComposeFusszeile';

type Props = ComponentProps<typeof EmailComposeFusszeile>;

function zeige(props: Partial<Props> = {}) {
    const alle: Props = {
        entwurfHinweis: 'Entwurf gespeichert', entwurfFehler: false, onErneutSpeichern: vi.fn(),
        zurueckText: 'Schließen', speichert: false, zurueckGesperrt: false, onZurueck: vi.fn(),
        sendet: false, sendenGesperrt: false, onSenden: vi.fn(), ...props,
    };
    render(<EmailComposeFusszeile {...alle} />);
    return alle;
}

describe('EmailComposeFusszeile', () => {
    it('zeigt den Entwurfsstatus und den Zurück-Knopf mit wählbarer Beschriftung', () => {
        const props = zeige({ zurueckText: 'Zurück zum Verlauf' });
        expect(screen.getByRole('status')).toHaveTextContent('Entwurf gespeichert');
        expect(screen.queryByRole('button', { name: 'Erneut speichern' })).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Zurück zum Verlauf' }));
        expect(props.onZurueck).toHaveBeenCalledTimes(1);
        fireEvent.click(screen.getByRole('button', { name: 'E-Mail senden' }));
        expect(props.onSenden).toHaveBeenCalledTimes(1);
    });

    it('bietet nach einem Speicherfehler „Erneut speichern“ an', () => {
        const props = zeige({ entwurfHinweis: 'Entwurf nicht gespeichert', entwurfFehler: true });
        fireEvent.click(screen.getByRole('button', { name: 'Erneut speichern' }));
        expect(props.onErneutSpeichern).toHaveBeenCalledTimes(1);
    });

    it('zeigt beim Speichern „Wird gespeichert…“ und sperrt den Zurück-Knopf', () => {
        zeige({ speichert: true, zurueckGesperrt: true });
        expect(screen.getByRole('button', { name: 'Wird gespeichert…' })).toBeDisabled();
    });

    it('sperrt „E-Mail senden“ mit Hinweis und zeigt den Versand an', () => {
        zeige({ sendenGesperrt: true, sendenHinweis: 'Höchstens 50 Empfänger pro Sammel-Mail.' });
        const senden = screen.getByRole('button', { name: 'E-Mail senden' });
        expect(senden).toBeDisabled();
        expect(senden).toHaveAttribute('title', 'Höchstens 50 Empfänger pro Sammel-Mail.');
    });

    it('meldet einen laufenden Versand', () => {
        zeige({ sendet: true, sendenGesperrt: true });
        expect(screen.getByRole('button', { name: /Wird gesendet/ })).toBeDisabled();
    });
});
