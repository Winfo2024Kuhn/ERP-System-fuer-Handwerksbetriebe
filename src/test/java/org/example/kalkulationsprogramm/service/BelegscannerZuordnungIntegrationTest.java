package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKiAnalyseStatus;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.BelegDto;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentVerknuepfungSperreRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Belegscanner gegen eine echte (H2-)Datenbank. Das Einordnen in die Kette läuft
 * erst nach dem Commit in eigener Transaktion – deshalb ohne Test-Transaktion;
 * der Test räumt die Datenbank selbst auf.
 *
 * <p>Nur der KI-Aufruf ist ersetzt. Nur Dummy-Daten, alle Nummern erfunden.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ BelegService.class, BelegKiAnalyseService.class, BelegZuordnungService.class,
        GeminiDokumentAnalyseService.class, LieferantDokumentAbgleich.class, SystemSettingsService.class,
        LieferantDokumentPositionService.class, LieferantDokumentPositionLeser.class,
        BelegscannerZuordnungIntegrationTest.Konfiguration.class })
class BelegscannerZuordnungIntegrationTest {

    @TestConfiguration
    static class Konfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean(name = "taskExecutor")
        TaskExecutor taskExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired private BelegService belegService;
    @Autowired private BelegKiAnalyseService belegKiAnalyseService;
    @Autowired private BelegRepository belegRepository;
    @Autowired private LieferantDokumentRepository dokumentRepository;
    @Autowired private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    @Autowired private LieferantDokumentVerknuepfungSperreRepository sperreRepository;
    @Autowired private LieferantenRepository lieferantenRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    // Externes (KI über Spy) und Nachbardienste ohne Bezug zur Zuordnung
    @SpyBean private GeminiDokumentAnalyseService analyseSpy;
    @MockBean private ZugferdExtractorService zugferdExtractorService;
    @MockBean private BelegKiKostenkontoService kostenkontoService;
    @MockBean private BelegSplitService belegSplitService;
    @MockBean private KasseSaldoService kasseSaldoService;
    @MockBean private BelegAuditService auditService;
    @MockBean private KassenbuchSchreibschutz schreibschutz;
    @MockBean private BelegVorschlagService vorschlagService;

    private TransactionTemplate tx;

    @BeforeEach
    void vorbereiten() {
        tx = new TransactionTemplate(transactionManager);
        org.mockito.Mockito.when(vorschlagService.ermittle(any()))
                .thenReturn(new BelegVorschlagService.Vorschlaege(null, null));
    }

    @AfterEach
    void raeumeAuf() {
        tx.executeWithoutResult(status -> {
            sperreRepository.deleteAll();
            dokumentRepository.findAll().forEach(d -> {
                d.getVerknuepfteDokumente().clear();
                dokumentRepository.save(d);
            });
            dokumentRepository.flush();
            dokumentRepository.deleteAll();
            belegRepository.deleteAll();
            lieferantenRepository.deleteAll();
        });
    }

    /** Lieferant „Muster GmbH“ mit Lieferschein LS-31000555; liefert [lieferantId, lieferscheinId]. */
    private Long[] lieferantMitLieferschein() {
        return tx.execute(status -> {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setLieferantenname("Muster GmbH");
            lieferant = lieferantenRepository.saveAndFlush(lieferant);
            LieferantDokument ls = new LieferantDokument();
            ls.setLieferant(lieferant);
            ls.setTyp(LieferantDokumentTyp.LIEFERSCHEIN);
            ls.setUploadDatum(LocalDateTime.of(2026, 6, 2, 8, 0));
            ls = dokumentRepository.saveAndFlush(ls);
            LieferantGeschaeftsdokument lsDaten = new LieferantGeschaeftsdokument();
            lsDaten.setDokument(ls);
            lsDaten.setDokumentNummer("LS-31000555");
            lsDaten.setDokumentDatum(LocalDate.of(2026, 6, 2));
            geschaeftsdokumentRepository.saveAndFlush(lsDaten);
            return new Long[] { lieferant.getId(), ls.getId() };
        });
    }

    private Long beleg(Long lieferantId) {
        return tx.execute(status -> {
            Beleg b = new Beleg();
            if (lieferantId != null) {
                b.setLieferant(lieferantenRepository.findById(lieferantId).orElseThrow());
            }
            b.setOriginalDateiname("scan.jpg");
            b.setGespeicherterDateiname("muster-scan.jpg");
            b.setMimeType("image/jpeg");
            b.setUploadDatum(LocalDateTime.of(2026, 6, 12, 9, 0));
            return belegRepository.saveAndFlush(b).getId();
        });
    }

    private LieferantDokumentDto.AnalyzeResponse kiRechnung() {
        return LieferantDokumentDto.AnalyzeResponse.builder()
                .dokumentTyp(LieferantDokumentTyp.RECHNUNG)
                .dokumentNummer("RE-33000777")
                .dokumentDatum(LocalDate.of(2026, 6, 10))
                .betragNetto(new BigDecimal("100.00"))
                .betragBrutto(new BigDecimal("119.00"))
                .referenzNummer("LS-31000555")
                .build();
    }

    @Test
    void mitLieferantEntstehtLieferantenDokumentAmLieferschein() {
        Long[] ids = lieferantMitLieferschein();
        Long belegId = beleg(ids[0]);
        doReturn(kiRechnung()).when(analyseSpy).analyzeFile(any(), anyString());

        belegKiAnalyseService.analysiereBelegAsync(belegId);

        tx.executeWithoutResult(status -> {
            assertThat(belegRepository.findById(belegId).orElseThrow().getKiAnalyseStatus())
                    .isEqualTo(BelegKiAnalyseStatus.DONE);
            LieferantDokument neu = dokumentRepository.findByBelegId(belegId).orElseThrow();
            assertThat(neu.getTyp()).isEqualTo(LieferantDokumentTyp.RECHNUNG);
            assertThat(neu.getVerknuepfteDokumente()).extracting(LieferantDokument::getId).containsExactly(ids[1]);
        });
    }

    @Test
    void fehlerBeimEinordnenVerwirftDieKiAuslesungNicht() {
        Long[] ids = lieferantMitLieferschein();
        Long belegId = beleg(ids[0]);
        doReturn(kiRechnung()).when(analyseSpy).analyzeFile(any(), anyString());
        doThrow(new IllegalStateException("Abgleich kaputt")).when(analyseSpy).performRelink(any());

        belegKiAnalyseService.analysiereBelegAsync(belegId);

        tx.executeWithoutResult(status -> {
            Beleg beleg = belegRepository.findById(belegId).orElseThrow();
            assertThat(beleg.getKiAnalyseStatus()).isEqualTo(BelegKiAnalyseStatus.DONE);
            assertThat(beleg.getBelegNummer()).isEqualTo("RE-33000777");
            // Das Lieferanten-Dokument ist gespeichert, nur noch nicht eingeordnet
            LieferantDokument neu = dokumentRepository.findByBelegId(belegId).orElseThrow();
            assertThat(neu.getVerknuepfteDokumente()).isEmpty();
        });
    }

    @Test
    void lieferantImPruefDialogGesetzt_lieferantenDokumentEntstehtUndFindetLieferschein() throws Exception {
        Long[] ids = lieferantMitLieferschein();
        // KI hat eine Rechnung erkannt, aber keinen Lieferanten gefunden
        String kiJson = new ObjectMapper().findAndRegisterModules().writeValueAsString(kiRechnung());
        Long ohneLieferant = beleg(null);
        Long belegId = tx.execute(status -> {
            Beleg beleg = belegRepository.findById(ohneLieferant).orElseThrow();
            beleg.setDokumentTyp(LieferantDokumentTyp.RECHNUNG);
            beleg.setBelegNummer("RE-33000777");
            beleg.setBelegDatum(LocalDate.of(2026, 6, 10));
            beleg.setKiExtraktionJson(kiJson);
            return belegRepository.saveAndFlush(beleg).getId();
        });

        BelegDto.UpdateRequest req = new BelegDto.UpdateRequest();
        req.setLieferantId(ids[0]);
        belegService.updateBeleg(belegId, req, null);

        tx.executeWithoutResult(status -> {
            LieferantDokument neu = dokumentRepository.findByBelegId(belegId).orElseThrow();
            assertThat(neu.getTyp()).isEqualTo(LieferantDokumentTyp.RECHNUNG);
            assertThat(neu.getLieferant().getId()).isEqualTo(ids[0]);
            assertThat(neu.getGeschaeftsdaten().getReferenzNummer()).isEqualTo("LS-31000555");
            assertThat(neu.getVerknuepfteDokumente()).extracting(LieferantDokument::getId).containsExactly(ids[1]);
        });

        // Erneutes Speichern im Dialog legt nichts doppelt an
        belegService.updateBeleg(belegId, req, null);
        tx.executeWithoutResult(status -> assertThat(dokumentRepository.findAll()).hasSize(2));
    }
}
