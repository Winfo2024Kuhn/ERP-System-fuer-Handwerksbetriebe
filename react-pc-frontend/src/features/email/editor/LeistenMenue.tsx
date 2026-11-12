import { useRef, useState, type ComponentType, type ReactNode } from 'react';
import { DropdownMenu, DropdownMenuContent, DropdownMenuTrigger } from '../../../components/ui/dropdown-menu';
import { useDialogTiefe } from '../../../components/ui/dialogTiefe';
import { DIALOG_Z_BASIS } from '../../../lib/modalEbene';
import { MenueKnopf } from './mailLeisteTeile';

type Icon = ComponentType<{ className?: string; 'aria-hidden'?: boolean }>;

interface LeistenMenueProps {
    titel: string;
    icon: Icon;
    /**
     * Inhalt des Menüs. `waehle` verpackt eine Aktion: Danach bleibt der Fokus im
     * Text (weiterschreiben), statt zum Menüknopf zurückzuspringen.
     */
    children: (waehle: (aktion: () => void) => () => void) => ReactNode;
    breite?: string;
}

/**
 * Untermenü der Formatierungsleiste (gemeinsames Dropdown: Pfeiltasten, Enter,
 * Escape). Escape bringt den Fokus zum Menüknopf zurück, eine Auswahl in den Text.
 * Liegt auch über einem Dialog, in dem das Mailfeld steht.
 */
export function LeistenMenue({ titel, icon, children, breite = 'w-56' }: LeistenMenueProps) {
    const [offen, setOffen] = useState(false);
    const gewaehlt = useRef(false);
    const tiefe = useDialogTiefe();
    const waehle = (aktion: () => void) => () => {
        gewaehlt.current = true;
        aktion();
    };
    return (
        <DropdownMenu open={offen} onOpenChange={setOffen} modal={false}>
            <DropdownMenuTrigger asChild>
                <MenueKnopf titel={titel} icon={icon} />
            </DropdownMenuTrigger>
            <DropdownMenuContent
                align="start"
                collisionPadding={12}
                className={`${breite} rounded-xl p-1.5 shadow-lg`}
                style={{ zIndex: DIALOG_Z_BASIS + tiefe + 1 }}
                onCloseAutoFocus={(event) => {
                    if (!gewaehlt.current) return;
                    gewaehlt.current = false;
                    event.preventDefault();
                }}
            >
                {children(waehle)}
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
