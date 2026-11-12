package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.example.kalkulationsprogramm.config.FlywayStartSetupConfig;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerAnlegenDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerRechteSpeichernDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerRechteSpeichernDto.Eintrag;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentSeiteDto;
import org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentDateiablage;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.service.AbteilungsRechtPruefung.Zugehoerigkeit;
import org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentOrdnerRechteService;
import org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentOrdnerService;
import org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentZugriff;
import org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentService;
import org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentVorschaubildCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Firmendokumente gegen echtes PostgreSQL: Zähler je Ordner (überfällig/bald fällig), Sortierung ohne
 * Groß/Klein, LIKE-Suche mit maskiertem „%“, Fälligkeitsliste, Namensdoppel ohne Groß/Klein und das
 * Löschen samt Datei nach dem Commit, dazu die Ordnerrechte je Abteilung (Suche/Fällige nur in sichtbaren
 * Ordnern, Rechte beim Verschieben und Löschen). Die Mockito-Tests sehen davon nur die Aufrufe.
 *
 * <p>Läuft nur mit {@code -Dkompat.db.url=jdbc:postgresql://…} (Wegwerf-Datenbank, siehe BACKEND_ARCH.md),
 * in der CI im Job „Datenbank-Kompatibilitaet (PostgreSQL)“. Die Dienste committen echt; die Testdaten
 * räumt {@link #aufraeumen()} weg.</p>
 */
@DataJpaTest(showSql = false, properties = { "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=true", "spring.flyway.out-of-order=true",
        "spring.flyway.validate-on-migrate=false", "spring.jpa.show-sql=false" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ FlywayStartSetupConfig.class, FirmendokumentService.class, FirmendokumentOrdnerService.class,
        FirmendokumentDateiablage.class, FirmendokumentZugriff.class, FirmendokumentOrdnerRechteService.class,
        FirmendokumentVorschaubildCache.class, FirmendokumentPostgresTest.FesteUhr.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "kompat.db.url", matches = "jdbc:postgresql:.*")
class FirmendokumentPostgresTest {

    private static final Path UPLOADS = erzeugeUploadOrdner();
    private static final byte[] PDF = "%PDF-1.7\nTest".getBytes(StandardCharsets.US_ASCII);

    @TestConfiguration
    static class FesteUhr {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-02T08:00:00Z"), ZoneId.of("Europe/Berlin"));
        }
    }

    @DynamicPropertySource
    static void datenbank(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getProperty("kompat.db.url"));
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.flyway.locations", () -> "classpath:db/postgresql/migration");
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET client_encoding TO 'UTF8'");
        // Kleiner Pool: viele Kontexte im Cache teilen sich die 100 Verbindungen der CI-Datenbank
        r.add("spring.datasource.hikari.maximum-pool-size", () -> "3");
        r.add("spring.datasource.hikari.minimum-idle", () -> "0");
        r.add("spring.datasource.username", () -> System.getProperty("kompat.db.user", "kompat"));
        r.add("spring.datasource.password", () -> System.getProperty("kompat.db.pass", "kompat"));
        r.add("file.upload-dir", UPLOADS::toString);
    }

    @Autowired FirmendokumentService service;
    @Autowired FirmendokumentOrdnerService ordnerService;
    @Autowired FirmendokumentDateiablage ablage;
    @Autowired FirmendokumentOrdnerRechteService rechteService;
    @Autowired AbteilungRepository abteilungRepository;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AbteilungsRechtPruefung rechtPruefung;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final List<Long> ordnerIds = new ArrayList<>();
    private final List<Long> abteilungIds = new ArrayList<>();

    private static Path erzeugeUploadOrdner() {
        try {
            Path ordner = Files.createTempDirectory("firmendokumente-pg");
            ordner.toFile().deleteOnExit();
            return ordner;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @AfterEach
    void aufraeumen() {
        for (Long id : ordnerIds) {
            jdbc.query("SELECT gespeicherter_dateiname FROM firmendokument WHERE ordner_id = ?",
                    rs -> { ablage.loesche(rs.getString(1)); }, id);
            jdbc.update("DELETE FROM firmendokument WHERE ordner_id = ?", id);
        }
        for (int i = ordnerIds.size() - 1; i >= 0; i--) {
            jdbc.update("DELETE FROM firmendokument_ordner WHERE id = ?", ordnerIds.get(i));
        }
        for (Long id : abteilungIds) {
            jdbc.update("DELETE FROM abteilung WHERE id = ?", id);
        }
    }

    private FirmendokumentOrdnerDto ordner(String name, Long parentId) {
        FirmendokumentOrdnerDto dto = ordnerService.anlegen(new FirmendokumentOrdnerAnlegenDto(name, parentId), null);
        ordnerIds.add(dto.id());
        return dto;
    }

    private FirmendokumentDto hochladen(Long ordnerId, String name, LocalDate faelligAm) {
        return service.hochladen(new MockMultipartFile("datei", "scan.pdf", "application/pdf", PDF), ordnerId,
                name, faelligAm, "Max Mustermann", null);
    }

    private static List<String> namenIm(FirmendokumentSeiteDto seite, Long ordnerId) {
        return seite.eintraege().stream().filter(d -> d.ordnerId().equals(ordnerId)).map(FirmendokumentDto::name).toList();
    }

    @Test
    @DisplayName("Zähler, Sortierung, Suche und Fälligkeit liefern auf PostgreSQL die erwarteten Dokumente")
    void abfragen() {
        Long pruefungen = ordner("Prüfungen " + suffix, null).id();
        Long tore = ordner("Tore", pruefungen).id();
        hochladen(tore, "Torprüfung Halle 2 " + suffix, LocalDate.of(2026, 10, 1));
        hochladen(tore, "wartung 50% Rabatt " + suffix, LocalDate.of(2026, 11, 20));
        hochladen(tore, "Gefährdung " + suffix, null);
        hochladen(tore, "Ausblick " + suffix, LocalDate.of(2027, 5, 1));

        FirmendokumentOrdnerDto toreDto = ordnerService.liste(null).stream().filter(o -> o.id().equals(tore)).findFirst()
                .orElseThrow();
        assertThat(toreDto.parentId()).isEqualTo(pruefungen);
        assertThat(toreDto.anzahlDokumente()).isEqualTo(4);
        assertThat(toreDto.anzahlUeberfaellig()).isEqualTo(1);
        assertThat(toreDto.anzahlBaldFaellig()).isEqualTo(1);

        FirmendokumentSeiteDto ersteSeite = service.imOrdner(tore, 0, 2, null);
        assertThat(ersteSeite.gesamt()).isEqualTo(4);
        assertThat(ersteSeite.eintraege()).extracting(FirmendokumentDto::name)
                .containsExactly("Ausblick " + suffix, "Gefährdung " + suffix);
        assertThat(service.imOrdner(tore, 1, 2, null).eintraege()).extracting(FirmendokumentDto::name)
                .containsExactly("Torprüfung Halle 2 " + suffix, "wartung 50% Rabatt " + suffix);

        assertThat(namenIm(service.suche("50% RABATT " + suffix, 0, 50, null), tore))
                .containsExactly("wartung 50% Rabatt " + suffix);
        assertThat(namenIm(service.suche("50_ rabatt " + suffix, 0, 50, null), tore)).isEmpty();
        assertThat(namenIm(service.suche("SCAN.PDF", 0, 50, null), tore)).hasSize(4);

        assertThat(namenIm(service.faellig(0, 50, null), tore))
                .containsExactly("Torprüfung Halle 2 " + suffix, "wartung 50% Rabatt " + suffix);
    }

    @Test
    @DisplayName("Namensdoppel ohne Groß/Klein, Ordner mit Inhalt bleibt, Löschen entfernt Eintrag und Datei")
    void bearbeitenUndLoeschen() {
        Long pruefungen = ordner("Prüfungen " + suffix, null).id();
        Long tore = ordner("Tore", pruefungen).id();
        assertThatThrownBy(() -> ordnerService.anlegen(new FirmendokumentOrdnerAnlegenDto("TORE", pruefungen), null))
                .hasMessageContaining("Hier gibt es schon einen Ordner");
        assertThatThrownBy(() -> ordnerService.verschieben(pruefungen, tore, null))
                .hasMessageContaining("nicht in sich selbst");

        FirmendokumentDto dokument = hochladen(tore, "Torprüfung " + suffix, null);
        String gespeichert = jdbc.queryForObject("SELECT gespeicherter_dateiname FROM firmendokument WHERE id = ?",
                String.class, dokument.id());
        assertThat(ablage.pfad(gespeichert)).exists();
        assertThatThrownBy(() -> ordnerService.loeschen(tore, null)).hasMessageContaining("Der Ordner ist nicht leer.");

        service.loeschen(dokument.id(), null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM firmendokument WHERE id = ?", Long.class, dokument.id()))
                .isZero();
        assertThat(ablage.pfad(gespeichert)).doesNotExist();
        ordnerService.loeschen(tore, null);
        ordnerIds.remove(tore);
    }

    private Long abteilung(String name) {
        Abteilung a = new Abteilung();
        a.setName(name + " " + suffix);
        Long id = abteilungRepository.save(a).getId();
        abteilungIds.add(id);
        return id;
    }

    @Test
    @DisplayName("Ordnerrechte: Werkstatt sieht eingeschränkten Zweig nur lesend, Geheimes gar nicht – auch in Suche und Fälligen")
    void ordnerrechte() {
        Long werkstatt = abteilung("Werkstatt");
        Long buero = abteilung("Büro");
        Long versicherungen = ordner("Versicherungen " + suffix, null).id();
        Long haftpflicht = ordner("Haftpflicht " + suffix, versicherungen).id();
        Long geheim = ordner("Geheim " + suffix, null).id();
        hochladen(haftpflicht, "Police " + suffix, LocalDate.of(2026, 11, 10));
        hochladen(geheim, "Police geheim " + suffix, LocalDate.of(2026, 11, 10));
        rechteService.speichern(versicherungen, new FirmendokumentOrdnerRechteSpeichernDto(true,
                List.of(new Eintrag(werkstatt, "ANSEHEN"), new Eintrag(buero, "AENDERN"))));
        rechteService.speichern(geheim, new FirmendokumentOrdnerRechteSpeichernDto(true, List.of()));
        Authentication max = mock(Authentication.class);
        given(rechtPruefung.zugehoerigkeit(max)).willReturn(new Zugehoerigkeit(false, Set.of(werkstatt)));
        Authentication admin = mock(Authentication.class);
        given(rechtPruefung.zugehoerigkeit(admin)).willReturn(new Zugehoerigkeit(true, Set.of()));

        List<FirmendokumentOrdnerDto> baum = ordnerService.liste(max);
        assertThat(baum).extracting(FirmendokumentOrdnerDto::id).contains(versicherungen, haftpflicht)
                .doesNotContain(geheim);
        assertThat(baum).filteredOn(o -> o.id().equals(haftpflicht)).singleElement()
                .satisfies(o -> assertThat(o.darfAendern()).isFalse())
                .satisfies(o -> assertThat(o.eingeschraenkt()).isTrue());
        assertThat(service.suche("police", 0, 50, max).eintraege()).extracting(FirmendokumentDto::name)
                .contains("Police " + suffix).doesNotContain("Police geheim " + suffix);
        assertThat(service.faellig(0, 50, max).eintraege()).extracting(FirmendokumentDto::name)
                .contains("Police " + suffix).doesNotContain("Police geheim " + suffix);
        assertThatThrownBy(() -> service.imOrdner(geheim, 0, 50, max)).hasMessageContaining("Den Ordner gibt es nicht mehr.");
        assertThatThrownBy(() -> hochladenAls(haftpflicht, max)).hasMessageContaining("Du darfst in diesem Ordner nichts ändern.");

        ordnerService.verschieben(haftpflicht, null, admin);
        assertThat(rechteService.laden(haftpflicht).eingeschraenkt()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM firmendokument_ordner_recht WHERE ordner_id = ?",
                Long.class, haftpflicht)).isEqualTo(2);

        ordnerService.verschieben(haftpflicht, versicherungen, admin);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM firmendokument_ordner_recht WHERE ordner_id = ?",
                Long.class, haftpflicht)).isZero();

        jdbc.update("DELETE FROM abteilung WHERE id = ?", buero);
        abteilungIds.remove(buero);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM firmendokument_ordner_recht WHERE abteilung_id = ?",
                Long.class, buero)).isZero();
    }

    private FirmendokumentDto hochladenAls(Long ordnerId, Authentication wer) {
        return service.hochladen(new MockMultipartFile("datei", "scan.pdf", "application/pdf", PDF), ordnerId,
                "Neu", null, "Max Mustermann", wer);
    }
}
