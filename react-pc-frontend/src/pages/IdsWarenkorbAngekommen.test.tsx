/**
 * Rücksprung-Seite aus dem Lieferanten-Shop.
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import IdsWarenkorbAngekommen from './IdsWarenkorbAngekommen';

function zeige(suche: string) {
    return render(
        <MemoryRouter initialEntries={[`/ids/warenkorb-angekommen${suche}`]}>
            <IdsWarenkorbAngekommen />
        </MemoryRouter>,
    );
}

afterEach(() => vi.restoreAllMocks());

describe('IdsWarenkorbAngekommen', () => {
    it('meldet Erfolg, sagt dem Dokument-Tab Bescheid und schließt den Tab auf Knopfdruck', async () => {
        const empfaenger = new BroadcastChannel('ids-warenkorb');
        const empfangen = new Promise((fertig) => { empfaenger.onmessage = (e) => fertig(e.data); });
        const schliessen = vi.spyOn(window, 'close').mockImplementation(() => undefined);
        zeige('?vorgang=42&ergebnis=ok');
        expect(screen.getByRole('heading', { name: 'Warenkorb übernommen' })).toBeInTheDocument();
        expect(screen.getByText(/du kannst diesen Tab jetzt schließen und im Dokument weitermachen/i)).toBeInTheDocument();
        await expect(empfangen).resolves.toEqual({ vorgangId: 42 });
        empfaenger.close();
        fireEvent.click(screen.getByRole('button', { name: /Tab schließen/ }));
        expect(schliessen).toHaveBeenCalled();
    });

    it.each([
        ['abgelaufen', /länger als 60 Minuten offen/],
        ['ungueltig', /keinem Shop-Besuch zugeordnet/],
        ['leer', /Der Warenkorb war leer/],
        ['fehler', /konnte nicht gelesen werden/],
        ['unsinn', /konnte nicht gelesen werden/],
    ])('erklärt das Ergebnis „%s“', (ergebnis, text) => {
        const senden = vi.spyOn(BroadcastChannel.prototype, 'postMessage');
        zeige(`?vorgang=42&ergebnis=${ergebnis}`);
        expect(screen.getByRole('status')).toHaveTextContent(text);
        expect(senden).not.toHaveBeenCalled();
    });

    it('meldet ohne gültige Vorgangsnummer nichts weiter', () => {
        const senden = vi.spyOn(BroadcastChannel.prototype, 'postMessage');
        zeige('?vorgang=abc&ergebnis=ok');
        expect(screen.getByRole('heading', { name: 'Warenkorb übernommen' })).toBeInTheDocument();
        expect(senden).not.toHaveBeenCalled();
    });
});
