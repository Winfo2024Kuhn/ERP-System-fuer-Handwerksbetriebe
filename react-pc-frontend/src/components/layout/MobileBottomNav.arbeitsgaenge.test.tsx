import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MobileBottomNav } from './MobileBottomNav';

/** Arbeitsgänge pflegen nur noch Admins – auch die Handy-Leiste der PC-App blendet sie sonst aus. */

const { auth } = vi.hoisted(() => ({ auth: { isAdmin: false } }));
vi.mock('../../auth/AuthContext', () => ({ useAuth: () => auth }));
vi.mock('../../features/telefon/useTelefonBerechtigung', () => ({ useTelefonBerechtigung: () => false }));
vi.mock('../../features/zeiterfassung/useZeiterfassungSichtbarkeit', () => ({ useZeiterfassungSichtbarkeit: () => false }));

function oeffneMehr() {
    render(<MemoryRouter initialEntries={['/projekte']}><MobileBottomNav /></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: /Mehr/ }));
}

describe('Handy-Leiste – Arbeitsgänge nur für Admins', () => {
    beforeEach(() => { auth.isAdmin = false; });

    it('zeigt Arbeitsgänge für Admins unter „Mehr“', () => {
        auth.isAdmin = true;
        oeffneMehr();
        expect(screen.getByRole('link', { name: /Arbeitsgänge/ })).toHaveAttribute('href', '/arbeitsgaenge');
    });

    it('blendet Arbeitsgänge für andere Benutzer aus, die übrigen Stammdaten bleiben', () => {
        oeffneMehr();
        expect(screen.getByRole('link', { name: /Leistungen & Kategorien/ })).toHaveAttribute('href', '/leistungen');
        expect(screen.queryByRole('link', { name: /Arbeitsgänge/ })).toBeNull();
    });

    it('zeigt Leistungen & Kategorien vor den Textvorlagen und Arbeitsgänge bei den Einstellungen', () => {
        auth.isAdmin = true;
        oeffneMehr();
        const namen = screen.getAllByRole('link').map((link) => link.textContent ?? '');
        const stelle = (name: string) => {
            const index = namen.findIndex((text) => text.includes(name));
            expect(index, `${name} fehlt im Menü`).toBeGreaterThan(-1);
            return index;
        };
        expect(stelle('Leistungen & Kategorien')).toBe(stelle('Textvorlagen') - 1);
        expect(stelle('Arbeitsgänge')).toBe(stelle('Formularwesen') + 1);
    });
});
