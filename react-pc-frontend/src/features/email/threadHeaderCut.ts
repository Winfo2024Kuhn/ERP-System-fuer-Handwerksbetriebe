import { BLOCK_TAGS, isSpacer } from './threadQuoteDom';

export const FORWARD_MARKER = /^[-\s]*(?:Weitergeleitete Nachricht|Forwarded message|Anfang der weitergeleiteten Nachricht|Begin forwarded message)/i;
const ORIGINAL_SEPARATOR = /^-{2,}\s*(?:Ursprüngliche Nachricht|Original-?Nachricht|Originalnachricht|Original Message)\s*-{2,}$/i;
const UNDERSCORE_SEPARATOR = /^_{10,}$/;
const HEADER_LABEL = /^(Von|From|Gesendet|Sent|Datum|Date|An|To|Betreff|Subject|Cc|Kopie)\s*:/i;
const HEADER_CATEGORY: Record<string, 'from' | 'date' | 'to' | 'subject' | undefined> = {
    von: 'from', from: 'from',
    gesendet: 'date', sent: 'date', datum: 'date', date: 'date',
    an: 'to', to: 'to',
    betreff: 'subject', subject: 'subject',
};
/** Outlook classic/mobile and OWA put the reply header into a box with a top border. */
const SEPARATOR_STYLE = /border-top\s*:\s*solid|border-style\s*:\s*solid\s+none\s+none/i;
const SKIPPED_TAGS = new Set(['SCRIPT', 'STYLE', 'HEAD', 'TITLE', 'BUTTON']);

interface TextLine {
    text: string;
    /** First node of the line: a text node (with offset) or a separator element (hr). */
    node: Node;
    offset: number;
    isRule?: boolean;
}

/** Linearises the visible text into lines the way a reader sees them (blocks, <br>, \n). */
function collectLines(doc: Document, excluded: Node[][]): TextLine[] {
    const lines: TextLine[] = [];
    let current: TextLine | null = null;
    const endLine = () => {
        if (current && current.text.trim()) lines.push({ ...current, text: current.text.replace(/\s+/g, ' ').trim() });
        current = null;
    };
    const isExcluded = (node: Node) => excluded.some(group => group.some(member => member.contains(node)));
    const visit = (node: Node) => {
        if (node.nodeType === 3) {
            const data = (node as Text).data;
            let position = 0;
            data.split('\n').forEach((segment, index) => {
                if (index > 0) endLine();
                if (!current && segment.trim()) {
                    current = { text: '', node, offset: position + segment.search(/\S/) };
                }
                if (current) current.text += segment;
                position += segment.length + 1;
            });
            return;
        }
        if (node.nodeType !== 1) return;
        const element = node as Element;
        if (SKIPPED_TAGS.has(element.tagName) || isExcluded(element)) return;
        if (element.tagName === 'BR') { endLine(); return; }
        if (element.tagName === 'HR') {
            endLine();
            lines.push({ text: '', node: element, offset: 0, isRule: true });
            return;
        }
        const block = BLOCK_TAGS.has(element.tagName);
        if (block) endLine();
        element.childNodes.forEach(visit);
        if (block) endLine();
    };
    visit(doc.body);
    endLine();
    return lines;
}

function headerCategory(line: string): string | undefined {
    const match = HEADER_LABEL.exec(line);
    return match ? HEADER_CATEGORY[match[1].toLowerCase()] : undefined;
}

/** Categories of a reply header starting at `start` (label lines, wrapped recipient lists allowed). */
function headerCategoriesAt(lines: TextLine[], start: number): Set<string> {
    const categories = new Set<string>();
    let gap = 0;
    for (let index = start; index < lines.length && index < start + 10; index++) {
        if (lines[index].isRule) break;
        const label = HEADER_LABEL.exec(lines[index].text);
        if (label) {
            gap = 0;
            const category = HEADER_CATEGORY[label[1].toLowerCase()];
            if (category) categories.add(category);
        } else if (++gap > 2) {
            break;
        }
    }
    return categories;
}

function climbToBlock(node: Node, body: HTMLElement): Node {
    let current = node;
    while (current.parentNode && current.parentNode !== body) {
        let previous = current.previousSibling;
        while (previous && isSpacer(previous) && previous.nodeName !== 'HR') previous = previous.previousSibling;
        if (previous) break;
        current = current.parentNode;
    }
    return current;
}

function hasSeparatorBox(node: Node, body: HTMLElement): boolean {
    for (let current: Node | null = node; current && current !== body; current = current.parentNode) {
        if (current.nodeType !== 1) continue;
        const element = current as Element;
        if (/divRplyFwdMsg$/.test(element.id) || SEPARATOR_STYLE.test(element.getAttribute('style') || '')) return true;
    }
    return false;
}

/**
 * Line where old history starts at `index`: an "Ursprüngliche Nachricht" separator, or a
 * header with Von + Datum and four categories (three suffice after a separator line/box).
 */
function findCutLine(lines: TextLine[], index: number, body: HTMLElement): TextLine | null {
    const line = lines[index];
    if (ORIGINAL_SEPARATOR.test(line.text)) return line;
    if (!headerCategory(line.text)) return null;
    const previous = lines[index - 1];
    const previousIsSeparator = !!previous && (!!previous.isRule || UNDERSCORE_SEPARATOR.test(previous.text));
    const categories = headerCategoriesAt(lines, index);
    const separated = previousIsSeparator || hasSeparatorBox(line.node, body);
    const complete = categories.has('from') && categories.has('date')
        && (categories.size === 4 || (separated && categories.size === 3));
    if (!complete) return null;
    return previousIsSeparator ? previous : line;
}

/**
 * reply: new text, then the first header cuts.
 * awaitForwardHeader: forwarded mail (flag from the backend), the first header introduces its content.
 * forwardHeader: label lines of the forwarded header. forwardBody: its text; the next reply header
 * or "Ursprüngliche Nachricht" cuts, a further forward marker opens the next forwarded mail.
 */
type Phase = 'reply' | 'awaitForwardHeader' | 'forwardHeader' | 'forwardBody';

/**
 * Unmarked reply history (Outlook, OWA, Outlook mobile, Samsung, Telekom, web.de/GMX):
 * a header block "Von/Gesendet/An/Betreff" or an "Ursprüngliche Nachricht" separator.
 * Clients that write such headers always top-post, so everything after it is old history.
 * Forwards ("Weitergeleitete Nachricht" or `keepForwardedContent`): the forwarded mail with
 * its header stays visible (nested forwards too), the cut starts at the next reply header or
 * "Ursprüngliche Nachricht" separator below it.
 * Without new (or forwarded) text before the cut nothing is collapsed.
 */
export function findHeaderCut(doc: Document, excluded: Node[][], keepForwardedContent: boolean): Node[] | null {
    const lines = collectLines(doc, excluded);
    let phase: Phase = keepForwardedContent ? 'awaitForwardHeader' : 'reply';
    let hasNewText = false;
    for (let index = 0; index < lines.length; index++) {
        const line = lines[index];
        if (line.isRule) continue;
        if (phase === 'forwardHeader') {
            if (HEADER_LABEL.test(line.text) || UNDERSCORE_SEPARATOR.test(line.text)) continue;
            phase = 'forwardBody';
        }
        const forwardStart = FORWARD_MARKER.test(line.text);
        const cutLine = forwardStart ? line : findCutLine(lines, index, doc.body);
        // A forward marker always opens a forwarded mail, also nested inside another forward.
        if (cutLine && (phase === 'awaitForwardHeader' || forwardStart)) {
            phase = 'forwardHeader';
            hasNewText = false;
            continue;
        }
        if (cutLine) return hasNewText ? collectTail(doc, cutLine) : null;
        if (!UNDERSCORE_SEPARATOR.test(line.text)) hasNewText = true;
    }
    return null;
}

/** Everything from the cut line to the end of the document, as sibling runs per ancestor. */
function collectTail(doc: Document, line: TextLine): Node[] {
    let start = line.node;
    if (start.nodeType === 3 && line.offset > 0 && (start as Text).data.slice(0, line.offset).trim()) {
        start = (start as Text).splitText(line.offset);
    }
    const cut = climbToBlock(start, doc.body);
    const nodes: Node[] = [];
    for (let sibling: Node | null = cut; sibling; sibling = sibling.nextSibling) nodes.push(sibling);
    for (let ancestor = cut.parentNode; ancestor && ancestor !== doc.body; ancestor = ancestor.parentNode) {
        for (let sibling = ancestor.nextSibling; sibling; sibling = sibling.nextSibling) nodes.push(sibling);
    }
    return nodes;
}
