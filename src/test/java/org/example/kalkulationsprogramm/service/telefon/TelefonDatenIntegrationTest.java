package org.example.kalkulationsprogramm.service.telefon;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Sprachnachricht;
import org.example.kalkulationsprogramm.domain.SteuerberaterAnsprechpartner;
import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.example.kalkulationsprogramm.domain.TelefonAnruf;
import org.example.kalkulationsprogramm.domain.TelefonAnrufArt;
import org.example.kalkulationsprogramm.domain.TelefonZuordnung;
import org.example.kalkulationsprogramm.dto.Telefon.AbholErgebnisDto;
import org.example.kalkulationsprogramm.dto.Telefon.AnrufbeantworterDto;
import org.example.kalkulationsprogramm.dto.Telefon.SprachnachrichtDto;
import org.example.kalkulationsprogramm.dto.Telefon.SteuerberaterAuswahlDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonAnrufDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonZuordnenDto;
import org.example.kalkulationsprogramm.repository.KontaktRufnummerRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.SprachnachrichtRepository;
import org.example.kalkulationsprogramm.repository.SteuerberaterKontaktRepository;
import org.example.kalkulationsprogramm.repository.TelefonAnrufRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Integrationstest auf H2: echte Repositories und JPQL-Abfragen, Zuordnung,
 * Abholung (mit simulierter Telefonanlage), Anrufliste, Anrufbeantworter,
 * Glocke und Aufbewahrung zusammen.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({TelefonService.class, RufnummernZuordnungService.class, TelefonAbholService.class,
        TelefonAufbewahrungService.class, TelefonBenachrichtigungService.class,
        TelefonDatenIntegrationTest.Konfiguration.class})
class TelefonDatenIntegrationTest {

    static final Instant JETZT = Instant.parse("2026-09-29T10:00:00Z");
    static Path ablageOrdner;

    @TestConfiguration
    static class Konfiguration {
        @Bean
        Clock clock() {
            return Clock.fixed(JETZT, ZoneId.of("Europe/Berlin"));
        }

        @Bean
        SprachnachrichtDateiablage ablage() throws IOException {
            ablageOrdner = Files.createTempDirectory("telefon-test");
            return new SprachnachrichtDateiablage(ablageOrdner.toString());
        }
    }

    @MockBean TelefonEinstellungenService einstellungen;
    @MockBean TelefonAnlage anlage;

    @Autowired TelefonService telefonService;
    @Autowired TelefonAbholService abholService;
    @Autowired TelefonAufbewahrungService aufbewahrung;
    @Autowired TelefonBenachrichtigungService glocke;
    @Autowired RufnummernZuordnungService zuordnung;
    @Autowired TelefonAnrufRepository anrufe;
    @Autowired SprachnachrichtRepository nachrichten;
    @Autowired KontaktRufnummerRepository gemerkte;
    @Autowired KundeRepository kunden;
    @Autowired LieferantenRepository lieferanten;
    @Autowired SteuerberaterKontaktRepository steuerberater;
    @Autowired jakarta.persistence.EntityManagerFactory emf;

    @TempDir Path tmp;

    private Kunde mustermann;
    private Lieferanten stahl;
    private final List<AnlagenAnruf> boxAnrufe = new ArrayList<>();
    private final List<AnlagenSprachnachricht> boxNachrichten = new ArrayList<>();

    private static final LocalDateTime HEUTE_0755 = LocalDateTime.of(2026, 9, 29, 7, 55);

    @BeforeEach
    void setUp() {
        nachrichten.deleteAll();
        anrufe.deleteAll();
        gemerkte.deleteAll();
        kunden.deleteAll();
        lieferanten.deleteAll();
        steuerberater.deleteAll();
        zuordnung.verwerfeCache();

        mustermann = new Kunde();
        mustermann.setKundennummer("K-1");
        mustermann.setName("Mustermann GmbH");
        mustermann.setOrt("Würzburg");
        mustermann.setTelefon("0931 / 123 45-67");
        mustermann = kunden.save(mustermann);

        stahl = new Lieferanten();
        stahl.setLieferantenname("Stahl Muster KG");
        stahl.setTelefon("+49 9721 55555");
        stahl = lieferanten.save(stahl);

        when(einstellungen.istAktiv()).thenReturn(true);
        when(einstellungen.zugang()).thenReturn(Optional.of(new TelefonZugang("fritz.box", "erp", "pw")));
        when(einstellungen.geschaeftsnummern()).thenReturn(List.of("2323"));
        when(einstellungen.anrufbeantworter()).thenReturn(List.of(new AnrufbeantworterDto(0, "AB Nacht"), new AnrufbeantworterDto(1, "AB Tag")));
        when(einstellungen.landesvorwahl()).thenReturn("49");
        when(einstellungen.ortsvorwahl()).thenReturn("931");
        when(einstellungen.aufbewahrungAnrufeMonate()).thenReturn(12);
        when(einstellungen.aufbewahrungSprachnachrichtenMonate()).thenReturn(3);

        boxAnrufe.clear();
        boxNachrichten.clear();
        when(anlage.ladeAnrufe(any(), anyInt())).thenAnswer(i -> List.copyOf(boxAnrufe));
        when(anlage.ladeSprachnachrichten(any(), anyInt())).thenAnswer(i -> boxNachrichten.stream()
                .filter(n -> n.anrufbeantworter() == (int) i.getArgument(1)).toList());
        when(anlage.ladeAudio(any(), any())).thenReturn(new byte[8000]);
    }

    private void boxAnruf(LocalDateTime zeit, TelefonAnrufArt art, String nummer, String eigene, Integer ab) {
        boxAnrufe.add(new AnlagenAnruf(zeit, art, nummer, eigene, 1, null, ab));
    }

    @Test
    @DisplayName("Abholung: nur Geschäftsnummer, Zuordnung, AB-Verknüpfung, idempotent")
    void abholung() {
        boxAnruf(HEUTE_0755, TelefonAnrufArt.ANGENOMMEN, "09311234567", "2323", null);
        boxAnruf(HEUTE_0755.plusMinutes(5), TelefonAnrufArt.ANRUFBEANTWORTER, "097215555 5", "2323", 1);
        boxAnruf(HEUTE_0755.plusMinutes(6), TelefonAnrufArt.VERPASST, "", "2323", null);
        boxAnruf(HEUTE_0755.plusMinutes(7), TelefonAnrufArt.ANGENOMMEN, "09311234567", "555000", null);
        boxNachrichten.add(new AnlagenSprachnachricht(1, HEUTE_0755.plusMinutes(5), "097215555 5", "2323",
                "/download.lua?path=/data/tam/rec/rec.1.000", "sid"));

        AbholErgebnisDto erstes = abholService.abholen();
        AbholErgebnisDto zweites = abholService.abholen();

        assertThat(erstes.erfolgreich()).isTrue();
        assertThat(erstes.neueAnrufe()).isEqualTo(3);
        assertThat(erstes.neueSprachnachrichten()).isEqualTo(1);
        assertThat(zweites.neueAnrufe()).isZero();
        assertThat(zweites.neueSprachnachrichten()).isZero();
        assertThat(anrufe.count()).isEqualTo(3);
        assertThat(anrufe.findAll()).noneMatch(a -> a.getEigeneNummer().equals("555000"));

        Page<TelefonAnrufDto> liste = telefonService.anrufe(null, false, null, null, null, null, null, 0, 50);
        assertThat(liste.getContent()).extracting(TelefonAnrufDto::art)
                .containsExactly("VERPASST", "ANRUFBEANTWORTER", "ANGENOMMEN");
        TelefonAnrufDto ab = liste.getContent().get(1);
        assertThat(ab.kontakt().name()).isEqualTo("Stahl Muster KG");
        assertThat(ab.anrufbeantworter()).isEqualTo(1);
        assertThat(ab.sprachnachrichtId()).isNotNull();
        assertThat(liste.getContent().get(2).kontakt().name()).isEqualTo("Mustermann GmbH");

        List<SprachnachrichtDto> ns = telefonService.sprachnachrichten(false, 1, null, null, null);
        assertThat(ns).hasSize(1);
        assertThat(ns.getFirst().dauerSekunden()).isEqualTo(1);
        assertThat(ns.getFirst().kontakt().typ()).isEqualTo("LIEFERANT");
        assertThat(telefonService.sprachnachrichten(false, 0, null, null, null)).isEmpty();
        assertThat(telefonService.audio(ns.getFirst().id())).exists();

        // Tagesfilter: Anrufe und Nachrichten vom 29.09., nichts vom Vortag.
        assertThat(telefonService.anrufe(null, false, null, HEUTE_0755.toLocalDate(), null, null, null, 0, 50).getTotalElements()).isEqualTo(3);
        assertThat(telefonService.anrufe(null, false, null, HEUTE_0755.toLocalDate().minusDays(1), null, null, null, 0, 50).getTotalElements()).isZero();
        assertThat(telefonService.sprachnachrichten(false, null, HEUTE_0755.toLocalDate(), null, null)).hasSize(1);
        assertThat(telefonService.sprachnachrichten(false, null, HEUTE_0755.toLocalDate().minusDays(1), null, null)).isEmpty();
    }

    @Test
    @DisplayName("Filter und Suche der Anrufliste, auch mit Sonderzeichen")
    void filterUndSuche() {
        boxAnruf(HEUTE_0755, TelefonAnrufArt.ANGENOMMEN, "09311234567", "2323", null);
        boxAnruf(HEUTE_0755.plusMinutes(1), TelefonAnrufArt.VERPASST, "0800 0000000", "2323", null);
        abholService.abholen();

        assertThat(telefonService.anrufe(TelefonAnrufArt.VERPASST, false, null, null, null, null, null, 0, 50).getTotalElements()).isEqualTo(1);
        assertThat(telefonService.anrufe(null, true, null, null, null, null, null, 0, 50).getContent())
                .extracting(TelefonAnrufDto::nummer).containsExactly("0800 0000000");
        assertThat(telefonService.anrufe(null, false, "muster", null, null, null, null, 0, 50).getTotalElements()).isEqualTo(1);
        assertThat(telefonService.anrufe(null, false, "0800", null, null, null, null, 0, 50).getTotalElements()).isEqualTo(1);
        assertThat(telefonService.anrufe(null, false, "%", null, null, null, null, 0, 50).getTotalElements()).isZero();
        assertThat(telefonService.anrufe(null, false, "'; DROP TABLE telefon_anruf; --", null, null, null, null, 0, 50).getTotalElements()).isZero();
        assertThat(telefonService.anrufe(null, false, null, null, null, mustermann.getId(), null, 0, 50).getTotalElements()).isEqualTo(1);
        assertThat(telefonService.anrufe(null, false, null, null, null, null, stahl.getId(), 0, 50).getTotalElements()).isZero();
        assertThat(telefonService.anrufe(null, false, null, null, null, null, null, -5, 10_000).getSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("AB ohne Nachricht steht als verpasster Anruf ohne Dauer da, mit Nachricht als Anrufbeantworter")
    void anrufbeantworterOhneNachrichtIstVerpasst() {
        boxAnruf(HEUTE_0755, TelefonAnrufArt.ANRUFBEANTWORTER, "0171 7777777", "2323", 0);
        boxAnruf(HEUTE_0755.plusMinutes(5), TelefonAnrufArt.ANRUFBEANTWORTER, "0171 8888888", "2323", 1);
        boxNachrichten.add(new AnlagenSprachnachricht(1, HEUTE_0755.plusMinutes(5), "0171 8888888", "2323",
                "/download.lua?path=/data/tam/rec/rec.1.000", "sid"));
        abholService.abholen();

        List<TelefonAnrufDto> liste = telefonService.anrufe(null, false, null, null, null, null, null, 0, 50).getContent();
        assertThat(liste).extracting(TelefonAnrufDto::art).containsExactly("ANRUFBEANTWORTER", "VERPASST");
        assertThat(liste).extracting(TelefonAnrufDto::dauerMinuten).containsExactly(1, 0);
        assertThat(telefonService.anrufe(TelefonAnrufArt.VERPASST, false, null, null, null, null, null, 0, 50).getContent())
                .extracting(TelefonAnrufDto::nummer).containsExactly("0171 7777777");
        assertThat(telefonService.anrufe(TelefonAnrufArt.ANRUFBEANTWORTER, false, null, null, null, null, null, 0, 50).getContent())
                .extracting(TelefonAnrufDto::nummer).containsExactly("0171 8888888");

        Long ohneNachricht = liste.get(1).id();
        assertThat(telefonService.hebeAnrufZuordnungAuf(ohneNachricht).art()).isEqualTo("VERPASST");
    }

    @Test
    @DisplayName("Zuordnen mit 'Nummer merken' zieht frühere unbekannte Anrufe und Nachrichten nach")
    void zuordnenMitMerken() {
        boxAnruf(HEUTE_0755, TelefonAnrufArt.VERPASST, "0800 0000000", "2323", null);
        boxAnruf(HEUTE_0755.plusMinutes(10), TelefonAnrufArt.ANRUFBEANTWORTER, "0800 0000000", "2323", 0);
        boxNachrichten.add(new AnlagenSprachnachricht(0, HEUTE_0755.plusMinutes(10), "0800 0000000", "2323",
                "/download.lua?path=/data/tam/rec/rec.0.000", "sid"));
        abholService.abholen();
        Long ersterAnruf = anrufe.findAll().stream().filter(a -> a.getArt() == TelefonAnrufArt.VERPASST).findFirst().orElseThrow().getId();

        TelefonAnrufDto dto = telefonService.ordneAnrufZu(ersterAnruf, new TelefonZuordnenDto(mustermann.getId(), null, null, true));

        assertThat(dto.zuordnung()).isEqualTo("MANUELL");
        assertThat(gemerkte.findByKundeIdOrderByAngelegtAmAsc(mustermann.getId())).hasSize(1);
        assertThat(anrufe.findAll()).allMatch(a -> a.getZuordnung() != TelefonZuordnung.KEINE);
        assertThat(nachrichten.findAll()).allMatch(s -> s.getZuordnung() == TelefonZuordnung.AUTOMATISCH);
        assertThat(telefonService.kontaktRufnummern(mustermann.getId(), null)).extracting(r -> r.nummer())
                .containsExactly("0800 0000000");

        // Künftige Anrufe dieser Nummer werden automatisch zugeordnet
        boxAnruf(HEUTE_0755.plusMinutes(30), TelefonAnrufArt.ANGENOMMEN, "+49 800 0000000", "2323", null);
        abholService.abholen();
        assertThat(telefonService.anrufe(null, false, null, null, null, mustermann.getId(), null, 0, 50).getTotalElements()).isEqualTo(3);

        // Gemerkte Nummer löschen und Zuordnung aufheben
        telefonService.loescheKontaktRufnummer(gemerkte.findAll().getFirst().getId());
        assertThat(telefonService.hebeAnrufZuordnungAuf(ersterAnruf).zuordnung()).isEqualTo("KEINE");
    }

    @Test
    @DisplayName("Steuerberater: Nummer beim Ansprechpartner speichern ordnet frühere und künftige Anrufe zu")
    void nummerBeimAnsprechpartnerSpeichern() {
        SteuerberaterKontakt kanzlei = new SteuerberaterKontakt();
        kanzlei.setName("Kanzlei Beispiel");
        kanzlei.setEmail("kanzlei@example.com");
        for (String[] name : new String[][]{{"Erika", "Beispiel", null}, {"Max", "Muster", "0931 66666"}}) {
            SteuerberaterAnsprechpartner person = new SteuerberaterAnsprechpartner();
            person.setSteuerberater(kanzlei);
            person.setVorname(name[0]);
            person.setNachname(name[1]);
            person.setTelefon(name[2]);
            kanzlei.getAnsprechpartnerListe().add(person);
        }
        kanzlei = steuerberater.save(kanzlei);
        boxAnruf(HEUTE_0755, TelefonAnrufArt.VERPASST, "0931 55555", "2323", null);
        boxAnruf(HEUTE_0755.plusMinutes(10), TelefonAnrufArt.ANGENOMMEN, "093155555", "2323", null);
        abholService.abholen();

        SteuerberaterAuswahlDto auswahl = telefonService.steuerberaterAuswahl().getFirst();
        assertThat(auswahl.ansprechpartner()).extracting(SteuerberaterAuswahlDto.Ansprechpartner::name)
                .containsExactlyInAnyOrder("Erika Beispiel", "Max Muster");
        Long erika = auswahl.ansprechpartner().stream().filter(a -> a.telefon() == null).findFirst().orElseThrow().id();
        Long max = auswahl.ansprechpartner().stream().filter(a -> a.telefon() != null).findFirst().orElseThrow().id();
        Long ersterAnruf = anrufe.findAll().stream().filter(a -> a.getArt() == TelefonAnrufArt.VERPASST).findFirst().orElseThrow().getId();

        // Max hat schon eine andere Nummer – nichts wird überschrieben, auch nicht die Zuordnung.
        Long kanzleiId = kanzlei.getId();
        assertThatThrownBy(() -> telefonService.ordneAnrufZu(ersterAnruf, new TelefonZuordnenDto(null, null, kanzleiId, false, erika)))
                .hasMessageContaining("nur zum Speichern der Nummer");
        assertThatThrownBy(() -> telefonService.ordneAnrufZu(ersterAnruf, new TelefonZuordnenDto(mustermann.getId(), null, null, true, erika)))
                .hasMessageContaining("nur beim Steuerberater");
        assertThatThrownBy(() -> telefonService.ordneAnrufZu(ersterAnruf,
                        new TelefonZuordnenDto(null, null, kanzleiId, true, max)))
                .hasMessageContaining("Max Muster hat schon die Nummer 0931 66666");
        assertThat(anrufe.findById(ersterAnruf).orElseThrow().getZuordnung()).isEqualTo(TelefonZuordnung.KEINE);

        TelefonAnrufDto dto = telefonService.ordneAnrufZu(ersterAnruf, new TelefonZuordnenDto(null, null, kanzleiId, true, erika));

        assertThat(dto.kontakt().ansprechpartner()).isEqualTo("Erika Beispiel");
        assertThat(steuerberater.findeAnsprechpartnerTelefone()).extracting(z -> z[2])
                .containsExactlyInAnyOrder("0931 55555", "0931 66666");
        assertThat(gemerkte.count()).isZero();
        assertThat(anrufe.findAll()).allMatch(a -> a.getSteuerberater() != null && a.getSteuerberater().getId().equals(kanzleiId));

        // Künftige Anrufe von Erika werden automatisch der Kanzlei zugeordnet, mit ihrem Namen.
        boxAnruf(HEUTE_0755.plusMinutes(30), TelefonAnrufArt.ANGENOMMEN, "+49 931 55555", "2323", null);
        abholService.abholen();
        List<TelefonAnrufDto> liste = telefonService.anrufe(null, false, null, null, "STEUERBERATER", null, null, 0, 50).getContent();
        assertThat(liste).hasSize(3).allSatisfy(a -> {
            assertThat(a.kontakt().id()).isEqualTo(kanzleiId);
            assertThat(a.kontakt().ansprechpartner()).isEqualTo("Erika Beispiel");
        });
    }

    @Test
    @DisplayName("Zuordnen ohne Merken betrifft nur den einen Eintrag; mehrdeutige Nummer liefert Kandidaten")
    void zuordnenOhneMerkenUndKandidaten() {
        stahl.setTelefon("0931 / 123 45-67");
        lieferanten.save(stahl);
        boxAnruf(HEUTE_0755, TelefonAnrufArt.VERPASST, "09311234567", "2323", null);
        boxAnruf(HEUTE_0755.plusMinutes(1), TelefonAnrufArt.VERPASST, "09311234567", "2323", null);
        abholService.abholen();

        List<TelefonAnrufDto> liste = telefonService.anrufe(null, false, null, null, null, null, null, 0, 50).getContent();
        assertThat(liste).allMatch(a -> a.kandidaten().size() == 2 && a.kontakt() == null);

        telefonService.ordneAnrufZu(liste.getFirst().id(), new TelefonZuordnenDto(null, stahl.getId(), null, false));
        assertThat(gemerkte.count()).isZero();
        assertThat(telefonService.anrufe(null, true, null, null, null, null, null, 0, 50).getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Abgehört setzen und zurücksetzen, Zähler neuer Nachrichten")
    void abgehoert() {
        boxNachrichten.add(new AnlagenSprachnachricht(0, HEUTE_0755, "09311234567", "2323",
                "/download.lua?path=/data/tam/rec/rec.0.000", "sid"));
        abholService.abholen();
        Long id = nachrichten.findAll().getFirst().getId();
        FrontendUserProfile gespeichert = persistiere("Max Mustermann");

        assertThat(telefonService.anzahlNeueSprachnachrichten()).isEqualTo(1);
        SprachnachrichtDto dto = telefonService.setzeAbgehoert(id, true, gespeichert);
        assertThat(dto.neu()).isFalse();
        assertThat(dto.abgehoertVon()).isEqualTo("Max Mustermann");
        assertThat(telefonService.anzahlNeueSprachnachrichten()).isZero();
        assertThat(telefonService.sprachnachrichten(true, null, null, null, null)).isEmpty();
        assertThat(telefonService.setzeAbgehoert(id, false, null).neu()).isTrue();
    }

    private FrontendUserProfile persistiere(String name) {
        var em = emf.createEntityManager();
        em.getTransaction().begin();
        FrontendUserProfile neu = new FrontendUserProfile();
        neu.setDisplayName(name);
        em.persist(neu);
        em.getTransaction().commit();
        em.close();
        return neu;
    }

    @Test
    @DisplayName("Glocke: neue Nachrichten und verpasste Anrufe, erledigt durch Rückruf")
    void glocke() {
        boxAnruf(HEUTE_0755, TelefonAnrufArt.VERPASST, "09311234567", "2323", null);
        boxAnruf(HEUTE_0755.plusMinutes(1), TelefonAnrufArt.VERPASST, "0800 0000000", "2323", null);
        boxAnruf(HEUTE_0755.plusMinutes(2), TelefonAnrufArt.ANRUFBEANTWORTER, "0171 7777777", "2323", 0);
        boxAnruf(HEUTE_0755.plusMinutes(3), TelefonAnrufArt.ANRUFBEANTWORTER, "0171 8888888", "2323", 1);
        boxNachrichten.add(new AnlagenSprachnachricht(1, HEUTE_0755.plusMinutes(3), "0171 8888888", "2323",
                "/download.lua?path=/data/tam/rec/rec.1.000", "sid"));
        boxAnruf(LocalDateTime.of(2026, 9, 1, 9, 0), TelefonAnrufArt.VERPASST, "0171 9999999", "2323", null);
        // Rückruf an Mustermann erledigt dessen verpassten Anruf
        boxAnruf(HEUTE_0755.plusMinutes(20), TelefonAnrufArt.AUSGEHEND, "0931 1234567", "2323", null);
        abholService.nachholen(60);

        List<TelefonBenachrichtigungService.Eintrag> verpasst = glocke.offeneVerpassteAnrufe();
        assertThat(verpasst).extracting(TelefonBenachrichtigungService.Eintrag::titel)
                .containsExactly("0171 7777777", "0800 0000000");
        assertThat(verpasst.getFirst().untertitel()).isEqualTo("AB Nacht, ohne Nachricht · heute 07:57");
        assertThat(verpasst.get(1).untertitel()).isEqualTo("Verpasst · heute 07:56");

        // Die Liste hinter dem Glocken-Link zeigt genau diese Anrufe
        Page<TelefonAnrufDto> offen = telefonService.offeneVerpassteAnrufe(0, 50);
        assertThat(offen.getTotalElements()).isEqualTo(2);
        assertThat(offen.getContent()).extracting(TelefonAnrufDto::nummer)
                .containsExactly("0171 7777777", "0800 0000000");

        List<TelefonBenachrichtigungService.Eintrag> neu = glocke.neueSprachnachrichten();
        assertThat(neu).hasSize(1);
        assertThat(neu.getFirst().untertitel()).startsWith("AB Tag · heute 07:58 · 0:01");
        assertThat(neu.getFirst().link()).startsWith("/telefon/anrufbeantworter?nachricht=");
    }

    @Test
    @DisplayName("Aufbewahrung löscht alte Anrufe und Nachrichten samt Datei; spätere Abholung holt sie nicht zurück")
    void aufbewahrung() {
        // Zuerst mit langer Frist abholen, damit die alten Einträge im ERP liegen
        when(einstellungen.aufbewahrungAnrufeMonate()).thenReturn(120);
        when(einstellungen.aufbewahrungSprachnachrichtenMonate()).thenReturn(120);
        boxAnruf(LocalDateTime.of(2025, 1, 10, 9, 0), TelefonAnrufArt.ANRUFBEANTWORTER, "09311234567", "2323", 0);
        boxAnruf(HEUTE_0755, TelefonAnrufArt.ANGENOMMEN, "09311234567", "2323", null);
        boxNachrichten.add(new AnlagenSprachnachricht(0, LocalDateTime.of(2025, 1, 10, 9, 0), "09311234567", "2323",
                "/download.lua?path=/data/tam/rec/rec.0.000", "sid"));
        boxNachrichten.add(new AnlagenSprachnachricht(0, HEUTE_0755, "09311234567", "2323",
                "/download.lua?path=/data/tam/rec/rec.0.001", "sid"));
        abholService.nachholen(999);
        Sprachnachricht alt = nachrichten.findAll().stream()
                .filter(s -> s.getZeitpunkt().getYear() == 2025).findFirst().orElseThrow();
        Path altDatei = ablageOrdner.resolve("sprachnachrichten").resolve(alt.getDateiName());
        assertThat(altDatei).exists();

        when(einstellungen.aufbewahrungAnrufeMonate()).thenReturn(12);
        when(einstellungen.aufbewahrungSprachnachrichtenMonate()).thenReturn(3);
        aufbewahrung.aufraeumen();

        assertThat(anrufe.findAll()).extracting(TelefonAnruf::getZeitpunkt).containsExactly(HEUTE_0755);
        assertThat(nachrichten.findAll()).extracting(Sprachnachricht::getZeitpunkt).containsExactly(HEUTE_0755);
        assertThat(altDatei).doesNotExist();

        // Die Box hat die alten Einträge noch – sie dürfen nicht wieder auftauchen
        abholService.abholen();
        abholService.nachholen(999);
        assertThat(anrufe.findAll()).extracting(TelefonAnruf::getZeitpunkt).containsExactly(HEUTE_0755);
        assertThat(nachrichten.findAll()).extracting(Sprachnachricht::getZeitpunkt).containsExactly(HEUTE_0755);
    }

    @Test
    @DisplayName("Kontakt gelöscht: Zuordnung wird zurückgesetzt und beim nächsten Abgleich neu gesucht")
    void verwaisteZuordnung() {
        boxAnruf(HEUTE_0755, TelefonAnrufArt.ANGENOMMEN, "09311234567", "2323", null);
        abholService.abholen();
        TelefonAnruf anruf = anrufe.findAll().getFirst();
        assertThat(anruf.getZuordnung()).isEqualTo(TelefonZuordnung.AUTOMATISCH);

        // So hinterlässt ON DELETE SET NULL den Anruf, wenn der Kunde gelöscht wird
        anruf.setKunde(null);
        anrufe.save(anruf);
        mustermann.setTelefon(null);
        kunden.save(mustermann);
        zuordnung.verwerfeCache();

        abholService.abholen();
        TelefonAnruf danach = anrufe.findById(anruf.getId()).orElseThrow();
        assertThat(danach.getZuordnung()).isEqualTo(TelefonZuordnung.KEINE);
        assertThat(telefonService.anrufe(null, true, null, null, null, null, null, 0, 50).getContent())
                .extracting(TelefonAnrufDto::id).containsExactly(anruf.getId());
    }

    @Test
    @DisplayName("Fehlgeschlagener Audio-Download legt keine Nachricht an; nächster Lauf holt nach")
    void downloadFehler() {
        boxNachrichten.add(new AnlagenSprachnachricht(0, HEUTE_0755, "09311234567", "2323",
                "/download.lua?path=/data/tam/rec/rec.0.000", "sid"));
        doThrow(new TelefonAnlageException(TelefonAnlageException.Grund.NICHT_ERREICHBAR))
                .when(anlage).ladeAudio(any(), any());
        AbholErgebnisDto e = abholService.abholen();
        assertThat(e.erfolgreich()).isFalse();
        assertThat(nachrichten.count()).isZero();

        doThrow(new TelefonAnlageException(TelefonAnlageException.Grund.UNERWARTETE_ANTWORT))
                .when(anlage).ladeAudio(any(), any());
        assertThat(abholService.abholen().erfolgreich()).isTrue();
        assertThat(nachrichten.count()).isZero();

        doReturn(new byte[]{}).when(anlage).ladeAudio(any(), any());
        assertThat(abholService.abholen().erfolgreich()).isTrue();
        assertThat(nachrichten.count()).isZero();

        doReturn(new byte[8000]).when(anlage).ladeAudio(any(), any());
        abholService.abholen();
        assertThat(nachrichten.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Steuerberater: Anruf von einer Durchwahl der Kanzlei wird zugeordnet und bleibt es beim nächsten Abholen")
    void steuerberaterBleibtZugeordnet() {
        SteuerberaterKontakt kanzlei = new SteuerberaterKontakt();
        kanzlei.setName("Kanzlei Beispiel");
        kanzlei.setEmail("kanzlei@example.com");
        kanzlei.setTelefon("0931 4444-0");
        kanzlei = steuerberater.save(kanzlei);
        boxAnruf(HEUTE_0755, TelefonAnrufArt.ANGENOMMEN, "0931444412", "2323", null);

        abholService.abholen();
        abholService.abholen();

        TelefonAnruf anruf = anrufe.findAll().getFirst();
        assertThat(anruf.getZuordnung()).isEqualTo(TelefonZuordnung.AUTOMATISCH);
        List<TelefonAnrufDto> liste = telefonService.anrufe(null, false, null, null, "STEUERBERATER", null, null, 0, 50).getContent();
        assertThat(liste).hasSize(1);
        assertThat(liste.getFirst().kontakt().typ()).isEqualTo("STEUERBERATER");
        assertThat(liste.getFirst().kontakt().id()).isEqualTo(kanzlei.getId());
        assertThat(telefonService.anrufe(null, false, null, null, "KUNDE", null, null, 0, 50).getContent()).isEmpty();
        assertThat(telefonService.steuerberaterAuswahl()).extracting(k -> k.name()).containsExactly("Kanzlei Beispiel");
    }
}
