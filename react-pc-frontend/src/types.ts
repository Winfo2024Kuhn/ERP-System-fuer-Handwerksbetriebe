export interface TextTemplate {
  id: string;
  name: string;
  content: string;
  docTypes?: string[];
}

export interface Kunde {
  id: string | number;
  name: string;
  ansprechpartner?: string;
  kundennummer?: string;
  anrede?: string;
  ansprechspartner?: string;
  strasse?: string;
  plz?: string;
  ort?: string;
  zahlungsziel?: number;
  kundenEmails?: string[];
}

// Shared Intefaces
export interface Kommunikation {
  id: number;
  referenzId: number;
  referenzTyp: 'PROJEKT' | 'ANFRAGE' | 'STAMMDATEN' | 'BESTELLUNG'; // Added BESTELLUNG
  referenzName: string;
  subject: string;
  absender: string;
  empfaenger: string;
  zeitpunkt: string;
  direction: 'IN' | 'OUT';
  snippet: string;
  body?: string;
  attachments?: EmailAttachment[];
  parentEmailId?: number;
  replyCount?: number;
}

export interface EmailAttachment {
  id: number;
  filename: string;
  url: string;
}

export interface KundeStatistik {
  projektAnzahl: number;
  anfrageAnzahl: number;
  emailAdresseAnzahl: number;
  letzteAktivitaet?: string;
  gesamtUmsatz?: number;
  gesamtGewinn?: number;
}

export interface KundeDetail {
  id: number;
  kundennummer: string;
  name: string;
  anrede?: string;
  ansprechspartner?: string;
  strasse?: string;
  plz?: string;
  ort?: string;
  telefon?: string;
  mobiltelefon?: string;
  zahlungsziel?: number;
  kundenEmails?: string[];
  hatProjekte?: boolean;
  statistik?: KundeStatistik;
  kommunikation?: Kommunikation[];
}

export interface LieferantStatistik {
  bestellungAnzahl?: number;
  artikelAnzahl?: number;
  gesamtKosten?: number;
  letzteAktivitaet?: string;
  lieferzeit?: number;
}

// ==================== Lieferant Dokumente ====================
export type LieferantDokumentTyp = 'ANGEBOT' | 'AUFTRAGSBESTAETIGUNG' | 'LIEFERSCHEIN' | 'RECHNUNG' | 'EINGANGSRECHNUNG' | 'GUTSCHRIFT' | 'SONSTIG';

export interface LieferantGeschaeftsdaten {
  dokumentNummer?: string;
  dokumentDatum?: string;
  betragNetto?: number;
  betragBrutto?: number;
  mwstSatz?: number;
  referenzNummer?: string;
  bestellnummer?: string;
  liefertermin?: string;
  aiConfidence?: number;
  // Zahlungsstatus
  zahlungsziel?: string;
  bezahlt?: boolean;
  bezahltAm?: string;
  bereitsGezahlt?: boolean;
  zahlungsart?: string;
  // Skonto-Konditionen
  skontoTage?: number;
  skontoProzent?: number;
  nettoTage?: number;
  tatsaechlichGezahlt?: number;
  mitSkonto?: boolean;
  // Neu: Flag für manuelle Prüfung bei fehlgeschlagener KI-Extraktion
  manuellePruefungErforderlich?: boolean;
  datenquelle?: string; // ZUGFERD, XML, AI, AI_FAILED, AI_ERROR
}


export interface LieferantProjektAnteil {
  id: number;
  projektId: number;
  projektName: string;
  auftragsnummer?: string;
  kostenstelleId?: number;
  kostenstelleName?: string;
  prozent: number;
  berechneterBetrag?: number;
  beschreibung?: string;
  zugeordnetVonName?: string;
  zugeordnetAm?: string;
}

export interface LieferantDokumentRef {
  id: number;
  typ: LieferantDokumentTyp;
  dokumentNummer?: string;
  dokumentDatum?: string;
}

export interface LieferantDokument {
  id: number;
  typ: LieferantDokumentTyp;
  originalDateiname: string;
  uploadDatum: string;
  url?: string;
  geschaeftsdaten?: LieferantGeschaeftsdaten;
  projektAnteile: LieferantProjektAnteil[];
  verknuepfteDokumente: LieferantDokumentRef[];
  uploadedByName?: string;
}

// Dokumentenkette (gruppierte verknüpfte Dokumente)
export interface LieferantDokumentenKette {
  id: string;
  dokumente: LieferantDokument[];
  hauptDokumentNummer?: string;
  gesamtBetrag?: number;
}

export const LIEFERANT_DOKUMENT_TYPEN: { value: LieferantDokumentTyp; label: string; color: string }[] = [
  { value: 'ANGEBOT', label: 'Angebot', color: 'blue' },
  { value: 'AUFTRAGSBESTAETIGUNG', label: 'Auftragsbestätigung', color: 'purple' },
  { value: 'LIEFERSCHEIN', label: 'Lieferschein', color: 'amber' },
  { value: 'RECHNUNG', label: 'Rechnung', color: 'rose' },
];

export interface LieferantNotiz {
  id: number;
  text: string;
  erstelltAm: string;
}

export interface LieferantReklamationBild {
  id: number;
  url: string;
  originalDateiname: string;
}

export interface LieferantReklamation {
  id: number;
  lieferantId: number;
  lieferantName: string;
  lieferscheinId?: number;
  lieferscheinNummer?: string;
  lieferscheinDateiname?: string;
  erstellerName: string;
  erstelltAm: string;
  beschreibung: string;
  status: string;
  bilder: LieferantReklamationBild[];
}

export interface LieferantDetail extends Lieferant {
  statistik?: LieferantStatistik;
  kommunikation?: Kommunikation[];
  dokumente?: LieferantDokument[];
  emails?: ProjektEmail[]; // Unified email structure for EmailsTab
  notizen?: LieferantNotiz[];
}



export interface LeistungsFolder {
  id: string;
  name: string;
  parentId: string | null;
  leaf?: boolean;
}

export interface LeistungsService {
  id: string;
  name: string;
  description: string;
  price: number;
  unit: string;
  folderId: string;
}

export interface ProduktkategorieDto {
  id: number | string;
  bezeichnung: string;
  beschreibung?: string;
  pfad?: string;
  leaf?: boolean;
  parentId?: number | null;
  verrechnungseinheit?: Verrechnungseinheit;
}

export interface Lieferant {
  id: number | string;
  lieferantenTyp?: string;
  lieferantenname?: string;
  eigeneKundennummer?: string;
  strasse?: string;
  plz?: string;
  ort?: string;
  telefon?: string;
  mobiltelefon?: string;
  vertreter?: string;
  rabatt?: number;
  lieferzeit?: number;
  istAktiv?: boolean;
  bearbeiter?: string;
  kundenEmails?: string[];
  erfassungsDatum?: string;
}

export interface ArtikelLieferantPreis {
  lieferantId: number;
  lieferantName: string;
  preis?: number;
  externeArtikelnummer?: string;
  preisDatum?: string;
}

export type PreisQuelle = 'RECHNUNG' | 'ANGEBOT' | 'KATALOG' | 'MANUELL' | 'VORSCHLAG';

export interface ArtikelPreisHistorieEintrag {
  id: number;
  preis: number;
  menge?: number;
  einheit: string | { name: string; anzeigename?: string };
  quelle: PreisQuelle;
  lieferantId?: number;
  lieferantName?: string;
  externeNummer?: string;
  belegReferenz?: string;
  erfasstAm: string;
  bemerkung?: string;
}

export interface Artikel {
  id: number;
  externeArtikelnummer?: string;
  produktlinie?: string;
  produktname: string;
  produkttext?: string;
  verpackungseinheit?: number;
  preiseinheit?: string;
  verrechnungseinheit?: string | { name: string; anzeigename?: string };
  preis?: number;
  preisDatum?: string;
  lieferantId?: number;
  lieferantenname?: string;
  lieferantenpreise?: ArtikelLieferantPreis[];
  anzahlLieferanten?: number;
  durchschnittspreisNetto?: number;
  durchschnittspreisMenge?: number;
  durchschnittspreisAktualisiertAm?: string;
  kategorieId?: number;
  kategoriePfad?: string;
  werkstoffName?: string;
  kgProMeter?: number;
}

export interface Abteilung {
  id: number;
  name: string;
}

export interface Mitarbeiter {
  id: number;
  vorname: string;
  nachname: string;
  strasse?: string;
  plz?: string;
  ort?: string;
  email?: string;
  telefon?: string;
  festnetz?: string;
  qualifikation?: string | null;
  stundenlohn?: number;
  jahresUrlaub?: number;
  geburtstag?: string;
  eintrittsdatum?: string;
  aktiv?: boolean;
  abteilungIds?: number[];
  abteilungNames?: string | null;
  loginToken?: string;
  en1090RolleIds?: number[];
  en1090RolleNames?: string | null;
}

export interface En1090Rolle {
  id: number;
  kurztext: string;
  beschreibung: string | null;
  sortierung: number;
  aktiv: boolean;
}

export interface Arbeitsgang {
  id: number;
  beschreibung: string;
  abteilungId: number | null;
  abteilungName: string | null;
  stundensatz: number | null;
  stundensatzJahr: number | null;
}

export type VerrechnungseinheitName = 'LAUFENDE_METER' | 'QUADRATMETER' | 'KILOGRAMM' | 'STUECK';

export interface Verrechnungseinheit {
  name: VerrechnungseinheitName;
  anzeigename: string;
}

export const VERRECHNUNGSEINHEITEN: Verrechnungseinheit[] = [
  { name: 'LAUFENDE_METER', anzeigename: 'Laufende Meter' },
  { name: 'QUADRATMETER', anzeigename: 'Quadratmeter' },
  { name: 'KILOGRAMM', anzeigename: 'Kilogramm' },
  { name: 'STUECK', anzeigename: 'Stück' },
];

export interface Produktkategorie {
  id: number;
  bezeichnung: string;
  bildUrl?: string;
  beschreibung?: string;
  verrechnungseinheit?: Verrechnungseinheit;
  pfad?: string;
  isLeaf: boolean;
  projektAnzahl?: number;
}

// ==================== Analyse DTOs ====================
export interface ProjektArbeitsgangAnalyse {
  arbeitsgangId: number;
  arbeitsgangBeschreibung: string;
  stundenProEinheit: number;
}

export interface ProjektAnalyse {
  id: number;
  auftragsnummer: string;
  kunde: string;
  bildUrl?: string;
  masseinheit: number;
  zeitGesamt: number;
  arbeitsgaenge: ProjektArbeitsgangAnalyse[];
  ausreisser?: boolean;
}

export interface ArbeitsgangAnalyse {
  arbeitsgangId: number;
  arbeitsgangBeschreibung: string;
  durchschnittStundenProEinheit: number;
}

export interface ProduktkategorieAnalyse {
  projektAnzahl: number;
  durchschnittlicheZeit: number;
  fixzeit: number;
  steigung: number;
  verrechnungseinheit: string;
  projekte: ProjektAnalyse[];
  arbeitsgangAnalysen: ArbeitsgangAnalyse[];
  rQuadrat: number;
  residualStdAbweichung: number;
  datenpunkte: number;
}

export interface ZeitprognoseDto {
  prognostizierteStunden: number;
  fixzeit: number;
  steigung: number;
  rQuadrat: number;
  residualStdAbweichung: number;
  datenpunkte: number;
  kategorieId: number;
  kategorieName: string;
  verrechnungseinheit: string;
}

// ==================== Projekt DTOs ====================
export interface Projekt {
  id: number;
  bauvorhaben: string;
  kunde?: string;
  kundenId?: number;
  kundennummer?: string;
  auftragsnummer?: string;
  anlegedatum?: string;
  abschlussdatum?: string;
  bruttoPreis?: number;
  bezahlt: boolean;
  abgeschlossen?: boolean;
  strasse?: string;
  plz?: string;
  ort?: string;
  kurzbeschreibung?: string;
  bildUrl?: string;
  projektArt?: string;
  excKlasse?: string | null;
}

export interface ProjektZeit {
  id: number;
  arbeitsgangBeschreibung: string;
  anzahlInStunden: number;
  stundensatz: number;
  mitarbeiterVorname?: string;
  mitarbeiterNachname?: string;
  produktkategorie?: ProduktkategorieDto;
}

export interface Materialkosten {
  id: number;
  beschreibung: string;
  externeArtikelnummer?: string;
  monat?: number;
  betrag: number;
  rechnungsnummer?: string;
}

// Nested Produktkategorie object in ProjektProduktkategorie
export interface ProjektProduktkategorieNested {
  id: number;
  bezeichnung: string;
  bildUrl?: string;
  beschreibung?: string;
  verrechnungseinheit?: Verrechnungseinheit;
  pfad?: string;
  isLeaf?: boolean;
}

export interface ProjektProduktkategorie {
  produktkategorie: ProjektProduktkategorieNested;
  menge: number;
}

// ==================== Dokument DTOs ====================
export type DokumentGruppe =
  | 'BILDER'
  | 'GESCHAEFTSDOKUMENTE'
  | 'PLANUNGSDOKUMENTE'
  | 'KALKULATIONSDOKUMENTE'
  | 'DOKUMENTATION_1090'
  | 'EINGANGSRECHNUNGEN'
  | 'DIVERSE_DOKUMENTE';

export const DOKUMENT_GRUPPEN: { value: DokumentGruppe; label: string }[] = [
  { value: 'BILDER', label: 'Bilder' },
  { value: 'GESCHAEFTSDOKUMENTE', label: 'Geschäftsdokumente' },
  { value: 'PLANUNGSDOKUMENTE', label: 'Planungsdokumente' },
  { value: 'KALKULATIONSDOKUMENTE', label: 'Kalkulationsdokumente' },
  { value: 'DOKUMENTATION_1090', label: 'Dokumentation 1090' },
  { value: 'EINGANGSRECHNUNGEN', label: 'Eingangsrechnungen' },
  { value: 'DIVERSE_DOKUMENTE', label: 'Diverse Dokumente' },
];

export interface ProjektDokument {
  id: number;
  originalDateiname: string;
  gespeicherterDateiname?: string;  // UUID-prefixed filename for download URL
  dateityp?: string;
  url: string;
  netzwerkPfad?: string;
  dokumentGruppe: DokumentGruppe;
  uploadDatum?: string;
  emailVersandDatum?: string;
  rechnungsnummer?: string;
  rechnungsdatum?: string;
  faelligkeitsdatum?: string;
  geschaeftsdokumentart?: string;
  mahnstufe?: string;
  referenzDokumentId?: number;
  referenzDokumentNummer?: string;
  mahnung?: boolean;
  rechnungsbetrag?: number;
  bezahlt?: boolean;
  projektId?: number;
  projektAuftragsnummer?: string;
  projektKunde?: string;
  projektKategorie?: string;
  // Lieferant für Dokumentzuordnung
  lieferant?: { id: number; lieferantenname: string };
  // Uploader-Info
  uploadedByVorname?: string;
  uploadedByNachname?: string;
}

// Geschäftsdokument (Rechnungen, Anfragen, Auftragsbestätigungen, etc.)
export interface ProjektGeschaeftsdokument {
  id: number;
  dokumentid: string;              // Dokumentnummer (Rechnungsnummer, Anfragesnummer, etc.)
  geschaeftsdokumentart: string;   // Rechnung, Anfrage, Auftragsbestätigung, Zeichnung, Mahnung
  originalDateiname: string;
  url: string;
  netzwerkPfad?: string;
  rechnungsdatum?: string;
  faelligkeitsdatum?: string;
  bruttoBetrag?: number;
  bezahlt?: boolean;
  mahnstufe?: string;
}

// Dokumenttypen für E-Mail-Templates (matches backend Dokumenttyp enum)
export type GeschaeftsdokumentTyp =
  | 'ANGEBOT'
  | 'AUFTRAGSBESTAETIGUNG'
  | 'TEILRECHNUNG'
  | 'ABSCHLAGSRECHNUNG'
  | 'SCHLUSSRECHNUNG'
  | 'ZAHLUNGSERINNERUNG'
  | 'ERSTE_MAHNUNG'
  | 'ZWEITE_MAHNUNG'
  | 'STORNORECHNUNG'
  | 'GUTSCHRIFT';

export const GESCHAEFTSDOKUMENT_TYPEN: { value: GeschaeftsdokumentTyp; label: string }[] = [
  { value: 'ANGEBOT', label: 'Angebot' },
  { value: 'AUFTRAGSBESTAETIGUNG', label: 'Auftragsbestätigung' },
  { value: 'TEILRECHNUNG', label: 'Teilrechnung' },
  { value: 'ABSCHLAGSRECHNUNG', label: 'Abschlagsrechnung' },
  { value: 'SCHLUSSRECHNUNG', label: 'Schlussrechnung' },
  { value: 'ZAHLUNGSERINNERUNG', label: 'Zahlungserinnerung' },
  { value: 'ERSTE_MAHNUNG', label: '1. Mahnung' },
  { value: 'ZWEITE_MAHNUNG', label: '2. Mahnung' },
  { value: 'STORNORECHNUNG', label: 'Stornorechnung' },
  { value: 'GUTSCHRIFT', label: 'Gutschrift' },
];

// E-Mail Anhang Interface
export interface ProjektEmailAttachment {
  id?: number;
  originalFilename: string;
  storedFilename: string;
  contentId?: string;
  inline?: boolean;
  url?: string; // Download-URL vom Backend
}

export interface ProjektEmail {
  id: number;
  subject?: string;
  from?: string;           // Backend liefert "from"
  sender?: string;         // Alias für Kompatibilität
  fromAddress?: string;    // Alias für Kompatibilität
  to?: string;             // Backend liefert "to"
  recipient?: string;      // Alias für Kompatibilität
  recipients?: string[];   // Alias für Kompatibilität
  direction: 'IN' | 'OUT';
  receivedDate?: string;
  sentAt?: string;         // Backend liefert "sentAt"
  sentDate?: string;       // Alias für Kompatibilität
  body?: string;           // Alias für Kompatibilität
  htmlBody?: string;       // Alias für Kompatibilität
  bodyHtml?: string;       // Backend liefert "bodyHtml"
  bodyPreview?: string;    // Alias für Kompatibilität
  attachments?: ProjektEmailAttachment[];
  parentId?: number;       // Thread: Parent-Email-ID (alt)
  parentEmailId?: number;  // Thread: Parent-Email-ID (Backend DTO)
  replyCount?: number;     // Thread: Anzahl Antworten
  replies?: ProjektEmail[];// Thread: Antworten
}

export type AipQuelle = 'OFFEN' | 'BESTELLT' | 'AUS_LAGER';

export interface ArtikelInProjekt {
  id: number;
  artikelId: number;
  artikelNummer?: string;
  externeArtikelnummer?: string;
  produktname?: string;
  produkttext?: string;
  beschreibung?: string;
  stueckzahl?: number;
  meter?: number;
  kilogramm?: number;
  einzelpreis?: number;
  gesamtpreis?: number;
  // Nur bei quelle=AUS_LAGER gesetzt (gleitender Durchschnittspreis).
  // Bei OFFEN/BESTELLT null — Preis kommt später über Eingangsrechnung.
  preisProStueck?: number;
  quelle?: AipQuelle;
}

export interface ProjektDetail extends Projekt {
  kundeDto?: KundeDetail;
  kundenEmails?: string[];
  materialkosten?: Materialkosten[];
  artikel?: ArtikelInProjekt[];
  produktkategorien?: ProjektProduktkategorie[];
  zeiten?: ProjektZeit[];
  emails?: ProjektEmail[];
  gesamtKilogramm?: number;
}

// ==================== Anfrage DTOs ====================
export interface Anfrage {
  id: number;
  kundenId?: number;
  kundenName?: string;
  bauvorhaben: string;
  kundennummer?: string;
  anfragesnummer?: string;
  betrag?: number;
  kundenEmails?: string[];
  emailVersandDatum?: string;
  projektId?: number;
  anlegedatum?: string;
  bildUrl?: string;
  projektStrasse?: string;
  projektPlz?: string;
  projektOrt?: string;
  kurzbeschreibung?: string;
  abgeschlossen?: boolean;
  // Erweiterte Kundendaten
  kundenStrasse?: string;
  kundenPlz?: string;
  kundenOrt?: string;
  kundenTelefon?: string;
  kundenMobiltelefon?: string;
  kundenAnsprechpartner?: string;
  kundenAnrede?: string;
}

export interface AnfrageDokument {
  id: number;
  originalDateiname: string;
  dateityp?: string;
  url: string;
  netzwerkPfad?: string;
  dokumentGruppe?: DokumentGruppe;
  uploadDatum?: string;
  emailVersandDatum?: string;
  anrede?: string;
  rechnungsnummer?: string;
  rechnungsdatum?: string;
  faelligkeitsdatum?: string;
  geschaeftsdokumentart?: string;
  rechnungsbetrag?: number;
  bezahlt?: boolean;
}

export interface AnfrageEmailAttachment {
  id?: number;
  originalFilename: string;
  storedFilename: string;
  contentId?: string;
  inline?: boolean;
}

export interface AnfrageEmail {
  id: number;
  sender?: string;
  fromAddress?: string;
  recipient?: string;
  recipients?: string[];
  subject?: string;
  body?: string;
  htmlBody?: string;
  sentAt?: string;
  attachments?: AnfrageEmailAttachment[];
  direction?: 'IN' | 'OUT';
  parentId?: number;
  parentEmailId?: number;
  replyCount?: number;
  replies?: AnfrageEmail[];
  benutzer?: string;
  frontendUserId?: number;
}

export interface AnfrageDetail extends Anfrage {
  emails?: AnfrageEmail[];
  dokumente?: AnfrageDokument[];
}

// ==================== Angebot DTOs ====================
export interface Angebot {
  id: number;
  kundenId?: number;
  kundenName?: string;
  bauvorhaben: string;
  kundennummer?: string;
  angebotsnummer?: string;
  betrag?: number;
  kundenEmails?: string[];
  emailVersandDatum?: string;
  projektId?: number;
  anlegedatum?: string;
  bildUrl?: string;
  projektStrasse?: string;
  projektPlz?: string;
  projektOrt?: string;
  kurzbeschreibung?: string;
  abgeschlossen?: boolean;
  createdAt?: string;
  kundenStrasse?: string;
  kundenPlz?: string;
  kundenOrt?: string;
  kundenTelefon?: string;
  kundenMobiltelefon?: string;
  kundenAnsprechpartner?: string;
  kundenAnrede?: string;
}

export interface AngebotDetail extends Angebot {
  emails?: ProjektEmail[];
  dokumente?: ProjektDokument[];
}

// ==================== Formularwesen DTOs ====================
export type FormBlockType =
  | 'heading'
  | 'text'
  | 'doknr'
  | 'projektnr'
  | 'kundennummer'
  | 'kunde'
  | 'adresse'
  | 'dokumenttyp'
  | 'datum'
  | 'seitenzahl'
  | 'logo'
  | 'table';

export interface FormBlockStyles {
  fontSize?: number;
  fontWeight?: string;
  color?: string;
  textAlign?: 'left' | 'center' | 'right';
}

export interface FormBlock {
  id: string;
  type: FormBlockType;
  page: number;
  x: number;
  y: number;
  z: number;
  width: number;
  height: number;
  content?: string;
  styles?: FormBlockStyles;
  // For table blocks: column widths in pixels
  tableColumns?: {
    pos: number;
    menge: number;
    me: number;
    bezeichnung: number;
    ep: number;
    gp: number;
  };
}

export interface FormTemplate {
  name?: string;
  html: string;
  placeholders?: string[];
  assignedDokumenttypen?: string[];
  assignedUserIds?: number[];
  modified?: string;
  created?: string;
}

export interface FormTemplateListItem {
  name: string;
  modified?: string;
  created?: string;
  assignedDokumenttypen?: string[];
}

export interface FrontendUser {
  id: number;
  displayName: string;
}

// Offene Posten / Outstanding Invoice Item
export type Mahnstufe = 'ZAHLUNGSERINNERUNG' | 'ERSTE_MAHNUNG' | 'ZWEITE_MAHNUNG';

export interface OffenerPosten {
  id: number;
  originalDateiname?: string;
  dateityp?: string;
  url?: string;
  dokumentGruppe?: string;
  uploadDatum?: string;
  emailVersandDatum?: string;
  rechnungsnummer?: string;
  rechnungsdatum?: string;
  faelligkeitsdatum?: string;
  rechnungsbetrag?: number;
  geschaeftsdokumentart?: string;
  bezahlt: boolean;
  mahnung: boolean;
  mahnstufe?: Mahnstufe;
  referenzDokumentId?: number;
  referenzDokumentNummer?: string;
  projektId?: number;
  projektAuftragsnummer?: string;
  projektKunde?: string;
}

// ==================== Ausgangs-Geschäftsdokumente ====================

export type AusgangsGeschaeftsDokumentTyp =
  | 'ANGEBOT'
  | 'AUFTRAGSBESTAETIGUNG'
  | 'RECHNUNG'
  | 'TEILRECHNUNG'
  | 'ABSCHLAGSRECHNUNG'
  | 'SCHLUSSRECHNUNG'
  | 'GUTSCHRIFT'
  | 'STORNO';

export const AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN: { value: AusgangsGeschaeftsDokumentTyp; label: string }[] = [
  { value: 'ANGEBOT', label: 'Angebot' },
  { value: 'AUFTRAGSBESTAETIGUNG', label: 'Auftragsbestätigung' },
  { value: 'RECHNUNG', label: 'Rechnung' },
  { value: 'TEILRECHNUNG', label: 'Teilrechnung' },
  { value: 'ABSCHLAGSRECHNUNG', label: 'Abschlagsrechnung' },
  { value: 'SCHLUSSRECHNUNG', label: 'Schlussrechnung' },
  { value: 'GUTSCHRIFT', label: 'Gutschrift' },
  { value: 'STORNO', label: 'Stornorechnung' },
];

export interface AusgangsGeschaeftsDokument {
  id: number;
  dokumentNummer: string;
  typ: AusgangsGeschaeftsDokumentTyp;
  datum: string;
  betreff?: string;
  betragNetto?: number;
  betragBrutto?: number;
  mwstSatz?: number;
  mwstBetrag?: number;
  abschlagsNummer?: number;
  zahlungszielTage?: number;
  versandDatum?: string;
  // Status
  gebucht: boolean;
  gebuchtAm?: string;
  storniert: boolean;
  storniertAm?: string;
  bearbeitbar: boolean;
  // Projekt
  projektId?: number;
  projektBauvorhaben?: string;
  projektnummer?: string;
  // Anfrage
  anfrageId?: number;
  // Kunde (Rechnungsadresse)
  kundeId?: number;
  kundennummer?: string;
  kundenName?: string;
  rechnungsadresse?: string;
  // Vorgänger
  vorgaengerId?: number;
  vorgaengerNummer?: string;
  positionenJson?: string;
  // Ersteller
  erstelltVonId?: number;
  erstelltVonName?: string;
}

export interface AusgangsGeschaeftsDokumentErstellen {
  typ: AusgangsGeschaeftsDokumentTyp;
  datum?: string;
  betreff?: string;
  betragNetto?: number;
  mwstSatz?: number;
  zahlungszielTage?: number;
  htmlInhalt?: string;
  positionenJson?: string;
  projektId?: number;
  anfrageId?: number;
  kundeId?: number;
  vorgaengerId?: number;
  // Ersteller
  erstelltVonId?: number;
  // Rechnungsadresse-Override (nur für dieses Dokument)
  rechnungsadresseOverride?: string;
}

export interface AusgangsGeschaeftsDokumentUpdate {
  datum?: string;
  betreff?: string;
  betragNetto?: number;
  mwstSatz?: number;
  zahlungszielTage?: number;
  htmlInhalt?: string;
  positionenJson?: string;
  // Rechnungsadresse-Override (nur für dieses Dokument)
  rechnungsadresseOverride?: string;
}

// ==================== Abrechnungsverlauf ====================

export interface AbrechnungspositionDto {
  id: number;
  dokumentNummer: string;
  typ: AusgangsGeschaeftsDokumentTyp;
  datum: string;
  betragNetto: number;
  abschlagsNummer?: number;
  storniert: boolean;
}

export interface AbrechnungsverlaufDto {
  basisdokumentId: number;
  basisdokumentNummer: string;
  basisdokumentTyp: AusgangsGeschaeftsDokumentTyp;
  basisdokumentBetragNetto: number;
  positionen: AbrechnungspositionDto[];
  bereitsAbgerechnet: number;
  restbetrag: number;
  bereitsAbgerechneteBlockIds?: string[];
}
