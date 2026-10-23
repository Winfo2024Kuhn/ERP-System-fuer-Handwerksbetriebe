import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { BildFeld } from './BildFeld';

beforeAll(() => {
    // jsdom kennt keine Objekt-URLs.
    URL.createObjectURL = vi.fn(() => 'blob:http://localhost/vorschau');
    URL.revokeObjectURL = vi.fn();
});

function Feld({ gespeichert = null as string | null }) {
    const [datei, setDatei] = useState<File | null>(null);
    const [entfernen, setEntfernen] = useState(false);
    return (
        <ToastProvider>
            <BildFeld gespeichertUrl={gespeichert} datei={datei} entfernen={entfernen} onDatei={setDatei} onEntfernen={setEntfernen} />
            <p data-testid="stand">{datei ? datei.name : '-'}|{entfernen ? 'entfernen' : 'behalten'}</p>
        </ToastProvider>
    );
}

const png = (name = 'foto.png') => new File(['x'], name, { type: 'image/png' });
const zone = () => screen.getByRole('button', { name: /Ordnerbild wählen/ });
const stand = () => screen.getByTestId('stand').textContent;

describe('BildFeld', () => {
    it('übernimmt ein hineingezogenes Bild mit Vorschau', () => {
        render(<Feld />);
        fireEvent.dragOver(zone(), { dataTransfer: { types: ['Files'], dropEffect: 'none' } });
        expect(screen.getByText('Loslassen zum Übernehmen')).toBeInTheDocument();
        fireEvent.drop(zone(), { dataTransfer: { files: [png()] } });
        expect(stand()).toBe('foto.png|behalten');
        expect(screen.getByAltText('Vorschau des Ordnerbilds')).toHaveAttribute('src', 'blob:http://localhost/vorschau');
        expect(screen.getByText('Neues Bild „foto.png“ – wird beim Speichern übernommen.')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Neues Bild verwerfen' }));
        expect(stand()).toBe('-|behalten');
    });

    it('nimmt ein eingefügtes Bild (⌘V/Strg+V) an und ignoriert Text', () => {
        render(<Feld />);
        fireEvent.paste(zone(), { clipboardData: { files: [] } });
        expect(stand()).toBe('-|behalten');
        fireEvent.paste(zone(), { clipboardData: { files: [png('zwischenablage.png')] } });
        expect(stand()).toBe('zwischenablage.png|behalten');
    });

    it('nimmt eine Datei aus der Dateiauswahl an', () => {
        const { container } = render(<Feld />);
        const eingabe = container.querySelector('input[type="file"]') as HTMLInputElement;
        expect(eingabe.accept).toBe('image/jpeg,image/png,image/gif,image/webp');
        fireEvent.change(eingabe, { target: { files: [png('auswahl.png')] } });
        expect(stand()).toBe('auswahl.png|behalten');
    });

    it('lehnt andere Dateien verständlich ab', () => {
        render(<Feld />);
        fireEvent.drop(zone(), { dataTransfer: { files: [new File(['x'], 'plan.pdf', { type: 'application/pdf' })] } });
        expect(stand()).toBe('-|behalten');
        expect(screen.getAllByText('„plan.pdf“ passt nicht als Ordnerbild. Erlaubt sind JPEG, PNG, GIF und WebP.').length).toBeGreaterThan(0);
    });

    it('kann ein gespeichertes Bild entfernen und doch behalten', () => {
        render(<Feld gespeichert="/bilder/gelaender.png" />);
        expect(screen.getByAltText('Vorschau des Ordnerbilds')).toHaveAttribute('src', '/bilder/gelaender.png');
        fireEvent.click(screen.getByRole('button', { name: 'Bild entfernen' }));
        expect(stand()).toBe('-|entfernen');
        expect(screen.getByText('Das Bild wird beim Speichern entfernt.')).toBeInTheDocument();
        expect(screen.queryByAltText('Vorschau des Ordnerbilds')).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Bild doch behalten' }));
        expect(stand()).toBe('-|behalten');
    });

    it('öffnet die Dateiauswahl per Tastatur', () => {
        const { container } = render(<Feld />);
        const eingabe = container.querySelector('input[type="file"]') as HTMLInputElement;
        const klick = vi.spyOn(eingabe, 'click');
        fireEvent.keyDown(zone(), { key: 'Enter' });
        fireEvent.keyDown(zone(), { key: ' ' });
        fireEvent.keyDown(zone(), { key: 'a' });
        expect(klick).toHaveBeenCalledTimes(2);
    });

    it('ignoriert Ziehen ohne Dateien', () => {
        render(<Feld />);
        fireEvent.dragOver(zone(), { dataTransfer: { types: ['text/plain'] } });
        expect(screen.queryByText('Loslassen zum Übernehmen')).not.toBeInTheDocument();
        fireEvent.dragOver(zone(), { dataTransfer: { types: ['Files'] } });
        fireEvent.dragLeave(zone());
        expect(screen.queryByText('Loslassen zum Übernehmen')).not.toBeInTheDocument();
    });
});
