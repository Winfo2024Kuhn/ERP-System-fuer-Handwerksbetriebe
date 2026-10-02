import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { ToastProvider } from '../ui/toast';
import { BildUploadFeld } from './BildUploadFeld';
import { pruefeBilddateien } from './bildUpload';

function zeige(onDateien = vi.fn(), laedt = false) {
    render(<ToastProvider><BildUploadFeld onDateien={onDateien} laedt={laedt} /></ToastProvider>);
    return onDateien;
}

const bild = (name: string, type = 'image/jpeg', size = 100) => {
    const datei = new File(['x'], name, { type });
    Object.defineProperty(datei, 'size', { value: size });
    return datei;
};

describe('pruefeBilddateien', () => {
    it('lässt JPG, PNG und WebP durch', () => {
        const r = pruefeBilddateien([bild('a.jpg'), bild('b.png', 'image/png'), bild('c.webp', 'image/webp')]);
        expect(r.gueltig).toHaveLength(3);
        expect(r.abgelehnt).toHaveLength(0);
    });

    it('lehnt Nicht-Bilder mit Begründung ab', () => {
        const r = pruefeBilddateien([bild('virus.exe', 'application/x-msdownload'), bild('plan.pdf', 'application/pdf')]);
        expect(r.gueltig).toHaveLength(0);
        expect(r.abgelehnt[0]).toContain('virus.exe');
    });

    it('lehnt zu große Bilder ab', () => {
        const r = pruefeBilddateien([bild('riesig.jpg', 'image/jpeg', 16 * 1024 * 1024)]);
        expect(r.gueltig).toHaveLength(0);
        expect(r.abgelehnt[0]).toContain('15 MB');
    });
});

describe('BildUploadFeld', () => {
    it('meldet gewählte Bilder nach oben', async () => {
        const user = userEvent.setup();
        const onDateien = zeige();

        await user.upload(screen.getByLabelText('Bilddateien auswählen'), bild('tor.jpg'));

        expect(onDateien).toHaveBeenCalledWith([expect.objectContaining({ name: 'tor.jpg' })]);
    });

    it('nimmt per Drag & Drop abgelegte Bilder an', () => {
        const onDateien = zeige();
        const datei = bild('geraender.png', 'image/png');

        fireEvent.drop(screen.getByTestId('bild-upload-feld'), { dataTransfer: { files: [datei] } });

        expect(onDateien).toHaveBeenCalledWith([datei]);
    });

    it('ignoriert abgelegte Dateien, die keine Bilder sind', () => {
        const onDateien = zeige();

        fireEvent.drop(screen.getByTestId('bild-upload-feld'), {
            dataTransfer: { files: [bild('plan.pdf', 'application/pdf')] },
        });

        expect(onDateien).not.toHaveBeenCalled();
    });

    it('sperrt Auswahl und Ablegen während eines Uploads', () => {
        const onDateien = zeige(vi.fn(), true);

        expect(screen.getByRole('button', { name: /Bilder auswählen/ })).toBeDisabled();
        fireEvent.drop(screen.getByTestId('bild-upload-feld'), { dataTransfer: { files: [bild('a.jpg')] } });
        expect(onDateien).not.toHaveBeenCalled();
    });
});
