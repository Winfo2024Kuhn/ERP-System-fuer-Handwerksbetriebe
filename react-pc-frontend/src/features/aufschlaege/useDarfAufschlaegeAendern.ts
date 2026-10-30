import { useRechtAbfragen } from '../../hooks/useRechtAbfragen';

/**
 * Darf der angemeldete Benutzer die Aufschläge ändern (Recht „Aufschläge ändern“ der Abteilung, Administratoren immer)?
 * Solange die Antwort fehlt oder die Abfrage scheitert, gilt „nein“ (siehe {@link useRechtAbfragen}).
 */
export function useDarfAufschlaegeAendern(): boolean {
    return useRechtAbfragen('/api/aufschlaege/berechtigung');
}
