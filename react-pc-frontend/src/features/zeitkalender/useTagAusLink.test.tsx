import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type { KalenderTag } from './kalenderTypen';
import { useTagAusLink } from './useTagAusLink';

const tag = (datum: string): KalenderTag => ({ datum, wochentag: 3, istFeiertag: false, feiertagName: null, sollStunden: 8, istStunden: 0, buchungen: [] });
const november = [tag('2026-11-17'), tag('2026-11-18'), tag('2026-11-19')];
const LINK = '/zeitbuchungen?mitarbeiterId=1&jahr=2026&monat=11&tag=2026-11-18&anlass=ueberschneidung&markiert=7,8';

function starte(url: string, start: { tage: KalenderTag[] | null; bereit: boolean; mitarbeiterId?: number }) {
    const oeffneTag = vi.fn();
    const wrapper = ({ children }: { children: ReactNode }) => <MemoryRouter initialEntries={[url]}>{children}</MemoryRouter>;
    const hook = renderHook(
        (p: { tage: KalenderTag[] | null; bereit: boolean; mitarbeiterId?: number }) => ({
            link: useTagAusLink({ mitarbeiterId: p.mitarbeiterId ?? 1, tage: p.tage, bereit: p.bereit, oeffneTag }),
            navigate: useNavigate(),
            location: useLocation(),
        }),
        { wrapper, initialProps: start },
    );
    return { ...hook, oeffneTag };
}

describe('useTagAusLink', () => {
    it('öffnet den Tag aus dem Link genau einmal, sobald der Monat geladen ist', () => {
        const { rerender, oeffneTag } = starte(LINK, { tage: null, bereit: false });
        expect(oeffneTag).not.toHaveBeenCalled();

        rerender({ tage: november, bereit: true });
        expect(oeffneTag).toHaveBeenCalledTimes(1);
        expect(oeffneTag).toHaveBeenCalledWith(november[1]);

        rerender({ tage: [...november], bereit: true });
        expect(oeffneTag).toHaveBeenCalledTimes(1);
    });

    it('liefert die Markierung nur für den verlinkten Tag', () => {
        const { result } = starte(LINK, { tage: november, bereit: true });
        expect(result.current.link.markierungFuer('2026-11-18')).toEqual({ ids: [7, 8], anlass: 'ueberschneidung' });
        expect(result.current.link.markierungFuer('2026-11-19')).toBeUndefined();
    });

    it('öffnet nichts, wenn der Tag nicht im geladenen Monat liegt, und nimmt den Link aus der Adresse', () => {
        const { rerender, result, oeffneTag } = starte(LINK, { tage: [tag('2026-10-01')], bereit: true });
        expect(result.current.location.search).toBe('?mitarbeiterId=1&jahr=2026&monat=11');
        rerender({ tage: november, bereit: true });
        expect(oeffneTag).not.toHaveBeenCalled();
    });

    it('verhält sich bei alten Links ohne Tag wie bisher', () => {
        const { result, oeffneTag } = starte('/zeitbuchungen?mitarbeiterId=1&jahr=2026&monat=11', { tage: november, bereit: true });
        expect(oeffneTag).not.toHaveBeenCalled();
        expect(result.current.link.markierungFuer('2026-11-18')).toBeUndefined();
    });

    it('nimmt beim Beenden nur tag, markiert und anlass aus der Adresse', () => {
        const { result } = starte(LINK, { tage: november, bereit: true });
        act(() => result.current.link.linkBeenden());
        expect(result.current.location.search).toBe('?mitarbeiterId=1&jahr=2026&monat=11');
        expect(result.current.link.markierungFuer('2026-11-18')).toBeUndefined();
    });

    it('öffnet denselben Link erneut, wenn er nach dem Schließen wieder aufgerufen wird', () => {
        const { result, oeffneTag } = starte(LINK, { tage: november, bereit: true });
        act(() => result.current.link.linkBeenden());
        act(() => result.current.navigate(LINK));
        expect(oeffneTag).toHaveBeenCalledTimes(2);
    });

    it('öffnet einen neuen Link für einen anderen Tag, auch wenn schon einer offen ist', () => {
        const { result, oeffneTag } = starte(LINK, { tage: november, bereit: true });
        act(() => result.current.navigate(LINK.replace('2026-11-18', '2026-11-19')));
        expect(oeffneTag).toHaveBeenLastCalledWith(november[2]);
    });

    it('wartet bei einem Mitarbeiterwechsel, bis dessen Monat geladen ist', () => {
        const { rerender, result, oeffneTag } = starte(LINK, { tage: november, bereit: true });
        act(() => result.current.navigate(LINK.replace('mitarbeiterId=1', 'mitarbeiterId=2')));
        rerender({ tage: november, bereit: false, mitarbeiterId: 2 });
        expect(oeffneTag).toHaveBeenCalledTimes(1);
        rerender({ tage: november, bereit: true, mitarbeiterId: 2 });
        expect(oeffneTag).toHaveBeenCalledTimes(2);
    });
});
