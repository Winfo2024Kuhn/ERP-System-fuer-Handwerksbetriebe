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
    /** ID der Leistung aus der Stammdaten-Tabelle (für Kategoriezuordnung) */
    leistungId?: number;
}

export interface DocumentEditorProps {
    projektId?: number;
    angebotId?: number;
    dokumentId?: number;
    initialDokumentTyp?: import('../../types').AusgangsGeschaeftsDokumentTyp;
    onClose: () => void;
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
    kundenEmails?: string[];
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
