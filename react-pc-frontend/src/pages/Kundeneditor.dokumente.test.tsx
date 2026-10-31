import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { Kundeneditor } from './Kundeneditor';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';

vi.mock('../auth/AuthContext', () => ({ useOptionalAuth: () => ({ isAdmin: true }) }));

// DSGVO: ausschließlich Dummy-Daten.
const AKTE = {
    id: 7,
    kundennummer: '1007',
    name: 'Max Mustermann',
    strasse: 'Musterweg 1',
    plz: '12345',
    ort: 'Musterstadt',
    zahlungsziel: 8,
    kundenEmails: [],
    projekte: [],
    anfragen: [],
    // Hängt nur am Kunden – ohne Projekt und ohne Anfrage.
    geschaeftsdokumente: [{ id: 88, dokumentNummer: 'AN-2026-001', typ: 'ANGEBOT', datum: '2026-10-01' }],
    kommunikation: [],
    notizen: [],
    gesperrt: false,
};

beforeEach(() => {
    global.fetch = vi.fn((url: string) => Promise.resolve({
        ok: url === '/api/kunden/7',
        status: url === '/api/kunden/7' ? 200 : 404,
        json: () => Promise.resolve(url === '/api/kunden/7' ? AKTE : {}),
    })) as unknown as typeof fetch;
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe('Kundeneditor – Dokumente ohne Projekt', () => {
    it('zeigt das Dokument mit Herkunft „Ohne Projekt“ und öffnet es im Dokument-Editor', async () => {
        const user = userEvent.setup();
        const oeffnen = vi.spyOn(window, 'open').mockImplementation(() => null);
        render(
            <ToastProvider>
                <ConfirmProvider>
                    <MemoryRouter initialEntries={['/kunden?kundeId=7']}>
                        <Kundeneditor />
                    </MemoryRouter>
                </ConfirmProvider>
            </ToastProvider>
        );

        await user.click(await screen.findByRole('button', { name: /Dokumente/ }));
        expect(screen.getByText('Ohne Projekt')).toBeInTheDocument();

        await user.click(screen.getByText('AN-2026-001'));
        expect(oeffnen).toHaveBeenCalledWith('/dokument-editor?dokumentId=88&dokumentTyp=ANGEBOT', '_blank');
    });
});
