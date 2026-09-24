import type { ComponentProps } from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { ZahlungszielTageEingabe } from './ZahlungszielTageEingabe';

/**
 * Die Eingabe uebernimmt bewusst erst beim Abschluss (Enter oder Feld
 * verlassen): der Editor fragt bei langen Zahlungszielen nach, und eine
 * Rueckfrage pro Tastendruck waere unbenutzbar.
 *
 * Wichtig fuer diese Tests: das Feld wird ueberall zuerst FOKUSSIERT, und nach
 * Escape wird ausdruecklich ein Blur ausgeloest. Genau daran ist ein Fehler
 * vorbeigelaufen — die fruehere Fassung rief im Escape-Zweig selbst `blur()`
 * auf und uebernahm dabei den Wert, statt ihn zu verwerfen (Befund des
 * Code-Reviews, 3. Runde). Ohne Fokus lief dieser Test ins Leere.
 */
function renderEingabe(overrides: Partial<ComponentProps<typeof ZahlungszielTageEingabe>> = {}) {
    const onUebernehmen = overrides.onUebernehmen ?? vi.fn(async () => true);
    const utils = render(
        <ZahlungszielTageEingabe tage={8} onUebernehmen={onUebernehmen} {...overrides} />
    );
    const feld = () => screen.getByTitle('Zahlungsziel in Tagen') as HTMLInputElement;
    feld().focus();
    return { ...utils, onUebernehmen, feld };
}

describe('ZahlungszielTageEingabe', () => {
    it('übernimmt nichts, solange nur getippt wird', () => {
        const { onUebernehmen, feld } = renderEingabe();

        fireEvent.change(feld(), { target: { value: '30' } });

        expect(feld()).toHaveValue('30');
        expect(onUebernehmen).not.toHaveBeenCalled();
    });

    it('übernimmt mit Enter', () => {
        const { onUebernehmen, feld } = renderEingabe();

        fireEvent.change(feld(), { target: { value: '30' } });
        fireEvent.keyDown(feld(), { key: 'Enter' });

        expect(onUebernehmen).toHaveBeenCalledWith(30);
    });

    it('übernimmt beim Verlassen des Feldes', () => {
        const { onUebernehmen, feld } = renderEingabe();

        fireEvent.change(feld(), { target: { value: '30' } });
        fireEvent.blur(feld());

        expect(onUebernehmen).toHaveBeenCalledWith(30);
    });

    it('verwirft den Entwurf mit Escape – auch wenn das Feld danach verlassen wird', () => {
        // Regression: Escape setzte den Entwurf zurueck und loeste `blur()` im
        // selben Ereignis aus. Der Blur-Handler las noch den ALTEN Entwurf und
        // uebernahm genau den Wert, den der Nutzer verwerfen wollte.
        const { onUebernehmen, feld } = renderEingabe();

        fireEvent.change(feld(), { target: { value: '30' } });
        fireEvent.keyDown(feld(), { key: 'Escape' });
        fireEvent.blur(feld());

        expect(feld()).toHaveValue('8');
        expect(onUebernehmen).not.toHaveBeenCalled();
    });

    it('meldet einen unveränderten Wert nicht als Eingabe', () => {
        const { onUebernehmen, feld } = renderEingabe();

        fireEvent.change(feld(), { target: { value: '8' } });
        fireEvent.keyDown(feld(), { key: 'Enter' });

        expect(onUebernehmen).not.toHaveBeenCalled();
    });

    it('meldet nichts, wenn das Feld nur betreten und wieder verlassen wird', () => {
        // Sonst schriebe ein blosser Klick ins Feld den Wert fest, den es
        // gerade zeigt — z.B. den Standard, waehrend das Dokument noch laedt.
        const { onUebernehmen, feld } = renderEingabe();

        fireEvent.blur(feld());

        expect(onUebernehmen).not.toHaveBeenCalled();
    });

    it('übernimmt einen von außen geänderten Wert, solange nicht getippt wurde', () => {
        // Anschliessendes Verlassen darf den vorher angezeigten Wert nicht
        // zurueckschreiben: sonst ueberschreibt ein blosser Klick ins Feld das
        // gerade geladene Zahlungsziel mit dem Standard.
        const onUebernehmen = vi.fn(async () => true);
        const { rerender, feld } = renderEingabe({ onUebernehmen });

        rerender(<ZahlungszielTageEingabe tage={21} onUebernehmen={onUebernehmen} />);
        expect(feld()).toHaveValue('21');

        fireEvent.blur(feld());
        expect(onUebernehmen).not.toHaveBeenCalled();
    });

    it('meldet denselben Wert nach der Übernahme kein zweites Mal', async () => {
        // Zwischen Uebernahme und neuem Prop-Wert liegt mindestens ein Render.
        // Ein Verlassen des Feldes in diesem Fenster darf keine zweite
        // Rueckfrage ausloesen. Der Blur kommt bewusst erst, NACHDEM die
        // Uebernahme durch ist — sonst pruefte der Test nur den Riegel gegen
        // gleichzeitige Uebernahmen.
        const { onUebernehmen, feld } = renderEingabe();

        fireEvent.change(feld(), { target: { value: '30' } });
        fireEvent.keyDown(feld(), { key: 'Enter' });
        await waitFor(() => expect(feld()).toHaveValue('30'));

        fireEvent.blur(feld());

        expect(onUebernehmen).toHaveBeenCalledTimes(1);
    });

    it('lässt einen getippten Entwurf von einer späten Änderung nicht überschreiben', () => {
        // Der Nutzer tippt, waehrend das Dokument noch laedt: seine Eingabe gilt.
        const onUebernehmen = vi.fn(async () => true);
        const { rerender, feld } = renderEingabe({ onUebernehmen });

        fireEvent.change(feld(), { target: { value: '30' } });
        rerender(<ZahlungszielTageEingabe tage={14} onUebernehmen={onUebernehmen} />);

        expect(feld()).toHaveValue('30');
    });

    it('setzt das Feld zurück, wenn die Übernahme abgelehnt wird', async () => {
        // Abgelehnt heisst: Wert ausserhalb der Grenzen oder Rueckfrage abgebrochen.
        const { feld } = renderEingabe({ onUebernehmen: vi.fn(async () => false) });

        fireEvent.change(feld(), { target: { value: '400' } });
        fireEvent.keyDown(feld(), { key: 'Enter' });

        await waitFor(() => expect(feld()).toHaveValue('8'));
    });

    it('reicht eine Eingabe, die keine reine Zahl ist, zur Prüfung weiter', () => {
        // `parseInt` wuerde aus "30abc" stillschweigend 30 machen.
        const { onUebernehmen, feld } = renderEingabe();

        fireEvent.change(feld(), { target: { value: '30abc' } });
        fireEvent.keyDown(feld(), { key: 'Enter' });

        expect(onUebernehmen).toHaveBeenCalledWith(NaN);
    });

    it('fragt nicht doppelt, wenn das Feld während der laufenden Übernahme verlassen wird', async () => {
        // Die Rueckfrage nimmt den Fokus -- ohne Riegel stuende sie zweimal da.
        let freigeben: (uebernommen: boolean) => void = () => { };
        const onUebernehmen = vi.fn(() => new Promise<boolean>(resolve => { freigeben = resolve; }));
        const { feld } = renderEingabe({ onUebernehmen });

        fireEvent.change(feld(), { target: { value: '30' } });
        fireEvent.keyDown(feld(), { key: 'Enter' });
        fireEvent.blur(feld());

        expect(onUebernehmen).toHaveBeenCalledTimes(1);
        freigeben(true);
        await waitFor(() => expect(feld()).toHaveValue('30'));
    });

    it('zeigt die übliche Schreibweise nach der Übernahme', async () => {
        // Sonst meldet das anschliessende Verlassen des Feldes "030" erneut an.
        const { feld } = renderEingabe();

        fireEvent.change(feld(), { target: { value: '030' } });
        fireEvent.keyDown(feld(), { key: 'Enter' });

        await waitFor(() => expect(feld()).toHaveValue('30'));
    });
});
