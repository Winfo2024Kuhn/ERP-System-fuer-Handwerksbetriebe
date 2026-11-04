import { describe, it, expect } from 'vitest';
import {
    eindeutigeEmails,
    neuerKontextAusProjekt,
    neuerKontextAusAnfrage,
    kontextAusDokumentSelbst,
    dokumentKontextAusProjekt,
    dokumentKontextAusAnfrage,
    dokumentKontextAusKunde,
    type ProjektKontextAntwort,
    type AnfrageKontextAntwort,
    type KundeKontextAntwort,
    type DokumentFuerKontext,
} from './dokumentKontextAbbildung';

const kunde: KundeKontextAntwort = {
    name: 'Max Mustermann',
    strasse: 'Musterweg 1',
    plz: '12345',
    ort: 'Musterstadt',
    anrede: 'HERR',
    ansprechspartner: 'Mustermann',
    zahlungsziel: 21,
    kundenEmails: ['max@example.com', 'buero@example.com'],
};

const projekt: ProjektKontextAntwort = {
    auftragsnummer: 'P-2026-01',
    bauvorhaben: 'Balkongeländer',
    kundennummer: 'K-100',
    kunde: 'Max Mustermann',
    kundenId: 7,
    kundenEmails: ['max@example.com'],
    kundeDto: kunde,
};

const anfrage: AnfrageKontextAntwort = {
    kundennummer: 'K-200',
    kundenName: 'Erika Musterfrau',
    kundenId: 8,
    kundenStrasse: 'Beispielstr. 2',
    kundenPlz: '54321',
    kundenOrt: 'Beispielstadt',
    kundenAnrede: 'FRAU',
    kundenAnsprechpartner: 'Musterfrau',
    kundenEmails: ['erika@example.com', 'erika@example.com', ''],
    zahlungsziel: 10,
    anfragesnummer: 'AF-2026-03',
};

const dokument: DokumentFuerKontext = {
    typ: 'RECHNUNG',
    gebucht: false,
    kundennummer: 'K-300',
    kundenName: 'Gespeicherter Name',
    rechnungsadresse: 'Gespeicherte Adresse',
    projektnummer: 'P-OLD',
    projektBauvorhaben: 'Altes Bauvorhaben',
    vorgaengerNummer: 'AB-2026-01',
    zahlungszielTage: 30,
};

describe('eindeutigeEmails', () => {
    it('sammelt Adressen ohne Doppelte und Leere, in Reihenfolge', () => {
        expect(eindeutigeEmails(['a@x.de', '', 'b@x.de'], undefined, ['b@x.de', 'c@x.de'], null)).toEqual(['a@x.de', 'b@x.de', 'c@x.de']);
    });
});

describe('Kontext eines neuen Dokuments', () => {
    it('aus dem Projekt: Kunde, Adresse aus Stammdaten, E-Mails, Zahlungsziel des Kunden', () => {
        const ergebnis = neuerKontextAusProjekt({ bezugsdokument: 'X' }, projekt, {
            geerbteAdresse: null, adresseBehalten: false, zahlungszielBehalten: false,
        });
        expect(ergebnis).toEqual({
            bezugsdokument: 'X',
            projektnummer: 'P-2026-01',
            projektBauvorhaben: 'Balkongeländer',
            kundennummer: 'K-100',
            kundenName: 'Max Mustermann',
            kundeId: 7,
            rechnungsadresse: 'Max Mustermann\nMusterweg 1\n12345 Musterstadt',
            anrede: 'HERR',
            ansprechpartner: 'Mustermann',
            kundenEmails: ['max@example.com', 'buero@example.com'],
            zahlungsziel: 21,
        });
    });

    it('aus dem Projekt: geerbte Adresse vor Stammdaten, bearbeitete Adresse und Zahlungsziel bleiben', () => {
        const vorher = { rechnungsadresse: 'Selbst getippt', zahlungsziel: 5 };
        expect(neuerKontextAusProjekt(vorher, projekt, { geerbteAdresse: 'Geerbt', adresseBehalten: false, zahlungszielBehalten: true }))
            .toMatchObject({ rechnungsadresse: 'Geerbt', zahlungsziel: 5 });
        expect(neuerKontextAusProjekt(vorher, projekt, { geerbteAdresse: 'Geerbt', adresseBehalten: true, zahlungszielBehalten: false }))
            .toMatchObject({ rechnungsadresse: 'Selbst getippt', zahlungsziel: 21 });
    });

    it('aus dem Projekt ohne Kunde: Standard-Zahlungsziel und leere Adresse', () => {
        const ergebnis = neuerKontextAusProjekt({}, { auftragsnummer: 'P-1' }, { geerbteAdresse: null, adresseBehalten: false, zahlungszielBehalten: false });
        expect(ergebnis).toMatchObject({ projektnummer: 'P-1', rechnungsadresse: '', zahlungsziel: 8, kundenEmails: [] });
    });

    it('aus der Anfrage: Kunde, Adresse aus der Anfrage, Anfrage als Bezug nur auf Wunsch', () => {
        const optionen = { geerbteAdresse: null, adresseBehalten: false, zahlungszielBehalten: false, anfrageAlsBezug: false };
        expect(neuerKontextAusAnfrage({}, anfrage, optionen)).toEqual({
            kundennummer: 'K-200',
            kundenName: 'Erika Musterfrau',
            kundeId: 8,
            rechnungsadresse: 'Erika Musterfrau\nBeispielstr. 2\n54321 Beispielstadt',
            anrede: 'FRAU',
            ansprechpartner: 'Musterfrau',
            kundenEmails: ['erika@example.com'],
            zahlungsziel: 10,
        });
        expect(neuerKontextAusAnfrage({}, anfrage, { ...optionen, anfrageAlsBezug: true }))
            .toMatchObject({ bezugsdokument: 'AF-2026-03', bezugsdokumentTyp: 'Angebot' });
    });

    it('aus der Anfrage: Ersatzfelder für Anrede und Ansprechpartner', () => {
        const alt: AnfrageKontextAntwort = { anrede: 'FAMILIE', kundenAnsprechspartner: 'Mustermann' };
        expect(neuerKontextAusAnfrage({}, alt, { geerbteAdresse: null, adresseBehalten: false, zahlungszielBehalten: true, anfrageAlsBezug: false }))
            .toMatchObject({ anrede: 'FAMILIE', ansprechpartner: 'Mustermann' });
    });
});

describe('Kontext eines gespeicherten Dokuments', () => {
    it('aus dem Dokument selbst (gebucht oder ohne Verknüpfung)', () => {
        expect(kontextAusDokumentSelbst(dokument)).toEqual({
            kundennummer: 'K-300',
            kundenName: 'Gespeicherter Name',
            rechnungsadresse: 'Gespeicherte Adresse',
            projektnummer: 'P-OLD',
            projektBauvorhaben: 'Altes Bauvorhaben',
            bezugsdokument: 'AB-2026-01',
            zahlungsziel: 30,
        });
        expect(kontextAusDokumentSelbst({ ...dokument, zahlungszielTage: undefined }).zahlungsziel).toBe(8);
    });

    it('aus dem Projekt: gespeicherte Adresse und Zahlungsziel haben Vorrang', () => {
        expect(dokumentKontextAusProjekt({ ...dokument, rechnungsadresseOverride: 'Override' }, projekt)).toEqual({
            projektnummer: 'P-2026-01',
            projektBauvorhaben: 'Balkongeländer',
            kundennummer: 'K-100',
            kundenName: 'Max Mustermann',
            kundeId: 7,
            rechnungsadresse: 'Override',
            anrede: 'HERR',
            ansprechpartner: 'Mustermann',
            bezugsdokument: 'AB-2026-01',
            zahlungsziel: 30,
        });
        expect(dokumentKontextAusProjekt({ ...dokument, zahlungszielTage: undefined }, projekt))
            .toMatchObject({ rechnungsadresse: 'Max Mustermann\nMusterweg 1\n12345 Musterstadt', zahlungsziel: 21 });
    });

    it('aus der Anfrage: Folgedokumente beziehen sich auf die Anfrage', () => {
        expect(dokumentKontextAusAnfrage(dokument, anfrage)).toEqual({
            kundennummer: 'K-200',
            kundenName: 'Erika Musterfrau',
            kundeId: 8,
            rechnungsadresse: 'Erika Musterfrau\nBeispielstr. 2\n54321 Beispielstadt',
            anrede: 'FRAU',
            ansprechpartner: 'Musterfrau',
            zahlungsziel: 30,
            bezugsdokument: 'AF-2026-03',
            bezugsdokumentTyp: 'Angebot',
        });
        const angebot = dokumentKontextAusAnfrage({ ...dokument, typ: 'ANGEBOT', zahlungszielTage: undefined }, anfrage);
        expect(angebot.bezugsdokument).toBeUndefined();
        expect(angebot.zahlungsziel).toBe(10);
    });

    it('aus dem Kunden: Name aus Vor- und Nachname, bearbeitete Adresse bleibt', () => {
        const privat: KundeKontextAntwort = { vorname: 'Max', nachname: 'Mustermann', anrede: 'HERR', ansprechpartner: 'Mustermann' };
        expect(dokumentKontextAusKunde({ rechnungsadresse: 'Alt' }, { ...dokument, kundeId: 7 }, privat, false)).toEqual({
            kundennummer: 'K-300',
            kundenName: 'Max Mustermann',
            kundeId: 7,
            rechnungsadresse: '',
            anrede: 'HERR',
            ansprechpartner: 'Mustermann',
            bezugsdokument: 'AB-2026-01',
            zahlungsziel: 30,
        });
        expect(dokumentKontextAusKunde({ rechnungsadresse: 'Alt' }, dokument, kunde, true).rechnungsadresse).toBe('Alt');
        expect(dokumentKontextAusKunde({}, { ...dokument, rechnungsadresseOverride: 'Override' }, kunde, false).rechnungsadresse).toBe('Override');
        expect(dokumentKontextAusKunde({}, { ...dokument, zahlungszielTage: undefined }, { ...kunde, kundennummer: 'K-9' }, false))
            .toMatchObject({ kundennummer: 'K-9', kundenName: 'Max Mustermann', zahlungsziel: 21 });
    });

    it('aus dem Kunden: gespeicherte Adresse erscheint beim Öffnen, obwohl sie als Nutzeränderung gilt', () => {
        const gespeichert = 'Max Mustermann\nAndere Str. 5\n12345 Musterstadt';
        const mitOverride = { ...dokument, kundeId: 7, rechnungsadresseOverride: gespeichert };
        expect(dokumentKontextAusKunde({}, mitOverride, kunde, true).rechnungsadresse).toBe(gespeichert);
        expect(dokumentKontextAusKunde({ rechnungsadresse: '' }, mitOverride, kunde, true).rechnungsadresse).toBe(gespeichert);
        expect(dokumentKontextAusKunde({ rechnungsadresse: 'Getippt' }, mitOverride, kunde, true).rechnungsadresse).toBe('Getippt');
        expect(dokumentKontextAusKunde({}, { ...dokument, kundeId: 7 }, kunde, true).rechnungsadresse)
            .toBe('Max Mustermann\nMusterweg 1\n12345 Musterstadt');
    });
});
