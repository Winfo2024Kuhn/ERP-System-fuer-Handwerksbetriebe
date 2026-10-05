import { escapeHtml, isLikelyPlainText } from '../../components/emailContentFrameUtils';

export interface ThreadQuoteOptions {
    /**
     * Weitergeleitete Mails: Der erste Kopf ("Von/Gesendet/An/Betreff") leitet den
     * eigentlichen Inhalt ein und darf nicht als alter Verlauf eingeklappt werden.
     */
    keepForwardedContent?: boolean;
}

/** A citation needs a reply attribution, not merely a short preceding paragraph. */
function isAttribution(text: string): boolean {
    const line = text.replace(/\s+/g, ' ').trim();
    return line.length < 500 && /\d|@/.test(line) && (
        /^Am .+ schrieb(?: .+)?:$/i.test(line)
        || /^On .+ wrote:$/i.test(line)
    );
}

const FORWARD_MARKER = /^[-\s]*(?:Weitergeleitete Nachricht|Forwarded message|Anfang der weitergeleiteten Nachricht|Begin forwarded message)/i;
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
const BLOCK_TAGS = new Set(['ADDRESS', 'ARTICLE', 'BLOCKQUOTE', 'CENTER', 'DIV', 'DL', 'DT', 'DD', 'FOOTER',
    'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'HEADER', 'LI', 'OL', 'P', 'PRE', 'SECTION', 'TABLE', 'TR', 'UL']);
const SKIPPED_TAGS = new Set(['SCRIPT', 'STYLE', 'HEAD', 'TITLE', 'BUTTON']);
const TABLE_PARTS = new Set(['TABLE', 'TBODY', 'THEAD', 'TFOOT', 'TR']);

function isSpacer(node: Node): boolean {
    return !node.textContent?.trim() && (node.nodeType === 3
        || (node.nodeType === 1 && !((node as Element).querySelector('img,table,iframe'))
            && ['BR', 'DIV', 'P', 'HR'].includes(node.nodeName)));
}

/** Wrap only explicitly prefixed plain-text citations; unprefixed inline answers stay visible. */
function markPlainTextQuotes(doc: Document): void {
    const walker = doc.createTreeWalker(doc.body, 4 /* SHOW_TEXT */);
    const texts: Text[] = [];
    while (walker.nextNode()) texts.push(walker.currentNode as Text);
    for (const text of texts) {
        if (text.parentElement?.closest('blockquote,.email-quote,.gmail_quote,.yahoo_quoted,style,script')) continue;
        const lines = text.data.split('\n');
        if (!lines.some(line => /^\s*>/.test(line))) continue;
        const fragment = doc.createDocumentFragment();
        let previous = text.previousSibling;
        while (previous && isSpacer(previous)) previous = previous.previousSibling;
        let citationContext = isAttribution(previous?.textContent || '');
        for (let index = 0; index < lines.length;) {
            const start = index;
            let quoteStart = index + 1;
            while (quoteStart < lines.length && !lines[quoteStart].trim()) quoteStart++;
            const attributed = isAttribution(lines[index]) && /^\s*>/.test(lines[quoteStart] || '');
            if (attributed) { index = quoteStart; citationContext = true; }
            if (citationContext && /^\s*>/.test(lines[index])) {
                while (index < lines.length && /^\s*>/.test(lines[index])) index++;
                const quote = doc.createElement('div');
                quote.className = 'email-quote';
                quote.style.whiteSpace = 'pre-wrap';
                quote.textContent = lines.slice(start, index).join('\n');
                fragment.append(quote);
            } else {
                fragment.append(doc.createTextNode(lines[index] + (index < lines.length - 1 ? '\n' : '')));
                index++;
            }
        }
        text.replaceWith(fragment);
    }
}

/** Explicitly marked citations (blockquote, Gmail, Yahoo, own replies). Later answers stay visible. */
function findMarkedQuoteGroups(doc: Document): Node[][] {
    const groups: Node[][] = [];
    const candidates = Array.from(doc.querySelectorAll('blockquote,.email-quote,.gmail_quote,.yahoo_quoted'));
    for (const element of candidates) {
        if (groups.some(group => group.some(node => node.contains(element)))) continue;
        // Gmail can wrap a quote AND a new answer. Collapse the explicit quote inside it.
        if (element.matches('.gmail_quote,.yahoo_quoted') && element.querySelector('blockquote')) continue;
        // A Gmail forward is the actual content, not old history.
        if (element.matches('.gmail_quote') && FORWARD_MARKER.test((element.textContent || '').trim())) continue;
        const group: Node[] = [element];
        let previous = element.previousSibling;
        const spacers: Node[] = [];
        while (previous && isSpacer(previous)) {
            spacers.unshift(previous);
            previous = previous.previousSibling;
        }
        const attributed = previous && isAttribution(previous.textContent || '');
        if (element.tagName === 'BLOCKQUOTE' && !element.matches('[type="cite"],.email-quote')
            && !attributed && !element.closest('.gmail_quote,.yahoo_quoted')) continue;
        if (attributed) group.unshift(previous!, ...spacers);
        groups.push(group);
    }
    return groups;
}

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
        const category = headerCategory(lines[index].text);
        if (HEADER_LABEL.test(lines[index].text)) {
            gap = 0;
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
 * Unmarked reply history (Outlook, OWA, Outlook mobile, Samsung, Telekom, web.de/GMX):
 * a header block "Von/Gesendet/An/Betreff" or an "Ursprüngliche Nachricht" separator.
 * Clients that write such headers always top-post, so everything after it is old history.
 */
function findHeaderCut(doc: Document, excluded: Node[][], options: ThreadQuoteOptions): Node[] | null {
    if (options.keepForwardedContent) return null;
    const lines = collectLines(doc, excluded);
    let hasNewText = false;
    for (let index = 0; index < lines.length; index++) {
        const line = lines[index];
        if (line.isRule) continue;
        if (FORWARD_MARKER.test(line.text)) return null;
        let cutLine: TextLine | null = null;
        const previous = lines[index - 1];
        const previousIsSeparator = previous && (previous.isRule || UNDERSCORE_SEPARATOR.test(previous.text));
        if (ORIGINAL_SEPARATOR.test(line.text)) {
            cutLine = line;
        } else if (headerCategory(line.text)) {
            const categories = headerCategoriesAt(lines, index);
            const separated = previousIsSeparator || hasSeparatorBox(line.node, doc.body);
            if (categories.has('from') && categories.has('date')
                && (categories.size === 4 || (separated && categories.size === 3))) {
                cutLine = previousIsSeparator ? previous : line;
            }
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

/** Display-only parsing shared by the snippet and iframe. Never alters the saved email. */
export function findThreadQuoteGroups(doc: Document, options: ThreadQuoteOptions = {}): Node[][] {
    markPlainTextQuotes(doc);
    const marked = findMarkedQuoteGroups(doc);
    const tail = findHeaderCut(doc, marked, options);
    if (!tail) return marked;
    const beforeTail = marked.filter(group => !tail.some(node => node.contains(group[0])));
    return [...beforeTail, tail];
}

/** Splits a group into runs of adjacent siblings, so nodes under different parents stay in place. */
function toSiblingRuns(group: Node[]): Node[][] {
    const runs: Node[][] = [];
    for (const node of group) {
        const run = runs[runs.length - 1];
        if (run && run[run.length - 1].nextSibling === node) run.push(node);
        else runs.push([node]);
    }
    return runs;
}

function setHidden(element: HTMLElement, hidden: boolean, shownDisplay: string): void {
    element.hidden = hidden;
    if (hidden) element.style.setProperty('display', 'none', 'important');
    else if (shownDisplay) element.style.setProperty('display', shownDisplay);
    else element.style.removeProperty('display');
}

export function collapseThreadQuotes(doc: Document, onHeightChange: () => void, options: ThreadQuoteOptions = {}): void {
    if (doc.querySelector('[data-quote-btn]')) return;
    findThreadQuoteGroups(doc, options).forEach((group, index) => {
        const toggled: { element: HTMLElement; shownDisplay: string }[] = [];
        const runs = toSiblingRuns(group);
        const button = doc.createElement('button');
        runs.forEach((run, runIndex) => {
            const parent = run[0].parentNode!;
            if (runIndex === 0) {
                // In Tabellen braucht der Button eine eigene Zeile, sonst ist das Markup ungültig.
                if (TABLE_PARTS.has(parent.nodeName) && run[0].nodeName === 'TR') {
                    const row = doc.createElement('tr');
                    const cell = doc.createElement('td');
                    cell.colSpan = 100;
                    cell.append(button);
                    row.append(cell);
                    parent.insertBefore(row, run[0]);
                } else {
                    parent.insertBefore(button, run[0]);
                }
            }
            // A <div> is invalid inside table structures; hide the rows themselves there.
            if (TABLE_PARTS.has(parent.nodeName)) {
                run.filter(node => node.nodeType === 1).forEach(node => {
                    const element = node as HTMLElement;
                    toggled.push({ element, shownDisplay: element.style.getPropertyValue('display') });
                });
                return;
            }
            const wrapper = doc.createElement('div');
            parent.insertBefore(wrapper, run[0]);
            run.forEach(node => wrapper.append(node));
            toggled.push({ element: wrapper, shownDisplay: 'block' });
        });
        const ids = toggled.map(({ element }, position) => {
            if (!element.id) element.id = position === 0 ? `email-quoted-history-${index}` : `email-quoted-history-${index}-${position}`;
            return element.id;
        });
        toggled.forEach(({ element, shownDisplay }) => setHidden(element, true, shownDisplay));
        button.type = 'button';
        button.dataset.quoteBtn = '1';
        button.textContent = 'Zitierten Verlauf anzeigen';
        button.setAttribute('aria-expanded', 'false');
        button.setAttribute('aria-controls', ids.join(' '));
        button.addEventListener('click', () => {
            const show = button.getAttribute('aria-expanded') !== 'true';
            toggled.forEach(({ element, shownDisplay }) => setHidden(element, !show, shownDisplay));
            button.setAttribute('aria-expanded', String(show));
            button.textContent = show ? 'Zitierten Verlauf ausblenden' : 'Zitierten Verlauf anzeigen';
            onHeightChange();
        });
    });
}

export function getThreadPreview(content: string, options: ThreadQuoteOptions = {}): string {
    const doc = new DOMParser().parseFromString(isLikelyPlainText(content) ? escapeHtml(content) : content, 'text/html');
    findThreadQuoteGroups(doc, options).forEach(group => group.forEach(node => node.parentNode?.removeChild(node)));
    doc.querySelectorAll('script,style,head').forEach(element => element.remove());
    doc.querySelectorAll('br,p,div,li,tr').forEach(element => element.append(doc.createTextNode(' ')));
    return (doc.body.textContent || '').replace(/\s+/g, ' ').trim();
}
