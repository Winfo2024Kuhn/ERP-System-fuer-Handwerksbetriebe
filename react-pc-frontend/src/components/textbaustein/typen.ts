/** Ein Textbaustein, wie ihn `/api/textbausteine` liefert. */
export interface TextbausteinDto {
    id: number;
    name: string;
    typ: string;
    beschreibung: string | null;
    html: string | null;
    placeholders: string[];
    /** Dokumentarten als Bezeichnung, z. B. „Angebot“ oder „1. Mahnung“. */
    dokumenttypen: string[];
    sortOrder: number | null;
    createdAt?: string;
    updatedAt?: string;
}

/** Was beim Anlegen (POST) bzw. Ändern (PUT) an das Backend geht. */
export type TextbausteinSpeichernDaten = Omit<TextbausteinDto, 'id' | 'createdAt' | 'updatedAt'>;

/** Ein Eintrag aus `/api/textbausteine/placeholders`. */
export interface Platzhalter {
    /** Das gespeicherte Kürzel, z. B. `{{KUNDENNAME}}`. */
    token: string;
    label: string;
    beschreibung: string;
    beispiel: string;
    gruppe: string;
}

/** Ein Eintrag aus `/api/textbausteine/dokumenttyp-gruppen`. */
export interface DokumenttypGruppe {
    gruppe: string;
    dokumenttypen: string[];
}

/**
 * Was der Bearbeiten-Dialog bearbeitet. `id: null` heißt neu anlegen (auch bei
 * „Kopie anlegen“). `typ`, `beschreibung` und `sortOrder` gehen unverändert
 * zurück, weil die Auswahl im Dokument-Editor sie nutzt.
 */
export interface TextbausteinEntwurf {
    id: number | null;
    name: string;
    /** Gespeichertes HTML mit Platzhaltern als `{{…}}`. */
    html: string;
    dokumenttypen: string[];
    typ: string;
    beschreibung: string | null;
    sortOrder: number | null;
}
