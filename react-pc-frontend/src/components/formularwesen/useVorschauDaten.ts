import { useEffect, useState } from 'react';
import type { Kunde, Projekt } from '../../types';
import { SAMPLE_DATA } from './constants';

type Liste<T> = T[] | { content?: T[]; data?: T[] } | null | undefined;
const alsListe = <T,>(antwort: Liste<T>): T[] => Array.isArray(antwort) ? antwort : (antwort?.content ?? antwort?.data ?? []);
const zufaellig = <T,>(liste: T[]): T | null => liste.length ? liste[Math.floor(Math.random() * liste.length)] : null;

/** Baut aus einem Kunden und einem Projekt die Werte für die Platzhalter. */
export function baueVorschauDaten(kunde: Kunde | null, projekt: Projekt | null): Record<string, string> {
    if (!kunde && !projekt) return SAMPLE_DATA;
    const ansprechpartner = kunde?.ansprechpartner || kunde?.ansprechspartner || '';
    const adressTeile = [
        kunde?.name,
        ansprechpartner,
        kunde?.strasse,
        [kunde?.plz, kunde?.ort].filter(Boolean).join(' '),
    ].filter(Boolean);
    return {
        ...SAMPLE_DATA,
        ...(kunde?.kundennummer ? { kundennummer: kunde.kundennummer } : {}),
        ...(kunde?.name ? { kundenname: kunde.name } : {}),
        ...(adressTeile.length > 0 ? { kundenadresse: adressTeile.join('\n') } : {}),
        ...(kunde?.anrede ? { anrede: kunde.anrede } : {}),
        ...(ansprechpartner ? { ansprechpartner } : {}),
        ...(projekt?.bauvorhaben ? { bauvorhaben: projekt.bauvorhaben } : {}),
        ...(projekt?.auftragsnummer ? { dokumentnummer: projekt.auftragsnummer } : {}),
        ...(projekt?.id ? { projektnummer: `PRJ-${projekt.id}` } : {}),
        datum: projekt?.anlegedatum ? new Date(projekt.anlegedatum).toLocaleDateString('de-DE') : new Date().toLocaleDateString('de-DE'),
    };
}

/**
 * Echte Beispieldaten für Editor und Vorschau: ein zufälliger Kunde und ein
 * zufälliges Projekt. Klappt das Laden nicht, bleiben die Musterdaten stehen –
 * die Vorschau funktioniert auch ohne.
 */
export function useVorschauDaten(): Record<string, string> {
    const [daten, setDaten] = useState<Record<string, string>>(SAMPLE_DATA);

    useEffect(() => {
        let abgebrochen = false;
        // Fehler fängt laden() selbst ab (leere Liste) – dann bleiben die Musterdaten.
        const laden = <T,>(url: string) => fetch(url).then(r => (r.ok ? r.json() : [])).catch(() => []) as Promise<Liste<T>>;
        Promise.all([laden<Kunde>('/api/kunden?size=500'), laden<Projekt>('/api/projekte/simple?size=500')])
            .then(([kunden, projekte]) => {
                if (abgebrochen) return;
                const kunde = zufaellig(alsListe(kunden).filter(k => k.name?.trim()));
                const projekt = zufaellig(alsListe(projekte).filter(p => p.bauvorhaben?.trim()));
                setDaten(baueVorschauDaten(kunde, projekt));
            });
        return () => { abgebrochen = true; };
    }, []);

    return daten;
}
