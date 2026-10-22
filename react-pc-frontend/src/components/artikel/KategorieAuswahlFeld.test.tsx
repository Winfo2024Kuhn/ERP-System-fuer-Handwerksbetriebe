import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { KategorieAuswahlFeld } from './KategorieAuswahlFeld';

describe('KategorieAuswahlFeld', () => {
    it('zeigt ohne Wahl den Hinweis und öffnet das Auswahlfenster, ohne Knopf zum Entfernen', async () => {
        const user = userEvent.setup();
        const onOeffnen = vi.fn();
        render(<KategorieAuswahlFeld name="" gewaehlt={false} onOeffnen={onOeffnen} onEntfernen={vi.fn()} />);

        await user.click(screen.getByRole('button', { name: 'Kategorie wählen' }));

        expect(onOeffnen).toHaveBeenCalledTimes(1);
        expect(screen.queryByRole('button', { name: 'Kategorie entfernen' })).toBeNull();
    });

    it('zeigt die gewählte Kategorie; Auswählen und Entfernen sind getrennte Knöpfe', async () => {
        const user = userEvent.setup();
        const onOeffnen = vi.fn();
        const onEntfernen = vi.fn();
        render(<KategorieAuswahlFeld name="Werkstoffe > Rohre" gewaehlt onOeffnen={onOeffnen} onEntfernen={onEntfernen} />);

        const auswahl = screen.getByRole('button', { name: 'Werkstoffe > Rohre' });
        const entfernen = screen.getByRole('button', { name: 'Kategorie entfernen' });
        expect(auswahl).not.toContainElement(entfernen);

        await user.click(entfernen);
        expect(onEntfernen).toHaveBeenCalledTimes(1);
        expect(onOeffnen).not.toHaveBeenCalled();

        await user.click(auswahl);
        expect(onOeffnen).toHaveBeenCalledTimes(1);
    });
});
