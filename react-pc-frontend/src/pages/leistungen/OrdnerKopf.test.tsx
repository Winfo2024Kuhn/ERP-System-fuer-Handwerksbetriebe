import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { OrdnerKopf } from './OrdnerKopf';
import { baueBaum } from './ordnerLogik';
import type { Ordner } from './typen';

const ordner = (id: number, name: string, parentId: number | null, bildUrl: string | null = null): Ordner => ({
    id, name, parentId, bildUrl, beschreibung: '', einheit: 'STUECK', einheitAnzeige: 'Stück', projektAnzahl: 0,
});

const METALLBAU = ordner(1, 'Metallbau', null);
const GELAENDER = ordner(2, 'Geländer', 1, '/api/images/gelaender.png');
const EDELSTAHL = ordner(3, 'Edelstahl', 2, 'javascript:alert(1)');
const BAUM = baueBaum([METALLBAU, GELAENDER, EDELSTAHL]);

function zeige(o: Ordner) {
    const handler = {
        onWaehlen: vi.fn(), onUnterordner: vi.fn(), onUmbenennen: vi.fn(), onVerschieben: vi.fn(), onLoeschen: vi.fn(),
    };
    render(<OrdnerKopf ordner={o} baum={BAUM} {...handler} />);
    return handler;
}

describe('OrdnerKopf', () => {
    it('zeigt Bild, Name und klickbaren Pfad', async () => {
        const h = zeige(GELAENDER);
        expect(screen.getByRole('heading', { name: 'Geländer' })).toBeInTheDocument();
        expect(screen.getByRole('img', { name: 'Bild von „Geländer“' })).toHaveAttribute('src', '/api/images/gelaender.png');

        const pfad = screen.getByRole('navigation', { name: 'Pfad' });
        await userEvent.click(screen.getByRole('button', { name: 'Metallbau' }));
        expect(h.onWaehlen).toHaveBeenCalledWith(1);
        await userEvent.click(screen.getByRole('button', { name: 'Alle Ordner' }));
        expect(h.onWaehlen).toHaveBeenCalledWith(null);
        expect(pfad).not.toHaveTextContent('Geländer');
    });

    it('zeigt unsichere Bild-Adressen nicht an', () => {
        zeige(EDELSTAHL);
        expect(screen.queryByRole('img')).not.toBeInTheDocument();
    });

    it('Knöpfe und Menü rufen die Aktionen auf', async () => {
        const h = zeige(METALLBAU);
        await userEvent.click(screen.getByRole('button', { name: /Unterordner/ }));
        expect(h.onUnterordner).toHaveBeenCalled();

        for (const [eintrag, handler] of [
            ['Umbenennen', h.onUmbenennen], ['Verschieben nach …', h.onVerschieben], ['Löschen …', h.onLoeschen],
        ] as const) {
            await userEvent.click(screen.getByRole('button', { name: 'Weitere Aktionen für „Metallbau“' }));
            await userEvent.click(await screen.findByRole('menuitem', { name: eintrag }));
            expect(handler).toHaveBeenCalledTimes(1);
        }
    });
});
