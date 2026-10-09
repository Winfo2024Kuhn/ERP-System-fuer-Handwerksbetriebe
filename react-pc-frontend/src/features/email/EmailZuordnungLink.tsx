import type { SyntheticEvent } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight, Briefcase, Calculator, FileCheck, Truck, type LucideIcon } from 'lucide-react';
import { cn } from '../../lib/utils';
import type { EmailZuordnung, EmailZuordnungArt } from './emailZuordnung';

const ICONS: Record<EmailZuordnungArt, LucideIcon> = {
    Projekt: Briefcase,
    Anfrage: FileCheck,
    Lieferant: Truck,
    Steuerberater: Calculator,
};

interface Props {
    /** `null` (keine Zuordnung) rendert nichts. */
    zuordnung: EmailZuordnung | null;
    /** `liste`: kleines Badge in der Mail-Liste, `kopf`: etwas größer im geöffneten Mail-Kopf. */
    variante: 'liste' | 'kopf';
    className?: string;
}

/**
 * Die Liste darf bei einem Klick auf den Chip weder die Zeile auswählen noch
 * (über dnd-kit am Zeilen-Wrapper) ein Ziehen der Mail starten.
 */
const nichtWeiterreichen = (e: SyntheticEvent) => e.stopPropagation();

/** Chip „Projekt 2026-041 · Garagentor“ – ein echter Link, damit Strg/Cmd-Klick einen neuen Tab öffnet. */
export function EmailZuordnungLink({ zuordnung, variante, className }: Props) {
    if (!zuordnung) return null;
    const Icon = ICONS[zuordnung.art];
    const liste = variante === 'liste';
    const basis = cn(
        'inline-flex min-w-0 items-center gap-1 border',
        liste ? 'max-w-[16rem] rounded px-1.5 py-0.5 text-xs' : 'max-w-full rounded-md px-2 py-0.5 text-xs font-medium',
        className,
    );
    const inhalt = <>
        <Icon className={cn('shrink-0', liste ? 'h-3 w-3' : 'h-3.5 w-3.5')} aria-hidden="true" />
        <span className="truncate" data-kuerzung-erlaubt="">{zuordnung.label}</span>
    </>;

    if (!zuordnung.pfad) {
        return <span className={cn(basis, 'border-slate-200 bg-slate-50 text-slate-600')} title={zuordnung.titel}
            data-testid="email-zuordnung">
            {inhalt}
        </span>;
    }

    return <Link
        to={zuordnung.pfad}
        title={zuordnung.titel}
        aria-label={zuordnung.titel}
        draggable={false}
        data-testid="email-zuordnung"
        onClick={nichtWeiterreichen}
        onPointerDown={nichtWeiterreichen}
        onMouseDown={nichtWeiterreichen}
        onKeyDown={nichtWeiterreichen}
        className={cn(basis,
            'cursor-pointer border-rose-100 bg-rose-50 text-rose-600 transition-colors',
            'hover:border-rose-200 hover:bg-rose-100 hover:text-rose-700',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-1')}
    >
        {inhalt}
        {!liste && <ChevronRight className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />}
    </Link>;
}
