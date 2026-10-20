import { describe, expect, it } from 'vitest';
import { baueTeilGutschriftPositionen, gutschriftPositionsTitel, heuteIso } from './gutschriftPositionen';
import { calculateNetto } from './document-editor/helpers';

describe('baueTeilGutschriftPositionen', () => {
    it('erzeugt genau eine Leistungsposition im Format des Dokument-Editors', () => {
        const json = baueTeilGutschriftPositionen('RE-2026-0042', '  Preisnachlass wegen Kratzer  ', 119, 'fest-1');

        expect(JSON.parse(json)).toEqual({
            blocks: [{
                id: 'fest-1',
                type: 'SERVICE',
                pos: '1',
                title: 'Gutschrift zu Rechnung RE-2026-0042: Preisnachlass wegen Kratzer',
                description: '',
                quantity: 1,
                unit: 'psch',
                price: 119,
                fontSize: 10,
                fett: false,
                optional: false,
            }],
            globalRabatt: 0,
        });
    });

    it('rechnet im Editor genau den eingegebenen Netto-Betrag (positiv)', () => {
        const { blocks } = JSON.parse(baueTeilGutschriftPositionen('RE-1', 'Nachlass', 1234.5));
        expect(calculateNetto(blocks)).toBe(1234.5);
    });

    it('trägt den Betrag immer positiv ein – das Minus setzt der Server', () => {
        const { blocks } = JSON.parse(baueTeilGutschriftPositionen('RE-1', 'Nachlass', -50));
        expect(blocks[0].price).toBe(50);
    });

    it('vergibt ohne festen Schlüssel eine eigene Block-ID', () => {
        const a = JSON.parse(baueTeilGutschriftPositionen('RE-1', 'x', 1)).blocks[0].id;
        const b = JSON.parse(baueTeilGutschriftPositionen('RE-1', 'x', 1)).blocks[0].id;
        expect(a).toBeTruthy();
        expect(a).not.toBe(b);
    });
});

describe('gutschriftPositionsTitel', () => {
    it('nennt Rechnung und Grund', () => {
        expect(gutschriftPositionsTitel('RE-7', 'Reklamation')).toBe('Gutschrift zu Rechnung RE-7: Reklamation');
    });
});

describe('heuteIso', () => {
    it('nimmt das Ortsdatum mit führenden Nullen', () => {
        expect(heuteIso(new Date(2026, 0, 5, 0, 30))).toBe('2026-01-05');
        expect(heuteIso(new Date(2026, 11, 31, 23, 59))).toBe('2026-12-31');
    });
});
