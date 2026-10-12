package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdAbzug;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdBelegverweis;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.example.kalkulationsprogramm.exception.FirmenstammdatenUnvollstaendigException;
import org.example.kalkulationsprogramm.util.EinheitenCode;
import org.example.kalkulationsprogramm.util.RabattRechner;
import org.mustangproject.Allowance;
import org.mustangproject.BankDetails;
import org.mustangproject.Charge;
import org.mustangproject.Contact;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.ReferencedDocument;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3;
import org.mustangproject.ZUGFeRD.model.DocumentCodeTypeConstants;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ZugferdErstellService {

    /** Firmeninformation kennt kein Länderfeld – der Betrieb sitzt in Deutschland. */
    private static final String LAENDERCODE_DEUTSCHLAND = "DE";

    /** Ländercode (2 Buchstaben) gefolgt von der Nummer, ohne Leerzeichen. */
    private static final Pattern USTID_FORMAT = Pattern.compile("^[A-Z]{2}[A-Z0-9]{2,12}$");

    /** Regelsteuersatz in Prozent, wenn die Daten keinen Satz mitbringen. */
    private static final BigDecimal STANDARD_MWST_SATZ = new BigDecimal("19");

    private static final DateTimeFormatter DATUM_DEUTSCH = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Dokumentarten, die als Gutschrift (UNTDID 1001: 381) ausgestellt werden. */
    private static final Set<String> GUTSCHRIFTARTEN = Set.of("gutschrift", "stornorechnung", "storno");

    private final FirmeninformationService firmeninformationService;

    private static Date toDate(LocalDate ld) {
        return ld == null ? null : Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    public Path erzeuge(String originalPdfPath, ZugferdDaten daten) {
        // Vor dem Erzeugen prüfen: lieber sprechend abbrechen als eine ungültige E-Rechnung ausstellen.
        TradeParty seller = baueVerkaeufer(firmeninformationService.getFirmeninformation());
        try {
            Path ziel = Files.createTempFile("zugferd-", ".pdf.html");

            try (ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()
                    // .setProfile(Profile.EN16931) // weggelassen → vermeidet Versionskonflikte
                    .setCreator("Kalkulationsprogramm")
                    .setProducer("Kalkulationsprogramm")
                    .load(originalPdfPath)) {
                exporter.setTransaction(baueRechnung(daten, seller));
                exporter.export(ziel.toString());
            }

            return ziel;
        } catch (Exception e) {
            throw new RuntimeException("ZUGFeRD Erstellung fehlgeschlagen", e);
        }
    }

    /**
     * Baut die E-Rechnung nach EN 16931: Kopf, Käufer, eine Zeile je Position mit eigenem
     * Steuersatz, Abzüge auf Belegebene. Die Summen rechnet Mustang aus den Zeilen.
     */
    Invoice baueRechnung(ZugferdDaten daten, TradeParty seller) {
        Invoice invoice = new Invoice();
        invoice.setNumber(daten.getRechnungsnummer());
        if (daten.getGeschaeftsdokumentart() != null
                && GUTSCHRIFTARTEN.contains(daten.getGeschaeftsdokumentart().trim().toLowerCase(Locale.ROOT))) {
            // Gutschrift und Storno geben Geld zurück bzw. heben eine Rechnung auf: Gutschrift (381) statt Rechnung (380)
            invoice.setDocumentCode(DocumentCodeTypeConstants.CREDITNOTE);
        }

        // ZUGFeRD requires an issue date - use today's date as fallback
        LocalDate issueDate = daten.getRechnungsdatum() != null
                ? daten.getRechnungsdatum()
                : LocalDate.now();
        invoice.setIssueDate(toDate(issueDate));
        if (daten.getLeistungsdatum() != null) {
            invoice.setDeliveryDate(toDate(daten.getLeistungsdatum()));
        }
        if (daten.getFaelligkeitsdatum() != null) {
            invoice.setDueDate(toDate(daten.getFaelligkeitsdatum()));
            // Sonst schreibt Mustang „Please remit until …“ auf eine deutsche Rechnung
            invoice.setPaymentTermDescription("Zahlbar bis " + daten.getFaelligkeitsdatum().format(DATUM_DEUTSCH) + ".");
        } else if (!istLeer(daten.getZahlungsbedingung())) {
            // BR-CO-25: Ohne Fälligkeit braucht ein positiver Betrag wenigstens eine Zahlungsbedingung
            invoice.setPaymentTermDescription(daten.getZahlungsbedingung().trim());
        }

        invoice.setSender(seller);
        invoice.setRecipient(baueKaeufer(daten));

        BigDecimal rechnungsSatz = alsProzent(daten.getMwstSatz());
        List<ZugferdArtikelPosition> positionen = daten.getArtikelPositionen();
        if (positionen == null || positionen.isEmpty()) {
            // Ohne Positionen eine Sammelposition mit dem Nettobetrag; die USt rechnet das Format selbst obendrauf.
            Product product = new Product();
            product.setName(normalizeDokumentenart(daten.getGeschaeftsdokumentart()));
            product.setUnit(EinheitenCode.EINHEIT);
            product.setVATPercent(rechnungsSatz);
            invoice.addItem(new Item(product, ermittleNetto(daten, rechnungsSatz), BigDecimal.ONE));
        } else {
            for (ZugferdArtikelPosition position : positionen) {
                invoice.addItem(bauePosition(position, rechnungsSatz));
            }
        }
        if (daten.getAbzuege() != null) {
            for (ZugferdAbzug abzug : daten.getAbzuege()) {
                fuegeAbzugHinzu(invoice, abzug, rechnungsSatz);
            }
        }
        if (daten.getVorrechnungen() != null) {
            for (ZugferdBelegverweis vorrechnung : daten.getVorrechnungen()) {
                if (vorrechnung != null && !istLeer(vorrechnung.nummer())) {
                    invoice.addInvoiceReferencedDocument(
                            new ReferencedDocument(vorrechnung.nummer().trim(), toDate(vorrechnung.datum())));
                }
            }
        }
        return invoice;
    }

    /**
     * Eine Rechnungszeile (BG-25). Mustang rechnet die Zeile als Menge × Einzelpreis − Abzug.
     * Den Abzug bemessen wir so, dass genau der Zeilenbetrag aus dem PDF herauskommt – der
     * Positionsrabatt ist dort schon nach der festen Rundungsregel abgezogen.
     */
    private static Item bauePosition(ZugferdArtikelPosition position, BigDecimal rechnungsSatz) {
        BigDecimal menge = position.getMenge() != null ? position.getMenge() : BigDecimal.ONE;
        BigDecimal einzelpreis = position.getEinzelpreis() != null ? position.getEinzelpreis() : BigDecimal.ZERO;
        if (einzelpreis.signum() < 0) {
            // BR-27: Der Einzelpreis darf nicht negativ sein. Eine Gutschrift-Position („Material
            // vom Kunden gestellt, −50 €“) wird deshalb zur negativen Menge – der Betrag bleibt gleich.
            einzelpreis = einzelpreis.negate();
            menge = menge.negate();
        }

        Product product = new Product();
        product.setName(istLeer(position.getBezeichnung()) ? "Position" : position.getBezeichnung().trim());
        if (!istLeer(position.getBeschreibung())) {
            product.setDescription(position.getBeschreibung().trim());
        }
        if (!istLeer(position.getExterneArtikelnummer())) {
            product.setSellerAssignedID(position.getExterneArtikelnummer().trim());
        }
        product.setUnit(EinheitenCode.unEceCode(position.getMengeneinheit()));
        product.setVATPercent(position.getMwstSatz() != null ? alsProzent(position.getMwstSatz()) : rechnungsSatz);

        Item item = new Item(product, einzelpreis, menge);
        if (position.getGesamtpreisNetto() != null) {
            BigDecimal ohneRabatt = menge.multiply(einzelpreis).setScale(2, RoundingMode.HALF_UP);
            BigDecimal differenz = ohneRabatt.subtract(position.getGesamtpreisNetto().setScale(2, RoundingMode.HALF_UP));
            if (differenz.signum() > 0) {
                item.addAllowance(new Allowance(differenz).setReason(RabattRechner.rabattText(position.getRabattProzent())));
            } else if (differenz.signum() < 0) {
                // Nur bei negativer Menge mit Rabatt: der Abzug kehrt sich dort zum Zuschlag um
                item.addCharge(new Charge(differenz.negate()).setReason(RabattRechner.rabattText(position.getRabattProzent())));
            }
        }
        return item;
    }

    /**
     * Abzug (BG-20) bzw. – bei negativem Betrag – Zuschlag (BG-21) auf die ganze Rechnung.
     * Mustang fasst Abzüge mit gleichem Steuersatz im XML zu einem zusammen und reiht die
     * Gründe aneinander; die Beträge bleiben richtig.
     */
    private static void fuegeAbzugHinzu(Invoice invoice, ZugferdAbzug abzug, BigDecimal rechnungsSatz) {
        if (abzug == null || abzug.betragNetto() == null || abzug.betragNetto().signum() == 0) {
            return;
        }
        BigDecimal betrag = abzug.betragNetto().setScale(2, RoundingMode.HALF_UP);
        BigDecimal satz = abzug.mwstSatz() != null ? alsProzent(abzug.mwstSatz()) : rechnungsSatz;
        String grund = !istLeer(abzug.grund()) ? abzug.grund().trim() : betrag.signum() > 0 ? "Nachlass" : "Zuschlag";
        if (betrag.signum() > 0) {
            invoice.addAllowance(new Allowance(betrag).setReason(grund).setTaxPercent(satz));
        } else {
            invoice.addCharge(new Charge(betrag.negate()).setReason(grund).setTaxPercent(satz));
        }
    }

    /**
     * Käufer (BG-7) mit Anschrift (BG-8). Der Ländercode ist Pflicht (BR-11) – ohne Angabe
     * sitzt der Kunde in Deutschland. Leere Felder bleiben weg: leere XML-Elemente lehnen
     * strenge Empfänger ab.
     */
    private static TradeParty baueKaeufer(ZugferdDaten daten) {
        TradeParty buyer = new TradeParty();
        buyer.setName(istLeer(daten.getKundenName()) ? "Kunde" : daten.getKundenName().trim());
        if (!istLeer(daten.getKundennummer())) {
            buyer.setID(daten.getKundennummer().trim());
        }
        if (!istLeer(daten.getKundenStrasse())) {
            buyer.setStreet(daten.getKundenStrasse().trim());
        }
        if (!istLeer(daten.getKundenAdresszusatz())) {
            buyer.setAdditionalAddress(daten.getKundenAdresszusatz().trim());
        }
        if (!istLeer(daten.getKundenPlz())) {
            buyer.setZIP(daten.getKundenPlz().trim());
        }
        if (!istLeer(daten.getKundenOrt())) {
            buyer.setLocation(daten.getKundenOrt().trim());
        }
        buyer.setCountry(istLeer(daten.getKundenLand())
                ? LAENDERCODE_DEUTSCHLAND
                : daten.getKundenLand().trim().toUpperCase(Locale.ROOT));
        if (!istLeer(daten.getEmail())) {
            buyer.setEmail(daten.getEmail().trim());
        }
        return buyer;
    }

    /**
     * Prüft vorab, ob die Firmenstammdaten für eine E-Rechnung reichen – ohne etwas zu erzeugen.
     * Wirft {@link FirmenstammdatenUnvollstaendigException}, sonst passiert nichts.
     */
    public void pruefeVerkaeuferdaten() {
        baueVerkaeufer(firmeninformationService.getFirmeninformation());
    }

    /**
     * Baut den Verkäufer (BG-4) aus den Firmenstammdaten. Pflicht nach EN 16931 bzw. §14 UStG:
     * Name, Anschrift (BT-35/37/38) und eine steuerliche Kennung – USt-IdNr. (BT-31) oder
     * Steuernummer (BT-32), sonst verletzt die Rechnung BR-S-02.
     * BR-CO-26 verlangt zusätzlich BT-29/30/31: Die Steuernummer allein erfüllt das nicht,
     * deshalb dient sie ohne USt-IdNr. zugleich als Kennung des Verkäufers (BT-29).
     * Kontakt und Bankverbindung sind optional und werden nur gesetzt, wenn hinterlegt.
     */
    TradeParty baueVerkaeufer(FirmeninformationDto firma) {
        List<String> fehlend = new ArrayList<>();
        if (istLeer(firma.getFirmenname())) fehlend.add("Firmenname");
        if (istLeer(firma.getStrasse())) fehlend.add("Straße");
        if (istLeer(firma.getPlz())) fehlend.add("Postleitzahl");
        if (istLeer(firma.getOrt())) fehlend.add("Ort");
        String ustIdNr = istLeer(firma.getUstIdNr()) ? null : firma.getUstIdNr().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (ustIdNr == null && istLeer(firma.getSteuernummer())) {
            fehlend.add("Umsatzsteuer-ID oder Steuernummer");
        } else if (ustIdNr != null && !USTID_FORMAT.matcher(ustIdNr).matches()) {
            // BR-CO-9: USt-IdNr. beginnt mit dem Ländercode
            fehlend.add("Umsatzsteuer-ID im Format DE123456789");
        }
        if (!fehlend.isEmpty()) {
            throw new FirmenstammdatenUnvollstaendigException("die E-Rechnung", fehlend);
        }

        TradeParty seller = new TradeParty();
        seller.setName(firma.getFirmenname().trim());
        seller.setStreet(firma.getStrasse().trim());
        seller.setZIP(firma.getPlz().trim());
        seller.setLocation(firma.getOrt().trim());
        seller.setCountry(LAENDERCODE_DEUTSCHLAND);
        if (ustIdNr != null) {
            seller.setVATID(ustIdNr);
        }
        if (!istLeer(firma.getSteuernummer())) {
            seller.setTaxID(firma.getSteuernummer().trim());
            if (ustIdNr == null) {
                seller.setID(firma.getSteuernummer().trim());
            }
        }
        if (!istLeer(firma.getEmail())) {
            seller.setEmail(firma.getEmail().trim());
        }
        if (!istLeer(firma.getTelefon()) || !istLeer(firma.getEmail())) {
            String ansprechpartner = !istLeer(firma.getGeschaeftsfuehrer())
                    ? firma.getGeschaeftsfuehrer().trim()
                    : firma.getFirmenname().trim();
            seller.setContact(new Contact(ansprechpartner,
                    istLeer(firma.getTelefon()) ? null : firma.getTelefon().trim(),
                    istLeer(firma.getEmail()) ? null : firma.getEmail().trim()));
        }
        if (!istLeer(firma.getIban())) {
            BankDetails bank = new BankDetails(firma.getIban().replaceAll("\\s+", ""));
            if (!istLeer(firma.getBic())) {
                bank.setBIC(firma.getBic().replaceAll("\\s+", ""));
            }
            bank.setAccountName(firma.getFirmenname().trim());
            seller.addBankDetails(bank);
        }
        return seller;
    }

    /**
     * Nettobetrag der Sammelposition: der ausgewiesene Nettobetrag, sonst wird er aus dem
     * Bruttobetrag ({@link ZugferdDaten#getBetrag()}) und dem Steuersatz zurückgerechnet.
     */
    private static BigDecimal ermittleNetto(ZugferdDaten daten, BigDecimal mwstSatz) {
        if (daten.getBetragNetto() != null) {
            return daten.getBetragNetto().setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal brutto = daten.getBetrag() != null ? daten.getBetrag() : BigDecimal.ZERO;
        BigDecimal faktor = BigDecimal.ONE.add(mwstSatz.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        return brutto.divide(faktor, 2, RoundingMode.HALF_UP);
    }

    /**
     * Steuersatz in Prozent (19). Im Programm kommt der Satz auch als Anteil vor (0.19, z. B. aus
     * dem ZUGFeRD-Extractor) – Werte zwischen 0 und 1 werden deshalb als Anteil gelesen, statt eine
     * Rechnung mit 0,19 % USt zu erzeugen. Ohne Angabe gilt der Regelsatz.
     */
    static BigDecimal alsProzent(BigDecimal satz) {
        if (satz == null) {
            return STANDARD_MWST_SATZ;
        }
        if (satz.signum() > 0 && satz.compareTo(BigDecimal.ONE) < 0) {
            return satz.multiply(new BigDecimal("100")).stripTrailingZeros();
        }
        return satz;
    }

    private static boolean istLeer(String wert) {
        return wert == null || wert.isBlank();
    }

    private static String normalizeDokumentenart(String art) {
        if (art == null) {
            return "Rechnung";
        }
        String trimmed = art.trim();
        return trimmed.isEmpty() ? "Rechnung" : trimmed;
    }
}
