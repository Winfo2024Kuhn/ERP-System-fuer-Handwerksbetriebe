import { describe, expect, it } from 'vitest';
import { klartextGrund } from './zustellGrund';

/**
 * Der Servertext eines Rückläufers ist für Handwerker unbrauchbar
 * ("550 5.1.1 ... User unknown"). Diese Übersetzung ist der Unterschied
 * zwischen "da steht irgendwas Rotes" und "ah, die Adresse stimmt nicht".
 *
 * Alle Adressen sind Dummy-Daten (DSGVO).
 */
describe('klartextGrund', () => {
    it('erklärt eine nicht existierende Adresse in Alltagssprache', () => {
        expect(klartextGrund('550 5.1.1 <max.mustermann@example.com> User unknown'))
            .toBe('Diese E-Mail-Adresse gibt es nicht. Bitte Adresse prüfen und erneut senden.');
    });

    it('erkennt auch die deutsche Formulierung von t-online', () => {
        expect(klartextGrund('unknown user / Teilnehmer existiert nicht'))
            .toBe('Diese E-Mail-Adresse gibt es nicht. Bitte Adresse prüfen und erneut senden.');
    });

    it('unterscheidet einen unbekannten Anbieter von einer unbekannten Adresse', () => {
        expect(klartextGrund('host unknown: no MX for domain'))
            .toContain('Den Anbieter dieser E-Mail-Adresse gibt es nicht');
    });

    it('nennt eine Ablehnung durch den Empfänger-Server beim Namen', () => {
        expect(klartextGrund('554 5.7.1 Recipient address rejected'))
            .toContain('Der Empfänger-Server hat die E-Mail abgelehnt');
    });

    it('fällt auf einen allgemeinen Satz zurück, wenn nichts passt', () => {
        expect(klartextGrund('irgendein unbekannter serverfehler'))
            .toBe('Diese E-Mail konnte nicht zugestellt werden. Bitte E-Mail-Adresse prüfen und erneut senden.');
    });

    it('kommt ohne Grund-Text zurecht', () => {
        expect(klartextGrund(undefined))
            .toBe('Diese E-Mail konnte nicht zugestellt werden. Bitte E-Mail-Adresse prüfen und erneut senden.');
        expect(klartextGrund('')).toContain('konnte nicht zugestellt werden');
    });
});
