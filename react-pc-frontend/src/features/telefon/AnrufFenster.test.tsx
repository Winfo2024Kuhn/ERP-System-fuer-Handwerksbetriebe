import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AnrufFenster } from './AnrufFenster';
import { KUNDE_ERIKA, KUNDE_MAX, LIEFERANT_GMBH, UEBERBLICK_MAX } from './telefonTestdaten';
import type { UeberblickZustand } from './useKontaktUeberblick';
import type { LiveAnrufAnzeige } from './useTelefonLive';

function live(teil: Partial<LiveAnrufAnzeige> = {}): LiveAnrufAnzeige {
    return {
        verbindungsId: '1',
        status: 'KLINGELT',
        nummer: '0931 1234567',
        kontakt: KUNDE_MAX,
        kandidaten: [],
        angenommen: false,
        gespraechSeit: null,
        verpasst: false,
        ...teil,
    };
}

const LAEDT: UeberblickZustand = { status: 'laedt' };

function zeige(anruf: LiveAnrufAnzeige, weitere = 0, ueberblick: UeberblickZustand | null = LAEDT) {
    const onSchliessen = vi.fn();
    const onKontaktOeffnen = vi.fn();
    const onOeffnen = vi.fn();
    const ergebnis = render(
        <AnrufFenster
            anruf={anruf}
            weitere={weitere}
            onSchliessen={onSchliessen}
            onKontaktOeffnen={onKontaktOeffnen}
            ueberblick={anruf.kontakt ? ueberblick : null}
            onOeffnen={onOeffnen}
        />,
    );
    return { ...ergebnis, onSchliessen, onKontaktOeffnen, onOeffnen };
}

describe('AnrufFenster', () => {
    afterEach(() => vi.useRealTimers());

    it('zeigt einen bekannten Kunden groß mit allen Angaben und öffnet die Akte', () => {
        const { onKontaktOeffnen } = zeige(live());
        expect(screen.getByRole('dialog', { name: 'Max Mustermann' })).toHaveAttribute('aria-modal', 'false');
        expect(screen.getByRole('status')).toHaveTextContent('ruft an');
        expect(screen.getByText('Kunde')).toBeInTheDocument();
        expect(screen.getByText('Kunden-Nr. K-1007')).toBeInTheDocument();
        expect(screen.getByText('Musterstadt')).toBeInTheDocument();
        expect(screen.getByText('0931 1234567')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Akte öffnen' }));
        expect(onKontaktOeffnen).toHaveBeenCalledWith(KUNDE_MAX);
    });

    it('kennzeichnet Lieferanten', () => {
        zeige(live({ kontakt: LIEFERANT_GMBH }));
        expect(screen.getByRole('heading', { name: 'Mustermann GmbH' })).toBeInTheDocument();
        expect(screen.getByText('Lieferant')).toBeInTheDocument();
    });

    it('stiehlt keinen Fokus: das Eingabefeld im Hintergrund behält ihn', () => {
        render(<input aria-label="Dokumenttext" />);
        const feld = screen.getByLabelText('Dokumenttext');
        feld.focus();
        zeige(live());
        expect(document.activeElement).toBe(feld);
    });

    it('zeigt unbekannte Nummern nur mit Schließen', () => {
        zeige(live({ kontakt: null }));
        expect(screen.getByRole('heading', { name: 'Unbekannte Nummer' })).toBeInTheDocument();
        expect(screen.getByText('0931 1234567')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Akte öffnen' })).toBeNull();
        expect(screen.getByRole('button', { name: 'Schließen' })).toBeInTheDocument();
    });

    it('zeigt unterdrückte Nummern', () => {
        zeige(live({ kontakt: null, nummer: '' }));
        expect(screen.getByRole('heading', { name: 'Nummer unterdrückt' })).toBeInTheDocument();
    });

    it('bietet bei mehrdeutiger Nummer die Kandidaten zum Öffnen an', () => {
        const { onKontaktOeffnen } = zeige(live({ kontakt: null, kandidaten: [KUNDE_MAX, KUNDE_ERIKA] }));
        expect(screen.getByRole('heading', { name: 'Einer dieser Kontakte' })).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: /Erika Mustermann/ }));
        expect(onKontaktOeffnen).toHaveBeenCalledWith(KUNDE_ERIKA);
    });

    it('zeigt im Gespräch die laufende Dauer', () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date(2026, 8, 29, 12, 0, 0));
        zeige(live({ status: 'IM_GESPRAECH', angenommen: true, gespraechSeit: Date.now() - 83_000 }));
        expect(screen.getByRole('status')).toHaveTextContent('Im Gespräch · 01:23');
        act(() => { vi.advanceTimersByTime(2000); });
        expect(screen.getByRole('status')).toHaveTextContent('Im Gespräch · 01:25');
    });

    it('meldet den Anrufbeantworter und verpasste Anrufe', () => {
        const { rerender } = zeige(live({ status: 'ANRUFBEANTWORTER' }));
        expect(screen.getByRole('status')).toHaveTextContent('Anrufbeantworter nimmt auf');
        rerender(<AnrufFenster anruf={live({ status: 'BEENDET', verpasst: true })} weitere={0} onSchliessen={vi.fn()} onKontaktOeffnen={vi.fn()} ueberblick={LAEDT} onOeffnen={vi.fn()} />);
        expect(screen.getByRole('status')).toHaveTextContent('Verpasst');
    });

    it('schließt per Escape und Klick auf den Hintergrund', () => {
        const { onSchliessen } = zeige(live());
        fireEvent.keyDown(document, { key: 'Escape' });
        expect(onSchliessen).toHaveBeenCalledTimes(1);
        fireEvent.click(screen.getByTestId('anruf-fenster-hintergrund'));
        expect(onSchliessen).toHaveBeenCalledTimes(2);
    });

    it('nennt weitere gleichzeitige Anrufe', () => {
        const { rerender } = zeige(live(), 1);
        expect(screen.getByText('+1 weiterer Anruf')).toBeInTheDocument();
        rerender(<AnrufFenster anruf={live()} weitere={2} onSchliessen={vi.fn()} onKontaktOeffnen={vi.fn()} ueberblick={LAEDT} onOeffnen={vi.fn()} />);
        expect(screen.getByText('+2 weitere Anrufe')).toBeInTheDocument();
    });

    describe('Überblick zum Anrufer', () => {
        it('zeigt Ansprechpartner, Adresse, Projekte und Anfragen und springt hinein', () => {
            const { onOeffnen } = zeige(live(), 0, { status: 'fertig', daten: UEBERBLICK_MAX });
            expect(screen.getByText('Ansprechpartner')).toBeInTheDocument();
            expect(screen.getByText('Erika Mustermann')).toBeInTheDocument();
            expect(screen.getByText('Musterweg 1')).toBeInTheDocument();
            expect(screen.getByText('12345 Musterstadt')).toBeInTheDocument();

            const projekte = screen.getByRole('region', { name: 'Projekte' });
            expect(projekte).toHaveTextContent('(2)');
            expect(projekte).toHaveTextContent('Auftrags-Nr. 2026-001 · Musterstadt');
            expect(screen.getByRole('button', { name: /Carport/ })).toHaveTextContent('Abgeschlossen');
            fireEvent.click(screen.getByRole('button', { name: /Wintergarten Musterweg/ }));
            expect(onOeffnen).toHaveBeenCalledWith('/projekte?projektId=21');

            expect(screen.getByRole('button', { name: /Balkongeländer/ })).toHaveTextContent('Angebots-Nr. AN-2026-044 · Beispielhausen');
            const ohneBauvorhaben = screen.getByRole('button', { name: /Anfrage ohne Bauvorhaben/ });
            expect(ohneBauvorhaben).toHaveTextContent('Noch kein Angebot');
            fireEvent.click(ohneBauvorhaben);
            expect(onOeffnen).toHaveBeenCalledWith('/anfragen?anfrageId=32');
        });

        it('zeigt beim Laden Platzhalter statt leerer Listen, der Ort steht schon da', () => {
            zeige(live());
            expect(screen.getByText('Wird geladen …')).toBeInTheDocument();
            expect(screen.getByText('Musterstadt')).toBeInTheDocument();
            expect(screen.queryByText('Noch keine Projekte.')).toBeNull();
            expect(screen.queryByRole('button', { name: /Wintergarten/ })).toBeNull();
        });

        it('meldet leere Listen', () => {
            zeige(live(), 0, { status: 'fertig', daten: { ...UEBERBLICK_MAX, ansprechpartner: null, projekte: [], anfragen: [] } });
            expect(screen.getByText('Noch keine Projekte.')).toBeInTheDocument();
            expect(screen.getByText('Keine Anfragen.')).toBeInTheDocument();
            expect(screen.queryByText('Ansprechpartner')).toBeNull();
        });

        it('meldet einen Ladefehler sichtbar, Akte öffnen geht weiter', () => {
            zeige(live(), 0, { status: 'fehler', meldung: 'Projekte und Anfragen konnten nicht geladen werden.' });
            expect(screen.getByRole('alert')).toHaveTextContent('Projekte und Anfragen konnten nicht geladen werden.');
            expect(screen.queryByRole('region', { name: 'Projekte' })).toBeNull();
            expect(screen.getByRole('button', { name: 'Akte öffnen' })).toBeInTheDocument();
        });

        it('zeigt beim Lieferanten den Vertreter, aber keine Projekte', () => {
            zeige(live({ kontakt: LIEFERANT_GMBH }), 0, {
                status: 'fertig',
                daten: { ...UEBERBLICK_MAX, typ: 'LIEFERANT', id: 3, nummer: null, ansprechpartner: 'Hans Beispiel', projekte: [], anfragen: [] },
            });
            expect(screen.getByText('Vertreter')).toBeInTheDocument();
            expect(screen.getByText('Hans Beispiel')).toBeInTheDocument();
            expect(screen.queryByRole('region', { name: 'Projekte' })).toBeNull();
        });

        it('nennt bei mehr als 50 Einträgen die Gesamtzahl und verweist auf die Akte', () => {
            zeige(live(), 0, { status: 'fertig', daten: { ...UEBERBLICK_MAX, projekteGesamt: 57 } });
            const projekte = screen.getByRole('region', { name: 'Projekte' });
            expect(projekte).toHaveTextContent('(57)');
            expect(projekte).toHaveTextContent('… und 55 weitere – alle stehen in der Akte.');
            expect(screen.getByRole('region', { name: 'Anfragen' })).not.toHaveTextContent('weitere –');
        });

        it('zeigt ohne zugeordneten Kontakt keinen Überblick', () => {
            zeige(live({ kontakt: null }));
            expect(screen.queryByTestId('anruf-kontakt-details')).toBeNull();
        });
    });
});
