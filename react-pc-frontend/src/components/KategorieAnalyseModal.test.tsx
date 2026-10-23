import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { KategorieAnalyseModal } from './KategorieAnalyseModal';

vi.mock('chart.js', () => {
    class Chart { destroy() {} }
    (Chart as unknown as { register: () => void }).register = () => {};
    return { Chart, registerables: [] };
});

describe('KategorieAnalyseModal', () => {
    it('zeigt Titel und Analyse und schließt per Knopf', async () => {
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve({
            projektAnzahl: 0, durchschnittlicheZeit: 0, fixzeit: 0, steigung: 0, verrechnungseinheit: 'Stück',
            rQuadrat: 0, residualStdAbweichung: 0, datenpunkte: 0, arbeitsgangAnalysen: [], projekte: [],
        }) });
        vi.stubGlobal('fetch', fetchMock);
        const onClose = vi.fn();
        render(<KategorieAnalyseModal kategorie={{ id: 3, bezeichnung: 'Balkon', leaf: true }} onClose={onClose} />);
        expect(screen.getByRole('dialog', { name: 'Analyse: Balkon' })).toBeInTheDocument();
        await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/produktkategorien/3/analyse'));
        screen.getByRole('button', { name: 'Fenster schließen' }).click();
        expect(onClose).toHaveBeenCalled();
        vi.unstubAllGlobals();
    });
});
