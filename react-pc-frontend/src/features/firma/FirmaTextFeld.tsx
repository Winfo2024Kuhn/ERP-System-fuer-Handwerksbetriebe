import type { ReactNode } from 'react';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import type { FirmaAendern, FirmenEntwurf } from './firmaTypen';

type TextFeldName = {
    [K in keyof FirmenEntwurf]-?: FirmenEntwurf[K] extends string ? K : never
}[keyof FirmenEntwurf];

interface Props {
    label: string;
    feld: TextFeldName;
    firma: FirmenEntwurf;
    aendern: FirmaAendern;
    type?: 'email' | 'url';
    placeholder?: string;
    className?: string;
    /** Hinweis unter dem Feld. */
    children?: ReactNode;
}

/** Beschriftetes Textfeld der Firmendaten (leere Werte als ''). */
export function FirmaTextFeld({ label, feld, firma, aendern, type, placeholder, className, children }: Props) {
    return (
        <div className={className}>
            <Label>{label}</Label>
            <Input
                type={type}
                value={firma[feld] || ''}
                onChange={e => aendern({ [feld]: e.target.value })}
                placeholder={placeholder}
            />
            {children}
        </div>
    );
}
