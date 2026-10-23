package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;
import org.example.kalkulationsprogramm.domain.AusgelesenePosition;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.repository.LieferantDokumentPositionRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LieferantDokumentPositionNachtragTest {

    private static final String ANTWORT_MIT_POSITIONEN = """
            {"dokumentTyp":"ANGEBOT","artikelPositionen":[
              {"positionNr":10,"bezeichnung":"Winkelschleifer 125 mm","menge":1,"einzelpreis":140.40,"gesamtpreisNetto":140.40},
              {"positionNr":20,"bezeichnung":"Trennscheibe 125 mm","menge":10,"einzelpreis":1.50,"gesamtpreisNetto":15.00}
            ]}""";

    @Mock private LieferantDokumentPositionRepository positionRepository;
    @Mock private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    @Mock private LieferantDokumentPositionService positionService;
    @Mock private PlatformTransactionManager transactionManager;

    private LieferantDokumentPositionNachtrag nachtrag;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        nachtrag = new LieferantDokumentPositionNachtrag(positionRepository, geschaeftsdokumentRepository,
                positionService, JsonMapper.builderWithJackson2Defaults().build(), new TransactionTemplate(transactionManager));
    }

    private LieferantGeschaeftsdokument dokument(long id, LieferantDokumentTyp typ, String antwort) {
        LieferantDokument d = new LieferantDokument();
        d.setId(id);
        d.setTyp(typ);
        LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
        gd.setId(id);
        gd.setDokument(d);
        gd.setAiRawJson(antwort);
        when(geschaeftsdokumentRepository.findById(id)).thenReturn(Optional.of(gd));
        return gd;
    }

    @Test
    @SuppressWarnings("unchecked")
    void uebernimmtPositionenAusDerGespeichertenKiAntwort() {
        LieferantGeschaeftsdokument gd = dokument(1L, LieferantDokumentTyp.ANGEBOT, ANTWORT_MIT_POSITIONEN);
        when(positionRepository.findeIdsOhnePositionenMitKiAntwort(LieferantDokumentPositionService.typenMitPositionen()))
                .thenReturn(List.of(1L));
        when(positionService.ersetzePositionen(eq(gd), eq(LieferantDokumentTyp.ANGEBOT), anyList())).thenReturn(2);

        assertThat(nachtrag.nachtragen()).isEqualTo(1);

        ArgumentCaptor<List<AusgelesenePosition>> captor = ArgumentCaptor.forClass(List.class);
        verify(positionService).ersetzePositionen(eq(gd), eq(LieferantDokumentTyp.ANGEBOT), captor.capture());
        assertThat(captor.getValue()).extracting(AusgelesenePosition::bezeichnung)
                .containsExactly("Winkelschleifer 125 mm", "Trennscheibe 125 mm");
    }

    @Test
    void ueberspringtLeereKaputteUndFehlendeAntworten() {
        dokument(1L, LieferantDokumentTyp.RECHNUNG, "{\"artikelPositionen\":[]}");
        dokument(2L, LieferantDokumentTyp.RECHNUNG, "{kein json");
        dokument(3L, LieferantDokumentTyp.RECHNUNG, "{\"dokumentTyp\":\"RECHNUNG\"}");
        dokument(4L, LieferantDokumentTyp.RECHNUNG, null);
        when(geschaeftsdokumentRepository.findById(5L)).thenReturn(Optional.empty());
        when(positionRepository.findeIdsOhnePositionenMitKiAntwort(any())).thenReturn(List.of(1L, 2L, 3L, 4L, 5L));

        assertThat(nachtrag.nachtragen()).isZero();

        verify(positionService, never()).ersetzePositionen(any(), any(), any());
    }

    @Test
    void inzwischenGespeichertePositionenBleibenUnberuehrt() {
        dokument(1L, LieferantDokumentTyp.ANGEBOT, ANTWORT_MIT_POSITIONEN);
        when(positionRepository.findeIdsOhnePositionenMitKiAntwort(any())).thenReturn(List.of(1L));
        when(positionRepository.countByGeschaeftsdokumentId(1L)).thenReturn(3L);

        assertThat(nachtrag.nachtragen()).isZero();

        verify(positionService, never()).ersetzePositionen(any(), any(), any());
    }

    @Test
    void einFehlerHaeltDieUebrigenDokumenteNichtAuf() {
        LieferantGeschaeftsdokument kaputt = dokument(1L, LieferantDokumentTyp.ANGEBOT, ANTWORT_MIT_POSITIONEN);
        LieferantGeschaeftsdokument gut = dokument(2L, LieferantDokumentTyp.ANGEBOT, ANTWORT_MIT_POSITIONEN);
        when(positionRepository.findeIdsOhnePositionenMitKiAntwort(any())).thenReturn(List.of(1L, 2L));
        when(positionService.ersetzePositionen(eq(kaputt), any(), anyList()))
                .thenThrow(new IllegalStateException("Constraint"));
        when(positionService.ersetzePositionen(eq(gut), any(), anyList())).thenReturn(2);

        assertThat(nachtrag.nachtragen()).isEqualTo(1);
    }
}
