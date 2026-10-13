import type { ProjektArt, ProjektDetail } from '../types';

/** Was der Bearbeiten-Dialog (`ProjektErstellenModal`) über ein bestehendes Projekt wissen muss. */
export interface ProjektBearbeiten {
    id: number;
    bauvorhaben: string;
    kunde: string;
    kundennummer: string;
    kundenId?: number;
    auftragsnummer: string;
    bruttoPreis?: number;
    strasse?: string;
    plz?: string;
    ort?: string;
    abgeschlossen?: boolean;
    projektArt?: ProjektArt;
    kundenEmails?: string[];
    kundeDto?: {
        id: number;
        name: string;
        kundennummer: string;
        strasse?: string;
        plz?: string;
        ort?: string;
        kundenEmails?: string[];
    };
    produktkategorien?: Array<{
        id?: number; // ProjektProduktkategorie.id
        produktkategorie: {
            id: number;
            bezeichnung: string;
            verrechnungseinheit?: { name: string; anzeigename: string };
        };
        menge?: number;
    }>;
}

/**
 * Baut aus dem geladenen Projekt die Vorbelegung für den Bearbeiten-Dialog.
 *
 * Eine Stelle für beide Aufrufer (Detailansicht und Liste): Fehlte hier früher
 * die Projektart, schlug der Dialog „Pauschalpreis“ vor – und Speichern machte
 * aus Regie-, Internen und Garantie-Projekten still ein Pauschal-Projekt.
 */
export function projektZumBearbeiten(projekt: ProjektDetail): ProjektBearbeiten {
    return {
        id: projekt.id,
        bauvorhaben: projekt.bauvorhaben || '',
        kunde: projekt.kunde || '',
        kundennummer: projekt.kundennummer || '',
        kundenId: projekt.kundeDto?.id,
        auftragsnummer: projekt.auftragsnummer || '',
        bruttoPreis: projekt.bruttoPreis,
        strasse: projekt.strasse,
        plz: projekt.plz,
        ort: projekt.ort,
        abgeschlossen: projekt.abgeschlossen,
        projektArt: projekt.projektArt,
        kundeDto: projekt.kundeDto,
        produktkategorien: projekt.produktkategorien,
        kundenEmails: projekt.kundenEmails,
    };
}
