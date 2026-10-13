import { describe, expect, it } from 'vitest';
import { projektZumBearbeiten } from './projektBearbeiten';
import type { ProjektDetail } from '../types';

const basis: ProjektDetail = {
    id: 7,
    bauvorhaben: 'Treppengeländer Musterstraße',
    kunde: 'Max Mustermann',
    kundennummer: 'K-1003',
    auftragsnummer: '2026/08/00007',
    bruttoPreis: 5000,
    bezahlt: false,
    abgeschlossen: true,
    strasse: 'Musterstraße 1',
    plz: '12345',
    ort: 'Musterstadt',
    projektArt: 'REGIE',
    kundeDto: { id: 3, kundennummer: 'K-1003', name: 'Max Mustermann', kundenEmails: ['max@example.com'] },
    kundenEmails: ['max@example.com', 'bauleitung@example.com'],
    produktkategorien: [{ produktkategorie: { id: 4, bezeichnung: 'Geländer' }, menge: 12 }],
};

describe('projektZumBearbeiten', () => {
    it('reicht die Projektart durch, damit Speichern sie nicht auf Pauschal zurücksetzt', () => {
        for (const art of ['PAUSCHAL', 'REGIE', 'INTERN', 'GARANTIE'] as const) {
            expect(projektZumBearbeiten({ ...basis, projektArt: art }).projektArt).toBe(art);
        }
    });

    it('übernimmt alle Felder, die der Bearbeiten-Dialog vorbelegt', () => {
        expect(projektZumBearbeiten(basis)).toEqual({
            id: 7,
            bauvorhaben: 'Treppengeländer Musterstraße',
            kunde: 'Max Mustermann',
            kundennummer: 'K-1003',
            kundenId: 3,
            auftragsnummer: '2026/08/00007',
            bruttoPreis: 5000,
            strasse: 'Musterstraße 1',
            plz: '12345',
            ort: 'Musterstadt',
            abgeschlossen: true,
            projektArt: 'REGIE',
            kundeDto: basis.kundeDto,
            produktkategorien: basis.produktkategorien,
            kundenEmails: basis.kundenEmails,
        });
    });

    it('füllt fehlende Pflichttexte mit Leerstrings und lässt eine unbekannte Projektart leer', () => {
        const ergebnis = projektZumBearbeiten({ id: 8, bauvorhaben: '', bezahlt: false });

        expect(ergebnis).toMatchObject({
            id: 8, bauvorhaben: '', kunde: '', kundennummer: '', auftragsnummer: '',
        });
        expect(ergebnis.kundenId).toBeUndefined();
        expect(ergebnis.projektArt).toBeUndefined();
    });
});
