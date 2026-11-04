import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { createPortal } from 'react-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Dialog, DialogEbene } from '../components/ui/dialog';
import { obersteModaleEbene, VORSCHAU_Z_INDEX } from '../lib/modalEbene';
import { useVorschauFokus } from './useVorschauFokus';

/** Kleinste Vorschau, wie ImageViewer und DocumentPreviewModal sie bauen. Dummy-Daten (DSGVO). */
function Vorschau({ onClose, onTaste }: { onClose: () => void; onTaste?: (e: KeyboardEvent) => boolean }) {
    const { fensterRef, schliessenRef, zIndex } = useVorschauFokus({ offen: true, onClose, onTaste });
    return createPortal(
        <div ref={fensterRef} role="dialog" aria-modal="true" aria-label="Max-Mustermann-Baustelle.jpg"
            tabIndex={-1} style={{ zIndex }}>
            <button>Herunterladen</button>
            <button>Vergrößern</button>
            <button ref={schliessenRef} onClick={onClose}>Vorschau schließen</button>
        </div>,
        document.body,
    );
}

function MitOpener({ imDialog = false, onDialogSchliessen = () => {}, onTaste, mitMeldung = false }: {
    imDialog?: boolean; onDialogSchliessen?: () => void; onTaste?: (e: KeyboardEvent) => boolean; mitMeldung?: boolean;
}) {
    const [offen, setOffen] = useState(false);
    const inhalt = (
        <>
            <button onClick={() => setOffen(true)}>Max-Mustermann-Baustelle.jpg öffnen</button>
            <input aria-label="Antwort" />
            {offen && <Vorschau onClose={() => setOffen(false)} onTaste={onTaste} />}
            {mitMeldung && (
                <div data-pc-toasts role="region" aria-label="Meldungen" tabIndex={0}>
                    <button>Rückgängig</button>
                </div>
            )}
        </>
    );
    if (!imDialog) return inhalt;
    return <Dialog open onOpenChange={o => { if (!o) onDialogSchliessen(); }} aria-label="Verlauf">{inhalt}</Dialog>;
}

afterEach(() => { document.body.style.overflow = ''; });

describe('useVorschauFokus', () => {
    it('setzt den Fokus beim Öffnen auf „Schließen“ und gibt ihn danach an den Opener zurück', async () => {
        const user = userEvent.setup();
        render(<MitOpener />);
        const opener = screen.getByRole('button', { name: 'Max-Mustermann-Baustelle.jpg öffnen' });
        await user.click(opener);
        expect(screen.getByRole('dialog', { name: 'Max-Mustermann-Baustelle.jpg' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Vorschau schließen' })).toHaveFocus();

        await user.keyboard('{Escape}');
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(opener).toHaveFocus();
    });

    it('hält Tab und Umschalt+Tab im Fenster', async () => {
        const user = userEvent.setup();
        render(<MitOpener />);
        await user.click(screen.getByRole('button', { name: /öffnen/ }));
        const schliessen = screen.getByRole('button', { name: 'Vorschau schließen' });
        await user.tab();
        expect(screen.getByRole('button', { name: 'Herunterladen' })).toHaveFocus();
        await user.tab({ shift: true });
        expect(schliessen).toHaveFocus();
        await user.tab({ shift: true });
        expect(screen.getByRole('button', { name: 'Vergrößern' })).toHaveFocus();
    });

    it('holt den Fokus zurück, wenn er per Programm hinter die Vorschau springt', async () => {
        const user = userEvent.setup();
        render(<MitOpener />);
        await user.click(screen.getByRole('button', { name: /öffnen/ }));
        act(() => screen.getByRole('textbox', { name: 'Antwort' }).focus());
        expect(screen.getByRole('button', { name: 'Vorschau schließen' })).toHaveFocus();
    });

    it('Escape über einem offenen Dialog schließt nur die Vorschau', async () => {
        const user = userEvent.setup();
        const dialogSchliessen = vi.fn();
        render(<MitOpener imDialog onDialogSchliessen={dialogSchliessen} />);
        const opener = screen.getByRole('button', { name: /öffnen/ });
        await user.click(opener);
        expect(screen.getByRole('button', { name: 'Vorschau schließen' })).toHaveFocus();

        await user.tab();
        await user.tab();
        await user.tab();
        expect(screen.getByRole('dialog', { name: 'Max-Mustermann-Baustelle.jpg' })).toContainElement(document.activeElement as HTMLElement);

        await user.keyboard('{Escape}');
        expect(screen.queryByRole('dialog', { name: 'Max-Mustermann-Baustelle.jpg' })).not.toBeInTheDocument();
        expect(dialogSchliessen).not.toHaveBeenCalled();
        expect(screen.getByRole('dialog', { name: 'Verlauf' })).toBeInTheDocument();
        expect(opener).toHaveFocus();

        await user.keyboard('{Escape}');
        expect(dialogSchliessen).toHaveBeenCalledTimes(1);
    });

    it('lässt Escape nicht zu anderen Tasten-Lauschern der Seite durch', async () => {
        const user = userEvent.setup();
        const seitenLauscher = vi.fn();
        window.addEventListener('keydown', seitenLauscher);
        try {
            render(<MitOpener />);
            await user.click(screen.getByRole('button', { name: /öffnen/ }));
            seitenLauscher.mockClear();
            await user.keyboard('{Escape}');
            expect(seitenLauscher).not.toHaveBeenCalled();
        } finally {
            window.removeEventListener('keydown', seitenLauscher);
        }
    });

    it('reicht weitere Tasten an onTaste und schluckt sie nur, wenn onTaste sie behandelt', async () => {
        const user = userEvent.setup();
        const seitenLauscher = vi.fn();
        const onTaste = vi.fn((e: KeyboardEvent) => e.key === '+');
        window.addEventListener('keydown', seitenLauscher);
        try {
            render(<MitOpener onTaste={onTaste} />);
            await user.click(screen.getByRole('button', { name: /öffnen/ }));
            seitenLauscher.mockClear();
            await user.keyboard('+');
            expect(onTaste).toHaveBeenCalledWith(expect.objectContaining({ key: '+' }));
            expect(seitenLauscher).not.toHaveBeenCalled();
            await user.keyboard('x');
            expect(seitenLauscher).toHaveBeenCalledTimes(1);
        } finally {
            window.removeEventListener('keydown', seitenLauscher);
        }
    });

    it('respektiert ein bereits verhindertes Escape', async () => {
        const user = userEvent.setup();
        const vorher = (e: KeyboardEvent) => { if (e.key === 'Escape') e.preventDefault(); };
        window.addEventListener('keydown', vorher, true);
        try {
            render(<MitOpener />);
            await user.click(screen.getByRole('button', { name: /öffnen/ }));
            await user.keyboard('{Escape}');
            expect(screen.getByRole('dialog', { name: 'Max-Mustermann-Baustelle.jpg' })).toBeInTheDocument();
        } finally {
            window.removeEventListener('keydown', vorher, true);
        }
    });

    it('sperrt das Scrollen der Seite und stellt den vorherigen Wert wieder her', async () => {
        const user = userEvent.setup();
        document.body.style.overflow = 'clip';
        render(<MitOpener />);
        await user.click(screen.getByRole('button', { name: /öffnen/ }));
        expect(document.body.style.overflow).toBe('hidden');
        await user.keyboard('{Escape}');
        expect(document.body.style.overflow).toBe('clip');
    });

    it('fokussiert den Opener nicht, wenn er beim Schließen nicht mehr im DOM ist', async () => {
        const user = userEvent.setup();
        function OhneOpener() {
            const [offen, setOffen] = useState(false);
            return (
                <>
                    {!offen && <button onClick={() => setOffen(true)}>Öffnen</button>}
                    <button>Anderer Knopf</button>
                    {offen && <Vorschau onClose={() => setOffen(false)} />}
                </>
            );
        }
        render(<OhneOpener />);
        await user.click(screen.getByRole('button', { name: 'Öffnen' }));
        await user.keyboard('{Escape}');
        await act(async () => { await Promise.resolve(); });
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(document.activeElement).toBe(document.body);
    });

    it('tut nichts, solange die Vorschau nicht offen ist', async () => {
        const user = userEvent.setup();
        const onClose = vi.fn();
        function Zu() {
            useVorschauFokus({ offen: false, onClose });
            return <button>Knopf</button>;
        }
        render(<Zu />);
        await user.keyboard('{Escape}');
        expect(onClose).not.toHaveBeenCalled();
        expect(document.body.style.overflow).toBe('');
    });

    it('lässt Browser-Zoom und -Navigation mit Strg, Cmd oder Alt durch', async () => {
        const user = userEvent.setup();
        const seitenLauscher = vi.fn();
        const onTaste = vi.fn(() => true);
        window.addEventListener('keydown', seitenLauscher);
        try {
            render(<MitOpener onTaste={onTaste} />);
            await user.click(screen.getByRole('button', { name: /öffnen/ }));
            seitenLauscher.mockClear();
            await user.keyboard('{Control>}+{/Control}{Meta>}0{/Meta}{Alt>}{ArrowLeft}{/Alt}');
            expect(onTaste).not.toHaveBeenCalled();
            expect(seitenLauscher).toHaveBeenCalledTimes(6);
        } finally {
            window.removeEventListener('keydown', seitenLauscher);
        }
    });

    it('lässt Tab die Meldungen erreichen; Escape dort schließt die Vorschau nicht', async () => {
        const user = userEvent.setup();
        render(<MitOpener mitMeldung />);
        await user.click(screen.getByRole('button', { name: /öffnen/ }));
        const schliessen = screen.getByRole('button', { name: 'Vorschau schließen' });
        expect(schliessen).toHaveFocus();
        await user.tab();
        expect(screen.getByRole('region', { name: 'Meldungen' })).toHaveFocus();
        await user.tab();
        expect(screen.getByRole('button', { name: 'Rückgängig' })).toHaveFocus();
        await user.tab();
        expect(screen.getByRole('button', { name: 'Herunterladen' })).toHaveFocus();
        await user.tab({ shift: true });
        expect(screen.getByRole('button', { name: 'Rückgängig' })).toHaveFocus();

        await user.keyboard('{Escape}');
        expect(screen.getByRole('dialog', { name: 'Max-Mustermann-Baustelle.jpg' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Herunterladen' })).toHaveFocus();
    });

    it('liegt über einem Dialog und unter dem Anruf-Fenster', async () => {
        const user = userEvent.setup();
        render(<MitOpener imDialog />);
        await user.click(screen.getByRole('button', { name: /öffnen/ }));
        const vorschau = screen.getByRole('dialog', { name: 'Max-Mustermann-Baustelle.jpg' });
        const zIndex = Number(vorschau.style.zIndex);
        expect(zIndex).toBe(VORSCHAU_Z_INDEX);
        expect(zIndex).toBeLessThan(70);
        expect(zIndex).toBeGreaterThan(Number(screen.getByRole('dialog', { name: 'Verlauf' }).closest<HTMLElement>('[style]')!.style.zIndex));
        expect(obersteModaleEbene()).toBe(vorschau);
    });

    it('liegt aus einem Dialog im Anruf-Fenster heraus über diesem Dialog', async () => {
        const user = userEvent.setup();
        render(<DialogEbene ueberZIndex={70}><MitOpener imDialog /></DialogEbene>);
        await user.click(screen.getByRole('button', { name: /öffnen/ }));
        const vorschau = screen.getByRole('dialog', { name: 'Max-Mustermann-Baustelle.jpg' });
        expect(Number(vorschau.style.zIndex)).toBeGreaterThan(71);
        expect(obersteModaleEbene()).toBe(vorschau);
        await user.keyboard('{Escape}');
        expect(screen.queryByRole('dialog', { name: 'Max-Mustermann-Baustelle.jpg' })).not.toBeInTheDocument();
        expect(screen.getByRole('dialog', { name: 'Verlauf' })).toBeInTheDocument();
    });
});
