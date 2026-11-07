import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { testBaum, testLeistung, TEST_ORDNER } from './testDaten';
import { ordnerAusSuche, useOrdnerAuswahl } from './useOrdnerAuswahl';
import type { Ordner } from './typen';

function zeige(adresse: string, ordnerRoh: Ordner[] = TEST_ORDNER, ladeStatus: 'laedt' | 'fertig' | 'fehler' = 'fertig') {
    const { baum } = testBaum(ordnerRoh);
    const wrapper = ({ children }: { children: ReactNode }) => <MemoryRouter initialEntries={[adresse]}>{children}</MemoryRouter>;
    return renderHook(() => ({ auswahl: useOrdnerAuswahl({ baum, ordnerRoh, ladeStatus }), ort: useLocation() }), { wrapper });
}

describe('ordnerAusSuche', () => {
    it('liest eine positive ganze Zahl, sonst null', () => {
        expect(ordnerAusSuche('ordner=12&reiter=auswertung')).toBe(12);
        expect(ordnerAusSuche('?ordner=12')).toBe(12);
        expect(ordnerAusSuche('ordner=abc')).toBeNull();
        expect(ordnerAusSuche('ordner=-3')).toBeNull();
        expect(ordnerAusSuche('')).toBeNull();
    });
});

describe('useOrdnerAuswahl', () => {
    it('liest Ordner und Reiter aus der Adresse; unbekannter Reiter heißt „Leistungen“', () => {
        expect(zeige('/leistungen?ordner=2&reiter=auswertung').result.current.auswahl).toMatchObject({ gewaehlt: 2, reiter: 'auswertung' });
        expect(zeige('/leistungen?ordner=2&reiter=quatsch').result.current.auswahl.reiter).toBe('leistungen');
        expect(zeige('/leistungen').result.current.auswahl.gewaehlterOrdner).toBeNull();
    });

    it('schreibt die Auswahl in die Adresse – „Leistungen“ ohne Reiter, Übersicht ohne beides', () => {
        const { result } = zeige('/leistungen?ordner=2&reiter=ordner');
        act(() => result.current.auswahl.waehle(4));
        expect(result.current.ort.search).toBe('?ordner=4&reiter=ordner');
        act(() => result.current.auswahl.waehle(4, 'leistungen'));
        expect(result.current.ort.search).toBe('?ordner=4');
        act(() => result.current.auswahl.waehle(null));
        expect(result.current.ort.search).toBe('');
    });

    it('klappt die Kette zum gewählten Ordner einmal auf; danach darf man zuklappen', () => {
        const { result } = zeige('/leistungen?ordner=2');
        expect([...result.current.auswahl.aufgeklappt].sort()).toEqual([1, 2]);
        act(() => result.current.auswahl.aufklappen(1, false));
        expect(result.current.auswahl.aufgeklappt.has(1)).toBe(false);
    });

    it('springt bei unbekanntem Ordner zur Übersicht', async () => {
        const { result } = zeige('/leistungen?ordner=99&reiter=ordner');
        await waitFor(() => expect(result.current.ort.search).toBe(''));
    });

    it('bleibt beim Ordner, solange er nur ausgeblendet ist (Löschen läuft)', () => {
        // Baum ohne Ordner 4 (ausgeblendet), aber in den Rohdaten noch da
        const { baum } = testBaum(TEST_ORDNER.filter((o) => o.id !== 4));
        const wrapper = ({ children }: { children: ReactNode }) => <MemoryRouter initialEntries={['/leistungen?ordner=4']}>{children}</MemoryRouter>;
        const { result } = renderHook(() => ({ a: useOrdnerAuswahl({ baum, ordnerRoh: TEST_ORDNER, ladeStatus: 'fertig' }), ort: useLocation() }), { wrapper });
        expect(result.current.ort.search).toBe('?ordner=4');
    });

    it('wartet mit dem Rücksprung, bis die Daten geladen sind', () => {
        const { result } = zeige('/leistungen?ordner=99', [], 'laedt');
        expect(result.current.ort.search).toBe('?ordner=99');
    });

    it('Leistung öffnen wählt ihren Ordner im Reiter „Leistungen“ und zählt hoch', () => {
        const { result } = zeige('/leistungen?ordner=4&reiter=ordner');
        act(() => result.current.auswahl.leistungOeffnen(testLeistung(11, 'Glasbrüstung', 2)));
        expect(result.current.ort.search).toBe('?ordner=2');
        expect(result.current.auswahl.oeffneLeistung).toEqual({ id: 11, zaehler: 1 });
        act(() => result.current.auswahl.leistungOeffnen(testLeistung(11, 'Glasbrüstung', 2)));
        expect(result.current.auswahl.oeffneLeistung).toEqual({ id: 11, zaehler: 2 });
    });
});
