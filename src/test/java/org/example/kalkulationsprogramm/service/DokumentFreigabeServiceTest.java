package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.FreigabeQuellTyp;
import org.example.kalkulationsprogramm.domain.FreigabeStatus;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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
    private WebPushService webPushService;
    @Mock
    private DateiSpeicherService dateiSpeicherService;
    @Mock
    private AutoAuftragsbestaetigungVersandService autoAuftragsbestaetigungVersandService;

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
}
