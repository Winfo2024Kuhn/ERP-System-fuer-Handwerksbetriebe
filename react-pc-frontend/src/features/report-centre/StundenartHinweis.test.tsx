import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { arbeitsstundenInfo, arbeitsstundenTitel, stundenartName } from './stundenart';
import { StundenartHinweis } from './StundenartHinweis';

const auth = vi.hoisted(() => ({ wert: null as { isAdmin: boolean } | null }));
vi.mock('../../auth/AuthContext', () => ({ useOptionalAuth: () => auth.wert }));

describe('StundenartHinweis', () => {
    it('verlinkt für Admins die Einstellung', () => {
        auth.wert = { isAdmin: true };
        render(<MemoryRouter><StundenartHinweis art="IST" /></MemoryRouter>);
        expect(screen.getByText(/Übermittelt werden/)).toHaveTextContent('Übermittelt werden: Iststunden');
        expect(screen.getByRole('link', { name: 'Einstellungen → Zeiterfassung' })).toHaveAttribute('href', '/einstellungen#zeiterfassung');
    });

    it('sagt Nicht-Admins, wer es ändern kann – ohne Link und ohne Umschalter', () => {
        auth.wert = { isAdmin: false };
        render(<StundenartHinweis art="SOLL" />);
        expect(screen.getByText(/Übermittelt werden/)).toHaveTextContent('Sollstunden');
        expect(screen.getByText('Ein Admin kann das unter Einstellungen → Zeiterfassung ändern.')).toBeInTheDocument();
        expect(screen.queryByRole('link')).not.toBeInTheDocument();
        expect(screen.queryByRole('button')).not.toBeInTheDocument();
    });

    it('gilt ohne Anmeldung als Nicht-Admin', () => {
        auth.wert = null;
        render(<StundenartHinweis art="SOLL" />);
        expect(screen.queryByRole('link')).not.toBeInTheDocument();
    });
});

describe('stundenart', () => {
    it('benennt Soll und Ist', () => {
        expect(stundenartName('SOLL')).toBe('Sollstunden');
        expect(stundenartName('IST')).toBe('Iststunden');
        expect(arbeitsstundenTitel('SOLL')).toBe('Arbeitsstunden (Soll)');
        expect(arbeitsstundenTitel('IST')).toBe('Arbeitsstunden (Ist)');
        expect(arbeitsstundenInfo('SOLL').hintergrund).toContain('zieht Urlaub, Feiertage, Krankheit und Fortbildung ab');
        expect(arbeitsstundenInfo('IST').hintergrund).toContain('wirklich gebuchte Arbeitszeit');
    });
});
