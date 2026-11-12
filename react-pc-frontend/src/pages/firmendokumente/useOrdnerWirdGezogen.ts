import { useEffect, useState } from 'react';
import { gezogeneOrdnerId } from './ordnerZiehen';

/**
 * Welcher Ordner wird gerade irgendwo auf der Seite gezogen (`null`: keiner)? Lauscht am Dokument,
 * damit es egal ist, ob der Ordner aus dem Baum oder aus dem rechten Bereich
 * kommt. Der Zustand wechselt erst nach dem Ziehstart: Ändert sich die Seite
 * noch im `dragstart`, bricht Chrome das Ziehen ab.
 */
export function useOrdnerWirdGezogen(): number | null {
    const [gezogen, setGezogen] = useState<number | null>(null);
    useEffect(() => {
        const uhren: ReturnType<typeof setTimeout>[] = [];
        const setzeSpaeter = (wert: number | null) => uhren.push(setTimeout(() => setGezogen(wert), 0));
        const start = (event: Event) => {
            const id = gezogeneOrdnerId((event as DragEvent).dataTransfer?.types ?? []);
            if (id !== null) setzeSpaeter(id);
        };
        const ende = () => setzeSpaeter(null);
        document.addEventListener('dragstart', start);
        document.addEventListener('dragend', ende);
        document.addEventListener('drop', ende);
        return () => {
            uhren.forEach(clearTimeout);
            document.removeEventListener('dragstart', start);
            document.removeEventListener('dragend', ende);
            document.removeEventListener('drop', ende);
        };
    }, []);
    return gezogen;
}
