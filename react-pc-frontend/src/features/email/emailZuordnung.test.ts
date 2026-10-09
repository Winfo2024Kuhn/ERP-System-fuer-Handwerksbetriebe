import { describe, expect, it } from 'vitest';
import { ermittleEmailZuordnung } from './emailZuordnung';

describe('ermittleEmailZuordnung', () => {
    describe('Projekt', () => {
        it('zeigt Auftragsnummer und Name und verlinkt ins Projekt', () => {
            expect(ermittleEmailZuordnung({
                zuordnungTyp: 'PROJEKT', projektId: 41, projektName: 'Garagentor Mustermann',
                projektAuftragsnummer: '2026-041',
            })).toEqual({
                art: 'Projekt',
                label: 'Projekt 2026-041 · Garagentor Mustermann',
                pfad: '/projekte?projektId=41',
                titel: 'Projekt 2026-041 · Garagentor Mustermann öffnen',
            });
        });

        it('lässt die Nummer weg, wenn das Projekt keine hat', () => {
            const z = ermittleEmailZuordnung({
                zuordnungTyp: 'PROJEKT', projektId: 41, projektName: 'Garagentor Mustermann', projektAuftragsnummer: null,
            });
            expect(z?.label).toBe('Projekt · Garagentor Mustermann');
        });

        it('behandelt eine leere Nummer wie keine', () => {
            const z = ermittleEmailZuordnung({
                zuordnungTyp: 'PROJEKT', projektId: 41, projektName: 'Garagentor', projektAuftragsnummer: '  ',
            });
            expect(z?.label).toBe('Projekt · Garagentor');
        });

        it('zeigt ohne Namen nur die Nummer', () => {
            const z = ermittleEmailZuordnung({ zuordnungTyp: 'PROJEKT', projektId: 41, projektAuftragsnummer: '2026-041' });
            expect(z?.label).toBe('Projekt 2026-041');
            expect(z?.pfad).toBe('/projekte?projektId=41');
        });

        it('fällt ohne Namen und Nummer auf die ID zurück', () => {
            const z = ermittleEmailZuordnung({ zuordnungTyp: 'PROJEKT', projektId: 41, projektName: '' });
            expect(z?.label).toBe('Projekt #41');
            expect(z?.titel).toBe('Projekt #41 öffnen');
        });

        it('hat ohne ID keinen Link', () => {
            expect(ermittleEmailZuordnung({ zuordnungTyp: 'PROJEKT', projektName: 'Garagentor' })).toEqual({
                art: 'Projekt', label: 'Projekt · Garagentor', pfad: null, titel: 'Projekt · Garagentor',
            });
            expect(ermittleEmailZuordnung({ zuordnungTyp: 'PROJEKT' })?.label).toBe('Projekt');
        });

        it('bleibt beim Projekt, auch wenn zusätzlich ein Lieferant verknüpft ist', () => {
            const z = ermittleEmailZuordnung({
                zuordnungTyp: 'PROJEKT', projektId: 41, projektName: 'Garagentor',
                lieferantId: 7, lieferantName: 'Musterhandel GmbH',
            });
            expect(z?.art).toBe('Projekt');
        });
    });

    describe('Anfrage', () => {
        it('verlinkt in die Anfrage', () => {
            expect(ermittleEmailZuordnung({
                zuordnungTyp: 'ANFRAGE', anfrageId: 12, anfrageName: 'Garagentor Mustermann',
                projektId: null, projektAuftragsnummer: null,
            })).toEqual({
                art: 'Anfrage',
                label: 'Anfrage · Garagentor Mustermann',
                pfad: '/anfragen?anfrageId=12',
                titel: 'Anfrage · Garagentor Mustermann öffnen',
            });
        });

        it('fällt ohne Namen auf die ID zurück', () => {
            expect(ermittleEmailZuordnung({ zuordnungTyp: 'ANFRAGE', anfrageId: 12 })?.label).toBe('Anfrage #12');
        });
    });

    describe('Lieferant', () => {
        it('verlinkt in die Lieferantenakte', () => {
            expect(ermittleEmailZuordnung({ zuordnungTyp: 'LIEFERANT', lieferantId: 7, lieferantName: 'Musterhandel GmbH' }))
                .toEqual({
                    art: 'Lieferant',
                    label: 'Lieferant · Musterhandel GmbH',
                    pfad: '/lieferanten?lieferantId=7',
                    titel: 'Lieferant · Musterhandel GmbH öffnen',
                });
        });

        it('fällt ohne Namen auf die ID zurück', () => {
            expect(ermittleEmailZuordnung({ zuordnungTyp: 'LIEFERANT', lieferantId: 7, lieferantName: null })?.label)
                .toBe('Lieferant #7');
        });
    });

    it('zeigt Steuerberater als Klartext ohne Link', () => {
        expect(ermittleEmailZuordnung({ zuordnungTyp: 'STEUERBERATER' })).toEqual({
            art: 'Steuerberater', label: 'Steuerberater', pfad: null, titel: 'Steuerberater',
        });
    });

    it('nimmt den Typ unabhängig von Groß-/Kleinschreibung', () => {
        expect(ermittleEmailZuordnung({ zuordnungTyp: ' anfrage ', anfrageId: 12, anfrageName: 'Treppe' })?.art)
            .toBe('Anfrage');
    });

    describe('keine Zuordnung', () => {
        it('liefert null bei KEINE – auch wenn noch IDs mitkommen', () => {
            expect(ermittleEmailZuordnung({ zuordnungTyp: 'KEINE' })).toBeNull();
            expect(ermittleEmailZuordnung({ zuordnungTyp: 'KEINE', lieferantId: 7, lieferantName: 'Musterhandel GmbH' }))
                .toBeNull();
        });

        it('liefert null, wenn gar nichts gesetzt ist', () => {
            expect(ermittleEmailZuordnung({})).toBeNull();
            expect(ermittleEmailZuordnung({ zuordnungTyp: null })).toBeNull();
        });
    });

    describe('ohne Typ entscheiden die IDs', () => {
        it('Projekt vor Anfrage vor Lieferant', () => {
            expect(ermittleEmailZuordnung({ projektId: 41, anfrageId: 12, lieferantId: 7 })?.art).toBe('Projekt');
            expect(ermittleEmailZuordnung({ anfrageId: 12, lieferantId: 7 })?.art).toBe('Anfrage');
            expect(ermittleEmailZuordnung({ lieferantId: 7 })?.art).toBe('Lieferant');
        });

        it('gilt auch für unbekannte Typen', () => {
            expect(ermittleEmailZuordnung({ zuordnungTyp: 'UNBEKANNT', anfrageId: 12 })?.art).toBe('Anfrage');
            expect(ermittleEmailZuordnung({ zuordnungTyp: 'UNBEKANNT' })).toBeNull();
        });

        it('ignoriert ungültige IDs', () => {
            expect(ermittleEmailZuordnung({ projektId: 0, anfrageId: -3, lieferantId: Number.NaN })).toBeNull();
        });
    });
});
