package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.argThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.sql.Connection;
import java.sql.DriverManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBedarf;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufsanfrageDto.Create;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufsanfrageDto.RevisionRequest;
import org.example.kalkulationsprogramm.repository.AnfrageLieferantRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRevisionRepository;
import org.example.kalkulationsprogramm.repository.EinkaufsanfrageRepository;
import org.example.kalkulationsprogramm.repository.EinkaufBedarfRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufsanfrageService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufDateiService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufPositionService;
import org.example.kalkulationsprogramm.service.DokumentnummerService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@Testcontainers
class EinkaufsanfrageServiceTest {
    @Container private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.44")
            .withDatabaseName("einkaufsanfrage_dummy").withUsername("test").withPassword("test");
    @Mock EinkaufsanfrageRepository anfragen;
    @Mock AnfrageRevisionRepository revisionen;
    @Mock AnfrageLieferantRepository lieferanten;
    @Mock EinkaufBedarfRepository bedarfe;
    @Mock DokumentnummerService nummern;
    @Mock EinkaufPositionService positionen;
    @Mock EinkaufDateiService dateien;
    @Mock EinkaufAuditService audit;
    @Mock org.example.kalkulationsprogramm.service.einkauf.LieferantEinkaufKontaktService kontakte;

    EinkaufsanfrageService service;

    @BeforeEach void setUp() {
        service = new EinkaufsanfrageService(anfragen, revisionen, lieferanten, bedarfe, nummern,
                positionen, dateien, new ObjectMapper(), audit, kontakte);
    }

    @Test void revisionLaesstVorherigeFassungUnveraendertUndProviderZaehltAktuelleHerkunftEinmal() {
        EinkaufBedarf bedarf = mock(EinkaufBedarf.class);
        when(bedarf.getId()).thenReturn(7L); when(bedarf.getVersion()).thenReturn(3L);
        when(bedarf.getBedarfMenge()).thenReturn(new BigDecimal("10")); when(bedarf.disponierbar()).thenReturn(new BigDecimal("10"));
        when(bedarf.getPosition()).thenReturn(testPosition(new BigDecimal("10"))); when(bedarf.getLiefergruppe()).thenReturn(null);
        when(bedarfe.findeAlleFuerUpdate(List.of(7L))).thenReturn(List.of(bedarf));
        when(positionen.validiere(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(positionen.mitTeilmenge(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(anfragen.saveAndFlush(any())).thenAnswer(invocation -> {
            var head = invocation.getArgument(0, org.example.kalkulationsprogramm.domain.einkauf.Einkaufsanfrage.class);
            head.setVersion(head.getVersion() == null ? 0 : head.getVersion() + 1);
            return head;
        });
        var head = new org.example.kalkulationsprogramm.domain.einkauf.Einkaufsanfrage("PA-2026-00001", 9L, UUID.randomUUID(), "hash");
        head.setId(22L); head.setVersion(0L);
        var old = new org.example.kalkulationsprogramm.domain.einkauf.AnfrageRevision(head, 1, null, null, UUID.randomUUID(), "old");
        old.setId(31L); head.setAktuelleRevision(old);
        when(anfragen.findById(22L)).thenReturn(Optional.of(head));
        when(anfragen.findByIdForUpdate(22L)).thenReturn(Optional.of(head));
        java.util.concurrent.atomic.AtomicReference<org.example.kalkulationsprogramm.domain.einkauf.AnfrageRevision> newRevision = new java.util.concurrent.atomic.AtomicReference<>();
        when(revisionen.findByIdempotenzKey(any())).thenAnswer(invocation -> newRevision.get() == null ? Optional.empty() : Optional.of(newRevision.get()));
        when(revisionen.saveAndFlush(any())).thenAnswer(invocation -> {
            var revision = invocation.getArgument(0, org.example.kalkulationsprogramm.domain.einkauf.AnfrageRevision.class);
            revision.setId(32L); newRevision.set(revision); return revision;
        });
        when(revisionen.summenAktuelleAnfragen(List.of(7L))).thenReturn(List.<Object[]>of(new Object[] {7L, new BigDecimal("4.000000")}));

        Create revisionInput = new Create(List.of(new Herkunft(7L, 3, new BigDecimal("4"))), List.of(), null, null, 9L, UUID.randomUUID());
        RevisionRequest revisionRequest = new RevisionRequest(0, revisionInput);
        var revised = service.revidieren(22L, revisionRequest, 9L);
        var repeated = service.revidieren(22L, revisionRequest, 9L);
        assertEquals(31L, old.getId());
        assertEquals(1, old.getNummer());
        assertEquals(32L, revised.kopf().aktuelleRevisionId());
        assertEquals(2, revised.kopf().revisionsNummer());
        assertEquals(revised.kopf().aktuelleRevisionId(), repeated.kopf().aktuelleRevisionId());
        assertEquals(new BigDecimal("4.000000"), service.angefragtFuerBedarfe(List.of(7L)).get(7L));
        verify(revisionen).summenAktuelleAnfragen(List.of(7L));
        verify(bedarf, never()).setReserviert(any());
    }

    @Test void lieferantenabsageWirdVersionsgeprueftUndAuditiert() {
        var participation = mock(org.example.kalkulationsprogramm.domain.einkauf.AnfrageLieferant.class);
        when(participation.getId()).thenReturn(41L);
        when(participation.getVersion()).thenReturn(2L);
        java.util.concurrent.atomic.AtomicReference<String> state = new java.util.concurrent.atomic.AtomicReference<>("AUSSTEHEND");
        when(participation.getStatus()).thenAnswer(invocation -> state.get());
        org.mockito.Mockito.doAnswer(invocation -> { state.set(invocation.getArgument(0)); return null; }).when(participation).setStatus(any());
        when(participation.getKontakt()).thenReturn(new Snapshot(3L, 4L, "Lieferant C", "test@example.com", null, null, null));
        when(lieferanten.findByIdAndRevisionAnfrageId(41L, 22L)).thenReturn(Optional.of(participation));
        when(lieferanten.saveAndFlush(participation)).thenReturn(participation);

        var result = service.aktualisiereLieferantenstatus(22L, 41L,
                new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufsanfrageDto.LieferantenstatusRequest(2, "ABSAGE"), 9L);

        assertEquals("ABGESAGT", result.status());
        verify(participation).setStatus("ABGESAGT");
        verify(audit).protokolliere(eq("EINKAUFSANFRAGE_LIEFERANT"), eq(41L), eq("LIEFERANT_ABSAGE"), eq(9L), any(), any(), isNull());
    }

    @Test void geloeschteAnfrageWirdAuditiertUndVonAktuellenAnfragemengenAusgeschlossen() {
        var head = new org.example.kalkulationsprogramm.domain.einkauf.Einkaufsanfrage("PA-2026-00001", 9L, UUID.randomUUID(), "hash");
        head.setId(22L); head.setVersion(0L);
        when(anfragen.findByIdForUpdate(22L)).thenReturn(Optional.of(head));
        when(anfragen.saveAndFlush(head)).thenReturn(head);
        when(revisionen.summenAktuelleAnfragen(List.of(7L))).thenReturn(List.of());

        service.loeschen(22L, 0L, 9L);

        assertEquals(true, head.isGeloescht());
        assertEquals(Map.of(), service.angefragtFuerBedarfe(List.of(7L)));
        when(anfragen.findById(22L)).thenReturn(Optional.of(head));
        assertThrows(org.example.kalkulationsprogramm.exception.NotFoundException.class, () -> service.laden(22L));
        verify(audit).protokolliere(eq("EINKAUFSANFRAGE"), eq(22L), eq("ANFRAGE_GELOESCHT"), eq(9L), any(), any(), isNull());
        verify(revisionen).summenAktuelleAnfragen(List.of(7L));
    }

    @Test void migration386IstAufMySqlWiederholbarUndSpeichertRevisionenUndHerkunft() throws Exception {
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE einkauf_bedarf (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB");
            }
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource("db/migration/V386__einkaufsanfragen_revisionen.sql")));
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource("db/migration/V386__einkaufsanfragen_revisionen.sql")));
            try (var query = connection.createStatement(); var rows = query.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'einkaufsanfrage%'") ) {
                rows.next(); assertEquals(5, rows.getInt(1));
            }
            try (var query = connection.createStatement()) {
                query.executeUpdate("INSERT INTO einkauf_bedarf (id) VALUES (1)");
                query.executeUpdate("INSERT INTO einkaufsanfrage (version,pa_nummer,zustaendig_id,idempotenz_key,payload_hash) VALUES (0,'PA-2026-00001',9,'00000000-0000-0000-0000-000000000001',REPEAT('a',64))");
                query.executeUpdate("INSERT INTO einkaufsanfrage_revision (anfrage_id,nummer,status,idempotenz_key,payload_hash) VALUES (1,1,'ENTWURF','00000000-0000-0000-0000-000000000002',REPEAT('b',64))");
                query.executeUpdate("INSERT INTO einkaufsanfrage_position (revision_id,position_snapshot,menge) VALUES (1,JSON_OBJECT('bezeichnung','Dummy'),4)");
                query.executeUpdate("INSERT INTO einkaufsanfrage_herkunft (position_id,bedarf_id,bedarf_version,menge) VALUES (1,1,0,4)");
                query.executeUpdate("UPDATE einkaufsanfrage SET aktuelle_revision_id=1 WHERE id=1");
                query.executeUpdate("INSERT INTO einkaufsanfrage_revision (anfrage_id,nummer,status,idempotenz_key,payload_hash) VALUES (1,2,'ENTWURF','00000000-0000-0000-0000-000000000003',REPEAT('c',64))");
                query.executeUpdate("INSERT INTO einkaufsanfrage_position (revision_id,position_snapshot,menge) VALUES (2,JSON_OBJECT('bezeichnung','Neue Fassung'),2)");
                query.executeUpdate("INSERT INTO einkaufsanfrage_herkunft (position_id,bedarf_id,bedarf_version,menge) VALUES (2,1,0,2)");
                query.executeUpdate("INSERT INTO einkaufsanfrage_lieferant (revision_id,kontakt_snapshot,rueckmeldecode,status,versandversuche) VALUES (2,JSON_OBJECT('lieferantId',3),'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa','AUSSTEHEND',JSON_ARRAY()), (2,JSON_OBJECT('lieferantId',4),'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb','AUSSTEHEND',JSON_ARRAY()), (2,JSON_OBJECT('lieferantId',5),'cccccccccccccccccccccccccccccccc','AUSSTEHEND',JSON_ARRAY())");
                query.executeUpdate("UPDATE einkaufsanfrage SET aktuelle_revision_id=2 WHERE id=1");
                try (var rows = query.executeQuery("SELECT SUM(h.menge) FROM einkaufsanfrage_herkunft h JOIN einkaufsanfrage_position p ON p.id=h.position_id JOIN einkaufsanfrage_revision r ON r.id=p.revision_id JOIN einkaufsanfrage a ON a.aktuelle_revision_id=r.id WHERE h.bedarf_id=1 AND a.geloescht_am IS NULL")) {
                    rows.next(); assertEquals(new BigDecimal("2.000000"), rows.getBigDecimal(1));
                }
                query.executeUpdate("UPDATE einkaufsanfrage SET geloescht_am=CURRENT_TIMESTAMP(6) WHERE id=1");
                try (var rows = query.executeQuery("SELECT SUM(h.menge) FROM einkaufsanfrage_herkunft h JOIN einkaufsanfrage_position p ON p.id=h.position_id JOIN einkaufsanfrage_revision r ON r.id=p.revision_id JOIN einkaufsanfrage a ON a.aktuelle_revision_id=r.id WHERE h.bedarf_id=1 AND a.geloescht_am IS NULL")) {
                    rows.next(); assertEquals(null, rows.getBigDecimal(1));
                }
            }
        }
    }

    @Test void anfrageErzeugtEinmaligePaUndMultipliziertAngefragteMengeNichtMitLieferanten() {
        EinkaufBedarf bedarf = mock(EinkaufBedarf.class);
        when(bedarf.getId()).thenReturn(7L);
        when(bedarf.getVersion()).thenReturn(3L);
        when(bedarf.getBedarfMenge()).thenReturn(new BigDecimal("10"));
        when(bedarf.disponierbar()).thenReturn(new BigDecimal("10"));
        when(bedarf.getPosition()).thenReturn(testPosition(new BigDecimal("10")));
        when(bedarf.getLiefergruppe()).thenReturn(null);
        when(bedarfe.findeAlleFuerUpdate(List.of(7L))).thenReturn(List.of(bedarf));
        when(positionen.validiere(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(positionen.mitTeilmenge(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(kontakte.snapshot(any(), any(), any(), any())).thenAnswer(invocation -> new Snapshot(invocation.getArgument(0), invocation.getArgument(1), "Lieferant", invocation.getArgument(2), null, null, null));
        when(nummern.naechsteEinkaufsnummer(eq("PA"), any(LocalDate.class))).thenReturn("PA-2026-00001");
        when(anfragen.saveAndFlush(any())).thenAnswer(invocation -> {
            var anfrage = invocation.getArgument(0, org.example.kalkulationsprogramm.domain.einkauf.Einkaufsanfrage.class);
            anfrage.setId(22L);
            return anfrage;
        });
        when(revisionen.saveAndFlush(any())).thenAnswer(invocation -> {
            var revision = invocation.getArgument(0, org.example.kalkulationsprogramm.domain.einkauf.AnfrageRevision.class);
            revision.setId(31L);
            return revision;
        });

        Create createRequest = new Create(List.of(new Herkunft(7L, 3, new BigDecimal("4"))),
                List.of(new Snapshot(1L, 2L, "Lieferant A", "test@example.com", "Max Mustermann", null, null),
                        new Snapshot(2L, 3L, "Lieferant B", "test@example.com", null, null, null),
                        new Snapshot(3L, 4L, "Lieferant C", "test@example.com", null, null, null)),
                null, null, 9L, UUID.randomUUID());
        var created = service.anlegen(createRequest, 9L);

        org.mockito.ArgumentCaptor<org.example.kalkulationsprogramm.domain.einkauf.AnfrageRevision> revisionCaptor = org.mockito.ArgumentCaptor.forClass(org.example.kalkulationsprogramm.domain.einkauf.AnfrageRevision.class);
        verify(revisionen, times(2)).saveAndFlush(revisionCaptor.capture());
        assertEquals(createRequest.idempotenzKey(), revisionCaptor.getAllValues().get(0).getIdempotenzKey());
        assertEquals(64, revisionCaptor.getAllValues().get(0).getPayloadHash().length());
        assertEquals("PA-2026-00001", created.kopf().paNummer());
        assertEquals(3, created.lieferanten().size());
        verify(nummern, times(1)).naechsteEinkaufsnummer(eq("PA"), any(LocalDate.class));
        verify(bedarf, never()).setReserviert(any());
        assertEquals(new BigDecimal("10"), bedarf.getBedarfMenge());
    }

    @Test void historischeFassungBleibtLesbarUndBehaeltAktuellenKopf() {
        var head = new org.example.kalkulationsprogramm.domain.einkauf.Einkaufsanfrage("PA-2026-00001", 9L, UUID.randomUUID(), "hash");
        head.setId(22L); head.setVersion(4L);
        var alt = new org.example.kalkulationsprogramm.domain.einkauf.AnfrageRevision(head, 1, LocalDate.of(2026, 9, 25), null, UUID.randomUUID(), "old");
        var neu = new org.example.kalkulationsprogramm.domain.einkauf.AnfrageRevision(head, 2, null, null, UUID.randomUUID(), "new");
        alt.setId(31L); neu.setId(32L); head.setAktuelleRevision(neu);
        var kontakt = new Snapshot(3L, 4L, "Dummy Lieferant", "test@example.com", null, null, "00017");
        alt.addLieferant(new org.example.kalkulationsprogramm.domain.einkauf.AnfrageLieferant(alt, kontakt));
        when(anfragen.findById(22L)).thenReturn(Optional.of(head));
        when(revisionen.findByIdAndAnfrageId(31L, 22L)).thenReturn(Optional.of(alt));
        when(revisionen.findByAnfrageIdOrderByNummerAsc(22L)).thenReturn(List.of(alt, neu));
        var detail = service.revisionLaden(22L, 31L);
        assertEquals(32L, detail.kopf().aktuelleRevisionId());
        assertEquals(31L, detail.angezeigteRevisionId());
        assertEquals(true, detail.historisch());
        assertEquals(1, detail.kopf().revisionsNummer());
        assertEquals("00017", detail.lieferanten().getFirst().kontakt().eigeneKundennummer());
        assertEquals(List.of(31L, 32L), service.revisionen(22L).stream().map(r -> r.id()).toList());
        verify(anfragen, never()).saveAndFlush(any());
    }

    @Test void fremdeOderGeloeschteRevisionBleibtGesperrt() {
        var head = new org.example.kalkulationsprogramm.domain.einkauf.Einkaufsanfrage("PA-2026-00001", 9L, UUID.randomUUID(), "hash");
        head.setId(22L);
        when(anfragen.findById(22L)).thenReturn(Optional.of(head));
        assertThrows(org.example.kalkulationsprogramm.exception.NotFoundException.class, () -> service.revisionLaden(22L, 99L));
        assertThrows(IllegalArgumentException.class, () -> service.revisionLaden(22L, -1L));
        head.markiereGeloescht(java.time.Instant.now());
        assertThrows(org.example.kalkulationsprogramm.exception.NotFoundException.class, () -> service.revisionen(22L));
        verify(revisionen, never()).findByAnfrageIdOrderByNummerAsc(any());
    }

    @Test void listeLaedtProjektUndAntwortzahlenImBatchNurFuerAktuelleRevisionen() {
        var head = new org.example.kalkulationsprogramm.domain.einkauf.Einkaufsanfrage("PA-2026-00001", 9L, UUID.randomUUID(), "hash");
        head.setId(22L);
        var rev = new org.example.kalkulationsprogramm.domain.einkauf.AnfrageRevision(head, 2, null, null, UUID.randomUUID(), "new");
        rev.setId(32L); head.setAktuelleRevision(rev);
        var page = org.springframework.data.domain.PageRequest.of(0, 20);
        when(anfragen.findAllByGeloeschtAmIsNullOrderByAngelegtAmDesc(page)).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(head)));
        when(revisionen.projekteFuerRevisionen(List.of(32L))).thenReturn(List.<Object[]>of(new Object[]{32L, 8L}, new Object[]{32L, 7L}));
        when(revisionen.antwortenFuerRevisionen(List.of(32L))).thenReturn(List.<Object[]>of(new Object[]{32L, 3L, 2L}));
        var kopf = service.suchen(page).getContent().getFirst();
        assertEquals(List.of(7L, 8L), kopf.projektIds());
        assertEquals(2, kopf.antworten());
        assertEquals(3, kopf.lieferantenAnzahl());
    }

    private static org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot testPosition(BigDecimal menge) {
        return new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot(
                org.example.kalkulationsprogramm.domain.einkauf.Positionsart.ARTIKEL, 5L, "A-5", null, null,
                "Dummyartikel", null, null,
                new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis(menge,
                        org.example.kalkulationsprogramm.domain.einkauf.Einheit.STUECK, menge, null, null, null),
                null, null, null, null, null, List.of(), List.of());
    }
}
