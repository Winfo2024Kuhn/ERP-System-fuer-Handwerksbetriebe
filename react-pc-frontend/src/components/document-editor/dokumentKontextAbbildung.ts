import type { AusgangsGeschaeftsDokument } from '../../types';
import { DEFAULT_ZAHLUNGSZIEL_TAGE, buildAdresse, buildAdresseFromAnfrage } from './helpers';
import type { KontextDaten } from './types';

/**
 * Reine Abbildungen „Antwort vom Server → Kontextdaten des Dokumenteditors“.
 * Geladen wird in `useDokumentKontext`; hier steht nur, welches Feld woher kommt.
 */

/** Felder aus `GET /api/kunden/{id}` bzw. `projekt.kundeDto`, die der Editor braucht. */
export interface KundeKontextAntwort {
    name?: string;
    vorname?: string;
    nachname?: string;
    kundennummer?: string;
    strasse?: string;
    plz?: string;
    ort?: string;
    anrede?: string;
    ansprechpartner?: string;
    /** Alter Feldname im Backend, wird weiter gelesen. */
    ansprechspartner?: string;
    zahlungsziel?: number;
    kundenEmails?: string[];
}

/** Felder aus `GET /api/projekte/{id}`. */
export interface ProjektKontextAntwort {
    auftragsnummer?: string;
    bauvorhaben?: string;
    kundennummer?: string;
    kunde?: string;
    kundenId?: number;
    kundenEmails?: string[];
    kundeDto?: KundeKontextAntwort | null;
}

/** Felder aus `GET /api/anfragen/{id}`. */
export interface AnfrageKontextAntwort {
    kundennummer?: string;
    kundenName?: string;
    kundenId?: number;
    kundenStrasse?: string;
    kundenPlz?: string;
    kundenOrt?: string;
    kundenAnrede?: string;
    anrede?: string;
    kundenAnsprechpartner?: string;
    kundenAnsprechspartner?: string;
    kundenEmails?: string[];
    zahlungsziel?: number;
    anfragesnummer?: string;
    bauvorhaben?: string;
}

export type DokumentFuerKontext = Pick<AusgangsGeschaeftsDokument,
    'typ' | 'gebucht' | 'kundennummer' | 'kundenName' | 'rechnungsadresse' | 'rechnungsadresseOverride'
    | 'projektnummer' | 'projektBauvorhaben' | 'vorgaengerNummer' | 'zahlungszielTage' | 'kundeId'>;

/** Wie ein neues Dokument mit schon vorhandenen Werten umgeht. */
export interface NeuerKontextOptionen {
    /** In einem anderen Dokument des Vorgangs bearbeitete Rechnungsadresse. */
    geerbteAdresse: string | null;
    /** Nutzer hat die Adresse schon bearbeitet: nicht überschreiben. */
    adresseBehalten: boolean;
    /** Gespeichertes oder schon eingetipptes Zahlungsziel: nicht überschreiben. */
    zahlungszielBehalten: boolean;
}

export function eindeutigeEmails(...listen: Array<string[] | null | undefined>): string[] {
    const emails: string[] = [];
    listen.forEach(liste => liste?.forEach(e => { if (e && !emails.includes(e)) emails.push(e); }));
    return emails;
}

function kundeAusProjekt(projekt: ProjektKontextAntwort): KontextDaten {
    return {
        projektnummer: projekt.auftragsnummer,
        projektBauvorhaben: projekt.bauvorhaben,
        kundennummer: projekt.kundennummer,
        kundenName: projekt.kunde,
        kundeId: projekt.kundenId,
        anrede: projekt.kundeDto?.anrede,
        ansprechpartner: projekt.kundeDto?.ansprechspartner || projekt.kundeDto?.ansprechpartner,
    };
}

function kundeAusAnfrage(anfrage: AnfrageKontextAntwort): KontextDaten {
    return {
        kundennummer: anfrage.kundennummer,
        kundenName: anfrage.kundenName,
        kundeId: anfrage.kundenId,
        anrede: anfrage.kundenAnrede || anfrage.anrede,
        ansprechpartner: anfrage.kundenAnsprechpartner || anfrage.kundenAnsprechspartner,
    };
}

const ANFRAGE_ALS_BEZUG = (anfrage: AnfrageKontextAntwort): KontextDaten =>
    ({ bezugsdokument: anfrage.anfragesnummer, bezugsdokumentTyp: 'Angebot' });

/** Neues Dokument aus einem Projekt: ergänzt den bisherigen Kontext. */
export function neuerKontextAusProjekt(vorher: KontextDaten, projekt: ProjektKontextAntwort, optionen: NeuerKontextOptionen): KontextDaten {
    return {
        ...vorher,
        ...kundeAusProjekt(projekt),
        rechnungsadresse: optionen.adresseBehalten
            ? vorher.rechnungsadresse
            : (optionen.geerbteAdresse || buildAdresse(projekt.kundeDto)),
        kundenEmails: eindeutigeEmails(projekt.kundenEmails, projekt.kundeDto?.kundenEmails),
        ...(optionen.zahlungszielBehalten ? {} : { zahlungsziel: projekt.kundeDto?.zahlungsziel ?? DEFAULT_ZAHLUNGSZIEL_TAGE }),
    };
}

/** Neues Dokument aus einer Anfrage; Folgedokumente (kein Angebot) beziehen sich auf sie. */
export function neuerKontextAusAnfrage(
    vorher: KontextDaten,
    anfrage: AnfrageKontextAntwort,
    optionen: NeuerKontextOptionen & { anfrageAlsBezug: boolean },
): KontextDaten {
    return {
        ...vorher,
        ...kundeAusAnfrage(anfrage),
        rechnungsadresse: optionen.adresseBehalten
            ? vorher.rechnungsadresse
            : (optionen.geerbteAdresse || buildAdresseFromAnfrage(anfrage)),
        kundenEmails: eindeutigeEmails(anfrage.kundenEmails),
        ...(optionen.zahlungszielBehalten ? {} : { zahlungsziel: anfrage.zahlungsziel ?? DEFAULT_ZAHLUNGSZIEL_TAGE }),
        ...(optionen.anfrageAlsBezug ? ANFRAGE_ALS_BEZUG(anfrage) : {}),
    };
}

/** Gebuchtes oder nicht verknüpftes Dokument: nur die im Dokument gespeicherten Werte. */
export function kontextAusDokumentSelbst(dokument: DokumentFuerKontext): KontextDaten {
    return {
        kundennummer: dokument.kundennummer,
        kundenName: dokument.kundenName,
        rechnungsadresse: dokument.rechnungsadresse,
        projektnummer: dokument.projektnummer,
        projektBauvorhaben: dokument.projektBauvorhaben,
        bezugsdokument: dokument.vorgaengerNummer,
        zahlungsziel: dokument.zahlungszielTage ?? DEFAULT_ZAHLUNGSZIEL_TAGE,
    };
}

/** Gespeichertes Dokument eines Projekts: gespeicherte Adresse und Zahlungsziel haben Vorrang. */
export function dokumentKontextAusProjekt(dokument: DokumentFuerKontext, projekt: ProjektKontextAntwort): KontextDaten {
    return {
        ...kundeAusProjekt(projekt),
        rechnungsadresse: dokument.rechnungsadresseOverride || buildAdresse(projekt.kundeDto),
        bezugsdokument: dokument.vorgaengerNummer,
        zahlungsziel: dokument.zahlungszielTage ?? projekt.kundeDto?.zahlungsziel ?? DEFAULT_ZAHLUNGSZIEL_TAGE,
    };
}

/** Gespeichertes Dokument einer Anfrage. */
export function dokumentKontextAusAnfrage(dokument: DokumentFuerKontext, anfrage: AnfrageKontextAntwort): KontextDaten {
    return {
        ...kundeAusAnfrage(anfrage),
        rechnungsadresse: dokument.rechnungsadresseOverride || buildAdresseFromAnfrage(anfrage),
        zahlungsziel: dokument.zahlungszielTage ?? anfrage.zahlungsziel ?? DEFAULT_ZAHLUNGSZIEL_TAGE,
        ...(dokument.typ !== 'ANGEBOT' ? ANFRAGE_ALS_BEZUG(anfrage) : {}),
    };
}

/** Gespeichertes Dokument, das nur an einem Kunden hängt. */
export function dokumentKontextAusKunde(
    vorher: KontextDaten,
    dokument: DokumentFuerKontext,
    kunde: KundeKontextAntwort,
    adresseBehalten: boolean,
): KontextDaten {
    return {
        kundennummer: kunde.kundennummer || dokument.kundennummer,
        kundenName: kunde.name || `${kunde.vorname || ''} ${kunde.nachname || ''}`.trim(),
        kundeId: dokument.kundeId,
        rechnungsadresse: adresseBehalten
            ? vorher.rechnungsadresse
            : (dokument.rechnungsadresseOverride || buildAdresse(kunde)),
        anrede: kunde.anrede,
        ansprechpartner: kunde.ansprechspartner || kunde.ansprechpartner,
        bezugsdokument: dokument.vorgaengerNummer,
        zahlungsziel: dokument.zahlungszielTage ?? kunde.zahlungsziel ?? DEFAULT_ZAHLUNGSZIEL_TAGE,
    };
}
