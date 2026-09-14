package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KasseDatevExportServiceTest {
    private final KasseDatevExportService service = new KasseDatevExportService();

    @Test
    void goldenDateiUndSpaltenpositionen() throws Exception {
        Beleg einnahme = beleg(1, BelegKategorie.KASSE_EINNAHME, "119.00", "100.00", "19", "8400");
        Beleg ausgabe = beleg(2, BelegKategorie.KASSE_AUSGABE, "10.70", "10.00", "7", "4930");
        ausgabe.setBeschreibung("Bürobedarf");
        ausgabe.setKostenstelle(kostenstelle("Werkstatt"));
        Beleg transfer = beleg(3, BelegKategorie.KASSE_EINNAHME, "200.00", "200.00", "0", null);
        transfer.setQuelle(BelegQuelle.TRANSFER);
        transfer.setBeschreibung("Überweisung Bank an Kasse");
        Beleg split = beleg(4, BelegKategorie.KASSE_AUSGABE, "119.00", "100.00", "19", "4930");
        var p = parameter(List.of(split, transfer, ausgabe, einnahme),
                Map.of(4L, List.of(anteil("60.00", "Baustelle A"), anteil("40.00", "Werkstatt"))), false);
        String csv = service.erzeugeCsv(p);
        try (var in = getClass().getResourceAsStream("/datev/buchungsstapel-golden.csv")) {
            assertThat(in).isNotNull();
            assertThat(csv).isEqualTo(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        var lines = csv.split("\r\n");
        assertThat(lines).hasSize(7);
        assertThat(lines[0].split(";", -1)).hasSize(22);
        for (int i = 1; i < lines.length; i++) assertThat(lines[i].split(";", -1)).hasSize(125);
        assertThat(lines[5].split(";", -1)[0]).isEqualTo("71,40");
        assertThat(lines[6].split(";", -1)[0]).isEqualTo("47,60");
        assertThat(new String(service.erzeugeCsvBytes(p), Charset.forName("windows-1252"))).isEqualTo(csv);
        assertThat(csv).doesNotEndWith("\r\n\r\n");
    }

    @ParameterizedTest
    @CsvSource({"KASSE_AUSGABE,19,9", "KASSE_AUSGABE,7,8", "KASSE_EINNAHME,19,3",
            "KASSE_EINNAHME,7,2", "KASSE_AUSGABE,0,''", "PRIVATEINLAGE,19,''", "PRIVATENTNAHME,7,''"})
    void steuerDatumUndUmsatz(BelegKategorie kategorie, String steuer, String bu) {
        String[] row = zeilen(parameter(List.of(beleg(1, kategorie, "12.34", "10.00", steuer, "4930")), Map.of(), false))[2].split(";", -1);
        assertThat(row[0]).isEqualTo("12,34");
        assertThat(row[1]).isEqualTo("S");
        assertThat(row[2]).isEqualTo("EUR");
        assertThat(row[8]).isEqualTo(bu);
        assertThat(row[9]).isEqualTo("0208");
        assertThat(row[10]).isEqualTo("1");
        assertThat(row[113]).isEqualTo("1");
    }

    @Test
    void transferHatKeinenSteuerschluesselUndBankkonto() {
        Beleg b = beleg(1, BelegKategorie.KASSE_AUSGABE, "119", "100", "19", null);
        b.setQuelle(BelegQuelle.TRANSFER);
        String[] row = zeilen(parameter(List.of(b), Map.of(), true))[2].split(";", -1);
        assertThat(row[6]).isEqualTo("1200");
        assertThat(row[7]).isEqualTo("1000");
        assertThat(row[8]).isEmpty();
        assertThat(row[13]).doesNotContain("PRÜFEN");
    }

    @Test
    void gegenbuchungKehrtUrspruenglicheSteuerUm() {
        Beleg b = beleg(1, BelegKategorie.KASSE_EINNAHME, "119", "100", "19", "4930");
        b.setStornoFuerBelegId(42L);
        String[] row = zeilen(parameter(List.of(b), Map.of(), false))[2].split(";", -1);
        assertThat(row[6]).isEqualTo("1000");
        assertThat(row[7]).isEqualTo("4930");
        assertThat(row[8]).isEqualTo("9");
    }

    @Test
    void migrierteGegenbuchungMitAufwandskontoIstKeinBanktransfer() {
        Beleg b = beleg(1, BelegKategorie.KASSE_EINNAHME, "119", "100", "19", "4930");
        b.setStornoFuerBelegId(42L);
        b.setQuelle(BelegQuelle.TRANSFER); // V375 klassifizierte alle istUmbuchung-Belege so.
        b.getSachkonto().setKontoTyp(SachkontoTyp.AUFWAND);
        String[] row = zeilen(parameter(List.of(b), Map.of(), false))[2].split(";", -1);
        assertThat(row[7]).isEqualTo("4930");
        assertThat(row[8]).isEqualTo("9");
    }

    @Test
    void offeneBuchungUndFehlendesKontoWerdenMarkiert() {
        Beleg b = beleg(1, BelegKategorie.KASSE_AUSGABE, "12.34", null, "0", null);
        b.setFestgeschrieben(false);
        b.setLaufendeNummer(null);
        b.setBeschreibung("Langer Text ".repeat(10));
        String[] lines = zeilen(parameter(List.of(b), Map.of(), true));
        assertThat(lines[0].split(";", -1)[20]).isEqualTo("0");
        String[] row = lines[2].split(";", -1);
        assertThat(row[6]).isEmpty();
        assertThat(row[10]).isEmpty();
        assertThat(row[13]).startsWith("\"PRÜFEN: ").hasSize(62);
        assertThat(row[113]).isEqualTo("0");
    }

    @Test
    void escapiertTexteUndErsetztNichtDarstellbareZeichen() {
        Beleg b = beleg(1, BelegKategorie.KASSE_AUSGABE, "12.34", null, "0", "4930");
        b.setBeschreibung("Büro; \"Notiz\"\r\n😀");
        var p = parameter(List.of(b), Map.of(), false);
        assertThat(zeilen(p)[2].split(";", -1)[13]).isEqualTo("\"Büro  \"\"Notiz\"\"  😀\"");
        byte[] bytes = service.erzeugeCsvBytes(p);
        assertThat(bytes[0]).isEqualTo((byte) '"');
        assertThat(new String(bytes, Charset.forName("windows-1252"))).contains("Büro", "?").doesNotContain("😀");
    }

    @Test
    void nurKassenbewegungenUndStabileReihenfolge() {
        Beleg offen2 = beleg(4, BelegKategorie.KASSE_AUSGABE, "4", null, "0", "4930");
        Beleg offen1 = beleg(3, BelegKategorie.KASSE_AUSGABE, "3", null, "0", "4930");
        for (Beleg b : List.of(offen1, offen2)) { b.setLaufendeNummer(null); b.setFestgeschrieben(false); }
        Beleg bank = beleg(2, BelegKategorie.BANK, "2", null, "0", null);
        String[] lines = zeilen(parameter(List.of(offen2, bank, offen1,
                beleg(1, BelegKategorie.PRIVATEINLAGE, "1", null, "0", null)), Map.of(), false));
        assertThat(lines).hasSize(5);
        assertThat(lines[2]).startsWith("1,00;");
        assertThat(lines[3]).startsWith("3,00;");
        assertThat(lines[4]).startsWith("4,00;");
    }

    @Test
    void unvollstaendigeZuordnungErhaeltRestOhneKostenstelle() {
        Beleg b = beleg(1, BelegKategorie.KASSE_AUSGABE, "119", "100", "19", "4930");
        String[] lines = zeilen(parameter(List.of(b), Map.of(1L, List.of(anteil("60", "Werkstatt"))), false));
        assertThat(lines).hasSize(4);
        assertThat(lines[2]).startsWith("71,40;");
        assertThat(lines[3]).startsWith("47,60;");
        assertThat(lines[3].split(";", -1)[36]).isEmpty();
    }

    @Test
    void rundungsrestBleibtCentgenau() {
        Beleg b = beleg(1, BelegKategorie.KASSE_AUSGABE, "0.05", "0.03", "19", "4930");
        String[] lines = zeilen(parameter(List.of(b), Map.of(1L, List.of(
                anteil("0.01", "A"), anteil("0.01", "B"), anteil("0.01", "C"))), false));
        assertThat(lines[2]).startsWith("0,02;");
        assertThat(lines[3]).startsWith("0,01;");
        assertThat(lines[4]).startsWith("0,02;");
    }

    @Test
    void mischbonFuehrtPrivatanteilOhneSteuerUndKostenstelle() {
        Beleg b = beleg(1, BelegKategorie.KASSE_AUSGABE, "119", "100", "19", "4930");
        b.setAufteilungsModus(BelegAufteilungsModus.TEILWEISE);
        b.setBetragFirmaNetto(new BigDecimal("60"));
        b.setBetragFirmaBrutto(new BigDecimal("71.40"));
        String[] lines = zeilen(parameter(List.of(b), Map.of(1L, List.of(anteil("60", "Werkstatt"))), false));
        assertThat(lines).hasSize(4);
        assertThat(lines[2]).startsWith("71,40;");
        String[] privat = lines[3].split(";", -1);
        assertThat(privat[0]).isEqualTo("47,60");
        assertThat(privat[6]).isEqualTo("1800");
        assertThat(privat[7]).isEqualTo("1000");
        assertThat(privat[8]).isEmpty();
        assertThat(privat[36]).isEmpty();
    }

    @Test
    void ungueltigeSplitsErzeugenKeineFalschenBuchungen() {
        Beleg b = beleg(1, BelegKategorie.KASSE_AUSGABE, "119", "100", "19", "4930");
        assertThatThrownBy(() -> service.erzeugeCsv(parameter(List.of(b),
                Map.of(1L, List.of(anteil("110", "Werkstatt"))), false)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Aufteilung");
    }

    @Test
    void leererExportBehauptetKeinenAbgeschlossenenMonat() {
        assertThat(zeilen(parameter(List.of(), Map.of(), false))[0].split(";", -1)[20]).isEqualTo("0");
    }

    private String[] zeilen(KasseDatevExportService.Parameter p) { return service.erzeugeCsv(p).split("\r\n"); }

    private static KasseDatevExportService.Parameter parameter(List<Beleg> belege,
            Map<Long, List<BelegKostenstellenAnteil>> splits, boolean trotzdem) {
        KasseEinstellung e = new KasseEinstellung();
        e.setDatevBeraternummer("0123456");
        e.setDatevMandantennummer("00123");
        e.setWirtschaftsjahrBeginnMonat(7);
        return new KasseDatevExportService.Parameter(YearMonth.of(2026, 8), belege, splits, e,
                "Max Mustermann", "Musterbetrieb GmbH", LocalDateTime.of(2026, 9, 1, 10, 20, 30, 123000000), trotzdem);
    }

    private static Beleg beleg(long id, BelegKategorie k, String brutto, String netto, String mwst, String konto) {
        Beleg b = new Beleg(); b.setId(id); b.setLaufendeNummer(id); b.setFestgeschrieben(true);
        b.setBelegKategorie(k); b.setBetragBrutto(new BigDecimal(brutto));
        b.setBetragNetto(netto == null ? null : new BigDecimal(netto));
        b.setMwstSatz(new BigDecimal(mwst)); b.setBelegDatum(LocalDate.of(2026, 8, 2));
        b.setBeschreibung("Buchung " + id);
        if (konto != null) { Sachkonto s = new Sachkonto(); s.setNummer(konto); b.setSachkonto(s); }
        return b;
    }

    private static Kostenstelle kostenstelle(String name) { Kostenstelle k = new Kostenstelle(); k.setBezeichnung(name); return k; }

    private static BelegKostenstellenAnteil anteil(String betrag, String name) {
        BelegKostenstellenAnteil a = new BelegKostenstellenAnteil();
        a.setBerechneterBetrag(new BigDecimal(betrag)); a.setKostenstelle(kostenstelle(name)); return a;
    }
}
