import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { GROESSE_MAX, GROESSE_MIN, normalisiereDarstellung, useDarstellung } from './useDarstellung';

const SCHLUESSEL = 'erp.gemerkt.firmendokumente.ansicht';

describe('normalisiereDarstellung', () => {
    it('nimmt gültige Werte und rundet die Größe', () => {
        expect(normalisiereDarstellung({ art: 'vorschau', groesse: 200.4 })).toEqual({ art: 'vorschau', groesse: 200 });
        expect(normalisiereDarstellung({ art: 'liste', groesse: 96 })).toEqual({ art: 'liste', groesse: 96 });
    });

    it('ist robust gegen Unsinn im Speicher', () => {
        expect(normalisiereDarstellung(null)).toEqual({ art: 'liste', groesse: 160 });
        expect(normalisiereDarstellung('vorschau')).toEqual({ art: 'liste', groesse: 160 });
        expect(normalisiereDarstellung({ art: 'kacheln', groesse: 'gross' })).toEqual({ art: 'liste', groesse: 160 });
        expect(normalisiereDarstellung({ art: 'vorschau', groesse: Number.NaN })).toEqual({ art: 'vorschau', groesse: 160 });
        expect(normalisiereDarstellung({ art: 'vorschau', groesse: 5000 })).toEqual({ art: 'vorschau', groesse: GROESSE_MAX });
        expect(normalisiereDarstellung({ art: 'vorschau', groesse: -3 })).toEqual({ art: 'vorschau', groesse: GROESSE_MIN });
    });
});

describe('useDarstellung', () => {
    it('startet mit der Liste und merkt sich die Ansicht im Browser', () => {
        const { result } = renderHook(() => useDarstellung());
        expect(result.current.art).toBe('liste');
        act(() => result.current.setArt('vorschau'));
        expect(result.current.art).toBe('vorschau');
        expect(JSON.parse(localStorage.getItem(SCHLUESSEL) ?? '')).toEqual({ art: 'vorschau', groesse: 160 });
    });

    it('zeigt die Größe beim Ziehen sofort, speichert sie aber erst beim Loslassen', () => {
        localStorage.setItem(SCHLUESSEL, JSON.stringify({ art: 'vorschau', groesse: 120 }));
        const { result } = renderHook(() => useDarstellung());
        expect(result.current.groesse).toBe(120);
        act(() => result.current.setGroesse(250));
        expect(result.current.groesse).toBe(250);
        expect(JSON.parse(localStorage.getItem(SCHLUESSEL) ?? '').groesse).toBe(120);
        act(() => result.current.groesseMerken(260));
        expect(result.current.groesse).toBe(260);
        expect(JSON.parse(localStorage.getItem(SCHLUESSEL) ?? '')).toEqual({ art: 'vorschau', groesse: 260 });
    });

    it('verwirft kaputte Einträge', () => {
        localStorage.setItem(SCHLUESSEL, '{kaputt');
        const { result } = renderHook(() => useDarstellung());
        expect(result.current).toMatchObject({ art: 'liste', groesse: 160 });
    });
});
