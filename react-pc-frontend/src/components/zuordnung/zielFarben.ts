/**
 * Kennfarben für die Ziele im Modus „Nach Positionen“: jedes Projekt bzw. jede
 * Kostenstelle bekommt eine eigene, dezente Farbe, die in der Zielzeile und im
 * Etikett jeder zugeordneten Position wiederkehrt.
 *
 * Bewusst ohne rose (Markenfarbe, Ablage-Zustand), ohne amber (Warnfarbe für
 * „nicht zugeordnet“) und ohne blau/indigo/violett (Design-System). Klassen
 * stehen ausgeschrieben, damit Tailwind sie findet.
 */
export interface ZielFarbe {
    /** Farbpunkt */
    punkt: string;
    /** Etikett in der Positionszeile */
    etikett: string;
    /** Linker Rand der Positionszeile */
    rand: string;
}

const FARBEN: ZielFarbe[] = [
    { punkt: 'bg-emerald-500', etikett: 'bg-emerald-50 text-emerald-800 ring-emerald-200', rand: 'border-l-emerald-500' },
    { punkt: 'bg-cyan-500', etikett: 'bg-cyan-50 text-cyan-800 ring-cyan-200', rand: 'border-l-cyan-500' },
    { punkt: 'bg-orange-500', etikett: 'bg-orange-50 text-orange-800 ring-orange-200', rand: 'border-l-orange-500' },
    { punkt: 'bg-lime-500', etikett: 'bg-lime-50 text-lime-800 ring-lime-200', rand: 'border-l-lime-500' },
    { punkt: 'bg-teal-600', etikett: 'bg-teal-50 text-teal-800 ring-teal-200', rand: 'border-l-teal-600' },
    { punkt: 'bg-stone-500', etikett: 'bg-stone-100 text-stone-800 ring-stone-300', rand: 'border-l-stone-500' },
    { punkt: 'bg-yellow-400', etikett: 'bg-yellow-50 text-yellow-800 ring-yellow-200', rand: 'border-l-yellow-400' },
];

/** Farbe zu einem fest vergebenen Farbindex; nach der letzten Farbe wiederholt sich die Reihe. */
export function zielFarbe(index: number): ZielFarbe {
    const i = ((index % FARBEN.length) + FARBEN.length) % FARBEN.length;
    return FARBEN[i];
}

/**
 * Kleinster noch freier Farbindex. Wird beim Hinzufügen eines Ziels einmal
 * vergeben und am Ziel gespeichert – so färbt das Entfernen eines anderen
 * Ziels nichts um.
 */
export function freierFarbIndex(belegt: ReadonlyArray<number | undefined>): number {
    const vergeben = new Set(belegt.filter((i): i is number => i != null));
    let index = 0;
    while (vergeben.has(index)) index++;
    return index;
}
