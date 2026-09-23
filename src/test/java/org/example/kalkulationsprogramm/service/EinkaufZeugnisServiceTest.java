package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.example.kalkulationsprogramm.domain.einkauf.BestellungPosition;
import org.example.kalkulationsprogramm.domain.einkauf.BestellungRevision;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufZeugnisErwartung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.DokumentSoll;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.example.kalkulationsprogramm.domain.einkauf.Positionsart;
import jakarta.persistence.EntityManager;
import org.example.kalkulationsprogramm.domain.einkauf.Dokumentart;
import org.example.kalkulationsprogramm.repository.BestellungRevisionRepository;
import org.example.kalkulationsprogramm.repository.EinkaufAnforderungsVorlageRepository;
import org.example.kalkulationsprogramm.repository.EinkaufZeugnisRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufZeugnisService;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class EinkaufZeugnisServiceTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.44")
            .withDatabaseName("einkauf_zeugnisse_dummy").withUsername("test").withPassword("test");

    @Test
    void snapshotzeugnisseWerdenJeAnforderungUndMitDerAngegebenenFristErwartet() {
        var revisions = mock(BestellungRevisionRepository.class);
        var zeugnisse = mock(EinkaufZeugnisRepository.class);
        var vorlagen = mock(EinkaufAnforderungsVorlageRepository.class);
        var revision=mock(BestellungRevision.class);
        var position=mock(BestellungPosition.class);
        var snapshot=new PositionSnapshot(Positionsart.ARTIKEL,21L,"DUMMY",null,null,"Profil","S235",null,
                null,null,null,null,null,null,List.of(
                        new DokumentSoll(Dokumentart.ZEUGNIS_3_1,"EN 10204, Stahl S235","SPEC-7",true),
                        new DokumentSoll(Dokumentart.ZEUGNIS_3_1,"Projektvorgabe, Charge separat","PROJ-8",true)),List.of());
        var saved=new AtomicReference<List<EinkaufZeugnisErwartung>>();
        when(revisions.findById(81L)).thenReturn(Optional.of(revision));
        when(revision.istAngenommen()).thenReturn(true);
        when(revision.istVerworfen()).thenReturn(false);
        when(revision.getNummer()).thenReturn(4);
        when(revision.getPositionen()).thenReturn(List.of(position));
        when(revision.getId()).thenReturn(81L);
        when(position.getId()).thenReturn(33L);
        when(position.getPosition()).thenReturn(snapshot);
        when(zeugnisse.findByRevision_IdOrderByIdAsc(81L)).thenReturn(List.of());
        doAnswer(call->{@SuppressWarnings("unchecked") var items=(Iterable<EinkaufZeugnisErwartung>)call.getArgument(0);var copy=new ArrayList<EinkaufZeugnisErwartung>();items.forEach(copy::add);saved.set(copy);return copy;})
                .when(zeugnisse).saveAll(anyIterable());
        var service = new EinkaufZeugnisService(revisions, zeugnisse, vorlagen,
                mock(org.example.kalkulationsprogramm.repository.EinkaufDateiRepository.class), mock(EntityManager.class));

        service.erwarte(81L,LocalDate.parse("2026-10-01"));

        assertEquals(2,saved.get().size());
        assertEquals(LocalDate.parse("2026-10-01"),saved.get().get(0).getFrist());
        assertEquals("SPEC-7",saved.get().get(0).getGrundlageVersion());
        assertEquals(0,saved.get().get(0).getAnforderungsIndex());
        assertEquals(1,saved.get().get(1).getAnforderungsIndex());
    }

    @Test
    void unbekannteGrundlageErzeugtNurPruefhinweisUndKeineAutomatischeFreigabe() {
        var revisions = mock(BestellungRevisionRepository.class);
        var zeugnisse = mock(EinkaufZeugnisRepository.class);
        var vorlagen = mock(EinkaufAnforderungsVorlageRepository.class);
        var service = new EinkaufZeugnisService(revisions, zeugnisse, vorlagen, mock(org.example.kalkulationsprogramm.repository.EinkaufDateiRepository.class), mock(EntityManager.class));
        when(vorlagen.findAktuelleBestaetigteFuerArtikelOderProjekt(44L, 7L)).thenReturn(List.of());

        var result = service.vorschlagen(44L, 7L);

        assertTrue(result.stream().anyMatch(soll -> !soll.fachlichBestaetigt()
                && soll.grundlage().contains("manuell prüfen")));
        verifyNoInteractions(revisions, zeugnisse);
    }

    @Test
    void erwartungBenutztNurAngenommeneBestellrevision() {
        var revisions = mock(BestellungRevisionRepository.class);
        var zeugnisse = mock(EinkaufZeugnisRepository.class);
        var vorlagen = mock(EinkaufAnforderungsVorlageRepository.class);
        var service = new EinkaufZeugnisService(revisions, zeugnisse, vorlagen, mock(org.example.kalkulationsprogramm.repository.EinkaufDateiRepository.class), mock(EntityManager.class));
        when(revisions.findById(81L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.erwarte(81L, LocalDate.parse("2026-10-01")));
        verify(zeugnisse, never()).save(any());
    }

    @Test
    void zeugnisMigrationErhaeltGemeinsameDateireferenzUndAppendOnlyPruefungen() throws Exception {
        try (var connection=java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())) {
            try(var statement=connection.createStatement()){
                statement.execute("CREATE TABLE einkauf_bestellung_revision(id BIGINT PRIMARY KEY) ENGINE=InnoDB");
                statement.execute("CREATE TABLE einkauf_bestellung_position(id BIGINT PRIMARY KEY) ENGINE=InnoDB");
                statement.execute("CREATE TABLE einkauf_datei(id BIGINT PRIMARY KEY) ENGINE=InnoDB");
                statement.execute("CREATE TABLE einkauf_lieferung_position(id BIGINT PRIMARY KEY) ENGINE=InnoDB");
                statement.execute("CREATE TABLE einkauf_charge(id BIGINT PRIMARY KEY) ENGINE=InnoDB");
            }
            var script=new org.springframework.core.io.support.EncodedResource(new org.springframework.core.io.ClassPathResource("db/migration/V394__einkauf_zeugnisse.sql"));
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,script);
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,script);
            try(var statement=connection.createStatement()){
                statement.executeUpdate("INSERT INTO einkauf_bestellung_revision VALUES(1)");
                statement.executeUpdate("INSERT INTO einkauf_bestellung_position VALUES(2),(3)");
                statement.executeUpdate("INSERT INTO einkauf_datei VALUES(4)");
                statement.executeUpdate("INSERT INTO einkauf_lieferung_position VALUES(5),(6)");
                statement.executeUpdate("INSERT INTO einkauf_charge VALUES(7),(8)");
                statement.executeUpdate("INSERT INTO einkauf_zeugnis_erwartung(revision_id,bestell_position_id,anforderungs_index,art,grundlage,grundlage_version,frist,status) VALUES(1,2,0,'ZEUGNIS_3_1','EN 10204','V1','2026-10-01','ANGEFORDERT'),(1,3,0,'CE_NACHWEIS','CE','V2','2026-10-01','ANGEFORDERT')");
                statement.executeUpdate("INSERT INTO einkauf_zeugnis_datei VALUES(1,4),(2,4)");
                statement.executeUpdate("INSERT INTO einkauf_zeugnis_lieferposition VALUES(1,5),(2,6)");
                statement.executeUpdate("INSERT INTO einkauf_zeugnis_charge VALUES(1,7),(2,8)");
                statement.executeUpdate("INSERT INTO einkauf_dokument_pruefung(zeugnis_id,akteur_id,geprueft_am,ergebnis,begruendung,grundlage_version) VALUES(1,9,NOW(6),'ABGELEHNT','Dokument unvollständig','V1'),(1,9,NOW(6),'BESTANDEN','Dokument vollständig','V1')");
                try(var rows=statement.executeQuery("SELECT (SELECT COUNT(*) FROM einkauf_zeugnis_datei WHERE datei_id=4),(SELECT COUNT(*) FROM einkauf_dokument_pruefung WHERE zeugnis_id=1)")){
                    assertTrue(rows.next());assertEquals(2,rows.getInt(1));assertEquals(2,rows.getInt(2));
                }
            }
        }
    }
}
