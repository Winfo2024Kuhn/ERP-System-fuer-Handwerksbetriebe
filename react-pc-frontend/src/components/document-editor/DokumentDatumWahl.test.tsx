import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { DokumentDatumWahl } from './DokumentDatumWahl';
import { DATUM_WAHL_START, type DatumWahl } from './datumWahlRegeln';

const HEUTE = '2026-11-17';
const ANGELEGT = '2026-11-03';

function zeige(wert: DatumWahl = DATUM_WAHL_START, weitere: Partial<Parameters<typeof DokumentDatumWahl>[0]> = {}) {
    const onChange = vi.fn();
    render(
        <DokumentDatumWahl
            gespeichertesDatum={ANGELEGT}
            heute={HEUTE}
            wert={wert}
            onChange={onChange}
            istRechnung={false}
            zahlungszielTage={14}
            {...weitere}
        />,
    );
    return onChange;
}

describe('DokumentDatumWahl', () => {
    it('bietet heute (vorausgewählt), das bisherige und ein anderes Datum an', () => {
        zeige();
        expect(screen.getByRole('radiogroup', { name: 'Datum oben auf dem Dokument' })).toBeInTheDocument();
        expect(screen.getByRole('radio', { name: 'Heute (17.11.2026)' })).toHaveAttribute('aria-checked', 'true');
        expect(screen.getByRole('radio', { name: 'Bisheriges Datum behalten (03.11.2026)' })).toHaveAttribute('aria-checked', 'false');
        expect(screen.getByRole('radio', { name: 'Anderes Datum …' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Datum oben auf dem Dokument/ })).toBeInTheDocument();
        expect(screen.queryByText(/Zahlbar bis/)).not.toBeInTheDocument();
    });

    it('meldet die Wahl „Bisheriges Datum behalten“', async () => {
        const onChange = zeige();
        await userEvent.click(screen.getByRole('radio', { name: /Bisheriges Datum/ }));
        expect(onChange).toHaveBeenCalledWith({ art: 'bisher', anderesDatum: '' });
    });

    it('zeigt bei „Anderes Datum“ den Kalender und meldet, dass noch eins fehlt', () => {
        zeige({ art: 'anders', anderesDatum: '' });
        expect(screen.getByRole('button', { name: 'Anderes Datum' })).toBeInTheDocument();
        expect(screen.getByText('Bitte ein Datum auswählen.')).toBeInTheDocument();
    });

    it('zeigt bei Rechnungen die Fälligkeit zum gewählten Datum', () => {
        zeige({ art: 'bisher', anderesDatum: '' }, { istRechnung: true });
        expect(screen.getByText('Zahlbar bis 17.11.2026')).toBeInTheDocument();
    });

    it('sperrt bei Rechnungen ein Datum in der Zukunft mit Erklärung', () => {
        zeige({ art: 'anders', anderesDatum: '2026-11-20' }, { istRechnung: true });
        expect(screen.getByText(/kein Datum in der Zukunft/)).toBeInTheDocument();
        expect(screen.queryByText(/Zahlbar bis/)).not.toBeInTheDocument();
    });

    it('sperrt „Bisheriges Datum behalten“, wenn eine Rechnung in der Zukunft datiert ist', () => {
        zeige(DATUM_WAHL_START, { istRechnung: true, gespeichertesDatum: '2026-12-01' });
        const bisher = screen.getByRole('radio', { name: /Bisheriges Datum/ });
        expect(bisher).toHaveAttribute('aria-disabled', 'true');
        expect(bisher).toHaveAttribute('title', expect.stringMatching(/Zukunft/));
    });

    it('erklärt per Info-Knopf, was das Datum bewirkt', async () => {
        zeige();
        await userEvent.click(screen.getByRole('button', { name: /Datum oben auf dem Dokument/ }));
        expect(await screen.findByText(/zählt das Zahlungsziel ab diesem Datum/)).toBeInTheDocument();
        expect(screen.getByText(/Entwürfe mit Wasserzeichen ändern das Datum nicht/)).toBeInTheDocument();
    });
});
