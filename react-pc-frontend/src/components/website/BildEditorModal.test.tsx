import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { BildEditorModal } from './BildEditorModal';
import { STANDARD_BEARBEITUNG } from './bildbearbeitung';

beforeEach(() => {
    // jsdom liefert keinen 2D-Kontext.
    HTMLCanvasElement.prototype.getContext = vi.fn(() => null) as never;
});

const basis = {
    offen: true,
    bildUrl: '/api/dokumente/baustelle.jpg',
    onAbbrechen: vi.fn(),
    onUebernehmen: vi.fn(),
};

describe('BildEditorModal', () => {
    it('zeigt nichts, solange es geschlossen ist', () => {
        const { container } = render(<BildEditorModal {...basis} offen={false} />);

        expect(container).toBeEmptyDOMElement();
    });

    it('bietet Drehen, Spiegeln und die Seitenverhaeltnisse an', () => {
        render(<BildEditorModal {...basis} />);

        expect(screen.getByRole('button', { name: 'Nach links drehen' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Nach rechts drehen' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Waagerecht spiegeln' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: '16:9' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Frei' })).toBeInTheDocument();
    });

    it('gibt die Drehung nach zweimal rechts als 180 Grad zurueck', async () => {
        const user = userEvent.setup();
        const onUebernehmen = vi.fn();
        render(<BildEditorModal {...basis} onUebernehmen={onUebernehmen} />);

        await user.click(screen.getByRole('button', { name: 'Nach rechts drehen' }));
        await user.click(screen.getByRole('button', { name: 'Nach rechts drehen' }));
        await user.click(screen.getByRole('button', { name: 'Übernehmen' }));

        expect(onUebernehmen).toHaveBeenCalledWith(
            expect.objectContaining({ drehung: 180 }));
    });

    it('dreht ueber 270 hinaus wieder auf 0', async () => {
        const user = userEvent.setup();
        const onUebernehmen = vi.fn();
        render(<BildEditorModal {...basis} onUebernehmen={onUebernehmen} />);

        for (let i = 0; i < 4; i++) {
            await user.click(screen.getByRole('button', { name: 'Nach rechts drehen' }));
        }
        await user.click(screen.getByRole('button', { name: 'Übernehmen' }));

        expect(onUebernehmen).toHaveBeenCalledWith(expect.objectContaining({ drehung: 0 }));
    });

    it('gibt das Spiegeln weiter', async () => {
        const user = userEvent.setup();
        const onUebernehmen = vi.fn();
        render(<BildEditorModal {...basis} onUebernehmen={onUebernehmen} />);

        await user.click(screen.getByRole('button', { name: 'Waagerecht spiegeln' }));
        await user.click(screen.getByRole('button', { name: 'Übernehmen' }));

        expect(onUebernehmen).toHaveBeenCalledWith(expect.objectContaining({ spiegelnX: true }));
    });

    it('setzt mit Zuruecksetzen alles auf den Ausgangszustand', async () => {
        const user = userEvent.setup();
        const onUebernehmen = vi.fn();
        render(<BildEditorModal {...basis} onUebernehmen={onUebernehmen} />);

        await user.click(screen.getByRole('button', { name: 'Nach rechts drehen' }));
        await user.click(screen.getByRole('button', { name: 'Waagerecht spiegeln' }));
        await user.click(screen.getByRole('button', { name: 'Zurücksetzen' }));
        await user.click(screen.getByRole('button', { name: 'Übernehmen' }));

        expect(onUebernehmen).toHaveBeenCalledWith(STANDARD_BEARBEITUNG);
    });

    it('uebernimmt eine vorhandene Bearbeitung beim Oeffnen', async () => {
        const user = userEvent.setup();
        const onUebernehmen = vi.fn();
        render(<BildEditorModal
            {...basis}
            startBearbeitung={{ ...STANDARD_BEARBEITUNG, drehung: 90, helligkeit: 120 }}
            onUebernehmen={onUebernehmen}
        />);

        await user.click(screen.getByRole('button', { name: 'Übernehmen' }));

        expect(onUebernehmen).toHaveBeenCalledWith(
            expect.objectContaining({ drehung: 90, helligkeit: 120 }));
    });

    it('meldet Abbrechen, ohne etwas zu uebernehmen', async () => {
        const user = userEvent.setup();
        const onAbbrechen = vi.fn();
        const onUebernehmen = vi.fn();
        render(<BildEditorModal {...basis} onAbbrechen={onAbbrechen} onUebernehmen={onUebernehmen} />);

        await user.click(screen.getByRole('button', { name: 'Abbrechen' }));

        expect(onAbbrechen).toHaveBeenCalled();
        expect(onUebernehmen).not.toHaveBeenCalled();
    });
});
