import type { ComponentType, ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';
import type { BerichtMitarbeiter, GeladenesPdf } from './api';

/** Zeitraum-Auswahl im Filter: ein Monat, ein ganzes Jahr oder frei von–bis. */
export type ZeitraumArt = 'monat' | 'jahr' | 'bereich';

/**
 * Alle Filterwerte eines Berichts. Ein Bericht nutzt nur die Teile, für die er
 * ein Filterfeld hat; so reicht für jeden Bericht derselbe Zustand.
 */
export interface FilterWerte {
    jahr: number;
    monat: number;
    zeitraumArt: ZeitraumArt;
    von: string;
    bis: string;
    /** `null`: noch nicht angefasst – dann gilt die Vorauswahl des Feldes. */
    mitarbeiterIds: number[] | null;
    schalter: Record<string, boolean>;
}

/** Text für einen Info-Knopf: „Was passiert im Hintergrund?“ und „Was bewirkt das?“. */
export interface InfoText {
    titel: string;
    hintergrund: string;
    bewirkt: string;
}

export interface MitarbeiterFeld {
    typ: 'mitarbeiter';
    min: number;
    max: number;
    /** Beim Öffnen alle Mitarbeiter vorauswählen (sonst keinen). */
    alleVorauswaehlen: boolean;
    /**
     * „Alle“ ist immer erlaubt, auch über `max`: Dann gehen keine IDs an den
     * Server, er nimmt selbst alle relevanten Mitarbeiter. `max` gilt nur für eine Teilauswahl.
     */
    alleErlaubt?: boolean;
}

export interface SchalterFeld {
    typ: 'schalter';
    schluessel: string;
    beschriftung: string;
    info?: InfoText;
}

/** Ein Filterfeld; `BerichtFilter` rendert jede Art auf dieselbe Weise. */
export type FilterFeld =
    | { typ: 'monat' }
    | { typ: 'zeitraum' }
    | MitarbeiterFeld
    | SchalterFeld;

/** Was der letzte Schritt des Assistenten zum Arbeiten bekommt. */
export interface BerichtAbschlussProps {
    definition: BerichtDefinition;
    werte: FilterWerte;
    /** Gewählte Mitarbeiter (schon aufgelöst, in Listenreihenfolge). */
    mitarbeiter: BerichtMitarbeiter[];
    /** Sind alle auswählbaren Mitarbeiter gewählt? Dann darf ein Bericht die IDs weglassen. */
    alleGewaehlt: boolean;
    /**
     * Ein PDF wurde erzeugt – kommt in „Zuletzt erstellt“. Liefert die Adresse
     * (`blob:`), über die es heruntergeladen wird; die Seite gibt sie später frei.
     */
    onPdfErstellt: (pdf: GeladenesPdf, titel: string) => string;
    /** Der „Zurück“-Knopf, damit jeder Abschluss ihn in seine Fußleiste setzt. */
    zurueck: ReactNode;
}

export interface BerichtDefinition {
    id: string;
    titel: string;
    beschreibung: string;
    icon: LucideIcon;
    filterFelder: FilterFeld[];
    standardwerte: (heute: Date) => FilterWerte;
    /**
     * Adresse des PDFs zu den Werten und den gewählten Mitarbeitern – für Berichte mit
     * `PdfAbschluss`. Ein eigener Abschluss (z. B. Steuerberater) baut die Adresse selbst.
     */
    pdfUrl?: (werte: FilterWerte, mitarbeiterIds: number[]) => string;
    /** Dateiname, falls der Server keinen nennt. */
    ersatzDateiname: (werte: FilterWerte) => string;
    /** Inhalt des letzten Schritts (Vorschau, Knöpfe). */
    Abschluss: ComponentType<BerichtAbschlussProps>;
}
