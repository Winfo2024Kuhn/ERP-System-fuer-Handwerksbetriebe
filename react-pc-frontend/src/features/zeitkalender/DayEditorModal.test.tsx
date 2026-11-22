import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DayEditorModal, type TagMarkierung } from './DayEditorModal';
import type { Buchung, KalenderTag } from './kalenderTypen';

vi.mock('../../components/ui/toast', () => ({ useToast: () => ({ error: vi.fn(), success: vi.fn(), warning: vi.fn(), info: vi.fn() }) }));
const { bestaetige } = vi.hoisted(() => ({ bestaetige: vi.fn() }));
vi.mock('../../components/ui/confirm-dialog', () => ({ useConfirm: () => bestaetige }));

const buchung = (id: number, start: string, ende: string): Buchung => ({
    id, projektId: 1, arbeitsgangId: 1, projektName: 'Testprojekt', arbeitsgangName: 'Montage',
    startZeit: start, endeZeit: ende, dauerMinuten: null, dauerFormatiert: null, notiz: '', typ: 'ARBEIT',
});
const tag: KalenderTag = {
    datum: '2026-11-18', wochentag: 3, istFeiertag: false, feiertagName: null, sollStunden: 8, istStunden: 4,
    buchungen: [buchung(1, '07:00', '11:27'), buchung(2, '11:27', '12:00'), buchung(3, '13:34', '16:00')],
};

const zeige = (markierung?: TagMarkierung, readOnly = false) => render(
    <DayEditorModal tag={tag} mitarbeiterId={1} projekte={[]} arbeitsgaenge={[]} onClose={vi.fn()} readOnly={readOnly} markierung={markierung} />
);

let scrollIntoView: ReturnType<typeof vi.fn>;
beforeEach(() => {
    bestaetige.mockReset().mockResolvedValue(false);
    scrollIntoView = vi.fn();
    Element.prototype.scrollIntoView = scrollIntoView as unknown as Element['scrollIntoView'];
});
afterEach(() => {
    delete (Element.prototype as Partial<Element>).scrollIntoView;
});

describe('DayEditorModal: Markierung aus einer Meldung', () => {
    it('hebt nur die markierten Buchungen hervor, mit Text statt nur Farbe', () => {
        const { container } = zeige({ ids: [2, 3], anlass: 'ueberschneidung' });
        const markiert = container.querySelectorAll('[data-markiert]');
        expect(markiert).toHaveLength(2);
        expect(within(markiert[0] as HTMLElement).getByText('Aus Meldung')).toBeVisible();
        expect(within(markiert[0] as HTMLElement).getByRole('textbox', { name: 'Von Buchung 2' })).toHaveValue('11:27');
        expect(screen.getAllByText('Aus Meldung')).toHaveLength(2);
    });

    it('holt die erste markierte Zeile in den sichtbaren Bereich', () => {
        const { container } = zeige({ ids: [3], anlass: null });
        expect(scrollIntoView).toHaveBeenCalledTimes(1);
        expect(scrollIntoView.mock.contexts[0]).toBe(container.querySelector('[data-markiert]'));
    });

    it('zeigt das Hinweisband zum Anlass mit Erklärung im Info-Knopf', () => {
        zeige({ ids: [2], anlass: 'ohne-feierabend' });
        const band = screen.getByRole('note', { name: 'Zeiten ohne Feierabend' });
        expect(within(band).getByText('Hier fehlt ein Feierabend. Das Ende wurde automatisch gesetzt.')).toBeVisible();
        fireEvent.click(within(band).getByRole('button', { name: 'Mehr zu: Zeiten ohne Feierabend' }));
        expect(screen.getByText(/beendet das Programm die Buchung abends automatisch/)).toBeVisible();
        expect(screen.getByText(/zählen diese Stunden doppelt/)).toBeVisible();
    });

    it('markiert ohne Anlass nur, ohne Hinweisband', () => {
        zeige({ ids: [2], anlass: null });
        expect(screen.queryByRole('note')).not.toBeInTheDocument();
        expect(screen.getByText('Aus Meldung')).toBeVisible();
    });

    it('zeigt ohne Meldung weder Markierung noch Band und scrollt nicht', () => {
        zeige();
        expect(screen.queryByText('Aus Meldung')).not.toBeInTheDocument();
        expect(screen.queryByRole('note')).not.toBeInTheDocument();
        expect(scrollIntoView).not.toHaveBeenCalled();
    });

    it('markiert auch im abgeschlossenen Monat, die Felder bleiben gesperrt', () => {
        zeige({ ids: [2], anlass: 'ueberschneidung' }, true);
        expect(screen.getByRole('note', { name: 'Überschneidung Handy/Büro' })).toBeVisible();
        expect(screen.getByRole('textbox', { name: 'Von Buchung 2' })).toBeDisabled();
        expect(screen.getByText('Aus Meldung')).toBeVisible();
    });
});

describe('DayEditorModal: Löschen', () => {
    it('nennt Zeitraum und Projekt am Löschen-Knopf und in der Rückfrage', async () => {
        zeige();
        fireEvent.click(screen.getByRole('button', { name: 'Buchung 07:00–11:27 löschen' }));
        await waitFor(() => expect(bestaetige).toHaveBeenCalledWith(expect.objectContaining({
            title: 'Buchung löschen?',
            message: 'Buchung 07:00–11:27 (Testprojekt) löschen? Die Buchung verschwindet aus dem Kalender, im Änderungsprotokoll bleibt sie sichtbar.',
            confirmLabel: 'Buchung löschen',
            variant: 'danger',
        })));
    });

    it('sagt bei Abwesenheiten, dass das Löschen endgültig ist', async () => {
        render(<DayEditorModal
            tag={{ ...tag, buchungen: [{ ...buchung(-4, '00:00', ''), endeZeit: null, typ: 'URLAUB', abwesenheitId: 4, projektName: 'URLAUB' }] }}
            mitarbeiterId={1} projekte={[]} arbeitsgaenge={[]} onClose={vi.fn()}
        />);
        fireEvent.click(screen.getByRole('button', { name: 'Urlaub löschen' }));
        await waitFor(() => expect(bestaetige).toHaveBeenCalledWith(expect.objectContaining({
            title: 'Urlaub löschen?',
            message: 'Urlaub am 18.11.2026 löschen? Das lässt sich nicht rückgängig machen.',
            confirmLabel: 'Urlaub löschen',
            variant: 'danger',
        })));
    });
});
