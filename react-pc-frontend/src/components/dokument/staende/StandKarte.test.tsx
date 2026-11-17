import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { StandKarte } from './StandKarte';
import { standEintrag } from './staendeTestdaten';
import type { StandEintrag } from './staendeTypen';

function zeige(teil: Partial<StandEintrag> = {}, props: Partial<Parameters<typeof StandKarte>[0]> = {}) {
    const onPdf = vi.fn();
    const onWiederherstellen = vi.fn();
    render(
        <StandKarte
            dokumentId={12}
            stand={standEintrag(teil)}
            sperrgrund={null}
            laeuft={false}
            onPdf={onPdf}
            onWiederherstellen={onWiederherstellen}
            {...props}
        />,
    );
    return { onPdf, onWiederherstellen };
}

describe('StandKarte', () => {
    it('zeigt Nummer, Zeit, wer exportiert und wer bearbeitet hat, und den Nettobetrag', () => {
        zeige();
        const karte = screen.getByRole('article', { name: 'Stand 3' });
        expect(within(karte).getByText('12.11.2026, 14:32 Uhr')).toBeInTheDocument();
        expect(within(karte).getByText('exportiert von Max Mustermann')).toBeInTheDocument();
        expect(within(karte).getByText('bearbeitet von Erika Musterfrau')).toBeInTheDocument();
        expect(within(karte).getByText(/1\.234,50\s€/)).toHaveClass('tabular-nums');
    });

    it('kennzeichnet aktuellen Inhalt, Sicherung vor dem Wiederherstellen und wiederhergestellte Stände', () => {
        zeige({ aktuell: true, art: 'VOR_WIEDERHERSTELLEN', wiederhergestelltAusNummer: 2 });
        expect(screen.getByText('aktueller Inhalt')).toBeInTheDocument();
        expect(screen.getByText('Gesichert vor dem Wiederherstellen')).toBeInTheDocument();
        expect(screen.getByText('Stand 2 wiederhergestellt')).toBeInTheDocument();
    });

    it('zeigt erst fünf Änderungen und klappt den Rest auf', async () => {
        const aenderungen = Array.from({ length: 12 }, (_, i) => `Änderung ${i + 1}`);
        zeige({ aenderungen });
        expect(screen.getByText('Änderung 5')).toBeInTheDocument();
        expect(screen.queryByText('Änderung 6')).not.toBeInTheDocument();

        const knopf = screen.getByRole('button', { name: 'Alle 12 Änderungen anzeigen' });
        expect(knopf).toHaveAttribute('aria-expanded', 'false');
        await userEvent.click(knopf);

        expect(screen.getByText('Änderung 12')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Weniger anzeigen' })).toHaveAttribute('aria-expanded', 'true');
    });

    it('öffnet „Im Editor ansehen“ als neuen Tab ohne Zugriff auf dieses Fenster', () => {
        zeige();
        const link = screen.getByRole('link', { name: 'Im Editor ansehen' });
        expect(link).toHaveAttribute('href', '/dokument-editor?dokumentId=12&stand=3');
        expect(link).toHaveAttribute('target', '_blank');
        expect(link.getAttribute('rel')).toContain('noopener');
    });

    it('zeigt PDF nur, wenn eines gespeichert ist', async () => {
        const { onPdf } = zeige();
        await userEvent.click(screen.getByRole('button', { name: 'PDF von Stand 3 ansehen' }));
        expect(onPdf).toHaveBeenCalled();
    });

    it('ohne gespeichertes PDF kein PDF-Knopf', () => {
        zeige({ pdfVorhanden: false });
        expect(screen.queryByRole('button', { name: /PDF/ })).not.toBeInTheDocument();
    });

    it('sperrt Wiederherstellen mit Begründung', async () => {
        const { onWiederherstellen } = zeige({}, { sperrgrund: 'Dieser Stand entspricht schon dem aktuellen Inhalt.' });
        const knopf = screen.getByRole('button', { name: 'Stand 3 wiederherstellen' });
        expect(knopf).toBeDisabled();
        expect(knopf).toHaveAccessibleDescription('Dieser Stand entspricht schon dem aktuellen Inhalt.');
        expect(knopf.parentElement).toHaveAttribute('title', 'Dieser Stand entspricht schon dem aktuellen Inhalt.');
        await userEvent.click(knopf);
        expect(onWiederherstellen).not.toHaveBeenCalled();
    });

    it('hat keinen eigenen Info-Knopf (der steht einmal im Kopf der Liste)', () => {
        zeige();
        expect(screen.queryByRole('button', { name: /Mehr zu/ })).not.toBeInTheDocument();
    });

    it('sperrt Wiederherstellen, solange es läuft', () => {
        zeige({}, { laeuft: true });
        expect(screen.getByRole('button', { name: 'Stand 3 wiederherstellen' })).toBeDisabled();
    });
});
