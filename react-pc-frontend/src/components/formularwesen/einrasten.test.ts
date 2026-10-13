import { describe, expect, it } from 'vitest';
import type { FormBlock } from '../../types';
import { einrastZiele, rasteEin, rasteWert } from './einrasten';
import { anschriftInsFenster, mmZuPt } from './normMasse';

const block = (teil: Partial<FormBlock>): FormBlock => ({ id: 'b', type: 'text', page: 1, x: 0, y: 0, z: 1, width: 100, height: 50, ...teil });

describe('einrastZiele', () => {
    it('Seitenränder und -mitte, andere Bausteine derselben Seite – ohne den Baustein selbst', () => {
        const ziele = einrastZiele([block({ id: 'ich', x: 5 }), block({ id: 'a', x: 100, y: 200 }), block({ id: 'z', page: 2, x: 333 })], 'ich', 1, null);
        expect(ziele.senkrecht).toEqual([0, 297.5, 595, 100, 150, 200]);
        expect(ziele.waagerecht).toEqual([0, 421, 842, 200, 225, 250]);
    });

    it('mit Norm-Hilfslinien auch Anschriftzone, Anschriftfeld und Infoblock – nur auf Seite 1', () => {
        const mitNorm = einrastZiele([], 'x', 1, 'B');
        expect(mitNorm.senkrecht).toContain(mmZuPt(20));
        expect(mitNorm.waagerecht).toContain(mmZuPt(62.7));
        expect(mitNorm.senkrecht).toContain(mmZuPt(125));
        expect(einrastZiele([], 'x', 2, 'B').senkrecht).toHaveLength(3);
    });
});

describe('rasteEin', () => {
    const ziele = { senkrecht: [0, 297.5, 595], waagerecht: [0, 421, 842] };

    it('springt mit der linken Kante an den Seitenrand und zeigt die Linie', () => {
        expect(rasteEin(4, 300, 100, 50, ziele, 6)).toEqual({ x: 0, y: 300, linien: { senkrecht: [0], waagerecht: [] } });
    });

    it('springt mit der Mitte an die Seitenmitte', () => {
        expect(rasteEin(245, 394, 100, 50, ziele, 6)).toMatchObject({ x: 247.5, y: 396, linien: { senkrecht: [297.5], waagerecht: [421] } });
    });

    it('springt mit der rechten/unteren Kante an den Rand', () => {
        expect(rasteEin(493, 790, 100, 50, ziele, 6)).toMatchObject({ x: 495, y: 792 });
    });

    it('außerhalb des Fangbereichs: ganze pt, keine Linien', () => {
        expect(rasteEin(100.4, 200.6, 100, 50, ziele, 6)).toEqual({ x: 100, y: 201, linien: { senkrecht: [], waagerecht: [] } });
    });

    it('rastet genau auf die Anschriftzone ein', () => {
        const zone = anschriftInsFenster('B');
        const ergebnis = rasteEin(zone.x - 1, zone.y - 2, zone.width, zone.height, einrastZiele([], 'x', 1, 'B'), 6);
        expect(ergebnis.x).toBeCloseTo(mmZuPt(20), 2);
        expect(ergebnis.y).toBeCloseTo(mmZuPt(62.7), 2);
    });
});

describe('rasteWert', () => {
    it('zieht eine Kante an die nächste Linie', () => {
        expect(rasteWert(103, [0, 100, 105], 6)).toEqual({ wert: 105, linie: 105 });
    });

    it('ohne Linie im Fangbereich: gerundet', () => {
        expect(rasteWert(50.6, [0, 100], 6)).toEqual({ wert: 51, linie: null });
    });
});
