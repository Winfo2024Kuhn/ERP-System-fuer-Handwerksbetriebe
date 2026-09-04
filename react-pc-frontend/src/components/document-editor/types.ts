import type { useEditor } from '@tiptap/react';
import type {
    AusgangsGeschaeftsDokument,
    AusgangsGeschaeftsDokumentTyp,
    FormBlock,
    FormBlockType
} from '../../types';

export interface DocBlock {
    id: string;
    type: 'TEXT' | 'SERVICE' | 'CLOSURE' | 'SEPARATOR' | 'SECTION_HEADER' | 'SUBTOTAL';
    content?: string;
    pos?: string;
    title?: string;
    quantity?: number;
    unit?: string;
    price?: number;
    description?: string;
    fontSize?: number;
    fett?: boolean;
    optional?: boolean;
    /** Label for SECTION_HEADER blocks (e.g. "Bauabschnitt 1: Rohbau") */
    sectionLabel?: string;
    /** Child blocks for SECTION_HEADER containers (nested services) */
    children?: DocBlock[];
    /** Rabatt in Prozent für diese Position (0-100) */
    discount?: number;
    /**
     * Name der Entweder-Oder-Gruppe. Gesetzt = der Kunde muss genau eine Variante
     * der Gruppe waehlen. Leer/undefined bei `optional: true` = frei dazubuchbare
     * Zusatzposition. Nur zusammen mit `optional: true` sinnvoll.
     */
    alternativGruppe?: string;
    /** ID der Leistung aus der Stammdaten-Tabelle (für Kategoriezuordnung) */
    leistungId?: number;
    /** ID der Produktkategorie (von Leistung.kategorie) */
    kategorieId?: number;
    /**
     * ID des Artikels aus den Stammdaten, aus dem diese Position entstanden ist.
     * Reine Herkunftsangabe: Preis und Text stehen fest im Block, damit ein
     * bereits verschicktes Angebot sich nicht rueckwirkend aendert, wenn jemand
     * den Artikel oder seinen Aufschlag anpasst.
     */
    artikelId?: number;
    /**
     * Markiert TEXT-Bloecke, die automatisch aus den
     * "Standard-Texte"-Defaults der Vorlage eingefuegt wurden.
     * Beim Wechsel des Dokumenttyps werden diese ersetzt; manuell
     * eingefuegte Textbausteine (ohne Rolle) bleiben erhalten.
     */
    textbausteinRolle?: 'VOR' | 'NACH';
    /** ID des verwendeten Textbausteins (zur Wiedererkennung). */
    textbausteinId?: number;
    /**
     * Fuer welchen Dokumenttyp dieser Default-Textbaustein erzeugt wurde.
     * Wird beim Umwandeln (z.B. Angebot -> AB) verwendet, um veraltete
     * Standard-Textbausteine automatisch durch die des neuen Typs zu ersetzen.
     */
    textbausteinDokumenttyp?: AusgangsGeschaeftsDokumentTyp;
}

export interface DocumentEditorProps {
    projektId?: number;
    anfrageId?: number;
    dokumentId?: number;
    initialDokumentTyp?: import('../../types').AusgangsGeschaeftsDokumentTyp;
    /**
     * Schliesst den Editor, wenn kein Tab zum Schliessen da ist (die Seite
     * entscheidet dann zwischen `navigate(-1)` und `window.close()`). Wird
     * NUR benutzt, wenn `onLockFreigeben` fehlt -- sonst uebernimmt der
     * X-Button-Ablauf selbst das Schliessen (siehe dort).
     */
    onClose: () => void;
    /**
     * true = die Seite haelt gerade kein Lock, der Nutzer darf nur lesen.
     * Verhaelt sich wie das bisherige `isLocked` (schreibgeschuetzt, keine
     * Aktionen). Default false.
     */
    readOnly?: boolean;
    /**
     * Gibt die von der Seite gehaltene Sperre aktiv frei und wartet, bis der
     * Server das bestaetigt hat. Wird im X-Button-Ablauf zwischen Speichern
     * und Tab-Schliessen aufgerufen (siehe dort). Ohne diesen Prop bleibt das
     * bisherige Verhalten von `onClose` unveraendert.
     */
    onLockFreigeben?: () => Promise<void>;
}

export interface KontextDaten {
    kundennummer?: string;
    kundenName?: string;
    kundeId?: number;
    rechnungsadresse?: string;
    projektnummer?: string;
    projektBauvorhaben?: string;
    anrede?: string;
    ansprechpartner?: string;
    bezugsdokument?: string;
    bezugsdokumentTyp?: string;
    bezugsdokumentDatum?: string;
    kundenEmails?: string[];
    zahlungsziel?: number;
}

export interface TextbausteinApiDto {
    id: number;
    name: string;
    typ: string;
    beschreibung?: string;
    html?: string;
    dokumenttypen?: string[];
}

export interface LeistungApiDto {
    id: number;
    name: string;
    description: string;
    price: number;
    unit: { name: string; anzeigename: string };
    folderId?: number;
    kategoriePfad?: string;
}

export interface ArbeitszeitartApiDto {
    id: number;
    bezeichnung: string;
    beschreibung?: string;
    stundensatz: number;
    aktiv: boolean;
}

export type EditorInstance = ReturnType<typeof useEditor>;

export type {
    AusgangsGeschaeftsDokument,
    AusgangsGeschaeftsDokumentTyp,
    FormBlock,
    FormBlockType
};
