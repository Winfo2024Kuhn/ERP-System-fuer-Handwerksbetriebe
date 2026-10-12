/**
 * Vitest-Suite fuer TeilrechnungPositionRow – Schwerpunkt: Langtext-Anzeige.
 *
 * Der Langtext einer Position kommt als HTML aus positionenJson und wird
 * dort ungeprueft gespeichert. Die Zeile darf ihn nur bereinigt anzeigen.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TeilrechnungPositionRow } from './TeilrechnungPositionRow';
import type { DocBlock } from './document-editor/types';

const position: DocBlock = {
    id: 'p1', type: 'SERVICE', pos: '1.1', quantity: 2, unit: 'Stk', price: 100,
    content: '<p>Geländer <em>verzinkt</em></p><img src="x" onerror="alert(1)"><script>alert(2)</script>',
};

const props = {
    block: position, selected: false, expanded: true,
    onToggleSelect: vi.fn(), onToggleExpand: vi.fn(),
};

describe('TeilrechnungPositionRow', () => {
    it('zeigt den aufgeklappten Langtext nur bereinigt an', () => {
        const { container } = render(<TeilrechnungPositionRow {...props} />);

        const langtext = container.querySelector('.rich-text-einheitlich')!;
        expect(langtext.querySelector('em')).toHaveTextContent('verzinkt');
        expect(langtext.querySelector('script')).toBeNull();
        expect(langtext.querySelector('[onerror]')).toBeNull();
    });

    it('nimmt ohne Titel den reinen Text des Langtexts als Bezeichnung', () => {
        render(<TeilrechnungPositionRow {...props} expanded={false} />);

        expect(screen.getByText(/Geländer verzinkt/)).toBeInTheDocument();
    });
});
