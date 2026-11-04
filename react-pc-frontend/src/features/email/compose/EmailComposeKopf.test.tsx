import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EmailComposeKopf } from './EmailComposeKopf';

describe('EmailComposeKopf', () => {
    it('zeigt Titel und Untertitel und schließt per X', () => {
        const onSchliessen = vi.fn();
        render(<EmailComposeKopf titel="Antwort" untertitel="Projekt: Treppe Mustermann" onSchliessen={onSchliessen} />);
        expect(screen.getByRole('heading', { name: 'Antwort' })).toBeInTheDocument();
        expect(screen.getByText('Projekt: Treppe Mustermann')).toBeInTheDocument();
        const x = screen.getByRole('button', { name: 'Fenster schließen' });
        expect(x).toHaveAttribute('title', 'Schließen');
        fireEvent.click(x);
        expect(onSchliessen).toHaveBeenCalledTimes(1);
    });

    it('sperrt das X, solange gespeichert oder gesendet wird', () => {
        render(<EmailComposeKopf titel="Neue E-Mail" untertitel="" onSchliessen={() => {}} schliessenGesperrt />);
        expect(screen.getByRole('button', { name: 'Fenster schließen' })).toBeDisabled();
    });
});
