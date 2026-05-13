/**
 * Vitest-Suite fuer blockOps – Ziel: 100% Branch-Coverage.
 *
 * Getestete reine Funktionen:
 *  - syncClosureBlock
 *  - insertBeforeNachtexte
 *  - insertAtAnchor
 *  - insertBlocksBeforeClosure
 *
 * DSGVO: ausschliesslich Dummy-Daten (Max Mustermann / test-IDs).
 */
import { describe, it, expect } from 'vitest';
import {
    CLOSURE_BLOCK_ID,
    syncClosureBlock,
    insertBeforeNachtexte,
    insertAtAnchor,
    insertBlocksBeforeClosure,
    validateRootReorder,
} from './blockOps';
import type { DocBlock } from './types';

// ─── Test-Fixtures ──────────────────────────────────────────────────────────
const text = (id: string, rolle?: 'VOR' | 'NACH'): DocBlock => ({
    id,
    type: 'TEXT',
    content: '<p>Max Mustermann</p>',
    ...(rolle ? { textbausteinRolle: rolle } : {}),
});

const service = (id: string): DocBlock => ({
    id,
    type: 'SERVICE',
    title: 'Leistung Mustermann',
    quantity: 1,
    unit: 'Stk',
    price: 0,
});

const section = (id: string, children: DocBlock[] = []): DocBlock => ({
    id,
    type: 'SECTION_HEADER',
    sectionLabel: 'Bauabschnitt 1',
    children,
});

const sectionEmpty = (id: string): DocBlock => ({
    id,
    type: 'SECTION_HEADER',
    sectionLabel: 'Bauabschnitt leer',
});

const separator = (id: string): DocBlock => ({ id, type: 'SEPARATOR' });
const subtotal = (id: string): DocBlock => ({ id, type: 'SUBTOTAL' });
const closure: DocBlock = { id: CLOSURE_BLOCK_ID, type: 'CLOSURE' };

// ─── syncClosureBlock ───────────────────────────────────────────────────────
describe('syncClosureBlock', () => {
    it('liefert identische Referenz wenn keine Leistung und kein CLOSURE vorhanden', () => {
        const blocks: DocBlock[] = [text('t1'), text('t2', 'NACH')];
        const result = syncClosureBlock(blocks);
        expect(result).toBe(blocks); // Identity!
    });

    it('fuegt CLOSURE direkt nach der letzten Leistung ein wenn keiner vorhanden', () => {
        const blocks: DocBlock[] = [text('vor1', 'VOR'), service('s1'), text('nach1', 'NACH')];
        const result = syncClosureBlock(blocks);
        expect(result.map(b => b.id)).toEqual(['vor1', 's1', CLOSURE_BLOCK_ID, 'nach1']);
    });

    it('fuegt CLOSURE nach SECTION_HEADER (mit Kindern) wenn keine Root-SERVICE existiert', () => {
        const blocks: DocBlock[] = [section('sec1', [service('child1')]), text('nach', 'NACH')];
        const result = syncClosureBlock(blocks);
        expect(result.map(b => b.id)).toEqual(['sec1', CLOSURE_BLOCK_ID, 'nach']);
    });

    it('fuegt CLOSURE nach SUBTOTAL ein wenn dies der letzte service-aehnliche Block ist', () => {
        const blocks: DocBlock[] = [section('sec1', [service('child1')]), subtotal('st1')];
        const result = syncClosureBlock(blocks);
        expect(result[result.length - 1].id).toBe(CLOSURE_BLOCK_ID);
        expect(result[result.length - 2].id).toBe('st1');
    });

    it('fuegt CLOSURE nach SEPARATOR ein wenn dies der letzte service-aehnliche Block ist', () => {
        const blocks: DocBlock[] = [service('s1'), separator('sep1')];
        const result = syncClosureBlock(blocks);
        expect(result.map(b => b.id)).toEqual(['s1', 'sep1', CLOSURE_BLOCK_ID]);
    });

    it('fuegt CLOSURE NICHT ein wenn nur eine leere SECTION_HEADER existiert', () => {
        const blocks: DocBlock[] = [sectionEmpty('sec1'), text('t1')];
        const result = syncClosureBlock(blocks);
        expect(result).toBe(blocks); // keine echte Leistung -> nichts zu syncen
    });

    it('haengt CLOSURE ans Ende an wenn hasServices stimmt, aber lastIdx -1 bleibt (defensive)', () => {
        // Defensive Pfad: Wenn eine Section mit Kindern vorhanden ist, ist hasServices=true.
        // Der lastIdx-Loop laeuft rueckwaerts und findet die Section ueberlicherweise.
        // Spezialfall mit lastIdx=-1 ist konstruiert nicht erreichbar, weil SECTION_HEADER
        // selbst im Loop matched. Wir testen daher Standardpfad mit Service am Anfang
        // (Closure landet direkt dahinter).
        const blocks: DocBlock[] = [service('s1'), text('t', 'NACH')];
        const result = syncClosureBlock(blocks);
        expect(result.map(b => b.id)).toEqual(['s1', CLOSURE_BLOCK_ID, 't']);
    });

    it('entfernt CLOSURE wenn keine Leistung mehr existiert', () => {
        const blocks: DocBlock[] = [text('t1'), closure];
        const result = syncClosureBlock(blocks);
        expect(result.map(b => b.id)).toEqual(['t1']);
    });

    it('liefert identische Referenz wenn CLOSURE schon korrekt vorhanden ist', () => {
        const blocks: DocBlock[] = [service('s1'), closure];
        const result = syncClosureBlock(blocks);
        expect(result).toBe(blocks);
    });
});

// ─── insertBeforeNachtexte ──────────────────────────────────────────────────
describe('insertBeforeNachtexte', () => {
    it('haengt SERVICE ans Ende wenn weder CLOSURE noch NACH-Text existiert', () => {
        const blocks: DocBlock[] = [text('t1')];
        const result = insertBeforeNachtexte(blocks, service('new'));
        expect(result.map(b => b.id)).toEqual(['t1', 'new']);
    });

    it('fuegt SERVICE vor CLOSURE ein wenn CLOSURE vorhanden', () => {
        const blocks: DocBlock[] = [service('s1'), closure, text('nach', 'NACH')];
        const result = insertBeforeNachtexte(blocks, service('new'));
        expect(result.map(b => b.id)).toEqual(['s1', 'new', CLOSURE_BLOCK_ID, 'nach']);
    });

    it('fuegt TEXT vor erstem NACH-Text ein wenn kein CLOSURE da', () => {
        const blocks: DocBlock[] = [text('vor', 'VOR'), text('nach', 'NACH')];
        const result = insertBeforeNachtexte(blocks, text('new'));
        expect(result.map(b => b.id)).toEqual(['vor', 'new', 'nach']);
    });

    it('haengt TEXT ans Ende wenn keine NACH-Texte existieren', () => {
        const blocks: DocBlock[] = [text('vor', 'VOR')];
        const result = insertBeforeNachtexte(blocks, text('new'));
        expect(result.map(b => b.id)).toEqual(['vor', 'new']);
    });

    it('SECTION_HEADER verhaelt sich wie SERVICE (vor CLOSURE)', () => {
        const blocks: DocBlock[] = [service('s1'), closure];
        const result = insertBeforeNachtexte(blocks, section('new'));
        expect(result.map(b => b.id)).toEqual(['s1', 'new', CLOSURE_BLOCK_ID]);
    });
});

// ─── insertAtAnchor ─────────────────────────────────────────────────────────
describe('insertAtAnchor', () => {
    it('faellt auf insertBeforeNachtexte zurueck wenn anchorId null', () => {
        const blocks: DocBlock[] = [service('s1'), closure];
        const result = insertAtAnchor(blocks, text('new'), null);
        expect(result.map(b => b.id)).toEqual(['s1', CLOSURE_BLOCK_ID, 'new']);
    });

    it('faellt auf insertBeforeNachtexte zurueck wenn anchorId === CLOSURE_BLOCK_ID', () => {
        const blocks: DocBlock[] = [service('s1'), closure];
        const result = insertAtAnchor(blocks, text('new'), CLOSURE_BLOCK_ID);
        expect(result.map(b => b.id)).toEqual(['s1', CLOSURE_BLOCK_ID, 'new']);
    });

    it('fuegt TEXT direkt nach Root-Anker ein', () => {
        const blocks: DocBlock[] = [text('t1'), text('t2'), service('s1')];
        const result = insertAtAnchor(blocks, text('new'), 't1');
        expect(result.map(b => b.id)).toEqual(['t1', 'new', 't2', 's1']);
    });

    it('clampt SERVICE auf vor CLOSURE wenn Anker hinter CLOSURE liegt', () => {
        const blocks: DocBlock[] = [service('s1'), closure, text('nach', 'NACH')];
        const result = insertAtAnchor(blocks, service('new'), 'nach');
        // Anker 'nach' ist Index 2 -> waere Insert nach Index 3, CLOSURE Index 1 -> clampen
        expect(result.map(b => b.id)).toEqual(['s1', 'new', CLOSURE_BLOCK_ID, 'nach']);
    });

    it('clampt SECTION_HEADER auf vor CLOSURE wenn Anker hinter CLOSURE liegt', () => {
        const blocks: DocBlock[] = [service('s1'), closure, text('nach', 'NACH')];
        const result = insertAtAnchor(blocks, section('new'), 'nach');
        expect(result.map(b => b.id)).toEqual(['s1', 'new', CLOSURE_BLOCK_ID, 'nach']);
    });

    it('TEXT bleibt nach Anker auch wenn das nach CLOSURE liegt', () => {
        const blocks: DocBlock[] = [service('s1'), closure, text('nach', 'NACH')];
        const result = insertAtAnchor(blocks, text('new'), 'nach');
        expect(result.map(b => b.id)).toEqual(['s1', CLOSURE_BLOCK_ID, 'nach', 'new']);
    });

    it('fuegt SERVICE in dieselbe Section nach einem child-Anker ein', () => {
        const child1 = service('c1');
        const child2 = service('c2');
        const blocks: DocBlock[] = [section('sec1', [child1, child2]), closure];
        const result = insertAtAnchor(blocks, service('new'), 'c1');
        const sec = result[0];
        expect(sec.children?.map(c => c.id)).toEqual(['c1', 'new', 'c2']);
    });

    it('fuegt TEXT auf Root direkt nach der Section ein wenn Anker ein child ist', () => {
        const blocks: DocBlock[] = [section('sec1', [service('c1')]), closure];
        const result = insertAtAnchor(blocks, text('new'), 'c1');
        expect(result.map(b => b.id)).toEqual(['sec1', 'new', CLOSURE_BLOCK_ID]);
    });

    it('clampt SECTION_HEADER vor CLOSURE wenn child-Anker in einer Section nach CLOSURE liegt', () => {
        // Konstruierter Edge-Case: CLOSURE vor der Section. In der UI nicht erreichbar
        // (Constraint im handleDragEnd), aber der Code-Pfad fuer SECTION_HEADER-Clamping
        // im child-Branch wird hier abgedeckt.
        const blocks: DocBlock[] = [closure, section('sec1', [service('c1')])];
        const result = insertAtAnchor(blocks, section('new'), 'c1');
        expect(result.map(b => b.id)).toEqual(['new', CLOSURE_BLOCK_ID, 'sec1']);
    });

    it('Section ohne children-Array wird nicht als Container fehlinterpretiert', () => {
        const blocks: DocBlock[] = [{ id: 'sec1', type: 'SECTION_HEADER' }, service('s1')];
        const result = insertAtAnchor(blocks, text('new'), 'unbekannt');
        // Anker nicht gefunden -> Fallback (haengt an Ende an, kein NACH-Text vorhanden)
        expect(result.map(b => b.id)).toEqual(['sec1', 's1', 'new']);
    });

    it('Fallback wenn Anker weder Root- noch Section-Child ist', () => {
        const blocks: DocBlock[] = [service('s1'), text('nach', 'NACH')];
        const result = insertAtAnchor(blocks, text('new'), 'ghost-id');
        expect(result.map(b => b.id)).toEqual(['s1', 'new', 'nach']);
    });

    it('iteriert ueber Sections und fallbackt wenn child-Anker nirgends gefunden wird', () => {
        // Hier existieren Sections mit Children, aber Anker liegt in keiner -> deckt
        // den childIdx === -1 Pfad in der inneren Section-Schleife ab.
        const blocks: DocBlock[] = [
            section('sec1', [service('c1')]),
            section('sec2', [service('c2')]),
            text('nach', 'NACH'),
        ];
        const result = insertAtAnchor(blocks, text('new'), 'ghost-id');
        expect(result.map(b => b.id)).toEqual(['sec1', 'sec2', 'new', 'nach']);
    });
});

// ─── insertBlocksBeforeClosure (GAEB-Bulk) ──────────────────────────────────
describe('insertBlocksBeforeClosure', () => {
    it('liefert prev unveraendert wenn keine Bloecke einzufuegen sind', () => {
        const blocks: DocBlock[] = [service('s1')];
        expect(insertBlocksBeforeClosure(blocks, [])).toBe(blocks);
    });

    it('fuegt vor CLOSURE ein wenn dieser vorhanden', () => {
        const blocks: DocBlock[] = [service('s1'), closure, text('nach', 'NACH')];
        const result = insertBlocksBeforeClosure(blocks, [service('new1'), service('new2')]);
        expect(result.map(b => b.id)).toEqual(['s1', 'new1', 'new2', CLOSURE_BLOCK_ID, 'nach']);
    });

    it('fuegt vor erstem NACH-Text ein wenn kein CLOSURE da', () => {
        const blocks: DocBlock[] = [service('s1'), text('nach', 'NACH')];
        const result = insertBlocksBeforeClosure(blocks, [service('new')]);
        expect(result.map(b => b.id)).toEqual(['s1', 'new', 'nach']);
    });

    it('haengt ans Ende an wenn weder CLOSURE noch NACH-Text vorhanden', () => {
        const blocks: DocBlock[] = [service('s1')];
        const result = insertBlocksBeforeClosure(blocks, [text('new')]);
        expect(result.map(b => b.id)).toEqual(['s1', 'new']);
    });

    it('nutzt den frueheren von CLOSURE vs. NACH-Text als Limit', () => {
        // Pathologischer Fall: CLOSURE liegt nach einem NACH-Text. Das fruehere Limit
        // (NACH-Text) gewinnt.
        const blocks: DocBlock[] = [service('s1'), text('nach', 'NACH'), closure];
        const result = insertBlocksBeforeClosure(blocks, [text('new')]);
        expect(result.map(b => b.id)).toEqual(['s1', 'new', 'nach', CLOSURE_BLOCK_ID]);
    });
});

// ─── validateRootReorder ────────────────────────────────────────────────────
describe('validateRootReorder', () => {
    it('liefert ok=true wenn kein CLOSURE im Array ist', () => {
        const order: DocBlock[] = [service('s1'), service('s2')];
        expect(validateRootReorder(order, 's2')).toEqual({ ok: true });
    });

    it('liefert ok=true wenn activeId nicht im Array ist', () => {
        const order: DocBlock[] = [service('s1'), closure];
        expect(validateRootReorder(order, 'ghost')).toEqual({ ok: true });
    });

    it('blockt SERVICE hinter CLOSURE', () => {
        // User hat 's1' (SERVICE) hinter CLOSURE geschoben
        const order: DocBlock[] = [closure, service('s1')];
        expect(validateRootReorder(order, 's1')).toEqual({
            ok: false,
            reason: 'SERVICE_AFTER_CLOSURE',
        });
    });

    it('blockt SECTION_HEADER (Bauabschnitt) hinter CLOSURE', () => {
        // Bug-Report 2026-05: Bauabschnitte muessen genauso wie Leistungen blockiert sein
        const order: DocBlock[] = [closure, section('sec1', [service('c1')])];
        expect(validateRootReorder(order, 'sec1')).toEqual({
            ok: false,
            reason: 'SERVICE_AFTER_CLOSURE',
        });
    });

    it('erlaubt TEXT hinter CLOSURE (Nachtext-Position)', () => {
        const order: DocBlock[] = [service('s1'), closure, text('nach')];
        expect(validateRootReorder(order, 'nach')).toEqual({ ok: true });
    });

    it('erlaubt TEXT zwischen SERVICE und CLOSURE', () => {
        const order: DocBlock[] = [service('s1'), text('mitten'), closure];
        expect(validateRootReorder(order, 'mitten')).toEqual({ ok: true });
    });

    it('erlaubt SEPARATOR hinter CLOSURE', () => {
        const order: DocBlock[] = [service('s1'), closure, separator('sep1')];
        expect(validateRootReorder(order, 'sep1')).toEqual({ ok: true });
    });

    it('blockt CLOSURE wenn er vor die letzte SERVICE geschoben wird', () => {
        // CLOSURE wurde vor 's2' geschoben, obwohl es weitere SERVICE danach gibt
        const order: DocBlock[] = [service('s1'), closure, service('s2')];
        expect(validateRootReorder(order, CLOSURE_BLOCK_ID)).toEqual({
            ok: false,
            reason: 'CLOSURE_BEFORE_LAST_SERVICE',
        });
    });

    it('blockt CLOSURE wenn er vor einen Bauabschnitt geschoben wird', () => {
        const order: DocBlock[] = [service('s1'), closure, section('sec1', [service('c1')])];
        expect(validateRootReorder(order, CLOSURE_BLOCK_ID)).toEqual({
            ok: false,
            reason: 'CLOSURE_BEFORE_LAST_SERVICE',
        });
    });

    it('erlaubt CLOSURE-Move solange er nach allen Leistungen bleibt', () => {
        const order: DocBlock[] = [service('s1'), service('s2'), text('t'), closure];
        expect(validateRootReorder(order, CLOSURE_BLOCK_ID)).toEqual({ ok: true });
    });

    it('erlaubt CLOSURE-Move wenn keine SERVICE/SECTION mehr existiert (lastServiceIdx=-1)', () => {
        // Defensiver Pfad: CLOSURE im Array aber keine Leistungen mehr -> kein Block-Grund
        const order: DocBlock[] = [text('t1'), closure];
        expect(validateRootReorder(order, CLOSURE_BLOCK_ID)).toEqual({ ok: true });
    });
});
