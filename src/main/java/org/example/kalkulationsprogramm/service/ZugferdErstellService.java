package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.example.kalkulationsprogramm.exception.FirmenstammdatenUnvollstaendigException;
import org.mustangproject.BankDetails;
import org.mustangproject.Contact;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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

                Invoice invoice = new Invoice();
                String dokumentart = normalizeDokumentenart(daten.getGeschaeftsdokumentart());
                invoice.setNumber(daten.getRechnungsnummer());

                // ZUGFeRD requires an issue date - use today's date as fallback
                LocalDate issueDate = daten.getRechnungsdatum() != null
                        ? daten.getRechnungsdatum()
                        : LocalDate.now();
                invoice.setIssueDate(toDate(issueDate));
                if (istRechnung(dokumentart) && daten.getFaelligkeitsdatum() != null) {
                    invoice.setDueDate(toDate(daten.getFaelligkeitsdatum()));
                }

                TradeParty buyer = new TradeParty();
                buyer.setName(daten.getKundenName() != null ? daten.getKundenName() : "Kunde");
                if (daten.getKundennummer() != null) {
                    buyer.setID(daten.getKundennummer());
                }

                invoice.setSender(seller);
                invoice.setRecipient(buyer);

                BigDecimal mwstSatz = alsProzent(daten.getMwstSatz());

                Product product = new Product();
                product.setName(dokumentart);
                product.setVATPercent(mwstSatz);

                // Eine Sammelposition mit dem Nettobetrag; die USt rechnet das Format selbst obendrauf.
                invoice.addItem(new Item(product, ermittleNetto(daten, mwstSatz), BigDecimal.ONE));

                exporter.setTransaction(invoice);
                exporter.export(ziel.toString());
            }

            return ziel;
        } catch (Exception e) {
            throw new RuntimeException("ZUGFeRD Erstellung fehlgeschlagen", e);
        }
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

    private String normalizeDokumentenart(String art) {
        if (art == null) {
            return "Rechnung";
        }
        String trimmed = art.trim();
        return trimmed.isEmpty() ? "Rechnung" : trimmed;
    }

    private boolean istRechnung(String art) {
        return "rechnung".equalsIgnoreCase(art);
    }
}
