package org.example.kalkulationsprogramm.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdAbzug;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdBelegverweis;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.example.kalkulationsprogramm.service.RechnungPdfService.AbrechnungspositionPdfDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.AbrechnungsverlaufPdfDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.ContentBlockDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.KopfdatenDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.RechnungDto;
import org.example.kalkulationsprogramm.util.RabattRechner;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.springframework.stereotype.Component;

/**
 * Baut aus den Daten, aus denen das sichtbare PDF entsteht, die Daten der eingebetteten
 * E-Rechnung. Die Beträge folgen genau dem Summenblock im PDF
 * ({@code RechnungPdfService#addSummenBlock}) – XML und Papier müssen auf den Cent übereinstimmen:
 * <ul>
 * <li>Jede berechnete Position bis zum Abschluss-Block wird eine Rechnungszeile mit ihrem
 *     Zeilenbetrag aus dem PDF; Alternativ- und Bedarfspositionen zählen wie dort nicht mit.</li>
 * <li>Eine Abschlagsrechnung (fester Nettobetrag) wird eine Zeile über diesen Betrag.</li>
 * <li>Der Pauschalrabatt und bei der Schlussrechnung die bereits gestellten Rechnungen werden
 *     Abzüge auf die ganze Rechnung; die Vorrechnungen stehen zusätzlich als Verweis (BG-3) darin.</li>
 * </ul>
 */
@Component
public class ZugferdRechnungMapper {

    /** Wie im PDF-Summenblock und in der Editor-Vorschau (ClosureBlock.tsx): fest 19 %. */
    static final BigDecimal MWST_SATZ_PROZENT = new BigDecimal("19");

    /** Zahlungsziel, das der Editor ohne Angabe druckt (helpers.ts#DEFAULT_ZAHLUNGSZIEL_TAGE). */
    static final int STANDARD_ZAHLUNGSZIEL_TAGE = 8;

    /** Dokumentarten mit Zahlungsziel – Angebot, Auftragsbestätigung, Gutschrift und Storno fordern nichts. */
    private static final Set<String> RECHNUNGSARTEN = Set.of("rechnung", "teilrechnung", "abschlagsrechnung", "schlussrechnung");
    /** Dokumentarten, die eine frühere Rechnung berichtigen (TypeCode 381, siehe ZugferdErstellService). */
    private static final Set<String> BERICHTIGUNGSARTEN = Set.of("gutschrift", "stornorechnung", "storno");

    /** Zahlungsbedingung auf Belegen, die nichts fordern. */
    static final String KEINE_ZAHLUNG = "Keine Zahlung fällig.";

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** PLZ und Ort, optional mit Länderkürzel davor: „12345 Musterstadt“, „A-1010 Wien“. */
    private static final Pattern PLZ_ORT = Pattern.compile("^(?:([A-Za-z]{1,3})-)?(\\d{4,5})\\s++(\\S.*+)$");

    /** Alte Länderkennzeichen vor der PLZ, die keine ISO-Codes sind. */
    private static final Map<String, String> KENNZEICHEN = Map.ofEntries(
            Map.entry("D", "DE"), Map.entry("A", "AT"), Map.entry("B", "BE"), Map.entry("E", "ES"),
            Map.entry("F", "FR"), Map.entry("H", "HU"), Map.entry("I", "IT"), Map.entry("L", "LU"),
            Map.entry("N", "NO"), Map.entry("P", "PT"), Map.entry("S", "SE"), Map.entry("FL", "LI"));

    private static final Set<String> ISO_LAENDER = Set.of(Locale.getISOCountries());

    /** Ländername (deutsch oder englisch, klein geschrieben) → ISO-Code, für eine Länderzeile unter dem Ort. */
    private static final Map<String, String> LAENDERNAMEN = baueLaendernamen();

    public ZugferdDaten ausRechnung(RechnungDto rechnung) {
        KopfdatenDto kopf = rechnung.kopfdaten();
        ZugferdDaten daten = new ZugferdDaten();
        daten.setGeschaeftsdokumentart(kopf.dokumentTyp());
        daten.setRechnungsnummer(kopf.rechnungsnummer());
        daten.setRechnungsdatum(kopf.rechnungsDatum());
        daten.setLeistungsdatum(kopf.leistungsDatum());
        daten.setKundenName(kopf.kundenName());
        daten.setKundennummer(kopf.kundennummer());
        uebernimmAnschrift(daten, kopf.kundenName(), kopf.kundenAdresse());
        daten.setMwstSatz(MWST_SATZ_PROZENT);

        // Das PDF summiert nur bis zum Abschluss-Block (CLOSURE); was danach steht, ist Schlusstext.
        BigDecimal positionenNetto = BigDecimal.ZERO;
        List<ZugferdArtikelPosition> positionen = new ArrayList<>();
        for (ContentBlockDto block : rechnung.contentBlocks() != null ? rechnung.contentBlocks() : List.<ContentBlockDto>of()) {
            if ("CLOSURE".equals(block.type())) {
                break;
            }
            if (block.isService() && block.gesamt() != null && !block.optional()) {
                positionenNetto = positionenNetto.add(block.gesamt());
                positionen.add(alsPosition(block));
            }
        }

        boolean istAbschlag = rechnung.betragNetto() != null;
        BigDecimal netto = istAbschlag ? rechnung.betragNetto() : positionenNetto;
        daten.setArtikelPositionen(istAbschlag ? new ArrayList<>(List.of(abschlagsPosition(rechnung))) : positionen);

        BigDecimal rabatt = RabattRechner.rabattBetrag(netto, rechnung.globalRabattProzent());
        if (rabatt.signum() != 0) {
            daten.getAbzuege().add(new ZugferdAbzug(RabattRechner.rabattText(rechnung.globalRabattProzent()),
                    rabatt, MWST_SATZ_PROZENT));
        }
        BigDecimal zahlbetragNetto = RabattRechner.normalisiereBetrag(netto).subtract(rabatt);

        // Die Schlussrechnung fordert ihre Positionen abzüglich der bereits gestellten Rechnungen –
        // im PDF nur, wenn ein Basisdokument (Auftrag) dahintersteht.
        AbrechnungsverlaufPdfDto verlauf = rechnung.abrechnungsverlauf();
        boolean hatBasisdokument = verlauf != null && verlauf.basisdokumentBetragNetto() != null
                && verlauf.basisdokumentBetragNetto().signum() > 0;
        if ("Schlussrechnung".equalsIgnoreCase(kopf.dokumentTyp()) && hatBasisdokument && verlauf.positionen() != null) {
            for (AbrechnungspositionPdfDto vorher : verlauf.positionen()) {
                if (vorher.betragNetto() != null && vorher.betragNetto().signum() != 0) {
                    daten.getAbzuege().add(new ZugferdAbzug(abzugsText(vorher), vorher.betragNetto(), MWST_SATZ_PROZENT));
                    zahlbetragNetto = zahlbetragNetto.subtract(vorher.betragNetto());
                }
                if (!istLeer(vorher.dokumentNummer())) {
                    daten.getVorrechnungen().add(new ZugferdBelegverweis(vorher.dokumentNummer().trim(), vorher.datum()));
                }
            }
        }

        // Gutschrift und Stornorechnung nennen die Rechnung, die sie berichtigen (BT-25, Issue #223).
        if (kopf.dokumentTyp() != null
                && BERICHTIGUNGSARTEN.contains(kopf.dokumentTyp().trim().toLowerCase(Locale.ROOT))
                && !istLeer(kopf.bezugsdokument())) {
            daten.getVorrechnungen().add(new ZugferdBelegverweis(kopf.bezugsdokument().trim(),
                    leseDatum(kopf.bezugsdokumentDatum())));
        }

        // Ein Zahlungsziel nur, wo etwas zu zahlen ist: nicht auf Angebot oder Gutschrift und nicht,
        // wenn die Schlussrechnung nach Abzug der Abschläge nichts mehr fordert.
        boolean fordertZahlung = kopf.dokumentTyp() != null
                && RECHNUNGSARTEN.contains(kopf.dokumentTyp().trim().toLowerCase(Locale.ROOT))
                && zahlbetragNetto.signum() > 0;
        if (fordertZahlung && kopf.rechnungsDatum() != null) {
            int tage = kopf.zahlungszielTage() != null ? kopf.zahlungszielTage() : STANDARD_ZAHLUNGSZIEL_TAGE;
            daten.setFaelligkeitsdatum(kopf.rechnungsDatum().plusDays(tage));
        } else if (!fordertZahlung && zahlbetragNetto.signum() > 0) {
            // Gutschrift, Storno, Angebot: positiver Betrag, aber nichts zu zahlen (BR-CO-25 verlangt eine Angabe)
            daten.setZahlungsbedingung(KEINE_ZAHLUNG);
        }
        return daten;
    }

    private static ZugferdArtikelPosition alsPosition(ContentBlockDto block) {
        ZugferdArtikelPosition position = new ZugferdArtikelPosition(null, bezeichnung(block), block.menge(),
                block.einheit(), block.einzelpreis(), null, block.gesamt());
        position.setBeschreibung(ohneHtml(block.beschreibungHtml()));
        position.setMwstSatz(MWST_SATZ_PROZENT);
        position.setRabattProzent(block.rabattProzent());
        return position;
    }

    /** Der Abschlag fordert einen festen Nettobetrag – die Positionen darunter zeigen nur den Auftrag. */
    private static ZugferdArtikelPosition abschlagsPosition(RechnungDto rechnung) {
        BigDecimal betrag = rechnung.betragNetto().setScale(2, RoundingMode.HALF_UP);
        String bezeichnung = istLeer(rechnung.kopfdaten().dokumentTyp()) ? "Abschlag" : rechnung.kopfdaten().dokumentTyp().trim();
        ZugferdArtikelPosition position = new ZugferdArtikelPosition(null, bezeichnung, BigDecimal.ONE, "psch",
                betrag, null, betrag);
        AbrechnungsverlaufPdfDto verlauf = rechnung.abrechnungsverlauf();
        if (verlauf != null && !istLeer(verlauf.basisdokumentTyp())) {
            StringBuilder bezug = new StringBuilder("zu ").append(verlauf.basisdokumentTyp().trim());
            if (!istLeer(verlauf.basisdokumentNummer())) {
                bezug.append(" Nr. ").append(verlauf.basisdokumentNummer().trim());
            }
            if (verlauf.basisdokumentDatum() != null) {
                bezug.append(" vom ").append(verlauf.basisdokumentDatum().format(DATUM));
            }
            position.setBeschreibung(bezug.toString());
        }
        position.setMwstSatz(MWST_SATZ_PROZENT);
        return position;
    }

    private static String bezeichnung(ContentBlockDto block) {
        if (!istLeer(block.beschreibung())) {
            return block.beschreibung().trim();
        }
        return istLeer(block.pos()) ? "Position" : "Position " + block.pos().trim();
    }

    /** Gleiche Schreibweise wie die Zeile „bereits gestellte Rechnungen“ im PDF. */
    private static String abzugsText(AbrechnungspositionPdfDto vorher) {
        String typ = istLeer(vorher.typ()) ? "Rechnung" : vorher.typ().trim();
        typ = typ.substring(0, 1).toUpperCase(Locale.ROOT) + typ.substring(1).toLowerCase(Locale.ROOT);
        StringBuilder text = new StringBuilder("Bereits berechnet: ").append(typ);
        if (!istLeer(vorher.dokumentNummer())) {
            text.append(" Nr. ").append(vorher.dokumentNummer().trim());
        }
        if (vorher.datum() != null) {
            text.append(" vom ").append(vorher.datum().format(DATUM));
        }
        return text.toString();
    }

    /**
     * Zerlegt die Rechnungsanschrift – im Editor ein Freitext, den der Nutzer überschreiben darf –
     * in Straße, PLZ, Ort und Land. Maßgeblich ist die letzte Zeile mit PLZ und Ort; die Zeile
     * darüber ist die Straße, alle übrigen Zeilen werden Adresszusatz – außer einer Länderzeile
     * direkt unter dem Ort. Ohne erkennbare PLZ landet der Text als Straße und Zusatz in der
     * E-Rechnung, das Land bleibt dann Deutschland.
     */
    static void uebernimmAnschrift(ZugferdDaten daten, String kundenName, String anschrift) {
        if (istLeer(anschrift)) {
            return;
        }
        List<String> zeilen = new ArrayList<>(anschrift.lines().map(String::trim).filter(z -> !z.isEmpty()).toList());
        // Das PDF druckt den Namen über die Anschrift – steht er auch darin, ist er keine Adresszeile.
        if (!zeilen.isEmpty() && !istLeer(kundenName) && zeilen.getFirst().equalsIgnoreCase(kundenName.trim())) {
            zeilen.removeFirst();
        }
        if (zeilen.isEmpty()) {
            return;
        }

        int plzZeile = -1;
        Matcher plzOrt = null;
        for (int i = zeilen.size() - 1; i >= 0 && plzZeile < 0; i--) {
            Matcher m = PLZ_ORT.matcher(zeilen.get(i));
            if (m.matches()) {
                plzZeile = i;
                plzOrt = m;
            }
        }
        if (plzOrt == null) {
            daten.setKundenStrasse(zeilen.getFirst());
            setzeZusatz(daten, zeilen.subList(1, zeilen.size()));
            return;
        }

        daten.setKundenPlz(plzOrt.group(2));
        daten.setKundenOrt(plzOrt.group(3).trim());
        String land = landAusKennzeichen(plzOrt.group(1));
        List<String> zusatz = new ArrayList<>(zeilen.subList(0, Math.max(plzZeile - 1, 0)));
        List<String> danach = new ArrayList<>(zeilen.subList(plzZeile + 1, zeilen.size()));
        if (!danach.isEmpty()) {
            String ausLaenderzeile = LAENDERNAMEN.get(danach.getFirst().toLowerCase(Locale.ROOT));
            if (ausLaenderzeile != null) {
                land = ausLaenderzeile;
                danach.removeFirst();
            }
        }
        zusatz.addAll(danach);
        daten.setKundenLand(land);
        if (plzZeile >= 1) {
            daten.setKundenStrasse(zeilen.get(plzZeile - 1));
        }
        setzeZusatz(daten, zusatz);
    }

    private static void setzeZusatz(ZugferdDaten daten, List<String> zeilen) {
        if (!zeilen.isEmpty()) {
            daten.setKundenAdresszusatz(String.join(", ", zeilen));
        }
    }

    private static String landAusKennzeichen(String kennzeichen) {
        if (kennzeichen == null) {
            return null;
        }
        String gross = kennzeichen.toUpperCase(Locale.ROOT);
        if (KENNZEICHEN.containsKey(gross)) {
            return KENNZEICHEN.get(gross);
        }
        return ISO_LAENDER.contains(gross) ? gross : null;
    }

    private static Map<String, String> baueLaendernamen() {
        Map<String, String> namen = new HashMap<>();
        for (String code : Locale.getISOCountries()) {
            Locale land = Locale.of("", code);
            namen.put(land.getDisplayCountry(Locale.GERMAN).toLowerCase(Locale.ROOT), code);
            namen.put(land.getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.ROOT), code);
        }
        return Map.copyOf(namen);
    }

    /**
     * Langtext ohne Formatierung – die E-Rechnung kennt nur reinen Text. Absätze, Listenpunkte
     * und Zeilenumbrüche bleiben als Zeilen erhalten.
     */
    static String ohneHtml(String html) {
        if (istLeer(html)) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int tiefe) {
                if (node instanceof TextNode textNode) {
                    text.append(textNode.text());
                } else if (node instanceof Element element && "br".equals(element.normalName())) {
                    text.append('\n');
                }
            }

            @Override
            public void tail(Node node, int tiefe) {
                if (node instanceof Element element && element.isBlock()) {
                    text.append('\n');
                }
            }
        }, Jsoup.parseBodyFragment(html).body());
        String ergebnis = String.join("\n", text.toString().lines().map(String::strip).filter(z -> !z.isEmpty()).toList());
        return ergebnis.isEmpty() ? null : ergebnis;
    }

    private static boolean istLeer(String wert) {
        return wert == null || wert.isBlank();
    }

    /** Datum aus den Kopfdaten: deutsch (31.12.2026) oder ISO (2026-12-31); sonst null. */
    private static LocalDate leseDatum(String text) {
        if (istLeer(text)) {
            return null;
        }
        String wert = text.trim();
        try {
            return wert.contains(".") ? LocalDate.parse(wert, DATUM) : LocalDate.parse(wert);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }
}
