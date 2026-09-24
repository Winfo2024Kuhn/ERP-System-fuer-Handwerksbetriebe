import { escapeHtml, isLikelyPlainText } from '../../components/emailContentFrameUtils';

/** A citation needs a reply attribution, not merely a short preceding paragraph. */
function isAttribution(text: string): boolean {
    const line = text.replace(/\s+/g, ' ').trim();
    return line.length < 500 && /\d|@/.test(line) && (
        /^Am .+ schrieb(?: .+)?:$/i.test(line)
        || /^On .+ wrote:$/i.test(line)
    );
}

function isOutlookHeader(element: Element): boolean {
    const text = element.textContent || '';
    return text.length < 1500 && /(?:Von|From):/i.test(text)
        && /(?:Gesendet|Sent):/i.test(text) && /(?:An|To):/i.test(text)
        && /(?:Betreff|Subject):/i.test(text);
}

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

/** Display-only parsing shared by the snippet and iframe. Never alters the saved email. */
export function findThreadQuoteGroups(doc: Document): Node[][] {
    markPlainTextQuotes(doc);
    const groups: Node[][] = [];
    const candidates = Array.from(doc.querySelectorAll(
        'blockquote,.email-quote,.gmail_quote,.yahoo_quoted,#divRplyFwdMsg,div[style*="border-top"]',
    ));
    for (const element of candidates) {
        if (groups.some(group => group.some(node => node.contains(element)))) continue;
        // Gmail can wrap a quote AND a new answer. Collapse the explicit quote inside it.
        if (element.matches('.gmail_quote,.yahoo_quoted') && element.querySelector('blockquote')) continue;
        const outlook = element.id === 'divRplyFwdMsg' || element.matches('div[style*="border-top"]');
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
        if (outlook) {
            if (!isOutlookHeader(element)) continue;
            // Outlook's reply header precedes one body container. Later sibling answers
            // must remain visible; an unstructured tail cannot safely be classified.
            let next = element.nextSibling;
            while (next && isSpacer(next)) { group.push(next); next = next.nextSibling; }
            if (!next || next.nodeType !== 1 || !['DIV', 'BLOCKQUOTE', 'TABLE'].includes(next.nodeName)) continue;
            group.push(next);
        }
        if (attributed) group.unshift(previous!, ...spacers);
        groups.push(group);
    }
    return groups;
}

export function collapseThreadQuotes(doc: Document, onHeightChange: () => void): void {
    if (doc.querySelector('[data-quote-btn]')) return;
    findThreadQuoteGroups(doc).forEach((group, index) => {
        const first = group[0];
        const wrapper = doc.createElement('div');
        wrapper.id = `email-quoted-history-${index}`;
        wrapper.hidden = true;
        wrapper.style.setProperty('display', 'none', 'important');
        const button = doc.createElement('button');
        button.type = 'button';
        button.dataset.quoteBtn = '1';
        button.textContent = 'Zitierten Verlauf anzeigen';
        button.setAttribute('aria-expanded', 'false');
        button.setAttribute('aria-controls', wrapper.id);
        first.parentNode?.insertBefore(button, first);
        first.parentNode?.insertBefore(wrapper, first);
        group.forEach(node => wrapper.append(node));
        button.addEventListener('click', () => {
            wrapper.hidden = !wrapper.hidden;
            wrapper.style.setProperty('display', wrapper.hidden ? 'none' : 'block', 'important');
            button.setAttribute('aria-expanded', String(!wrapper.hidden));
            button.textContent = wrapper.hidden ? 'Zitierten Verlauf anzeigen' : 'Zitierten Verlauf ausblenden';
            onHeightChange();
        });
    });
}

export function getThreadPreview(content: string): string {
    const doc = new DOMParser().parseFromString(isLikelyPlainText(content) ? escapeHtml(content) : content, 'text/html');
    findThreadQuoteGroups(doc).forEach(group => group.forEach(node => node.parentNode?.removeChild(node)));
    doc.querySelectorAll('script,style,head').forEach(element => element.remove());
    doc.querySelectorAll('br,p,div,li,tr').forEach(element => element.append(doc.createTextNode(' ')));
    return (doc.body.textContent || '').replace(/\s+/g, ' ').trim();
}
