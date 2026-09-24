import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import AbteilungBerechtigungenEditor from './AbteilungBerechtigungenEditor';
const { toast } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('../components/ui/toast', () => ({ useToast: () => toast }));
const fetchMock = vi.fn();
beforeEach(() => { vi.clearAllMocks(); vi.stubGlobal('fetch', fetchMock); });
const abteilung = { abteilungId: 1, abteilungName: 'Testabteilung', berechtigungen: [], darfMonatAbschliessen: false };
it('speichert das explizite Abschlussrecht mit den bisherigen Dokumentrechten', async () => {
    fetchMock.mockResolvedValue({ ok: true, json: async () => [abteilung] });
    render(<AbteilungBerechtigungenEditor />);
    const checkbox = await screen.findByRole('checkbox', { name: /Monate abschließen und wieder öffnen/ });
    expect(checkbox).not.toBeChecked(); fireEvent.click(checkbox);
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));
    await waitFor(() => expect(toast.success).toHaveBeenCalled());
    expect(JSON.parse(fetchMock.mock.calls.find(c => c[1]?.method === 'PUT')![1].body)).toMatchObject({ darfMonatAbschliessen: true, berechtigungen: [] });
});
it('zeigt 403 beim Speichern und meldet keinen Erfolg', async () => {
    fetchMock.mockImplementation(async (_url, init) => init?.method === 'PUT' ? { ok: false, status: 403 } : { ok: true, json: async () => [abteilung] });
    render(<AbteilungBerechtigungenEditor />);
    fireEvent.click(await screen.findByRole('button', { name: 'Speichern' }));
    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Nur Administratoren dürfen Berechtigungen ändern.'));
    expect(toast.success).not.toHaveBeenCalled();
});
it('unterscheidet Ladefehler von einer leeren Abteilungsliste', async () => {
    fetchMock.mockResolvedValue({ ok: false }); render(<AbteilungBerechtigungenEditor />);
    expect(await screen.findByRole('alert')).toBeVisible();
    expect(screen.queryByText('Keine Abteilungen vorhanden.')).not.toBeInTheDocument();
});
