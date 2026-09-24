/** DTOs mirror the public records in dto/Einkauf; Java BigDecimal/Long values arrive as JSON numbers. */
export type Positionsart = 'ARTIKEL' | 'ZEICHNUNGSTEIL';
export type Einheit = 'STUECK' | 'METER' | 'KILOGRAMM' | 'TONNE' | 'QUADRATMETER';
export type Dokumentart = 'ZEUGNIS_2_1' | 'ZEUGNIS_2_2' | 'ZEUGNIS_3_1' | 'ZEUGNIS_3_2' | 'LEISTUNGSERKLAERUNG' | 'CE_NACHWEIS';
export type Zahl = number;
export type Id = number;

export interface Mengenbasis { menge: Zahl | null; einheit: Einheit | null; stueckzahl: Zahl | null; einzelLaengeMm: Zahl | null; kgJeMeter: Zahl | null; faktorQuelle: string | null }
export interface DokumentSoll { art: Dokumentart; grundlage: string | null; grundlageVersion: string | null; fachlichBestaetigt: boolean }
export interface PositionSnapshot {
  art: Positionsart; artikelId: Id | null; interneReferenz: string | null; zeichnungsnummer: string | null;
  zeichnungsrevision: string | null; bezeichnung: string | null; werkstoff: string | null; abmessung: string | null;
  basis: Mengenbasis | null; schnittForm: string | null; winkelLinks: string | null; winkelRechts: string | null;
  bearbeitung: string | null; oberflaeche: string | null; dokumente: DokumentSoll[]; anlageVersionIds: Id[];
}
export interface Herkunft { bedarfId: Id | null; version: number; menge: Zahl | null }
export interface Liefergruppe { lieferadresse: string | null; bedarfstermin: string | null; projektId: Id | null; lagerzweck: string | null }
export interface KontaktSnapshot { lieferantId: Id | null; kontaktId: Id | null; lieferantenname: string | null; email: string | null; name: string | null; anrede: string | null; eigeneKundennummer: string | null }
export interface Mengenstand { bedarf: Zahl | null; lagergedeckt: Zahl | null; angefragt: Zahl | null; reserviert: Zahl | null; bestellt: Zahl | null; geliefert: Zahl | null; storniert: Zahl | null; ungedeckt: Zahl | null; disponierbar: Zahl | null }
export interface BedarfCreate { position: PositionSnapshot; liefergruppe: Liefergruppe; artikelInProjektId: Id | null }
export interface BedarfUpdate { version: number; position: PositionSnapshot; liefergruppe: Liefergruppe }
export interface BedarfResponse { id: Id; version: number; position: PositionSnapshot; liefergruppe: Liefergruppe; mengen: Mengenstand; nachpflegeErforderlich: boolean; historischerHinweis: string | null }
export interface Page<T> { content: T[]; pageable: { pageNumber: number; pageSize: number; sort: { sorted: boolean; unsorted: boolean; empty: boolean }; offset: number; paged: boolean; unpaged: boolean }; totalPages: number; totalElements: number; last: boolean; size: number; number: number; sort: { sorted: boolean; unsorted: boolean; empty: boolean }; numberOfElements: number; first: boolean; empty: boolean }

export interface AnfrageCreate { positionen: Herkunft[]; empfaenger: KontaktSnapshot[]; antwortfrist: string | null; liefertermin: string | null; zustaendigId: Id | null; idempotenzKey: string }
export interface AnfrageKopf { id: Id; version: number; paNummer: string; zustaendigId: Id | null; aktuelleRevisionId: Id | null; revisionsNummer: number; status: string; antwortfrist: string | null; liefertermin: string | null; projektIds: Id[]; antworten: number; lieferantenAnzahl: number }
export interface AnfragePositionszeile { id: Id; snapshot: PositionSnapshot; herkuenfte: Herkunft[] }
export interface Lieferantenbeteiligung { id: Id; lieferantId: Id; lieferantenname: string; status: string; version: number; kontakt: KontaktSnapshot | null }
export interface AnfrageDetail { kopf: AnfrageKopf; positionen: AnfragePositionszeile[]; lieferanten: Lieferantenbeteiligung[]; angezeigteRevisionId: Id; historisch: boolean }
export interface AnfrageRevisionRequest { version: number; inhalt: AnfrageCreate }
export interface LieferantenstatusRequest { version: number; status: string }

export interface BedarfDirektpreis { bedarfId: Id; preis: Zahl; einheit: Einheit; basisMenge: Zahl; preisHistorieId: Id | null; bestaetigtAm: string | null; gueltigBis: string | null; bestaetigungsbeleg: string | null }
export interface BestellungDirekt { lieferantId: Id; empfaenger: KontaktSnapshot; paket: Herkunft[]; preise: BedarfDirektpreis[]; liefertermin: string | null; bestaetigungsfrist: string | null; bedingungen: string | null; idempotenzKey: string }
export interface BestellungAusAngebot { angebotVersionId: Id; paket: Herkunft[]; entscheidungsgrund: string | null; idempotenzKey: string }
export interface BestellPosition { id: Id; snapshot: PositionSnapshot; menge: Zahl; nettoEinzelpreis: Zahl | null; herkuenfte: Array<{ bedarfId: Id; version: number; menge: Zahl }> }
export interface BestellRevision { id: Id; nummer: number; version: number; snapshot: Record<string, unknown>; sha256: string; versandId: Id | null; verworfen: boolean; positionen: BestellPosition[] }
export type BestellungStatus = 'ENTWURF' | 'BESTELLT' | 'TEILGELIEFERT' | 'GELIEFERT' | 'STORNIERT';
export type LieferantenBestellstatus = 'AUSSTEHEND' | 'BESTAETIGT' | 'ABWEICHUNG';
export interface BestellungDetail { id: Id; version: number; nummer: string; lieferantId: Id; angebotsversionId: Id | null; anfrageRevisionId: Id | null; empfaenger: KontaktSnapshot; status: BestellungStatus; lieferantenStatus: LieferantenBestellstatus; revisionen: BestellRevision[] }
export interface BestellungUebersicht { id: Id; nummer: string; lieferantId: Id; status: BestellungStatus; lieferantenStatus: LieferantenBestellstatus; angelegtAm: string }
export interface BestellungAenderung { version: number; inhalt: BestellungDirekt; grund: string }

export interface AngebotsKosten { schluessel: string; art: string; betrag: Zahl; basis: string | null; basisMenge: Zahl | null; prozentBasisSchluessel: string | null; enthalten: boolean; variabel: boolean; quelle: string | null }
export interface ZeugnisZusage { art: Dokumentart; status: string; aufpreis: Zahl | null }
export interface AngebotsPosition { anfragePositionId: Id | null; originalNummer: string | null; originalText: string | null; angeboten: Mengenbasis | null; mindestmenge: Zahl | null; verpackungseinheit: Zahl | null; liefertermin: string | null; abweichungen: string[]; zeugnisse: ZeugnisZusage[]; kosten: AngebotsKosten[] }
export interface AngebotErfassung { anfrageRevisionId: Id | null; angebotsnummer: string | null; datum: string | null; gueltigBis: string | null; waehrung: string | null; positionen: AngebotsPosition[]; kosten: AngebotsKosten[]; zahlungsbedingungen: string | null; skontoProzent: Zahl | null; skontoTage: number | null; emailId: Id | null; originalDateiId: Id | null }
export interface AngebotsAbweichungsfreigabe { begruendung: string | null }
export interface AngebotVersion { id: Id; angebotId: Id; nummer: number; version: number; anfrageRevisionId: Id | null; status: string; angebotsnummer: string | null; datum: string | null; gueltigBis: string | null; waehrung: string | null; positionen: AngebotsPosition[]; kosten: AngebotsKosten[]; zahlungsbedingungen: string | null; skontoProzent: Zahl | null; skontoTage: number | null; emailId: Id | null; originalDateiId: Id | null; bestaetigtVon: Id | null; abweichungBestaetigtVon: Id | null; abweichungBestaetigtAm: string | null; abweichungBestaetigung: string | null }
export interface Angebot { id: Id; beteiligungId: Id; status: string; versionen: AngebotVersion[] }

export interface Vorschau { version: number; vorschauHash: string; subject: string; htmlBody: string; empfaenger: string; pdfDateiId: Id | null; anlageVersionIds: Id[] }
export interface VersandFreigabe { version: number; vorschauHash: string; idempotenzKey: string }
export interface VersandErgebnis { beteiligungId: Id | null; status: string; fehlerCode: string | null; messageId: string | null }
export interface VersandDto { id: Id; version: number; typ: string; vorgangId: Id; revisionId: Id | null; status: string; fehlerCode: string | null; erstelltAm: string; angenommenAm: string | null; archiviert: boolean; messageId: string | null }
export interface Nachricht { emailId: Id; messageId: string | null; subject: string | null; fromAddress: string | null; sentAt: string | null; typ: string | null; vorgangId: Id | null; beteiligungId: Id | null; revisionId: Id | null; status: string | null; quelle: string | null }
export interface ZuordnungRequest { typ: string; vorgangId: Id; beteiligungId: Id | null; revisionId: Id | null; begruendung: string | null }
export interface Zuordnungsergebnis { emailId: Id; typ: string; vorgangId: Id; beteiligungId: Id | null; revisionId: Id | null; status: string; quelle: string; bestaetigt: boolean }

export interface Lieferanteil { bestellPositionId: Id; menge: Zahl; charge: string | null; schmelznummer: string | null; projektAnteile: Array<{ bedarfId: Id; version: number; menge: Zahl }> }
export interface LieferungAnnahme { version: number; lieferscheinId: Id | null; eingang: string; positionen: Lieferanteil[]; idempotenzKey: string }
export interface Lieferung { id: Id; bestellungId: Id; revisionId: Id; lieferscheinId: Id | null; eingang: string; positionen: Lieferanteil[] }
export interface BestaetigteBestellPosition { bestellPositionId: Id; menge: Zahl; nettoPreis: Zahl | null; abweichung: string | null }
export interface LieferbestaetigungRequest { dokumentId: Id | null; datum: string | null; liefertermin: string | null; positionen: BestaetigteBestellPosition[] }
export interface Lieferbestaetigung { id: Id; dokumentId: Id | null; datum: string | null; liefertermin: string | null; abweichung: boolean; positionen: BestaetigteBestellPosition[] }

export type ChargeStatus = 'ANGEFORDERT' | 'ERWARTET' | 'EINGEGANGEN' | 'ZUGEORDNET' | 'GEPRUEFT' | 'KLAERUNG_NOETIG';
export interface ChargeStatusDto { chargeId: Id; status: ChargeStatus; version: number; materialFreigegeben: boolean }
export interface ZeugnisErwartung { id: Id; version: number; revisionId: Id | null; bestellPositionId: Id | null; art: Dokumentart; grundlage: string | null; grundlageVersion: string | null; frist: string | null; status: ChargeStatus; dateiIds: Id[]; lieferPositionIds: Id[]; chargeIds: Id[]; materialFreigegeben: boolean; chargeStaende: ChargeStatusDto[]; zuordnungen?: ChargeZuordnungDto[] }
export interface ChargeZuordnungDto { zuordnungId: Id; erwartungId: Id; chargeId: Id; status: ChargeStatus; version: number; materialFreigegeben: boolean }
export interface ZeugnisZuordnung { dokumentId: Id | null; erwartungIds: Id[]; lieferPositionIds: Id[]; chargeIds: Id[]; schmelznummer: string | null }
export interface ZeugnisZuordnungResponse { erwartungen: ZeugnisErwartung[]; klaerungNoetig: boolean; chargen: ChargeZuordnungDto[] }
export interface ZeugnisPruefung { version: number; ergebnis: string; begruendung: string; grundlageVersion: string | null }
export interface ZeugnisPruefungResponse { id: Id; erwartungId: Id; ergebnis: string; begruendung: string; grundlageVersion: string | null; akteurId: Id; geprueftAm: string; materialFreigegeben: boolean }
export interface ZeugnisVorlage { id: Id; artikelId: Id | null; projektId: Id | null; art: Dokumentart; grundlage: string | null; grundlageVersion: string | null; fachlichBestaetigt: boolean }

export interface AbgleichQuelle { typ: string; id: Id | null; bezeichnung: string | null; betrag: Zahl | null }
export type Quelle = AbgleichQuelle;
export interface EinkaufLagerEntnahmeRequest { anteil: Herkunft; preisJeEinheit: Zahl | null; preisQuelle: string | null; entnommenAm: string | null; idempotenzKey: string }
export interface EinkaufLagerBewertungRequest { preisJeEinheit: Zahl | null; preisQuelle: string | null }
export interface EinkaufLagerBewertung { preisJeEinheit: Zahl | null; preisQuelle: string | null; bewertetAm: string; akteurId: Id | null; mitarbeiterId: Id | null }
export interface EinkaufLagerEntnahme { id: Id; projektId: Id; bedarfId: Id; bedarfVersion: number; menge: Zahl; einheit: Einheit; preisJeEinheit: Zahl | null; preisQuelle: string | null; bewerteterBetrag: Zahl | null; bewertungOffen: boolean; offenerBedarf: Zahl | null; entnommenAm: string; akteurId: Id; mitarbeiterId: Id | null; bewertungen: EinkaufLagerBewertung[] }
export interface DateiPdfSnapshot { dateiId: Id; sha256: string; byteAnzahl: number }
export interface EinkaufAnlage { id: Id; dateiId: Id; bedarfId: Id | null; revision: string | null; dateiname: string; mimeTyp: string; byteAnzahl: number; sha256: string; freigegeben: boolean; versendet: boolean; hochgeladenAm: string }
export interface Faelligkeit { typ: string; vorgangId: Id; nummer: string | null; beteiligungId: Id | null; frist: string | null; zustaendigId: Id | null; hinweis: string | null }
export interface NachfrageRequest { typ: string; vorgangId: Id; beteiligungId: Id | null }
export interface NachfrageEntwurf { typ: string; vorgangId: Id; beteiligungId: Id | null; vorlageId: Id; vorlageVersion: number; empfaenger: string; subject: string; htmlBody: string; fehlendeNachweise: string[] }
export interface AnfrageKommunikationZuordnungRequest { typ: string; vorgangId: Id; beteiligungId: Id | null; revisionId: Id | null; begruendung: string | null }
export interface EinkaufsVersandErgebnis { beteiligungId: Id | null; status: string; fehlerCode: string | null; messageId: string | null }
export interface EinkaufsMailAbruf { importierteNachrichten: number }
export interface EinkaufVorlagenGerendert { templateId: Id; version: number; subject: string; htmlBody: string; hash: string }
export interface EinkaufVorlagenKontext { typ: string; skalare: Record<string, string>; positionen: PositionSnapshot[]; rueckmeldecode: string | null }
export interface EinkaufVorlagenPlatzhalter { token: string; label: string; pflicht: boolean; imBetreffErlaubt: boolean }
export interface EinkaufVergleichRechenschritt { key: string; formel: string; basis: Zahl | null; ergebnis: Zahl | null; quellenbezug: string | null }
export interface EinkaufAngebotSumme { angebotVersionId: Id; nettoGesamt: Zahl | null; vollstaendig: boolean; technischGeeignet: boolean; gueltig: boolean; hindernisse: string[]; rechnung: EinkaufVergleichRechenschritt[] }
export interface EinkaufVergleich { anfrageId: Id; stichtag: string; angebote: EinkaufAngebotSumme[]; bestesAngebotVersionId: Id | null }
export interface EinkaufAnalyseJob { id: Id; emailId: Id | null; angebotId: Id | null; status: string; erstelltAm: string; beendetAm: string | null; hinweis: string | null; weitereJobIds: Id[] }
export interface EinkaufAnalyseQuelle { emailId: Id | null; dateiId: Id | null; seite: number | null; zitat: string | null; textStart: number | null; textEnd: number | null }
export interface EinkaufAnalyseFeldvorschlag { feldpfad: string; wert: unknown; quelle: EinkaufAnalyseQuelle | null; confidence: Zahl | null; hinweis: string | null }
export interface EinkaufAnalyseUebernahme { erwarteteAngebotVersion: number; akzeptierteFeldpfade: string[]; korrekturen: Record<string, unknown> }
export interface EinkaufAnalyseEmpfehlung { angebotVersionId: Id; nettoGesamt: Zahl | null; quellen: string[]; text: string | null }
export type PreisScope = 'STANDARD' | 'PROJEKT' | 'MENGENSTAFFEL';
export interface EinkaufPreisUebernahme { scope: string; projektId: Id | null; abMenge: Zahl | null; bisMenge: Zahl | null; begruendung: string | null; idempotenzKey: string }
export interface EinkaufPreisvorschlag { artikelId: Id; lieferantId: Id; preis: Zahl; waehrung: string; einheit: Einheit | string; datum: string; gueltigBis: string | null; scope: PreisScope; hinweis: string | null }
export interface HiCadBildVorschlag { dateiId: Id; dateiname: string; mimeTyp: string; byteAnzahl: number; url: string }
export interface HiCadZeile { zeilennummer: number; rohtext: string; vorschlag: PositionSnapshot | null; artikelKandidaten: Id[]; bereitsUebernommen: boolean; hinweise: string[]; bilder: HiCadBildVorschlag[] }
export interface HiCadVorschau { id: Id; dateiHash: string; dateiSchonImportiert: boolean; zeilen: HiCadZeile[] }
export interface HiCadZeilenFortschritt { zeilennummer: number; gesamtmenge: Zahl; uebernommeneMenge: Zahl; verbleibendeMenge: Zahl; vollstaendigUebernommen: boolean }
export interface HiCadImportFortschritt { id: Id; version: number; duplikat: boolean; zeilen: HiCadZeilenFortschritt[] }
export interface HiCadZeilenAuswahl { zeilennummer: number; menge: Zahl; korrigiert: PositionSnapshot | null; bestaetigteBildDateiIds: Id[] }
export interface HiCadUebernahme { version: number; zeilen: HiCadZeilenAuswahl[]; duplikatBewusst: boolean; idempotenzKey: string }
export type EinkaufBerechtigung = 'LESEN' | 'BEARBEITEN' | 'ANFRAGE_SENDEN' | 'BESTELLUNG_FREIGEBEN' | 'ZEUGNIS_PRUEFEN';
export interface EinkaufBerechtigungen { rechte: EinkaufBerechtigung[] }
export interface EinkaufBestellFreigabe { version: number; vorschauHash: string; idempotenzKey: string }
export interface EinkaufBestellFreigabeVersandErgebnis { bestellungId: Id | null; status: string; fehlerCode: string | null; messageId: string | null }
export interface EinkaufExternerNachweis { version: number; versendetAm: string; dateiId: Id | null; begruendung: string; idempotenzKey: string }
export interface EinkaufBestellStorno { version: number; anteile: Array<{ bedarfId: Id | null; version: number; menge: Zahl | null }>; belegDateiId: Id | null; grund: string; idempotenzKey: string }
export interface EinkaufBestellStornoErgebnis { bestellungId: Id; status: string; hinweis: string | null }
export interface EinkaufBestellVorschauErgebnis { vorschau: Vorschau; revisionsVersion: number; sha256: string }
export interface MailkontoResponse { id: string; version: number; aktiv: boolean; fromAddress: string | null; fromName: string | null; smtpHost: string | null; smtpPort: number; smtpUsername: string | null; smtpTls: 'TLS' | 'STARTTLS'; imapHost: string | null; imapPort: number; imapUsername: string | null; imapTls: 'TLS' | 'STARTTLS'; inbox: string | null; sent: string | null; smtpPasswordSet: boolean; imapPasswordSet: boolean; letzterAbruf: string | null; letzterFehler: string | null }
export interface MailkontoServerZugang { host: string; port: number; username: string; password: string; tls: 'TLS' | 'STARTTLS' }
export interface MailkontoUpdate { version: number | null; aktiv: boolean; fromAddress: string; fromName: string; smtpHost: string; smtpPort: number; smtpUsername: string; smtpTls: 'TLS' | 'STARTTLS'; imapHost: string; imapPort: number; imapUsername: string; imapTls: 'TLS' | 'STARTTLS'; inbox: string; sent: string; smtpPassword: string; imapPassword: string }
export interface EinkaufKontakt { id: Id | null; version: number; name: string | null; anrede: string | null; email: string; standardAnfrage: boolean; standardBestellung: boolean; aktiv: boolean }
export interface EinkaufLiefergruppenBeleg { typ: string; nummer: string; revision: number; empfaenger: KontaktSnapshot; positionen: PdfPosition[]; kopfkosten: PdfKosten[]; liefergruppen: Liefergruppe[]; antwortfrist: string | null; liefertermin: string | null; bedingungen: string | null; nettoSumme: Zahl | null; entwurf: boolean }
export interface PdfPosition { positionsnummer: string; technik: PositionSnapshot; herkuenfte: PdfHerkunft[]; kosten: PdfKosten[]; nettoSumme: Zahl | null }
export interface PdfHerkunft { bedarfId: Id | null; projektNummer: string | null; menge: Zahl | null; einheit: Einheit }
export interface PdfKosten { bezeichnung: string; betrag: Zahl | null; basis: string | null; basisMenge: Zahl | null; enthalten: boolean; rechenweg: string | null }
export interface MailTransportNachricht { messageId: string | null; to: string; subject: string; html: string; inReplyTo: string | null; references: string[]; anlagen: Array<{ data: string | null; filename: string; mimeType: string; file: string | null }> }
export type MailTransportStatus = 'ANGENOMMEN' | 'SICHER_FEHLGESCHLAGEN' | 'UNKLAR';
export interface MailTransportVersandergebnis { status: MailTransportStatus; messageId: string | null; fehlerCode: string | null; mime: string | null }
export interface MailTransportArchivErgebnis { erfolgreich: boolean; fehlerCode: string | null }
export interface MailTransportTestverbindung { smtpErfolgreich: boolean; imapErfolgreich: boolean; fehlerCode: string | null }
export interface MailTransportTestmail { empfaenger: string; empfaengerBestaetigt: boolean }
export interface MailTransportTestmailErgebnis { status: MailTransportStatus; messageId: string | null; fehlerCode: string | null }
export interface EinkaufVersandAngenommen { ereignisSchluessel: string; versandId: Id; typ: string; vorgangId: Id; revisionId: Id | null; beteiligungId: Id | null; zeit: string }
export interface EinkaufVersandKlaerung { version: number; entscheidung: 'BEREITS_ANGENOMMEN' | 'NACHWEISLICH_NICHT_GESENDET'; beleg: string }
export interface ZeugnisVorlageCreate { artikelId: Id | null; projektId: Id | null; art: Dokumentart; grundlage: string }
export type ZeugnisPruefungDto = ZeugnisPruefungResponse;
export interface EinkaufDateiPaketPruefung { versionIds: Id[]; pdfBytes: number }
export interface EinkaufVersandSnapshot { typ: string; vorgangId: Id; revisionId: Id | null; beteiligungId: Id | null; konto: { kontoId: string }; nachricht: MailTransportNachricht; freigabeHash: string }
export interface Abweichung { positionId: Id | null; feld: string; vereinbart: Zahl | null; abgerechnet: Zahl | null; differenz: Zahl | null; rechenweg: string | null; quellen: AbgleichQuelle[] }
export interface PositionAbgleich { positionId: Id; bezeichnung: string; einheit: Einheit; vereinbart: Zahl | null; bestaetigt: Zahl | null; geliefert: Zahl | null; kumuliertAbgerechnet: Zahl | null; offen: Zahl | null; abweichungen: Abweichung[]; pruefen: boolean; quellen: AbgleichQuelle[] }
export interface Rechnungsabgleich { bestellungId: Id; positionen: PositionAbgleich[]; unbelegteDokumentIds: Id[] }
export interface RechnungsabgleichMengenstand { offen: Zahl | null; differenz: Zahl | null }
export interface RechnungsKosten { schluessel: string; art: string; betrag: Zahl; basis: string | null; basisMenge: Zahl | null; enthalten: boolean; quelle: string | null; prozentBasisSchluessel: string | null }
export interface BelegPosition { originalPositionsnummer: string | null; bestellPositionId: Id | null; menge: Zahl; einheit: Einheit; nettoEinzelpreis: Zahl | null; preisBasisMenge: Zahl | null; nurPreisKorrektur: boolean; kosten: RechnungsKosten[]; quellen: AbgleichQuelle[] }
export interface BelegZuordnung { bestellungId: Id; version: number; art: string; bezugsDokumentId: Id | null; positionen: BelegPosition[]; idempotenzKey: string }

export interface ApiFieldError { field: string; message: string }
export interface ApiErrorBody { message: string; fieldErrors: ApiFieldError[] }
