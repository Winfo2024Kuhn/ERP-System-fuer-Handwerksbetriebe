import { describe, expect, it } from 'vitest';
import { stapelMeldung } from './stapelHochladen';

describe('stapelMeldung', () => {
    it('meldet den Erfolg mit Anzahl und Ordner', () => {
        expect(stapelMeldung({ gesamt: 40, fehler: [], ordnerName: 'Fotos Baustelle' }))
            .toEqual({ art: 'erfolg', text: '40 Dateien in „Fotos Baustelle“ abgelegt.' });
        expect(stapelMeldung({ gesamt: 1, fehler: [], ordnerName: 'Montag' })).toEqual({ art: 'erfolg', text: '1 Datei in „Montag“ abgelegt.' });
        // Gingen die Dateien in verschiedene Ordner, steht keiner im Text.
        expect(stapelMeldung({ gesamt: 3, fehler: [], ordnerName: null })).toEqual({ art: 'erfolg', text: '3 Dateien abgelegt.' });
    });

    it('nennt bei Teilfehlern die Dateien – den Grund einmal, wenn er überall gleich ist', () => {
        const grund = 'Die Datei konnte nicht hochgeladen werden.';
        expect(stapelMeldung({ gesamt: 40, fehler: [{ name: 'a.jpg', grund }, { name: 'b.jpg', grund }], ordnerName: 'Fotos' }))
            .toEqual({ art: 'fehler', text: `38 von 40 abgelegt. Nicht geklappt: a.jpg, b.jpg (${grund})` });
        expect(stapelMeldung({ gesamt: 3, fehler: [{ name: 'a.jpg', grund: 'Zu groß.' }, { name: 'b.pdf', grund: 'Kein Recht.' }], ordnerName: null }))
            .toEqual({ art: 'fehler', text: '1 von 3 abgelegt. Nicht geklappt: a.jpg (Zu groß.), b.pdf (Kein Recht.)' });
    });

    it('kürzt lange Fehlerlisten', () => {
        const fehler = Array.from({ length: 8 }, (_, i) => ({ name: `foto_${i + 1}.jpg`, grund: 'Der Server ist ausgelastet.' }));
        expect(stapelMeldung({ gesamt: 8, fehler, ordnerName: 'Fotos' }).text)
            .toBe('0 von 8 abgelegt. Nicht geklappt: foto_1.jpg, foto_2.jpg, foto_3.jpg, foto_4.jpg, foto_5.jpg und 3 weitere (Der Server ist ausgelastet.)');
    });

    it('sagt nach dem Abbrechen, wie viele abgelegt sind – mit Fehlern, falls es welche gab', () => {
        expect(stapelMeldung({ gesamt: 40, fehler: [], ordnerName: 'Fotos', abgebrochen: 28 }))
            .toEqual({ art: 'hinweis', text: '12 von 40 abgelegt, Rest abgebrochen.' });
        expect(stapelMeldung({ gesamt: 40, fehler: [{ name: 'a.jpg', grund: 'Zu groß.' }], ordnerName: 'Fotos', abgebrochen: 28 }))
            .toEqual({ art: 'fehler', text: '11 von 40 abgelegt, Rest abgebrochen. Nicht geklappt: a.jpg (Zu groß.)' });
    });
});
