import { htmlFuerVersand } from '../mailEditorHtml';

export interface VersandAngaben {
    draftId: number | null | undefined;
    recipient: string;
    anEmpfaenger: string[];
    ccRecipients: string[];
    einzelversand: boolean;
    subject: string;
    editorHtml: string;
    benutzer: { id: number; displayName: string } | null;
    zuordnung: { typ: 'PROJEKT' | 'ANFRAGE'; id: number } | null;
    geschaeftsdokument: boolean;
    /** Nur bei frei gewähltem Absender; Antwort/Weiterleitung legt das Backend fest. */
    postfachId: number | null;
    weitergeleitetVonEmailId?: number;
    weiterleitungsAnhangIds: number[];
    freigabenAbloesenFuerDokumentId?: number;
}

/** Baut das `dto` für `POST /api/emails/send` bzw. `/{id}/reply`. */
export function baueVersandDto(a: VersandAngaben) {
    return {
        draftId: a.draftId,
        // Einzelversand: jeder Empfänger einzeln, damit das Backend je eine Mail baut.
        recipients: a.einzelversand ? a.anEmpfaenger : [a.recipient.trim()],
        cc: a.einzelversand ? [] : a.ccRecipients.filter(c => c.trim().length > 0),
        subject: a.subject.trim(),
        body: htmlFuerVersand(a.editorHtml),
        direction: 'OUT',
        benutzer: a.benutzer?.displayName || '',
        frontendUserId: a.benutzer?.id || null,
        projektId: a.zuordnung?.typ === 'PROJEKT' && a.zuordnung.id ? a.zuordnung.id : null,
        anfrageId: a.zuordnung?.typ === 'ANFRAGE' && a.zuordnung.id ? a.zuordnung.id : null,
        // Entscheidet serverseitig, ob das Postfach für Geschäftsdokumente statt des Standard-Postfachs greift.
        geschaeftsdokument: a.geschaeftsdokument,
        postfachId: a.postfachId,
        weitergeleitetVonEmailId: a.weitergeleitetVonEmailId ?? null,
        // Welche Anhänge der Original-Mail mitgehen; das Backend hängt sie selbst an.
        weiterleitungsAnhangIds: a.weitergeleitetVonEmailId ? a.weiterleitungsAnhangIds : null,
        einzelversand: a.einzelversand,
        freigabenAbloesenFuerDokumentId: a.freigabenAbloesenFuerDokumentId ?? null,
    };
}
