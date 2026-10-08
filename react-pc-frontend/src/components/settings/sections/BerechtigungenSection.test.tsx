import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { BerechtigungenSection } from './BerechtigungenSection';
const { toast } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('../../ui/toast', () => ({ useToast: () => toast }));
const fetchMock = vi.fn();
beforeEach(() => { vi.clearAllMocks(); vi.stubGlobal('fetch', fetchMock); });
const abteilung = { abteilungId: 1, abteilungName: 'Testabteilung', berechtigungen: [], darfMonatAbschliessen: false };
it('speichert das explizite Abschlussrecht mit den bisherigen Dokumentrechten', async () => {
    fetchMock.mockResolvedValue({ ok: true, json: async () => [abteilung] });
    render(<BerechtigungenSection />);
    const checkbox = await screen.findByRole('checkbox', { name: /Monate abschließen und wieder öffnen/ });
    expect(checkbox).not.toBeChecked(); fireEvent.click(checkbox);
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));
    await waitFor(() => expect(toast.success).toHaveBeenCalled());
    expect(JSON.parse(fetchMock.mock.calls.find(c => c[1]?.method === 'PUT')![1].body)).toMatchObject({ darfMonatAbschliessen: true, berechtigungen: [] });
});
it('zeigt 403 beim Speichern und meldet keinen Erfolg', async () => {
    fetchMock.mockImplementation(async (_url, init) => init?.method === 'PUT' ? { ok: false, status: 403 } : { ok: true, json: async () => [abteilung] });
    render(<BerechtigungenSection />);
    fireEvent.click(await screen.findByRole('button', { name: 'Speichern' }));
    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Nur Administratoren dürfen Berechtigungen ändern.'));
    expect(toast.success).not.toHaveBeenCalled();
});
it('unterscheidet Ladefehler von einer leeren Abteilungsliste', async () => {
    fetchMock.mockResolvedValue({ ok: false }); render(<BerechtigungenSection />);
    expect(await screen.findByRole('alert')).toBeVisible();
    expect(screen.queryByText(/Noch keine Abteilungen angelegt/)).not.toBeInTheDocument();
});
it('speichert das Recht "Anrufe & Anrufbeantworter" wie das Abschlussrecht', async () => {
    fetchMock.mockResolvedValue({ ok: true, json: async () => [{ ...abteilung, darfTelefonSehen: undefined }] });
    render(<BerechtigungenSection />);
    const checkbox = await screen.findByRole('checkbox', { name: /Anrufe & Anrufbeantworter/ });
    expect(checkbox).not.toBeChecked();
    expect(screen.getByText(/hört Nachrichten auf dem Anrufbeantworter ab und bekommt bei Anrufen das Anruf-Fenster/)).toBeInTheDocument();
    fireEvent.click(checkbox);
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));
    await waitFor(() => expect(toast.success).toHaveBeenCalled());
    expect(JSON.parse(fetchMock.mock.calls.find(c => c[1]?.method === 'PUT')![1].body)).toMatchObject({ darfTelefonSehen: true, darfMonatAbschliessen: false });
});
it('zeigt Dokumenttypen in Klartext und koppelt Scannen an Sehen', async () => {
    fetchMock.mockResolvedValue({ ok: true, json: async () => [{ ...abteilung, berechtigungen: [{ typ: 'GUTSCHRIFT', darfSehen: false, darfScannen: false }] }] });
    render(<BerechtigungenSection />);
    const scannen = await screen.findByRole('checkbox', { name: 'Gutschrift scannen' });
    expect(screen.queryByText('GUTSCHRIFT')).not.toBeInTheDocument();
    fireEvent.click(scannen);
    expect(screen.getByRole('checkbox', { name: 'Gutschrift sehen' })).toBeChecked();
});
it('lädt das Häkchen für Webseiten-Anfragen und speichert es mit', async () => {
    fetchMock.mockResolvedValue({ ok: true, json: async () => [{ ...abteilung, darfWebseitenAnfragenPushen: true }] });
    render(<BerechtigungenSection />);
    const push = await screen.findByRole('checkbox', { name: /Neue Anfrage über Webseite/ });
    expect(push).toBeChecked();
    fireEvent.click(push);
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));
    await waitFor(() => expect(toast.success).toHaveBeenCalled());
    expect(JSON.parse(fetchMock.mock.calls.find(c => c[1]?.method === 'PUT')![1].body)).toMatchObject({ darfWebseitenAnfragenPushen: false });
});
it('zeigt den Typ BELEG als Klartext', async () => {
    fetchMock.mockResolvedValue({ ok: true, json: async () => [{ ...abteilung, berechtigungen: [{ typ: 'BELEG', darfSehen: true, darfScannen: false }] }] });
    render(<BerechtigungenSection />);
    expect(await screen.findByRole('checkbox', { name: 'Belege (Kasse, Bank) sehen' })).toBeChecked();
    expect(screen.queryByText('BELEG')).not.toBeInTheDocument();
});
