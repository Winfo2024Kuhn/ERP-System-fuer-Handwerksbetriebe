import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import MietabrechnungEditor from './MietabrechnungEditor';
import { ParteienView } from '../components/mietabrechnung/ParteienView';
import { RaeumeView } from '../components/mietabrechnung/RaeumeView';
import { KostenpositionenView } from '../components/mietabrechnung/KostenpositionenView';
import { MietabrechnungService as api } from '../components/mietabrechnung/MietabrechnungService';

vi.mock('../components/mietabrechnung/MietabrechnungService', () => ({ MietabrechnungService: {
    getMietobjekte: vi.fn(), createMietobjekt: vi.fn(), getParteien: vi.fn(), createPartei: vi.fn(),
    getRaeume: vi.fn(), getVerbraucherForRaum: vi.fn(), getZaehlerstaende: vi.fn(), createRaum: vi.fn(), createZaehlerstand: vi.fn(),
    getKostenstellen: vi.fn(), createKostenposition: vi.fn(), updateKostenposition: vi.fn(), updateRaum: vi.fn(), updateZaehlerstand: vi.fn(),
} }));
function show(node: React.ReactNode) { return render(<MemoryRouter><ToastProvider><ConfirmProvider>{node}</ConfirmProvider></ToastProvider></MemoryRouter>); }
beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.getMietobjekte).mockResolvedValue([]);
    vi.mocked(api.getParteien).mockResolvedValue([]);
    vi.mocked(api.getRaeume).mockResolvedValue([{ id: 1, name: 'Testraum', beschreibung: '', flaecheQuadratmeter: 10 }]);
    vi.mocked(api.getVerbraucherForRaum).mockResolvedValue([{ id: 1, raumId: 1, name: 'Testzähler', verbrauchsart: 'WASSER', einheit: 'm³', seriennummer: '001', aktiv: true }]);
    vi.mocked(api.getZaehlerstaende).mockResolvedValue([]);
    vi.mocked(api.getKostenstellen).mockResolvedValue([{ id: 1, name: 'Testkosten', kostenpositionen: [] }]);
});
afterEach(cleanup);

describe('Mietformulare mit eigenen Eingaben', () => {
    it('erstellt Mietobjekte nur mit einem bestätigten Namen im eigenen Dialog', async () => {
        const user = userEvent.setup(); show(<MietabrechnungEditor />);
        await user.click(screen.getByRole('button', { name: 'Neues Objekt' }));
        const name = screen.getByRole('textbox', { name: 'Name des Mietobjekts' });
        await user.click(screen.getByRole('button', { name: 'Anlegen', exact: true }));
        expect(api.createMietobjekt).not.toHaveBeenCalled();
        await user.type(name, 'Testobjekt');
        await user.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(api.createMietobjekt).not.toHaveBeenCalled();
    });
    it('hält leeren Vorschuss ungültig und überträgt 12,50 als Zahl', async () => {
        const user = userEvent.setup(); show(<ParteienView mietobjektId={1} />);
        await user.click(screen.getByRole('button', { name: /Partei hinzufügen/ }));
        await user.type(screen.getByRole('textbox', { name: 'Name / Firma' }), 'Testpartei');
        const amount = screen.getByRole('textbox', { name: 'Monatlicher Vorschuss (€)' });
        await user.click(amount); expect(amount).toHaveValue('');
        await user.click(screen.getByRole('button', { name: 'Speichern', exact: true }));
        expect(api.createPartei).not.toHaveBeenCalled();
        await user.type(amount, '12,50');
        await user.click(screen.getByRole('button', { name: 'Speichern', exact: true }));
        await waitFor(() => expect(api.createPartei).toHaveBeenCalledWith(1, expect.objectContaining({ monatlicherVorschuss: 12.5 })));
    });
    it('prüft die Raumfläche vollständig vor dem Speichern', async () => {
        const user = userEvent.setup(); show(<RaeumeView mietobjektId={1} />);
        await user.click(screen.getByRole('button', { name: /Raum hinzufügen/ }));
        await user.type(screen.getByRole('textbox', { name: 'Bezeichnung', exact: true }), 'Testzimmer');
        const area = screen.getByRole('textbox', { name: 'Fläche (m²)' });
        await user.click(area); expect(area).toHaveValue('');
        await user.type(area, '12,'); await user.click(screen.getByRole('button', { name: 'Speichern', exact: true }));
        expect(api.createRaum).not.toHaveBeenCalled();
        await user.type(area, '5'); await user.click(screen.getByRole('button', { name: 'Speichern', exact: true }));
        await waitFor(() => expect(api.createRaum).toHaveBeenCalledWith(1, expect.objectContaining({ flaecheQuadratmeter: 12.5 })));
    });
    it('hält Jahre ganzzahlig und lässt den optionalen Verbrauch leer', async () => {
        const user = userEvent.setup(); show(<RaeumeView mietobjektId={1} />);
        await user.click(await screen.findByRole('button', { name: 'Stand', exact: true }));
        const year = screen.getByRole('textbox', { name: 'Abrechnungsjahr' });
        const amount = screen.getByRole('textbox', { name: 'Zählerstand' });
        fireEvent.change(year, { target: { value: '2026,5' } });
        fireEvent.change(amount, { target: { value: '12,5' } });
        await user.click(screen.getByRole('button', { name: 'Speichern', exact: true })); expect(api.createZaehlerstand).not.toHaveBeenCalled();
        fireEvent.change(year, { target: { value: '2026' } });
        fireEvent.change(screen.getByRole('textbox', { name: 'Verbrauch (optional)' }), { target: { value: ' ' } });
        await user.click(screen.getByRole('button', { name: 'Speichern', exact: true }));
        await waitFor(() => expect(api.createZaehlerstand).toHaveBeenCalledWith(1, expect.objectContaining({ abrechnungsJahr: 2026, stand: 12.5, verbrauch: undefined })));
    });
    it('verhindert unvollständige Kostenbeträge und erhält zulässige Gutschriften', async () => {
        const user = userEvent.setup(); show(<KostenpositionenView mietobjektId={1} />);
        await waitFor(() => expect(api.getKostenstellen).toHaveBeenCalled());
        await user.click(screen.getByRole('button', { name: /Kostenposition$/ }));
        const amount = screen.getByRole('textbox', { name: 'Betrag (€)' });
        fireEvent.change(amount, { target: { value: '12,' } });
        await user.click(screen.getByRole('button', { name: 'Speichern', exact: true })); expect(api.createKostenposition).not.toHaveBeenCalled();
        fireEvent.change(amount, { target: { value: '-12,50' } });
        await user.click(screen.getByRole('button', { name: 'Speichern', exact: true }));
        await waitFor(() => expect(api.createKostenposition).toHaveBeenCalledWith(1, expect.objectContaining({ betrag: -12.5 })));
    });
    it('rundet Vorschüsse nicht still und zeigt fehlgeschlagene Speicherung im Dialog', async () => {
        const user = userEvent.setup(); show(<ParteienView mietobjektId={1} />);
        await user.click(screen.getByRole('button', { name: /Partei hinzufügen/ }));
        await user.type(screen.getByRole('textbox', { name: 'Name / Firma' }), 'Testpartei');
        const amount = screen.getByRole('textbox', { name: 'Monatlicher Vorschuss (€)' });
        fireEvent.change(amount, { target: { value: '12,345' } });
        await user.click(screen.getByRole('button', { name: 'Speichern', exact: true }));
        expect(api.createPartei).not.toHaveBeenCalled(); expect(amount).toHaveValue('12,345');
        vi.mocked(api.createPartei).mockRejectedValueOnce(new Error('Testfehler'));
        fireEvent.change(amount, { target: { value: '12,50' } });
        await user.click(screen.getByRole('button', { name: 'Speichern', exact: true }));
        expect(await screen.findByText('Partei konnte nicht gespeichert werden.')).toBeVisible();
        expect(screen.getByRole('dialog', { name: 'Partei bearbeiten' })).toBeVisible();
    });
    it('übernimmt bei Verbrauchsberechnung nur den vollständig geprüften Faktor', async () => {
        const user = userEvent.setup(); show(<KostenpositionenView mietobjektId={1} />);
        await waitFor(() => expect(api.getKostenstellen).toHaveBeenCalled());
        await user.click(screen.getByRole('button', { name: /Kostenposition$/ }));
        await user.click(screen.getByRole('combobox', { name: 'Berechnungsart' }));
        await user.click(screen.getByRole('option', { name: 'Verbrauch × Faktor' }));
        const factor = screen.getByRole('textbox', { name: 'Verbrauchsfaktor (€ pro Einheit)' });
        fireEvent.change(factor, { target: { value: '1,2345678' } });
        await user.click(screen.getByRole('button', { name: 'Speichern', exact: true })); expect(api.createKostenposition).not.toHaveBeenCalled();
        fireEvent.change(factor, { target: { value: '1,234567' } });
        await user.click(screen.getByRole('button', { name: 'Speichern', exact: true }));
        await waitFor(() => expect(api.createKostenposition).toHaveBeenCalledWith(1, expect.objectContaining({ betrag: 0, verbrauchsfaktor: 1.234567 })));
    });

    it('zeigt fehlende Bestandsfläche leer und fordert sie vor dem Speichern an', async () => {
        vi.mocked(api.getRaeume).mockResolvedValue([{ id: 1, name: 'Testraum', beschreibung: '', flaecheQuadratmeter: null }]);
        const user = userEvent.setup(); show(<RaeumeView mietobjektId={1} />);
        await user.click(await screen.findByRole('button', { name: 'Bearbeiten', exact: true }));
        expect(screen.getByRole('textbox', { name: 'Fläche (m²)' })).toHaveValue('');
        await user.click(screen.getByRole('button', { name: 'Speichern', exact: true }));
        expect(api.updateRaum).not.toHaveBeenCalled();
    });
    it('erhält vier gespeicherte Nachkommastellen beim Bearbeiten eines Zählerstands', async () => {
        vi.mocked(api.getZaehlerstaende).mockResolvedValue([{ id: 1, verbrauchsgegenstandId: 1, abrechnungsJahr: 2026, stichtag: '2026-12-31', stand: 12.3456, verbrauch: 1.2345, kommentar: '' }]);
        const user = userEvent.setup(); show(<RaeumeView mietobjektId={1} />);
        await user.click(await screen.findByRole('button', { name: 'Zählerstand bearbeiten' }));
        expect(screen.getByRole('textbox', { name: 'Zählerstand', exact: true })).toHaveValue('12,3456');
        await user.click(screen.getByRole('button', { name: 'Speichern', exact: true }));
        await waitFor(() => expect(api.updateZaehlerstand).toHaveBeenCalledWith(1, expect.objectContaining({ stand: 12.3456, verbrauch: 1.2345 })));
    });
    it('bearbeitet einen gespeicherten Faktor mit sechs Stellen und fehlendem Festbetrag', async () => {
        vi.mocked(api.getKostenstellen).mockResolvedValue([{ id: 1, name: 'Testkosten', kostenpositionen: [{ id: 1, kostenstelleId: 1, abrechnungsJahr: new Date().getFullYear(), buchungsdatum: '2026-09-09', betrag: null, berechnung: 'VERBRAUCHSFAKTOR', verbrauchsfaktor: 1.234567, beschreibung: '' }] }]);
        const user = userEvent.setup(); show(<KostenpositionenView mietobjektId={1} />);
        await user.click(await screen.findByRole('button', { name: 'Bearbeiten', exact: true }));
        expect(screen.getByRole('textbox', { name: 'Verbrauchsfaktor (€ pro Einheit)' })).toHaveValue('1,234567');
        await user.click(screen.getByRole('button', { name: 'Speichern', exact: true }));
        await waitFor(() => expect(api.updateKostenposition).toHaveBeenCalledWith(1, expect.objectContaining({ verbrauchsfaktor: 1.234567 })));
    });

});
