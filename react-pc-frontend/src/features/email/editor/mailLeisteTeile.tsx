import { forwardRef, type ComponentType, type ReactNode } from 'react';
import { ChevronDown } from 'lucide-react';
import { Button, type ButtonProps } from '../../../components/ui/button';
import { cn } from '../../../lib/utils';
import { auswahlBehalten } from './mailLeisteTasten';

type Icon = ComponentType<{ className?: string; 'aria-hidden'?: boolean }>;

const KNOPF = 'h-8 min-h-8 px-2 text-slate-600 hover:bg-rose-50 hover:text-rose-700 focus-visible:ring-inset focus-visible:ring-offset-0';

interface LeistenKnopfProps extends Omit<ButtonProps, 'children'> {
    titel: string;
    icon: Icon;
    /** Tastenkürzel für den Tooltip, z. B. „⌘/Strg+B“. */
    kuerzel?: string;
    aktiv?: boolean;
}

/** Icon-Knopf der Leiste mit Tooltip und sichtbarem An/Aus-Zustand. */
export function LeistenKnopf({ titel, icon: Symbol, kuerzel, aktiv, className, ...props }: LeistenKnopfProps) {
    return (
        <Button
            type="button"
            variant="ghost"
            size="sm"
            aria-label={titel}
            title={kuerzel ? `${titel} (${kuerzel})` : titel}
            aria-pressed={aktiv === undefined ? undefined : aktiv}
            onMouseDown={auswahlBehalten}
            className={cn(KNOPF, 'w-8 px-0', aktiv && 'bg-rose-100 text-rose-700', className)}
            {...props}
        >
            <Symbol aria-hidden className="h-4 w-4" />
        </Button>
    );
}

interface MenueKnopfProps extends Omit<ButtonProps, 'children'> {
    titel: string;
    icon: Icon;
}

/** Knopf, der ein Untermenü öffnet (Trigger für das Dropdown, darum mit Ref). */
export const MenueKnopf = forwardRef<HTMLButtonElement, MenueKnopfProps>(
    ({ titel, icon: Symbol, className, ...props }, ref) => (
        <Button
            ref={ref}
            type="button"
            variant="ghost"
            size="sm"
            aria-label={titel}
            title={titel}
            onMouseDown={auswahlBehalten}
            className={cn(KNOPF, 'gap-1 data-[state=open]:bg-rose-100 data-[state=open]:text-rose-700', className)}
            {...props}
        >
            <Symbol aria-hidden className="h-4 w-4" />
            <span className="text-xs font-medium">{titel}</span>
            <ChevronDown aria-hidden className="h-3.5 w-3.5 text-slate-400" />
        </Button>
    ),
);
MenueKnopf.displayName = 'MenueKnopf';

export function Trenner() {
    return <span aria-hidden="true" className="mx-0.5 h-5 w-px shrink-0 bg-slate-200" />;
}

/** Abschnittstitel in einem Leisten-Menü. */
export function MenueTitel({ children }: { children: ReactNode }) {
    return <p className="px-2 pb-1 pt-1.5 text-xs font-semibold uppercase tracking-wide text-slate-500">{children}</p>;
}
