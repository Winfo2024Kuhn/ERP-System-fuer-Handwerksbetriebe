import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';
import { describe, expect, it } from 'vitest';
import { AnsichtInfo, FaelligAmInfo, HochladenInfo, OrdnerVerschiebenInfo, RechteAuswahlInfo, RechteInfo, SeitenInfo } from './InfoTexte';

/** Neutrale Anrede (Imperativ/Infinitiv): weder „du“ noch die Höflichkeitsform „Sie“. */
const ANREDE = /\b(du|dich|dir|dein\w*|Sie|Ihnen|Ihr\w*)\b/;

describe('InfoTexte', () => {
    it.each<[string, ReactElement]>([
        ['Firmendokumente', <SeitenInfo />],
        ['Fällig am', <FaelligAmInfo />],
        ['Wer darf das sehen?', <RechteInfo />],
        ['Alle oder nur bestimmte Abteilungen', <RechteAuswahlInfo />],
        ['Ordner verschieben', <OrdnerVerschiebenInfo />],
        ['Liste oder Vorschau', <AnsichtInfo />],
        ['Mehrere Dateien hochladen', <HochladenInfo />],
    ])('„%s“ spricht neutral an', async (titel, info) => {
        const user = userEvent.setup();
        render(info);
        await user.click(screen.getByRole('button', { name: `Mehr zu: ${titel}` }));
        const text = screen.getByRole('dialog', { name: titel }).textContent ?? '';
        expect(text).not.toMatch(ANREDE);
        // Keine Gedankenstriche als Satzzeichen (Punkt oder Komma statt „ – “).
        expect(text).not.toContain(' – ');
    });

    it('erklärt das Hochladen mehrerer Dateien ohne Bedingungssatz in Sie-Form', async () => {
        const user = userEvent.setup();
        render(<HochladenInfo />);
        await user.click(screen.getByRole('button', { name: 'Mehr zu: Mehrere Dateien hochladen' }));
        expect(screen.getByText(/^Werden mehrere Dateien auf einmal gewählt oder gezogen, legt das Programm alle ohne Nachfrage in diesem Ordner ab\./)).toBeInTheDocument();
    });

    it('warnt in der Seiten-Info, dass ein eingeschränkter Hauptordner seine Einstellung verliert', async () => {
        const user = userEvent.setup();
        render(<SeitenInfo />);
        await user.click(screen.getByRole('button', { name: 'Mehr zu: Firmendokumente' }));
        expect(screen.getByRole('dialog', { name: 'Firmendokumente' })).toHaveTextContent(
            'Kommt ein Hauptordner mit eingeschränkter Sichtbarkeit in einen anderen Ordner, geht seine eigene Einstellung „Wer darf das sehen?“ verloren.',
        );
    });
});
