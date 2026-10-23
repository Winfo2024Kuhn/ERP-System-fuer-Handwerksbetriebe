import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { LeistungLoeschenDialog } from './LeistungLoeschenDialog';
import type { LeistungVerwendung } from './typen';

const VERWENDUNG: LeistungVerwendung = {
    dokumentAnzahl: 23,
    dokumente: [
        { id: 77, dokumentNummer: 'AN-2026-031', typ: 'ANGEBOT', datum: '2026-03-02', projektId: 9, bauvorhaben: 'Neubau Mustermann' },
        { id: 78, dokumentNummer: 'AB-2026-012', typ: 'AUFTRAGSBESTAETIGUNG', datum: '2026-03-10', projektId: 9, bauvorhaben: 'Neubau Mustermann' },
        { id: 79, dokumentNummer: null, typ: 'RECHNUNG', datum: null, projektId: null, bauvorhaben: null },
    ],
};

describe('LeistungLoeschenDialog', () => {
    it('listet die Dokumente in Alltagssprache mit „und N weitere“ und erklärt die Folgen', () => {
        render(<ToastProvider><LeistungLoeschenDialog name="Glasbrüstung" verwendung={VERWENDUNG} onAbbrechen={vi.fn()} onLoeschen={vi.fn()} /></ToastProvider>);
        expect(screen.getByRole('dialog', { name: '„Glasbrüstung“ steckt in 23 Dokumenten' })).toBeInTheDocument();
        expect(screen.getByText('Angebot')).toBeInTheDocument();
        expect(screen.getByText('Auftragsbestätigung')).toBeInTheDocument();
        expect(screen.getByText('02.03.2026')).toBeInTheDocument();
        expect(screen.getByText('und 20 weitere')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Mehr zu: Leistung löschen' })).toBeInTheDocument();
    });

    it('Einzahl im Titel und „Trotzdem löschen“ ruft das Löschen auf', async () => {
        const onLoeschen = vi.fn().mockResolvedValue(undefined);
        render(<ToastProvider><LeistungLoeschenDialog name="Glasbrüstung" verwendung={{ dokumentAnzahl: 1, dokumente: VERWENDUNG.dokumente.slice(0, 1) }}
            onAbbrechen={vi.fn()} onLoeschen={onLoeschen} /></ToastProvider>);
        expect(screen.getByRole('dialog', { name: '„Glasbrüstung“ steckt in einem Dokument' })).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Trotzdem löschen' }));
        expect(onLoeschen).toHaveBeenCalledTimes(1);
    });

    it('„Abbrechen“ löscht nichts; ein Fehler erscheint im Dialog', async () => {
        const onAbbrechen = vi.fn();
        const onLoeschen = vi.fn().mockRejectedValue(new Error('Die Leistung gibt es nicht mehr.'));
        render(<ToastProvider><LeistungLoeschenDialog name="Glasbrüstung" verwendung={VERWENDUNG} onAbbrechen={onAbbrechen} onLoeschen={onLoeschen} /></ToastProvider>);
        await userEvent.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(onAbbrechen).toHaveBeenCalled();
        await userEvent.click(screen.getByRole('button', { name: 'Trotzdem löschen' }));
        await waitFor(() => expect(screen.getAllByText('Die Leistung gibt es nicht mehr.').length).toBeGreaterThan(0));
    });
});
