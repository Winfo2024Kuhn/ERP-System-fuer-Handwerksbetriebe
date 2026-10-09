package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.example.kalkulationsprogramm.domain.AusgelesenePosition;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantDokumentVerknuepfungSperre;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.domain.UntdidCodeliste;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentVerknuepfungSperreRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.LieferantDokumentPositionLeser.KiAntwort;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service zur KI-gestützten Analyse von Lieferanten-Dokumenten.
 * 
 * Reihenfolge der Datenextraktion:
 * 1. ZUGFeRD-PDF prüfen (strukturierte Daten eingebettet)
 * 2. XML-Datei prüfen (maschinenlesbar)
 * 3. Fallback: Gemini AI für OCR/Analyse
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiDokumentAnalyseService {

    private final ObjectMapper objectMapper;
    private final LieferantenRepository lieferantenRepository;
    private final LieferantDokumentRepository dokumentRepository;
    private final ZugferdExtractorService zugferdExtractorService;
    private final LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;
    private final SystemSettingsService systemSettingsService;
    private final ApplicationEventPublisher eventPublisher;
    private final LieferantDokumentAbgleich dokumentAbgleich;
    private final LieferantDokumentVerknuepfungSperreRepository sperreRepository;
    private final LieferantDokumentPositionService positionService;
    private final LieferantDokumentPositionLeser positionLeser;
    private final TransactionTemplate transactionTemplate;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    // Synchronized lock to ensure sequential API calls (prevents rate limiting &
    // race conditions)
    private static final ReentrantLock API_LOCK = new ReentrantLock(true); // fair lock

    // Minimum delay between API calls (ms) to prevent rate limiting
    private static final long API_CALL_DELAY_MS = 500;
    private static volatile long lastApiCallTime = 0;

    // Retry configuration for transient failures
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 2000; // 2 seconds, doubles each retry

    @Value("${ai.gemini.model.dokument-analyse:gemini-3-flash-preview}")
    private String geminiModel;

    @Value("${ai.gemini.model.pro:gemini-3.1-pro-preview}")
    private String geminiProModel;

    @Value("${upload.path:uploads}")
    private String uploadPath;

    /** Ausgabelimit je Gemini-Aufruf; Flash-Modelle erlauben bis 65.536. */
    @Value("${ai.gemini.dokument-analyse.max-output-tokens:65536}")
    private int maxOutputTokens = 65536;

    private static final String SYSTEM_PROMPT_DOKUMENT_ANALYSE = """
            Du bist ein Experte für die Analyse von deutschen Geschäftsdokumenten.

            !!! KRITISCHE PRÜFUNG: DOKUMENT-GÜLTIGKEIT !!!
            Prüfe das Dokument EXTREM SORGFÄLTIG auf Wasserzeichen oder großflächige Texte wie:
            - "Abschrift" (sehr häufig bei Kopien!)
            - "Kopie"
            - "Entwurf"
            - "Duplikat"
            - "Nicht zur Verbuchung"
            Falls einer dieser Begriffe irgendwo im Dokument (oft diagonal im Hintergrund als Wasserzeichen) sichtbar ist, ist das Dokument KEIN offizielles Original.
            DIES IST ESSENZIELL FÜR DIE BUCHHALTUNG.

            FALLS EINE KOPIE/ABSCHRIFT ERKANNT WIRD:
            Hänge zwingend " (Kopie)" an den dokumentTyp an (Beispiel: "RECHNUNG (Kopie)").

            DOKUMENTTYP-KLASSIFIKATION (WICHTIG - lies sorgfältig!):

            1. ANGEBOT:
               - Titel enthält: "Angebot", "Offerte", "Kostenvoranschlag"
               - Typische Nummern: AN-..., A-..., Angebots-Nr...
               - Hat KEINE Zahlungsaufforderung, keinen Fälligkeitstermin
               - Ist unverbindlich, Kunde muss erst bestellen

            2. AUFTRAGSBESTAETIGUNG (AB):
               - Titel enthält: "Auftragsbestätigung", "AB", "Bestellbestätigung", "Order Confirmation"
               - Typische Nummern: AB-..., AUF-..., Auftrags-Nr..., Bestätigung-Nr...
               - Bestätigt eine eingegangene Bestellung
               - Hat oft Liefertermin, aber KEINE Zahlungsaufforderung
               - Referenziert oft eine Angebotsnummer oder Bestellnummer

            3. LIEFERSCHEIN:
               - Titel enthält: "Lieferschein", "Warenbegleitschein", "Packing Slip"
               - Typische Nummern: LS-..., L-..., Lieferschein-Nr...
               - Dokumentiert gelieferte Waren
               - Hat KEINEN Gesamtbetrag mit MwSt, oft nur Stückzahlen

            4. RECHNUNG:
               - Titel enthält: "Rechnung", "Invoice", "Faktura"
               - Typische Nummern: RE-..., R-..., Rechnung-Nr..., Invoice-Nr...
               - Hat IMMER einen Bruttobetrag mit MwSt-Ausweis
               - Hat IMMER Zahlungsziel/Fälligkeitsdatum oder Bankverbindung
               - Fordert zur Zahlung auf

            5. GUTSCHRIFT (Credit Note):
               - Korrektur einer vorherigen Rechnung (Retoure, Nachlass, Rabatt)
               - Enthält oft Bezug auf Originalrechnung
               - Hat negativen Betrag oder "Gutschrift" im Titel
               - Keine Zahlungsaufforderung, sondern Guthaben

            6. WERKSTOFFZEUGNIS (Materialnachweis zu einer Lieferung):
               - Titel enthält: "Abnahmeprüfzeugnis", "Werkszeugnis", "Werkstoffzeugnis",
                 "Prüfbescheinigung", "Inspection Certificate", "Mill Test Certificate",
                 "Materialzertifikat", "Abnahmezeugnis"
               - Nennt meist EN 10204 bzw. DIN 50049 und den Typ "2.2", "3.1" oder "3.2"
               - Enthält Erzeugnis, Abmessung, Werkstoff/Güte (z. B. S235JR, S355J2, 1.4301),
                 Charge/Schmelze und oft chemische Analyse oder Festigkeitswerte
               - Hat KEINE Beträge und keine Zahlungsaufforderung
               - NICHT gemeint: Firmen-Zertifikate ohne Lieferbezug (ISO 9001, EN 1090,
                 Schweißfachbetrieb, Umweltzertifikat) – die sind SONSTIG

            7. SONSTIG (KEIN GESCHÄFTSDOKUMENT):
               - Kataloge, Produktinformationen, Werbematerial
               - Newsletter, Rundschreiben, Infoschreiben
               - Allgemeine Korrespondenz ohne Geschäftsvorgang
               - Technische Datenblätter, Firmen-Zertifikate (ISO 9001, EN 1090 …)
               - Zusammenstellungen, Abrechnungsübersichten ("E-ZUSAMMENSTELLUNG",
                 "Rechnungsübersicht", "Rechnungszusammenstellung") – auch wenn sie
                 Beträge, Zahlungsziele oder Bankdaten enthalten!
               - ALLES was NICHT Angebot/AB/Lieferschein/Rechnung/Gutschrift/Werkstoffzeugnis ist
               - WICHTIG: Wenn du dir nicht sicher bist ob es ein Geschäftsdokument ist,
                 dann wähle SONSTIG und setze confidence auf 0.0!

            EXTRAHIERE die folgenden Informationen als JSON:

            {
                "dokumentTyp": "ANGEBOT|AUFTRAGSBESTAETIGUNG|LIEFERSCHEIN|RECHNUNG|GUTSCHRIFT|WERKSTOFFZEUGNIS|SONSTIG (ggf. mit Vermerk ' (Kopie)')",
                "istGeschaeftsdokument": true/false,
                "dokumentNummer": "Die Dokumentnummer (exakt wie im Dokument) oder null",
                "dokumentDatum": "YYYY-MM-DD oder null",
                "betragNetto": 1234.56 oder null,
                "betragBrutto": 1469.33 oder null,
                "mwstSatz": 0.19 oder null,
                "liefertermin": "YYYY-MM-DD oder null",
                "zahlungsziel": "YYYY-MM-DD (berechnetes Fälligkeitsdatum) oder null",
                "bestellnummer": "Unsere Bestellnummer ('Ihre Bestellung', 'Ihre Bestell-Nr.', 'Ihr Auftrag Nr.') oder null. Bei ANGEBOT fast immer null. NICHT die Kundennummer!",
                "referenzNummer": "Nummer die auf ein VORHERIGES Dokument verweist. PRIORITÄT je nach Dokumenttyp: Bei AB→Angebots-Nr. suchen. Bei RECHNUNG/LIEFERSCHEIN→Auftrags-Nr./AB-Nr. suchen. Bei WERKSTOFFZEUGNIS→Lieferschein-Nr., sonst Auftrags-Nr. Bei GUTSCHRIFT→Rechnungs-Nr. suchen. Suche nach: 'Ihr Angebot', 'unser Angebot', 'gemäß Angebot', 'Angebots-Nr.', 'Auftrags-Nr.', 'AB-Nr.', 'Ihre Bestellung', 'Rechnungs-Nr.'",
                "weitereReferenzen": ["ALLE weiteren im Dokument genannten Nummern anderer Geschäftsdokumente: Angebots-Nr., Auftrags-/AB-Nr., Lieferschein-Nr., Rechnungs-Nr., Vorgangs-Nr. NICHT die eigene dokumentNummer, NICHT Kundennummer, Artikelnummer, Telefon, IBAN oder Steuernummer. Leeres Array wenn keine."],
                "kommission": "Kommission / Bauvorhaben / Projekt / Objekt / 'Ihr Zeichen' falls angegeben (z.B. 'BV Mustermann, Hauptstr. 5'), sonst null",
                "bereitsGezahlt": true/false,
                "zahlungsart": "VORAUSKASSE|SEPA_LASTSCHRIFT|KREDITKARTE|PAYPAL|AMAZON_PAY|UEBERWEISUNG|BAR|SONSTIGE|null",
                "skontoTage": 8 oder null,
                "skontoProzent": 2.0 oder null,
                "nettoTage": 30 oder null,
                "confidence": 0.0-1.0,
                "artikelPositionen": [...],
                "lieferantName": "Name des Lieferanten/Absenders",
                "lieferantStrasse": "Straße und Hausnummer",
                "lieferantPlz": "Postleitzahl",
                "lieferantOrt": "Ort"
            }

            WICHTIG FÜR NICHT-GESCHÄFTSDOKUMENTE:
            - Wenn dokumentTyp = "SONSTIG", dann setze istGeschaeftsdokument = false
            - Wenn dokumentTyp = "SONSTIG", dann sind alle anderen Felder (dokumentNummer, betrag etc.) = null
              und artikelPositionen = []
            - confidence sollte bei SONSTIG = 0.0 sein, da keine relevanten Daten extrahiert werden

            ARTIKELPOSITIONEN EXTRAKTION – ZWEI SCHRITTE:
            SCHRITT 1: Bestimme ZUERST den dokumentTyp (siehe oben).
            SCHRITT 2: NUR wenn dokumentTyp ANGEBOT, AUFTRAGSBESTAETIGUNG, LIEFERSCHEIN,
              RECHNUNG, GUTSCHRIFT oder WERKSTOFFZEUGNIS ist, lies die Positionen aus. Bei SONSTIG
              (Formulare, Kataloge, Preislisten, Infoschreiben, Firmen-Zertifikate,
              Zusammenstellungen) ist artikelPositionen IMMER ein leeres Array [] – dort KEINE
              Positionen auslesen.

            - Extrahiere ALLE Positionen (jede Tabellenzeile mit Artikel oder Betrag),
              AUCH solche ohne Artikelnummer (Fracht, Zuschläge, Arbeitsleistung, Freitext-Artikel).
            - Jede Position: {"positionNr": 1, "positionsArt": "WARE|NEBENKOSTEN|RABATT",
              "externeArtikelnummer": "..." oder null, "bezeichnung": "Artikeltext",
              "menge": 12.5, "mengeneinheit": "...", "einzelpreis": 12.34,
              "preiseinheit": "...", "gesamtpreisNetto": 154.25,
              "werkstoff": "..." oder null, "charge": "..." oder null, "abmessung": "..." oder null}
            - werkstoff, charge, abmessung bei JEDEM Dokumenttyp ausfüllen, wenn sie zur Position
              aufgedruckt sind (auch auf Lieferscheinen/Rechnungen), sonst null:
              werkstoff = Werkstoff/Güte ("S235JR+AR", "S355J2+N", "1.4301", "EN AW-6060 T66"),
              charge = Charge, Schmelze, Heat No. oder Los-Nr. exakt wie gedruckt,
              abmessung = Maße ("50x5", "60,3x2,9", "IPE 200", "2000x1000x3").
            - WERKSTOFFZEUGNIS: Jede Erzeugnis-Zeile (je Charge) ist eine Position.
              bezeichnung = Erzeugnis mit Maß (z. B. "Flachstahl 50x5"), menge/mengeneinheit wie
              gedruckt (Stück, kg, t, m), einzelpreis/preiseinheit/gesamtpreisNetto = null,
              positionsArt = WARE. Prüfwerte (Streckgrenze, Analyse) NICHT als Position aufnehmen.
            - positionsArt NEBENKOSTEN: Fracht, Versand, Porto, Verpackung, Palette, Maut,
              Energie-, Legierungs-, Material-, Mindermengen- oder Kleinmengenzuschlag.
            - positionsArt RABATT: Rabatt-, Abzugs- oder Bonuszeilen; gesamtpreisNetto NEGATIV.
            - Alles andere ist WARE.
            - bezeichnung: kurzer, vollständiger Artikeltext (z. B. "Flachstahl 50x5 S235JR").
            - gesamtpreisNetto ist die Zeilensumme netto. KEINE Summenzeilen (Zwischensumme,
              Nettobetrag, MwSt, Gesamtbetrag) als Position aufnehmen.
            - Auf Lieferscheinen fehlen Preise oft: dann einzelpreis/gesamtpreisNetto = null.
            - AUFGESCHLÜSSELTE POSITIONEN: Wird eine Position auf Folgeseiten in Bestandteile
              mit Einzelpreisen zerlegt (Ausstattung, Zubehör, Optionen) und endet mit einer
              "Positionssumme", dann NUR die Hauptposition mit der Positionssumme aufnehmen –
              die Bestandteile NICHT als eigene Positionen. Die Summe aller Positionen muss
              zum Nettobetrag des Dokuments passen.
            - Die externe Artikelnummer ist oft eine Materialnummer wie "12345" oder "MAT-001"
            - einzelpreis ist der Preis EINER Preiseinheit, NICHT die Positionssumme
            - Die Preiseinheit ist entscheidend: "€/t" = pro Tonne, "€/100kg" = pro 100kg,
              "€/kg" = pro kg, "€/Stück" = pro Stück, "€/m" = pro laufendem Meter
            - Die Mengeneinheit ist die Einheit der gelieferten Menge, z.B. "Stück", "kg", "m"
            - Falls keine Preiseinheit erkennbar: gib null zurück. NICHT raten und NICHT
              "kg" annehmen - auf Rechnungen stehen genauso oft Stückpreise für
              Zukaufteile (Handlaufhalter, Glasklemmen) wie Kilopreise für Stahl.

            ZAHLUNGSBEDINGUNGEN ERKENNUNG (SEHR WICHTIG!):
            Typische Muster auf deutschen Rechnungen:
            - "8 TAGE 2% 30 TAGE NETTO" → skontoTage=8, skontoProzent=2, nettoTage=30
            - "14 Tage 3% Skonto, 30 Tage netto" → skontoTage=14, skontoProzent=3, nettoTage=30
            - "bei Zahlung binnen 10 Tagen 2% Skonto" → skontoTage=10, skontoProzent=2
            - "zahlbar innerhalb 30 Tagen" → nettoTage=30
            - "SOFORT NETTO KASSE" → nettoTage=0 (sofort fällig)
            - "rein netto Kasse" → nettoTage=0
            - "Valuta 30 Tage" → nettoTage=30

            LIEFERTERMIN ERKENNUNG (WICHTIG - NICHT MIT ZAHLUNGSBEDINGUNGEN VERWECHSELN!):
            Der Liefertermin gibt an WANN DIE WARE GELIEFERT WIRD, nicht wann bezahlt werden muss!

            Formate:
            - Explizites Datum: "15.01.2026" → liefertermin="2026-01-15"
            - Kalenderwoche: "KW 02/2026" → berechne Montag der KW → liefertermin="2026-01-05"
            - KW-Bereich: "KW 02-03/2026" → verwende Montag der ersten KW → liefertermin="2026-01-05"
            - Mit CA/U.V.: "CA. KW 02/2026 U.V." → ignoriere "CA." und "U.V.", parse KW
            - "TERMIN: KW 45/2025" → liefertermin="2025-11-03"
            - TAGE-FORMAT: "TERMIN: CA. 8 TAGE U. Ü. V." → liefertermin = dokumentDatum + 8 Tage
            - TAGE-FORMAT: "Lieferzeit ca. 5 Tage" → liefertermin = dokumentDatum + 5 Tage
            - TAGE-FORMAT: "Lieferung innerhalb 10 Werktagen" → liefertermin = dokumentDatum + 10 Tage

            WICHTIG: "8 TAGE" bei TERMIN/LIEFERZEIT ist NICHT Skonto! Das ist der Liefertermin!
            Skonto steht immer im Kontext von "Zahlung", "Skonto", "netto", "%".

            ZAHLUNGSZIEL BERECHNUNG:
            - Wenn ein explizites Fälligkeitsdatum im Dokument steht → verwende dieses
            - Wenn nur nettoTage gefunden → berechne: dokumentDatum + nettoTage = zahlungsziel
            - Beispiel: dokumentDatum="2024-12-01", nettoTage=30 → zahlungsziel="2024-12-31"


            REGELN:
            1. Antworte NUR mit dem JSON-Objekt, ohne Erklärungen.
            2. Verwende null für fehlende Werte.
            3. Beträge als Dezimalzahlen ohne Währungssymbol.
            4. Datum immer im Format YYYY-MM-DD.
            5. Bei AUFTRAGSBESTAETIGUNG besonders auf "AB", "Bestätigung", "Auftragsbestätigung" im Titel achten!
            6. Wenn das Dokument "Rechnung" im Titel hat UND Zahlungsinfos enthält -> RECHNUNG
            7. Wenn das Dokument "Auftragsbestätigung" oder "AB" im Titel hat -> AUFTRAGSBESTAETIGUNG
            8. confidence zwischen 0.0 und 1.0 basierend auf Lesbarkeit und Eindeutigkeit.
            9. bereitsGezahlt = true wenn:
                - "bereits bezahlt", "Betrag dankend erhalten" oder ähnliches steht
                - "Vorauskasse", "per vorkasse" oder "Vorkasse" vermerkt ist
                - Ein "Bezahlt"-Kasten/Stempel sichtbar ist (z.B. oben rechts "Bezahlt", häufig bei Amazon)
                - Eine "Zahlungsreferenznummer" angegeben ist
                - Eine direkte Zahlungsart angegeben ist: "Kreditkarte", "MasterCard", "VISA", "PayPal", "Amazon Pay", "Lastschrift"
                - "Zahlung erfolgt am..." oder ähnliches Datum in der Vergangenheit
                - SEPA-Lastschrift / SEPA-Mandat / Einzugsermächtigung / Bankeinzug erwähnt wird
                - "Abbuchung erfolgt", "wird abgebucht", "Einzug", "Lastschrifteinzug"
                - "per Lastschrift", "per Bankeinzug", "Mandat-Referenz"
                - "IBAN-Lastschrift", "SEPA Direct Debit", "Basisverfahren"
                - "Der Betrag wird eingezogen", "erfolgt automatisch"
                - "buchen wir ab", "buchen ab", "werden abgebucht", "Betrag buchen wir"
                - "Mandatsreferenz" oder "Gläubiger-ID" vorhanden
                - Bei automatischen Zahlungsarten ist bereitsGezahlt IMMER true!
            10. EXTRAHIERE zahlungsart NUR wenn die Zahlungsart explizit oder eindeutig genannt ist:
                - "Vorauskasse", "Vorkasse", "prepaid" → zahlungsart="VORAUSKASSE"
                - "SEPA-Lastschrift", "SEPA-Mandat", "Lastschrifteinzug", "Bankeinzug", "SEPA Direct Debit" → zahlungsart="SEPA_LASTSCHRIFT"
                - "Kreditkarte", "MasterCard", "VISA" → zahlungsart="KREDITKARTE"
                - "Kartenzahlung", "Kartenzahlg.", "EC-Karte", "EC Karte", "girocard", "Girocard", "Maestro", "V Pay", "Debitkarte" → zahlungsart="KREDITKARTE"
                  (Begruendung: Im ERP gibt es nur eine "Karte"-Kategorie — Debit-/Kreditkarte
                  werden zusammengefasst. Der Buchhalter kann am PC noch praezisieren.)
                - "PayPal" → zahlungsart="PAYPAL"
                - "Amazon Pay" → zahlungsart="AMAZON_PAY"
                - "Überweisung", "bitte überweisen" → zahlungsart="UEBERWEISUNG"
                - "Barzahlung", "bar bezahlt" → zahlungsart="BAR"
                - Andere explizit genannte Zahlungsart → zahlungsart="SONSTIGE"
                - Wenn keine eindeutige Zahlungsart erkennbar ist → zahlungsart=null
            11. SEPA-Lastschrift ist AUSDRÜCKLICH NICHT Vorauskasse.
                Wenn eine Rechnung per SEPA-Lastschrift bezahlt wird, dann:
                - bereitsGezahlt = true
                - zahlungsart = "SEPA_LASTSCHRIFT"
                - NICHT "VORAUSKASSE"
            12. IMMER nach Skonto-Bedingungen suchen! Diese stehen oft klein gedruckt am Dokumentende.
            13. Wenn zahlungsziel nicht als Datum lesbar, aber nettoTage erkannt: berechne zahlungsziel selbst!
            14. Wenn das Dokument kein Angebot/AB/Lieferschein/Rechnung/Gutschrift/Werkstoffzeugnis ist --> dokumentTyp="SONSTIG", istGeschaeftsdokument=false
                WERKSTOFFZEUGNIS ist ein Geschäftsdokument (istGeschaeftsdokument=true) OHNE Beträge:
                dokumentNummer = Zeugnis-/Bescheinigungs-Nr., betragNetto/betragBrutto/mwstSatz = null,
                zahlungsziel/skonto/zahlungsart = null.
            15. REFERENZNUMMER EXTRAKTION (SEHR WICHTIG für Dokumenten-Verknüpfung!):
                - Bei AUFTRAGSBESTAETIGUNG: Suche nach "Ihr Angebot", "unser Angebot", "gemäß Angebot", "Angebots-Nr.", "Bezug: Angebot" → das ist die Angebotsnummer
                - Bei LIEFERSCHEIN: Suche nach "Auftrags-Nr.", "AB-Nr.", "Ihre Bestellung" → das ist die AB-Nummer
                - Bei RECHNUNG: Suche nach "Auftrags-Nr.", "AB-Nr." (PRIORITÄT!) oder "Lieferschein-Nr." → verweist auf AB oder Lieferschein
                - Bei GUTSCHRIFT: Suche nach "Rechnungs-Nr.", "zu Rechnung" → verweist auf die Original-Rechnung
                - Bei WERKSTOFFZEUGNIS: Suche nach "Lieferschein-Nr.", "Lieferschein", "Delivery Note" (PRIORITÄT!),
                  sonst "Auftrags-Nr.", "Kommissions-Nr.", "Ihre Bestellung" → verweist auf Lieferschein bzw. AB.
                  Die übrigen dieser Nummern in "weitereReferenzen", die Bestellnummer in "bestellnummer".
                - Bei ANGEBOT: Ist es ein geändertes Angebot ("ersetzt Angebot", "Änderung zu Angebot",
                  "Revision", "Nachtragsangebot zu"), dann die Nummer des ursprünglichen Angebots als
                  referenzNummer eintragen.
                - Alle übrigen genannten Belegnummern zusätzlich in "weitereReferenzen" eintragen.
                - "kommission" IMMER mit ausfüllen, wenn Kommission/Bauvorhaben/Projekt genannt ist –
                  besonders bei ANGEBOT, denn dort fehlt die Bestellnummer noch und die Kommission ist
                  oft der einzige Bezug zur späteren Auftragsbestätigung.
            16. DOKUMENT-GÜLTIGKEIT: Wenn "Abschrift", "Kopie", "Entwurf" oder "Duplikat" irgendwo im Dokument steht, MUSS " (Kopie)" an dokumentTyp angehängt werden.
            17. ABRECHNUNGS-ZUSAMMENSTELLUNG: Wenn der Titel oder die Überschrift des
                Dokuments "Zusammenstellung" enthält (z.B. "E-ZUSAMMENSTELLUNG",
                "E-Zusammenstellung", "Rechnungszusammenstellung") → ZWINGEND SONSTIG,
                istGeschaeftsdokument=false. Das gilt AUCH wenn das Dokument Beträge,
                Rechnungsnummern, Zahlungsziele oder Bankverbindungen enthält.
            18. KEIN STEUERBELEG: Wenn das Dokument den Satz "Gilt nicht als
                Umsatzsteuererstattungsbeleg" ODER "berechtigt nicht zum Vorsteuerabzug"
                enthält → ZWINGEND SONSTIG, istGeschaeftsdokument=false.
                Solche Dokumente sind nie buchhalterisch verwertbare Belege.
            """;

    /**
     * Analysiert ein Dokument und erstellt automatisch LieferantGeschaeftsdokument.
     * 
     * Reihenfolge:
     * 1. ZUGFeRD-PDF prüfen
     * 2. XML-Datei prüfen
     * 3. Fallback: Gemini AI
     */
    @Transactional
    public LieferantGeschaeftsdokument analysiereDokument(LieferantDokument dokument) {
        return analysiereDokument(dokument, null);
    }

    /**
     * Re-analysiert ein Dokument anhand der ID in einer neuen Transaktion.
     * Wird für Batch-Reanalyse verwendet um Session-Konflikte zu vermeiden.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public LieferantGeschaeftsdokument reanalysiereDokumentById(Long dokumentId) {
        LieferantDokument dokument = dokumentRepository.findById(dokumentId).orElse(null);
        if (dokument == null) {
            log.warn("Dokument {} nicht gefunden für Re-Analyse", dokumentId);
            return null;
        }
        return analysiereDokument(dokument, null);
    }

    /**
     * Startet die Analyse asynchron im Hintergrund.
     * Ideal für User-Uploads, damit der User nicht warten muss.
     * 
     * TEMPORARILY DISABLED FOR TESTING
     */
    @org.springframework.scheduling.annotation.Async
    public void analyzeAsync(Long dokumentId) {
        // TEMPORARILY DISABLED FOR TESTING - uncomment below to re-enable
        // log.info("Starte asynchrone Analyse für Dokument {}", dokumentId);
        // reanalysiereDokumentById(dokumentId);
        log.debug("analyzeAsync DISABLED for testing - skipping document {}", dokumentId);
    }

    /**
     * Analysiert eine Datei OHNE sie als Dokument zu speichern.
     * Wird für den manuellen Dokument-Importer verwendet (Vorschau der extrahierten
     * Daten).
     * 
     * @param dateiPfad        Pfad zur temporären Datei
     * @param originalFilename Original-Dateiname für MIME-Type Erkennung
     * @return AnalyzeResponse DTO mit extrahierten Daten
     */
    public org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse analyzeFile(
            Path dateiPfad, String originalFilename) {
        return analyzeFile(dateiPfad, originalFilename, false);
    }

    public org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse analyzeFile(
            Path dateiPfad, String originalFilename, boolean useProModel) {
        try {
            dateiPfad = validiereAnalyseDateiPfad(dateiPfad, true);
            String lower = originalFilename.toLowerCase();

            // 1. Versuche ZUGFeRD-Extraktion bei PDF
            if (lower.endsWith(".pdf")) {
                var zugferdResult = versucheZugferdExtraktionFuerPreview(dateiPfad, originalFilename);
                if (zugferdResult != null) {
                    return zugferdResult;
                }
            }

            // 2. Versuche XML-Extraktion bei XML-Dateien
            if (lower.endsWith(".xml")) {
                var xmlResult = versucheXmlExtraktionFuerPreview(leseXmlDateiSicher(dateiPfad, true));
                if (xmlResult != null) {
                    return xmlResult;
                }
            }

            // 3. Fallback: KI-Analyse (für PDFs und Bilder)
            return analysierePerKiFuerPreview(dateiPfad, originalFilename, useProModel);

        } catch (Exception e) {
            log.error("Fehler bei Dateianalyse: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Analysiert eine PDF auf MEHRERE Rechnungen (z.B. Amazon mit 2+ Rechnungen).
     * Erkennt "Seite x/y" Muster um Grenzen zu identifizieren und splittet die PDF.
     * 
     * @param dateiPfad        Pfad zur PDF-Datei
     * @param originalFilename Original-Dateiname
     * @return Liste von MultiInvoiceAnalyzeResponse (bei einzelner Rechnung: Liste
     *         mit 1 Element)
     */
    public java.util.List<org.example.kalkulationsprogramm.dto.LieferantDokumentDto.MultiInvoiceAnalyzeResponse> analyzeFileForMultipleInvoices(
            Path dateiPfad, String originalFilename) {

        java.util.List<org.example.kalkulationsprogramm.dto.LieferantDokumentDto.MultiInvoiceAnalyzeResponse> results = new java.util.ArrayList<>();

        try {
            dateiPfad = validiereAnalyseDateiPfad(dateiPfad, true);

            // Nur PDFs können mehrere Rechnungen enthalten
            if (!originalFilename.toLowerCase().endsWith(".pdf")) {
                // Für nicht-PDFs: Normale Analyse
                var singleResult = analyzeFile(dateiPfad, originalFilename);
                if (singleResult != null) {
                    results.add(org.example.kalkulationsprogramm.dto.LieferantDokumentDto.MultiInvoiceAnalyzeResponse
                            .builder()
                            .pageRange("alle")
                            .analyzeResponse(singleResult)
                            .build());
                }
                return results;
            }

            // KI fragen: Wie viele Dokumente sind in dieser PDF?
            byte[] pdfBytes = Files.readAllBytes(dateiPfad);
            String multiDocPrompt = buildMultiDocumentPrompt();
            String jsonResponse = rufGeminiApiMitPrompt(pdfBytes, "application/pdf", multiDocPrompt);

            if (jsonResponse == null) {
                // Fallback: Einzelne Analyse
                var singleResult = analyzeFile(dateiPfad, originalFilename);
                if (singleResult != null) {
                    results.add(org.example.kalkulationsprogramm.dto.LieferantDokumentDto.MultiInvoiceAnalyzeResponse
                            .builder()
                            .pageRange("alle")
                            .analyzeResponse(singleResult)
                            .splitPdfBase64(Base64.getEncoder().encodeToString(pdfBytes))
                            .build());
                }
                return results;
            }

            // JSON parsen
            var jsonArray = objectMapper.readTree(jsonResponse);

            if (!jsonArray.isArray() || jsonArray.size() == 0) {
                // Fallback: Einzelne Analyse
                var singleResult = analyzeFile(dateiPfad, originalFilename);
                if (singleResult != null) {
                    results.add(org.example.kalkulationsprogramm.dto.LieferantDokumentDto.MultiInvoiceAnalyzeResponse
                            .builder()
                            .pageRange("alle")
                            .analyzeResponse(singleResult)
                            .splitPdfBase64(Base64.getEncoder().encodeToString(pdfBytes))
                            .build());
                }
                return results;
            }

            // Mehrere Rechnungen erkannt - PDF splitten
            try (org.apache.pdfbox.pdmodel.PDDocument originalDoc = org.apache.pdfbox.Loader
                    .loadPDF(dateiPfad.toFile())) {

                int totalPages = originalDoc.getNumberOfPages();

                for (var invoiceNode : jsonArray) {
                    String pageRange = invoiceNode.has("seiten") ? invoiceNode.get("seiten").asText() : "alle";

                    // Parse AnalyzeResponse von diesem Invoice-Node
                    var analyzeResponse = parseJsonToAnalyzeResponse(invoiceNode.toString());

                    // PDF-Seiten extrahieren
                    int[] pages = parsePageRange(pageRange, totalPages);
                    byte[] splitPdfBytes = extractPages(originalDoc, pages);

                    results.add(org.example.kalkulationsprogramm.dto.LieferantDokumentDto.MultiInvoiceAnalyzeResponse
                            .builder()
                            .pageRange(pageRange)
                            .analyzeResponse(analyzeResponse)
                            .splitPdfBase64(Base64.getEncoder().encodeToString(splitPdfBytes))
                            .build());
                }
            }

            return results;

        } catch (Exception e) {
            log.error("Fehler bei Multi-Invoice-Analyse: {}", e.getMessage(), e);
            // Fallback: Einzelne Analyse
            try {
                var singleResult = analyzeFile(dateiPfad, originalFilename);
                if (singleResult != null) {
                    byte[] pdfBytes = Files.readAllBytes(dateiPfad);
                    results.add(org.example.kalkulationsprogramm.dto.LieferantDokumentDto.MultiInvoiceAnalyzeResponse
                            .builder()
                            .pageRange("alle")
                            .analyzeResponse(singleResult)
                            .splitPdfBase64(Base64.getEncoder().encodeToString(pdfBytes))
                            .build());
                }
            } catch (Exception ex) {
                log.error("Auch Fallback fehlgeschlagen", ex);
            }
            return results;
        }
    }

    private String buildMultiDocumentPrompt() {
        return """
                Analysiere dieses PDF-Dokument SEHR SORGFÄLTIG.

                WICHTIG: Prüfe ob MEHRERE SEPARATE Rechnungen/Geschäftsdokumente in diesem PDF enthalten sind.
                Achte auf "Seite 1/X" Muster - wenn die Seitenzählung neu bei 1 beginnt, ist das ein neues Dokument!

                Beispiel: "Seite 1/2", "Seite 2/2" = erste Rechnung (2 Seiten)
                          "Seite 1/1" = zweite Rechnung (1 Seite)

                Antworte NUR mit einem JSON-Array. Für JEDES erkannte Dokument ein Objekt:
                [
                  {
                    "seiten": "1-2",
                    "dokumentTyp": "RECHNUNG",
                    "dokumentNummer": "...",
                    "dokumentDatum": "YYYY-MM-DD",
                    "betragBrutto": 123.45,
                    "betragNetto": 103.74,
                    "bestellnummer": "...",
                    "referenzNummer": "...",
                    "weitereReferenzen": ["weitere genannte Belegnummern (Angebot, AB, Lieferschein, Rechnung), nicht Kundennummer"],
                    "kommission": "Kommission/Bauvorhaben falls angegeben, sonst null",
                    "bereitsGezahlt": true,
                                        "zahlungsart": "SEPA_LASTSCHRIFT",
                    "confidence": 0.95
                  }
                ]

                Wenn nur EIN Dokument vorhanden ist, gib trotzdem ein Array mit einem Element zurück.

                WICHTIG FÜR "bereitsGezahlt":
                Setze auf true wenn:
                - "Bezahlt"-Vermerk sichtbar (z.B. Box "Bezahlt" oben rechts wie bei Amazon)
                - Zahlungsreferenznummer vorhanden
                - Zahlungsart Kreditkarte/PayPal/Amazon Pay/Lastschrift
                - "Betrag dankend erhalten"
                - SEPA-Lastschrift / SEPA-Mandat / Einzugsermächtigung / Bankeinzug
                - "Abbuchung erfolgt", "wird abgebucht", "per Lastschrift"
                - "buchen wir ab", "buchen ab", "Mandatsreferenz", "Gläubiger-ID"
                - Bei automatischen Zahlungsarten ist bereitsGezahlt IMMER true!

                WICHTIG FÜR "zahlungsart":
                - SEPA-Lastschrift / Bankeinzug / SEPA Direct Debit -> "SEPA_LASTSCHRIFT"
                - Vorauskasse / Vorkasse -> "VORAUSKASSE"
                - SEPA-Lastschrift ist NICHT Vorauskasse
                """;
    }

    public String rufGeminiApiMitPrompt(byte[] bytes, String mimeType, String customPrompt) {
        return rufGeminiApiMitPrompt(bytes, mimeType, customPrompt, false);
    }

    public String rufGeminiApiMitPrompt(byte[] bytes, String mimeType, String customPrompt, boolean useProModel) {
        API_LOCK.lock();
        try {
            // Ensure minimum delay between API calls to prevent rate limiting
            long now = System.currentTimeMillis();
            long elapsed = now - lastApiCallTime;
            if (elapsed < API_CALL_DELAY_MS && lastApiCallTime > 0) {
                try {
                    Thread.sleep(API_CALL_DELAY_MS - elapsed);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            log.debug("[Gemini API] Starte Multi-Doc API-Aufruf (Lock erworben)");

            String base64Data = Base64.getEncoder().encodeToString(bytes);
            String modelToUse = useProModel ? geminiProModel : geminiModel;
            // Key zur Laufzeit aus System-Setup lesen.
            String geminiApiKey = systemSettingsService.getGeminiApiKey();
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelToUse
                    + ":generateContent?key=" + geminiApiKey;

            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode contents = requestBody.putArray("contents");
            ObjectNode content = contents.addObject();
            ArrayNode parts = content.putArray("parts");

            // Bild/PDF Teil
            ObjectNode inlineData = parts.addObject().putObject("inline_data");
            inlineData.put("mime_type", mimeType);
            inlineData.put("data", base64Data);

            // Custom Prompt Teil
            parts.addObject().put("text", customPrompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            lastApiCallTime = System.currentTimeMillis();

            if (response.statusCode() != 200) {
                log.error("Gemini API Fehler: {} - {}", response.statusCode(), response.body());
                return null;
            }

            JsonNode responseJson = objectMapper.readTree(response.body());
            JsonNode candidates = responseJson.path("candidates");
            if (candidates.isEmpty()) {
                return null;
            }

            String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText();

            // JSON aus Markdown extrahieren falls vorhanden
            if (text.contains("```json")) {
                int start = text.indexOf("```json") + 7;
                int end = text.indexOf("```", start);
                if (end > start) {
                    text = text.substring(start, end).trim();
                }
            } else if (text.contains("```")) {
                int start = text.indexOf("```") + 3;
                int end = text.indexOf("```", start);
                if (end > start) {
                    text = text.substring(start, end).trim();
                }
            }

            log.debug("[Gemini API] Multi-Doc API-Aufruf erfolgreich");
            return text.trim();

        } catch (Exception e) {
            log.error("Gemini Multi-Doc API-Aufruf fehlgeschlagen: {}", e.getMessage(), e);
            return null;
        } finally {
            API_LOCK.unlock();
        }
    }

    private int[] parsePageRange(String pageRange, int totalPages) {
        if (pageRange == null || pageRange.equalsIgnoreCase("alle")) {
            int[] all = new int[totalPages];
            for (int i = 0; i < totalPages; i++) {
                all[i] = i;
            }
            return all;
        }

        try {
            java.util.List<Integer> pages = new java.util.ArrayList<>();
            String[] parts = pageRange.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.contains("-")) {
                    String[] range = part.split("-");
                    int start = Integer.parseInt(range[0].trim()) - 1; // 0-indexed
                    int end = Integer.parseInt(range[1].trim()) - 1;
                    for (int i = start; i <= end && i < totalPages; i++) {
                        if (i >= 0)
                            pages.add(i);
                    }
                } else {
                    int page = Integer.parseInt(part) - 1;
                    if (page >= 0 && page < totalPages)
                        pages.add(page);
                }
            }
            return pages.stream().mapToInt(Integer::intValue).toArray();
        } catch (Exception e) {
            log.warn("Konnte Page-Range nicht parsen: {}", pageRange);
            int[] all = new int[totalPages];
            for (int i = 0; i < totalPages; i++) {
                all[i] = i;
            }
            return all;
        }
    }

    private byte[] extractPages(org.apache.pdfbox.pdmodel.PDDocument sourceDoc, int[] pageIndices) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument newDoc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            for (int pageIndex : pageIndices) {
                if (pageIndex >= 0 && pageIndex < sourceDoc.getNumberOfPages()) {
                    newDoc.addPage(sourceDoc.getPage(pageIndex));
                }
            }
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            newDoc.save(baos);
            return baos.toByteArray();
        }
    }

    /**
     * ZUGFeRD-Extraktion die ein DTO zurückgibt (für Preview).
     */
    private org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse versucheZugferdExtraktionFuerPreview(
            Path dateiPfad, String dateiname) {
        try {
            var zugferd = zugferdExtractorService.extract(dateiPfad.toString(), dateiname);

            if (zugferd.getRechnungsnummer() == null && zugferd.getBetrag() == null) {
                return null;
            }

            log.info("ZUGFeRD-Daten gefunden für Preview: Nr={}, Betrag={}",
                    zugferd.getRechnungsnummer(), zugferd.getBetrag());

            // Dokumenttyp bestimmen
            LieferantDokumentTyp typ = LieferantDokumentTyp.RECHNUNG;
            if (zugferd.getGeschaeftsdokumentart() != null) {
                typ = switch (zugferd.getGeschaeftsdokumentart()) {
                    case "Angebot" -> LieferantDokumentTyp.ANGEBOT;
                    case "Auftragsbestätigung" -> LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG;
                    case "Gutschrift" -> LieferantDokumentTyp.GUTSCHRIFT;
                    default -> LieferantDokumentTyp.RECHNUNG;
                };
            }

            return org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse.builder()
                    .dokumentTyp(typ)
                    .dokumentNummer(zugferd.getRechnungsnummer())
                    .dokumentDatum(zugferd.getRechnungsdatum())
                    .betragNetto(zugferd.getBetragNetto())
                    .betragBrutto(zugferd.getBetrag())
                    .mwstSatz(zugferd.getMwstSatz())
                    .zahlungsziel(zugferd.getFaelligkeitsdatum())
                    .bestellnummer(zugferd.getBestellnummer())
                    .referenzNummer(zugferd.getReferenzNummer())
                    .skontoTage(zugferd.getSkontoTage())
                    .skontoProzent(zugferd.getSkontoProzent())
                    .nettoTage(zugferd.getNettoTage())
                    .bereitsGezahlt(zugferd.getBereitsGezahlt())
                    .aiConfidence(1.0)
                    .analyseQuelle("ZUGFeRD")
                    .build();

        } catch (Exception e) {
            log.debug("ZUGFeRD-Preview fehlgeschlagen: {}", e.getMessage());
            return null;
        }
    }

    /**
     * XML-Extraktion die ein DTO zurückgibt (für Preview).
     */
    private org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse versucheXmlExtraktionFuerPreview(
            String xmlContent) {
        try {
            if (!xmlContent.contains("Invoice") && !xmlContent.contains("CrossIndustryInvoice")) {
                return null;
            }

            String dokumentNummer = extractXmlValue(xmlContent, "ID", "InvoiceNumber", "ram:ID");
            java.math.BigDecimal betragBrutto = null;
            String betragStr = extractXmlValue(xmlContent, "GrandTotalAmount", "PayableAmount", "ram:GrandTotalAmount");
            if (betragStr != null) {
                try {
                    betragBrutto = new java.math.BigDecimal(betragStr.replace(',', '.'));
                } catch (NumberFormatException ignored) {
                }
            }

            LocalDate dokumentDatum = null;
            String datumStr = extractXmlValue(xmlContent, "IssueDate", "IssueDateTime", "ram:DateTimeString");
            if (datumStr != null) {
                try {
                    datumStr = datumStr.replaceAll("[^0-9]", "");
                    if (datumStr.length() >= 8) {
                        dokumentDatum = LocalDate.parse(datumStr.substring(0, 8),
                                java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
                    }
                } catch (Exception ignored) {
                }
            }

            if (dokumentNummer == null && betragBrutto == null) {
                return null;
            }

            return org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse.builder()
                    .dokumentTyp(LieferantDokumentTyp.RECHNUNG)
                    .dokumentNummer(dokumentNummer)
                    .dokumentDatum(dokumentDatum)
                    .betragBrutto(betragBrutto)
                    .aiConfidence(1.0)
                    .analyseQuelle("XML")
                    .build();

        } catch (Exception e) {
            log.debug("XML-Preview fehlgeschlagen: {}", e.getMessage());
            return null;
        }
    }

    /**
     * KI-Analyse die ein DTO zurückgibt (für Preview).
     */
    private org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse analysierePerKiFuerPreview(
            Path dateiPfad, String originalFilename, boolean useProModel) {
        try {
            byte[] bytes = Files.readAllBytes(dateiPfad);
            String mimeType = getMimeTypeFromFilename(originalFilename);

            String jsonResponse = rufGeminiApi(bytes, mimeType, useProModel);
            if (jsonResponse == null) {
                return null;
            }

            // JSON parsen und DTO erstellen
            return parseJsonToAnalyzeResponse(jsonResponse);

        } catch (Exception e) {
            log.error("KI-Preview fehlgeschlagen: {}", e.getMessage(), e);
            return null;
        }
    }

    private String getMimeTypeFromFilename(String filename) {
        if (filename == null)
            return "application/pdf";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf"))
            return "application/pdf";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
            return "image/jpeg";
        if (lower.endsWith(".png"))
            return "image/png";
        return "application/pdf";
    }

    private org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse parseJsonToAnalyzeResponse(
            String jsonResponse) {
        try {
            var json = objectMapper.readTree(jsonResponse);

            var builder = org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse.builder();

            // Dokumenttyp
            String typStr = json.has("dokumentTyp") ? json.get("dokumentTyp").asText(null) : null;
            if (typStr != null) {
                typStr = typStr.replace(" (Kopie)", "").trim();
                try {
                    builder.dokumentTyp(LieferantDokumentTyp.valueOf(typStr));
                } catch (Exception e) {
                    builder.dokumentTyp(LieferantDokumentTyp.SONSTIG);
                }
            } else {
                builder.dokumentTyp(LieferantDokumentTyp.RECHNUNG);
            }

            // Dokumentnummer
            if (json.has("dokumentNummer") && !json.get("dokumentNummer").isNull()) {
                builder.dokumentNummer(json.get("dokumentNummer").asText());
            }

            // Datum
            LocalDate parsedDokumentDatum = null;
            if (json.has("dokumentDatum") && !json.get("dokumentDatum").isNull()) {
                parsedDokumentDatum = parseDateFlexibel(json.get("dokumentDatum").asText());
                if (parsedDokumentDatum != null) {
                    builder.dokumentDatum(parsedDokumentDatum);
                } else {
                    log.warn("Konnte dokumentDatum nicht parsen: '{}'", json.get("dokumentDatum").asText());
                }
            }

            // Beträge
            if (json.has("betragNetto") && !json.get("betragNetto").isNull()) {
                builder.betragNetto(new java.math.BigDecimal(json.get("betragNetto").asText()));
            }
            if (json.has("betragBrutto") && !json.get("betragBrutto").isNull()) {
                builder.betragBrutto(new java.math.BigDecimal(json.get("betragBrutto").asText()));
            }
            if (json.has("mwstSatz") && !json.get("mwstSatz").isNull()) {
                builder.mwstSatz(new java.math.BigDecimal(json.get("mwstSatz").asText()));
            }

            // Zahlungsziel
            LocalDate parsedZahlungsziel = null;
            if (json.has("zahlungsziel") && !json.get("zahlungsziel").isNull()) {
                String zahlungszielStr = json.get("zahlungsziel").asText();
                parsedZahlungsziel = parseDateFlexibel(zahlungszielStr);
                if (parsedZahlungsziel != null) {
                    builder.zahlungsziel(parsedZahlungsziel);
                } else {
                    log.warn("Konnte zahlungsziel nicht parsen: '{}' – versuche Fallback über nettoTage", zahlungszielStr);
                }
            }

            // Referenzen
            if (json.has("bestellnummer") && !json.get("bestellnummer").isNull()) {
                builder.bestellnummer(json.get("bestellnummer").asText());
            }
            if (json.has("referenzNummer") && !json.get("referenzNummer").isNull()) {
                builder.referenzNummer(json.get("referenzNummer").asText());
            }

            // Skonto
            Integer parsedNettoTage = null;
            if (json.has("skontoTage") && !json.get("skontoTage").isNull()) {
                builder.skontoTage(json.get("skontoTage").asInt());
            }
            if (json.has("skontoProzent") && !json.get("skontoProzent").isNull()) {
                builder.skontoProzent(new java.math.BigDecimal(json.get("skontoProzent").asText()));
            }
            if (json.has("nettoTage") && !json.get("nettoTage").isNull()) {
                parsedNettoTage = json.get("nettoTage").asInt();
                builder.nettoTage(parsedNettoTage);
            }

            // Fallback: zahlungsziel aus dokumentDatum + nettoTage berechnen
            if (parsedZahlungsziel == null && parsedDokumentDatum != null && parsedNettoTage != null && parsedNettoTage > 0) {
                parsedZahlungsziel = parsedDokumentDatum.plusDays(parsedNettoTage);
                builder.zahlungsziel(parsedZahlungsziel);
                log.info("Zahlungsziel per Fallback berechnet: {} + {} Tage = {}", parsedDokumentDatum, parsedNettoTage, parsedZahlungsziel);
            }

            // Bereits gezahlt
            if (json.has("bereitsGezahlt") && !json.get("bereitsGezahlt").isNull()) {
                builder.bereitsGezahlt(json.get("bereitsGezahlt").asBoolean());
            }
            if (json.has("zahlungsart") && !json.get("zahlungsart").isNull()) {
                builder.zahlungsart(normalizeZahlungsart(json.get("zahlungsart").asText()));
            }

            // Lieferantendaten
            if (json.has("lieferantName") && !json.get("lieferantName").isNull()) {
                builder.lieferantName(json.get("lieferantName").asText());
            }
            if (json.has("lieferantStrasse") && !json.get("lieferantStrasse").isNull()) {
                builder.lieferantStrasse(json.get("lieferantStrasse").asText());
            }
            if (json.has("lieferantPlz") && !json.get("lieferantPlz").isNull()) {
                builder.lieferantPlz(json.get("lieferantPlz").asText());
            }
            if (json.has("lieferantOrt") && !json.get("lieferantOrt").isNull()) {
                builder.lieferantOrt(json.get("lieferantOrt").asText());
            }

            // Confidence
            if (json.has("confidence") && !json.get("confidence").isNull()) {
                builder.aiConfidence(json.get("confidence").asDouble());
            } else {
                builder.aiConfidence(0.8);
            }

            builder.analyseQuelle("KI");

            builder.aiRawJson(jsonResponse);
            return builder.build();

        } catch (Exception e) {
            log.warn("Fehler beim Parsen der KI-Antwort (wird als manuelle Prüfung markiert): {}", e.getMessage());
            return null;
        }
    }

    /**
     * Versucht ein Datum flexibel zu parsen (ISO, deutsches Format, Slash-Format).
     * Gibt null zurück wenn kein Format passt.
     */
    private LocalDate parseDateFlexibel(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        dateStr = dateStr.trim();

        // 1. ISO-Format: YYYY-MM-DD (Standard von der KI erwartet)
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception ignored) {
        }

        // 2. Deutsches Format: dd.MM.yyyy (z.B. "05.03.2026")
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch (Exception ignored) {
        }

        // 3. Slash-Format: dd/MM/yyyy
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception ignored) {
        }

        // 4. Kompaktes Format ohne Trennzeichen: yyyyMMdd
        try {
            if (dateStr.length() == 8 && dateStr.matches("\\d{8}")) {
                return LocalDate.parse(dateStr, DateTimeFormatter.BASIC_ISO_DATE);
            }
        } catch (Exception ignored) {
        }

        log.warn("Datum konnte mit keinem Format geparst werden: '{}'", dateStr);
        return null;
    }


    // REQUIRES_NEW: Jede Dokumentanalyse in eigener Transaktion → sofortiger Commit
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public LieferantGeschaeftsdokument analysiereDokument(LieferantDokument dokument, Path explicitPath) {
        try {
            // Bei REQUIRES_NEW müssen wir das Dokument frisch laden, da es sonst "detached"
            // ist
            LieferantDokument freshDokument = dokumentRepository.findById(dokument.getId()).orElse(null);
            if (freshDokument == null) {
                log.error("Dokument {} nicht gefunden!", dokument.getId());
                return null;
            }

            Path dateiPfad = explicitPath != null ? explicitPath : getDateiPfad(freshDokument);
            if (dateiPfad == null) {
                log.warn("Konnte Datei nicht finden für Dokument {}", freshDokument.getId());
                return null;
            }
            dateiPfad = validiereAnalyseDateiPfad(dateiPfad, explicitPath != null);

            String dateiname = freshDokument.getEffektiverDateiname();
            LieferantGeschaeftsdokument geschaeftsdaten = null;

            // 1. Prüfe auf ZUGFeRD-PDF oder XML
            if (dateiname != null) {
                String lower = dateiname.toLowerCase();

                if (lower.endsWith(".pdf")) {
                    // Versuche ZUGFeRD-Extraktion
                    geschaeftsdaten = versucheZugferdExtraktion(dateiPfad, dateiname, freshDokument);
                } else if (lower.endsWith(".xml")) {
                    // Versuche XML-Extraktion (XRechnung, ZUGFeRD-XML)
                    geschaeftsdaten = versucheXmlExtraktion(
                            leseXmlDateiSicher(dateiPfad, explicitPath != null),
                            freshDokument,
                            dateiPfad.getFileName().toString());
                }
            }

            // 2. Falls keine strukturierten Daten, Fallback auf KI
            if (geschaeftsdaten == null) {
                log.info("Keine ZUGFeRD/XML-Daten gefunden, verwende KI-Analyse für Dokument {}",
                        freshDokument.getId());
                geschaeftsdaten = analysierePerKi(freshDokument, dateiPfad);

                // Preise aus der KI-Auswertung vormerken. Erst hier liegen
                // Artikelpositionen und Lieferant gleichzeitig vor. Geschrieben
                // werden sie nach dem Commit dieser Transaktion.
                if (geschaeftsdaten != null && geschaeftsdaten.getAiRawJson() != null) {
                    try {
                        verarbeiteArtikelPositionen(
                                objectMapper.readTree(geschaeftsdaten.getAiRawJson()),
                                freshDokument.getLieferant(),
                                geschaeftsdaten.getDetectedTyp(),
                                geschaeftsdaten.getDokumentDatum(),
                                geschaeftsdaten.getDokumentNummer());
                    } catch (Exception e) {
                        log.warn("Preisuebernahme aus KI-Analyse fehlgeschlagen für Dokument {}: {}",
                                freshDokument.getId(), e.getMessage());
                    }
                }
            }

            if (geschaeftsdaten == null) {
                log.warn("Konnte keine Metadaten extrahieren für Dokument {}", freshDokument.getId());
                return null;
            }
            // Vor dem Duplikat-Check sichern: dort kann geschaeftsdaten durch das
            // bestehende Geschaeftsdokument ersetzt werden (transientes Feld ginge verloren).
            var ausgelesenePositionen = geschaeftsdaten.getAusgelesenePositionen();

            // Duplikat-Check: Wenn Dokumentnummer existiert, WIEDERVERWENDEN statt löschen
            // Bei PDF+XML-Paar sollen beide Dokumente die gleichen Geschäftsdaten
            // referenzieren
            if (geschaeftsdaten.getDokumentNummer() != null && !geschaeftsdaten.getDokumentNummer().isBlank()) {
                Long lieferantId = freshDokument.getLieferant().getId();
                var existingList = lieferantGeschaeftsdokumentRepository.findByLieferantIdAndDokumentNummer(
                        lieferantId, geschaeftsdaten.getDokumentNummer());
                var existingOpt = existingList.stream()
                        .filter(gd -> !gd.getId().equals(freshDokument.getId()))
                        .findFirst();
                if (existingOpt.isPresent()) {
                    LieferantGeschaeftsdokument bestehendes = existingOpt.get();
                    log.info(
                            "Dokumentnummer {} existiert bereits (ID={}). Aktualisiere statt löschen.",
                            geschaeftsdaten.getDokumentNummer(), bestehendes.getId());

                    // Aktualisiere das bestehende Geschäftsdokument mit neuen Daten (falls besser)
                    // ZUGFeRD/XML hat höhere Priorität als KI, daher Daten übernehmen
                    if (geschaeftsdaten.getAiRawJson() == null) { // Neue Daten kommen von ZUGFeRD/XML
                        // Nur Felder aktualisieren die im neuen besser sind
                        if (geschaeftsdaten.getDokumentDatum() != null) {
                            bestehendes.setDokumentDatum(geschaeftsdaten.getDokumentDatum());
                        }
                        if (geschaeftsdaten.getBetragNetto() != null) {
                            bestehendes.setBetragNetto(geschaeftsdaten.getBetragNetto());
                        }
                        if (geschaeftsdaten.getBetragBrutto() != null) {
                            bestehendes.setBetragBrutto(geschaeftsdaten.getBetragBrutto());
                        }
                        if (geschaeftsdaten.getZahlungsziel() != null) {
                            bestehendes.setZahlungsziel(geschaeftsdaten.getZahlungsziel());
                        }
                        if (geschaeftsdaten.getBestellnummer() != null) {
                            bestehendes.setBestellnummer(geschaeftsdaten.getBestellnummer());
                        }
                        if (geschaeftsdaten.getReferenzNummer() != null) {
                            bestehendes.setReferenzNummer(geschaeftsdaten.getReferenzNummer());
                        }
                        if (geschaeftsdaten.getAiConfidence() != null &&
                                (bestehendes.getAiConfidence() == null ||
                                        geschaeftsdaten.getAiConfidence()
                                                .compareTo(bestehendes.getAiConfidence()) > 0)) {
                            bestehendes.setAiConfidence(geschaeftsdaten.getAiConfidence());
                        }
                        log.info("Geschäftsdaten aktualisiert mit ZUGFeRD/XML-Daten");
                    }

                    // ZUGFeRD/XML-Positionen sind verlässlicher als die der KI: eine
                    // KI-Analyse ersetzt sie nicht.
                    boolean neueDatenVonKi = geschaeftsdaten.getAiRawJson() != null;
                    if (neueDatenVonKi && istStrukturiert(bestehendes)) {
                        ausgelesenePositionen = null;
                    }

                    // Das aktuelle Dokument referenziert die bestehenden Geschäftsdaten
                    geschaeftsdaten = bestehendes;
                }
            }

            // Erkannter Belegtyp hat Vorrang vor der bloßen Nummernheuristik.
            // Einen bereits festgelegten fachlichen Typ bei Reanalyse beibehalten.
            if (freshDokument.getTyp() == null || freshDokument.getTyp() == LieferantDokumentTyp.SONSTIG) {
                LieferantDokumentTyp erkannterTyp = geschaeftsdaten.getDetectedTyp();
                if (erkannterTyp == null) {
                    erkannterTyp = erkenneTypAusNummer(geschaeftsdaten.getDokumentNummer());
                }
                if (erkannterTyp != null) {
                    freshDokument.setTyp(erkannterTyp);
                }
            }

            DokumentBetragsvorzeichen.normalisiereGutschrift(geschaeftsdaten, freshDokument.getTyp());

            // Automatische Verknüpfung
            automatischeVerknuepfung(freshDokument, geschaeftsdaten);

            // WICHTIG: Bei @MapsId funktioniert Cascade NICHT von Parent zu Child!
            // Das Child (geschaeftsdaten) muss ZUERST explizit gespeichert werden.
            // geschaeftsdaten.dokument wurde bereits gesetzt (in
            // parseJsonToGeschaeftsdaten)
            LieferantGeschaeftsdokument savedGeschaeftsdaten = lieferantGeschaeftsdokumentRepository
                    .saveAndFlush(geschaeftsdaten);
            log.debug("LieferantGeschaeftsdokument gespeichert mit ID: {}", savedGeschaeftsdaten.getId());

            // Dann Dokument aktualisieren (referenziert jetzt das gespeicherte
            // geschaeftsdaten)
            freshDokument.setGeschaeftsdaten(savedGeschaeftsdaten);
            dokumentRepository.saveAndFlush(freshDokument);

            // Positionen erst jetzt: sie brauchen die ID der Geschaeftsdaten (@MapsId)
            // und den endgueltigen Typ. Formulare/SONSTIG bekommen keine.
            positionService.ersetzePositionen(savedGeschaeftsdaten, freshDokument.getTyp(), ausgelesenePositionen);

            log.info("Dokument {} erfolgreich analysiert: {} (Quelle: {}, Confidence: {})",
                    freshDokument.getId(),
                    geschaeftsdaten.getDokumentNummer(),
                    geschaeftsdaten.getAiRawJson() != null ? "KI" : "ZUGFeRD/XML",
                    geschaeftsdaten.getAiConfidence());

            // Nachträgliche Verknüpfung in beide Richtungen (z. B. Angebot nach der AB)
            performRelink(freshDokument);

            return savedGeschaeftsdaten;

        } catch (Exception e) {
            log.error("Fehler bei Dokumentanalyse für Dokument {}", dokument.getId(), e);
            return null;
        }
    }

    private static boolean istStrukturiert(LieferantGeschaeftsdokument gd) {
        return "ZUGFERD".equals(gd.getDatenquelle()) || "XML".equals(gd.getDatenquelle());
    }

    /**
     * Versucht ZUGFeRD-Daten aus PDF zu extrahieren.
     */
    private LieferantGeschaeftsdokument versucheZugferdExtraktion(Path dateiPfad, String dateiname,
            LieferantDokument dokument) {
        try {
            ZugferdDaten zugferd = zugferdExtractorService.extract(dateiPfad.toString(), dateiname);

            // Prüfe ob relevante Daten extrahiert wurden
            if (zugferd.getRechnungsnummer() == null && zugferd.getBetrag() == null) {
                log.debug("Keine ZUGFeRD-Daten in PDF gefunden: {}", dateiname);
                return null;
            }

            log.info("ZUGFeRD-Daten gefunden in {}: Nr={}, Betrag={}",
                    dateiname, zugferd.getRechnungsnummer(), zugferd.getBetrag());

            // Existierendes Geschaeftsdokument wiederverwenden oder neues erstellen
            LieferantGeschaeftsdokument gd = null;
            if (dokument != null) {
                gd = dokument.getGeschaeftsdaten();
            }

            if (gd == null) {
                gd = new LieferantGeschaeftsdokument();
                if (dokument != null) {
                    gd.setDokument(dokument);
                }
            }
            gd.setDokumentNummer(zugferd.getRechnungsnummer());
            gd.setDokumentDatum(zugferd.getRechnungsdatum());
            gd.setBetragBrutto(zugferd.getBetrag());
            gd.setAiConfidence(1.0); // Strukturierte Daten = 100% Confidence
            gd.setAnalysiertAm(LocalDateTime.now());
            gd.setDatenquelle("ZUGFERD");
            gd.setVerifiziert(true);

            // Erweiterte ZUGFeRD-Felder (Skonto, Netto, Zahlungsziel)
            if (zugferd.getFaelligkeitsdatum() != null) {
                gd.setZahlungsziel(zugferd.getFaelligkeitsdatum());
            }
            if (zugferd.getBetragNetto() != null) {
                gd.setBetragNetto(zugferd.getBetragNetto());
            }
            if (zugferd.getMwstSatz() != null) {
                gd.setMwstSatz(zugferd.getMwstSatz());
            }
            if (zugferd.getSkontoTage() != null) {
                gd.setSkontoTage(zugferd.getSkontoTage());
            }
            if (zugferd.getSkontoProzent() != null) {
                gd.setSkontoProzent(zugferd.getSkontoProzent());
            }
            if (zugferd.getNettoTage() != null) {
                gd.setNettoTage(zugferd.getNettoTage());
            }
            if (zugferd.getBestellnummer() != null) {
                gd.setBestellnummer(zugferd.getBestellnummer());
            }
            if (zugferd.getReferenzNummer() != null) {
                gd.setReferenzNummer(zugferd.getReferenzNummer());
            }

            // Bereits bezahlt (z.B. Amazon, Vorauskasse): nicht mehr in Offene Posten zeigen
            if (Boolean.TRUE.equals(zugferd.getBereitsGezahlt())) {
                gd.setBereitsGezahlt(true);
                gd.setBezahlt(true); // Wenn bereits gezahlt, setze auch bezahlt-Flag
                // Das echte Zahldatum liefert die ZUGFeRD-Rechnung nicht; bei Vorauskasse/Amazon
                // ist die Zahlung zum Rechnungsdatum erfolgt -> als Bezahldatum übernehmen.
                if (zugferd.getRechnungsdatum() != null) {
                    gd.setBezahltAm(zugferd.getRechnungsdatum());
                }
            }

            // Dokumenttyp aus ZUGFeRD-Daten
            if (zugferd.getGeschaeftsdokumentart() != null) {
                LieferantDokumentTyp typ = switch (zugferd.getGeschaeftsdokumentart()) {
                    case "Rechnung" -> LieferantDokumentTyp.RECHNUNG;
                    case "Angebot" -> LieferantDokumentTyp.ANGEBOT;
                    case "Auftragsbestätigung" -> LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG;
                    case "Gutschrift" -> LieferantDokumentTyp.GUTSCHRIFT;
                    case "Lieferschein" -> LieferantDokumentTyp.LIEFERSCHEIN;
                    default -> null;
                };
                if (typ != null) {
                    // Setze detectedTyp auf dem Geschäftsdokument (transient)
                    gd.setDetectedTyp(typ);
                    // Überschreibe auch SONSTIG (Default-Typ) mit erkanntem Typ
                    if (dokument != null && (dokument.getTyp() == null
                            || dokument.getTyp() == LieferantDokumentTyp.SONSTIG)) {
                        dokument.setTyp(typ);
                    }
                }
            }

            // Artikelpositionen verarbeiten und Preise aktualisieren.
            //
            // Bewusst nicht gd.getDetectedTyp(): der stammt aus
            // zugferd.getGeschaeftsdokumentart(), und das faellt auf "Rechnung"
            // zurueck, sobald der Dateiname nichts hergibt. Ein ZUGFeRD-Lieferavis
            // namens "Avis_4711.pdf" wuerde darueber Preise schreiben. Massgeblich
            // ist allein der TypeCode, den die Datei selbst ausweist.
            if (dokument != null) {
                verarbeiteStrukturiertePositionen(zugferd.getArtikelPositionen(), dokument.getLieferant(),
                        ausgewiesenerTyp(zugferd.getTypeCode()), gd.getDokumentDatum(),
                        gd.getDokumentNummer());
            }
            gd.setAusgelesenePositionen(LieferantDokumentPositionService.ausZugferd(zugferd.getArtikelPositionen()));

            return gd;

        } catch (Exception e) {
            log.debug("ZUGFeRD-Extraktion fehlgeschlagen für {}: {}", dateiname, e.getMessage());
            return null;
        }
    }

    /**
     * Versucht XML-Rechnung zu parsen (z.B. XRechnung, ZUGFeRD-XML).
     */
    private LieferantGeschaeftsdokument versucheXmlExtraktion(Path dateiPfad, LieferantDokument dokument) {
        try {
            return versucheXmlExtraktion(
                    leseXmlDateiSicher(dateiPfad, true),
                    dokument,
                    dateiPfad != null ? dateiPfad.getFileName().toString() : "unbekannt");
        } catch (Exception e) {
            log.debug("XML-Extraktion fehlgeschlagen für {}: {}",
                    dateiPfad != null ? dateiPfad.getFileName() : "unbekannt",
                    e.getMessage());
            return null;
        }
    }

    private LieferantGeschaeftsdokument versucheXmlExtraktion(String xmlContent, LieferantDokument dokument,
            String dateinameFuerLog) {
        try {
            // Einfache Prüfung ob es eine Rechnungs-XML ist
            if (!xmlContent.contains("Invoice") && !xmlContent.contains("CrossIndustryInvoice")) {
                log.debug("XML ist keine Rechnung: {}", dateinameFuerLog);
                return null;
            }

            log.info("XML-Rechnung gefunden: {}", dateinameFuerLog);

            // Existierendes Geschaeftsdokument wiederverwenden oder neues erstellen
            LieferantGeschaeftsdokument gd = null;
            if (dokument != null) {
                gd = dokument.getGeschaeftsdaten();
            }
            if (gd == null) {
                gd = new LieferantGeschaeftsdokument();
                if (dokument != null) {
                    gd.setDokument(dokument);
                }
            }
            gd.setAiConfidence(1.0); // Strukturierte Daten
            gd.setDatenquelle("XML");
            gd.setAnalysiertAm(LocalDateTime.now());

            // Dokumentnummer extrahieren
            gd.setDokumentNummer(extractXmlValue(xmlContent, "ID", "InvoiceNumber", "ram:ID"));

            // Bruttobetrag (Gesamtsumme inkl. MwSt).
            // UBL: TaxInclusiveAmount/PayableAmount, CII: GrandTotalAmount.
            BigDecimal brutto = parseXmlBetrag(extractXmlValue(xmlContent,
                    "GrandTotalAmount", "PayableAmount", "TaxInclusiveAmount", "ram:GrandTotalAmount"));
            if (brutto != null) {
                gd.setBetragBrutto(brutto);
            }

            // Nettobetrag (Summe ohne MwSt).
            // UBL: TaxExclusiveAmount, CII: TaxBasisTotalAmount.
            BigDecimal netto = parseXmlBetrag(extractXmlValue(xmlContent,
                    "TaxBasisTotalAmount", "TaxExclusiveAmount", "ram:TaxBasisTotalAmount"));

            // MwSt-Satz (z.B. 19.00 -> 0.19, konsistent zur ZUGFeRD-Extraktion).
            BigDecimal mwstSatz = parseXmlBetrag(extractXmlValue(xmlContent,
                    "RateApplicablePercent", "ApplicablePercent", "Percent", "ram:RateApplicablePercent"));
            if (mwstSatz != null && mwstSatz.compareTo(BigDecimal.ONE) > 0) {
                mwstSatz = mwstSatz.movePointLeft(2);
            }
            if (mwstSatz != null) {
                gd.setMwstSatz(mwstSatz);
            }

            // Netto notfalls aus Brutto + MwSt-Satz berechnen.
            if (netto == null && brutto != null && mwstSatz != null
                    && mwstSatz.compareTo(BigDecimal.ZERO) > 0) {
                netto = brutto.divide(BigDecimal.ONE.add(mwstSatz), 2, java.math.RoundingMode.HALF_UP);
            }
            if (netto != null) {
                gd.setBetragNetto(netto);
            }

            // Rechnungsdatum.
            LocalDate dokumentDatum = parseXmlDatum(
                    extractXmlValue(xmlContent, "IssueDate", "IssueDateTime", "ram:DateTimeString"));
            if (dokumentDatum != null) {
                gd.setDokumentDatum(dokumentDatum);
            }

            // Fälligkeitsdatum / Zahlungsziel (UBL: DueDate).
            LocalDate faelligkeit = parseXmlDatum(extractXmlValue(xmlContent, "DueDate"));
            if (faelligkeit != null) {
                gd.setZahlungsziel(faelligkeit);
            }

            // Dokumenttyp aus XML-Inhalt bestimmen (TypeCode per extractXmlValue, unterstützt Namespace-Prefixe)
            LieferantDokumentTyp xmlTyp = LieferantDokumentTyp.RECHNUNG;
            String typeCode = extractXmlValue(xmlContent, "TypeCode", "ram:TypeCode");
            if ("381".equals(typeCode) || xmlContent.contains("CreditNote")) {
                xmlTyp = LieferantDokumentTyp.GUTSCHRIFT;
            }
            gd.setDetectedTyp(xmlTyp);
            if (dokument != null && (dokument.getTyp() == null
                    || dokument.getTyp() == LieferantDokumentTyp.SONSTIG)) {
                dokument.setTyp(xmlTyp);
            }

            // Taugt die XML ueberhaupt? Wenn nicht, faellt die Analyse gleich auf die
            // KI zurueck - dann darf hier auch kein Preis geschrieben werden, sonst
            // laege derselbe Beleg zweimal in der Historie, einmal je Lesart.
            if (gd.getDokumentNummer() == null && gd.getBetragBrutto() == null) {
                return null;
            }

            // Auch die reine XRechnung fuehrt ihre Positionen mit - vorher wurden hier
            // nur die Kopfdaten gelesen und kein Preis aktualisiert.
            //
            // Fuer die Preisuebernahme zaehlt aber nur ein ausdruecklich ausgewiesener
            // Typ, nicht die Annahme "Rechnung" von oben. Sonst laege jede XML ohne
            // TypeCode als Rechnung vor - auch ein Lieferschein (270) oder eine
            // Auftragsbestaetigung (231), also genau das, was die Typpruefung in
            // preisQuelleFuer aussperren soll.
            var xmlPositionen = zugferdExtractorService.extractLineItems(xmlContent);
            if (dokument != null) {
                verarbeiteStrukturiertePositionen(xmlPositionen,
                        dokument.getLieferant(), ausgewiesenerTyp(typeCode), gd.getDokumentDatum(),
                        gd.getDokumentNummer());
            }
            gd.setAusgelesenePositionen(LieferantDokumentPositionService.ausZugferd(xmlPositionen));

            return gd;

        } catch (Exception e) {
            log.debug("XML-Extraktion fehlgeschlagen: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Einfache XML-Wert-Extraktion für mehrere mögliche Tag-Namen.
     */
    private String extractXmlValue(String xml, String... tagNames) {
        for (String tag : tagNames) {
            // Versuche verschiedene Formate.
            // (?:\\s[^>]*)? erlaubt Attribute am Tag, z.B. <cbc:PayableAmount currencyID="EUR">.
            // UBL/XRechnung führt Beträge IMMER mit currencyID-Attribut -> ohne diese
            // Toleranz blieben Netto/Brutto leer (Bug: Offene Posten zeigten 0,00 €).
            String[] patterns = {
                    "<" + tag + "(?:\\s[^>]*)?>([^<]+)</" + tag + ">",
                    "<[^:]+:" + tag + "(?:\\s[^>]*)?>([^<]+)</[^:]+:" + tag + ">"
            };
            for (String pattern : patterns) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                java.util.regex.Matcher m = p.matcher(xml);
                if (m.find()) {
                    return m.group(1).trim();
                }
            }
        }
        return null;
    }

    /**
     * Parst einen Betrag aus einem XML-Wert (z.B. "867.27" oder "867,27").
     * Gibt {@code null} zurück, wenn der Wert fehlt oder nicht parsbar ist.
     */
    private BigDecimal parseXmlBetrag(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parst ein Datum aus einem XML-Wert. Akzeptiert ISO (2026-05-29) und
     * BASIC_ISO (20260529); nicht-numerische Zeichen werden entfernt.
     */
    private LocalDate parseXmlDatum(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            String digits = raw.replaceAll("[^0-9]", "");
            if (digits.length() >= 8) {
                return LocalDate.parse(digits.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
            }
        } catch (Exception ignored) {
            // ungültiges Datum -> null
        }
        return null;
    }

    /**
     * Fallback: Analyse per Gemini AI mit Validierung und Retry bei unvollständigen
     * Daten. Bei fehlgeschlagener Validierung wird das Dokument trotzdem
     * gespeichert,
     * aber mit manuellePruefungErforderlich=true markiert.
     */
    /**
     * Führt die Analyse durch und gibt das Ergebnis zurück, OHNE Änderungen in der
     * DB zu speichern.
     * Nutzt ZUGFeRD oder KI.
     */
    public LieferantGeschaeftsdokument analyzeAndReturnData(Path dateiPfad, String originalDateiname) {
        try {
            dateiPfad = validiereAnalyseDateiPfad(dateiPfad, true);
            String lower = originalDateiname != null ? originalDateiname.toLowerCase() : "";

            // 1. ZUGFeRD (nur PDFs - XML ist in PDF eingebettet)
            if (lower.endsWith(".pdf")) {
                LieferantGeschaeftsdokument zugferd = versucheZugferdExtraktion(dateiPfad, originalDateiname, null);
                if (zugferd != null)
                    return zugferd;
            }

            // 2. Standalone XML (XRechnung, ZUGFeRD-XML)
            if (lower.endsWith(".xml")) {
                LieferantGeschaeftsdokument xmlResult = versucheXmlExtraktion(
                        leseXmlDateiSicher(dateiPfad, true),
                        null,
                        dateiPfad.getFileName().toString());
                if (xmlResult != null)
                    return xmlResult;
            }

            // 3. Fallback: KI-Analyse (für PDFs und Bilder)
            String mimeType = "application/pdf";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
                mimeType = "image/jpeg";
            else if (lower.endsWith(".png"))
                mimeType = "image/png";
            else if (lower.endsWith(".xml"))
                mimeType = "application/xml";

            return analysierePerKi(dateiPfad, mimeType, null);

        } catch (Exception e) {
            log.error("Fehler bei analyzeAndReturnData: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Fallback: Analyse per Gemini AI mit Validierung und Retry bei unvollständigen
     * Daten. Bei fehlgeschlagener Validierung wird das Dokument trotzdem
     * gespeichert,
     * aber mit manuellePruefungErforderlich=true markiert.
     */
    private LieferantGeschaeftsdokument analysierePerKi(LieferantDokument dokument, Path dateiPfad) {
        return analysierePerKi(dateiPfad, getMimeType(dokument), dokument != null ? dokument.getId() : null);
    }

    private LieferantGeschaeftsdokument analysierePerKi(Path dateiPfad, String mimeType, Long docIdForLog) {
        try {
            byte[] bytes = Files.readAllBytes(dateiPfad);
            // mimeType ist bereits übergeben

            // Erster Versuch mit Standard-Model (Retry-Logik ist in rufGeminiApi eingebaut)
            KiAntwort ersteAntwort = rufGeminiApiMitStatus(bytes, mimeType, false, false);
            String jsonResponse = ersteAntwort != null ? ersteAntwort.text() : null;
            // true, sobald die verwendete Antwort ohne Positionen angefordert wurde
            boolean ohnePositionen = false;
            if (jsonResponse == null) {
                log.warn(
                        "[KI-Analyse] Keine API-Antwort für Dokument {} - erstelle leeres Geschäftsdokument zur manuellen Prüfung",
                        docIdForLog);
                // Erstelle leeres Geschäftsdokument mit Flag für manuelle Prüfung
                LieferantGeschaeftsdokument emptyResult = new LieferantGeschaeftsdokument();
                // emptyResult.setDokument(dokument); // NICHT SETZEN, da wir noch kein Dokument
                // haben!
                emptyResult.setManuellePruefungErforderlich(true);
                emptyResult.setDatenquelle("AI_FAILED");
                emptyResult.setAnalysiertAm(java.time.LocalDateTime.now());
                return emptyResult;
            }

            // Prüfe ob JSON abgeschnitten wurde (häufig bei vielen Artikelpositionen).
            // Gemini meldet das selbst (MAX_TOKENS); die Klammerprüfung bleibt als Rückfall.
            boolean jsonTruncated = ersteAntwort.abgeschnitten() || isJsonTruncated(jsonResponse);
            if (jsonTruncated) {
                log.warn(
                        "[KI-Analyse] JSON-Antwort wurde abgeschnitten für Dokument {}. Kopfdaten ohne Positionen, Positionen seitenweise...",
                        docIdForLog);
                // Retry mit Flag um Artikelpositionen zu überspringen
                jsonResponse = rufGeminiApi(bytes, mimeType, false, true); // skipArtikelPositionen=true
                if (jsonResponse == null || isJsonTruncated(jsonResponse)) {
                    log.warn("[KI-Analyse] Auch vereinfachte Anfrage abgeschnitten - versuche Pro-Model...");
                    jsonResponse = rufGeminiApi(bytes, mimeType, true, true); // Pro-Model + skipArtikelPositionen
                }
                ohnePositionen = true;
            }

            LieferantGeschaeftsdokument result = mapJsonToData(jsonResponse);

            // Wenn Parsing fehlgeschlagen, prüfe ob JSON abgeschnitten und retry
            if (result == null) {
                if (isJsonTruncated(jsonResponse)) {
                    log.warn(
                            "[KI-Analyse] JSON nach wie vor abgeschnitten - versuche nochmal mit Pro-Model ohne Artikel...");
                    jsonResponse = rufGeminiApi(bytes, mimeType, true, true);
                    result = jsonResponse != null ? mapJsonToData(jsonResponse) : null;
                    ohnePositionen = true;
                } else {
                    // isJsonTruncated() kann false-negative liefern wenn Gemini bei Token-Limit
                    // eine schließende } anhängt, der JSON-Inhalt aber intern unvollständig ist.
                    // In diesem Fall noch einmal mit Pro-Model versuchen.
                    log.warn(
                            "[KI-Analyse] JSON-Parsing fehlgeschlagen für Dokument {} trotz scheinbar vollständiger Antwort - Pro-Model Fallback...",
                            docIdForLog);
                    KiAntwort proAntwort = rufGeminiApiMitStatus(bytes, mimeType, true, false);
                    if (proAntwort != null && !proAntwort.abgeschnitten()) {
                        result = mapJsonToData(proAntwort.text());
                        ohnePositionen = false;
                    }
                }

                if (result == null) {
                    log.warn(
                            "[KI-Analyse] JSON-Parsing fehlgeschlagen für Dokument {} - erstelle zur manuellen Prüfung",
                            docIdForLog);
                    result = new LieferantGeschaeftsdokument();
                    result.setManuellePruefungErforderlich(true);
                    result.setDatenquelle("AI_PARSE_FAILED");
                    result.setAiRawJson(jsonResponse);
                    result.setAnalysiertAm(java.time.LocalDateTime.now());
                    return result;
                }
            }

            // Validiere die extrahierten Daten
            // Typ haben wir hier potentiell noch nicht sicher, wir validieren allgemein auf
            // Nummer
            boolean validierungBestanden = validiereGeschaeftsdaten(result, null);

            if (!validierungBestanden) {
                log.warn(
                        "[KI-Analyse] Datenvalidierung fehlgeschlagen für Dokument {} (Typ unbekannt). Versuche mit Pro-Model...",
                        docIdForLog);

                // Retry mit Pro-Model; eine abgeschnittene Antwort hätte eine halbe Positionsliste
                KiAntwort proAntwort = rufGeminiApiMitStatus(bytes, mimeType, true, false);
                jsonResponse = proAntwort != null && !proAntwort.abgeschnitten() ? proAntwort.text() : null;
                if (jsonResponse != null) {
                    LieferantGeschaeftsdokument retryResult = mapJsonToData(jsonResponse);
                    if (retryResult != null && validiereGeschaeftsdaten(retryResult, null)) {
                        log.info("[KI-Analyse] Pro-Model erfolgreich - Datenvalidierung bestanden");
                        retryResult.setManuellePruefungErforderlich(false);
                        return retryResult;
                    }
                    // Pro-Model hat auch nicht alle Daten - benutze das bessere Ergebnis
                    if (retryResult != null) {
                        result = retryResult;
                        ohnePositionen = false;
                    }
                }

                // Beide Models konnten nicht alle Pflichtfelder extrahieren - Flag setzen
                log.warn(
                        "[KI-Analyse] Auch Pro-Model konnte keine vollständigen Daten extrahieren - markiere zur manuellen Prüfung");
                result.setManuellePruefungErforderlich(true);
            } else {
                // Validierung bestanden
                result.setManuellePruefungErforderlich(false);
            }

            if (ohnePositionen) {
                ergaenzePositionenSeitenweise(result, bytes, mimeType, docIdForLog);
            }
            return result;

        } catch (Exception e) {
            log.error("KI-Analyse fehlgeschlagen für Dokument {}", docIdForLog, e);
            // Auch bei Exception: Dokument mit Flag erstellen
            LieferantGeschaeftsdokument errorResult = new LieferantGeschaeftsdokument();
            errorResult.setManuellePruefungErforderlich(true);
            errorResult.setDatenquelle("AI_ERROR");
            errorResult.setAnalysiertAm(java.time.LocalDateTime.now());
            return errorResult;
        }
    }

    /**
     * Die Kopfdaten kamen ohne Positionen (Antwort war zu lang). Statt sie ganz
     * wegzulassen, liest der {@link LieferantDokumentPositionLeser} sie
     * seitenweise nach und schreibt sie in die KI-Antwort – dort liest sie auch
     * die Preisübernahme. Gelingt das nicht vollständig, bleibt es bei "nicht
     * ausgelesen" ({@code null}); eine halbe Liste wird nie gespeichert.
     */
    private void ergaenzePositionenSeitenweise(LieferantGeschaeftsdokument gd, byte[] bytes, String mimeType,
            Long docIdForLog) {
        gd.setAusgelesenePositionen(null);
        if (!LieferantDokumentPositionService.hatPositionen(gd.getDetectedTyp())) {
            return;
        }
        List<AusgelesenePosition> positionen =
                positionLeser.leseSeitenweise(bytes, mimeType, this::rufNurPositionen);
        if (positionen == null) {
            log.warn("[KI-Analyse] Positionen für Dokument {} nicht vollständig lesbar – später nachlesbar",
                    docIdForLog);
            return;
        }
        String mitPositionen = LieferantDokumentPositionService.mitPositionen(gd.getAiRawJson(), positionen,
                objectMapper);
        if (mitPositionen != null) {
            gd.setAiRawJson(mitPositionen);
        }
        gd.setAusgelesenePositionen(positionen);
        log.info("[KI-Analyse] Dokument {}: {} Positionen seitenweise gelesen", docIdForLog, positionen.size());
    }

    /** Dokumente, deren Positionen gerade nachgelesen werden – je Dokument nur ein Lauf. */
    private final java.util.Set<Long> positionenInArbeit = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Liest die Positionen eines Dokuments nachträglich per KI – für ältere
     * Dokumente oder wenn die Analyse sie nicht vollständig liefern konnte.
     * Kopfdaten (Nummer, Beträge, Typ) bleiben unverändert.
     *
     * <p>Die KI-Aufrufe laufen bewusst <b>außerhalb</b> einer Transaktion: Sie
     * können bei langen Belegen Minuten dauern und hielten sonst so lange eine
     * Datenbankverbindung. Nur das Laden der Datei und das Speichern sind kurze
     * Transaktionen.
     *
     * @return Anzahl gespeicherter Positionen
     * @throws java.util.NoSuchElementException wenn das Dokument fehlt
     * @throws IllegalArgumentException wenn das Dokument keine Positionen haben kann
     * @throws PositionenNichtLesbarException wenn Datei oder KI-Antwort nicht verfügbar
     *                                  sind oder schon ein Lauf für das Dokument läuft
     */
    public int positionenNachlesen(Long dokumentId) {
        if (!positionenInArbeit.add(dokumentId)) {
            throw new PositionenNichtLesbarException("Die Positionen werden gerade schon ausgelesen");
        }
        try {
            NachleseVorlage vorlage = transactionTemplate.execute(status -> ladeNachleseVorlage(dokumentId));
            List<AusgelesenePosition> positionen =
                    positionLeser.lese(vorlage.bytes(), vorlage.mimeType(), this::rufNurPositionen);
            if (positionen == null) {
                throw new PositionenNichtLesbarException("Die KI konnte die Positionen nicht vollständig lesen");
            }
            Integer anzahl = transactionTemplate.execute(status -> speichereNachgelesenePositionen(dokumentId, positionen));
            return anzahl != null ? anzahl : 0;
        } finally {
            positionenInArbeit.remove(dokumentId);
        }
    }

    private record NachleseVorlage(byte[] bytes, String mimeType) {
    }

    private NachleseVorlage ladeNachleseVorlage(Long dokumentId) {
        LieferantDokument dokument = dokumentRepository.findById(dokumentId)
                .orElseThrow(() -> new NoSuchElementException("Dokument nicht gefunden"));
        pruefeNachlesbar(dokument);
        Path dateiPfad = getDateiPfad(dokument);
        if (dateiPfad == null) {
            throw new PositionenNichtLesbarException("Datei zum Dokument nicht gefunden");
        }
        try {
            return new NachleseVorlage(Files.readAllBytes(validiereAnalyseDateiPfad(dateiPfad, false)),
                    getMimeType(dokument));
        } catch (java.io.IOException | IllegalArgumentException | SecurityException e) {
            // Keine Details nach außen: die Meldungen nennen den Serverpfad.
            throw new PositionenNichtLesbarException("Datei zum Dokument nicht lesbar");
        }
    }

    private int speichereNachgelesenePositionen(Long dokumentId, List<AusgelesenePosition> positionen) {
        LieferantDokument dokument = dokumentRepository.findById(dokumentId)
                .orElseThrow(() -> new NoSuchElementException("Dokument nicht gefunden"));
        pruefeNachlesbar(dokument); // könnte sich während des KI-Laufs geändert haben
        LieferantGeschaeftsdokument gd = dokument.getGeschaeftsdaten();
        String mitPositionen = LieferantDokumentPositionService.mitPositionen(gd.getAiRawJson(), positionen,
                objectMapper);
        if (mitPositionen != null) {
            gd.setAiRawJson(mitPositionen);
            lieferantGeschaeftsdokumentRepository.save(gd);
        }
        return positionService.ersetzePositionen(gd, dokument.getTyp(), positionen);
    }

    private static void pruefeNachlesbar(LieferantDokument dokument) {
        if (dokument.getGeschaeftsdaten() == null) {
            throw new IllegalArgumentException("Dokument ist noch nicht analysiert");
        }
        if (!LieferantDokumentPositionService.hatPositionen(dokument.getTyp())) {
            throw new IllegalArgumentException("Für diesen Dokumenttyp werden keine Positionen ausgelesen");
        }
    }

    /**
     * Validiert ob die extrahierten Geschäftsdaten vollständig sind basierend auf
     * dem Dokumenttyp.
     * 
     * Pflichtfelder je Typ:
     * - RECHNUNG: Dokumentnummer + (BetragBrutto ODER BetragNetto)
     * - LIEFERSCHEIN: Dokumentnummer
     * - WERKSTOFFZEUGNIS: Dokumentnummer (Zeugnisse tragen keine Beträge)
     * - ANGEBOT: Dokumentnummer + (BetragBrutto ODER BetragNetto)
     * - AUFTRAGSBESTAETIGUNG: Dokumentnummer
     * - GUTSCHRIFT: Dokumentnummer + (BetragBrutto ODER BetragNetto)
     * - SONSTIG: keine Pflichtfelder (darf null sein)
     */
    private boolean validiereGeschaeftsdaten(LieferantGeschaeftsdokument gd, LieferantDokumentTyp typ) {
        if (gd == null) {
            return false;
        }

        // Dokumentnummer ist für fast alle Typen Pflicht
        boolean hatDokumentNummer = gd.getDokumentNummer() != null && !gd.getDokumentNummer().isBlank();
        boolean hatBetrag = gd.getBetragBrutto() != null || gd.getBetragNetto() != null;

        if (typ == null) {
            // Wenn Typ unbekannt, mindestens Dokumentnummer prüfen
            if (!hatDokumentNummer) {
                log.debug("[Validierung] Fehlend: Dokumentnummer (Typ unbekannt)");
                return false;
            }
            return true;
        }

        switch (typ) {
            case RECHNUNG:
                // Rechnung: Dokumentnummer + Betrag erforderlich
                if (!hatDokumentNummer) {
                    log.debug("[Validierung] RECHNUNG: Dokumentnummer fehlt");
                    return false;
                }
                if (!hatBetrag) {
                    log.debug("[Validierung] RECHNUNG: Kein Betrag (Brutto oder Netto) erkannt");
                    return false;
                }
                return true;

            case LIEFERSCHEIN:
            case WERKSTOFFZEUGNIS:
                // Lieferschein und Werkstoffzeugnis: Nur Dokumentnummer (keine Beträge)
                if (!hatDokumentNummer) {
                    log.debug("[Validierung] {}: Dokumentnummer fehlt", typ);
                    return false;
                }
                return true;

            case ANGEBOT:
                // Angebot: Dokumentnummer + Betrag (damit man vergleichen kann)
                if (!hatDokumentNummer) {
                    log.debug("[Validierung] ANGEBOT: Dokumentnummer fehlt");
                    return false;
                }
                if (!hatBetrag) {
                    log.debug("[Validierung] ANGEBOT: Kein Betrag erkannt");
                    return false;
                }
                return true;

            case AUFTRAGSBESTAETIGUNG:
                // AB: Dokumentnummer erforderlich
                if (!hatDokumentNummer) {
                    log.debug("[Validierung] AUFTRAGSBESTAETIGUNG: Dokumentnummer fehlt");
                    return false;
                }
                return true;

            case GUTSCHRIFT:
                // Gutschrift: Dokumentnummer + Betrag
                if (!hatDokumentNummer) {
                    log.debug("[Validierung] GUTSCHRIFT: Dokumentnummer fehlt");
                    return false;
                }
                if (!hatBetrag) {
                    log.debug("[Validierung] GUTSCHRIFT: Kein Betrag erkannt");
                    return false;
                }
                return true;

            case SONSTIG:
                // Sonstige Dokumente haben keine Pflichtfelder
                return true;

            default:
                // Unbekannter Typ - mindestens Dokumentnummer
                return hatDokumentNummer;
        }
    }

    private Path getDateiPfad(LieferantDokument dokument) {
        String dateiname = dokument.getEffektiverGespeicherterDateiname();
        if (dateiname == null)
            return null;

        Long lieferantId = dokument.getLieferant().getId();

        // 1. E-Mail-Attachments: uploads/attachments/ (flat structure - most common)
        Path pfad = pruefeUploadDateiPfad(Path.of(uploadPath, "attachments", dateiname));
        if (pfad != null) {
            return pfad;
        }

        // 2. E-Mail-Attachments: uploads/attachments/lieferanten/{lieferantId}/
        pfad = pruefeUploadDateiPfad(Path.of(uploadPath, "attachments", "lieferanten", lieferantId.toString(), dateiname));
        if (pfad != null) {
            return pfad;
        }

        // 3. Manuell hochgeladene Dokumente: uploads/lieferanten/{lieferantId}/
        pfad = pruefeUploadDateiPfad(Path.of(uploadPath, "lieferanten", lieferantId.toString(), dateiname));
        if (pfad != null) {
            return pfad;
        }

        // 4. Vendor-Invoices: uploads/attachments/vendor-invoices/
        pfad = pruefeUploadDateiPfad(Path.of(uploadPath, "attachments", "vendor-invoices", dateiname));
        if (pfad != null) {
            return pfad;
        }

        // 5. Fallback: uploads/lieferant-emails/
        pfad = pruefeUploadDateiPfad(Path.of(uploadPath, "lieferant-emails", dateiname));
        if (pfad != null) {
            return pfad;
        }

        // 6. Fallback: uploads/email/
        pfad = pruefeUploadDateiPfad(Path.of(uploadPath, "email", dateiname));
        if (pfad != null) {
            return pfad;
        }

        log.warn("Datei nicht gefunden: {} (geprüft: attachments/, attachments/lieferanten/{}, lieferanten/{})",
                dateiname, lieferantId, lieferantId);
        return null;
    }

    private String leseXmlDateiSicher(Path dateiPfad, boolean tempDateienErlaubt) throws java.io.IOException {
        Path sichererPfad = validiereAnalyseDateiPfad(dateiPfad, tempDateienErlaubt);
        // Zeichensatz aus BOM bzw. XML-Deklaration – XRechnungen kommen auch als ISO-8859-1.
        return ZugferdExtractorService.dekodiereXml(Files.readAllBytes(sichererPfad));
    }

    private Path validiereAnalyseDateiPfad(Path dateiPfad, boolean tempDateienErlaubt) {
        Path normalizedPath = dateiPfad.toAbsolutePath().normalize();
        Path uploadRoot = getUploadRootPath();

        boolean erlaubterPfad = normalizedPath.startsWith(uploadRoot);
        if (!erlaubterPfad && tempDateienErlaubt) {
            Path tempRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
            erlaubterPfad = normalizedPath.startsWith(tempRoot);
        }

        if (!erlaubterPfad) {
            throw new SecurityException("Dateizugriff außerhalb der erlaubten Verzeichnisse: " + normalizedPath);
        }

        if (!Files.isRegularFile(normalizedPath)) {
            throw new IllegalArgumentException("Datei ist nicht lesbar oder existiert nicht: " + normalizedPath);
        }

        return normalizedPath;
    }

    private Path pruefeUploadDateiPfad(Path pfad) {
        Path normalizedPath = pfad.toAbsolutePath().normalize();
        Path uploadRoot = getUploadRootPath();
        if (!normalizedPath.startsWith(uploadRoot)) {
            log.warn("Verwerfe ungültigen Upload-Pfad außerhalb von {}: {}", uploadRoot, normalizedPath);
            return null;
        }
        if (!Files.isRegularFile(normalizedPath)) {
            return null;
        }
        log.debug("Datei gefunden unter: {}", normalizedPath);
        return normalizedPath;
    }

    private Path getUploadRootPath() {
        String configuredUploadPath = (uploadPath == null || uploadPath.isBlank()) ? "uploads" : uploadPath;
        return Path.of(configuredUploadPath).toAbsolutePath().normalize();
    }

    /**
     * Prüft ob eine E-Mail-Domain zu einem bekannten Lieferanten gehört.
     * Extrahiert die Domain aus der E-Mail-Adresse und sucht passende Lieferanten.
     */
    public Optional<Lieferanten> findeLieferantByEmailDomain(String emailAddress) {
        if (emailAddress == null || !emailAddress.contains("@")) {
            return Optional.empty();
        }

        String domain = emailAddress.substring(emailAddress.lastIndexOf("@") + 1).toLowerCase();
        List<Lieferanten> lieferanten = lieferantenRepository.findByEmailDomain(domain);

        if (lieferanten.isEmpty()) {
            log.debug("Kein Lieferant gefunden für Domain: {}", domain);
            return Optional.empty();
        }

        if (lieferanten.size() > 1) {
            log.warn("Mehrere Lieferanten gefunden für Domain {}: {}", domain, lieferanten.size());
        }

        return Optional.of(lieferanten.getFirst());
    }

    /**
     * Hängt die Vorgänger eines Dokuments an (Angebot ← AB ← Lieferschein ←
     * Rechnung ← Gutschrift). Entschieden wird im {@link LieferantDokumentAbgleich}.
     */
    private void automatischeVerknuepfung(LieferantDokument dokument, LieferantGeschaeftsdokument geschaeftsdaten) {
        if (dokument.getLieferant() == null) {
            log.warn("[Verknüpfung] Dokument {} hat keinen Lieferanten!", dokument.getId());
            return;
        }
        Long lieferantId = dokument.getLieferant().getId();
        // Alle Dokumente des Lieferanten, nicht nur die Vorgänger-Typen: Erst über alle
        // Belege zeigt sich, ob eine gemeinsame Nummer trennscharf ist (Kundennummer).
        automatischeVerknuepfung(dokument, geschaeftsdaten,
                dokumentRepository.findByLieferantIdOrderByUploadDatumDesc(lieferantId),
                speicherMitSperren(sperreRepository.findByLieferantId(lieferantId)));
    }

    /**
     * Neuer Merkmal-Speicher, der die von Hand gelösten Paare kennt – der
     * Abgleich verknüpft sie dann nicht wieder.
     */
    private LieferantDokumentAbgleich.Merkmalspeicher speicherMitSperren(
            Collection<LieferantDokumentVerknuepfungSperre> sperren) {
        LieferantDokumentAbgleich.Merkmalspeicher speicher = dokumentAbgleich.neuerSpeicher();
        if (sperren != null) {
            sperren.forEach(s -> speicher.sperre(s.getDokumentId(), s.getVerknuepftId()));
        }
        return speicher;
    }

    private LieferantDokumentAbgleich.Merkmalspeicher speicherFuerLieferant(LieferantDokument dokument) {
        Long lieferantId = dokument.getLieferant() != null ? dokument.getLieferant().getId() : null;
        return speicherMitSperren(lieferantId != null ? sperreRepository.findByLieferantId(lieferantId) : List.of());
    }

    private void automatischeVerknuepfung(LieferantDokument dokument, LieferantGeschaeftsdokument geschaeftsdaten,
            List<LieferantDokument> vorhandeneKandidaten, LieferantDokumentAbgleich.Merkmalspeicher speicher) {
        if (dokument.getLieferant() == null) {
            log.warn("[Verknüpfung] Dokument {} hat keinen Lieferanten!", dokument.getId());
            return;
        }
        List<LieferantDokumentTyp> vorgaengerTypen = LieferantDokumentAbgleich.vorgaengerTypen(dokument.getTyp());
        if (vorgaengerTypen.isEmpty()) {
            log.debug("[Verknüpfung] Dokumenttyp {} hat keine Vorgängertypen definiert", dokument.getTyp());
            return;
        }

        List<LieferantDokument> kandidaten = vorhandeneKandidaten != null
                ? vorhandeneKandidaten
                : dokumentRepository.findByLieferantIdAndTypIn(dokument.getLieferant().getId(), vorgaengerTypen);

        var ergebnis = dokumentAbgleich.findeVorgaenger(dokument, geschaeftsdaten, kandidaten, speicher);
        dokument.getVerknuepfteDokumente().addAll(ergebnis.sicher());
        // Ein Hinweis-Treffer ergänzt nur eine Lücke: Steht schon ein Vorgänger dieses
        // Typs fest (z. B. von Hand gesetzt), bleibt die Zuordnung unverfälscht.
        LieferantDokument hinweis = ergebnis.hinweis();
        if (hinweis != null && !hatVorgaengerVomTyp(dokument, hinweis.getTyp())) {
            dokument.getVerknuepfteDokumente().add(hinweis);
        }
    }

    private static boolean hatVorgaengerVomTyp(LieferantDokument dokument, LieferantDokumentTyp typ) {
        return dokument.getVerknuepfteDokumente().stream().anyMatch(v -> v.getTyp() == typ);
    }

    /**
     * Verknüpft ein (neues oder neu analysiertes) Dokument in beide Richtungen:
     * mit seinen Vorgängern und mit vorhandenen Nachfolgern, die auf es warten.
     */
    public void performRelink(LieferantDokument dokument) {
        if (dokument.getLieferant() == null)
            return;
        List<LieferantDokument> alleDokumente = dokumentRepository.findByLieferantIdOrderByUploadDatumDesc(
                dokument.getLieferant().getId());
        performRelink(dokument, alleDokumente);
    }

    /**
     * Wie {@link #performRelink(LieferantDokument)}, mit bereits geladenen
     * Dokumenten desselben Lieferanten.
     */
    public void performRelink(LieferantDokument dokument, List<LieferantDokument> alleDokumente) {
        if (dokument.getLieferant() == null || dokument.getGeschaeftsdaten() == null) {
            return;
        }
        LieferantDokumentAbgleich.Merkmalspeicher speicher = speicherFuerLieferant(dokument);

        // 1. Vorgänger dieses Dokuments (z. B. Rechnung -> AB)
        automatischeVerknuepfung(dokument, dokument.getGeschaeftsdaten(), alleDokumente, speicher);

        // 2. Nachfolger, zu denen dieses Dokument als Vorgänger gehört. Typischer Fall:
        // Das Angebot kommt erst nach der Auftragsbestätigung ins Postfach. Alle
        // Dokumente als Kandidaten: Der Abgleich filtert die Typen selbst, braucht aber
        // alle Belege, um Kundennummern von Auftragsnummern zu unterscheiden.
        LieferantDokumentTyp meinTyp = dokument.getTyp();
        for (LieferantDokument anderes : alleDokumente) {
            if (anderes == dokument || anderes.getGeschaeftsdaten() == null
                    || (anderes.getId() != null && anderes.getId().equals(dokument.getId()))
                    || !LieferantDokumentAbgleich.vorgaengerTypen(anderes.getTyp()).contains(meinTyp)
                    || anderes.getVerknuepfteDokumente().contains(dokument)) {
                continue;
            }
            var ergebnis = dokumentAbgleich.findeVorgaenger(
                    anderes, anderes.getGeschaeftsdaten(), alleDokumente, speicher);
            boolean passt = ergebnis.sicher().contains(dokument)
                    || (ergebnis.hinweis() == dokument && !hatVorgaengerVomTyp(anderes, meinTyp));
            if (passt) {
                anderes.getVerknuepfteDokumente().add(dokument);
                dokumentRepository.save(anderes);
                log.info("[Relink] Dokument {} -> {} nachträglich verknüpft", anderes.getId(), dokument.getId());
            }
        }
    }

    /**
     * Verknüpft alle Dokumente eines Lieferanten neu (bestehende Verknüpfungen
     * bleiben). Prüft nur die Vorgänger-Richtung: Weil jedes Dokument einmal an
     * der Reihe ist und der Nummernbezug beidseitig zählt, ist die Rückrichtung
     * aus {@link #performRelink(LieferantDokument, List)} hier überflüssig.
     *
     * @return Anzahl neu entstandener Verknüpfungen
     */
    public int verknuepfeDokumenteNeu(List<LieferantDokument> dokumente) {
        Set<Long> lieferanten = dokumente.stream()
                .filter(d -> d.getLieferant() != null && d.getLieferant().getId() != null)
                .map(d -> d.getLieferant().getId())
                .collect(java.util.stream.Collectors.toSet());
        List<LieferantDokumentVerknuepfungSperre> sperren = new ArrayList<>();
        lieferanten.forEach(id -> sperren.addAll(sperreRepository.findByLieferantId(id)));
        return verknuepfeDokumenteNeu(dokumente, sperren);
    }

    /**
     * Wie {@link #verknuepfeDokumenteNeu(List)}, mit bereits geladenen Sperren.
     */
    int verknuepfeDokumenteNeu(List<LieferantDokument> dokumente,
            Collection<LieferantDokumentVerknuepfungSperre> sperren) {
        LieferantDokumentAbgleich.Merkmalspeicher speicher = speicherMitSperren(sperren);
        // In Kettenreihenfolge und je Typ vom ältesten zum neuesten: So hängen
        // Angebots-Fassungen schon aneinander, wenn die AB ihr Angebot sucht.
        List<LieferantDokument> reihenfolge = dokumente.stream()
                .sorted(Comparator
                        .comparing((LieferantDokument d) -> LieferantDokumentAbgleich.kettenRang(d.getTyp()))
                        .thenComparing(d -> d.getGeschaeftsdaten() != null ? d.getGeschaeftsdaten().getDokumentDatum() : null,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(LieferantDokument::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        int neu = 0;
        for (LieferantDokument dokument : reihenfolge) {
            if (dokument.getLieferant() == null || dokument.getGeschaeftsdaten() == null) {
                continue;
            }
            int vorher = dokument.getVerknuepfteDokumente().size();
            automatischeVerknuepfung(dokument, dokument.getGeschaeftsdaten(), dokumente, speicher);
            neu += dokument.getVerknuepfteDokumente().size() - vorher;
        }
        return neu;
    }

    private String getMimeType(LieferantDokument dokument) {
        String dateiname = dokument.getEffektiverDateiname();
        if (dateiname == null)
            return "application/pdf";

        String lower = dateiname.toLowerCase();
        if (lower.endsWith(".pdf"))
            return "application/pdf";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
            return "image/jpeg";
        if (lower.endsWith(".png"))
            return "image/png";
        return "application/pdf";
    }

    /**
     * Prüft ob ein JSON-String abgeschnitten wurde (unvollständig ist).
     * Das passiert wenn die API-Antwort das maxOutputTokens-Limit erreicht.
     */
    private boolean isJsonTruncated(String json) {
        if (json == null || json.isBlank()) {
            return true;
        }
        String trimmed = json.trim();
        // Gültiges JSON muss mit } oder ] enden
        if (!trimmed.endsWith("}") && !trimmed.endsWith("]")) {
            return true;
        }
        // Zusätzlich: Prüfe ob Klammern balanciert sind
        int openBraces = 0;
        int openBrackets = 0;
        boolean inString = false;
        char prevChar = 0;
        for (char c : trimmed.toCharArray()) {
            if (c == '"' && prevChar != '\\') {
                inString = !inString;
            }
            if (!inString) {
                if (c == '{')
                    openBraces++;
                else if (c == '}')
                    openBraces--;
                else if (c == '[')
                    openBrackets++;
                else if (c == ']')
                    openBrackets--;
            }
            prevChar = c;
        }
        return openBraces != 0 || openBrackets != 0;
    }

    private String rufGeminiApi(byte[] dokumentBytes, String mimeType, boolean useProModel) {
        return rufGeminiApi(dokumentBytes, mimeType, useProModel, false);
    }

    private String rufGeminiApi(byte[] dokumentBytes, String mimeType, boolean useProModel,
            boolean skipArtikelPositionen) {
        KiAntwort antwort = rufGeminiApiMitStatus(dokumentBytes, mimeType, useProModel, skipArtikelPositionen);
        return antwort != null ? antwort.text() : null;
    }

    private KiAntwort rufGeminiApiMitStatus(byte[] dokumentBytes, String mimeType, boolean useProModel,
            boolean skipArtikelPositionen) {
        // Bei skipArtikelPositionen: Kürzere Anfrage um Tokenlimit zu vermeiden
        String userText = skipArtikelPositionen
                ? "Analysiere dieses Dokument. WICHTIG: Gib artikelPositionen als leeres Array [] zurück um die Antwort kurz zu halten."
                : "Analysiere dieses Dokument.";
        return rufGemini(dokumentBytes, mimeType, SYSTEM_PROMPT_DOKUMENT_ANALYSE, userText, useProModel);
    }

    /**
     * Nur-Positionen-Aufruf für den {@link LieferantDokumentPositionLeser}: der
     * Prompt kommt als System-Anweisung, das Standardmodell liest.
     */
    private KiAntwort rufNurPositionen(byte[] dokumentBytes, String mimeType, String prompt) {
        return rufGemini(dokumentBytes, mimeType, prompt, "Lies alle Positionen dieses Dokuments aus.", false);
    }

    /**
     * Ein Gemini-Aufruf mit Dokument, System-Anweisung und Nutzertext. Meldet
     * über {@link KiAntwort#abgeschnitten()}, ob die Antwort am Ausgabelimit
     * endete ({@code finishReason = MAX_TOKENS}).
     */
    private KiAntwort rufGemini(byte[] dokumentBytes, String mimeType, String systemPrompt, String userText,
            boolean useProModel) {
        API_LOCK.lock();
        try {
            // Ensure minimum delay between API calls to prevent rate limiting
            long now = System.currentTimeMillis();
            long elapsed = now - lastApiCallTime;
            if (elapsed < API_CALL_DELAY_MS && lastApiCallTime > 0) {
                try {
                    Thread.sleep(API_CALL_DELAY_MS - elapsed);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            log.debug("[Gemini API] Starte Dokumentanalyse API-Aufruf (Lock erworben, Model: {})",
                    useProModel ? "Pro" : "Standard");

            // Key zur Laufzeit aus System-Setup lesen.
            String geminiApiKey = systemSettingsService.getGeminiApiKey();
            if (geminiApiKey == null || geminiApiKey.isBlank()) {
                log.warn("Gemini API Key nicht konfiguriert");
                return null;
            }

            String base64Data = Base64.getEncoder().encodeToString(dokumentBytes);

            // Build Request
            ObjectNode requestBody = objectMapper.createObjectNode();

            // System Prompt
            ObjectNode systemInstruction = objectMapper.createObjectNode();
            ArrayNode sysParts = objectMapper.createArrayNode();
            sysParts.add(objectMapper.createObjectNode().put("text", systemPrompt));
            systemInstruction.set("parts", sysParts);
            requestBody.set("systemInstruction", systemInstruction);

            // User Content
            ArrayNode contents = objectMapper.createArrayNode();
            ObjectNode userMsg = objectMapper.createObjectNode().put("role", "user");
            ArrayNode parts = objectMapper.createArrayNode();

            ObjectNode dataPart = objectMapper.createObjectNode();
            ObjectNode inlineData = objectMapper.createObjectNode();
            inlineData.put("mimeType", mimeType);
            inlineData.put("data", base64Data);
            dataPart.set("inlineData", inlineData);
            parts.add(dataPart);

            parts.add(objectMapper.createObjectNode().put("text", userText));

            userMsg.set("parts", parts);
            contents.add(userMsg);
            requestBody.set("contents", contents);

            // Config - maxOutputTokens wichtig für PDFs mit vielen Artikeln
            ObjectNode config = objectMapper.createObjectNode();
            config.put("temperature", 0.0);
            config.put("responseMimeType", "application/json");
            // Lange Rechnungen haben hunderte Positionen – 16k reichten dafür nicht.
            config.put("maxOutputTokens", maxOutputTokens);
            requestBody.set("generationConfig", config);

            // Send with RETRY logic
            String body = objectMapper.writeValueAsString(requestBody);

            // RETRY LOOP: Bei Fehlern bis zu MAX_RETRIES versuchen
            // Beim 3. Versuch (attempt == 2) wird das Pro-Model als Fallback verwendet
            HttpResponse<String> response = null;
            Exception lastException = null;

            for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                try {
                    // Beim letzten Versuch (3.) auf Pro-Model wechseln als Fallback
                    String modelToUse;
                    if (attempt == MAX_RETRIES - 1 && !useProModel) {
                        modelToUse = geminiProModel;
                        log.info("[Gemini API] Letzter Versuch - wechsle zu Pro-Model: {}", geminiProModel);
                    } else {
                        modelToUse = useProModel ? geminiProModel : geminiModel;
                    }

                    String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s".formatted(
                            modelToUse, geminiApiKey);

                    HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(180))
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();

                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    lastApiCallTime = System.currentTimeMillis();

                    // Erfolg (2xx) - Parse Response
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        JsonNode root = objectMapper.readTree(response.body());

                        // Prüfe ob candidates vorhanden
                        JsonNode candidates = root.path("candidates");
                        if (candidates.isEmpty() || candidates.get(0) == null) {
                            log.warn("[Gemini API] Keine candidates in Antwort - mglw. Safety-Block");
                            // Log safety ratings if present
                            if (root.has("promptFeedback")) {
                                log.warn("[Gemini API] promptFeedback: {}", root.get("promptFeedback"));
                            }
                            return null;
                        }

                        String result = candidates.get(0).path("content").path("parts").path(0).path("text").asText();
                        boolean abgeschnitten = "MAX_TOKENS".equals(candidates.get(0).path("finishReason").asText());
                        if (abgeschnitten) {
                            log.warn("[Gemini API] Antwort am Ausgabelimit ({} Tokens) abgeschnitten (Model: {})",
                                    maxOutputTokens, modelToUse);
                        }

                        // Nur Länge und Model loggen: die Antwort enthält Geschäftsdaten (DSGVO).
                        log.info("[Gemini API] Dokumentanalyse erfolgreich (Attempt {}, Model: {}, {} Zeichen)",
                                attempt + 1, modelToUse, result.length());
                        return new KiAntwort(result, abgeschnitten);
                    }

                    // Rate Limiting (429) oder Server-Fehler (5xx) - Retry mit Backoff
                    if (response.statusCode() == 429 || response.statusCode() >= 500) {
                        long delay = INITIAL_RETRY_DELAY_MS * (1L << attempt); // Exponential backoff
                        log.warn("[Gemini API] Fehler {} bei Attempt {}/{} - Retry in {}ms. Body: {}",
                                response.statusCode(), attempt + 1, MAX_RETRIES, delay,
                                response.body().substring(0, Math.min(200, response.body().length())));
                        Thread.sleep(delay);
                        continue;
                    }

                    // Client-Fehler (4xx außer 429) - Nicht retryable
                    log.error("[Gemini API] Client-Fehler {} - kein Retry. Body: {}",
                            response.statusCode(), response.body());
                    return null;

                } catch (java.net.http.HttpTimeoutException timeoutEx) {
                    // Timeout - Retry mit Backoff
                    lastException = timeoutEx;
                    long delay = INITIAL_RETRY_DELAY_MS * (1L << attempt);
                    log.warn("[Gemini API] Timeout bei Attempt {}/{} - Retry in {}ms: {}",
                            attempt + 1, MAX_RETRIES, delay, timeoutEx.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    lastException = e;
                    log.error("[Gemini API] Exception bei Attempt {}/{}: {}",
                            attempt + 1, MAX_RETRIES, e.getMessage());
                    // Nur bei bestimmten Exceptions retry
                    if (e instanceof java.io.IOException) {
                        long delay = INITIAL_RETRY_DELAY_MS * (1L << attempt);
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    } else {
                        break; // Andere Exceptions nicht retryable
                    }
                }
            }

            // Alle Retries erschöpft
            log.error("[Gemini API] Alle {} Retries fehlgeschlagen. Letzte Exception: {}",
                    MAX_RETRIES, lastException != null ? lastException.getMessage() : "keine");
            return null;

        } catch (Exception e) {
            log.error("Fehler beim Aufruf der Gemini API", e);
            return null;
        } finally {
            API_LOCK.unlock();
        }
    }

    private LieferantGeschaeftsdokument mapJsonToData(String jsonString) {
        try {
            JsonNode json = objectMapper.readTree(jsonString);

            // Prüfe zuerst ob es ein Geschäftsdokument ist
            if (json.has("istGeschaeftsdokument") && !json.get("istGeschaeftsdokument").asBoolean()) {
                log.info("KI klassifiziert Dokument als KEIN Geschäftsdokument");
                return null;
            }

            // Prüfe Dokumenttyp - wenn SONSTIG, dann kein Geschäftsdokument
            if (json.has("dokumentTyp") && !json.get("dokumentTyp").isNull()) {
                String typString = json.get("dokumentTyp").asText().toUpperCase();
                if ("SONSTIG".equals(typString)) {
                    log.info("KI klassifiziert Dokument als SONSTIG (kein Geschäftsdokument)");
                    return null;
                }
            }

            // Neues Geschaeftsdokument erstellen
            LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
            gd.setAiRawJson(jsonString);
            gd.setAnalysiertAm(LocalDateTime.now());

            // Dokumenttyp aus KI-Response
            if (json.has("dokumentTyp") && !json.get("dokumentTyp").isNull()) {
                String typString = json.get("dokumentTyp").asText().toUpperCase();
                LieferantDokumentTyp typ = typAusKiAntwort(typString);
                if (typ != null) {
                    gd.setDetectedTyp(typ);
                    log.info("KI erkannte Dokumenttyp: {}", typ);
                } else {
                    log.debug("Unbekannter Dokumenttyp von KI: {}", typString);
                }
            }

            // Dokumentnummer
            if (json.has("dokumentNummer") && !json.get("dokumentNummer").isNull()) {
                gd.setDokumentNummer(json.get("dokumentNummer").asText().trim());
            }

            // Datum
            if (json.has("dokumentDatum") && !json.get("dokumentDatum").isNull()) {
                try {
                    gd.setDokumentDatum(
                            LocalDate.parse(json.get("dokumentDatum").asText(), DateTimeFormatter.ISO_LOCAL_DATE));
                } catch (Exception e) {
                    log.debug("Konnte Datum nicht parsen: {}", json.get("dokumentDatum").asText());
                }
            }

            // Beträge
            if (json.has("betragNetto") && !json.get("betragNetto").isNull()) {
                gd.setBetragNetto(new BigDecimal(json.get("betragNetto").asText()));
            }
            if (json.has("betragBrutto") && !json.get("betragBrutto").isNull()) {
                gd.setBetragBrutto(new BigDecimal(json.get("betragBrutto").asText()));
            }
            if (json.has("mwstSatz") && !json.get("mwstSatz").isNull()) {
                gd.setMwstSatz(new BigDecimal(json.get("mwstSatz").asText()));
            }

            // Liefertermin
            if (json.has("liefertermin") && !json.get("liefertermin").isNull()) {
                try {
                    gd.setLiefertermin(
                            LocalDate.parse(json.get("liefertermin").asText(), DateTimeFormatter.ISO_LOCAL_DATE));
                } catch (Exception e) {
                    log.debug("Konnte Liefertermin nicht parsen: {}", json.get("liefertermin").asText());
                }
            }

            // Bestellnummer und Referenznummer
            if (json.has("bestellnummer") && !json.get("bestellnummer").isNull()) {
                gd.setBestellnummer(json.get("bestellnummer").asText().trim());
            }
            if (json.has("referenzNummer") && !json.get("referenzNummer").isNull()) {
                gd.setReferenzNummer(json.get("referenzNummer").asText().trim());
            }

            // Zahlungsziel
            LocalDate zahlungszielDatum = null;
            if (json.has("zahlungsziel") && !json.get("zahlungsziel").isNull()) {
                try {
                    zahlungszielDatum = LocalDate.parse(json.get("zahlungsziel").asText(),
                            DateTimeFormatter.ISO_LOCAL_DATE);
                    gd.setZahlungsziel(zahlungszielDatum);
                } catch (Exception e) {
                    log.debug("Konnte Zahlungsziel nicht parsen: {}", json.get("zahlungsziel").asText());
                }
            }

            // Skonto-Konditionen parsen
            Integer skontoTage = null;
            BigDecimal skontoProzent = null;
            Integer nettoTage = null;

            if (json.has("skontoTage") && !json.get("skontoTage").isNull()) {
                skontoTage = json.get("skontoTage").asInt();
                gd.setSkontoTage(skontoTage);
                log.info("Skonto erkannt: {} Tage", skontoTage);
            }
            if (json.has("skontoProzent") && !json.get("skontoProzent").isNull()) {
                skontoProzent = new BigDecimal(json.get("skontoProzent").asText());
                gd.setSkontoProzent(skontoProzent);
                log.info("Skonto Prozent: {}%", skontoProzent);
            }
            if (json.has("nettoTage") && !json.get("nettoTage").isNull()) {
                nettoTage = json.get("nettoTage").asInt();
                gd.setNettoTage(nettoTage);
                log.info("Netto Tage: {}", nettoTage);
            } else {
                log.info("KEIN nettoTage in KI-Response gefunden. JSON hat: {}", json.fieldNames());
            }

            // Fallback: Berechne zahlungsziel aus nettoTage wenn nicht vorhanden
            if (zahlungszielDatum == null && nettoTage != null && gd.getDokumentDatum() != null) {
                zahlungszielDatum = gd.getDokumentDatum().plusDays(nettoTage);
                gd.setZahlungsziel(zahlungszielDatum);
                log.debug("Zahlungsziel aus nettoTage berechnet: {} + {} Tage = {}",
                        gd.getDokumentDatum(), nettoTage, zahlungszielDatum);
            }

            // Bereits gezahlt (Vorauskasse etc.)
            if (json.has("bereitsGezahlt") && !json.get("bereitsGezahlt").isNull()) {
                boolean bereitsGezahlt = json.get("bereitsGezahlt").asBoolean();
                gd.setBereitsGezahlt(bereitsGezahlt);
                if (bereitsGezahlt) {
                    gd.setBezahlt(true); // Wenn bereits gezahlt, setze auch bezahlt-Flag
                }
            }
            if (json.has("zahlungsart") && !json.get("zahlungsart").isNull()) {
                gd.setZahlungsart(normalizeZahlungsart(json.get("zahlungsart").asText()));
            }

            // Confidence
            if (json.has("confidence") && !json.get("confidence").isNull()) {
                gd.setAiConfidence(json.get("confidence").asDouble());
            }

            // Positionen fuer die Speicherung mitgeben. Die Preisuebernahme passiert
            // in analysiereDokument(), sobald auch der Lieferant vorliegt.
            gd.setAusgelesenePositionen(LieferantDokumentPositionService.ausKiAntwort(json));
            return gd;

        } catch (Exception e) {
            log.error("Fehler beim Parsen der KI-Response: {}", jsonString, e);
            return null;
        }
    }

    /**
     * Dokumenttyp aus der KI-Antwort. Kopien, Entwürfe und Abschriften meldet die
     * KI mit dem Vermerk " (Kopie)". Angebot, AB, Lieferschein und Werkstoffzeugnis
     * bekommen trotzdem ihren Typ – sie gehören in die Dokumentenkette, und Zeugnisse
     * tragen den Vermerk „Kopie“ fast immer. Rechnungs- und
     * Gutschrift-Kopien bleiben ohne Typ, damit keine Kopie in den offenen Posten
     * landet.
     */
    static LieferantDokumentTyp typAusKiAntwort(String typString) {
        if (typString == null || typString.isBlank()) {
            return null;
        }
        String roh = typString.trim().toUpperCase(java.util.Locale.ROOT);
        boolean kopie = roh.endsWith("(KOPIE)");
        String basis = kopie ? roh.substring(0, roh.length() - "(KOPIE)".length()).trim() : roh;
        LieferantDokumentTyp typ;
        try {
            typ = LieferantDokumentTyp.valueOf(basis);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (kopie && (typ == LieferantDokumentTyp.RECHNUNG || typ == LieferantDokumentTyp.GUTSCHRIFT
                || typ == LieferantDokumentTyp.SONSTIG)) {
            return null;
        }
        return typ;
    }

    private LieferantDokumentTyp erkenneTypAusNummer(String nummer) {
        if (nummer == null)
            return null;
        String upper = nummer.toUpperCase();

        if (upper.startsWith("RE") || upper.contains("RECHNUNG"))
            return LieferantDokumentTyp.RECHNUNG;
        if (upper.startsWith("AB") || upper.contains("AUFTRAGS"))
            return LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG;
        if (upper.startsWith("LS") || upper.contains("LIEFER"))
            return LieferantDokumentTyp.LIEFERSCHEIN;
        if (upper.startsWith("AN") || upper.contains("ANGEBOT"))
            return LieferantDokumentTyp.ANGEBOT;

        return null;
    }

    /**
     * Uebergibt die von der KI ausgelesenen Artikelpositionen an die
     * Preisuebernahme.
     *
     * <p>Dieser Weg lag lange brach: Der Aufruf war auskommentiert mit dem
     * Vermerk, ohne Lieferant und DB gehe das hier nicht. Der Lieferant haengt
     * aber am Dokument und steht in {@code analysiereDokument} bereit - gescannte
     * Rechnungen haben deshalb nie einen Preis aktualisiert.
     */
    private void verarbeiteArtikelPositionen(JsonNode json, Lieferanten lieferant, LieferantDokumentTyp typ,
            LocalDate dokumentDatum, String belegnummer) {
        if (lieferant == null || json == null || !json.has("artikelPositionen")) {
            return;
        }
        PreisQuelle quelle = preisQuelleFuer(typ);
        if (quelle == null) {
            return;
        }
        JsonNode positionen = json.get("artikelPositionen");
        if (positionen == null || !positionen.isArray()) {
            return;
        }

        List<PreisUebernahmeService.Position> ausgelesen = new ArrayList<>();
        for (JsonNode pos : positionen) {
            ausgelesen.add(new PreisUebernahmeService.Position(
                    textOderNull(pos, "externeArtikelnummer"),
                    betragOderNull(pos),
                    textOderNull(pos, "preiseinheit"),
                    textOderNull(pos, "mengeneinheit")));
        }

        meldePreisuebernahmeAn(lieferant, quelle, dokumentDatum, belegnummer, ausgelesen);
    }

    private String textOderNull(JsonNode pos, String feld) {
        if (pos == null || !pos.has(feld) || pos.get(feld).isNull()) {
            return null;
        }
        String wert = pos.get(feld).asText();
        return wert == null || wert.isBlank() ? null : wert.trim();
    }

    private BigDecimal betragOderNull(JsonNode pos) {
        String roh = textOderNull(pos, "einzelpreis");
        if (roh == null) {
            return null;
        }
        try {
            return new BigDecimal(roh.replace(',', '.'));
        } catch (NumberFormatException e) {
            log.debug("Einzelpreis aus KI-Antwort nicht lesbar: {}", roh);
            return null;
        }
    }

    /**
     * Uebergibt Artikelpositionen aus strukturierten Rechnungsdaten (ZUGFeRD-PDF
     * oder XRechnung-XML) an die Preisuebernahme.
     */
    private void verarbeiteStrukturiertePositionen(
            List<org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition> positionen,
            Lieferanten lieferant, LieferantDokumentTyp typ, LocalDate dokumentDatum, String belegnummer) {
        if (lieferant == null || positionen == null || positionen.isEmpty()) {
            return;
        }
        PreisQuelle quelle = preisQuelleFuer(typ);
        if (quelle == null) {
            return;
        }

        List<PreisUebernahmeService.Position> ausgelesen = positionen.stream()
                .map(pos -> new PreisUebernahmeService.Position(
                        pos.getExterneArtikelnummer(),
                        pos.getEinzelpreis(),
                        pos.getPreiseinheit(),
                        pos.getMengeneinheit()))
                .toList();

        meldePreisuebernahmeAn(lieferant, quelle, dokumentDatum, belegnummer, ausgelesen);
    }

    /**
     * Meldet die Preisuebernahme an - ausgefuehrt wird sie erst, wenn die Analyse
     * committet ist.
     *
     * <p>Frueher rief diese Klasse den {@code PreisUebernahmeService} direkt auf,
     * der dafuer mit {@code REQUIRES_NEW} eine zweite Transaktion mitten in der
     * laufenden Analyse oeffnete. Rollte die Analyse anschliessend zurueck, blieb
     * ein Preisstand fuer einen Beleg stehen, den es in der Datenbank nie gab. Das
     * Event greift erst nach dem Commit, also nur fuer Belege, die tatsaechlich
     * gespeichert sind - und die zweite Verbindung wird erst gebraucht, wenn die
     * Analyse mit ihrer Arbeit fertig ist.
     *
     * <p><b>Zugestellt wird nur mit laufender Transaktion.</b> Ohne sie verwirft
     * Spring das Event stillschweigend ({@code fallbackExecution} bleibt aus - eine
     * Preisuebernahme ohne den zugehoerigen Beleg waere genau das, was dieser Umbau
     * abstellt). Beide Wege in die Analyse bringen eine mit: {@code analysiereDokument}
     * ist transaktional, ebenso {@code reanalysiereDokumentById} fuer die
     * Stapelverarbeitung.
     */
    private void meldePreisuebernahmeAn(Lieferanten lieferant, PreisQuelle quelle, LocalDate dokumentDatum,
            String belegnummer, List<PreisUebernahmeService.Position> positionen) {
        // Der Lieferant haengt als Lazy-Proxy am Dokument. Der Empfaenger arbeitet
        // nach dem Commit in einer neuen Transaktion - dort waere der Proxy
        // abgehaengt und der erste Zugriff auf den Namen wuerde abbrechen. Also
        // hier aufloesen, solange die Analyse-Transaktion noch laeuft.
        Hibernate.initialize(lieferant);
        eventPublisher.publishEvent(new PreisUebernahmeEvent(
                lieferant, quelle, alsDatum(dokumentDatum), belegnummer, positionen));
    }

    /**
     * Entscheidet, ob aus diesem Dokumenttyp ueberhaupt ein Einkaufspreis werden
     * darf, und haelt dessen Herkunft im Verlauf fest.
     *
     * <p>Nur Belege, die einen ausgehandelten Einkaufspreis nennen, kommen durch:
     * <ul>
     * <li><b>Rechnung</b> - der tatsaechlich berechnete Preis, die verlaesslichste Quelle.</li>
     * <li><b>Angebot, Auftragsbestaetigung</b> - zugesagte Preise; als Angebotsquelle
     *     gekennzeichnet, damit im Verlauf sichtbar bleibt, dass noch nichts geflossen ist.</li>
     * </ul>
     *
     * <p>Alles andere wird abgelehnt, und zwar mit Absicht: Ein <b>Lieferschein</b>
     * fuehrt Listen- statt Nettopreise. Eine <b>Gutschrift</b> nennt den erstatteten
     * Betrag, nicht den Einkaufspreis - eine Mengenrabatt-Gutschrift wuerde sonst
     * den Artikelpreis auf den Rabattbetrag setzen. Und <b>SONSTIG</b> bzw. ein
     * unerkannter Typ steht laut Analyse-Prompt fuer Kataloge, Preislisten und
     * Rechnungszusammenstellungen - dort stehen zwar Zahlen neben Artikelnummern,
     * aber keine, die in die Kalkulation gehoeren.
     *
     * @return die Quelle, oder {@code null} wenn aus diesem Typ kein Preis werden darf
     */
    private PreisQuelle preisQuelleFuer(LieferantDokumentTyp typ) {
        if (typ == null) {
            return null;
        }
        return switch (typ) {
            case RECHNUNG -> PreisQuelle.RECHNUNG;
            case ANGEBOT, AUFTRAGSBESTAETIGUNG -> PreisQuelle.ANGEBOT_EMAIL;
            default -> null;
        };
    }

    private Date alsDatum(LocalDate datum) {
        return datum == null ? null : Date.from(datum.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Dokumenttyp, den die Rechnungsdatei selbst ausweist (UNTDID 1001) - ohne
     * Annahmen.
     *
     * <p>Bewusst getrennt von der Typ-Erkennung fuer die Anzeige: die darf im
     * Zweifel "Rechnung" raten - aus dem Dateinamen oder als Vorgabe -, damit der
     * Beleg in der Dokumentliste einsortiert ist. Fuer die Preisuebernahme waere
     * dasselbe Raten gefaehrlich, weil daraus Kalkulationsdaten werden. Was hier
     * nicht ausdruecklich in der Datei steht, ergibt {@code null} und damit keinen
     * Preis.
     *
     * <p>Deckt alle gebraeuchlichen Rechnungsarten ab, nicht nur 380: Sammel- und
     * Abschlagsrechnungen sind bei Stahlhaendlern und am Bau die Regel.
     *
     * @return der ausgewiesene Typ, oder {@code null} wenn die Datei keinen nennt
     */
    private LieferantDokumentTyp ausgewiesenerTyp(String typeCode) {
        return UntdidCodeliste.typFuer(typeCode);
    }

    private String normalizeZahlungsart(String zahlungsart) {
        if (zahlungsart == null || zahlungsart.isBlank()) {
            return null;
        }

        String normalized = zahlungsart.trim().toUpperCase()
                .replace('-', '_')
                .replace(' ', '_')
                .replace(".", "");

        return switch (normalized) {
            case "VORAUSKASSE", "VORKASSE", "PREPAID", "PREPAYMENT" -> "VORAUSKASSE";
            case "SEPA_LASTSCHRIFT", "LASTSCHRIFT", "SEPA", "SEPA_DIRECT_DEBIT", "DIRECT_DEBIT",
                    "BANKEINZUG", "LASTSCHRIFTEINZUG", "EINZUGSERMACHTIGUNG" -> "SEPA_LASTSCHRIFT";
            case "KREDITKARTE", "CREDIT_CARD", "MASTERCARD", "VISA",
                    "KARTENZAHLUNG", "KARTENZAHLG", "EC_KARTE", "EC", "GIROCARD",
                    "MAESTRO", "V_PAY", "DEBITKARTE", "DEBIT_CARD" -> "KREDITKARTE";
            case "PAYPAL" -> "PAYPAL";
            case "AMAZON_PAY", "AMAZONPAY" -> "AMAZON_PAY";
            case "UEBERWEISUNG", "ÜBERWEISUNG", "BANK_TRANSFER", "TRANSFER" -> "UEBERWEISUNG";
            case "BAR", "BARZAHLUNG", "CASH" -> "BAR";
            case "SONSTIGE", "SONSTIG", "OTHER" -> "SONSTIGE";
            default -> "SONSTIGE";
        };
    }

    /**
     * Backfill für alle Lieferanten: ergänzt fehlende Verknüpfungen in den
     * Dokumentenketten. Bestehende (auch von Hand gesetzte) Verknüpfungen bleiben
     * erhalten – es wird nur hinzugefügt, nie gelöscht.
     *
     * @return Anzahl neu entstandener Verknüpfungen
     */
    @Transactional
    public int relinkAlleDokumente() {
        log.info("[Backfill] Ergänze Verknüpfungen für alle Lieferanten...");
        Map<Long, List<LieferantDokument>> jeLieferant = dokumentRepository.findAll().stream()
                .filter(d -> d.getLieferant() != null && d.getLieferant().getId() != null)
                .collect(java.util.stream.Collectors.groupingBy(d -> d.getLieferant().getId()));
        // Sperren einmal für alle laden – es sind wenige, von Hand gelöste Paare.
        List<LieferantDokumentVerknuepfungSperre> sperren = sperreRepository.findAll();
        int neu = jeLieferant.values().stream().mapToInt(d -> verknuepfeDokumenteNeu(d, sperren)).sum();
        log.info("[Backfill] Fertig: {} neue Verknüpfungen bei {} Lieferanten.", neu, jeLieferant.size());
        return neu;
    }

    /**
     * Backfill für einen Lieferanten, siehe {@link #relinkAlleDokumente()}.
     *
     * @return Anzahl neu entstandener Verknüpfungen
     */
    @Transactional
    public int relinkDokumenteByLieferant(Long lieferantId) {
        int neu = verknuepfeDokumenteNeu(dokumentRepository.findByLieferantIdOrderByUploadDatumDesc(lieferantId));
        log.info("[Backfill] Lieferant {}: {} neue Verknüpfungen.", lieferantId, neu);
        return neu;
    }
}
