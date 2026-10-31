import { describe, expect, it } from 'vitest';
import {
    UNBEKANNT_LABEL,
    chipDarstellung,
    chipHtmlZuKuerzel,
    findePlatzhalter,
    kuerzelZuChipHtml,
    platzhalterNachGruppe,
    verwendeteKuerzel,
} from './platzhalter';
import { TEST_KATALOG } from './__tests__/testdaten';

function chips(html: string) {
    const wurzel = document.createElement('template');
    wurzel.innerHTML = html;
    return Array.from(wurzel.content.querySelectorAll<HTMLElement>('[data-platzhalter-chip]'));
}

describe('findePlatzhalter', () => {
    it('ordnet Kürzel ohne Rücksicht auf Groß-/Kleinschreibung und Leerraum zu', () => {
        expect(findePlatzhalter(TEST_KATALOG, '{{Kundenname}}')?.label).toBe('Kundenname');
        expect(findePlatzhalter(TEST_KATALOG, '{{ kundenname }}')?.label).toBe('Kundenname');
        expect(findePlatzhalter(TEST_KATALOG, '{{ANREDE}}')?.label).toBe('Anrede');
        expect(findePlatzhalter(TEST_KATALOG, '{{Irgendwas}}')).toBeUndefined();
    });
});

describe('kuerzelZuChipHtml', () => {
    it('macht aus bekannten Kürzeln Chips mit Bezeichnung und Beispiel', () => {
        const html = kuerzelZuChipHtml('<p>Hallo {{Kundenname}}, Ihre {{DOKUMENTNUMMER}}</p>', TEST_KATALOG);
        const [kunde, nummer] = chips(html);
        expect(kunde.getAttribute('data-platzhalter-chip')).toBe('{{Kundenname}}');
        expect(kunde.querySelector('[data-chip-bezeichnung]')?.textContent).toBe('Kundenname');
        expect(kunde.querySelector('[data-chip-beispiel]')?.textContent).toBe('Max Mustermann');
        expect(kunde.getAttribute('data-platzhalter-bekannt')).toBe('true');
        expect(nummer.querySelector('[data-chip-beispiel]')?.textContent).toBe('RE-2026-0108');
    });

    it('zeigt unbekannte Kürzel als grauen Chip mit dem Kürzel', () => {
        const [chip] = chips(kuerzelZuChipHtml('<p>{{Irgendwas}}</p>', TEST_KATALOG));
        expect(chip.getAttribute('data-platzhalter-bekannt')).toBe('false');
        expect(chip.querySelector('[data-chip-bezeichnung]')?.textContent).toBe(UNBEKANNT_LABEL);
        expect(chip.querySelector('[data-chip-beispiel]')?.textContent).toBe('{{Irgendwas}}');
    });

    it('entfernt Schadcode und lässt Kürzel in Attributen unberührt', () => {
        const html = kuerzelZuChipHtml(
            '<p>{{Kundenname}}<img src=x onerror=alert(1)><script>alert(2)</script></p><a href="https://example.org/{{KUNDENNAME}}">Link</a>',
            TEST_KATALOG,
        );
        expect(html).not.toContain('onerror');
        expect(html).not.toContain('<script');
        expect(html).toContain('href="https://example.org/{{KUNDENNAME}}"');
        expect(chips(html)).toHaveLength(1);
    });

    it('verträgt leere Eingaben', () => {
        expect(kuerzelZuChipHtml(null, TEST_KATALOG)).toBe('');
        expect(kuerzelZuChipHtml('', TEST_KATALOG)).toBe('');
    });

    it('bleibt bei sehr langen Texten ohne Kürzel schnell', () => {
        const lang = `<p>${'{'.repeat(5000)}${'a'.repeat(5000)}</p>`;
        const start = performance.now();
        kuerzelZuChipHtml(lang, TEST_KATALOG);
        expect(performance.now() - start).toBeLessThan(500);
    });
});

describe('chipHtmlZuKuerzel', () => {
    it('stellt das ursprüngliche Kürzel exakt wieder her (Round-Trip, Schreibweise bleibt)', () => {
        const original = '<p>Hallo {{Kundenname}}, {{ANREDE}} und {{Irgendwas}}!</p><p><strong>{{DOKUMENTNUMMER}}</strong></p>';
        expect(chipHtmlZuKuerzel(kuerzelZuChipHtml(original, TEST_KATALOG))).toBe(original);
    });

    it('lässt HTML ohne Chips unverändert und verträgt leere Eingaben', () => {
        expect(chipHtmlZuKuerzel('<p>Nur Text</p>')).toBe('<p>Nur Text</p>');
        expect(chipHtmlZuKuerzel(null)).toBe('');
    });
});

describe('verwendeteKuerzel', () => {
    it('nennt jedes Kürzel einmal in der Schreibweise des Textes', () => {
        expect(verwendeteKuerzel('<p>{{Kundenname}} {{Kundenname}}</p><p>{{DATUM}}</p>')).toEqual(['{{Kundenname}}', '{{DATUM}}']);
        expect(verwendeteKuerzel('<p>Ohne</p>')).toEqual([]);
        expect(verwendeteKuerzel(undefined)).toEqual([]);
    });
});

describe('chipDarstellung und platzhalterNachGruppe', () => {
    it('behält das Original-Kürzel und gruppiert in Backend-Reihenfolge', () => {
        expect(chipDarstellung(TEST_KATALOG, '{{kundenname}}')).toEqual({
            token: '{{kundenname}}', label: 'Kundenname', beispiel: 'Max Mustermann', bekannt: true,
        });
        expect(platzhalterNachGruppe(TEST_KATALOG).map((g) => [g.gruppe, g.eintraege.length])).toEqual([
            ['Kunde', 3], ['Dokument', 1], ['Zahlung', 1],
        ]);
    });
});
