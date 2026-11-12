import { useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react';
import { createPortal } from 'react-dom';
import { ExternalLink, Link2, Pencil, Unlink } from 'lucide-react';
import { Button } from '../../../components/ui/button';
import { useDialogTiefe } from '../../../components/ui/dialogTiefe';
import { useToast } from '../../../components/ui/toast';
import { DIALOG_Z_BASIS } from '../../../lib/modalEbene';
import { istErlaubterLink, kurzeAdresse } from './mailLink';
import { auswahlBehalten } from './mailLeisteTasten';

const ABSTAND = 6;
const RAND = 8;

interface MailLinkLeisteProps {
    id: string;
    link: HTMLAnchorElement;
    onAendern: () => void;
    onEntfernen: () => void;
    /** Zurück in den Text (Escape, Shift+Tab). */
    onZurueck: () => void;
    /** Weiter zum nächsten Bedienelement hinter dem Feld (Tab). */
    onWeiter: () => void;
    onSchliessen: () => void;
}

interface Lage { top: number; left: number; sichtbar: boolean }

function berechneLage(link: HTMLAnchorElement, leiste: HTMLElement | null): Lage {
    const rect = link.getBoundingClientRect();
    const feld = link.closest('[contenteditable="true"]')?.getBoundingClientRect();
    const breite = leiste?.offsetWidth ?? 0;
    const hoehe = leiste?.offsetHeight ?? 0;
    // Ist der Link aus dem sichtbaren Teil des Felds gescrollt, verschwindet auch die Leiste.
    const sichtbar = !feld || feld.height === 0 || (rect.bottom > feld.top && rect.top < feld.bottom);
    const unten = rect.bottom + ABSTAND;
    const top = unten + hoehe > window.innerHeight - RAND ? Math.max(RAND, rect.top - ABSTAND - hoehe) : unten;
    const left = Math.max(RAND, Math.min(rect.left, window.innerWidth - breite - RAND));
    return { top, left, sichtbar };
}

/**
 * Kleine schwebende Leiste direkt am Link im Mail-Textfeld: gekürzte Adresse,
 * „Ändern“, „Öffnen“ und „Entfernen“. Hängt fest positioniert am `body`, damit
 * kein Rahmen mit `overflow` sie abschneidet, und liegt über dem Dialog, in dem
 * das Feld steht.
 */
export function MailLinkLeiste({ id, link, onAendern, onEntfernen, onZurueck, onWeiter, onSchliessen }: MailLinkLeisteProps) {
    const toast = useToast();
    const tiefe = useDialogTiefe();
    const leisteRef = useRef<HTMLDivElement>(null);
    const [lage, setLage] = useState<Lage>({ top: 0, left: 0, sichtbar: false });
    const href = link.getAttribute('href') ?? '';

    useLayoutEffect(() => {
        const aktualisieren = () => setLage(berechneLage(link, leisteRef.current));
        aktualisieren();
        window.addEventListener('resize', aktualisieren);
        window.addEventListener('scroll', aktualisieren, true);
        return () => {
            window.removeEventListener('resize', aktualisieren);
            window.removeEventListener('scroll', aktualisieren, true);
        };
    }, [link]);

    const oeffnen = () => {
        // Adresse erst beim Klick lesen und prüfen: Sie kann aus eingefügtem fremdem HTML stammen.
        const ziel = link.getAttribute('href');
        if (!ziel || !istErlaubterLink(ziel)) {
            toast.error('Dieser Link lässt sich nicht öffnen.');
            return;
        }
        window.open(ziel, '_blank', 'noopener,noreferrer');
    };

    const beiTaste = (event: KeyboardEvent<HTMLDivElement>) => {
        const knoepfe = Array.from(event.currentTarget.querySelectorAll('button'));
        const index = knoepfe.indexOf(document.activeElement as HTMLButtonElement);
        if (event.key === 'Escape' || event.key === 'Tab') {
            event.preventDefault();
            event.stopPropagation();
            if (event.key === 'Tab' && !event.shiftKey) onWeiter();
            else onZurueck();
        } else if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
            event.preventDefault();
            const schritt = event.key === 'ArrowRight' ? 1 : -1;
            knoepfe[(index + schritt + knoepfe.length) % knoepfe.length]?.focus();
        }
    };

    return createPortal(
        <div
            ref={leisteRef}
            id={id}
            role="toolbar"
            aria-label="Link"
            onKeyDown={beiTaste}
            onBlur={(event) => {
                const ziel = event.relatedTarget as Node | null;
                if (!event.currentTarget.contains(ziel) && !link.closest('[contenteditable="true"]')?.contains(ziel)) onSchliessen();
            }}
            className="flex max-w-[calc(100vw-1rem)] items-center gap-1 rounded-lg border border-slate-200 bg-white py-1 pl-2.5 pr-1 text-sm shadow-lg"
            style={{ position: 'fixed', top: lage.top, left: lage.left, zIndex: DIALOG_Z_BASIS + tiefe + 1, visibility: lage.sichtbar ? 'visible' : 'hidden' }}
        >
            <Link2 aria-hidden className="h-4 w-4 shrink-0 text-slate-400" />
            <span className="mr-1 whitespace-nowrap text-slate-700" title={href}>{kurzeAdresse(href)}</span>
            <Button type="button" variant="ghost" size="sm" onMouseDown={auswahlBehalten} onClick={onAendern}
                aria-label="Link ändern" title="Text oder Adresse ändern" className="h-8 min-h-8 gap-1 px-2 focus-visible:ring-inset focus-visible:ring-offset-0">
                <Pencil aria-hidden className="h-4 w-4" />Ändern
            </Button>
            <Button type="button" variant="ghost" size="sm" onMouseDown={auswahlBehalten} onClick={oeffnen}
                aria-label="Link öffnen" title="In neuem Tab öffnen" className="h-8 min-h-8 w-8 px-0 text-slate-600 focus-visible:ring-inset focus-visible:ring-offset-0">
                <ExternalLink aria-hidden className="h-4 w-4" />
            </Button>
            <Button type="button" variant="ghost" size="sm" onMouseDown={auswahlBehalten} onClick={onEntfernen}
                aria-label="Link entfernen" title="Link entfernen, Text bleibt" className="h-8 min-h-8 w-8 px-0 text-slate-600 focus-visible:ring-inset focus-visible:ring-offset-0">
                <Unlink aria-hidden className="h-4 w-4" />
            </Button>
        </div>,
        document.body,
    );
}
