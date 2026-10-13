import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../auth/AuthContext';
import { RequireAuth } from '../../auth/RouteGuards';
import { MainLayout } from './MainLayout';
import { ToastProvider } from '../ui/toast';
import { antwort, stubbeFetch } from '../../features/telefon/telefonTestdaten';
import { benutzer } from '../../features/ki/kiTestdaten';

// Prüfgegenstand ist nur der KI-Chat – Menüleiste, Handy-Leiste und
// Anruf-Fenster haben eigene Tests und brauchen hier kein Netz.
vi.mock('./RibbonNav', () => ({ RibbonNavigation: () => <nav>Menüleiste</nav> }));
vi.mock('./MobileBottomNav', () => ({ MobileBottomNav: () => null }));
vi.mock('../../features/telefon/AnrufFensterHost', () => ({ AnrufFensterHost: () => null }));
vi.mock('../../hooks/useBerechtigungenJeBenutzer', () => ({ useBerechtigungenJeBenutzer: () => undefined }));

function zeige(kiAktiv: boolean) {
    stubbeFetch((url) => (url.pathname === '/api/auth/me' ? antwort(benutzer({ kiAktiv })) : undefined));
    return render(
        <ToastProvider>
            <MemoryRouter initialEntries={['/projekte']}>
                <AuthProvider>
                    <Routes>
                        <Route element={<RequireAuth><MainLayout /></RequireAuth>}>
                            <Route path="/projekte" element={<p>Projektliste</p>} />
                        </Route>
                    </Routes>
                </AuthProvider>
            </MemoryRouter>
        </ToastProvider>,
    );
}

describe('MainLayout – KI-Hilfe-Chat', () => {
    beforeEach(() => {
        // Der Chat merkt sich „offen“ je Benutzer im localStorage und geht beim Laden wieder auf.
        localStorage.setItem('frontendUserSelection', JSON.stringify({ id: 1 }));
        localStorage.setItem('ki-hilfe-open-1', 'true');
        localStorage.setItem('ki-hilfe-open', 'true');
        Element.prototype.scrollIntoView = vi.fn();
    });
    afterEach(() => {
        vi.unstubAllGlobals();
        localStorage.clear();
    });

    it('geht ohne KI-Schlüssel nicht von selbst auf, obwohl er als offen gemerkt ist', async () => {
        zeige(false);

        expect(await screen.findByText('Projektliste')).toBeInTheDocument();
        expect(screen.queryByRole('heading', { name: 'KI-Hilfe' })).not.toBeInTheDocument();
    });

    it('stellt mit KI-Schlüssel den gemerkten Offen-Zustand wieder her', async () => {
        zeige(true);

        expect(await screen.findByText('Projektliste')).toBeInTheDocument();
        expect(await screen.findByRole('heading', { name: 'KI-Hilfe' })).toBeInTheDocument();
    });
});
