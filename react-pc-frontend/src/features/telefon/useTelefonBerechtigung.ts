import { erzeugeBerechtigungsSpeicher } from '../../lib/berechtigungsSpeicher';
import { ladeBerechtigung } from './api';

/**
 * Darf der angemeldete Benutzer Anrufe und Anrufbeantworter sehen?
 *
 * <p>Rückgabe: `null` solange noch geprüft wird, danach `true`/`false`
 * (Fehler = `false`). Alle Stellen teilen sich eine Anfrage, siehe
 * {@link erzeugeBerechtigungsSpeicher}. {@link setzeTelefonBerechtigungZurueck}
 * leert den Speicher – das Layout ruft das bei jedem Benutzerwechsel auf.</p>
 */
const speicher = erzeugeBerechtigungsSpeicher(ladeBerechtigung);

/** Vergisst das Ergebnis; alle eingebundenen Stellen fragen neu. */
export const setzeTelefonBerechtigungZurueck = speicher.setzeZurueck;

export const useTelefonBerechtigung = speicher.useBerechtigung;
