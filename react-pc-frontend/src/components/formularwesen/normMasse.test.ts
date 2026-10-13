import { describe, expect, it } from 'vitest';
import type { FormBlock } from '../../types';
import {
    PT_JE_MM, STANDARD_BRIEFFORM, anschriftImFenster, anschriftInsFenster, findeAnschriftBlock, formatiereMm,
    infoblockPosition, mmZuPt, mmZuPtGerundet, normMasse, pruefeAnschrift, ptZuMm, rechteckInPt,
} from './normMasse';

const block = (teil: Partial<FormBlock>): FormBlock => ({
    id: 'b', type: 'text', page: 1, x: 0, y: 0, z: 1, width: 100, height: 50, ...teil,
});

describe('Umrechnung mm ↔ pt', () => {
    it('rechnet in PDF-Punkten (1 pt = 1/72 Zoll, A4 ≈ 595 × 842 pt)', () => {
        expect(PT_JE_MM).toBeCloseTo(2.8346, 4);
        expect(mmZuPt(210)).toBeCloseTo(595.28, 2);
        expect(ptZuMm(842)).toBeCloseTo(297.0, 1);
        expect(ptZuMm(mmZuPt(45))).toBeCloseTo(45, 10);
    });

    it('rundet mm-Eingaben auf zwei Nachkommastellen in pt', () => {
        expect(mmZuPtGerundet(20)).toBe(56.69);
        expect(formatiereMm(mmZuPtGerundet(20))).toBe('20,0');
    });

    it('zeigt mm mit einer Nachkommastelle und Komma', () => {
        expect(formatiereMm(mmZuPt(27.3))).toBe('27,3');
        expect(formatiereMm(0)).toBe('0,0');
    });
});

describe('normMasse', () => {
    it('Form B: Anschriftfeld 20/45 mm, 85 × 45 mm, Zonen 17,7 + 27,3 mm, Falz 105/210, Loch 148,5', () => {
        const m = normMasse('B');
        expect(m.anschriftfeld).toEqual({ x: 20, y: 45, breite: 85, hoehe: 45 });
        expect(m.zusatzzone).toEqual({ x: 20, y: 45, breite: 85, hoehe: 17.7 });
        expect(m.anschriftzone.y).toBeCloseTo(62.7, 10);
        expect(m.anschriftzone.hoehe).toBeCloseTo(27.3, 10);
        expect(m.fenster).toEqual({ x: 20, y: 45, breite: 90, hoehe: 45 });
        expect(m.infoblock).toEqual({ x: 125, y: 50, breite: 75, hoehe: 40 });
        expect(m.falzmarken).toEqual([105, 210]);
        expect(m.lochmarke).toBe(148.5);
    });

    it('Form A: Anschriftfeld bei 27 mm, Falz 87/192, Infoblock ab 32 mm', () => {
        const m = normMasse('A');
        expect(m.anschriftfeld.y).toBe(27);
        expect(m.anschriftzone.y).toBeCloseTo(44.7, 10);
        expect(m.fenster.y).toBe(27);
        expect(m.infoblock).toEqual({ x: 125, y: 32, breite: 75, hoehe: 40 });
        expect(m.falzmarken).toEqual([87, 192]);
        expect(m.lochmarke).toBe(148.5);
    });

    it('liefert eine Kopie der Falzmarken (keine geteilte Liste)', () => {
        normMasse('B').falzmarken.push(1);
        expect(normMasse('B').falzmarken).toEqual([105, 210]);
    });

    it('Standard ist Form B', () => {
        expect(STANDARD_BRIEFFORM).toBe('B');
    });

    it('rechnet Rechtecke in pt um', () => {
        const r = rechteckInPt({ x: 210, y: 10, breite: 105, hoehe: 0 });
        expect(r.x).toBeCloseTo(595.28, 2);
        expect(r.y).toBeCloseTo(28.35, 2);
        expect(r.width).toBeCloseTo(297.64, 2);
        expect(r.height).toBe(0);
    });
});

describe('findeAnschriftBlock', () => {
    it('nimmt das Kundenadresse-Feld auf Seite 1', () => {
        const adresse = block({ id: 'a', type: 'adresse' });
        expect(findeAnschriftBlock([block({ id: 't', content: '{{KUNDENADRESSE}}' }), adresse])).toBe(adresse);
    });

    it('sonst den Text mit {{KUNDENADRESSE}}', () => {
        const text = block({ id: 't', content: 'Max Mustermann\n{{KUNDENADRESSE}}' });
        expect(findeAnschriftBlock([block({ id: 'x', content: 'Hallo' }), text])).toBe(text);
    });

    it('ignoriert Folgeseiten, Tabellen, Logos und leere Texte', () => {
        expect(findeAnschriftBlock([
            block({ type: 'adresse', page: 2 }),
            block({ type: 'table', content: '{{KUNDENADRESSE}}' }),
            block({ type: 'logo', content: '{{KUNDENADRESSE}}' }),
            block({ content: undefined }),
        ])).toBeNull();
    });
});

describe('anschriftImFenster', () => {
    it.each(['A', 'B'] as const)('Form %s: die Norm-Position liegt im Fenster', form => {
        expect(anschriftImFenster(anschriftInsFenster(form), form)).toBe(true);
    });

    it('genau die Zone (Kommawerte) passt', () => {
        expect(anschriftImFenster(rechteckInPt(normMasse('B').anschriftzone), 'B')).toBe(true);
    });

    it('verträgt einen halben pt Rundung, aber nicht mehr', () => {
        const zone = rechteckInPt(normMasse('B').anschriftzone);
        expect(anschriftImFenster({ ...zone, x: zone.x - 0.5 }, 'B')).toBe(true);
        expect(anschriftImFenster({ ...zone, x: zone.x - 0.6 }, 'B')).toBe(false);
        expect(anschriftImFenster({ ...zone, y: zone.y - 0.6 }, 'B')).toBe(false);
        expect(anschriftImFenster({ ...zone, width: zone.width + 0.6 }, 'B')).toBe(false);
        expect(anschriftImFenster({ ...zone, height: zone.height + 0.6 }, 'B')).toBe(false);
    });

    it('Position für Form B ist bei Form A zu tief', () => {
        expect(anschriftImFenster(anschriftInsFenster('B'), 'A')).toBe(false);
        expect(anschriftImFenster(anschriftInsFenster('A'), 'B')).toBe(false);
    });
});

describe('pruefeAnschrift', () => {
    it('keine Anschrift', () => {
        expect(pruefeAnschrift([block({ content: 'Hallo' })], 'B')).toBe('keine-anschrift');
    });

    it('passt', () => {
        expect(pruefeAnschrift([block({ type: 'adresse', ...anschriftInsFenster('B') })], 'B')).toBe('passt');
    });

    it('reicht in die Zusatz- und Vermerkzone, liegt aber im Feld', () => {
        const feld = rechteckInPt(normMasse('B').anschriftfeld);
        expect(pruefeAnschrift([block({ type: 'adresse', x: Math.ceil(feld.x), y: Math.ceil(feld.y), width: 200, height: 100 })], 'B')).toBe('zusatzzone');
    });

    it('ragt heraus', () => {
        expect(pruefeAnschrift([block({ type: 'adresse', x: 24, y: 148, width: 280, height: 140 })], 'B')).toBe('ragt-heraus');
    });
});

describe('anschriftInsFenster / infoblockPosition', () => {
    it('Form B: ganze pt, nach innen gerundet', () => {
        expect(anschriftInsFenster('B')).toEqual({ x: 57, y: 178, width: 240, height: 77 });
    });

    it('Form A liegt 18 mm höher', () => {
        expect(anschriftInsFenster('A')).toEqual({ x: 57, y: 127, width: 240, height: 77 });
    });

    it('Infoblock B ab 125/50 mm, A ab 125/32 mm', () => {
        expect(infoblockPosition('B')).toEqual({ x: 355, y: 142, width: 212, height: 113 });
        expect(infoblockPosition('A').y).toBe(91);
    });
});
