package org.example.kalkulationsprogramm.service;

import static org.example.kalkulationsprogramm.domain.Dokumenttyp.ABSCHLAGSRECHNUNG;
import static org.example.kalkulationsprogramm.domain.Dokumenttyp.ANGEBOT;
import static org.example.kalkulationsprogramm.domain.Dokumenttyp.AUFTRAGSBESTAETIGUNG;
import static org.example.kalkulationsprogramm.domain.Dokumenttyp.ERSTE_MAHNUNG;
import static org.example.kalkulationsprogramm.domain.Dokumenttyp.GUTSCHRIFT;
import static org.example.kalkulationsprogramm.domain.Dokumenttyp.NACHTRAGSANGEBOT;
import static org.example.kalkulationsprogramm.domain.Dokumenttyp.RECHNUNG;
import static org.example.kalkulationsprogramm.domain.Dokumenttyp.SCHLUSSRECHNUNG;
import static org.example.kalkulationsprogramm.domain.Dokumenttyp.STORNORECHNUNG;
import static org.example.kalkulationsprogramm.domain.Dokumenttyp.TEILRECHNUNG;
import static org.example.kalkulationsprogramm.domain.Dokumenttyp.ZAHLUNGSERINNERUNG;
import static org.example.kalkulationsprogramm.domain.Dokumenttyp.ZWEITE_MAHNUNG;

import java.util.Arrays;
import java.util.List;
import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.dto.Textbaustein.DokumenttypGruppeDto;
import org.example.kalkulationsprogramm.dto.Textbaustein.TextbausteinPlatzhalterDto;
import org.springframework.stereotype.Service;

/**
 * Feste Auswahllisten der Textbaustein-Seite: welche Platzhalter es gibt (mit Beispielwert,
 * damit die Oberfläche ohne Vorschau auskommt) und wie die Dokumentarten gruppiert sind.
 * Einzige Quelle für beides, das Frontend pflegt keine eigene Liste.
 */
@Service
public class TextbausteinKatalogService {

    private static final String KUNDE = "Kunde";
    private static final String DOKUMENT = "Dokument";
    private static final String PROJEKT = "Projekt";
    private static final String ZAHLUNG = "Zahlung";
    private static final String VORGAENGER = "Vorgänger-Dokument";

    /** Reihenfolge = Anzeige-Reihenfolge in der Oberfläche. */
    private static final List<TextbausteinPlatzhalterDto> PLATZHALTER = List.of(
            p("{{Anrede}}", "Anrede", "Briefanrede passend zum Kunden", "Sehr geehrter Herr", KUNDE),
            p("{{KUNDENNAME}}", "Kundenname", "Name bzw. Firma des Kunden", "Max Mustermann", KUNDE),
            p("{{Ansprechpartner}}", "Ansprechpartner", "Ansprechpartner beim Kunden", "Erika Musterfrau", KUNDE),
            p("{{KUNDENNUMMER}}", "Kundennummer", "Kundennummer aus den Kundendaten", "K-1001", KUNDE),
            p("{{KUNDENADRESSE}}", "Kundenadresse", "Straße, PLZ und Ort des Kunden",
                    "Musterstraße 1, 12345 Musterstadt", KUNDE),
            p("{{DOKUMENTTYP}}", "Dokumentart", "Art des Dokuments", "Rechnung", DOKUMENT),
            p("{{DOKUMENTNUMMER}}", "Dokumentnummer", "Nummer des Dokuments", "RE-2026-0108", DOKUMENT),
            p("{{DATUM}}", "Datum", "Datum des Dokuments", "15.03.2026", DOKUMENT),
            p("{{SEITENZAHL}}", "Seitenzahl", "Seitenangabe im Dokument", "Seite 1 von 2", DOKUMENT),
            p("{{PROJEKTNUMMER}}", "Projektnummer", "Nummer des Projekts", "P-2026-017", PROJEKT),
            p("{{BAUVORHABEN}}", "Bauvorhaben", "Name des Bauvorhabens bzw. Projekts", "Neubau Carport", PROJEKT),
            p("{{ZAHLUNGSZIEL}}", "Zahlungsziel (Datum)", "Datum, bis zu dem bezahlt werden soll",
                    "29.03.2026", ZAHLUNG),
            p("{{ZAHLUNGSZIEL_TAGE}}", "Zahlungsziel (Tage)", "Zahlungsfrist des Kunden in Tagen", "14", ZAHLUNG),
            p("{{BEZUGSDOKUMENTTYP}}", "Art des Vorgängers", "Art des vorherigen Dokuments", "Angebot", VORGAENGER),
            p("{{BEZUGSDOKUMENTNUMMER}}", "Nummer des Vorgängers", "Nummer des vorherigen Dokuments",
                    "AN-2026-0042", VORGAENGER),
            p("{{BEZUGSDOKUMENTDATUM}}", "Datum des Vorgängers", "Datum des vorherigen Dokuments",
                    "01.03.2026", VORGAENGER));

    /** Jede Dokumentart steht in genau einer Gruppe (abgesichert im Test). */
    private static final List<DokumenttypGruppeDto> DOKUMENTTYP_GRUPPEN = List.of(
            g("Angebote & Aufträge", ANGEBOT, NACHTRAGSANGEBOT, AUFTRAGSBESTAETIGUNG),
            g("Rechnungen", RECHNUNG, TEILRECHNUNG, ABSCHLAGSRECHNUNG, SCHLUSSRECHNUNG),
            g("Mahnungen", ZAHLUNGSERINNERUNG, ERSTE_MAHNUNG, ZWEITE_MAHNUNG),
            g("Korrekturen", STORNORECHNUNG, GUTSCHRIFT));

    public List<TextbausteinPlatzhalterDto> platzhalter() {
        return PLATZHALTER;
    }

    public List<DokumenttypGruppeDto> dokumenttypGruppen() {
        return DOKUMENTTYP_GRUPPEN;
    }

    private static TextbausteinPlatzhalterDto p(String token, String label, String beschreibung,
            String beispiel, String gruppe) {
        return new TextbausteinPlatzhalterDto(token, label, beschreibung, beispiel, gruppe);
    }

    private static DokumenttypGruppeDto g(String gruppe, Dokumenttyp... typen) {
        return new DokumenttypGruppeDto(gruppe, Arrays.stream(typen).map(Dokumenttyp::getLabel).toList());
    }
}
