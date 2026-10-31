import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { SearchableSelect } from './searchable-select';
import type { SearchableSelectProps } from './searchable-select';

const OPTIONEN = [
    { value: 'a-1', label: 'Alpha', sublabel: 'erstes' },
    { value: 'b-2', label: 'Beta', sublabel: 'zweites' },
    { value: 'c-3', label: 'Gamma' },
];

function Probe(props: Partial<SearchableSelectProps>) {
    const [wert, setWert] = useState('');
    return <SearchableSelect ariaLabel="Auswahl" value={wert} onChange={setWert} options={OPTIONEN} {...props} />;
}

describe('SearchableSelect', () => {
    it('sucht lokal in Bezeichnung, Wert und Zusatz und wählt per Klick', async () => {
        const user = userEvent.setup();
        render(<Probe />);
        const knopf = screen.getByRole('button', { name: 'Auswahl' });
        expect(knopf).toHaveAttribute('aria-expanded', 'false');
        await user.click(knopf);
        expect(knopf).toHaveAttribute('aria-expanded', 'true');
        await user.type(screen.getByRole('combobox'), 'zweit');
        expect(screen.getAllByRole('option')).toHaveLength(1);
        await user.clear(screen.getByRole('combobox'));
        await user.type(screen.getByRole('combobox'), 'c-3');
        await user.click(screen.getByRole('option', { name: /Gamma/ }));
        expect(knopf).toHaveTextContent('Gamma');
        expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    });

    it('ist per Tastatur bedienbar: Pfeile markieren, Enter wählt, Escape schließt', async () => {
        const user = userEvent.setup();
        render(<Probe />);
        const knopf = screen.getByRole('button', { name: 'Auswahl' });
        await user.click(knopf);
        const suche = screen.getByRole('combobox');
        await waitFor(() => expect(suche).toHaveFocus());
        await user.keyboard('{ArrowDown}{ArrowDown}{ArrowUp}');
        expect(suche).toHaveAttribute('aria-activedescendant', screen.getByRole('option', { name: /Alpha/ }).id);
        await user.keyboard('{ArrowDown}{Enter}');
        expect(knopf).toHaveTextContent('Beta');
        expect(knopf).toHaveFocus();

        await user.click(knopf);
        await user.keyboard('{Escape}');
        expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
        expect(knopf).toHaveFocus();
    });

    it('meldet Öffnen und Schließen, zeigt Laden, Filter, Hinweise und eigenen Leer-Text', async () => {
        const user = userEvent.setup();
        const onOpenChange = vi.fn();
        render(
            <div>
                <Probe
                    options={[]}
                    loading
                    onOpenChange={onOpenChange}
                    filter={<button type="button">Mein Filter</button>}
                    meldungen={<span>Ein Hinweis</span>}
                />
                <p>Daneben</p>
            </div>,
        );
        await user.click(screen.getByRole('button', { name: 'Auswahl' }));
        expect(onOpenChange).toHaveBeenLastCalledWith(true);
        expect(screen.getByText('Wird geladen …')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Mein Filter' })).toBeInTheDocument();
        expect(screen.getByText('Ein Hinweis')).toBeInTheDocument();
        // Klick daneben schließt.
        await user.click(screen.getByText('Daneben'));
        expect(onOpenChange).toHaveBeenLastCalledWith(false);
        expect(screen.queryByText('Ein Hinweis')).not.toBeInTheDocument();
    });

    it('zeigt ohne Treffer den eigenen Text', async () => {
        const user = userEvent.setup();
        render(<Probe keineTrefferText="Nichts da." />);
        await user.click(screen.getByRole('button', { name: 'Auswahl' }));
        await user.type(screen.getByRole('combobox'), 'zzz');
        expect(screen.getByText('Nichts da.')).toBeInTheDocument();
    });

    it('übernimmt auf Wunsch den getippten Begriff – nach Prüfung', async () => {
        const user = userEvent.setup();
        render(
            <Probe
                anzeigeText="Eigener Name"
                freieEingabe={{
                    hinweis: 'Nicht dabei? Einfach tippen.',
                    knopfText: (b) => `„${b}“ übernehmen`,
                    pruefen: (b) => (b.includes(' ') ? 'Keine Leerzeichen.' : null),
                }}
            />,
        );
        const knopf = screen.getByRole('button', { name: 'Auswahl' });
        await user.click(knopf);
        expect(screen.getByText('Nicht dabei? Einfach tippen.')).toBeInTheDocument();
        await user.type(screen.getByRole('combobox'), 'a b');
        await user.click(screen.getByRole('button', { name: '„a b“ übernehmen' }));
        expect(screen.getByRole('alert')).toHaveTextContent('Keine Leerzeichen.');
        await user.type(screen.getByRole('combobox'), 'x');
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
        await user.clear(screen.getByRole('combobox'));
        await user.type(screen.getByRole('combobox'), 'frei-1{Enter}');
        expect(knopf).toHaveTextContent('Eigener Name');
    });

    it('lässt sich leeren und sperren', async () => {
        const user = userEvent.setup();
        const { rerender } = render(<SearchableSelect ariaLabel="Auswahl" value="a-1" onChange={() => {}} options={OPTIONEN} disabled />);
        expect(screen.getByRole('button', { name: 'Auswahl' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Leeren' })).not.toBeInTheDocument();

        const onChange = vi.fn();
        rerender(<SearchableSelect ariaLabel="Auswahl" value="a-1" onChange={onChange} options={OPTIONEN} leerenText="Leeren" />);
        await user.click(screen.getByRole('button', { name: 'Leeren' }));
        expect(onChange).toHaveBeenCalledWith('');
        expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    });

    it('sucht mit onSearch auf dem Server', async () => {
        const user = userEvent.setup();
        const onSearch = vi.fn(async (begriff: string) => (begriff ? [{ value: 's', label: `Treffer ${begriff}` }] : []));
        render(<Probe options={[]} onSearch={onSearch} />);
        await user.click(screen.getByRole('button', { name: 'Auswahl' }));
        await user.type(screen.getByRole('combobox'), 'x');
        expect(await screen.findByRole('option', { name: 'Treffer x' }, { timeout: 2000 })).toBeInTheDocument();
    });
});
