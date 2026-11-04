/** DOM building blocks shared by the thread quote detection (display only). */

export const BLOCK_TAGS = new Set(['ADDRESS', 'ARTICLE', 'BLOCKQUOTE', 'CENTER', 'DIV', 'DL', 'DT', 'DD', 'FOOTER',
    'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'HEADER', 'LI', 'OL', 'P', 'PRE', 'SECTION', 'TABLE', 'TR', 'UL']);

/**
 * Wrappers mail clients put around a citation. Yahoo prefixes its class
 * (`ydp8a8a0641yahoo_quoted`), so the class is matched as a substring.
 */
export const QUOTE_WRAPPER_SELECTOR = '.gmail_quote,[class*="yahoo_quoted"]';

/** Everything that explicitly marks a citation, including our own plain-text quote blocks. */
export const MARKED_QUOTE_SELECTOR = `blockquote,.email-quote,${QUOTE_WRAPPER_SELECTOR}`;

/** Empty text, line breaks and empty paragraphs between an attribution and its quote. */
export function isSpacer(node: Node): boolean {
    return !node.textContent?.trim() && (node.nodeType === 3
        || (node.nodeType === 1 && !((node as Element).querySelector('img,table,iframe'))
            && ['BR', 'DIV', 'P', 'HR'].includes(node.nodeName)));
}
