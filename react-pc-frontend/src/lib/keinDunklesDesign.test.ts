import { describe, expect, it } from 'vitest';
import tailwindConfig from '../../tailwind.config.js';

/**
 * Das Programm hat kein dunkles Design (Nutzervorgabe 31.10.2026). Früher wurden Aufklappmenüs
 * (E-Mail „…“, KI-Optimierung) schwarz, wenn der Mac auf „Dunkel“ stand, weil Tailwind „dark:“-Klassen
 * nach der Betriebssystem-Einstellung umschaltet.
 */
describe('kein dunkles Design', () => {
    it('Tailwind schaltet dark: nur per Klasse, nicht nach dem Betriebssystem', () => {
        expect(tailwindConfig.darkMode).toBe('class');
    });

    it('keine Komponente bringt dark:-Klassen mit', () => {
        const dateien = import.meta.glob('../**/*.{ts,tsx}', { query: '?raw', import: 'default', eager: true }) as Record<string, string>;
        const mitDark = Object.entries(dateien)
            .filter(([pfad]) => !pfad.endsWith('keinDunklesDesign.test.ts'))
            .filter(([, inhalt]) => /\sdark:[a-z]/.test(inhalt))
            .map(([pfad]) => pfad);
        expect(mitDark).toEqual([]);
    });
});
