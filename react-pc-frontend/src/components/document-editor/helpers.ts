import type { DocBlock } from './types';

export const unitMap: Record<string, string> = {
    'LAUFENDE_METER': 'lfm',
    'QUADRATMETER': 'm²',
    'KILOGRAMM': 'kg',
    'STUECK': 'Stk'
};

/**
 * Extracts the dominant font size from HTML content.
 * Scans ALL font-size declarations (not just the first one) and returns
 * the most frequently used size (in pt, clamped to 10-20).
 *
 * WICHTIG: TiptapEditor speichert immer in pt (z.B. "12pt").
 * Falls px gefunden wird, wird es zu pt konvertiert (px * 0.75 = pt).
 */
export const extractFontSizeFromHtml = (html: string): number | undefined => {
    if (!html) return undefined;

    const fontSizeRegex = /font-size:\s*(\d+(?:\.\d+)?)(pt|px|em|rem)?/gi;
    const sizes: number[] = [];
    let match: RegExpExecArray | null;

    while ((match = fontSizeRegex.exec(html)) !== null) {
        let size = parseFloat(match[1]);
        const unit = match[2]?.toLowerCase();
        if (unit === 'px') {
            size = size * 0.75;
        } else if (unit === 'em' || unit === 'rem') {
            size = size * 10; // rough estimate: 1em ≈ 10pt base
        }
        sizes.push(Math.max(10, Math.min(20, Math.round(size))));
    }

    if (sizes.length === 0) return undefined;

    // Return the most frequently used font-size (dominant size for the block)
    const freq = new Map<number, number>();
    for (const s of sizes) {
        freq.set(s, (freq.get(s) || 0) + 1);
    }
    let dominant = sizes[0];
    let maxCount = 0;
    for (const [size, count] of freq) {
        if (count > maxCount) {
            maxCount = count;
            dominant = size;
        }
    }
    return dominant;
};

/**
 * Checks if HTML content contains bold formatting.
 */
export const extractBoldFromHtml = (html: string): boolean => {
    if (!html) return false;
    return /<(strong|b)\b/i.test(html) || /font-weight:\s*(bold|700|800|900)/i.test(html);
};

export function buildAdresse(kunde: any): string {
    if (!kunde) return '';
    const parts = [];
    if (kunde.name) parts.push(kunde.name);
    if (kunde.strasse) parts.push(kunde.strasse);
    if (kunde.plz || kunde.ort) parts.push(`${kunde.plz || ''} ${kunde.ort || ''}`.trim());
    return parts.join('\n');
}

export function buildAdresseFromAnfrage(anfrage: any): string {
    const parts = [];
    if (anfrage.kundenName) parts.push(anfrage.kundenName);
    if (anfrage.kundenStrasse) parts.push(anfrage.kundenStrasse);
    if (anfrage.kundenPlz || anfrage.kundenOrt) parts.push(`${anfrage.kundenPlz || ''} ${anfrage.kundenOrt || ''}`.trim());
    return parts.join('\n');
}

export function blocksToHtml(blocks: DocBlock[]): string {
    return blocks.map(block => {
        if (block.type === 'TEXT') {
            return block.content || '';
        } else if (block.type === 'SEPARATOR') {
            return '<hr/>';
        } else if (block.type === 'SECTION_HEADER') {
            let html = `<h3>${block.sectionLabel || ''}</h3>`;
            if (block.children) {
                html += block.children.map(child => {
                    if (child.type === 'SERVICE') {
                        return `<div class="service-line">
                            <span class="pos">${child.pos}</span>
                            <span class="title">${child.title}</span>
                            <span class="total">${(child.quantity || 0) * (child.price || 0)}</span>
                        </div>`;
                    }
                    return '';
                }).join('\n');
            }
            return html;
        } else if (block.type === 'SERVICE') {
            return `<div class="service-line">
                <span class="pos">${block.pos}</span>
                <span class="qty">${block.quantity}</span>
                <span class="unit">${block.unit}</span>
                <span class="title">${block.title}</span>
                <span class="price">${block.price}</span>
                <span class="total">${(block.quantity || 0) * (block.price || 0)}</span>
            </div>`;
        }
        return '';
    }).join('\n');
}

/**
 * Calculates netto total from all services (root + nested in sections).
 */
/**
 * Returns the line total for a single SERVICE block, applying per-position discount.
 */
 export function serviceLineTotal(b: DocBlock): number {
    const base = (b.quantity || 0) * (b.price || 0);
    if (b.discount && b.discount > 0) {
        return base * (1 - b.discount / 100);
    }
    return base;
}

export function calculateNetto(blocks: DocBlock[]): number {
    let total = 0;
    for (const b of blocks) {
        if (b.type === 'SERVICE' && !b.optional) {
            total += serviceLineTotal(b);
        }
        if (b.type === 'SECTION_HEADER' && b.children) {
            for (const child of b.children) {
                if (child.type === 'SERVICE' && !child.optional) {
                    total += serviceLineTotal(child);
                }
            }
        }
    }
    return total;
}

/**
 * Gets all SERVICE blocks in document order (root + nested in sections).
 */
export function getAllServiceBlocks(blocks: DocBlock[]): DocBlock[] {
    const result: DocBlock[] = [];
    for (const b of blocks) {
        if (b.type === 'SERVICE') result.push(b);
        if (b.type === 'SECTION_HEADER' && b.children) {
            for (const child of b.children) {
                if (child.type === 'SERVICE') result.push(child);
            }
        }
    }
    return result;
}

/**
 * Calculates the subtotal for a section's children.
 */
export function calculateSectionSubtotal(section: DocBlock): number {
    if (!section.children) return 0;
    return section.children
        .filter(c => c.type === 'SERVICE' && !c.optional)
        .reduce((sum, c) => sum + serviceLineTotal(c), 0);
}

/**
 * Flattens nested blocks (sections with children) into a flat list for PDF backend.
 * SECTION_HEADER → its children → auto-generated SUBTOTAL
 * Injects hierarchical position strings (e.g. "1.0", "1.1") from buildPositionMap.
 */
export function flattenBlocksForPdf(blocks: DocBlock[]): DocBlock[] {
    const posMap = buildPositionMap(blocks);
    const flat: DocBlock[] = [];
    for (const b of blocks) {
        if (b.type === 'SECTION_HEADER') {
            // Add section header with position (e.g. "1.0")
            flat.push({ ...b, children: undefined, pos: posMap.get(b.id) || '' });
            // Add all children with positions (e.g. "1.1", "1.2")
            if (b.children && b.children.length > 0) {
                for (const child of b.children) {
                    flat.push({ ...child, pos: posMap.get(child.id) || '' });
                }
                // Auto-generate a subtotal block
                flat.push({
                    id: `subtotal-${b.id}`,
                    type: 'SUBTOTAL',
                    sectionLabel: b.sectionLabel,
                });
            }
        } else if (b.type !== 'SUBTOTAL') {
            // Root-level blocks get their position from the map
            if (b.type === 'SERVICE') {
                flat.push({ ...b, pos: posMap.get(b.id) || '' });
            } else {
                flat.push(b);
            }
        }
    }
    return flat;
}

/**
 * Finds which container (root or section ID) a block belongs to.
 */
export function findBlockContainer(blocks: DocBlock[], itemId: string): string | null {
    for (const b of blocks) {
        if (b.id === itemId) return 'root';
        if (b.type === 'SECTION_HEADER' && b.children) {
            if (b.children.some(c => c.id === itemId)) return b.id;
        }
    }
    return null;
}

/**
 * Formats a number as German locale currency string.
 */
export function formatCurrency(value: number): string {
    return value.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/**
 * Builds a position map for all blocks with hierarchical numbering.
 * SECTION_HEADER → "1.0", its children → "1.1", "1.2", etc.
 * Root-level SERVICE → next top-level number ("2", "3", etc.)
 * Other block types are skipped.
 * Returns Map<blockId, positionString>.
 */
export function buildPositionMap(blocks: DocBlock[]): Map<string, string> {
    const map = new Map<string, string>();
    let topCounter = 1;

    for (const block of blocks) {
        if (block.type === 'SECTION_HEADER') {
            map.set(block.id, `${topCounter}.0`);
            if (block.children) {
                let serviceCounter = 1;
                for (const child of block.children) {
                    if (child.type === 'SERVICE') {
                        map.set(child.id, `${topCounter}.${serviceCounter}`);
                        serviceCounter++;
                    }
                }
            }
            topCounter++;
        } else if (block.type === 'SERVICE') {
            map.set(block.id, `${topCounter}`);
            topCounter++;
        }
    }
    return map;
}

export interface ClosureSectionSummary {
    label: string;
    total: number;
    position: string;
}

export interface ClosureSummary {
    sections: ClosureSectionSummary[];
    sonstigeTotal: number;
    hasSonstige: boolean;
    gesamtNetto: number;
}

/**
 * Computes the closure summary breakdown:
 * - Each Bauabschnitt with label + sum of non-optional services
 * - "Sonstige Leistungen" for root-level services not in any section
 * - Grand total
 */
export function computeClosureSummary(blocks: DocBlock[]): ClosureSummary {
    const posMap = buildPositionMap(blocks);
    const sections: ClosureSectionSummary[] = [];
    let sonstigeTotal = 0;

    for (const block of blocks) {
        if (block.type === 'CLOSURE') continue;
        if (block.type === 'SECTION_HEADER') {
            const sectionTotal = (block.children || [])
                .filter(c => c.type === 'SERVICE' && !c.optional)
                .reduce((sum, c) => sum + serviceLineTotal(c), 0);
            sections.push({
                label: block.sectionLabel || 'Bauabschnitt',
                total: sectionTotal,
                position: posMap.get(block.id) || '',
            });
        } else if (block.type === 'SERVICE' && !block.optional) {
            sonstigeTotal += serviceLineTotal(block);
        }
    }

    const gesamtNetto = sections.reduce((s, sec) => s + sec.total, 0) + sonstigeTotal;

    return {
        sections,
        sonstigeTotal,
        hasSonstige: sonstigeTotal > 0 && sections.length > 0,
        gesamtNetto,
    };
}
