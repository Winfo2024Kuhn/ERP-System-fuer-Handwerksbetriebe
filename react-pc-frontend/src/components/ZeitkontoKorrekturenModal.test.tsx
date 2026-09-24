import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import { ZeitkontoKorrekturenModal } from './ZeitkontoKorrekturenModal';
const toastError = vi.fn();
vi.mock('./ui/toast', () => ({ useToast: () => ({ error: toastError }) }));
const fetchMock = vi.fn();
beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockReset().mockImplementation(async (_url, init) => ({ ok: true, json: async () => init ? {} : [{ id: 1, mitarbeiterId: 1, datum: '2026-09-01', erstelltAm: '2026-09-01', stunden: 8, grund: 'Testkorrektur', typ: 'STUNDEN' }] }));
});
it('verlangt eine Begründung im eigenen Stornierungsdialog und sendet erst nach Bestätigung', async () => {
    const user = userEvent.setup();
    render(<ZeitkontoKorrekturenModal mitarbeiterId={1} mitarbeiterName="Max Mustermann" onClose={vi.fn()} onUpdate={vi.fn()} />);
    await user.click(await screen.findByTitle('Stornieren'));
    expect(screen.getByRole('dialog', { name: 'Korrektur stornieren' })).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Neue Korrektur' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Stornierung bestätigen' }));
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false);
    await user.type(screen.getByRole('textbox', { name: 'Stornierungsgrund' }), 'Doppelt erfasst');
    await user.click(screen.getByRole('button', { name: 'Stornierung bestätigen' }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/zeitkonto/korrekturen/1', expect.objectContaining({ method: 'DELETE', body: JSON.stringify({ bearbeiterId: 1, stornierungsgrund: 'Doppelt erfasst' }) })));
});
it('übernimmt nur vollständige Kommawerte als numerische Stunden', async () => {
    const user = userEvent.setup();
    render(<ZeitkontoKorrekturenModal mitarbeiterId={1} mitarbeiterName="Max Mustermann" onClose={vi.fn()} onUpdate={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Neue Korrektur' }));
    const hours = screen.getByRole('textbox', { name: 'Anzahl Stunden' });
    await user.type(hours, '8,');
    await user.type(screen.getByRole('textbox', { name: 'Begründung' }), 'Nachtrag');
    await user.click(screen.getByRole('button', { name: 'Speichern' }));
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);
    expect(hours).toHaveValue('8,');
    await user.type(hours, '5');
    await user.click(screen.getByRole('button', { name: 'Speichern' }));
    await waitFor(() => expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST' && JSON.parse(init.body).stunden === 8.5)).toBe(true));
});
