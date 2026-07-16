package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.FreigabeQuellTyp;
import org.example.kalkulationsprogramm.domain.FreigabeStatus;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentErstellenDto;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DokumentFreigabeServiceTest {

    @Mock
    private DokumentFreigabeRepository repository;
    @Mock
    private AnfrageDokumentRepository anfrageDokumentRepository;
    @Mock
    private ProjektDokumentRepository projektDokumentRepository;
    @Mock
    private AusgangsGeschaeftsDokumentRepository ausgangsGeschaeftsDokumentRepository;
    @Mock
    private AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;
    @Mock
    private AusgangsGeschaeftsDokumentAuditService ausgangsGeschaeftsDokumentAuditService;
    @Mock
    private WebPushService webPushService;
    @Mock
    private DateiSpeicherService dateiSpeicherService;
    @Mock
    private AutoAuftragsbestaetigungVersandService autoAuftragsbestaetigungVersandService;
    @Mock
    private ProjektManagementService projektManagementService;
    @Mock
    private AnfrageRepository anfrageRepository;
    @Mock
    private org.springframework.core.task.TaskExecutor taskExecutor;

    @InjectMocks
    private DokumentFreigabeService service;

    /**
     * Regression: Filter "Angebot angenommen" zeigte 0 Treffer, weil der Service
     * nur Freigaben mit QuellTyp ANFRAGE (altes System) berücksichtigte. Neue
     * Angebote werden im AusgangsGeschaeftsDokument-System geführt und tragen
     * QuellTyp AUSGANGS_DOKUMENT.
     */
    @Test
    void findJuengsteProAnfrage_findetAcceptedAuchFuerAusgangsGeschaeftsDokumente() {
        Long anfrageId = 42L;
        Long ausgangsDokId = 700L;

        when(anfrageDokumentRepository.findGeschaeftsdokumentIdMappingByAnfrageIds(List.of(anfrageId)))
                .thenReturn(List.of());
        List<Object[]> mappingNeu = List.<Object[]>of(new Object[] { ausgangsDokId, anfrageId });
        when(ausgangsGeschaeftsDokumentRepository.findIdAnfrageIdMappingByAnfrageIds(List.of(anfrageId)))
                .thenReturn(mappingNeu);

        DokumentFreigabe freigabe = new DokumentFreigabe();
        freigabe.setQuellTyp(FreigabeQuellTyp.AUSGANGS_DOKUMENT);
        freigabe.setQuellDokumentId(ausgangsDokId);
        freigabe.setStatus(FreigabeStatus.ACCEPTED);
        freigabe.setErstelltAm(LocalDateTime.now().minusDays(1));
        freigabe.setAkzeptiertAm(LocalDateTime.now());
        when(repository.findByQuelle(eq(FreigabeQuellTyp.AUSGANGS_DOKUMENT), eq(List.of(ausgangsDokId))))
                .thenReturn(List.of(freigabe));

        Map<Long, DokumentFreigabe> result = service.findJuengsteProAnfrage(List.of(anfrageId));

        assertThat(result).containsKey(anfrageId);
        assertThat(result.get(anfrageId).getStatus()).isEqualTo(FreigabeStatus.ACCEPTED);
    }

    @Test
    void findJuengsteProAnfrage_leereListeLiefertLeereMap() {
        assertThat(service.findJuengsteProAnfrage(List.of())).isEmpty();
    }

    // ============== Beweissicherung: Vor- und Nachname bei Annahme ==============

    @Test
    void akzeptiere_ohneVorname_wirftIllegalArgumentException() {
        // Service-Check: Auch wenn die Bean-Validation umgangen wird (z.B. interner
        // Direktaufruf), darf eine Annahme nicht ohne Namen durchlaufen.
        DokumentFreigabe pending = pendingFreigabe("uuid-1");
        when(repository.findByUuid("uuid-1")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() ->
                service.akzeptiere("uuid-1", "1.2.3.4", "UA", "max@mustermann.de",
                        "   ", "Mustermann", "Mustermann"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vor- und Nachname");
    }

    @Test
    void akzeptiere_ohneNachname_wirftIllegalArgumentException() {
        DokumentFreigabe pending = pendingFreigabe("uuid-2");
        when(repository.findByUuid("uuid-2")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() ->
                service.akzeptiere("uuid-2", "1.2.3.4", "UA", null,
                        "Max", "", "Max"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vor- und Nachname");
    }

    @Test
    void akzeptiere_speichertNormalisiertenVorUndNachnameSowieZusammengesetztenAnzeigeName() {
        DokumentFreigabe pending = pendingFreigabe("uuid-3");
        when(repository.findByUuid("uuid-3")).thenReturn(Optional.of(pending));
        when(repository.save(any(DokumentFreigabe.class))).thenAnswer(inv -> inv.getArgument(0));
        // Auto-AB-Pfad inaktiv lassen: kein AusgangsGeschaeftsDokument finden.
        when(ausgangsGeschaeftsDokumentRepository.findById(any())).thenReturn(Optional.empty());

        DokumentFreigabe result = service.akzeptiere(
                "uuid-3", "1.2.3.4", "UA", "max@mustermann.de",
                "  Max   ", "  Mustermann\t", "");

        assertThat(result.getStatus()).isEqualTo(FreigabeStatus.ACCEPTED);
        assertThat(result.getUnterzeichnerVorname()).isEqualTo("Max");
        assertThat(result.getUnterzeichnerNachname()).isEqualTo("Mustermann");
        assertThat(result.getUnterzeichnerName()).isEqualTo("Max Mustermann");
        assertThat(result.getAkzeptiertAm()).isNotNull();
        assertThat(result.getHashAcceptance()).isNotBlank();
    }

    @Test
    void akzeptiere_idempotent_doppelteAnnahmeAendertNamensfelderNicht() {
        // Erster Klick hat bereits ACCEPTED inkl. Namen, Hash und Zeitstempel.
        DokumentFreigabe bereits = new DokumentFreigabe();
        bereits.setUuid("uuid-4");
        bereits.setStatus(FreigabeStatus.ACCEPTED);
        bereits.setUnterzeichnerVorname("Max");
        bereits.setUnterzeichnerNachname("Mustermann");
        bereits.setUnterzeichnerName("Max Mustermann");
        bereits.setAkzeptiertAm(LocalDateTime.now().minusMinutes(10));
        bereits.setAkzeptiertIp("1.1.1.1");
        bereits.setHashAcceptance("hash-vom-ersten-klick");
        when(repository.findByUuid("uuid-4")).thenReturn(Optional.of(bereits));

        DokumentFreigabe result = service.akzeptiere(
                "uuid-4", "9.9.9.9", "neuer UA", "anders@mustermann.de",
                "Erika", "Musterfrau", "Erika Musterfrau");

        // Felder bleiben unverändert — der erste Klick ist der Beweis.
        assertThat(result.getUnterzeichnerVorname()).isEqualTo("Max");
        assertThat(result.getUnterzeichnerNachname()).isEqualTo("Mustermann");
        assertThat(result.getUnterzeichnerName()).isEqualTo("Max Mustermann");
        assertThat(result.getAkzeptiertIp()).isEqualTo("1.1.1.1");
        assertThat(result.getHashAcceptance()).isEqualTo("hash-vom-ersten-klick");
    }

    // ============== Mitbeauftragte Alternativpositionen ==============

    @Test
    void akzeptiere_mitAlternativen_speichertAuswahlBetragUndErzeugtAbMitMergePositionen() {
        DokumentFreigabe pending = pendingFreigabe("uuid-alt");
        when(repository.findByUuid("uuid-alt")).thenReturn(Optional.of(pending));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String positionenJson = "{\"blocks\":[]}";
        AusgangsGeschaeftsDokument angebot = new AusgangsGeschaeftsDokument();
        angebot.setId(123L);
        angebot.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
        angebot.setPositionenJson(positionenJson);
        angebot.setBetragNetto(new BigDecimal("1000.00"));
        angebot.setMwstSatz(new BigDecimal("0.19"));
        when(ausgangsGeschaeftsDokumentRepository.findById(123L)).thenReturn(Optional.of(angebot));
        when(ausgangsGeschaeftsDokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Nur diese beiden IDs sind im Dokument tatsächlich optional.
        when(ausgangsGeschaeftsDokumentService.sammleOptionaleAlternativIds(positionenJson))
                .thenReturn(Set.of("alt-1", "alt-2"));
        when(ausgangsGeschaeftsDokumentService.summeAusgewaehlterAlternativenNetto(eq(positionenJson), any()))
                .thenReturn(new BigDecimal("200.00"));
        when(ausgangsGeschaeftsDokumentService.bereitePositionenFuerTypwechsel(positionenJson))
                .thenReturn(positionenJson);
        when(ausgangsGeschaeftsDokumentService.markiereAlternativenAlsBeauftragt(eq(positionenJson), any()))
                .thenReturn("{\"merged\":true}");
        when(ausgangsGeschaeftsDokumentService.erstellen(any())).thenReturn(null);

        // "fremd-999" ist nicht optional → muss verworfen werden (Tamper-Schutz).
        DokumentFreigabe result = service.akzeptiere(
                "uuid-alt", "1.2.3.4", "UA", "max@mustermann.de",
                "Max", "Mustermann", "Max Mustermann",
                List.of("alt-2", "alt-1", "fremd-999"));

        // Auswahl persistiert: nur gültige IDs, sortiert, als JSON.
        assertThat(result.getAkzeptierteAlternativen()).isEqualTo("[\"alt-1\",\"alt-2\"]");
        // Verbindlicher Betrag = (1000 + 200) * 1,19 = 1428,00.
        assertThat(result.getAkzeptierterBetrag()).isEqualByComparingTo("1428.00");

        // AB wurde mit den zusammengeführten Positionen und neuem Netto (1200) erzeugt.
        ArgumentCaptor<AusgangsGeschaeftsDokumentErstellenDto> captor =
                ArgumentCaptor.forClass(AusgangsGeschaeftsDokumentErstellenDto.class);
        verify(ausgangsGeschaeftsDokumentService).erstellen(captor.capture());
        assertThat(captor.getValue().getPositionenJson()).isEqualTo("{\"merged\":true}");
        assertThat(captor.getValue().getBetragNetto()).isEqualByComparingTo("1200.00");
        assertThat(captor.getValue().getTyp()).isEqualTo(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
    }

    @Test
    void akzeptiere_nurUngueltigeAlternativIds_speichertKeineAuswahlUndKeinenBetrag() {
        DokumentFreigabe pending = pendingFreigabe("uuid-fremd");
        when(repository.findByUuid("uuid-fremd")).thenReturn(Optional.of(pending));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String positionenJson = "{\"blocks\":[]}";
        AusgangsGeschaeftsDokument angebot = new AusgangsGeschaeftsDokument();
        angebot.setId(123L);
        angebot.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
        angebot.setPositionenJson(positionenJson);
        when(ausgangsGeschaeftsDokumentRepository.findById(123L)).thenReturn(Optional.of(angebot));
        when(ausgangsGeschaeftsDokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ausgangsGeschaeftsDokumentService.sammleOptionaleAlternativIds(positionenJson))
                .thenReturn(Set.of("alt-1"));
        when(ausgangsGeschaeftsDokumentService.erstellen(any())).thenReturn(null);

        DokumentFreigabe result = service.akzeptiere(
                "uuid-fremd", "1.2.3.4", "UA", "max@mustermann.de",
                "Max", "Mustermann", "Max Mustermann",
                List.of("fremd-1", "fremd-2"));

        assertThat(result.getStatus()).isEqualTo(FreigabeStatus.ACCEPTED);
        assertThat(result.getAkzeptierteAlternativen()).isNull();
        assertThat(result.getAkzeptierterBetrag()).isNull();

        // AB ohne explizite Positionen/Betrag (Standard-Pfad, Service erbt selbst).
        ArgumentCaptor<AusgangsGeschaeftsDokumentErstellenDto> captor =
                ArgumentCaptor.forClass(AusgangsGeschaeftsDokumentErstellenDto.class);
        verify(ausgangsGeschaeftsDokumentService).erstellen(captor.capture());
        assertThat(captor.getValue().getPositionenJson()).isNull();
        assertThat(captor.getValue().getBetragNetto()).isNull();
    }

    @Test
    void akzeptiere_mitSnapshot_rechnetGegenSnapshotNichtGegenGeaendertesLiveDokument() {
        // GoBD/Tamper: Snapshot vom Versand-Zeitpunkt ist maßgeblich, auch wenn das
        // Live-Dokument danach bearbeitet wurde.
        DokumentFreigabe pending = pendingFreigabe("uuid-snap");
        String snapshotJson = "{\"snapshot\":true}";
        pending.setPositionenSnapshot(snapshotJson);
        pending.setBasisNetto(new BigDecimal("1000.00"));
        pending.setMwstSatz(new BigDecimal("0.19"));
        when(repository.findByUuid("uuid-snap")).thenReturn(Optional.of(pending));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Live-Dokument wurde NACH Versand geändert (andere Positionen + völlig anderer Betrag).
        AusgangsGeschaeftsDokument liveGeaendert = new AusgangsGeschaeftsDokument();
        liveGeaendert.setId(123L);
        liveGeaendert.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
        liveGeaendert.setPositionenJson("{\"live\":\"changed\"}");
        liveGeaendert.setBetragNetto(new BigDecimal("9999.00"));
        liveGeaendert.setMwstSatz(new BigDecimal("0.19"));
        when(ausgangsGeschaeftsDokumentRepository.findById(123L)).thenReturn(Optional.of(liveGeaendert));
        when(ausgangsGeschaeftsDokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Alle Helfer arbeiten auf dem SNAPSHOT-JSON, nie auf dem Live-JSON.
        when(ausgangsGeschaeftsDokumentService.sammleOptionaleAlternativIds(snapshotJson))
                .thenReturn(Set.of("alt-1"));
        when(ausgangsGeschaeftsDokumentService.summeAusgewaehlterAlternativenNetto(eq(snapshotJson), any()))
                .thenReturn(new BigDecimal("200.00"));
        when(ausgangsGeschaeftsDokumentService.bereitePositionenFuerTypwechsel(snapshotJson))
                .thenReturn(snapshotJson);
        when(ausgangsGeschaeftsDokumentService.markiereAlternativenAlsBeauftragt(eq(snapshotJson), any()))
                .thenReturn("{\"merged\":true}");
        when(ausgangsGeschaeftsDokumentService.erstellen(any())).thenReturn(null);

        DokumentFreigabe result = service.akzeptiere(
                "uuid-snap", "1.2.3.4", "UA", "max@mustermann.de",
                "Max", "Mustermann", "Max Mustermann", List.of("alt-1"));

        // Betrag aus Snapshot-Basis (1000), NICHT aus Live (9999): (1000+200)*1,19 = 1428,00.
        assertThat(result.getAkzeptierterBetrag()).isEqualByComparingTo("1428.00");
        assertThat(result.getAkzeptierteAlternativen()).isEqualTo("[\"alt-1\"]");

        // AB-Positionen + Netto stammen aus dem Snapshot (1000 + 200), nicht aus 9999.
        ArgumentCaptor<AusgangsGeschaeftsDokumentErstellenDto> captor =
                ArgumentCaptor.forClass(AusgangsGeschaeftsDokumentErstellenDto.class);
        verify(ausgangsGeschaeftsDokumentService).erstellen(captor.capture());
        assertThat(captor.getValue().getPositionenJson()).isEqualTo("{\"merged\":true}");
        assertThat(captor.getValue().getBetragNetto()).isEqualByComparingTo("1200.00");
    }

    /**
     * Nachtragsangebote sind – wie Angebote – digital freigebbar. Vor dieser
     * Erweiterung lieferte der Service nur für ANGEBOT einen Freigabe-Block.
     */
    @Test
    void erstelleFreigabeBlock_fuerNachtragsangebot_liefertFreigabeBlock() {
        AusgangsGeschaeftsDokument nachtrag = new AusgangsGeschaeftsDokument();
        nachtrag.setId(42L);
        nachtrag.setTyp(AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT);
        nachtrag.setDokumentNummer("NA-2026/06/00001");
        when(ausgangsGeschaeftsDokumentRepository.findById(42L)).thenReturn(Optional.of(nachtrag));
        when(repository.findByQuelle(any(), any())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<String> block = service.erstelleFreigabeBlockFuerDokument(
                42L, false, "test@example.com", null);

        assertThat(block).isPresent();
        assertThat(block.get()).contains("Nachtragsangebot");
    }

    /**
     * Regression fuer den async AB-Versand: Die Auto-AB-Mail darf erst NACH dem
     * Commit der Annahme-Transaktion (via afterCommit + TaskExecutor) rausgehen
     * — sonst blockiert SMTP-Latenz die Kundenantwort und ein Lock-Timeout der
     * Mail-Archivierung koennte die bereits gespeicherte Annahme kippen.
     */
    @Test
    void akzeptiere_verschicktAutoAbErstNachCommitUeberTaskExecutor() {
        DokumentFreigabe pending = pendingFreigabe("uuid-async-ab");
        pending.setKundeEmail("max@example.de");
        when(repository.findByUuid("uuid-async-ab")).thenReturn(Optional.of(pending));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AusgangsGeschaeftsDokument angebot = new AusgangsGeschaeftsDokument();
        angebot.setId(123L);
        angebot.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
        when(ausgangsGeschaeftsDokumentRepository.findById(123L)).thenReturn(Optional.of(angebot));
        when(ausgangsGeschaeftsDokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AusgangsGeschaeftsDokument ab = new AusgangsGeschaeftsDokument();
        ab.setId(777L);
        ab.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
        when(ausgangsGeschaeftsDokumentService.erstellen(any())).thenReturn(ab);

        org.mockito.Mockito.doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                .when(taskExecutor).execute(any());

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.akzeptiere("uuid-async-ab", "1.2.3.4", "UA", "max@example.de",
                    "Max", "Mustermann", "Max Mustermann", null);

            // Vor dem Commit darf nichts rausgehen.
            verify(autoAuftragsbestaetigungVersandService, org.mockito.Mockito.never())
                    .versendeNachAnnahme(any(), any(), any());

            for (org.springframework.transaction.support.TransactionSynchronization sync
                    : org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }

        verify(autoAuftragsbestaetigungVersandService).versendeNachAnnahme(
                eq(777L), eq("max@example.de"), eq("uuid-async-ab"));
    }

    /**
     * Helper: PENDING-Freigabe mit Hash und Salt-Init, sodass {@code akzeptiere}
     * den Acceptance-Hash berechnen kann ohne NPE.
     */
    private DokumentFreigabe pendingFreigabe(String uuid) {
        DokumentFreigabe f = new DokumentFreigabe();
        f.setUuid(uuid);
        f.setQuellTyp(FreigabeQuellTyp.AUSGANGS_DOKUMENT);
        f.setQuellDokumentId(123L);
        f.setDokumentNummer("ANG-2026-0001");
        f.setDokumentArt("Angebot");
        f.setKundeName("Mustermann GmbH");
        f.setStatus(FreigabeStatus.PENDING);
        f.setErstelltAm(LocalDateTime.now().minusHours(1));
        f.setAblaufDatum(LocalDateTime.now().plusDays(14));
        f.setHashOriginal("a".repeat(64));
        // hashSalt ist @Value-injiziert → für Tests via Reflection setzen.
        ReflectionTestUtils.setField(service, "hashSalt", "TEST_SALT");
        return f;
    }
}
