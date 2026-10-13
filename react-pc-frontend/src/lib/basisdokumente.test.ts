import { describe, expect, it } from 'vitest';
import { aktiveBasisdokumente, basisdokumentSperre, hatAktivesBasisdokument } from './basisdokumente';
import type { AusgangsGeschaeftsDokumentTyp } from '../types';

type Dok = { id: number; typ: AusgangsGeschaeftsDokumentTyp; vorgaengerId?: number; storniert: boolean };

const rechnung = (id: number, storniert = false): Dok => ({ id, typ: 'RECHNUNG', storniert });
const storno = (id: number, vorgaengerId: number): Dok => ({ id, typ: 'STORNO', vorgaengerId, storniert: false });
const angebot = (id: number): Dok => ({ id, typ: 'ANGEBOT', storniert: false });

describe('Basisdokumente (Issue #262)', () => {
    it('ohne Dokumente ist alles außer dem Nachtragsangebot frei', () => {
        expect(hatAktivesBasisdokument([])).toBe(false);
        expect(basisdokumentSperre('RECHNUNG', [])).toEqual({ gesperrt: false });
        expect(basisdokumentSperre('ANGEBOT', [])).toEqual({ gesperrt: false });
        expect(basisdokumentSperre('NACHTRAGSANGEBOT', [])).toEqual({ gesperrt: true, grund: 'Zuerst ein Angebot anlegen' });
    });

    it('stornierte Rechnung samt Stornorechnung sperrt keine neue Rechnung', () => {
        const dokumente = [rechnung(1, true), storno(2, 1)];
        expect(aktiveBasisdokumente(dokumente)).toEqual([]);
        expect(basisdokumentSperre('RECHNUNG', dokumente).gesperrt).toBe(false);
        expect(basisdokumentSperre('ANGEBOT', dokumente).gesperrt).toBe(false);
        expect(basisdokumentSperre('AUFTRAGSBESTAETIGUNG', dokumente).gesperrt).toBe(false);
    });

    it('eine aktive Rechnung sperrt weitere Basisdokumente', () => {
        const dokumente = [rechnung(1, true), storno(2, 1), rechnung(3)];
        expect(basisdokumentSperre('RECHNUNG', dokumente)).toEqual({ gesperrt: true, grund: 'Es existiert bereits ein Basisdokument' });
        expect(basisdokumentSperre('ANGEBOT', dokumente).grund).toContain('Nachtragsangebot');
        expect(basisdokumentSperre('NACHTRAGSANGEBOT', dokumente).gesperrt).toBe(true);
    });

    it('ein Angebot erlaubt Nachtragsangebote, sperrt aber eine eigenständige Rechnung', () => {
        const dokumente = [angebot(1), { id: 2, typ: 'ABSCHLAGSRECHNUNG', vorgaengerId: 1, storniert: true } as Dok, storno(3, 2)];
        expect(hatAktivesBasisdokument(dokumente)).toBe(true);
        expect(basisdokumentSperre('NACHTRAGSANGEBOT', dokumente)).toEqual({ gesperrt: false });
        expect(basisdokumentSperre('RECHNUNG', dokumente).gesperrt).toBe(true);
    });
});
