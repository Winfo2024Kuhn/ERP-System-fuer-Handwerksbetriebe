import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { SteuerberaterKontakteListe } from './SteuerberaterKontakteListe';
import type { SteuerberaterKontakt } from './firmaTypen';

/** Dummy-Daten (DSGVO): Kanzlei Mustermann. */
const KANZLEI = {
    id: 3, name: 'Kanzlei Mustermann', email: 'kanzlei@example.org', autoProcessEmails: true, aktiv: true,
    gueltigAb: '2026-03-01', gueltigBis: null, weitereEmails: [],
} as unknown as SteuerberaterKontakt;

describe('SteuerberaterKontakteListe', () => {
    it('benennt die Icon-Knöpfe mit dem Namen und reicht den ganzen Kontakt weiter', async () => {
        const user = userEvent.setup();
        const onBearbeiten = vi.fn();
        const onLoeschen = vi.fn();
        render(<SteuerberaterKontakteListe steuerberater={[KANZLEI]} onNeu={vi.fn()} onBearbeiten={onBearbeiten} onLoeschen={onLoeschen} />);

        const bearbeiten = screen.getByRole('button', { name: 'Kanzlei Mustermann bearbeiten' });
        const loeschen = screen.getByRole('button', { name: 'Kanzlei Mustermann löschen' });
        expect(bearbeiten).toHaveAttribute('title', 'Bearbeiten');
        expect(loeschen).toHaveAttribute('title', 'Löschen');
        await user.click(bearbeiten);
        await user.click(loeschen);
        expect(onBearbeiten).toHaveBeenCalledWith(KANZLEI);
        expect(onLoeschen).toHaveBeenCalledWith(KANZLEI);
    });

    it('zeigt das Datum deutsch und das Kennzeichen für die ganze Kanzlei', () => {
        render(<SteuerberaterKontakteListe steuerberater={[KANZLEI]} onNeu={vi.fn()} onBearbeiten={vi.fn()} onLoeschen={vi.fn()} />);
        expect(screen.getByText('01.03.2026')).toBeInTheDocument();
        expect(screen.getByText('Ganze Kanzlei wird erkannt')).toBeInTheDocument();
    });
});
