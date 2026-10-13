import { describe, expect, it } from 'vitest';
import { Hash, Image, MapPin, Table2, Type } from 'lucide-react';
import type { FormBlock } from '../../types';
import { BAUSTEINE, bausteinIcon, bausteinName, istBausteinArt, neuerBaustein } from './bausteinVorlagen';
import { anschriftImFenster, pruefeAnschrift } from './normMasse';

const vorhanden: FormBlock[] = [{ id: 'a', type: 'text', page: 1, x: 0, y: 0, z: 7, width: 10, height: 10 }];

describe('neuerBaustein', () => {
    it.each(['A', 'B'] as const)('„Anschrift“ landet bei Form %s direkt im Fenster', form => {
        const b = neuerBaustein('anschrift', { seite: 1, items: vorhanden, form });
        expect(b.type).toBe('text');
        expect(b.content).toBe('{{KUNDENADRESSE}}');
        expect(anschriftImFenster(b, form)).toBe(true);
        expect(pruefeAnschrift([b], form)).toBe('passt');
    });

    it('liegt über allen anderen Bausteinen und auf der gewählten Seite', () => {
        const b = neuerBaustein('text', { seite: 2, items: vorhanden, form: 'B' });
        expect(b.z).toBe(8);
        expect(b.page).toBe(2);
        expect(neuerBaustein('text', { seite: 1, items: [], form: 'B' }).z).toBe(1);
    });

    it('beim Ablegen mittig unter dem Mauszeiger', () => {
        const b = neuerBaustein('logo', { seite: 1, items: [], form: 'B', mitte: { x: 300, y: 400 } });
        expect(b).toMatchObject({ type: 'logo', x: 200, y: 340, width: 200, height: 120, content: '/image001.png' });
    });

    it('legt alle Arten mit sinnvollem Inhalt an', () => {
        const art = (a: Parameters<typeof neuerBaustein>[0]) => neuerBaustein(a, { seite: 1, items: [], form: 'B' });
        expect(art('infoblock').content).toContain('{{DOKUMENTNUMMER}}');
        expect(art('seitenzahl')).toMatchObject({ content: 'Seite {{SEITENZAHL}}', styles: { fontSize: 9 } });
        expect(art('tabelle')).toMatchObject({ type: 'table', width: 560, height: 220 });
        expect(art('text')).toMatchObject({ content: 'Neuer Text', x: 178, y: 391 });
        expect(new Set(BAUSTEINE.map(b => art(b.art).id)).size).toBe(BAUSTEINE.length);
    });

    it('erkennt gültige Bausteinarten beim Ablegen', () => {
        expect(istBausteinArt('anschrift')).toBe(true);
        expect(istBausteinArt('<script>')).toBe(false);
    });
});

describe('bausteinName / bausteinIcon', () => {
    it.each([
        [{ type: 'table' }, 'Positionen', Table2],
        [{ type: 'logo' }, 'Logo / Bild', Image],
        [{ type: 'adresse' }, 'Anschrift', MapPin],
        [{ type: 'seitenzahl' }, 'Seitenzahl', undefined],
        [{ type: 'text', content: 'An\n{{KUNDENADRESSE}}' }, 'Anschrift', MapPin],
        [{ type: 'text', content: 'Seite {{SEITENZAHL}}' }, 'Seitenzahl', undefined],
        [{ type: 'text', content: 'Nr. {{DOKUMENTNUMMER}}' }, 'Nummer & Datum', Hash],
        [{ type: 'text', content: 'Datum {{DATUM}}' }, 'Nummer & Datum', Hash],
        [{ type: 'heading', content: 'Hallo' }, 'Überschrift', Type],
        [{ type: 'text' }, 'Text', Type],
        [{ type: 'datum' }, 'Datum', Type],
        [{ type: 'unbekannt' }, 'Baustein', Type],
    ] as const)('%o → %s', (block, name, icon) => {
        expect(bausteinName(block as FormBlock)).toBe(name);
        if (icon) expect(bausteinIcon(block as FormBlock)).toBe(icon);
    });
});
