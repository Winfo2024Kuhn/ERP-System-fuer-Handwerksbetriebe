package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentCounterRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AusgangsGeschaeftsDokumentServiceBezahltTest {

    @Mock
    private AusgangsGeschaeftsDokumentRepository dokumentRepository;

    @Mock
    private AusgangsGeschaeftsDokumentCounterRepository counterRepository;

    @Mock
    private ProjektRepository projektRepository;

    @Mock
    private AnfrageRepository anfrageRepository;

    @Mock
    private KundeRepository kundeRepository;

    @Mock
    private FrontendUserProfileRepository frontendUserProfileRepository;

    @Mock
    private LeistungRepository leistungRepository;

    @Mock
    private ProduktkategorieRepository produktkategorieRepository;

    @Mock
    private ProjektDokumentRepository projektDokumentRepository;

    @Mock
    private ZeitbuchungRepository zeitbuchungRepository;

    @Mock
    private LeistungWpsAutoAssignService leistungWpsAutoAssignService;

    private AusgangsGeschaeftsDokumentService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new AusgangsGeschaeftsDokumentService(
                tempDir.toString(),
                dokumentRepository,
                counterRepository,
                projektRepository,
                anfrageRepository,
                kundeRepository,
                frontendUserProfileRepository,
                leistungRepository,
                produktkategorieRepository,
                projektDokumentRepository,
                zeitbuchungRepository,
                leistungWpsAutoAssignService
        );
    }

    @Nested
    class AktualisiereProjektPreisAusDokumenten {

        @Test
        void setztBezahltUndAbgeschlossenWennRechnungssummeAusreichtUndKeineOffenenPosten() {
            Projekt projekt = new Projekt();
            projekt.setId(1L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);
            projekt.setAbgeschlossen(false);

            AusgangsGeschaeftsDokument anfrage = new AusgangsGeschaeftsDokument();
            anfrage.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            anfrage.setBetragBrutto(new BigDecimal("1000.00"));
            anfrage.setStorniert(false);

            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setBetragBrutto(new BigDecimal("1000.00"));
            rechnung.setStorniert(false);

            when(projektRepository.findById(1L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L))
                    .thenReturn(List.of(anfrage, rechnung));
            when(projektDokumentRepository.existsOffenePostenByProjektId(1L)).thenReturn(false);

            service.aktualisiereProjektPreisAusDokumenten(1L);

            assertThat(projekt.isBezahlt()).isTrue();
            assertThat(projekt.isAbgeschlossen()).isTrue();
            verify(projektRepository).save(projekt);
        }

        @Test
        void setztNichtBezahltWennRechnungssummeAusreichtAberNochOffenePostenExistieren() {
            Projekt projekt = new Projekt();
            projekt.setId(2L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);
            projekt.setAbgeschlossen(false);

            AusgangsGeschaeftsDokument anfrage = new AusgangsGeschaeftsDokument();
            anfrage.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            anfrage.setBetragBrutto(new BigDecimal("5000.00"));
            anfrage.setStorniert(false);

            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setBetragBrutto(new BigDecimal("5000.00"));
            rechnung.setStorniert(false);

            when(projektRepository.findById(2L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(2L))
                    .thenReturn(List.of(anfrage, rechnung));
            // Noch offene Posten vorhanden!
            when(projektDokumentRepository.existsOffenePostenByProjektId(2L)).thenReturn(true);

            service.aktualisiereProjektPreisAusDokumenten(2L);

            assertThat(projekt.isBezahlt()).isFalse();
            assertThat(projekt.isAbgeschlossen()).isFalse();
            verify(projektRepository).save(projekt);
        }

        @Test
        void setztNichtBezahltWennRechnungssummeNichtAusreicht() {
            Projekt projekt = new Projekt();
            projekt.setId(3L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);
            projekt.setAbgeschlossen(false);

            AusgangsGeschaeftsDokument anfrage = new AusgangsGeschaeftsDokument();
            anfrage.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            anfrage.setBetragBrutto(new BigDecimal("10000.00"));
            anfrage.setStorniert(false);

            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setBetragBrutto(new BigDecimal("5000.00"));
            rechnung.setStorniert(false);

            when(projektRepository.findById(3L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(3L))
                    .thenReturn(List.of(anfrage, rechnung));

            service.aktualisiereProjektPreisAusDokumenten(3L);

            assertThat(projekt.isBezahlt()).isFalse();
            assertThat(projekt.isAbgeschlossen()).isFalse();
        }

        @Test
        void oeffnetProjektWiederWennOffenePostenHinzukommen() {
            Projekt projekt = new Projekt();
            projekt.setId(4L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(true);
            projekt.setAbgeschlossen(true);

            AusgangsGeschaeftsDokument anfrage = new AusgangsGeschaeftsDokument();
            anfrage.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            anfrage.setBetragBrutto(new BigDecimal("2000.00"));
            anfrage.setStorniert(false);

            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setBetragBrutto(new BigDecimal("2000.00"));
            rechnung.setStorniert(false);

            when(projektRepository.findById(4L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(4L))
                    .thenReturn(List.of(anfrage, rechnung));
            // Offene Posten wurden hinzugefügt
            when(projektDokumentRepository.existsOffenePostenByProjektId(4L)).thenReturn(true);

            service.aktualisiereProjektPreisAusDokumenten(4L);

            assertThat(projekt.isBezahlt()).isFalse();
            assertThat(projekt.isAbgeschlossen()).isFalse();
        }

        @Test
        void auftragsbestaetigungHatPrioritaetVorAnfrage() {
            Projekt projekt = new Projekt();
            projekt.setId(5L);
            projekt.setBruttoPreis(BigDecimal.ZERO);
            projekt.setBezahlt(false);

            AusgangsGeschaeftsDokument anfrage = new AusgangsGeschaeftsDokument();
            anfrage.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            anfrage.setBetragBrutto(new BigDecimal("5000.00"));
            anfrage.setStorniert(false);

            AusgangsGeschaeftsDokument ab = new AusgangsGeschaeftsDokument();
            ab.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            ab.setBetragBrutto(new BigDecimal("8000.00"));
            ab.setStorniert(false);

            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setBetragBrutto(new BigDecimal("8000.00"));
            rechnung.setStorniert(false);

            when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findByProjektIdOrderByDatumDesc(5L))
                    .thenReturn(List.of(anfrage, ab, rechnung));
            when(projektDokumentRepository.existsOffenePostenByProjektId(5L)).thenReturn(false);

            service.aktualisiereProjektPreisAusDokumenten(5L);

            assertThat(projekt.isBezahlt()).isTrue();
            assertThat(projekt.isAbgeschlossen()).isTrue();
        }

        @Test
        void ignoriertNullProjektId() {
            service.aktualisiereProjektPreisAusDokumenten(null);

            verifyNoInteractions(projektRepository);
        }

        @Test
        void ignoriertNichtGefundenesProjekt() {
            when(projektRepository.findById(99L)).thenReturn(Optional.empty());

            service.aktualisiereProjektPreisAusDokumenten(99L);

            verify(projektRepository, never()).save(any());
        }
    }
}
