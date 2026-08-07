import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { DokumentHierarchie } from './DokumentHierarchie';
import { ToastProvider } from './ui/toast';
import type { AusgangsGeschaeftsDokument, AusgangsGeschaeftsDokumentTyp } from '../types';

const mockFetch = vi.fn();
global.fetch = mockFetch as unknown as typeof fetch;

/**
 * Abgerechnet wird immer am untersten Dokument eines Vorgangs. Hängt unter einem
 * Angebot (oder Nachtragsangebot) bereits eine Auftragsbestätigung, entstehen die
 * Rechnungen dort — sonst liefen zwei Abrechnungsstände nebeneinander und der
 * Restbetrag stimmte in keinem von beiden.
 */
describe('DokumentHierarchie – Rechnungen nur am untersten Dokument', () => {
    beforeEach(() => {
        mockFetch.mockReset();
        // Freigabe-Status-Abfrage im useEffect
        mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve({}) } as unknown as Response);
    });

    function dok(
        id: number,
        typ: AusgangsGeschaeftsDokumentTyp,
        dokumentNummer: string,
        vorgaengerId?: number
    ): AusgangsGeschaeftsDokument {
        return {
            id,
            dokumentNummer,
            typ,
            datum: '2026-01-15',
            betreff: 'Carport Musterstraße',
            betragNetto: 10000,
            gebucht: false,
            storniert: false,
            bearbeitbar: true,
            vorgaengerId,
        } as AusgangsGeschaeftsDokument;
    }

    function renderBaum(
        dokumente: AusgangsGeschaeftsDokument[],
        kontext: { projektId?: number; anfrageId?: number } = { projektId: 158 }
    ) {
        return render(
            <ToastProvider>
                <DokumentHierarchie
                    ausgangsDokumente={dokumente}
                    {...kontext}
                    onRefresh={vi.fn()}
                    toast={{ error: vi.fn(), success: vi.fn() }}
                />
            </ToastProvider>
        );
    }

    /** Öffnet das Aktionsmenü des Dokuments über einen Klick auf dessen Karte. */
    async function oeffneAktionsmenue(dokumentNummer: string) {
        fireEvent.click(screen.getByText(dokumentNummer));
        await waitFor(() => expect(screen.getByText('Öffnen (neuer Tab)')).toBeInTheDocument());
    }

    it('sperrt "Rechnung erstellen" am Angebot, sobald eine Auftragsbestätigung darunter hängt', async () => {
        renderBaum([
            dok(1, 'ANGEBOT', '2026/01/00001'),
            dok(2, 'AUFTRAGSBESTAETIGUNG', '2026/01/00002', 1),
        ]);

        await oeffneAktionsmenue('2026/01/00001');

        expect(screen.getByText('→ Rechnung erstellen').closest('button')).toBeDisabled();
        expect(screen.getByText('Rechnung bitte an der Auftragsbestätigung anlegen')).toBeInTheDocument();
    });

    it('sperrt nicht, wenn am Angebot trotz Auftragsbestätigung bereits abgerechnet wurde', async () => {
        // Bestandsvorgang: Der Abrechnungsverlauf ist am Angebot vollständig,
        // an der AB fehlten die 3.000 EUR — also bleibt es am Angebot.
        renderBaum([
            dok(1, 'ANGEBOT', '2026/01/00001'),
            dok(2, 'ABSCHLAGSRECHNUNG', '2026/01/00002', 1),
            dok(3, 'AUFTRAGSBESTAETIGUNG', '2026/01/00003', 1),
        ]);

        await oeffneAktionsmenue('2026/01/00001');

        expect(screen.getByText('→ Rechnung erstellen').closest('button')).toBeEnabled();
    });

    it('sperrt "Rechnung erstellen" auch am Nachtragsangebot mit Auftragsbestätigung', async () => {
        renderBaum([
            dok(1, 'ANGEBOT', '2026/01/00001'),
            dok(3, 'NACHTRAGSANGEBOT', 'NA-2026/01/00001'),
            dok(4, 'AUFTRAGSBESTAETIGUNG', '2026/01/00003', 3),
        ]);

        await oeffneAktionsmenue('NA-2026/01/00001');

        expect(screen.getByText('→ Rechnung erstellen').closest('button')).toBeDisabled();
    });

    it('erlaubt "Rechnung erstellen" am Angebot ohne Auftragsbestätigung', async () => {
        renderBaum([dok(1, 'ANGEBOT', '2026/01/00001')]);

        await oeffneAktionsmenue('2026/01/00001');

        expect(screen.getByText('→ Rechnung erstellen').closest('button')).toBeEnabled();
        expect(screen.queryByText('Rechnung bitte an der Auftragsbestätigung anlegen')).not.toBeInTheDocument();
    });

    it('bietet gar keine Auftragsbestätigung an', async () => {
        // Eine AB bestätigt einen Auftrag — den gibt es erst, wenn aus der
        // Anfrage ein Projekt geworden ist. Sie entsteht deshalb nur im
        // Projekt-Editor oder automatisch bei digitaler Angebotsannahme.
        renderBaum([dok(1, 'ANGEBOT', '2026/01/00001')], { anfrageId: 42 });

        await oeffneAktionsmenue('2026/01/00001');

        expect(screen.queryByText('→ Auftragsbestätigung')).not.toBeInTheDocument();
    });

    it('erlaubt "Rechnung erstellen" an der Auftragsbestätigung selbst', async () => {
        renderBaum([
            dok(1, 'ANGEBOT', '2026/01/00001'),
            dok(2, 'AUFTRAGSBESTAETIGUNG', '2026/01/00002', 1),
        ]);

        await oeffneAktionsmenue('2026/01/00002');

        expect(screen.getByText('→ Rechnung erstellen').closest('button')).toBeEnabled();
    });
});
