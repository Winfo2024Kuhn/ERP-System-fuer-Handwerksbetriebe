package org.example.kalkulationsprogramm.service.ids;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.ids.IdsProtokoll;
import org.example.kalkulationsprogramm.util.HtmlKlartext;
import org.example.kalkulationsprogramm.util.SicheresXml;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * IDS-Connect 2.5 (ITEK, Schnittstellenbeschreibung vom 02.11.2020).
 *
 * <p><b>Hinweg:</b> POST als {@code multipart/form-data} an die Shop-Adresse mit {@code action=WKE}
 * („Warenkorb empfangen“: Nutzer stellt im Shop zusammen, der Shop schickt den Warenkorb ohne Bestellung
 * zurück), {@code version=2.5}, {@code kndnr}, {@code name_kunde}, {@code pw_kunde} und {@code hookurl}.</p>
 *
 * <p><b>Rückweg:</b> Der Shop schickt per Browser ein Formular an {@code hookurl}; das Feld {@code warenkorb}
 * enthält XML mit Wurzel {@code Warenkorb}, darin {@code WarenkorbInfo/RueckgabeKZ} und je Artikel ein
 * {@code Order/OrderItem} mit {@code ArtNo}, {@code Qty}, {@code QU} (Pflicht) sowie u. a. {@code Kurztext},
 * {@code Langtext}, {@code NetPrice} (Einkaufspreis des Kunden), {@code PriceBasis}, {@code Hinweis},
 * {@code Fehlertext}, {@code EAN}. Preise sind seit 2.5 optional; Dezimaltrenner ist der Punkt.
 * Unbekannte Elemente werden übergangen. Würth (Intershop) setzt eine XHTML-DOCTYPE-Zeile vor seine XML-Antworten;
 * eine solche einfache DOCTYPE wird hingenommen, eine mit eigenen Definitionen nicht (XXE).</p>
 */
@Component
public class IdsConnect25Adapter implements IdsProtokollAdapter {

    static final int MAX_ZEILEN = 500;
    private static final int PREIS_SKALA = 4;
    private static final int MAX_ZAHL_ZEICHEN = 20;
    private static final java.util.regex.Pattern LEERRAUM = java.util.regex.Pattern.compile("\\s++");
    /** Mindestens zwei Leerraumzeichen am Stück: Würths Füllung zwischen Nummer und Verpackungseinheit. */
    private static final java.util.regex.Pattern FUELLUNG = java.util.regex.Pattern.compile("\\s{2,}+");
    /** Nur Ziffern mit Punkt/Komma – kein Exponent, kein Vorzeichen. Possessiv gegen Backtracking. */
    private static final java.util.regex.Pattern ZAHL = java.util.regex.Pattern.compile("[0-9][0-9.,]*+");
    /** Grenzen der Datenbankspalten (menge NUMERIC(15,3), einkaufspreis NUMERIC(19,4)) mit Luft. */
    static final BigDecimal MAX_MENGE = new BigDecimal("1000000000");
    static final BigDecimal MAX_PREIS = new BigDecimal("1000000000");
    static final BigDecimal MAX_PREISBASIS = new BigDecimal("1000000");

    @Override
    public IdsProtokoll protokoll() {
        return IdsProtokoll.IDS_CONNECT_2_5;
    }

    @Override
    public ShopFormular anmeldeFormular(IdsZugangsdaten zugang, String ruecksprung) {
        Map<String, String> felder = new LinkedHashMap<>();
        felder.put("action", "WKE");
        felder.put("version", "2.5");
        setzeWennVorhanden(felder, "kndnr", zugang.kundennummer());
        setzeWennVorhanden(felder, "name_kunde", zugang.benutzername());
        setzeWennVorhanden(felder, "pw_kunde", zugang.passwort());
        felder.put("hookurl", ruecksprung);
        return new ShopFormular(zugang.shopUrl(), "multipart/form-data", felder);
    }

    @Override
    public IdsWarenkorb leseWarenkorb(IdsRueckgabe rueckgabe) {
        if (rueckgabe == null || rueckgabe.istLeer()) {
            throw new IdsWarenkorbUngueltig("Der Shop hat einen leeren Warenkorb geschickt.");
        }
        Document doc;
        try {
            doc = rueckgabe.warenkorbBytes() != null && rueckgabe.warenkorbBytes().length > 0
                    ? SicheresXml.parseMitEinfacherDoctype(rueckgabe.warenkorbBytes())
                    : SicheresXml.parseMitEinfacherDoctype(rueckgabe.warenkorbText());
        } catch (SicheresXml.NichtLesbar e) {
            throw new IdsWarenkorbUngueltig("Der Warenkorb des Shops ist kein lesbares IDS-XML.", e);
        }
        Element wurzel = doc.getDocumentElement();
        String wurzelName = wurzel.getLocalName() != null ? wurzel.getLocalName() : wurzel.getNodeName();
        if (!"Warenkorb".equals(wurzelName)) {
            throw new IdsWarenkorbUngueltig("Der Shop hat keinen IDS-Warenkorb geschickt.");
        }

        boolean mitBestellung = SicheresXml.elemente(wurzel, "RueckgabeKZ").stream()
                .map(e -> e.getTextContent() == null ? "" : e.getTextContent().toLowerCase(Locale.ROOT))
                .anyMatch(t -> t.contains("bestellung"));

        List<Element> items = SicheresXml.elemente(wurzel, "OrderItem");
        if (items.size() > MAX_ZEILEN) {
            throw new IdsWarenkorbUngueltig("Der Warenkorb hat mehr als " + MAX_ZEILEN + " Artikel.");
        }
        List<IdsWarenkorb.Zeile> zeilen = new ArrayList<>();
        for (Element item : items) {
            IdsWarenkorb.Zeile zeile = leseZeile(SicheresXml.kinder(item));
            if (zeile != null) {
                zeilen.add(zeile);
            }
        }
        if (zeilen.isEmpty()) {
            throw new IdsWarenkorbUngueltig("Der Warenkorb des Shops enthält keine Artikel.");
        }
        return new IdsWarenkorb(mitBestellung, zeilen);
    }

    private static IdsWarenkorb.Zeile leseZeile(Map<String, String> f) {
        String artikelnummer = text(f.get("ArtNo"));
        if (artikelnummer == null) {
            return null;
        }
        artikelnummer = ohneVerpackungseinheit(artikelnummer);
        List<String> hinweise = new ArrayList<>();
        BigDecimal menge = zahl(f.get("Qty"));
        if (menge == null || menge.signum() <= 0 || menge.compareTo(MAX_MENGE) >= 0) {
            menge = BigDecimal.ONE;
            hinweise.add("Der Shop hat keine gültige Menge geschickt – bitte prüfen.");
        }
        String langtext = text(f.get("Langtext"));
        String bezeichnung = text(f.get("Kurztext"));
        if (bezeichnung == null) {
            bezeichnung = langtext != null ? langtext.lines().findFirst().orElse(langtext) : "Artikel " + artikelnummer;
        }
        BigDecimal einkaufspreis = einzelpreis(zahl(f.get("NetPrice")), zahl(f.get("PriceBasis")), menge);
        if (f.get("NetPrice") != null && !f.get("NetPrice").isBlank() && einkaufspreis == null) {
            hinweise.add("Der Shop hat einen unlesbaren Preis geschickt – bitte Einkaufspreis eintragen.");
        }
        String hinweis = text(f.get("Hinweis"));
        if (hinweis != null) {
            hinweise.add(hinweis);
        }
        String fehler = text(f.get("Fehlertext"));
        if (fehler != null) {
            hinweise.add(fehler);
        }
        return new IdsWarenkorb.Zeile(
                kuerze(artikelnummer, 100),
                kuerze(bezeichnung, 255),
                kuerze(langtext, 2000),
                menge.setScale(Math.min(Math.max(menge.scale(), 0), 3), RoundingMode.HALF_UP),
                kuerze(IdsEinheiten.kurzform(f.get("QU")), 20),
                einkaufspreis,
                hinweise.isEmpty() ? null : kuerze(String.join(" ", hinweise), 1000),
                kuerze(text(f.get("EAN")), 20));
    }

    /**
     * Würth schickt die Artikelnummer auf feste Breite aufgefüllt und hängt die Verpackungseinheit an
     * („08925304     24“ = Nummer 08925304, VE 24). Ein Block aus mindestens zwei Leerzeichen trennt also
     * Nummer und Zusatz; der Zusatz fällt weg, weil Menge und Preis ohnehin je Stück gerechnet werden.
     * Einzelne Leerzeichen gehören zur Nummer („0903 100 100“) und bleiben.
     */
    static String ohneVerpackungseinheit(String artikelnummer) {
        java.util.regex.Matcher m = FUELLUNG.matcher(artikelnummer);
        String nummer = m.find() ? artikelnummer.substring(0, m.start()) : artikelnummer;
        return LEERRAUM.matcher(nummer.trim()).replaceAll(" ");
    }

    /**
     * Einkaufspreis je einer Einheit. Laut Spezifikation „bezieht sich [NetPrice] immer auf die Anfragemenge“,
     * ist also der Preis der ganzen Zeile – so schickt es Würth (144 Kartuschen: NetPrice 691,20 → 4,80 je Stück).
     * Nennt der Shop eine Preisbasis über 1 (Preis je 100 Stück), gilt der Preis je dieser Basis.
     */
    static BigDecimal einzelpreis(BigDecimal preis, BigDecimal basis, BigDecimal menge) {
        if (preis == null || preis.signum() < 0 || preis.compareTo(MAX_PREIS) >= 0) {
            return null;
        }
        boolean mitBasis = basis != null && basis.compareTo(BigDecimal.ONE) > 0 && basis.compareTo(MAX_PREISBASIS) <= 0;
        BigDecimal teiler = mitBasis ? basis : (menge == null || menge.signum() <= 0 ? BigDecimal.ONE : menge);
        return preis.divide(teiler, PREIS_SKALA, RoundingMode.HALF_UP);
    }

    /**
     * Liest eine Zahl aus dem Shop. Laut Spezifikation Punkt ohne Tausendertrenner; manche Shops schicken
     * trotzdem ein Komma oder deutsches Format. Kommen Punkt und Komma vor, ist das letzte der Dezimaltrenner
     * ({@code 1.234,56} und {@code 1,234.56}). Exponenten, Vorzeichen und überlange Werte werden nicht gelesen.
     */
    static BigDecimal zahl(String roh) {
        String t = text(roh);
        if (t == null || t.length() > MAX_ZAHL_ZEICHEN || !ZAHL.matcher(t).matches()) {
            return null;
        }
        int punkt = t.lastIndexOf('.');
        int komma = t.lastIndexOf(',');
        String normal;
        if (punkt >= 0 && komma >= 0) {
            normal = komma > punkt ? t.replace(".", "").replace(',', '.') : t.replace(",", "");
        } else {
            normal = t.replace(',', '.');
        }
        try {
            return new BigDecimal(normal);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Getrimmter Klartext ohne HTML; leer wird {@code null}. */
    private static String text(String roh) {
        if (roh == null) {
            return null;
        }
        String t = HtmlKlartext.entferneTags(roh).trim();
        return t.isEmpty() ? null : t;
    }

    private static String kuerze(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    private static void setzeWennVorhanden(Map<String, String> felder, String name, String wert) {
        if (wert != null && !wert.isBlank()) {
            felder.put(name, wert);
        }
    }
}
