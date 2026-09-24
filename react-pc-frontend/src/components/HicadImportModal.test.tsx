import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
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
    render(<MemoryRouter><ToastProvider><ConfirmProvider>
        <HicadImportModal isOpen onClose={onClose} onSuccess={onSuccess} projekt={{ id: 7, bauvorhaben: 'Neubau Max Mustermann', auftragsnummer: 'A-0001' }} />
    </ConfirmProvider></ToastProvider></MemoryRouter>);
    fireEvent.change(document.getElementById('hicad-file-input')!, { target: { files: [new File(['x'], 'saegeliste.xlsx')] } });
    fireEvent.click(screen.getByRole('button', { name: /Analysieren/ }));
};

beforeEach(() => { vi.clearAllMocks(); vi.unstubAllGlobals(); });

const ohneArtikel = () => vorschau({ gruppen: [{ ...vorschau().gruppen[0], artikelId: null, artikelProduktname: null }] });

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

    it('legt eine Gruppe ohne Artikel und ohne Lieferant als Freitext an', async () => {
        api.ladeHicadVorschau.mockResolvedValue(ohneArtikel());
        api.uebernehmeHicad.mockResolvedValue({ angelegtePositionen: 1, erledigteGruppen: ['heb 220||s235jr'] });
        const onSuccess = vi.fn();
        await oeffne(onSuccess);

        expect(await screen.findByText('als Freitext')).toBeInTheDocument();
        expect(screen.getByText('1 als Freitext')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Lieferant \(optional\)/ })).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: /1 Position anlegen/ }));
        await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1));
        expect(api.uebernehmeHicad).toHaveBeenCalledWith(expect.objectContaining({ entscheidungen: {
            'heb 220||s235jr': expect.objectContaining({ artikelId: null, lieferantId: null }) } }));
    });

    it('ordnet den Artikel über das normale Artikel-Auswahlfenster zu und legt ohne Lieferant an', async () => {
        vi.stubGlobal('fetch', vi.fn(async (url: string) => url.includes('/filteroptionen')
            ? { ok: true, json: async () => ({ produktlinien: [], werkstoffe: [], profilformen: [] }) }
            : { ok: true, json: async () => ({ artikel: [{ id: 42, produktname: 'HEB 220', abmessung: '220 x 220', artikelnummer: 'ST-0815',
                werkstoffName: 'S235JR', positionsEinheit: 'lfm', preisHinweis: 'OK', guenstigsterPreis: 55 }], gesamt: 1, seite: 0, seitenGroesse: 20 }) }));
        api.ladeHicadVorschau.mockResolvedValue(ohneArtikel());
        api.uebernehmeHicad.mockResolvedValue({ angelegtePositionen: 1, erledigteGruppen: ['heb 220||s235jr'] });
        await oeffne();

        fireEvent.click(await screen.findByRole('button', { name: /Artikel zuordnen/ }));
        const dialog = await screen.findByRole('dialog', { name: 'Artikel zuordnen' });
        expect(within(dialog).getByText(/Welcher Artikel ist „HEB 220“/)).toBeInTheDocument();
        expect(within(dialog).queryByLabelText(/Menge für/)).not.toBeInTheDocument();
        fireEvent.click(await within(dialog).findByLabelText('HEB 220 auswählen'));
        fireEvent.click(within(dialog).getByRole('button', { name: 'Übernehmen' }));

        await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Artikel zuordnen' })).not.toBeInTheDocument());
        expect(screen.getByText('HEB 220 220 x 220')).toBeInTheDocument();
        expect(screen.getByText('· Nr. ST-0815')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Artikel ändern/ })).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: /1 Position anlegen/ }));
        await waitFor(() => expect(api.uebernehmeHicad).toHaveBeenCalledWith(expect.objectContaining({ entscheidungen: {
            'heb 220||s235jr': expect.objectContaining({ artikelId: 42, artikelnummer: 'ST-0815', lieferantId: null }) } })));
    });
});
