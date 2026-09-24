import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from './ui/toast';
import { ConfirmProvider } from './ui/confirm-dialog';
import { HicadImportModal } from './HicadImportModal';
import type { PreviewResponse } from '../features/einkauf/originalHicadApi';

const api = vi.hoisted(() => ({ ladeHicadVorschau: vi.fn(), uebernehmeHicad: vi.fn(), optimiereZuschnitt: vi.fn() }));
vi.mock('../features/einkauf/originalHicadApi', async importOriginal => ({
    ...await importOriginal<typeof import('../features/einkauf/originalHicadApi')>(), ...api,
}));

const vorschau = (werte: Partial<PreviewResponse> = {}): PreviewResponse => ({
    auftragsnummer: 'A-0001', auftragstext: 'Dummy-Treppe', kunde: 'Max Mustermann', importId: 12, importVersion: 1,
    gruppen: [{
        groupKey: 'heb 220||s235jr', bezeichnung: 'HEB 220', werkstoff: 'S235JR', artikelId: 6, artikelProduktname: 'HEB 220',
        verpackungseinheitM: 12, defaultAggregieren: false, summeMeter: 5.08, summeStueck: 1, berechneteStaebe: 1,
        zeilen: [{ posNr: '1200', anzahl: 1, bezeichnung: 'HEB 220', laengeMm: 5076.5, werkstoff: 'S235JR', anschnittFlansch: '45° 45°',
            anschnittbildFlanschUrl: '/api/einkauf/hicad/12/bilder/88' }],
    }],
    ...werte,
});

const oeffne = async (onSuccess = vi.fn(), onClose = vi.fn()) => {
    render(<ToastProvider><ConfirmProvider>
        <HicadImportModal isOpen onClose={onClose} onSuccess={onSuccess} projekt={{ id: 7, bauvorhaben: 'Neubau Max Mustermann', auftragsnummer: 'A-0001' }} />
    </ConfirmProvider></ToastProvider>);
    fireEvent.change(document.getElementById('hicad-file-input')!, { target: { files: [new File(['x'], 'saegeliste.xlsx')] } });
    fireEvent.click(screen.getByRole('button', { name: /Analysieren/ }));
};

beforeEach(() => { vi.clearAllMocks(); });

describe('HiCAD-Import prüfen (EN1090-Fenster)', () => {
    it('zeigt die Profilgruppen und legt die Positionen im aktuellen Projekt an', async () => {
        api.ladeHicadVorschau.mockResolvedValue(vorschau());
        api.uebernehmeHicad.mockResolvedValue({ angelegtePositionen: 1, erledigteGruppen: ['heb 220||s235jr'] });
        const onSuccess = vi.fn();
        await oeffne(onSuccess);

        expect(await screen.findByRole('heading', { name: 'HEB 220' })).toBeInTheDocument();
        expect(api.ladeHicadVorschau).toHaveBeenCalledWith(expect.any(File), 7);
        expect(screen.getByText('Dummy-Treppe')).toBeInTheDocument();
        expect(screen.getByText('5.076,5 mm')).toBeInTheDocument();
        expect(screen.getByAltText('Anschnitt Flansch')).toHaveAttribute('src', '/api/einkauf/hicad/12/bilder/88');

        fireEvent.click(screen.getByRole('button', { name: /1 Position anlegen/ }));
        await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1));
        expect(api.uebernehmeHicad).toHaveBeenCalledWith(expect.objectContaining({ projektId: 7, duplikatBewusst: false,
            entscheidungen: { 'heb 220||s235jr': expect.objectContaining({ artikelId: 6, aggregieren: false }) } }));
        expect(await screen.findByText('1 Position angelegt')).toBeInTheDocument();
    });

    it('zeigt die Servermeldung als Fehler-Toast, wenn die Datei nicht gelesen werden kann', async () => {
        api.ladeHicadVorschau.mockRejectedValue(new Error('Makros und externe Verknüpfungen sind nicht erlaubt.'));
        await oeffne();
        expect(await screen.findByText('Makros und externe Verknüpfungen sind nicht erlaubt.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Analysieren/ })).toBeEnabled();
    });

    it('lehnt eine ungültige Stangenlänge vor dem Anlegen ab', async () => {
        api.ladeHicadVorschau.mockResolvedValue(vorschau());
        await oeffne();
        fireEvent.click(await screen.findByRole('button', { name: /Stangenware/ }));
        fireEvent.change(screen.getByLabelText('Stangenlänge für HEB 220 in Metern'), { target: { value: '0' } });
        fireEvent.click(screen.getByRole('button', { name: /1 Position anlegen/ }));
        expect(await screen.findByText(/HEB 220: die Stangenlänge muss mindestens 1 sein/)).toBeInTheDocument();
        expect(api.uebernehmeHicad).not.toHaveBeenCalled();
    });

    it('fragt bei einer schon importierten Datei nach und legt beim Abbrechen nichts an', async () => {
        api.ladeHicadVorschau.mockResolvedValue(vorschau({ dateiSchonImportiert: true }));
        await oeffne();
        fireEvent.click(await screen.findByRole('button', { name: /1 Position anlegen/ }));
        fireEvent.click(await screen.findByRole('button', { name: 'Abbrechen' }));
        await waitFor(() => expect(screen.queryByText('Sägeliste schon importiert')).not.toBeInTheDocument());
        expect(api.uebernehmeHicad).not.toHaveBeenCalled();
    });
});
