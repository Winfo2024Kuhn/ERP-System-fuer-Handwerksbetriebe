import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';
import MitarbeiterEditor from './MitarbeiterEditor';

vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ isAdmin: true, user: null }) }));
vi.mock('../features/zeiterfassung/useZeiterfassungSichtbarkeit', () => ({ useZeiterfassungSichtbarkeit: () => false }));

const MITARBEITER = {
    id: 42, vorname: 'Max', nachname: 'Mustermann', strasse: null, plz: null, ort: null,
    email: 'max.mustermann@example.invalid', telefon: null, festnetz: null, qualifikation: null,
    stundenlohn: null, geburtstag: null, eintrittsdatum: '2026-01-01', aktiv: true,
    fuehrtZeitkonto: false, abteilungIds: [], abteilungNames: null, loginToken: 'alter-code', jahresUrlaub: 30,
};

describe('MitarbeiterEditor – QR-Code neu erstellen', () => {
    const requests: { url: string; init?: RequestInit }[] = [];

    beforeEach(() => {
        requests.length = 0;
        global.fetch = vi.fn((url: string, init?: RequestInit) => {
            requests.push({ url, init });
            if (url === '/api/mitarbeiter') return Promise.resolve({ ok: true, json: () => Promise.resolve([MITARBEITER]) });
            if (url.endsWith('/regenerate-token')) return Promise.resolve({ ok: true, text: () => Promise.resolve('neuer-code') });
            return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
        }) as unknown as typeof fetch;
    });

    afterEach(() => { vi.restoreAllMocks(); });

    const regenerierungen = () => requests.filter(r => r.url === '/api/mitarbeiter/42/regenerate-token' && r.init?.method === 'POST');

    it('fragt vor dem Ersetzen nach und erstellt den Code erst nach Bestätigung', async () => {
        const user = userEvent.setup();
        render(<MemoryRouter><ConfirmProvider><ToastProvider><MitarbeiterEditor /></ToastProvider></ConfirmProvider></MemoryRouter>);
        await user.click(await screen.findByText('Mustermann', { exact: true }));
        await user.click(await screen.findByRole('button', { name: /QR-Code/ }));
        await user.click(await screen.findByRole('button', { name: 'Neu generieren' }));

        const rueckfrage = await screen.findByRole('dialog', { name: 'Neuen QR-Code erstellen?' });
        expect(within(rueckfrage).getByText('Neuen QR-Code erstellen?')).toBeVisible();
        expect(within(rueckfrage).getByText('Der bisherige QR-Code funktioniert danach nicht mehr. Max muss die Handy-App mit dem neuen Code neu einrichten.')).toBeVisible();
        await user.click(within(rueckfrage).getByRole('button', { name: 'Abbrechen' }));
        expect(regenerierungen()).toHaveLength(0);

        await user.click(screen.getByRole('button', { name: 'Neu generieren' }));
        await user.click(await screen.findByRole('button', { name: 'Neuen Code erstellen' }));
        await waitFor(() => expect(regenerierungen()).toHaveLength(1));
        expect(await screen.findByText('neuer-code')).toBeVisible();
    });

    it('zeigt den Code zum Abtippen und kopiert ihn, falls das Scannen nicht klappt', async () => {
        const user = userEvent.setup();
        const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue();
        render(<MemoryRouter><ConfirmProvider><ToastProvider><MitarbeiterEditor /></ToastProvider></ConfirmProvider></MemoryRouter>);
        await user.click(await screen.findByText('Mustermann', { exact: true }));
        await user.click(await screen.findByRole('button', { name: /QR-Code/ }));

        expect(await screen.findByText('alter-code')).toBeVisible();
        await user.click(screen.getByRole('button', { name: 'Code kopieren' }));
        expect(writeText).toHaveBeenCalledWith('alter-code');
    });

    it('meldet, wenn der Browser das Kopieren verweigert', async () => {
        const user = userEvent.setup();
        vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValue(new Error('verweigert'));
        render(<MemoryRouter><ConfirmProvider><ToastProvider><MitarbeiterEditor /></ToastProvider></ConfirmProvider></MemoryRouter>);
        await user.click(await screen.findByText('Mustermann', { exact: true }));
        await user.click(await screen.findByRole('button', { name: /QR-Code/ }));
        await user.click(await screen.findByRole('button', { name: 'Code kopieren' }));

        expect(await screen.findByText('Code konnte nicht kopiert werden. Bitte von Hand markieren und kopieren.')).toBeVisible();
    });
});
