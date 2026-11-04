import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EmailsTabAttachmentPreview } from './EmailsTabAttachmentPreview';

describe('EmailsTabAttachmentPreview', () => {
    it('zeigt nichts ohne gewählten Anhang', () => {
        const { container } = render(<EmailsTabAttachmentPreview attachment={null} onClose={vi.fn()} />);
        expect(container).toBeEmptyDOMElement();
    });

    it('zeigt ein Bild mit der Download-Adresse des Anhangs', () => {
        render(<EmailsTabAttachmentPreview
            attachment={{ url: '/api/emails/7/attachments/3', type: 'image', name: 'foto.jpg' }} onClose={vi.fn()} />);
        expect(screen.getByText('foto.jpg')).toBeInTheDocument();
        expect(screen.getByAltText('foto.jpg')).toHaveAttribute('src', '/api/emails/7/attachments/3');
    });

    it('zeigt ein PDF im Rahmen', () => {
        render(<EmailsTabAttachmentPreview
            attachment={{ url: '/api/emails/7/attachments/4', type: 'pdf', name: 'angebot.pdf' }} onClose={vi.fn()} />);
        expect(screen.getByTitle('angebot.pdf')).toHaveAttribute('src', '/api/emails/7/attachments/4');
    });

    it('lässt gefährliche Adressen nicht in img oder iframe', () => {
        const { rerender } = render(<EmailsTabAttachmentPreview
            attachment={{ url: 'javascript:alert(1)', type: 'image', name: 'x.png' }} onClose={vi.fn()} />);
        expect(screen.getByAltText('x.png').getAttribute('src') ?? '').toBe('');

        rerender(<EmailsTabAttachmentPreview
            attachment={{ url: 'data:text/html,<script>alert(1)</script>', type: 'pdf', name: 'x.pdf' }} onClose={vi.fn()} />);
        expect(screen.getByTitle('x.pdf').getAttribute('src') ?? '').toBe('');

        rerender(<EmailsTabAttachmentPreview
            attachment={{ url: '//fremd.example.com/x.pdf', type: 'pdf', name: 'y.pdf' }} onClose={vi.fn()} />);
        expect(screen.getByTitle('y.pdf').getAttribute('src') ?? '').toBe('');
    });

    it('schließt per X und per Klick auf den Hintergrund, nicht per Klick in den Inhalt', () => {
        const onClose = vi.fn();
        const { container } = render(<EmailsTabAttachmentPreview
            attachment={{ url: '/api/emails/7/attachments/3', type: 'image', name: 'foto.jpg' }} onClose={onClose} />);
        fireEvent.click(screen.getByText('foto.jpg'));
        expect(onClose).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole('button'));
        expect(onClose).toHaveBeenCalledTimes(1);
        fireEvent.click(container.firstChild as Element);
        expect(onClose).toHaveBeenCalledTimes(2);
    });
});
