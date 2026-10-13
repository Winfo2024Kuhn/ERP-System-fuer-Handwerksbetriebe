import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TerminKalender from './TerminKalender';
const { toast } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('../components/ui/toast', () => ({ useToast: () => toast }));
vi.mock('../components/ui/confirm-dialog', () => ({ useConfirm: () => vi.fn().mockResolvedValue(true) }));
const fetchMock = vi.fn();
beforeEach(() => { vi.clearAllMocks(); fetchMock.mockResolvedValue({ ok: true, json: async () => [] }); vi.stubGlobal('fetch', fetchMock); });
describe('Terminuhrzeiten im eigenen Eingabefeld', () => {
    it('bewahrt ungültige Entwürfe und sendet erst vollständige Uhrzeiten', async () => {
        render(<MemoryRouter><TerminKalender /></MemoryRouter>);
        fireEvent.click(await screen.findByRole('button', { name: 'Neuer Termin' }));
        fireEvent.change(screen.getByPlaceholderText('z.B. Montage, Besprechung...'), { target: { value: 'Testtermin' } });
        const von = screen.getByRole('textbox', { name: 'Von' });
        const bis = screen.getByRole('textbox', { name: 'Bis' });
        fireEvent.change(von, { target: { value: '25:99' } });
        fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(von).toHaveValue('25:99');
        expect(fetchMock.mock.calls.filter(call => call[1]?.method === 'POST')).toHaveLength(0);
        expect(toast.error).toHaveBeenCalled();
        fireEvent.change(von, { target: { value: '09:05' } });
        fireEvent.change(bis, { target: { value: '' } });
        fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(fetchMock.mock.calls.filter(call => call[1]?.method === 'POST')).toHaveLength(0);
        fireEvent.change(bis, { target: { value: '10:15' } });
        fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(fetchMock.mock.calls.filter(call => call[1]?.method === 'POST')).toHaveLength(1));
        const request = fetchMock.mock.calls.find(call => call[1]?.method === 'POST');
        expect(JSON.parse(request![1].body)).toMatchObject({ startZeit: '09:05:00', endeZeit: '10:15:00' });
    });
});

describe('Team-Abwesenheiten ohne Einblick (#268)', () => {
    it('zeigt „ABWESEND" als neutrales „Abwesend" statt auf Urlaub zurückzufallen', async () => {
        const heute = new Date();
        const datum = `${heute.getFullYear()}-${String(heute.getMonth() + 1).padStart(2, '0')}-15`;
        fetchMock.mockImplementation(async (input: string) => {
            if (input.startsWith('/api/abwesenheit/team')) {
                return { ok: true, json: async () => [{ id: 7, datum, typ: 'ABWESEND', stunden: 8, mitarbeiterId: 3, mitarbeiterName: 'Hans Beispiel' }] };
            }
            return { ok: true, json: async () => [] };
        });
        render(<MemoryRouter><TerminKalender /></MemoryRouter>);

        const eintrag = await screen.findByTitle('Hans Beispiel');
        // Neutrale slate-Farbe wie Krankheit, nicht das Urlaubs-Blau.
        expect(eintrag.className).toContain('text-slate-600');
        expect(eintrag.className).not.toContain('text-sky-700');

        // Die Legende erklärt den neuen Eintrag.
        expect(screen.getByText('Legende:').parentElement).toHaveTextContent('Abwesend');
    });
});
