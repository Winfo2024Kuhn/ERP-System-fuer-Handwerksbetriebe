import type { ReactNode } from 'react';

/** Abschnitt in den Seitenspalten des Editors: kleine Überschrift, darunter der Inhalt. */
export function Gruppe({ titel, children, zusatz }: { titel: string; children: ReactNode; zusatz?: ReactNode }) {
    return (
        <section className="border-t border-slate-100 px-4 py-4 first:border-t-0">
            <h3 className="mb-3 flex items-center gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500">{titel}{zusatz}</h3>
            {children}
        </section>
    );
}
