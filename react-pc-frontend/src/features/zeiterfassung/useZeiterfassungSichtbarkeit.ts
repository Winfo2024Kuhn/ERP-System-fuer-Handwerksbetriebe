import { erzeugeBerechtigungsSpeicher } from '../../lib/berechtigungsSpeicher';
import { ladeZeiterfassungSichtbarkeit } from './api';

/**
 * Sieht der angemeldete Benutzer am PC den Menü-Bereich „Zeiterfassung“?
 *
 * <p>Rückgabe: `null` solange noch geprüft wird, danach `true`/`false`
 * (Fehler = `false`). Menü, Seitenschutz und Handy-Navigation teilen sich eine
 * Anfrage. {@link setzeZeiterfassungSichtbarkeitZurueck} leert den Speicher –
 * das Layout ruft das bei jedem Benutzerwechsel auf.</p>
 */
const speicher = erzeugeBerechtigungsSpeicher(ladeZeiterfassungSichtbarkeit);

export const setzeZeiterfassungSichtbarkeitZurueck = speicher.setzeZurueck;

export const useZeiterfassungSichtbarkeit = speicher.useBerechtigung;
