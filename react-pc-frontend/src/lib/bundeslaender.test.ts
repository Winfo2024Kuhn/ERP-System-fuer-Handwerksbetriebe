import { describe, expect, it } from 'vitest';
import { BUNDESLAENDER, BUNDESLAND_OPTIONEN, STANDARD_BUNDESLAND, bundeslandName } from './bundeslaender';

describe('bundeslaender', () => {
    it('enthält genau die 16 Länder mit eindeutigen Kürzeln aus dem API-Vertrag', () => {
        expect(BUNDESLAENDER).toHaveLength(16);
        expect(new Set(BUNDESLAENDER.map(l => l.kuerzel)).size).toBe(16);
        expect(BUNDESLAENDER.map(l => l.kuerzel)).toEqual(['BW', 'BY', 'BE', 'BB', 'HB', 'HH', 'HE', 'MV', 'NI', 'NW', 'RP', 'SL', 'SN', 'ST', 'SH', 'TH']);
    });

    it('liefert den Klartext zum Kürzel, auch bei Kleinschreibung und Leerzeichen', () => {
        expect(bundeslandName('BY')).toBe('Bayern');
        expect(bundeslandName(' nw ')).toBe('Nordrhein-Westfalen');
        expect(bundeslandName(STANDARD_BUNDESLAND)).toBe('Bayern');
    });

    it('liefert null für leere oder unbekannte Kürzel', () => {
        expect(bundeslandName(null)).toBeNull();
        expect(bundeslandName(undefined)).toBeNull();
        expect(bundeslandName('')).toBeNull();
        expect(bundeslandName('XX')).toBeNull();
    });

    it('baut Auswahl-Optionen mit Kürzel als Wert und Namen als Anzeige', () => {
        expect(BUNDESLAND_OPTIONEN[0]).toEqual({ value: 'BW', label: 'Baden-Württemberg' });
        expect(BUNDESLAND_OPTIONEN).toHaveLength(16);
    });
});
