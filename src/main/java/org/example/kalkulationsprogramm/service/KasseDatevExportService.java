package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegKostenstellenAnteil;
import org.example.kalkulationsprogramm.domain.KasseEinstellung;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reiner CSV-Writer für Kassen-Buchungen; unabhängig vom LODAS-Lohnexport. */
@Service
public class KasseDatevExportService {
    private static final DateTimeFormatter TAG = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter BELEGTAG = DateTimeFormatter.ofPattern("ddMM");
    private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String SPALTEN = spalten();

    /** firmenname ist Paket-Metadatum für Task8, im EXTF-Kopf nicht vorgesehen. */
    public record Parameter(YearMonth monat, List<Beleg> belege,
                            Map<Long, List<BelegKostenstellenAnteil>> splitsJeBeleg,
                            KasseEinstellung einstellung, String erstellerName,
                            String firmenname, LocalDateTime erzeugtAm,
                            boolean trotzLueckenExportieren) { }

    public String erzeugeCsv(Parameter p) {
        List<Beleg> belege = p.belege().stream()
                .filter(b -> b.getBelegKategorie() != null && b.getBelegKategorie().istKassenBewegung())
                .sorted(Comparator.comparing(Beleg::getLaufendeNummer, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Beleg::getBelegDatum, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Beleg::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        StringBuilder csv = new StringBuilder(kopf(p, belege)).append("\r\n").append(SPALTEN).append("\r\n");
        for (Beleg b : belege) {
            var konten = BuchungssatzAbleitung.ableitenKonten(b, p.einstellung());
            String text = b.getBeschreibung();
            if (p.trotzLueckenExportieren() && (konten.sollKontoNr() == null || konten.habenKontoNr() == null)) {
                text = "PRÜFEN: " + (text == null ? "" : text);
            }
            BigDecimal brutto = geld(b.getBetragBrutto());
            BigDecimal firma = b.istTeilweiseFirma() ? geld(b.getBuchungsbetragBrutto()) : brutto;
            if (firma.compareTo(brutto) > 0) throw new IllegalArgumentException("Firmenbetrag übersteigt den Belegbetrag: " + b.getId());
            List<BelegKostenstellenAnteil> splits = p.splitsJeBeleg() == null || b.getId() == null
                    ? List.of() : p.splitsJeBeleg().getOrDefault(b.getId(), List.of());
            if (splits.isEmpty()) {
                csv.append(zeile(b, firma, konten, steuer(b), text, b.getKostenstelle()));
            } else {
                verteile(csv, b, firma, splits, konten, text);
            }
            if (firma.compareTo(brutto) < 0) {
                // Der Mischbon bewegt den vollen Kassenbetrag. Der private Rest
                // darf weder Aufwand/Vorsteuer noch Baustellenkosten erhöhen.
                var privat = b.getBelegKategorie().istAusgang()
                        ? new BuchungssatzAbleitung.Konten("1800", konten.habenKontoNr())
                        : new BuchungssatzAbleitung.Konten(konten.sollKontoNr(), "1800");
                csv.append(zeile(b, brutto.subtract(firma), privat, "", text, null));
            }
        }
        return csv.toString();
    }

    private static void verteile(StringBuilder csv, Beleg b, BigDecimal brutto,
                                 List<BelegKostenstellenAnteil> splits,
                                 BuchungssatzAbleitung.Konten konten, String text) {
        // berechneterBetrag enthält NETTO-Kosten, nicht den DATEV-Bruttoumsatz.
        // Kumuliert runden, damit auch drei Anteile den Gesamtbetrag centgenau
        // erhalten. Nicht zugeordnete Beträge bleiben ohne KOST1 sichtbar.
        BigDecimal basis = geld(b.getBuchungsbetragNetto());
        if (basis.signum() == 0) {
            if (brutto.signum() != 0) throw new IllegalArgumentException("Aufteilung ohne Berechnungsbasis: " + b.getId());
            csv.append(zeile(b, brutto, konten, steuer(b), text, b.getKostenstelle()));
            return;
        }
        BigDecimal nettoSumme = BigDecimal.ZERO;
        BigDecimal verteilt = BigDecimal.ZERO;
        for (BelegKostenstellenAnteil a : splits) {
            if (a.getBerechneterBetrag() == null || a.getBerechneterBetrag().signum() < 0) {
                throw new IllegalArgumentException("Aufteilung enthält einen ungültigen Betrag: " + b.getId());
            }
            nettoSumme = nettoSumme.add(a.getBerechneterBetrag());
            if (nettoSumme.compareTo(basis) > 0) throw new IllegalArgumentException("Aufteilung übersteigt den Belegbetrag: " + b.getId());
            BigDecimal kumuliert = brutto.multiply(nettoSumme).divide(basis, 2, RoundingMode.HALF_UP);
            csv.append(zeile(b, kumuliert.subtract(verteilt), konten, steuer(b), text, a.getKostenstelle()));
            verteilt = kumuliert;
        }
        if (verteilt.compareTo(brutto) < 0) csv.append(zeile(b, brutto.subtract(verteilt), konten, steuer(b), text, null));
    }

    public byte[] erzeugeCsvBytes(Parameter p) {
        // Windows-1252 ohne BOM ist der DATEV-Standard und erhält Umlaute auch
        // in älteren Importen. UTF-8 mit BOM ist ebenfalls üblich, sein BOM
        // kann aber bei älteren Lesern im ersten Feld landen. Zeichen außerhalb
        // der Codepage (z.B. Emoji) ersetzen, statt den Export abzubrechen.
        var encoder = Charset.forName("windows-1252").newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
        try {
            var buffer = encoder.encode(CharBuffer.wrap(erzeugeCsv(p)));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw new IllegalStateException("DATEV-Datei konnte nicht kodiert werden", e);
        }
    }

    private static String kopf(Parameter p, List<Beleg> belege) {
        KasseEinstellung e = p.einstellung() == null ? new KasseEinstellung() : p.einstellung();
        int beginn = e.getWirtschaftsjahrBeginnMonat() == null ? 1 : e.getWirtschaftsjahrBeginnMonat();
        return String.join(";", "\"EXTF\"", "700", "21", "\"Buchungsstapel\"", "13", ZEIT.format(p.erzeugtAm()),
                "", "\"HW\"", text(p.erstellerName(), 25), "", nummer(e.getDatevBeraternummer()),
                nummer(e.getDatevMandantennummer()), TAG.format(Wirtschaftsjahr.beginn(p.monat(), beginn)), "4",
                TAG.format(p.monat().atDay(1)), TAG.format(p.monat().atEndOfMonth()), text("Kasse " + p.monat(), 30),
                "", "1", "", !belege.isEmpty() && belege.stream().allMatch(Beleg::istFestgeschrieben) ? "1" : "0", "\"EUR\"");
    }

    private static String zeile(Beleg b, BigDecimal betrag, BuchungssatzAbleitung.Konten konten,
                                String bu, String beschreibung, Kostenstelle kostenstelle) {
        String[] f = new String[125]; Arrays.fill(f, "");
        f[0] = geld(betrag).toPlainString().replace('.', ','); f[1] = "S"; f[2] = "EUR";
        f[6] = nummer(konten.sollKontoNr()); f[7] = nummer(konten.habenKontoNr()); f[8] = bu;
        f[9] = b.getBelegDatum() == null ? "" : BELEGTAG.format(b.getBelegDatum());
        f[10] = b.istFestgeschrieben() && b.getLaufendeNummer() != null ? b.getLaufendeNummer().toString() : "";
        f[13] = text(beschreibung, 60);
        f[36] = kostenstelle == null ? "" : text(kostenstelle.getBezeichnung(), 36);
        f[113] = b.istFestgeschrieben() ? "1" : "0";
        return String.join(";", f) + "\r\n";
    }

    private static String steuer(Beleg b) {
        if (BuchungssatzAbleitung.istDatevTransfer(b) || b.getMwstSatz() == null) return "";
        BelegKategorie k = b.getBelegKategorie();
        if (k != BelegKategorie.KASSE_EINNAHME && k != BelegKategorie.KASSE_AUSGABE) return "";
        boolean ausgabe = k == BelegKategorie.KASSE_AUSGABE;
        // Storno kehrt die Kategorie um, muss aber dieselbe Steuerart wie das
        // Original rückgängig machen (Vorsteuer bleibt Vorsteuer).
        if (b.getStornoFuerBelegId() != null) ausgabe = !ausgabe;
        if (b.getMwstSatz().compareTo(BigDecimal.valueOf(19)) == 0) return ausgabe ? "9" : "3";
        if (b.getMwstSatz().compareTo(BigDecimal.valueOf(7)) == 0) return ausgabe ? "8" : "2";
        return "";
    }

    private static BigDecimal geld(BigDecimal betrag) {
        return (betrag == null ? BigDecimal.ZERO : betrag.abs()).setScale(2, RoundingMode.HALF_UP);
    }

    private static String nummer(String s) {
        if (s == null || s.isBlank()) return "";
        if (!s.matches("[0-9]+")) throw new IllegalArgumentException("DATEV-Kontonummern und Kennungen dürfen nur Ziffern enthalten");
        return s;
    }

    private static String text(String s, int max) {
        String sauber = s == null ? "" : s.replace(';', ' ').replace('\r', ' ').replace('\n', ' ');
        int ende = sauber.offsetByCodePoints(0, Math.min(max, sauber.codePointCount(0, sauber.length())));
        return "\"" + sauber.substring(0, ende).replace("\"", "\"\"") + "\"";
    }

    private static String spalten() {
        List<String> s = new ArrayList<>(List.of("Umsatz (ohne Soll/Haben-Kz)", "Soll/Haben-Kennzeichen", "WKZ Umsatz",
                "Kurs", "Basis-Umsatz", "WKZ Basis-Umsatz", "Konto", "Gegenkonto (ohne BU-Schlüssel)", "BU-Schlüssel",
                "Belegdatum", "Belegfeld 1", "Belegfeld 2", "Skonto", "Buchungstext", "Postensperre", "Diverse Adressnummer",
                "Geschäftspartnerbank", "Sachverhalt", "Zinssperre", "Beleglink"));
        for (int i = 1; i <= 8; i++) { s.add("Beleginfo - Art " + i); s.add("Beleginfo - Inhalt " + i); }
        s.addAll(List.of("KOST1 - Kostenstelle", "KOST2 - Kostenstelle", "Kost-Menge", "EU-Land u. UStID (Bestimmung)",
                "EU-Steuersatz (Bestimmung)", "Abw. Versteuerungsart", "Sachverhalt L+L", "Funktionsergänzung L+L",
                "BU 49 Hauptfunktionstyp", "BU 49 Hauptfunktionsnummer", "BU 49 Funktionsergänzung"));
        for (int i = 1; i <= 20; i++) { s.add("Zusatzinformation - Art " + i); s.add("Zusatzinformation- Inhalt " + i); }
        s.addAll(List.of("Stück", "Gewicht", "Zahlweise", "Forderungsart", "Veranlagungsjahr", "Zugeordnete Fälligkeit",
                "Skontotyp", "Auftragsnummer", "Buchungstyp", "USt-Schlüssel (Anzahlungen)", "EU-Land (Anzahlungen)",
                "Sachverhalt L+L (Anzahlungen)", "EU-Steuersatz (Anzahlungen)", "Erlöskonto (Anzahlungen)", "Herkunft-Kz",
                "Buchungs GUID", "KOST-Datum", "SEPA-Mandatsreferenz", "Skontosperre", "Gesellschaftername", "Beteiligtennummer",
                "Identifikationsnummer", "Zeichnernummer", "Postensperre bis", "Bezeichnung SoBil-Sachverhalt",
                "Kennzeichen SoBil-Buchung", "Festschreibung", "Leistungsdatum", "Datum Zuord. Steuerperiode", "Fälligkeit",
                "Generalumkehr (GU)", "Steuersatz", "Land", "Abrechnungsreferenz", "BVV-Position",
                "EU-Land u. UStID (Ursprung)", "EU-Steuersatz (Ursprung)", "Abw. Skontokonto"));
        return s.stream().map(label -> text(label, 100)).collect(Collectors.joining(";"));
    }
}
