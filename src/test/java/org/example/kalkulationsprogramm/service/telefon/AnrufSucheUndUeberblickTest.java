package org.example.kalkulationsprogramm.service.telefon;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Sprachnachricht;
import org.example.kalkulationsprogramm.domain.SteuerberaterAnsprechpartner;
import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.example.kalkulationsprogramm.domain.TelefonAnruf;
import org.example.kalkulationsprogramm.domain.TelefonAnrufArt;
import org.example.kalkulationsprogramm.domain.TelefonZuordnung;
import org.example.kalkulationsprogramm.dto.Telefon.AnrufKontaktUeberblickDto;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.SprachnachrichtRepository;
import org.example.kalkulationsprogramm.repository.SteuerberaterKontaktRepository;
import org.example.kalkulationsprogramm.repository.TelefonAnrufRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wortweise Suche der Anrufliste über alle Kontaktdaten und der Überblick
 * für das Anruf-Fenster – auf H2 mit echten Abfragen.
 */
@DataJpaTest
@Import(AnrufKontaktUeberblickService.class)
class AnrufSucheUndUeberblickTest {

    private static final LocalDateTime ZEIT = LocalDateTime.of(2026, 9, 29, 8, 0);

    @Autowired TelefonAnrufRepository anrufe;
    @Autowired KundeRepository kunden;
    @Autowired LieferantenRepository lieferanten;
    @Autowired ProjektRepository projekte;
    @Autowired SteuerberaterKontaktRepository steuerberater;
    @Autowired AnfrageRepository anfragen;
    @Autowired AusgangsGeschaeftsDokumentRepository dokumente;
    @Autowired AnrufKontaktUeberblickService ueberblickService;
    @Autowired SprachnachrichtRepository nachrichten;

    private Kunde mustermann;
    private Kunde musterfrau;
    private Lieferanten stahl;
    private SteuerberaterKontakt kanzlei;

    @BeforeEach
    void setUp() {
        mustermann = kunde("K-1", "Max Mustermann", "Erika Beispiel", "Hauptstraße 1", "97070", "Würzburg",
                "max.mustermann@example.com");
        musterfrau = kunde("K-2", "Maria Musterfrau", null, "Bahnhofstraße 5", "97421", "Schweinfurt", null);

        stahl = new Lieferanten();
        stahl.setLieferantenname("Stahl Muster KG");
        stahl.setVertreter("Hans Beispiel");
        stahl.setOrt("Kitzingen");
        stahl.setStrasse("Industriestraße 9");
        stahl.setPlz("97318");
        stahl.setEigeneKundennummer("KD-4711");
        stahl = lieferanten.save(stahl);

        projekt(mustermann, "Wintergarten Mustermann", "2026-001", false, LocalDate.of(2026, 3, 1));
        projekt(mustermann, "Carport", "2025-017", true, LocalDate.of(2025, 5, 1));
        projekt(mustermann, "Treppengeländer", "2026-009", false, LocalDate.of(2026, 6, 1));

        Anfrage offen = anfrage(mustermann, "Balkongeländer Gartenseite", false, LocalDate.of(2026, 8, 1));
        Anfrage vordach = anfrage(mustermann, "Vordach", true, LocalDate.of(2026, 1, 1));
        angebot(offen, "AN-2026-040", false);
        angebot(offen, "AN-2026-044", false);
        angebot(vordach, "AN-2026-002", true);

        anruf(mustermann, null, "09311234567", null);
        anruf(musterfrau, null, "097211111", null);
        anruf(null, stahl, "093215555", null);
        anruf(null, null, "08001234", "Fremde Firma");

        kanzlei = new SteuerberaterKontakt();
        kanzlei.setName("Kanzlei Beispiel");
        kanzlei.setEmail("kanzlei@example.com");
        kanzlei.setTelefon("0931 4444-0");
        SteuerberaterAnsprechpartner frau = new SteuerberaterAnsprechpartner();
        frau.setSteuerberater(kanzlei);
        frau.setVorname("Christine");
        frau.setNachname("Beispiel-Lohn");
        frau.setTelefon("0931 4444-12");
        SteuerberaterAnsprechpartner leer = new SteuerberaterAnsprechpartner();
        leer.setSteuerberater(kanzlei);
        leer.setNachname(" ");
        kanzlei.getAnsprechpartnerListe().addAll(List.of(frau, leer));
        kanzlei = steuerberater.save(kanzlei);
        TelefonAnruf vomSteuerberater = new TelefonAnruf();
        vomSteuerberater.setZeitpunkt(ZEIT);
        vomSteuerberater.setArt(TelefonAnrufArt.ANGENOMMEN);
        vomSteuerberater.setNummerRoh("0931444412");
        vomSteuerberater.setEigeneNummer("2323");
        vomSteuerberater.setSteuerberater(kanzlei);
        vomSteuerberater.setZuordnung(TelefonZuordnung.AUTOMATISCH);
        vomSteuerberater.setAngelegtAm(ZEIT);
        anrufe.save(vomSteuerberater);
    }

    @Nested
    @DisplayName("Suche der Anrufliste")
    class Suche {

        @Test
        @DisplayName("findet über Vorname, Ansprechpartner, Wohnort, PLZ und Straße des Kunden")
        void kundenAttribute() {
            assertThat(nummern("Max")).containsExactly("09311234567");
            assertThat(nummern("erika")).containsExactly("09311234567");
            assertThat(nummern("würzburg")).containsExactly("09311234567");
            assertThat(nummern("97421")).containsExactly("097211111");
            assertThat(nummern("Bahnhofstr")).containsExactly("097211111");
            assertThat(nummern("K-2")).containsExactly("097211111");
            assertThat(nummern("max.mustermann@")).containsExactly("09311234567");
        }

        @Test
        @DisplayName("findet Lieferanten über Vertreter, Ort und unsere Kundennummer")
        void lieferantenAttribute() {
            assertThat(nummern("hans")).containsExactly("093215555");
            assertThat(nummern("kitzingen")).containsExactly("093215555");
            assertThat(nummern("KD-4711")).containsExactly("093215555");
            assertThat(nummern("beispiel")).containsExactlyInAnyOrder("09311234567", "093215555", "0931444412");
        }

        @Test
        @DisplayName("findet über Bauvorhaben und Auftragsnummer der Projekte und Anfragen")
        void projekteUndAnfragen() {
            assertThat(nummern("wintergarten")).containsExactly("09311234567");
            assertThat(nummern("2025-017")).containsExactly("09311234567");
            assertThat(nummern("balkon")).containsExactly("09311234567");
        }

        @Test
        @DisplayName("jedes Wort muss passen – egal in welchem Feld")
        void mehrereWoerter() {
            assertThat(nummern("muster")).containsExactlyInAnyOrder("09311234567", "097211111", "093215555");
            assertThat(nummern("Maria Schweinfurt")).containsExactly("097211111");
            assertThat(nummern("Max Würzburg")).containsExactly("09311234567");
            assertThat(nummern("Max Schweinfurt")).isEmpty();
            assertThat(nummern("  fremde ,  firma ")).containsExactly("08001234");
        }

        @Test
        @DisplayName("Platzhalter und SQL-Fragmente werden wörtlich gesucht")
        void sonderzeichen() {
            assertThat(nummern("%")).isEmpty();
            assertThat(nummern("_")).isEmpty();
            assertThat(nummern("'; DROP TABLE telefon_anruf; --")).isEmpty();
            assertThat(nummern("<script>alert(1)</script>")).isEmpty();
            assertThat(anrufe.count()).isEqualTo(5);
        }

        @Test
        @DisplayName("Filter nach Kontakt und nur unbekannte wirken zusammen mit der Suche")
        void filterMitSuche() {
            assertThat(treffer(null, false, mustermann.getId(), null, "muster")).hasSize(1);
            assertThat(treffer(null, true, null, null, null)).hasSize(1);
            assertThat(treffer(TelefonAnrufArt.VERPASST, false, null, null, null)).isEmpty();
            assertThat(treffer(null, false, null, stahl.getId(), "")).hasSize(1);
        }

        @Test
        @DisplayName("AB ohne Nachricht zählt als verpasst, „Anrufbeantworter\" nur mit Nachricht")
        void anrufbeantworterOhneNachrichtIstVerpasst() {
            TelefonAnruf ohneNachricht = abAnruf("0931-ohne");
            TelefonAnruf mitNachricht = abAnruf("0931-mit");
            TelefonAnruf verpasst = abAnruf("0931-verpasst");
            verpasst.setArt(TelefonAnrufArt.VERPASST);
            anrufe.save(verpasst);
            Sprachnachricht s = new Sprachnachricht();
            s.setZeitpunkt(ZEIT);
            s.setNummerRoh("0931-mit");
            s.setDateiName("nachricht.wav");
            s.setAnruf(mitNachricht);
            s.setAngelegtAm(ZEIT);
            nachrichten.save(s);

            assertThat(nummernMitArt(TelefonAnrufArt.VERPASST))
                    .containsExactlyInAnyOrder(ohneNachricht.getNummerRoh(), "0931-verpasst");
            assertThat(nummernMitArt(TelefonAnrufArt.ANRUFBEANTWORTER)).containsExactly("0931-mit");
            assertThat(anrufe.count(AnrufSuche.filter(TelefonAnrufArt.VERPASST, false, null, null, null, null, null)))
                    .isEqualTo(2);
            assertThat(nummernMitArt(TelefonAnrufArt.ANGENOMMEN)).hasSize(5);
        }

        private TelefonAnruf abAnruf(String nummer) {
            TelefonAnruf a = new TelefonAnruf();
            a.setZeitpunkt(ZEIT);
            a.setArt(TelefonAnrufArt.ANRUFBEANTWORTER);
            a.setAnrufbeantworter(0);
            a.setNummerRoh(nummer);
            a.setEigeneNummer("2323");
            a.setDauerMinuten(1);
            a.setZuordnung(TelefonZuordnung.KEINE);
            a.setAngelegtAm(ZEIT);
            return anrufe.save(a);
        }

        private List<String> nummernMitArt(TelefonAnrufArt art) {
            return anrufe.findAll(AnrufSuche.filter(art, false, null, null, null, null, null), PageRequest.of(0, 50))
                    .map(TelefonAnruf::getNummerRoh).getContent();
        }

        @Test
        @DisplayName("Zählabfrage bei mehreren Seiten: mit und ohne Suche, mit Filter nach Kontakt")
        void zaehlabfrage() {
            var ohneSuche = anrufe.findAll(AnrufSuche.filter(null, false, null, null, null, null, null), PageRequest.of(0, 1));
            assertThat(ohneSuche.getContent()).hasSize(1);
            assertThat(ohneSuche.getTotalElements()).isEqualTo(5);
            var mitSuche = anrufe.findAll(AnrufSuche.filter(null, false, null, null, "muster", null, null), PageRequest.of(1, 1));
            assertThat(mitSuche.getContent()).hasSize(1);
            assertThat(mitSuche.getTotalElements()).isEqualTo(3);
            var mitKontakt = anrufe.findAll(AnrufSuche.filter(null, false, mustermann.getId(), null, "max würzburg", null, null),
                    PageRequest.of(0, 1));
            assertThat(mitKontakt.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Tagesfilter: 0:00 zählt zum Tag, 24:00 schon zum nächsten; kombinierbar mit der Suche")
        void tagesfilter() {
            anruf(mustermann, null, "0931-mitternacht", null, LocalDateTime.of(2026, 9, 30, 0, 0));
            anruf(mustermann, null, "0931-spaet", null, LocalDateTime.of(2026, 9, 29, 23, 59, 59));
            anruf(mustermann, null, "0931-frueh", null, LocalDateTime.of(2026, 9, 29, 0, 0));

            assertThat(tag(LocalDate.of(2026, 9, 29), null)).hasSize(7).doesNotContain("0931-mitternacht");
            assertThat(tag(LocalDate.of(2026, 9, 30), null)).containsExactly("0931-mitternacht");
            assertThat(tag(LocalDate.of(2026, 9, 28), null)).isEmpty();
            assertThat(tag(LocalDate.of(2026, 9, 29), "hans")).containsExactly("093215555");
            assertThat(tag(LocalDate.of(2026, 9, 30), "hans")).isEmpty();
        }

        private List<String> tag(LocalDate tag, String suche) {
            return anrufe.findAll(AnrufSuche.filter(null, false, null, null, suche, tag, null),
                            PageRequest.of(0, 50, Sort.by(Sort.Order.desc("zeitpunkt"))))
                    .map(TelefonAnruf::getNummerRoh).getContent();
        }

        @Test
        @DisplayName("Steuerberater: Suche über Kanzlei und Ansprechpartner, Filter nach Kontaktart")
        void steuerberater() {
            assertThat(nummern("kanzlei")).containsExactly("0931444412");
            assertThat(nummern("christine")).containsExactly("0931444412");
            assertThat(nummern("beispiel-lohn")).containsExactly("0931444412");
            assertThat(art("STEUERBERATER")).containsExactly("0931444412");
            assertThat(art("KUNDE")).containsExactlyInAnyOrder("09311234567", "097211111");
            assertThat(art("LIEFERANT")).containsExactly("093215555");
            assertThatThrownBy(() -> art("PRIVAT")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> art("'; DROP TABLE telefon_anruf; --")).isInstanceOf(IllegalArgumentException.class);
        }

        private List<String> art(String kontaktart) {
            return anrufe.findAll(AnrufSuche.filter(null, false, null, null, null, null, kontaktart),
                    PageRequest.of(0, 50)).map(TelefonAnruf::getNummerRoh).getContent();
        }

        @Test
        @DisplayName("Suchmuster: höchstens sechs Wörter, 100 Zeichen, doppelte fallen weg")
        void suchmuster() {
            assertThat(AnrufSuche.suchmuster(null)).isEmpty();
            assertThat(AnrufSuche.suchmuster("   ")).isEmpty();
            assertThat(AnrufSuche.suchmuster("Max max MAX")).containsExactly("%max%");
            assertThat(AnrufSuche.suchmuster("a b c d e f g h")).hasSize(AnrufSuche.MAX_WOERTER);
            assertThat(AnrufSuche.suchmuster("x".repeat(10_000))).singleElement()
                    .satisfies(m -> assertThat(m).hasSize(AnrufSuche.MAX_SUCHE + 2));
            assertThat(AnrufSuche.suchmuster("50%_a\\b")).containsExactly("%50\\%\\_a\\\\b%");
        }

        private List<String> nummern(String suche) {
            return treffer(null, false, null, null, suche).stream().map(TelefonAnruf::getNummerRoh).toList();
        }

        private List<TelefonAnruf> treffer(TelefonAnrufArt art, boolean nurUnbekannt, Long kundeId, Long lieferantId,
                                           String suche) {
            return anrufe.findAll(AnrufSuche.filter(art, nurUnbekannt, kundeId, lieferantId, suche, null, null),
                    PageRequest.of(0, 50, Sort.by(Sort.Order.desc("zeitpunkt")))).getContent();
        }
    }

    @Nested
    @DisplayName("Überblick für das Anruf-Fenster")
    class Ueberblick {

        @Test
        @DisplayName("Kunde: Stammdaten, Projekte und Anfragen – offene zuerst, dann die neuesten")
        void kunde() {
            AnrufKontaktUeberblickDto dto = ueberblickService.ueberblick(mustermann.getId(), null, null);

            assertThat(dto.typ()).isEqualTo("KUNDE");
            assertThat(dto.name()).isEqualTo("Max Mustermann");
            assertThat(dto.nummer()).isEqualTo("K-1");
            assertThat(dto.ansprechpartner()).isEqualTo("Erika Beispiel");
            assertThat(dto.strasse()).isEqualTo("Hauptstraße 1");
            assertThat(dto.plz()).isEqualTo("97070");
            assertThat(dto.ort()).isEqualTo("Würzburg");
            assertThat(dto.projekte()).extracting(AnrufKontaktUeberblickDto.Projekt::auftragsnummer)
                    .containsExactly("2026-009", "2026-001", "2025-017");
            assertThat(dto.projekte().getLast().abgeschlossen()).isTrue();
            assertThat(dto.anfragen()).extracting(AnrufKontaktUeberblickDto.Anfrage::bauvorhaben)
                    .containsExactly("Balkongeländer Gartenseite", "Vordach");
            assertThat(dto.projekteGesamt()).isEqualTo(3);
            assertThat(dto.anfragenGesamt()).isEqualTo(2);
            // Neuestes Angebot zählt, stornierte fallen weg.
            assertThat(dto.anfragen().getFirst().angebotsnummer()).isEqualTo("AN-2026-044");
            assertThat(dto.anfragen().getLast().angebotsnummer()).isNull();
        }

        @Test
        @DisplayName("Viele Projekte: höchstens 50 mitliefern, Gesamtzahl stimmt")
        void begrenzt() {
            for (int i = 0; i < AnrufKontaktUeberblickDto.MAX_EINTRAEGE + 5; i++) {
                projekt(musterfrau, "Wartung " + i, "2026-5" + String.format("%02d", i), false,
                        LocalDate.of(2026, 1, 1).plusDays(i));
            }
            AnrufKontaktUeberblickDto dto = ueberblickService.ueberblick(musterfrau.getId(), null, null);
            assertThat(dto.projekte()).hasSize(AnrufKontaktUeberblickDto.MAX_EINTRAEGE);
            assertThat(dto.projekteGesamt()).isEqualTo(AnrufKontaktUeberblickDto.MAX_EINTRAEGE + 5);
            assertThat(dto.projekte().getFirst().bauvorhaben()).isEqualTo("Wartung 54");
        }

        @Test
        @DisplayName("Kunde ohne Projekte und Anfragen liefert leere Listen")
        void kundeOhneVorgaenge() {
            AnrufKontaktUeberblickDto dto = ueberblickService.ueberblick(musterfrau.getId(), null, null);
            assertThat(dto.ansprechpartner()).isNull();
            assertThat(dto.projekte()).isEmpty();
            assertThat(dto.anfragen()).isEmpty();
        }

        @Test
        @DisplayName("Lieferant: Vertreter als Ansprechpartner, keine Kundennummer")
        void lieferant() {
            AnrufKontaktUeberblickDto dto = ueberblickService.ueberblick(null, stahl.getId(), null);
            assertThat(dto.typ()).isEqualTo("LIEFERANT");
            assertThat(dto.name()).isEqualTo("Stahl Muster KG");
            assertThat(dto.ansprechpartner()).isEqualTo("Hans Beispiel");
            assertThat(dto.ort()).isEqualTo("Kitzingen");
            assertThat(dto.nummer()).isNull();
            assertThat(dto.projekte()).isEmpty();
        }

        @Test
        @DisplayName("Steuerberater: Kanzlei mit ihren Ansprechpartnern, ohne Adresse und Projekte")
        void steuerberaterUeberblick() {
            AnrufKontaktUeberblickDto dto = ueberblickService.ueberblick(null, null, kanzlei.getId());
            assertThat(dto.typ()).isEqualTo("STEUERBERATER");
            assertThat(dto.name()).isEqualTo("Kanzlei Beispiel");
            assertThat(dto.ansprechpartner()).isEqualTo("Christine Beispiel-Lohn");
            assertThat(dto.ort()).isNull();
            assertThat(dto.projekte()).isEmpty();
            assertThatThrownBy(() -> ueberblickService.ueberblick(null, 1L, kanzlei.getId())).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ueberblickService.ueberblick(null, null, Long.MAX_VALUE)).isInstanceOf(NoSuchElementException.class);

            SteuerberaterKontakt alt = new SteuerberaterKontakt();
            alt.setName("Alte Kanzlei");
            alt.setEmail("alt@example.com");
            alt.setAnsprechpartner("Herr Muster");
            alt = steuerberater.save(alt);
            assertThat(ueberblickService.ueberblick(null, null, alt.getId()).ansprechpartner()).isEqualTo("Herr Muster");
        }

        @Test
        @DisplayName("Ungültige Angaben: keine oder beide IDs, unbekannte IDs")
        void ungueltig() {
            assertThatThrownBy(() -> ueberblickService.ueberblick(null, null, null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ueberblickService.ueberblick(1L, 1L, null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ueberblickService.ueberblick(Long.MAX_VALUE, null, null)).isInstanceOf(NoSuchElementException.class);
            assertThatThrownBy(() -> ueberblickService.ueberblick(-1L, null, null)).isInstanceOf(NoSuchElementException.class);
            assertThatThrownBy(() -> ueberblickService.ueberblick(null, 0L, null)).isInstanceOf(NoSuchElementException.class);
        }
    }

    private Kunde kunde(String nummer, String name, String ansprechpartner, String strasse, String plz, String ort,
                        String email) {
        Kunde k = new Kunde();
        k.setKundennummer(nummer);
        k.setName(name);
        k.setAnsprechspartner(ansprechpartner);
        k.setStrasse(strasse);
        k.setPlz(plz);
        k.setOrt(ort);
        if (email != null) {
            k.getKundenEmails().add(email);
        }
        return kunden.save(k);
    }

    private void projekt(Kunde kunde, String bauvorhaben, String auftragsnummer, boolean abgeschlossen,
                         LocalDate angelegt) {
        Projekt p = new Projekt();
        p.setKundenId(kunde);
        p.setBauvorhaben(bauvorhaben);
        p.setAuftragsnummer(auftragsnummer);
        p.setAnlegedatum(angelegt);
        p.setBruttoPreis(BigDecimal.ZERO);
        p.setAbgeschlossen(abgeschlossen);
        projekte.save(p);
    }

    private Anfrage anfrage(Kunde kunde, String bauvorhaben, boolean abgeschlossen, LocalDate angelegt) {
        Anfrage a = new Anfrage();
        a.setKunde(kunde);
        a.setBauvorhaben(bauvorhaben);
        a.setAbgeschlossen(abgeschlossen);
        a.setAnlegedatum(angelegt);
        return anfragen.save(a);
    }

    private void angebot(Anfrage anfrage, String nummer, boolean storniert) {
        AusgangsGeschaeftsDokument d = new AusgangsGeschaeftsDokument();
        d.setAnfrage(anfrage);
        d.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
        d.setDokumentNummer(nummer);
        d.setDatum(LocalDate.of(2026, 8, 2));
        d.setStorniert(storniert);
        dokumente.save(d);
    }

    private void anruf(Kunde kunde, Lieferanten lieferant, String nummer, String nameFritzbox) {
        anruf(kunde, lieferant, nummer, nameFritzbox, ZEIT);
    }

    private void anruf(Kunde kunde, Lieferanten lieferant, String nummer, String nameFritzbox, LocalDateTime zeit) {
        TelefonAnruf a = new TelefonAnruf();
        a.setZeitpunkt(zeit);
        a.setArt(TelefonAnrufArt.ANGENOMMEN);
        a.setNummerRoh(nummer);
        a.setEigeneNummer("2323");
        a.setNameFritzbox(nameFritzbox);
        a.setKunde(kunde);
        a.setLieferant(lieferant);
        a.setZuordnung(kunde != null || lieferant != null ? TelefonZuordnung.AUTOMATISCH : TelefonZuordnung.KEINE);
        a.setAngelegtAm(ZEIT);
        anrufe.save(a);
    }
}
