package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class BelegZuordnungServiceTest {

    @Mock private GeminiDokumentAnalyseService analyseService;
    @Mock private LieferantDokumentRepository dokumentRepository;
    @Mock private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    @Mock private BelegRepository belegRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private BelegZuordnungService service;
    private Lieferanten lieferant;

    @BeforeEach
    void setUp() {
        // Ohne laufende Transaktion führt der Service "nach dem Commit" sofort aus.
        service = new BelegZuordnungService(analyseService, dokumentRepository, geschaeftsdokumentRepository,
                belegRepository, objectMapper, mock(PlatformTransactionManager.class), Runnable::run);
        lieferant = new Lieferanten();
        lieferant.setId(4L);
        lieferant.setLieferantenname("Muster GmbH");
    }

    private Beleg beleg(LieferantDokumentTyp typ, Lieferanten belegLieferant) {
        Beleg beleg = new Beleg();
        beleg.setId(7L);
        beleg.setDokumentTyp(typ);
        beleg.setLieferant(belegLieferant);
        beleg.setBelegNummer("LS-31000555");
        beleg.setBelegDatum(LocalDate.of(2026, 6, 2));
        beleg.setGespeicherterDateiname("muster-scan.jpg");
        beleg.setOriginalDateiname("scan.jpg");
        beleg.setKiZahlungsart("KARTE");
        return beleg;
    }

    private LieferantDokument gespeichert;

    private void speichernMitId() {
        when(dokumentRepository.save(any(LieferantDokument.class))).thenAnswer(inv -> {
            gespeichert = inv.getArgument(0);
            gespeichert.setId(70L);
            return gespeichert;
        });
        org.mockito.Mockito.lenient().when(dokumentRepository.findById(70L))
                .thenAnswer(inv -> Optional.ofNullable(gespeichert));
    }

    @Test
    void ordneEinVerknuepftNurMitLieferantUndGeschaeftsdaten() {
        LieferantDokument ohneDaten = new LieferantDokument();
        ohneDaten.setLieferant(lieferant);
        LieferantDokument ohneLieferant = new LieferantDokument();
        ohneLieferant.setGeschaeftsdaten(new LieferantGeschaeftsdokument());
        LieferantDokument vollstaendig = new LieferantDokument();
        vollstaendig.setId(3L);
        vollstaendig.setLieferant(lieferant);
        vollstaendig.setGeschaeftsdaten(new LieferantGeschaeftsdokument());
        when(dokumentRepository.findById(3L)).thenReturn(Optional.of(vollstaendig));

        service.ordneEin(null);
        service.ordneEin(ohneDaten);
        service.ordneEin(ohneLieferant);
        service.ordneEin(vollstaendig);

        verify(analyseService).performRelink(vollstaendig);
        verify(analyseService, never()).performRelink(ohneDaten);
        verify(analyseService, never()).performRelink(ohneLieferant);
    }

    @Test
    void fehlerBeimEinordnenWirdNurProtokolliert() {
        LieferantDokument dokument = new LieferantDokument();
        dokument.setId(3L);
        dokument.setLieferant(lieferant);
        dokument.setGeschaeftsdaten(new LieferantGeschaeftsdokument());
        when(dokumentRepository.findById(3L)).thenReturn(Optional.of(dokument));
        doThrow(new IllegalStateException("kaputt")).when(analyseService).performRelink(dokument);

        service.ordneEin(dokument);

        verify(analyseService).performRelink(dokument);
    }

    @Test
    void mitLaufenderTransaktionErstNachDemCommit() {
        LieferantDokument dokument = new LieferantDokument();
        dokument.setId(3L);
        dokument.setLieferant(lieferant);
        dokument.setGeschaeftsdaten(new LieferantGeschaeftsdokument());
        when(dokumentRepository.findById(3L)).thenReturn(Optional.of(dokument));
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.ordneEin(dokument);
            // Während der Transaktion des Aufrufers passiert nichts – sie kann nicht rollback-only werden
            verify(analyseService, never()).performRelink(any());

            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(analyseService).performRelink(dokument);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void scannerLieferscheinMitLieferantWirdLieferantenDokumentUndEingeordnet() {
        Beleg beleg = beleg(LieferantDokumentTyp.LIEFERSCHEIN, lieferant);
        when(dokumentRepository.findByBelegId(7L)).thenReturn(Optional.empty());
        speichernMitId();
        var ergebnis = LieferantDokumentDto.AnalyzeResponse.builder()
                .dokumentTyp(LieferantDokumentTyp.LIEFERSCHEIN)
                .bestellnummer("47711111")
                .referenzNummer("2299000001")
                .bereitsGezahlt(true)
                .aiRawJson("{\"weitereReferenzen\":[\"AB-4711\"]}")
                .build();

        var neu = service.uebernehmeScannerBeleg(beleg, ergebnis);

        assertThat(neu).isPresent();
        LieferantDokument ld = neu.get();
        assertThat(ld.getTyp()).isEqualTo(LieferantDokumentTyp.LIEFERSCHEIN);
        assertThat(ld.getBeleg()).isSameAs(beleg);
        assertThat(ld.getGespeicherterDateiname()).isEqualTo("belege/muster-scan.jpg");
        LieferantGeschaeftsdokument daten = ld.getGeschaeftsdaten();
        assertThat(daten.getBestellnummer()).isEqualTo("47711111");
        assertThat(daten.getReferenzNummer()).isEqualTo("2299000001");
        // Rohe KI-Antwort: die weiteren Referenzen braucht der Abgleich
        assertThat(daten.getAiRawJson()).contains("AB-4711");
        // Ein Lieferschein ist nie "bezahlt"
        assertThat(daten.getBereitsGezahlt()).isNotEqualTo(Boolean.TRUE);
        verify(geschaeftsdokumentRepository).save(daten);
        verify(analyseService).performRelink(ld);
    }

    @Test
    void scannerRechnungOhneRohantwortSpeichertDasDtoUndBezahltStatus() {
        Beleg beleg = beleg(LieferantDokumentTyp.RECHNUNG, lieferant);
        when(dokumentRepository.findByBelegId(7L)).thenReturn(Optional.empty());
        when(geschaeftsdokumentRepository.existsByLieferantIdAndDokumentNummer(anyLong(), anyString())).thenReturn(false);
        speichernMitId();
        var ergebnis = LieferantDokumentDto.AnalyzeResponse.builder()
                .dokumentTyp(LieferantDokumentTyp.RECHNUNG)
                .betragBrutto(new BigDecimal("119.00"))
                .bereitsGezahlt(true)
                .build();

        LieferantDokument ld = service.uebernehmeScannerBeleg(beleg, ergebnis).orElseThrow();

        assertThat(ld.getGeschaeftsdaten().getBereitsGezahlt()).isTrue();
        assertThat(ld.getGeschaeftsdaten().getAiRawJson()).contains("RECHNUNG");
    }

    @Test
    void ohneLieferantOderMitFalscherBelegartPassiertNichts() {
        assertThat(service.uebernehmeScannerBeleg(beleg(LieferantDokumentTyp.RECHNUNG, null), null)).isEmpty();
        assertThat(service.uebernehmeScannerBeleg(beleg(LieferantDokumentTyp.SONSTIG, lieferant), null)).isEmpty();
        assertThat(service.uebernehmeScannerBeleg(beleg(null, lieferant), null)).isEmpty();
        assertThat(service.uebernehmeScannerBeleg(null, null)).isEmpty();

        verify(dokumentRepository, never()).save(any());
        verify(analyseService, never()).performRelink(any());
    }

    @Test
    void idempotent_vorhandenesDokumentOderBekannteNummer() {
        Beleg beleg = beleg(LieferantDokumentTyp.RECHNUNG, lieferant);
        when(dokumentRepository.findByBelegId(7L)).thenReturn(Optional.of(new LieferantDokument()));
        assertThat(service.uebernehmeScannerBeleg(beleg, null)).isEmpty();

        when(dokumentRepository.findByBelegId(7L)).thenReturn(Optional.empty());
        when(geschaeftsdokumentRepository.existsByLieferantIdAndDokumentNummer(4L, "LS-31000555")).thenReturn(true);
        assertThat(service.uebernehmeScannerBeleg(beleg, null)).isEmpty();

        verify(dokumentRepository, never()).save(any());
    }

    @Test
    void lieferantSpaeterGesetzt_liestGespeicherteKiAuslesung() throws Exception {
        Beleg beleg = beleg(LieferantDokumentTyp.RECHNUNG, lieferant);
        beleg.setKiExtraktionJson(objectMapper.writeValueAsString(LieferantDokumentDto.AnalyzeResponse.builder()
                .dokumentTyp(LieferantDokumentTyp.RECHNUNG)
                .referenzNummer("LS-31000555")
                .build()).replace("}", ",\"unbekanntesFeld\":1}"));
        when(belegRepository.findById(7L)).thenReturn(Optional.of(beleg));
        when(dokumentRepository.findByBelegId(7L)).thenReturn(Optional.empty());
        speichernMitId();

        service.uebernehmeScannerBelegNachCommit(7L);

        ArgumentCaptor<LieferantGeschaeftsdokument> daten = ArgumentCaptor.forClass(LieferantGeschaeftsdokument.class);
        verify(geschaeftsdokumentRepository).save(daten.capture());
        assertThat(daten.getValue().getReferenzNummer()).isEqualTo("LS-31000555");
        verify(analyseService).performRelink(any());
    }

    @Test
    void lieferantSpaeterGesetzt_kaputteKiAuslesungUndFehlerBleibenFolgenlos() {
        Beleg beleg = beleg(LieferantDokumentTyp.RECHNUNG, lieferant);
        beleg.setKiExtraktionJson("{kaputt");
        when(belegRepository.findById(7L)).thenReturn(Optional.of(beleg));
        when(belegRepository.findById(8L)).thenThrow(new IllegalStateException("DB weg"));
        when(dokumentRepository.findByBelegId(7L)).thenReturn(Optional.empty());
        speichernMitId();

        service.uebernehmeScannerBelegNachCommit(7L);
        service.uebernehmeScannerBelegNachCommit(8L);
        service.uebernehmeScannerBelegNachCommit(null);

        verify(dokumentRepository).save(any(LieferantDokument.class));
    }

    @Test
    void analyseNachDemHochladen() {
        service.analysiereUndOrdneEinNachCommit(55L);
        service.analysiereUndOrdneEinNachCommit(null);

        ArgumentCaptor<LieferantDokument> verweis = ArgumentCaptor.forClass(LieferantDokument.class);
        verify(analyseService).analysiereDokument(verweis.capture());
        assertThat(verweis.getValue().getId()).isEqualTo(55L);
    }
}
