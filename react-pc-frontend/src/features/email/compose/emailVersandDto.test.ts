import { describe, expect, it } from 'vitest';
import { baueVersandDto, type VersandAngaben } from './emailVersandDto';

const BASIS: VersandAngaben = {
    draftId: 55,
    recipient: ' max.mustermann@example.org ',
    anEmpfaenger: ['max.mustermann@example.org'],
    ccRecipients: ['erika.musterfrau@example.org', ' '],
    einzelversand: false,
    subject: ' Angebot Geländer ',
    editorHtml: '<p>Hallo<script>alert(1)</script> https://onedrive.example.com/f/c/abc.</p>',
    benutzer: { id: 3, displayName: 'Max Mustermann' },
    zuordnung: { typ: 'PROJEKT', id: 12 },
    geschaeftsdokument: false,
    postfachId: 7,
    weiterleitungsAnhangIds: [4],
};

describe('baueVersandDto', () => {
    it('baut die normale Mail mit bereinigtem, verlinktem Text', () => {
        expect(baueVersandDto(BASIS)).toEqual({
            draftId: 55,
            recipients: ['max.mustermann@example.org'],
            cc: ['erika.musterfrau@example.org'],
            subject: 'Angebot Geländer',
            body: '<p>Hallo <a href="https://onedrive.example.com/f/c/abc">https://onedrive.example.com/f/c/abc</a>.</p>',
            direction: 'OUT',
            benutzer: 'Max Mustermann',
            frontendUserId: 3,
            projektId: 12,
            anfrageId: null,
            geschaeftsdokument: false,
            postfachId: 7,
            weitergeleitetVonEmailId: null,
            weiterleitungsAnhangIds: null,
            einzelversand: false,
            freigabenAbloesenFuerDokumentId: null,
        });
    });

    it('Einzelversand ohne CC, Anfrage, Weiterleitung, ohne Profil', () => {
        const dto = baueVersandDto({
            ...BASIS, einzelversand: true, anEmpfaenger: ['a@example.org', 'b@example.org'],
            zuordnung: { typ: 'ANFRAGE', id: 9 }, weitergeleitetVonEmailId: 701, benutzer: null,
            freigabenAbloesenFuerDokumentId: 5,
        });
        expect(dto).toMatchObject({
            recipients: ['a@example.org', 'b@example.org'], cc: [], projektId: null, anfrageId: 9,
            weitergeleitetVonEmailId: 701, weiterleitungsAnhangIds: [4], benutzer: '', frontendUserId: null,
            freigabenAbloesenFuerDokumentId: 5,
        });
    });
});
