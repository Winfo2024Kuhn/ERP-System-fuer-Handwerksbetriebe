import { useRechtAbfragen } from '../../hooks/useRechtAbfragen';

/**
 * Darf der angemeldete Benutzer Kostenstellen anlegen, ändern, löschen und wiederherstellen
 * (Recht „Kostenstellen verwalten“ der Abteilung, Administratoren immer)?
 * Solange die Antwort fehlt oder die Abfrage scheitert, gilt „nein“ (siehe {@link useRechtAbfragen}).
 */
export function useDarfKostenstellenVerwalten(): boolean {
    return useRechtAbfragen('/api/firma/kostenstellen/berechtigung');
}
