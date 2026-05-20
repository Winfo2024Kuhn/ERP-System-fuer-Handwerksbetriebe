/**
 * Pure Block-Operationen fuer den DocumentEditor.
 *
 * Diese Datei kapselt die Insert-/Sync-Logik rund um den virtuellen CLOSURE-Marker.
 * Sie wird sowohl von [index.tsx](./index.tsx) als auch von der Vitest-Suite in
 * [blockOps.test.ts](./blockOps.test.ts) importiert.
 *
 * Hintergrund / Constraints (vgl. UX-Anforderung aus 2026-05):
 *  - Es gibt genau einen "Abschluss"-Marker im Block-Array. Er ist Drag-and-Drop-baar,
 *    aber Leistungen (SERVICE) und Bauabschnitte (SECTION_HEADER) duerfen nie hinter
 *    ihm landen. Textbausteine und Separatoren sind frei positionierbar.
 *  - Beim Hinzufuegen eines neuen Blocks soll dieser direkt unter dem aktuell
 *    fokussierten Block einfuegt werden – nicht ans Ende des Dokuments.
 *  - Der CLOSURE-Marker wird nicht persistiert; beim Laden synthetisch eingefuegt.
 */

import type { DocBlock } from './types';

/**
 * Sentinel-ID des virtuellen Abschluss-Blocks. Wird beim Save aus dem
 * positionenJson herausgefiltert, sodass das gespeicherte Format mit bestehenden
 * Dokumenten kompatibel bleibt.
 */
export const CLOSURE_BLOCK_ID = '__closure__';

/**
 * Synchronisiert das CLOSURE-Marker-Element in der Block-Liste.
 *  - Wenn mindestens eine Leistung (SERVICE oder SECTION_HEADER mit Kindern)
 *    existiert und kein CLOSURE im Array ist: einfuegen direkt nach dem
 *    letzten SERVICE/SECTION_HEADER/SUBTOTAL/SEPARATOR.
 *  - Wenn keine Leistung mehr existiert und CLOSURE noch vorhanden ist: entfernen.
 *  - Sonst: identische Referenz zurueckgeben (kein Re-Render-Loop).
 */
export function syncClosureBlock(prev: DocBlock[]): DocBlock[] {
    const closureIdx = prev.findIndex(b => b.id === CLOSURE_BLOCK_ID);
    const hasServices = prev.some(b =>
        (b.type === 'SERVICE')
        || (b.type === 'SECTION_HEADER' && (b.children?.length ?? 0) > 0)
    );

    if (hasServices && closureIdx === -1) {
        let lastIdx = -1;
        for (let i = prev.length - 1; i >= 0; i--) {
            const t = prev[i].type;
            if (t === 'SERVICE' || t === 'SECTION_HEADER' || t === 'SUBTOTAL' || t === 'SEPARATOR') {
                lastIdx = i;
                break;
            }
        }
        // hasServices=true impliziert immer einen Match im Loop oben (SERVICE oder
        // SECTION_HEADER mit Kindern werden beide vom Loop erfasst), daher gilt
        // lastIdx >= 0 by-construction. Kein separater -1-Fallback noetig:
        // prev.slice(0, lastIdx + 1) liefert den Praefix bis einschliesslich der
        // letzten Leistung.
        const closureBlock: DocBlock = { id: CLOSURE_BLOCK_ID, type: 'CLOSURE' };
        return [...prev.slice(0, lastIdx + 1), closureBlock, ...prev.slice(lastIdx + 1)];
    }

    if (!hasServices && closureIdx !== -1) {
        return prev.filter(b => b.id !== CLOSURE_BLOCK_ID);
    }

    return prev;
}

/**
 * Fuegt einen Block vor dem ersten NACH-Textbaustein ein. Wenn keine
 * NACH-Bloecke vorhanden sind, wird ans Ende angehaengt. Respektiert
 * CLOSURE-Constraint fuer SERVICE und SECTION_HEADER.
 */
export function insertBeforeNachtexte(prev: DocBlock[], block: DocBlock): DocBlock[] {
    const closureIdx = prev.findIndex(b => b.id === CLOSURE_BLOCK_ID);
    const firstNachIdx = prev.findIndex(b => b.textbausteinRolle === 'NACH');
    // SERVICE/SECTION_HEADER: bevorzugt vor CLOSURE; Fallback: vor erstem NACH-Text.
    // Bug 2026-05-16: Ohne den Fallback landete die erste Leistung in einem frisch
    // angelegten Dokument (Vor-/Nachtext bereits geladen, aber noch keine Leistung
    // -> kein CLOSURE) ans Ende, also hinter dem NACH-Textbaustein.
    const limit = block.type === 'SERVICE' || block.type === 'SECTION_HEADER'
        ? (closureIdx !== -1 ? closureIdx : firstNachIdx)
        : firstNachIdx;
    if (limit === -1) {
        return [...prev, block];
    }
    return [
        ...prev.slice(0, limit),
        block,
        ...prev.slice(limit),
    ];
}

/**
 * Fuegt einen Block direkt nach dem aktuell selektierten Block ein, falls
 * Anker existiert (typischerweise activeEditorId). Sonst Fallback auf
 * "vor erstem NACH-Textbaustein". Respektiert die CLOSURE-Constraint: SERVICE
 * und SECTION_HEADER duerfen nie nach CLOSURE_BLOCK_ID landen.
 *
 * Sonderfaelle:
 *  - Anker liegt innerhalb einer SECTION (als child): SERVICE- und TEXT-Bloecke
 *    werden in dieselbe Section hinter dem Anker einsortiert; SECTION_HEADER
 *    und andere Container-Typen landen auf Root-Ebene direkt nach der Section
 *    (Sections duerfen nicht verschachtelt werden).
 *  - Anker ist null, leer oder selbst der CLOSURE-Marker -> Fallback.
 */
export function insertAtAnchor(
    prev: DocBlock[],
    block: DocBlock,
    anchorId: string | null,
): DocBlock[] {
    if (!anchorId || anchorId === CLOSURE_BLOCK_ID) {
        return insertBeforeNachtexte(prev, block);
    }
    const closureIdx = prev.findIndex(b => b.id === CLOSURE_BLOCK_ID);

    const topAnchorIdx = prev.findIndex(b => b.id === anchorId);
    if (topAnchorIdx !== -1) {
        let insertIdx = topAnchorIdx + 1;
        if (block.type === 'SERVICE' || block.type === 'SECTION_HEADER') {
            if (closureIdx !== -1 && insertIdx > closureIdx) {
                insertIdx = closureIdx;
            } else if (closureIdx === -1) {
                // Ohne CLOSURE auf den ersten NACH-Textbaustein clampen, damit eine
                // Leistung nicht hinter dem Nachtext landet (Bug 2026-05-16: User hat
                // den NACH-Block fokussiert und dann "+ Leistung" gedrueckt).
                const firstNachIdx = prev.findIndex(b => b.textbausteinRolle === 'NACH');
                if (firstNachIdx !== -1 && insertIdx > firstNachIdx) {
                    insertIdx = firstNachIdx;
                }
            }
        }
        return [...prev.slice(0, insertIdx), block, ...prev.slice(insertIdx)];
    }

    for (let i = 0; i < prev.length; i++) {
        const b = prev[i];
        if (b.type === 'SECTION_HEADER' && b.children) {
            const childIdx = b.children.findIndex(c => c.id === anchorId);
            if (childIdx !== -1) {
                if (block.type === 'SERVICE' || block.type === 'TEXT') {
                    const newChildren = [...b.children];
                    newChildren.splice(childIdx + 1, 0, block);
                    return prev.map(x => x.id === b.id ? { ...x, children: newChildren } : x);
                }
                let insertIdx = i + 1;
                if (block.type === 'SECTION_HEADER'
                    && closureIdx !== -1 && insertIdx > closureIdx) {
                    insertIdx = closureIdx;
                }
                return [...prev.slice(0, insertIdx), block, ...prev.slice(insertIdx)];
            }
        }
    }

    return insertBeforeNachtexte(prev, block);
}

/**
 * Validiert eine Root-Level-Reorder-Operation gegen die CLOSURE-Constraint.
 * Liefert:
 *  - { ok: true } wenn der Move erlaubt ist
 *  - { ok: false, reason: 'SERVICE_AFTER_CLOSURE' } wenn eine Leistung
 *    oder ein Bauabschnitt hinter den CLOSURE-Marker geschoben wuerde
 *  - { ok: false, reason: 'CLOSURE_BEFORE_LAST_SERVICE' } wenn der CLOSURE
 *    selbst vor die letzte Leistung/Bauabschnitt geschoben wuerde
 *
 * `newOrder` ist das Array nach arrayMove (dnd-kit). `activeId` ist die ID
 * des aktiv gedraggten Blocks.
 */
export type MoveValidation =
    | { ok: true }
    | { ok: false; reason: 'SERVICE_AFTER_CLOSURE' | 'CLOSURE_BEFORE_LAST_SERVICE' };

export function validateRootReorder(newOrder: DocBlock[], activeId: string): MoveValidation {
    const closureIdx = newOrder.findIndex(b => b.id === CLOSURE_BLOCK_ID);
    if (closureIdx === -1) return { ok: true };

    const activeIdx = newOrder.findIndex(b => b.id === activeId);
    if (activeIdx === -1) return { ok: true };
    const activeBlock = newOrder[activeIdx];

    // Constraint 1: SERVICE oder SECTION_HEADER (Bauabschnitt) duerfen nie
    // hinter dem CLOSURE-Marker landen.
    if ((activeBlock.type === 'SERVICE' || activeBlock.type === 'SECTION_HEADER')
        && activeIdx > closureIdx) {
        return { ok: false, reason: 'SERVICE_AFTER_CLOSURE' };
    }

    // Constraint 2: CLOSURE selbst darf nicht vor die letzte Leistung/Bauabschnitt
    // wandern. Suche letzten SERVICE/SECTION_HEADER von hinten.
    if (activeBlock.id === CLOSURE_BLOCK_ID) {
        let lastServiceIdx = -1;
        for (let i = newOrder.length - 1; i >= 0; i--) {
            const t = newOrder[i].type;
            if (t === 'SERVICE' || t === 'SECTION_HEADER') {
                lastServiceIdx = i;
                break;
            }
        }
        if (lastServiceIdx !== -1 && closureIdx < lastServiceIdx) {
            return { ok: false, reason: 'CLOSURE_BEFORE_LAST_SERVICE' };
        }
    }

    return { ok: true };
}

/**
 * Fuegt einen Block als letztes Kind in einen Bauabschnitt (SECTION_HEADER) ein.
 *
 * Anwendungsfall: Das "+"-Symbol direkt im Bauabschnitt-Header. Damit kann der
 * User Leistungen, Stundensaetze oder Textbausteine gezielt INNERHALB der
 * Section einfuegen, statt auf Root-Ebene.
 *
 * Verhalten:
 *  - Findet die Section per sectionId und haengt block ans Ende von children an.
 *  - Wenn die Section nicht existiert: Fallback auf insertBeforeNachtexte.
 *  - Block-Typen, die in einer Section nicht sinnvoll sind (SECTION_HEADER,
 *    SEPARATOR, SUBTOTAL, CLOSURE), werden ebenfalls auf Root-Ebene
 *    eingefuegt – Sections duerfen nicht verschachtelt werden.
 */
export function insertIntoSection(
    prev: DocBlock[],
    block: DocBlock,
    sectionId: string,
): DocBlock[] {
    const isChildCompatible = block.type === 'SERVICE' || block.type === 'TEXT';
    if (!isChildCompatible) {
        return insertBeforeNachtexte(prev, block);
    }
    const sectionIdx = prev.findIndex(b => b.id === sectionId && b.type === 'SECTION_HEADER');
    if (sectionIdx === -1) {
        return insertBeforeNachtexte(prev, block);
    }
    return prev.map((b, i) => {
        if (i !== sectionIdx) return b;
        const children = b.children ?? [];
        return { ...b, children: [...children, block] };
    });
}

/**
 * Fuegt mehrere Bloecke gemeinsam vor dem CLOSURE-Marker bzw. vor dem ersten
 * NACH-Textbaustein ein. Genutzt vom GAEB-Import (Bulk-Insert).
 */
export function insertBlocksBeforeClosure(prev: DocBlock[], blocksToInsert: DocBlock[]): DocBlock[] {
    if (blocksToInsert.length === 0) return prev;
    const closureIdx = prev.findIndex(b => b.id === CLOSURE_BLOCK_ID);
    const firstNachIdx = prev.findIndex(b => b.textbausteinRolle === 'NACH');
    let insertAt = prev.length;
    if (closureIdx !== -1) insertAt = Math.min(insertAt, closureIdx);
    if (firstNachIdx !== -1) insertAt = Math.min(insertAt, firstNachIdx);
    return [
        ...prev.slice(0, insertAt),
        ...blocksToInsert,
        ...prev.slice(insertAt),
    ];
}
