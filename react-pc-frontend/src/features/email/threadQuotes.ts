import { escapeHtml, isLikelyPlainText } from '../../components/emailContentFrameUtils';
import { FORWARD_MARKER, findHeaderCut } from './threadHeaderCut';
import { markPlainTextQuotes } from './threadPlainTextQuotes';
import { attributionBefore } from './threadQuoteAttribution';
import { MARKED_QUOTE_SELECTOR, QUOTE_WRAPPER_SELECTOR } from './threadQuoteDom';

export interface ThreadQuoteOptions {
    /**
     * Weitergeleitete Mails: Der erste Kopf ("Von/Gesendet/An/Betreff") leitet den
     * eigentlichen Inhalt ein und darf nicht als alter Verlauf eingeklappt werden.
     * Erst ein weiterer Kopf bzw. Trenner darunter beginnt den alten Verlauf.
     */
    keepForwardedContent?: boolean;
}

const TABLE_PARTS = new Set(['TABLE', 'TBODY', 'THEAD', 'TFOOT', 'TR']);

/** Wrapper elements (no blockquote) that hold the actual quote further down, or a forward. */
function isWrapperOnly(element: Element): boolean {
    if (element.tagName === 'BLOCKQUOTE') return false;
    // Gmail can wrap a quote AND a new answer. Collapse the explicit quote inside it.
    if (element.matches(QUOTE_WRAPPER_SELECTOR) && element.querySelector('blockquote')) return true;
    // A Gmail forward is the actual content, not old history.
    return element.matches('.gmail_quote') && FORWARD_MARKER.test((element.textContent || '').trim());
}

/**
 * Explicitly marked citations: blockquote (Apple `type="cite"`, Gmail `gmail_quote`, or after an
 * attribution), Gmail/Yahoo wrappers and own plain-text quotes. A blockquote always collapses as
 * a whole, so nested older mails (multi-level Gmail) go with it. Later answers stay visible.
 */
function findMarkedQuoteGroups(doc: Document): Node[][] {
    const groups: Node[][] = [];
    for (const element of Array.from(doc.querySelectorAll(MARKED_QUOTE_SELECTOR))) {
        if (groups.some(group => group.some(node => node.contains(element)))) continue;
        if (isWrapperOnly(element)) continue;
        const attribution = attributionBefore(element);
        if (element.tagName === 'BLOCKQUOTE' && !element.matches('[type="cite"],.email-quote')
            && !attribution && !element.closest(QUOTE_WRAPPER_SELECTOR)) continue;
        groups.push(attribution ? [...attribution, element] : [element]);
    }
    return groups;
}

/** Display-only parsing shared by the snippet and iframe. Never alters the saved email. */
export function findThreadQuoteGroups(doc: Document, options: ThreadQuoteOptions = {}): Node[][] {
    markPlainTextQuotes(doc);
    const marked = findMarkedQuoteGroups(doc);
    const tail = findHeaderCut(doc, marked, !!options.keepForwardedContent);
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

interface Toggled { element: HTMLElement; shownDisplay: string }

function insertButton(doc: Document, button: HTMLButtonElement, first: Node): void {
    const parent = first.parentNode!;
    // In Tabellen braucht der Button eine eigene Zeile, sonst ist das Markup ungültig.
    if (TABLE_PARTS.has(parent.nodeName) && first.nodeName === 'TR') {
        const row = doc.createElement('tr');
        const cell = doc.createElement('td');
        cell.colSpan = 100;
        cell.append(button);
        row.append(cell);
        parent.insertBefore(row, first);
    } else {
        parent.insertBefore(button, first);
    }
}

/** Puts a run into a hideable wrapper; table rows are hidden in place (a <div> would be invalid there). */
function makeHideable(doc: Document, run: Node[]): Toggled[] {
    const parent = run[0].parentNode!;
    if (TABLE_PARTS.has(parent.nodeName)) {
        return run.filter(node => node.nodeType === 1).map(node => {
            const element = node as HTMLElement;
            return { element, shownDisplay: element.style.getPropertyValue('display') };
        });
    }
    const wrapper = doc.createElement('div');
    parent.insertBefore(wrapper, run[0]);
    run.forEach(node => wrapper.append(node));
    return [{ element: wrapper, shownDisplay: 'block' }];
}

export function collapseThreadQuotes(doc: Document, onHeightChange: () => void, options: ThreadQuoteOptions = {}): void {
    if (doc.querySelector('[data-quote-btn]')) return;
    findThreadQuoteGroups(doc, options).forEach((group, index) => {
        const button = doc.createElement('button');
        const toggled = toSiblingRuns(group).flatMap((run, runIndex) => {
            if (runIndex === 0) insertButton(doc, button, run[0]);
            return makeHideable(doc, run);
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
