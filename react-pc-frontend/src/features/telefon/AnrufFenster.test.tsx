import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AnrufFenster } from './AnrufFenster';
import { KUNDE_ERIKA, KUNDE_MAX, LIEFERANT_GMBH } from './telefonTestdaten';
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

function zeige(anruf: LiveAnrufAnzeige, weitere = 0) {
    const onSchliessen = vi.fn();
    const onKontaktOeffnen = vi.fn();
    const ergebnis = render(<AnrufFenster anruf={anruf} weitere={weitere} onSchliessen={onSchliessen} onKontaktOeffnen={onKontaktOeffnen} />);
    return { ...ergebnis, onSchliessen, onKontaktOeffnen };
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
        rerender(<AnrufFenster anruf={live({ status: 'BEENDET', verpasst: true })} weitere={0} onSchliessen={vi.fn()} onKontaktOeffnen={vi.fn()} />);
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
        rerender(<AnrufFenster anruf={live()} weitere={2} onSchliessen={vi.fn()} onKontaktOeffnen={vi.fn()} />);
        expect(screen.getByText('+2 weitere Anrufe')).toBeInTheDocument();
    });
});
