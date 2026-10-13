import { createElement } from 'react';
import type { FormBlock } from '../../types';
import { bausteinIcon } from './bausteinVorlagen';

/** Symbol eines Bausteins (Anschrift, Text, Positionen …), passend zum Namen in Ebenen und Eigenschaften. */
export function BausteinSymbol({ block, className }: { block: Pick<FormBlock, 'type' | 'content'>; className?: string }) {
    return createElement(bausteinIcon(block), { 'aria-hidden': true, className });
}
