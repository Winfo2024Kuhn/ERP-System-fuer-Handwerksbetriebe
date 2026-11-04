import { attributionBefore, isAttribution, looksLikeAttribution, startsLine } from './threadQuoteAttribution';
import { MARKED_QUOTE_SELECTOR } from './threadQuoteDom';

const QUOTE_LINE = /^\s*>/;
const isQuoteLine = (line: string) => QUOTE_LINE.test(line);
const isBlank = (line: string) => !line.trim();

/**
 * Start of a closing block of ">" lines (only blank lines after it), or -1.
 * An intro line right before the block belongs to it when it looks like an attribution
 * (short, ends in ":", digit or address); ordinary sentences like "bitte so einbauen:" stay.
 * A single ">" line only counts after a recognised attribution ("Breite > 3 m" stays).
 */
export function trailingQuoteStart(lines: string[]): number {
    let end = lines.length;
    while (end > 0 && isBlank(lines[end - 1])) end--;
    let start = end;
    while (start > 0 && (isQuoteLine(lines[start - 1]) || isBlank(lines[start - 1]))) start--;
    while (start < end && isBlank(lines[start])) start++;
    if (start === end) return -1;
    let intro = start - 1;
    while (intro >= 0 && isBlank(lines[intro])) intro--;
    const hasIntro = intro >= 0 && looksLikeAttribution(lines[intro]);
    const quoteLines = lines.slice(start, end).filter(isQuoteLine).length;
    if (quoteLines < 2 && !(hasIntro && isAttribution(lines[intro]))) return -1;
    return hasIntro ? intro : start;
}

function visibleTextNodes(doc: Document): Text[] {
    const walker = doc.createTreeWalker(doc.body, 4 /* SHOW_TEXT */);
    const texts: Text[] = [];
    while (walker.nextNode()) texts.push(walker.currentNode as Text);
    return texts;
}

const inMarkedQuote = (text: Text) => !!text.parentElement?.closest(MARKED_QUOTE_SELECTOR);

/**
 * The closing ">" block of the whole mail (t-online, Hetzner: no recognisable attribution).
 * Only when new text stands before it and no other ">" line does (otherwise: inline answers).
 * Works on one text node, i.e. plain text and `<pre>`, not on one `<p>`/`<br>` per line.
 * A block in the node's first line must also start a visible line (not "Breite <b>&gt; 3 m</b>").
 */
function findTrailingQuote(texts: Text[]): { text: Text; start: number } | null {
    const visible = texts.filter(text => text.data.trim() && !text.parentElement?.closest('style,script,head,title'));
    const last = visible[visible.length - 1];
    if (!last || inMarkedQuote(last)) return null;
    const lines = last.data.split('\n');
    const start = trailingQuoteStart(lines);
    if (start < 0 || (start === 0 && !startsLine(last))) return null;
    const before = visible.slice(0, -1).filter(text => !inMarkedQuote(text))
        .flatMap(text => text.data.split('\n'))
        .concat(lines.slice(0, start));
    return before.some(line => !isBlank(line)) && !before.some(isQuoteLine) ? { text: last, start } : null;
}

function createQuote(doc: Document, text: string): HTMLElement {
    const quote = doc.createElement('div');
    quote.className = 'email-quote';
    quote.style.whiteSpace = 'pre-wrap';
    quote.textContent = text;
    return quote;
}

/** Rebuilds one text node: ">" runs after an attribution and the closing block become quote blocks. */
function splitQuotedLines(doc: Document, lines: string[], attributed: boolean, trailingStart: number): DocumentFragment {
    const fragment = doc.createDocumentFragment();
    let citationContext = attributed;
    for (let index = 0; index < lines.length;) {
        if (trailingStart >= 0 && index >= trailingStart) {
            fragment.append(createQuote(doc, lines.slice(index).join('\n')));
            break;
        }
        const start = index;
        let quoteStart = index + 1;
        while (quoteStart < lines.length && isBlank(lines[quoteStart])) quoteStart++;
        if (isAttribution(lines[index]) && isQuoteLine(lines[quoteStart] || '')) {
            index = quoteStart;
            citationContext = true;
        }
        if (citationContext && isQuoteLine(lines[index])) {
            while (index < lines.length && isQuoteLine(lines[index])) index++;
            fragment.append(createQuote(doc, lines.slice(start, index).join('\n')));
        } else {
            fragment.append(doc.createTextNode(lines[index] + (index < lines.length - 1 ? '\n' : '')));
            index++;
        }
    }
    return fragment;
}

/**
 * Wraps prefixed plain-text citations ("> …") in `.email-quote` blocks: after a recognised
 * attribution, and the closing ">" block of the mail. Unprefixed inline answers stay visible.
 */
export function markPlainTextQuotes(doc: Document): void {
    const texts = visibleTextNodes(doc);
    const trailing = findTrailingQuote(texts);
    for (const text of texts) {
        if (text.parentElement?.closest(`${MARKED_QUOTE_SELECTOR},style,script`)) continue;
        const lines = text.data.split('\n');
        if (!lines.some(isQuoteLine)) continue;
        const trailingStart = trailing?.text === text ? trailing.start : -1;
        text.replaceWith(splitQuotedLines(doc, lines, attributionBefore(text) !== null, trailingStart));
    }
}
