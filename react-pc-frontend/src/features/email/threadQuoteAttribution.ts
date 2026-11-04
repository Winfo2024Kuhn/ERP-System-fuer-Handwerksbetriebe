import { BLOCK_TAGS, isSpacer } from './threadQuoteDom';

const MAX_ATTRIBUTION_LENGTH = 500;
/** Real attribution lines have a handful of inline parts (text, link, text). Longer runs are given up. */
const MAX_INLINE_NODES = 20;

/** True when an address (`@`) stands before the keyword, e.g. "Max <max@example.com> schrieb am …". */
function addressBefore(line: string, keyword: string): boolean {
    const at = line.indexOf(keyword);
    return at > 0 && line.lastIndexOf('@', at) !== -1;
}

/**
 * Known reply attributions, checked on the lower-cased line. Plain string checks
 * instead of regexes, so long hostile lines cost linear time (no ReDoS).
 */
const ATTRIBUTION_FORMATS: ((line: string) => boolean)[] = [
    // Apple Mail, Thunderbird, WEB.DE/GMX app: "Am 9. Okt. 2026 um 10:00 schrieb Max Mustermann <max@example.com>:"
    // Yahoo/AOL: "Am Dienstag, 7. Oktober 2026 um 10:00:00 MESZ hat Max Mustermann <…> Folgendes geschrieben:"
    line => line.startsWith('am ')
        && (line.includes(' schrieb') || (line.endsWith(' geschrieben:') && line.includes(' hat '))),
    // English clients: "On Sep 8, 2026, max@example.com wrote:"
    line => line.startsWith('on ') && line.endsWith(' wrote:'),
    // Gmail: "Max Mustermann <max@example.com> schrieb am Do., 9. Okt. 2026 um 10:00:" (English: "… wrote on …:")
    line => addressBefore(line, ' schrieb am ') || addressBefore(line, ' wrote on '),
    // Webmail plain text: "09.10.2026 10:00 - max@example.com schrieb:"
    line => line.endsWith(' schrieb:') && line.includes('@'),
];

function normalise(text: string): string {
    return text.replace(/\s+/g, ' ').trim().toLowerCase();
}

/** Pre-check shared with unknown intro lines: short, ends in ":", contains a digit or an address. */
export function looksLikeAttribution(text: string): boolean {
    if (text.length > MAX_ATTRIBUTION_LENGTH * 4) return false;
    const line = normalise(text);
    return line.length < MAX_ATTRIBUTION_LENGTH && line.endsWith(':') && /\d|@/.test(line);
}

/**
 * A citation needs a reply attribution, not merely a short preceding paragraph:
 * a line passing `looksLikeAttribution` in one of the known formats.
 */
export function isAttribution(text: string): boolean {
    if (!looksLikeAttribution(text)) return false;
    const line = normalise(text);
    return ATTRIBUTION_FORMATS.some(matches => matches(line));
}

function isInline(node: Node): boolean {
    return node.nodeType !== 1 || (!BLOCK_TAGS.has(node.nodeName) && !['BR', 'HR', 'BODY', 'HTML'].includes(node.nodeName));
}

function textOf(node: Node): string {
    return node.nodeType === 8 ? '' : node.textContent || '';
}

interface InlineRun {
    nodes: Node[];
    /** 'boundary': a block or <br> stands before the run, 'start': the run starts its parent, 'limit': given up. */
    stop: 'boundary' | 'start' | 'limit';
}

/** Inline siblings (text, links, comments) back to the previous block boundary or <br>, at most 20 nodes. */
function inlineRunEndingAt(last: Node | null): InlineRun {
    const nodes: Node[] = [];
    let length = 0;
    let current = last;
    while (current && isInline(current)) {
        if (nodes.length >= MAX_INLINE_NODES || length >= MAX_ATTRIBUTION_LENGTH) return { nodes: nodes.reverse(), stop: 'limit' };
        nodes.push(current);
        length += textOf(current).length;
        current = current.previousSibling;
    }
    return { nodes: nodes.reverse(), stop: current ? 'boundary' : 'start' };
}

/** True when no visible text stands before the node on its line (inline parents included). */
export function startsLine(node: Node): boolean {
    for (let current: Node = node; ;) {
        const run = inlineRunEndingAt(current.previousSibling);
        if (run.stop === 'limit' || run.nodes.some(part => textOf(part).trim())) return false;
        const parent = current.parentNode;
        if (run.stop === 'boundary' || !parent || !isInline(parent)) return true;
        current = parent;
    }
}

/**
 * The attribution line right before a quote, plus the spacers in between, or null.
 * The line is either one block element (Gmail `gmail_attr`, Apple `<div>`) or a run of
 * inline siblings, e.g. WEB.DE/GMX: `Am 09.10.26, 10:00 schrieb Max &lt;<a>max@example.com</a>&gt;:<blockquote>`.
 */
export function attributionBefore(node: Node): Node[] | null {
    const spacers: Node[] = [];
    let previous = node.previousSibling;
    while (previous && isSpacer(previous)) {
        spacers.push(previous);
        previous = previous.previousSibling;
    }
    spacers.reverse();
    if (!previous) return null;
    if (!isInline(previous)) return isAttribution(textOf(previous)) ? [previous, ...spacers] : null;
    const run = inlineRunEndingAt(previous);
    if (run.stop === 'limit') return null;
    return isAttribution(run.nodes.map(textOf).join('')) ? [...run.nodes, ...spacers] : null;
}
