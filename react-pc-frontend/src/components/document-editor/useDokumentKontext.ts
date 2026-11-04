import { useCallback, useEffect, useRef, useState } from 'react';
import type { AusgangsGeschaeftsDokument } from '../../types';
import {
    neuerKontextAusAnfrage,
    neuerKontextAusProjekt,
    type AnfrageKontextAntwort,
    type ProjektKontextAntwort,
} from './dokumentKontextAbbildung';
import { holeJson, ladeBezugKontext, ladeStammdatenKontext, type KontextAenderung } from './dokumentKontextLaden';
import type { KontextDaten } from './types';

interface Parameter {
    dokumentId?: number;
    projektId?: number;
    anfrageId?: number;
    /** Typ beim Öffnen (Folgedokumente einer Anfrage beziehen sich auf sie). */
    dokumentTyp: string;
    /** Betreff aus Projekt bzw. Anfrage übernehmen (nur neue Dokumente). */
    setzeBetreff: (betreff: string) => void;
}

/**
 * Kunden-, Projekt- und Bezugsdaten des Dokumenteditors (`kontextDaten`):
 * Woher Anrede, Adresse, Zahlungsziel und E-Mail-Adressen kommen.
 *
 * - Neues Dokument aus Projekt/Anfrage: lädt beim Öffnen selbst.
 * - Gespeichertes Dokument: `ladeFuerDokument(data)` aus dem Dokument-Laden.
 * - Vom Nutzer bearbeitete Adresse/Zahlungsziel (die beiden `…UserEditedRef`)
 *   überschreibt ein später ankommender Ladevorgang nicht mehr.
 */
export function useDokumentKontext({ dokumentId, projektId, anfrageId, dokumentTyp, setzeBetreff }: Parameter) {
    const [kontextDaten, setKontextDaten] = useState<KontextDaten>({});
    // `handleSave` haengt `kontextDaten` nicht in seiner Dep-Liste und liest
    // ueber dieses Ref, sonst ginge eine reine Adressaenderung verloren.
    const kontextDatenRef = useRef(kontextDaten);
    useEffect(() => { kontextDatenRef.current = kontextDaten; }, [kontextDaten]);
    // Sticky: hat der Nutzer Adresse bzw. Zahlungsziel angefasst, darf ein
    // spaeter eintreffender Kontext-Load sie nicht mehr ueberschreiben.
    const adresseUserEditedRef = useRef(false);
    const zahlungszielUserEditedRef = useRef(false);

    /** Ref-first-Setter fuer Nutzeraenderungen (Rueckgaengig, Adresse, Zahlungsziel). */
    const setzeKontextDaten = useCallback((naechste: KontextDaten) => {
        kontextDatenRef.current = naechste;
        setKontextDaten(naechste);
    }, []);

    /**
     * true, sobald der Kontext-Load (Projekt/Anfrage) abgeschlossen ist, auch
     * ohne Daten. Die Standard-Textbausteine warten nur, solange er laeuft.
     */
    const [kontextGeladen, setKontextGeladen] = useState(false);
    useEffect(() => {
        const loadKontext = async () => {
            try {
                // Geerbte Rechnungsanschrift: wurde sie in einem Dokument dieses
                // Vorgangs bearbeitet, zeigt ein NEUES Dokument sie sofort an
                // (das Backend uebernimmt sie beim Erstellen ohnehin).
                let geerbteAdresse: string | null = null;
                if (!dokumentId && (projektId || anfrageId)) {
                    try {
                        const basis = projektId ? `projekt/${projektId}` : `anfrage/${anfrageId}`;
                        const antwort = await holeJson<{ rechnungsadresseOverride?: string }>(
                            `/api/ausgangs-dokumente/${basis}/geerbte-rechnungsadresse`);
                        geerbteAdresse = antwort?.rechnungsadresseOverride || null;
                    } catch { /* Fallback: berechnete Kundenadresse anzeigen */ }
                }
                // Gespeichertes Dokument: dessen Zahlungsziel setzt ladeFuerDokument;
                // kaeme diese Antwort zuletzt an, stuende sonst der Kunden-Standard da.
                // `dokumentId` aus der Closure -- der Effekt haengt bewusst nur an
                // projektId/anfrageId.
                const optionen = () => ({
                    geerbteAdresse,
                    adresseBehalten: adresseUserEditedRef.current,
                    zahlungszielBehalten: !!dokumentId || zahlungszielUserEditedRef.current,
                });
                if (projektId) {
                    const projekt = await holeJson<ProjektKontextAntwort>(`/api/projekte/${projektId}`);
                    if (projekt) {
                        setKontextDaten(prev => neuerKontextAusProjekt(prev, projekt, optionen()));
                        setzeBetreff(projekt.bauvorhaben || '');
                    }
                } else if (anfrageId) {
                    const anfrage = await holeJson<AnfrageKontextAntwort>(`/api/anfragen/${anfrageId}`);
                    if (anfrage) {
                        const anfrageAlsBezug = !dokumentId && dokumentTyp !== 'ANGEBOT';
                        setKontextDaten(prev => neuerKontextAusAnfrage(prev, anfrage, { ...optionen(), anfrageAlsBezug }));
                        setzeBetreff(anfrage.bauvorhaben || '');
                    }
                }
            } catch (err) {
                console.error('Fehler beim Laden der Kontext-Daten:', err);
            } finally {
                setKontextGeladen(true);
            }
        };
        loadKontext();
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [projektId, anfrageId]);

    /**
     * Kontext eines gespeicherten Dokuments: erst Kunde/Projekt/Anfrage, dann
     * der Vorgaenger (abgewartet, damit umgewandelte Standardtexte nicht mit
     * leerem {{BEZUGSDOKUMENTDATUM}} eingefroren werden). Ein gespeicherter
     * Adress-Override gilt als Nutzeraenderung. Netzwerkfehler beim
     * Stammdaten-Laden gehen an den Aufrufer weiter.
     */
    const ladeFuerDokument = useCallback((data: AusgangsGeschaeftsDokument): Promise<void> => {
        if (data.rechnungsadresseOverride) adresseUserEditedRef.current = true;
        const anwenden = (aenderung: KontextAenderung | null) => { if (aenderung) setKontextDaten(aenderung); };
        return ladeStammdatenKontext(data, () => adresseUserEditedRef.current)
            .then(anwenden)
            .then(() => ladeBezugKontext(data))
            .then(anwenden);
    }, []);

    return {
        kontextDaten,
        kontextDatenRef,
        setzeKontextDaten,
        adresseUserEditedRef,
        zahlungszielUserEditedRef,
        kontextGeladen,
        ladeFuerDokument,
    };
}
