import { describe, expect, it } from 'vitest';
import type { EmailItem } from './emailCenterModel';
import { endgueltigFrage, endgueltigText, inBloecke, naechsteNachLoeschen, papierkorbText, zurueckText } from './emailLoeschen';

const mail = (id: number): EmailItem => ({ id, type: 'EMAIL', direction: 'IN', subject: `Mail ${id}` });

describe('Texte', () => {
    it('nennt eine oder mehrere E-Mails', () => {
        expect(papierkorbText(1)).toBe('E-Mail in den Papierkorb gelegt.');
        expect(papierkorbText(3)).toBe('3 E-Mails in den Papierkorb gelegt.');
        expect(zurueckText(1)).toBe('E-Mail ist wieder da.');
        expect(zurueckText(3)).toBe('3 E-Mails sind wieder da.');
        expect(endgueltigText(1)).toBe('E-Mail endgültig gelöscht.');
        expect(endgueltigText(2)).toBe('2 E-Mails endgültig gelöscht.');
    });

    it('fragt vor dem endgültigen Löschen mit Betreff nach', () => {
        expect(endgueltigFrage(1, 'Angebot Geländer')).toEqual({
            title: 'Endgültig löschen?',
            message: '„Angebot Geländer“ wird endgültig gelöscht und auch vom Mailserver entfernt. Das lässt sich nicht rückgängig machen.',
            confirmLabel: 'Endgültig löschen',
            variant: 'danger',
        });
    });

    it('nennt bei mehreren Mails die Anzahl, auch für einen ganzen Verlauf', () => {
        expect(endgueltigFrage(3).message)
            .toBe('3 E-Mails werden endgültig gelöscht und auch vom Mailserver entfernt. Das lässt sich nicht rückgängig machen.');
        expect(endgueltigFrage(3, 'Angebot Geländer').message)
            .toBe('Der Verlauf „Angebot Geländer“ mit 3 E-Mails wird endgültig gelöscht und auch vom Mailserver entfernt. Das lässt sich nicht rückgängig machen.');
    });

    it('nimmt ohne Betreff einen neutralen Text', () => {
        expect(endgueltigFrage(1, '  ').message)
            .toBe('Die E-Mail wird endgültig gelöscht und auch vom Mailserver entfernt. Das lässt sich nicht rückgängig machen.');
    });
});

describe('inBloecke', () => {
    it('teilt große Mengen in Blöcke à 500', () => {
        const ids = Array.from({ length: 1201 }, (_, i) => i + 1);
        expect(inBloecke(ids, 500).map(block => block.length)).toEqual([500, 500, 201]);
        expect(inBloecke([], 500)).toEqual([]);
    });
});

describe('naechsteNachLoeschen', () => {
    const liste = [mail(1), mail(2), mail(3), mail(4)];

    it('springt auf die Mail unter der gelöschten', () => {
        expect(naechsteNachLoeschen(liste, new Set([2]))?.id).toBe(3);
    });

    it('springt bei mehreren auf die Mail unter der letzten gelöschten', () => {
        expect(naechsteNachLoeschen(liste, new Set([1, 3]))?.id).toBe(4);
    });

    it('nimmt am Listenende die Mail darüber', () => {
        expect(naechsteNachLoeschen(liste, new Set([4]))?.id).toBe(3);
        expect(naechsteNachLoeschen(liste, new Set([3, 4]))?.id).toBe(2);
    });

    it('gibt nichts zurück, wenn die Liste danach leer ist oder nichts davon sichtbar war', () => {
        expect(naechsteNachLoeschen(liste, new Set([1, 2, 3, 4]))).toBeNull();
        expect(naechsteNachLoeschen(liste, new Set([99]))).toBeNull();
    });
});
