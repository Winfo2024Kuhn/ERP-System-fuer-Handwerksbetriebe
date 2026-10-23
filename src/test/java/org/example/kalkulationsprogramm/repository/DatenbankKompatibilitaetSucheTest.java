package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.example.kalkulationsprogramm.config.FlywayStartSetupConfig;
import org.example.kalkulationsprogramm.controller.ArtikelController;
import org.example.kalkulationsprogramm.controller.KundeController;
import org.example.kalkulationsprogramm.controller.LieferantenController;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektArt;
import org.example.kalkulationsprogramm.domain.TelefonAnruf;
import org.example.kalkulationsprogramm.domain.TelefonAnrufArt;
import org.example.kalkulationsprogramm.domain.TelefonZuordnung;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelResponseDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeListItemDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantArtikelpreisDto;
import org.example.kalkulationsprogramm.mapper.KundeMapper;
import org.example.kalkulationsprogramm.service.ArtikelPositionsPreisService;
import org.example.kalkulationsprogramm.service.ArtikelService;
import org.example.kalkulationsprogramm.service.KategorieService;
import org.example.kalkulationsprogramm.service.LieferantArtikelpreisMapper;
import org.example.kalkulationsprogramm.service.LieferantArtikelpreisService;
import org.example.kalkulationsprogramm.service.telefon.RufnummernZuordnungService;
import org.example.kalkulationsprogramm.service.telefon.TelefonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

/**
 * Laufen die Suchen der Oberflaeche mit allen Filtern und allen Sortierungen auf
 * PostgreSQL - und finden sie, was der Nutzer erwartet?
 *
 * <p>Die Suchen bauen ihre Abfrage zur Laufzeit (Specification + Sortierung aus
 * dem Request), {@link DatenbankKompatibilitaetTest} sieht sie deshalb nicht.
 * Hier laufen sie ueber den echten Controller/Service gegen die echte Datenbank:
 * <ul>
 * <li>jede angebotene Sortierung in beide Richtungen (die Schluessel kommen aus
 * dem Controller - eine neue Sortierspalte wird automatisch mitgeprueft),</li>
 * <li>jeder Filter einzeln und alle zusammen (PostgreSQL: {@code DISTINCT} plus
 * {@code ORDER BY lower(...)} -> Fehler 42P10),</li>
 * <li>Gross-/Kleinschreibung samt Umlauten ("WÜRZBURG" findet "Würzburg"),</li>
 * <li>leere Werte sortieren wie bisher: aufsteigend zuerst, absteigend
 * zuletzt (PostgreSQL von sich aus genau umgekehrt).</li>
 * </ul>
 * Alles in zurueckgerollten Transaktionen - die Wegwerf-Datenbank bleibt sauber.
 */
@DataJpaTest(showSql = false, properties = { "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=true", "spring.flyway.out-of-order=true",
        "spring.flyway.validate-on-migrate=false", "spring.jpa.show-sql=false" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ FlywayStartSetupConfig.class, KundeMapper.class, ArtikelService.class, KategorieService.class,
        ArtikelPositionsPreisService.class, LieferantArtikelpreisMapper.class, LieferantArtikelpreisService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "kompat.db.url", matches = "jdbc:postgresql:.*")
class DatenbankKompatibilitaetSucheTest {

    @DynamicPropertySource
    static void datenbank(DynamicPropertyRegistry r) {
        KompatibilitaetsDatenbank.eintragen(r);
    }

    @Autowired
    ApplicationContext context;
    @Autowired
    EntityManager em;
    @Autowired
    PlatformTransactionManager transaktionen;
    @Autowired
    LieferantArtikelpreisService artikelpreise;

    TransactionTemplate tx;
    /** Kennzeichnet die Testdaten eines Falls - die Datenbank kann noch anderes enthalten. */
    String kennung;

    @BeforeEach
    void vorbereiten() {
        tx = new TransactionTemplate(transaktionen);
        kennung = UUID.randomUUID().toString().substring(0, 8);
    }

    // ------------------------------------------------------------------
    // Kunden (GET /api/kunden, Kundenliste und Kundenauswahl)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Kundensuche: jeder Filter, auch Typ und E-Mail (DISTINCT + Sortierung nach Name)")
    void kundensuche() {
        String gross = kennung.toUpperCase(Locale.ROOT);
        List<String> fehler = new ArrayList<>();
        // Freitext ohne Gross-/Kleinschreibung
        kunden(fehler, "Freitext", k -> k.sucheKunden("MUSTERMANN " + gross, null, null, null, null, null, false, 0, 50, null),
                "Max Mustermann");
        kunden(fehler, "Name + Ort mit Umlaut", k -> k.sucheKunden(null, gross, null, "WÜRZBURG", null, null, false, 0, 50, null),
                "Erika Musterfrau", "Max Mustermann");
        kunden(fehler, "Kundennummer", k -> k.sucheKunden(null, null, "k-" + kennung, null, null, null, false, 0, 50, null),
                "Erika Musterfrau", "Max Mustermann");
        // Zwei passende Adressen beim selben Kunden: trotzdem nur eine Zeile
        kunden(fehler, "E-Mail", k -> k.sucheKunden(null, null, null, null, kennung + "@EXAMPLE", null, false, 0, 50, null),
                "Erika Musterfrau", "Max Mustermann");
        kunden(fehler, "Typ Kunde", k -> k.sucheKunden(null, kennung, null, null, null, "KUNDE", false, 0, 50, null),
                "Max Mustermann");
        kunden(fehler, "Typ Anfrager", k -> k.sucheKunden(null, kennung, null, null, null, "ANFRAGER", false, 0, 50, null),
                "Erika Musterfrau");
        kunden(fehler, "alle Filter", k -> k.sucheKunden("muster", kennung, "K-" + gross, "würzburg",
                "rechnung." + kennung, "KUNDE", false, 0, 50, null), "Max Mustermann");
        // Datenschutz-Sperre (Issue #264): keiner der Testkunden ist gesperrt
        // Nur Admins dürfen gesperrte Kunden auflisten (Issue #264)
        var admin = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("chef", null,
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        kunden(fehler, "nur gesperrte", k -> k.sucheKunden(null, kennung, null, null, null, null, true, 0, 50, admin));
        assertThat(fehler).as("Kundensuchen, die auf dieser Datenbank scheitern oder anderes finden").isEmpty();
    }

    private void kunden(List<String> fehler, String fall,
            Function<KundeController, org.example.kalkulationsprogramm.dto.Kunde.KundeSearchResponseDto> suche,
            String... erwartet) {
        pruefen(fehler, "Kunden/" + fall, () -> {
            kundenAnlegen();
            List<String> namen = suche.apply(bauen(KundeController.class)).getKunden().stream()
                    .map(KundeListItemDto::getName).map(this::ohneKennung).toList();
            return namen.equals(List.of(erwartet)) ? null : "erwartet " + List.of(erwartet) + ", gefunden " + namen;
        });
    }

    private Kunde kundenAnlegen() {
        Kunde max = kunde("Max Mustermann " + kennung, "K-" + kennung + "-1", "Würzburg",
                "max." + kennung + "@example.com", "rechnung." + kennung + "@example.com");
        Kunde erika = kunde("Erika Musterfrau " + kennung, "K-" + kennung + "-2", "würzburg",
                "erika." + kennung + "@example.com");
        em.persist(max);
        em.persist(erika);
        Projekt projekt = new Projekt();
        projekt.setBauvorhaben("Carport Mustermann " + kennung);
        projekt.setAuftragsnummer("A-" + kennung);
        projekt.setAnlegedatum(LocalDate.of(2026, 3, 15));
        projekt.setBruttoPreis(BigDecimal.TEN);
        projekt.setBezahlt(false);
        projekt.setAbgeschlossen(false);
        projekt.setProjektArt(ProjektArt.values()[0]);
        projekt.setKundenId(max);
        em.persist(projekt);
        em.flush();
        em.clear();
        return max;
    }

    private static Kunde kunde(String name, String nummer, String ort, String... emails) {
        Kunde kunde = new Kunde();
        kunde.setName(name);
        kunde.setKundennummer(nummer);
        kunde.setOrt(ort);
        kunde.setKundenEmails(new ArrayList<>(List.of(emails)));
        return kunde;
    }

    // ------------------------------------------------------------------
    // Artikel (GET /api/artikel?sort=...&dir=..., Artikelliste)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Artikelsuche: jede Sortierspalte in beide Richtungen, leere Werte wie bisher")
    void artikelsucheAlleSortierungen() {
        List<String> fehler = new ArrayList<>();
        for (String spalte : sortierschluessel(ArtikelController.class, "SORT_FIELDS").keySet()) {
            for (String richtung : List.of("asc", "desc")) {
                pruefen(fehler, "Artikel sort=" + spalte + "&dir=" + richtung, () -> {
                    artikelAnlegen();
                    List<String> treffer = artikelSuche(k -> k.sucheArtikel("RUNDROHR " + kennung.toUpperCase(Locale.ROOT),
                            null, null, null, null, false, null, null, null, null, null, 0, 50, spalte, richtung));
                    List<String> erwartet = erwartet(spalte.equals("produktname"), richtung, "Rundrohr A", "Rundrohr B");
                    if (spalte.equals("preis")) {
                        // Nach Preis sortiert der Controller die Seite selbst nach
                        return treffer.size() == 2 && treffer.containsAll(erwartet) ? null : "gefunden " + treffer;
                    }
                    return treffer.equals(erwartet) ? null : "erwartet " + erwartet + ", gefunden " + treffer;
                });
            }
        }
        pruefen(fehler, "Artikel mit allen Filtern", () -> {
            artikelAnlegen();
            List<String> treffer = artikelSuche(k -> k.sucheArtikel("rundrohr " + kennung, "WÜRTH " + kennung.toUpperCase(Locale.ROOT),
                    "rohr", "s235jr " + kennung, null, true, null, null, null, false, false, 0, 50, "werkstoffName", "desc"));
            return treffer.equals(List.of("Rundrohr A")) ? null : "gefunden " + treffer;
        });
        assertThat(fehler).as("Artikelsuchen, die auf dieser Datenbank scheitern oder anders sortieren").isEmpty();
    }

    @Test
    @DisplayName("Artikelsuche: Zahlen zählen als ganze Zahl, Komma gleich Punkt (regulärer Ausdruck in SQL)")
    void artikelsucheZahlenAlsGanzeZahl() {
        List<String> fehler = new ArrayList<>();
        pruefen(fehler, "Artikel q=flachstahl 20x10", () -> {
            for (String name : List.of("Flachstahl " + kennung + " 20 x 10", "Flachstahl " + kennung + " 120 x 10",
                    "Flachstahl " + kennung + " 100 x 20", "Flachstahl " + kennung + " 20,5 x 10")) {
                Artikel a = new Artikel();
                a.setProduktname(name);
                em.persist(a);
            }
            em.flush();
            em.clear();
            List<String> treffer = artikelSuche(k -> k.sucheArtikel("FLACHSTAHL " + kennung.toUpperCase(Locale.ROOT) + " 20x10",
                    null, null, null, null, false, null, null, null, null, null, 0, 50, "produktname", "asc"));
            List<String> komma = artikelSuche(k -> k.sucheArtikel("flachstahl " + kennung + " 20.5",
                    null, null, null, null, false, null, null, null, null, null, 0, 50, "produktname", "asc"));
            // Hochkomma im Zahlenpfad: darf weder scheitern noch etwas finden
            List<String> hochkomma = artikelSuche(k -> k.sucheArtikel("flachstahl " + kennung + " 20' or '1'='1",
                    null, null, null, null, false, null, null, null, null, null, 0, 50, "produktname", "asc"));
            return treffer.equals(List.of("Flachstahl 20 x 10")) && komma.equals(List.of("Flachstahl 20,5 x 10"))
                    && hochkomma.isEmpty()
                    ? null : "gefunden " + treffer + " / " + komma + " / " + hochkomma;
        });
        assertThat(fehler).as("Zahlensuche, die auf dieser Datenbank scheitert").isEmpty();
    }

    private List<String> artikelSuche(
            Function<ArtikelController, org.example.kalkulationsprogramm.dto.Artikel.ArtikelSearchResponseDto> suche) {
        return suche.apply(bauen(ArtikelController.class)).getArtikel().stream()
                .map(ArtikelResponseDto::getProduktname).map(this::ohneKennung).toList();
    }

    /**
     * Rundrohr A hat in jeder Sortierspalte einen Wert, Rundrohr B ausser dem Namen
     * nirgends (kein Werkstoff, kein Lieferantenpreis, kein Text).
     */
    private void artikelAnlegen() {
        Werkstoff stahl = new Werkstoff();
        stahl.setName("S235JR " + kennung);
        em.persist(stahl);
        Lieferanten wuerth = new Lieferanten();
        wuerth.setLieferantenname("Würth " + kennung);
        em.persist(wuerth);

        // Werkstoff-Artikel: „kg/m“ (masse) liegt an der Unterklasse und wird mitsortiert (Issue #265)
        org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe a = new org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe();
        a.setArtikelnummer("AN-" + kennung);
        a.setMasse(new BigDecimal("2.3400"));
        a.setProduktname("Rundrohr " + kennung + " A");
        a.setProduktlinie("Rohr");
        a.setProdukttext("Rundrohr geschweisst");
        a.setVerpackungseinheit(6L);
        a.setWerkstoff(stahl);
        em.persist(a);
        em.persist(preis(a, wuerth, new BigDecimal("12.50"), "EX-" + kennung, LocalDate.of(2026, 3, 1)));

        Artikel b = new Artikel();
        b.setProduktname("Rundrohr " + kennung + " B");
        em.persist(b);
        em.flush();
        em.clear();
    }

    // ------------------------------------------------------------------
    // Artikelpreise eines Lieferanten (GET /api/lieferanten/{id}/artikelpreise)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Artikelpreise eines Lieferanten: jede Sortierung, mit und ohne Suchbegriff")
    void artikelpreiseAlleSortierungen() {
        List<String> fehler = new ArrayList<>();
        Map<String, String> spalten = sortierschluessel(LieferantenController.class, "ARTIKELPREIS_SORT_FIELDS");
        for (Map.Entry<String, String> spalte : spalten.entrySet()) {
            for (Sort.Direction richtung : Sort.Direction.values()) {
                for (String suche : Arrays.asList(null, "BLECH " + kennung.toUpperCase(Locale.ROOT))) {
                    pruefen(fehler, "Artikelpreise sort=" + spalte.getKey() + " " + richtung + " q=" + suche, () -> {
                        Long lieferantId = artikelpreiseAnlegen();
                        // Wie LieferantenController.listArtikelpreise
                        List<String> treffer = artikelpreise.suche(lieferantId, suche,
                                PageRequest.of(0, 50, Sort.by(new Sort.Order(richtung, spalte.getValue()))))
                                .getContent().stream().map(LieferantArtikelpreisDto::getProduktname)
                                .map(this::ohneKennung).toList();
                        List<String> erwartet = erwartet(spalte.getKey().equals("produktname"),
                                richtung.name().toLowerCase(Locale.ROOT), "Blech A", "Blech B");
                        return treffer.equals(erwartet) ? null : "erwartet " + erwartet + ", gefunden " + treffer;
                    });
                }
            }
        }
        assertThat(fehler).as("Artikelpreis-Listen, die auf dieser Datenbank scheitern oder anders sortieren").isEmpty();
    }

    /** Blech A mit allen Angaben, Blech B ohne Werkstoff, Preis, Datum und Lieferanten-Nummer. */
    private Long artikelpreiseAnlegen() {
        Werkstoff stahl = new Werkstoff();
        stahl.setName("DC01 " + kennung);
        em.persist(stahl);
        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("Mustermann Stahlhandel " + kennung);
        em.persist(lieferant);
        Artikel a = new Artikel();
        a.setProduktname("Blech " + kennung + " A");
        a.setWerkstoff(stahl);
        em.persist(a);
        Artikel b = new Artikel();
        b.setProduktname("Blech " + kennung + " B");
        em.persist(b);
        em.persist(preis(a, lieferant, new BigDecimal("4.20"), "EX-" + kennung, LocalDate.of(2026, 3, 1)));
        em.persist(preis(b, lieferant, null, null, null));
        em.flush();
        em.clear();
        return lieferant.getId();
    }

    private static LieferantenArtikelPreise preis(Artikel artikel, Lieferanten lieferant, BigDecimal betrag,
            String nummer, LocalDate datum) {
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setArtikel(artikel);
        preis.setLieferant(lieferant);
        preis.setPreis(betrag);
        preis.setExterneArtikelnummer(nummer);
        preis.setPreisAenderungsdatum(datum == null ? null
                : Date.from(datum.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        preis.setQuelle(PreisQuelle.MANUELL);
        preis.setAktuell(true);
        return preis;
    }

    // ------------------------------------------------------------------
    // Lieferant zu einem Namen (Artikel-Import, KI-Belege, Rechnungseingang)
    // ------------------------------------------------------------------

    @Autowired
    LieferantenRepository lieferanten;

    @Test
    @DisplayName("Lieferant per Name: ohne Gross-/Kleinschreibung, auch wenn PostgreSQL zwei Schreibweisen hat")
    void lieferantNachNameOhneGrossKleinschreibung() {
        List<String> fehler = new ArrayList<>();
        pruefen(fehler, "Name in anderer Schreibweise", () -> {
            Lieferanten wuerth = new Lieferanten();
            wuerth.setLieferantenname("Würth " + kennung);
            em.persist(wuerth);
            em.flush();
            // Mit Leerzeichen am Ende: frueher (MySQL, PAD SPACE) wurde das gefunden
            return lieferanten.findByLieferantennameIgnoreCase("WÜRTH " + kennung.toUpperCase(Locale.ROOT) + " ")
                    .map(Lieferanten::getId).filter(wuerth.getId()::equals).isPresent() ? null : "nicht gefunden";
        });
        // PostgreSQL laesst zwei Schreibweisen desselben Namens zu
        pruefen(fehler, "zwei Schreibweisen", () -> {
            Lieferanten klein = new Lieferanten();
            klein.setLieferantenname("würth " + kennung);
            em.persist(klein);
            Lieferanten gross = new Lieferanten();
            gross.setLieferantenname("WÜRTH " + kennung.toUpperCase(Locale.ROOT));
            em.persist(gross);
            em.flush();
            Long exakt = lieferanten.findByLieferantennameIgnoreCase("WÜRTH " + kennung.toUpperCase(Locale.ROOT))
                    .map(Lieferanten::getId).orElse(null);
            Long aeltester = lieferanten.findByLieferantennameIgnoreCase("Würth " + kennung)
                    .map(Lieferanten::getId).orElse(null);
            return gross.getId().equals(exakt) && klein.getId().equals(aeltester) ? null
                    : "exakt " + exakt + ", sonst " + aeltester;
        });
        assertThat(fehler).isEmpty();
    }

    @Autowired
    LieferantGeschaeftsdokumentRepository geschaeftsdokumente;

    @Test
    @DisplayName("Doppelte Belegnummer beim Lieferanten: ohne Gross-/Kleinschreibung erkannt")
    void belegnummerOhneGrossKleinschreibung() {
        List<String> fehler = new ArrayList<>();
        pruefen(fehler, "Belegnummer", () -> {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setLieferantenname("Mustermann Stahlhandel " + kennung);
            em.persist(lieferant);
            LieferantDokument dokument = new LieferantDokument();
            dokument.setLieferant(lieferant);
            dokument.setTyp(LieferantDokumentTyp.RECHNUNG);
            dokument.setUploadDatum(LocalDateTime.of(2026, 3, 15, 8, 0));
            em.persist(dokument);
            LieferantGeschaeftsdokument daten = new LieferantGeschaeftsdokument();
            daten.setDokument(dokument);
            daten.setDokumentNummer("RE-" + kennung.toUpperCase(Locale.ROOT));
            daten.setBetragBrutto(new BigDecimal("119.00"));
            em.persist(daten);
            em.flush();
            String andersGeschrieben = "re-" + kennung;
            List<String> nichtErkannt = new ArrayList<>();
            if (!geschaeftsdokumente.existsByLieferantIdAndDokumentNummer(lieferant.getId(), andersGeschrieben)) {
                nichtErkannt.add("existsByLieferantIdAndDokumentNummer");
            }
            if (!geschaeftsdokumente.existsByLieferantIdAndDokumentNummerAndBetragBrutto(lieferant.getId(),
                    andersGeschrieben, new BigDecimal("119.00"))) {
                nichtErkannt.add("existsByLieferantIdAndDokumentNummerAndBetragBrutto");
            }
            if (geschaeftsdokumente.findByLieferantIdAndDokumentNummer(lieferant.getId(), andersGeschrieben).isEmpty()) {
                nichtErkannt.add("findByLieferantIdAndDokumentNummer");
            }
            return nichtErkannt.isEmpty() ? null : "nicht erkannt: " + nichtErkannt;
        });
        assertThat(fehler).isEmpty();
    }

    // ------------------------------------------------------------------
    // Anrufliste (GET /api/telefon/anrufe?suche=...)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Anrufliste: Suche ueber Kontakt und Ort ohne Gross-/Kleinschreibung, mit allen Filtern")
    void anrufsuche() {
        List<String> fehler = new ArrayList<>();
        String gross = kennung.toUpperCase(Locale.ROOT);
        for (String kontaktart : Arrays.asList(null, "KUNDE")) {
            pruefen(fehler, "Anrufe suche=WÜRZBURG kontaktart=" + kontaktart, () -> {
                Kunde max = kundenAnlegen();
                TelefonAnruf anruf = new TelefonAnruf();
                anruf.setZeitpunkt(LocalDateTime.of(2026, 3, 15, 9, 30));
                anruf.setArt(TelefonAnrufArt.ANGENOMMEN);
                anruf.setEigeneNummer("100");
                anruf.setNummerRoh("0931 " + kennung);
                anruf.setKunde(em.getReference(Kunde.class, max.getId()));
                anruf.setZuordnung(TelefonZuordnung.AUTOMATISCH);
                anruf.setAngelegtAm(LocalDateTime.of(2026, 3, 15, 9, 31));
                em.persist(anruf);
                em.flush();
                em.clear();
                RufnummernZuordnungService zuordnung = mock(RufnummernZuordnungService.class);
                when(zuordnung.verzeichnis()).thenReturn(new RufnummernZuordnungService.Verzeichnis());
                TelefonService telefon = bauen(TelefonService.class, zuordnung);
                List<Long> treffer = telefon.anrufe(null, false, "WÜRZBURG " + gross, LocalDate.of(2026, 3, 15),
                        kontaktart, null, null, 0, 50).getContent().stream()
                        .map(org.example.kalkulationsprogramm.dto.Telefon.TelefonAnrufDto::id).toList();
                return treffer.equals(List.of(anruf.getId())) ? null : "gefunden " + treffer;
            });
        }
        assertThat(fehler).as("Anrufsuchen, die auf dieser Datenbank scheitern oder anderes finden").isEmpty();
    }

    // ------------------------------------------------------------------
    // Hilfen
    // ------------------------------------------------------------------

    /** Ein Fall in einer eigenen, zurueckgerollten Transaktion; scheitert er, landet das in {@code fehler}. */
    private void pruefen(List<String> fehler, String fall, java.util.function.Supplier<String> pruefung) {
        try {
            String abweichung = tx.execute(status -> {
                status.setRollbackOnly();
                return pruefung.get();
            });
            if (abweichung != null) {
                fehler.add(fall + ": " + abweichung);
            }
        } catch (RuntimeException e) {
            fehler.add(fall + ": " + ursache(e));
        }
    }

    /**
     * Erwartete Reihenfolge zweier Treffer: {@code mitWert} hat in der Sortierspalte einen
     * Wert, der andere keinen (ausser bei {@code beideMitWert}). Leer gilt wie bisher
     * als kleinster Wert.
     */
    private static List<String> erwartet(boolean beideMitWert, String richtung, String mitWert, String ohneWert) {
        boolean aufsteigend = "asc".equals(richtung);
        if (beideMitWert) {
            return aufsteigend ? List.of(mitWert, ohneWert) : List.of(ohneWert, mitWert);
        }
        return aufsteigend ? List.of(ohneWert, mitWert) : List.of(mitWert, ohneWert);
    }

    private String ohneKennung(String text) {
        return text == null ? null : text.replace(kennung + " ", "").replace(" " + kennung, "");
    }

    /** Die Sortierschluessel, die der Controller anbietet (Schluessel aus dem Request -> Eigenschaft). */
    @SuppressWarnings("unchecked")
    private static <V> Map<String, V> sortierschluessel(Class<?> controller, String feld) {
        try {
            Field f = controller.getDeclaredField(feld);
            f.setAccessible(true);
            Map<String, V> schluessel = (Map<String, V>) f.get(null);
            assertThat(schluessel).as(controller.getSimpleName() + "." + feld).isNotEmpty();
            return new java.util.TreeMap<>(schluessel);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Sortierschluessel " + controller.getSimpleName() + "." + feld
                    + " nicht gefunden - Test anpassen", e);
        }
    }

    /**
     * Baut Controller/Service ueber den Konstruktor: Repositories und die importierten
     * Beans echt, {@code vorgaben} fuer passende Typen, alles andere als Attrappe.
     */
    private <T> T bauen(Class<T> klasse, Object... vorgaben) {
        Constructor<?> konstruktor = Arrays.stream(klasse.getDeclaredConstructors())
                .max(Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] argumente = Arrays.stream(konstruktor.getParameterTypes()).map(typ -> {
            for (Object vorgabe : vorgaben) {
                if (typ.isInstance(vorgabe)) {
                    return vorgabe;
                }
            }
            Object bean = context.getBeanProvider(typ).getIfUnique();
            return bean != null ? bean : mock(typ);
        }).toArray();
        try {
            konstruktor.setAccessible(true);
            return klasse.cast(konstruktor.newInstance(argumente));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String ursache(Throwable fehler) {
        Throwable aktuell = fehler;
        while (aktuell.getCause() != null && aktuell.getCause() != aktuell) {
            aktuell = aktuell.getCause();
        }
        return aktuell.getClass().getSimpleName() + ": " + aktuell.getMessage();
    }
}
