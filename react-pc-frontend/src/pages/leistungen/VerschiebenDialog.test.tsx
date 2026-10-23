import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { VerschiebenDialog } from './VerschiebenDialog';
import type { ZielEintrag } from './typen';

const ZIELE: ZielEintrag[] = [
    { id: null, name: 'Oberste Ebene (kein übergeordneter Ordner)', ebene: 0, gesperrt: null },
    { id: 1, name: 'Metallbau', ebene: 0, gesperrt: 'Das ist der Ordner selbst.' },
    { id: 5, name: 'Fliesen', ebene: 0, gesperrt: null },
];

function zeige(onBestaetigen = vi.fn().mockResolvedValue(undefined), onAbbrechen = vi.fn()) {
    render(
        <ToastProvider>
            <VerschiebenDialog titel="„Tore“ verschieben" beschreibung="Wohin soll der Ordner?" info={<span>Info</span>}
                ziele={ZIELE} onAbbrechen={onAbbrechen} onBestaetigen={onBestaetigen} />
        </ToastProvider>,
    );
    return { onBestaetigen, onAbbrechen };
}

describe('VerschiebenDialog', () => {
    it('verschiebt erst nach einer Wahl und sperrt ungültige Ziele', async () => {
        const { onBestaetigen } = zeige();
        expect(screen.getByRole('dialog', { name: '„Tore“ verschieben' })).toBeInTheDocument();
        expect(screen.getByText('Info')).toBeInTheDocument();
        const knopf = screen.getByRole('button', { name: 'Hierhin verschieben' });
        expect(knopf).toBeDisabled();
        expect(screen.getByText('Bitte zuerst einen Ziel-Ordner wählen.')).toBeInTheDocument();
        expect(screen.getByRole('radio', { name: /Metallbau/ })).toBeDisabled();

        await userEvent.click(screen.getByRole('radio', { name: /Fliesen/ }));
        await userEvent.click(knopf);
        expect(onBestaetigen).toHaveBeenCalledWith(5);
    });

    it('oberste Ebene schickt null', async () => {
        const { onBestaetigen } = zeige();
        await userEvent.click(screen.getByRole('radio', { name: /Oberste Ebene/ }));
        await userEvent.click(screen.getByRole('button', { name: 'Hierhin verschieben' }));
        expect(onBestaetigen).toHaveBeenCalledWith(null);
    });

    it('zeigt Fehler im Dialog und lässt erneut versuchen', async () => {
        const { onBestaetigen } = zeige(vi.fn()
            .mockRejectedValueOnce(new Error('In „Fliesen“ liegen Leistungen. Dort kann kein Unterordner hinein.'))
            .mockRejectedValueOnce('kaputt'));
        await userEvent.click(screen.getByRole('radio', { name: /Fliesen/ }));
        await userEvent.click(screen.getByRole('button', { name: 'Hierhin verschieben' }));
        expect(await screen.findByText(/Dort kann kein Unterordner hinein/, { selector: 'p[role="alert"]' })).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Hierhin verschieben' }));
        expect(await screen.findByText('Das Verschieben hat nicht geklappt.', { selector: 'p[role="alert"]' })).toBeInTheDocument();
        expect(onBestaetigen).toHaveBeenCalledTimes(2);
    });

    it('„Abbrechen“ verschiebt nichts', async () => {
        const { onBestaetigen, onAbbrechen } = zeige();
        await userEvent.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(onAbbrechen).toHaveBeenCalled();
        expect(onBestaetigen).not.toHaveBeenCalled();
    });
});
