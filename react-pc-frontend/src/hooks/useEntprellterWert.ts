import { useEffect, useState } from 'react';

/**
 * Gibt `wert` erst zurück, wenn er sich `ms` Millisekunden lang nicht mehr
 * geändert hat – z. B. für Suchfelder, die nicht bei jedem Tastendruck den
 * Server fragen sollen.
 */
export function useEntprellterWert<T>(wert: T, ms: number): T {
    const [entprellt, setEntprellt] = useState(wert);
    useEffect(() => {
        const timer = setTimeout(() => setEntprellt(wert), ms);
        return () => clearTimeout(timer);
    }, [wert, ms]);
    return entprellt;
}
