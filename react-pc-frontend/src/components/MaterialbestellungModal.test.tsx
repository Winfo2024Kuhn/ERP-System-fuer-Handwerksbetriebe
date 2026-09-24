import { useState } from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MaterialbestellungModal } from './MaterialbestellungModal';
import { ToastProvider } from './ui/toast';

const ok = (body: unknown) => new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
const mutationen = () => vi.mocked(fetch).mock.calls.filter(([, init]) => init?.method === 'POST' || init?.method === 'PUT');
function open(props = {}) { return render(<ToastProvider><MaterialbestellungModal isOpen onClose={vi.fn()} {...props} /></ToastProvider>); }
async function fuelle(name: string, menge = '4') {
    fireEvent.change(screen.getByLabelText('Produktname Position 1'), { target: { value: name } });
    fireEvent.change(screen.getByLabelText('Menge Position 1'), { target: { value: menge } });
}
beforeEach(() => vi.stubGlobal('fetch', vi.fn(async () => ok([]))));
afterEach(() => vi.unstubAllGlobals());

describe('Materialbedarf mit der bestehenden EN1090-Maske', () => {
    it('speichert freien Vorratsbedarf ohne Lieferant und mit Kommamenge', async () => {
        const onSuccess = vi.fn();
        open({ ohneProjekt: true, onSuccess });
        await fuelle('Flachstahl', '12,5');
        fireEvent.click(screen.getByRole('combobox', { name: 'Einheit Position 1' }));
        fireEvent.click(screen.getByRole('option', { name: 'm (Meter)' }));
        fireEvent.click(screen.getByRole('button', { name: 'Bedarf speichern' }));
        await waitFor(() => expect(onSuccess).toHaveBeenCalledOnce());
        expect(mutationen()).toHaveLength(1);
        expect(mutationen()[0][0]).toBe('/api/einkauf/bedarf');
        expect(JSON.parse(String(mutationen()[0][1]?.body))).toMatchObject({ position: { art: 'FREITEXT', basis: { menge: 12.5, einheit: 'METER' } }, liefergruppe: { projektId: null, lagerzweck: 'Werkstatt / auf Vorrat' } });
        expect(screen.queryByText('Lieferant *')).not.toBeInTheDocument();
        expect(screen.queryByText('Warengruppe')).not.toBeInTheDocument();
        expect(vi.mocked(fetch).mock.calls.some(([url]) => String(url).includes('kategorien'))).toBe(false);
    });
    it('verwendet die Katalogsuche und speichert die Artikelkennung im Projekt', async () => {
        vi.mocked(fetch).mockImplementation(async url => ok(String(url).startsWith('/api/artikel?') ? { artikel: [{ id: 71, produktname: 'IPE 200', werkstoffName: 'S235', verrechnungseinheit: { name: 'LAUFENDE_METER' }, kgProMeter: 22.4, abmessung: '200 x 100', produkttext: 'Katalogtext' }], gesamt: 1 } : []));
        open({ initialProjekt: { id: 9, bauvorhaben: 'Musterhalle' }, projektSperren: true });
        fireEvent.click(screen.getByRole('button', { name: 'Artikel suchen...' }));
        fireEvent.click(await screen.findByText('IPE 200'));
        fireEvent.click(screen.getByRole('button', { name: 'Bedarf speichern' }));
        await waitFor(() => expect(mutationen()).toHaveLength(1));
        expect(JSON.parse(String(mutationen()[0][1]?.body))).toMatchObject({ position: { art: 'ARTIKEL', artikelId: 71, bezeichnung: 'IPE 200', abmessung: '200 x 100', basis: { einheit: 'METER', kgJeMeter: 22.4, faktorQuelle: 'Artikelstamm' } }, liefergruppe: { projektId: 9, lagerzweck: null } });
    });
    it('leert Nullwerte bei Fokus und bewahrt ungültige Entwürfe ohne API-Schreibzugriff', async () => {
        open(); await fuelle('Material', '0');
        const field = screen.getByLabelText('Menge Position 1');
        fireEvent.focus(field); expect(field).toHaveValue('');
        fireEvent.change(field, { target: { value: '1,' } });
        fireEvent.click(screen.getByRole('button', { name: 'Bedarf speichern' }));
        expect(field).toHaveValue('1,');
        expect(mutationen()).toHaveLength(0);
        expect(await screen.findAllByText(/vollständige Zahl mit Dezimalkomma/)).not.toHaveLength(0);
        expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument();
    });
    it('behält bei Serverfehlern die Eingaben und meldet den konkreten Fehler', async () => {
        vi.mocked(fetch).mockImplementation(async (_url, init) => init?.method === 'POST' ? new Response(JSON.stringify({ message: 'Bedarf konnte nicht gespeichert werden.' }), { status: 500 }) : ok([]));
        const onClose = vi.fn(); open({ onClose }); await fuelle('Werkstattbedarf');
        fireEvent.click(screen.getByRole('button', { name: 'Bedarf speichern' }));
        await screen.findAllByText('Bedarf konnte nicht gespeichert werden.');
        expect(screen.getByLabelText('Produktname Position 1')).toHaveValue('Werkstattbedarf');
        expect(onClose).not.toHaveBeenCalled();
    });
    it('wiederholt nach Teilfehler nur die noch nicht gespeicherten Positionen', async () => {
        let anzahl = 0;
        vi.mocked(fetch).mockImplementation(async (_url, init) => {
            if (init?.method !== 'POST') return ok([]);
            anzahl++;
            return anzahl === 2 ? new Response(JSON.stringify({ message: 'Zweite Position fehlgeschlagen.' }), { status: 500 }) : ok({});
        });
        function Parent() {
            const [offen, setOffen] = useState(true);
            return <ToastProvider>{offen && <MaterialbestellungModal isOpen onClose={() => setOffen(false)} onSuccess={() => setOffen(false)} />}</ToastProvider>;
        }
        render(<Parent />); await fuelle('Erste Position');
        fireEvent.click(screen.getByRole('button', { name: 'Leere Zeile' }));
        fireEvent.change(screen.getByLabelText('Produktname Position 2'), { target: { value: 'Zweite Position' } });
        fireEvent.click(screen.getByRole('button', { name: 'Bedarf speichern' }));
        await waitFor(() => expect(screen.getByLabelText('Produktname Position 1')).toHaveValue('Zweite Position'));
        fireEvent.click(screen.getByRole('button', { name: 'Bedarf speichern' }));
        await waitFor(() => expect(mutationen()).toHaveLength(3));
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        expect(mutationen().map(([, init]) => JSON.parse(String(init?.body)).position.bezeichnung)).toEqual(['Erste Position', 'Zweite Position', 'Zweite Position']);
    });
    it('fordert eine bewusste Bestätigung eines neuen Zeugnisses vor dem Speichern', async () => {
        open(); await fuelle('Stahlprofil');
        fireEvent.click(screen.getByRole('combobox', { name: 'Zeugnis Position 1' }));
        fireEvent.click(screen.getByRole('option', { name: 'Abnahmeprüfzeugnis 3.1' }));
        const bestaetigung = screen.getByRole('checkbox', { name: 'Zeugnisanforderung fachlich geprüft' });
        expect(bestaetigung).not.toBeChecked();
        fireEvent.click(screen.getByRole('button', { name: 'Bedarf speichern' }));
        expect(mutationen()).toHaveLength(0);
        expect(await screen.findAllByText(/Zeugnisanforderung fachlich prüfen und bestätigen/)).not.toHaveLength(0);
        fireEvent.click(bestaetigung);
        fireEvent.click(screen.getByRole('button', { name: 'Bedarf speichern' }));
        await waitFor(() => expect(mutationen()).toHaveLength(1));
        expect(JSON.parse(String(mutationen()[0][1]?.body)).position.dokumente).toEqual([expect.objectContaining({ art: 'ZEUGNIS_3_1', fachlichBestaetigt: true })]);
    });

});
