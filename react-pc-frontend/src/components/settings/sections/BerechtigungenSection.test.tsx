import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { BerechtigungenSection } from './BerechtigungenSection';
const { toast } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('../../ui/toast', () => ({ useToast: () => toast }));
// Die Postfach-Karte lädt eigenständig und hat eigene Tests (berechtigungen/PostfachSichtbarkeitKarte.test.tsx).
vi.mock('./berechtigungen/PostfachSichtbarkeitKarte', () => ({ PostfachSichtbarkeitKarte: () => <div data-testid="postfach-karte" /> }));
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
it('zeigt die Postfach-Karte ganz oben, auch während die Abteilungen noch laden', async () => {
    fetchMock.mockReturnValue(new Promise(() => {}));
    render(<BerechtigungenSection />);
    expect(screen.getByTestId('postfach-karte')).toBeInTheDocument();
    expect(screen.getByText(/Lade Einstellungen/)).toBeInTheDocument();
});
it('lädt den Haken "Zeiterfassung im Menü sehen" und speichert ihn über den bestehenden PUT', async () => {
    fetchMock.mockResolvedValue({ ok: true, json: async () => [{ ...abteilung, darfZeiterfassungSehen: true }] });
    render(<BerechtigungenSection />);
    const haken = await screen.findByRole('checkbox', { name: /Zeiterfassung im Menü sehen/ });
    expect(haken).toBeChecked();
    fireEvent.click(haken);
    expect(haken).not.toBeChecked();
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));
    await waitFor(() => expect(toast.success).toHaveBeenCalled());
    const put = fetchMock.mock.calls.find(c => c[1]?.method === 'PUT')!;
    expect(put[0]).toBe('/api/abteilungen/1/berechtigungen');
    expect(JSON.parse(put[1].body)).toMatchObject({ darfZeiterfassungSehen: false, darfMonatAbschliessen: false });
});
it('setzt den Haken "Zeiterfassung im Menü sehen" und schickt ihn mit', async () => {
    fetchMock.mockResolvedValue({ ok: true, json: async () => [{ ...abteilung, darfZeiterfassungSehen: false }] });
    render(<BerechtigungenSection />);
    const haken = await screen.findByRole('checkbox', { name: /Zeiterfassung im Menü sehen/ });
    expect(haken).not.toBeChecked();
    fireEvent.click(haken);
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));
    await waitFor(() => expect(toast.success).toHaveBeenCalled());
    expect(JSON.parse(fetchMock.mock.calls.find(c => c[1]?.method === 'PUT')![1].body)).toMatchObject({ darfZeiterfassungSehen: true });
});
it('erklärt "Zeiterfassung im Menü sehen" im Info-Popover, ohne den Haken zu verändern', async () => {
    fetchMock.mockResolvedValue({ ok: true, json: async () => [{ ...abteilung, darfZeiterfassungSehen: true }] });
    render(<BerechtigungenSection />);
    const haken = await screen.findByRole('checkbox', { name: /Zeiterfassung im Menü sehen/ });
    fireEvent.click(screen.getByRole('button', { name: 'Mehr zu: Zeiterfassung im Menü sehen' }));
    const info = await screen.findByRole('dialog', { name: 'Zeiterfassung im Menü sehen' });
    expect(within(info).getByText('Was passiert im Hintergrund?')).toBeInTheDocument();
    expect(within(info).getByText('Was bewirkt das?')).toBeInTheDocument();
    expect(within(info).getByText(/Administratoren und die Geschäftsführung sehen ihn immer\. Gehört ein Mitarbeiter zu mehreren Abteilungen, reicht ein Haken/)).toBeInTheDocument();
    expect(within(info).getByText(/Lange Krankheit und den übrigen Seiten/)).toBeInTheDocument();
    expect(within(info).getByText(/Urlaubsanträge anderer Mitarbeiter an diesen Zugang auch nicht mehr heraus/)).toBeInTheDocument();
    expect(within(info).getByText(/kranke Kollegen nur als „Abwesend“/)).toBeInTheDocument();
    expect(within(info).getByText(/Das Stempeln und Urlaub-Beantragen in der Handy-App bleibt davon unberührt\. Ein PC-Zugang ohne verknüpften Mitarbeiter sieht den Bereich nur als Administrator/)).toBeInTheDocument();
    expect(haken).toBeChecked();
});
it('lädt das Recht "Aufschläge ändern", erklärt es im Info-Popover und speichert es mit', async () => {
    fetchMock.mockResolvedValue({ ok: true, json: async () => [{ ...abteilung, darfAufschlaegeAendern: undefined }] });
    render(<BerechtigungenSection />);
    const checkbox = await screen.findByRole('checkbox', { name: /Aufschläge ändern/ });
    expect(checkbox).not.toBeChecked();
    expect(screen.getByText(/den allgemeinen Aufschlag und den Aufschlag je Kategorie einstellen/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Mehr zu: Aufschläge ändern/ }));
    expect(await screen.findByText(/Der Aufschlag direkt am Artikel kann weiterhin jeder Benutzer/)).toBeVisible();
    expect(checkbox).not.toBeChecked();

    fireEvent.click(checkbox);
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));
    await waitFor(() => expect(toast.success).toHaveBeenCalled());
    expect(JSON.parse(fetchMock.mock.calls.find(c => c[1]?.method === 'PUT')![1].body)).toMatchObject({ darfAufschlaegeAendern: true, darfMonatAbschliessen: false });
});
it('ein schon gesetztes Recht "Aufschläge ändern" bleibt beim Speichern erhalten', async () => {
    fetchMock.mockResolvedValue({ ok: true, json: async () => [{ ...abteilung, darfAufschlaegeAendern: true }] });
    render(<BerechtigungenSection />);
    expect(await screen.findByRole('checkbox', { name: /Aufschläge ändern/ })).toBeChecked();
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));
    await waitFor(() => expect(toast.success).toHaveBeenCalled());
    expect(JSON.parse(fetchMock.mock.calls.find(c => c[1]?.method === 'PUT')![1].body)).toMatchObject({ darfAufschlaegeAendern: true });
});
