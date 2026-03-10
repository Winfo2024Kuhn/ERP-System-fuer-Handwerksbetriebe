import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogDescription,
    DialogFooter,
} from './dialog';

describe('Dialog', () => {
    it('rendert nichts wenn open=false', () => {
        const { container } = render(
            <Dialog open={false}>
                <DialogContent>Inhalt</DialogContent>
            </Dialog>
        );
        expect(container.firstChild).toBeNull();
    });

    it('rendert Inhalt wenn open=true', () => {
        render(
            <Dialog open={true}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Titel</DialogTitle>
                        <DialogDescription>Beschreibung</DialogDescription>
                    </DialogHeader>
                </DialogContent>
            </Dialog>
        );
        expect(screen.getByText('Titel')).toBeInTheDocument();
        expect(screen.getByText('Beschreibung')).toBeInTheDocument();
    });

    it('hat einen Schließen-Button', () => {
        render(
            <Dialog open={true}>
                <DialogContent>Inhalt</DialogContent>
            </Dialog>
        );
        expect(screen.getByText('Close')).toBeInTheDocument();
    });

    it('ruft onOpenChange beim Klick auf X auf', async () => {
        const handleOpenChange = vi.fn();
        const user = userEvent.setup();
        render(
            <Dialog open={true} onOpenChange={handleOpenChange}>
                <DialogContent>Inhalt</DialogContent>
            </Dialog>
        );
        // Der X-Button hat sr-only "Close"
        await user.click(screen.getByText('Close').closest('button')!);
        expect(handleOpenChange).toHaveBeenCalledWith(false);
    });

    it('rendert DialogFooter', () => {
        render(
            <Dialog open={true}>
                <DialogContent>
                    <DialogFooter>
                        <button>Speichern</button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        );
        expect(screen.getByText('Speichern')).toBeInTheDocument();
    });
});
