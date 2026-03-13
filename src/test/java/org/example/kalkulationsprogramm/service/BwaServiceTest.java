package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.BwaPosition;
import org.example.kalkulationsprogramm.domain.BwaUpload;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.example.kalkulationsprogramm.dto.BwaUploadDto;
import org.example.kalkulationsprogramm.repository.BwaUploadRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BwaServiceTest {

    @Mock
    private BwaUploadRepository bwaUploadRepository;

    @InjectMocks
    private BwaService service;

    private BwaUpload erstelleBwaUpload(Long id, Integer jahr, Integer monat) {
        BwaUpload upload = new BwaUpload();
        upload.setId(id);
        upload.setJahr(jahr);
        upload.setMonat(monat);
        upload.setOriginalDateiname("bwa_" + monat + ".pdf");
        upload.setUploadDatum(LocalDateTime.of(2025, 1, 15, 10, 0));
        upload.setAnalysiert(true);
        upload.setFreigegeben(false);
        upload.setGesamtGemeinkosten(new BigDecimal("5000.00"));
        upload.setKostenAusRechnungen(new BigDecimal("4500.00"));
        upload.setKostenAusBwa(new BigDecimal("4800.00"));
        return upload;
    }

    @Nested
    class FindByJahr {

        @Test
        void findetBwaUploadsFuerJahr() {
            BwaUpload u1 = erstelleBwaUpload(1L, 2025, 3);
            BwaUpload u2 = erstelleBwaUpload(2L, 2025, 2);

            when(bwaUploadRepository.findByJahrOrderByMonatDesc(2025)).thenReturn(List.of(u1, u2));

            List<BwaUploadDto> result = service.findByJahr(2025);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(1L);
            assertThat(result.get(0).getJahr()).isEqualTo(2025);
            assertThat(result.get(0).getPdfUrl()).isEqualTo("/api/bwa/1/pdf");
        }

        @Test
        void gibtLeereListeZurueckBeiKeinemErgebnis() {
            when(bwaUploadRepository.findByJahrOrderByMonatDesc(2020)).thenReturn(List.of());

            List<BwaUploadDto> result = service.findByJahr(2020);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class FindById {

        @Test
        void findetBwaUploadPerId() {
            BwaUpload upload = erstelleBwaUpload(5L, 2025, 6);
            when(bwaUploadRepository.findById(5L)).thenReturn(Optional.of(upload));

            BwaUploadDto result = service.findById(5L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(5L);
            assertThat(result.getMonat()).isEqualTo(6);
        }

        @Test
        void gibtNullZurueckBeiUnbekannterID() {
            when(bwaUploadRepository.findById(99L)).thenReturn(Optional.empty());

            BwaUploadDto result = service.findById(99L);

            assertThat(result).isNull();
        }

        @Test
        void mapptFreigegebenVonNameKorrekt() {
            BwaUpload upload = erstelleBwaUpload(5L, 2025, 6);
            Mitarbeiter mitarbeiter = new Mitarbeiter();
            mitarbeiter.setVorname("Max");
            mitarbeiter.setNachname("Mustermann");
            upload.setFreigegebenVon(mitarbeiter);

            when(bwaUploadRepository.findById(5L)).thenReturn(Optional.of(upload));

            BwaUploadDto result = service.findById(5L);

            assertThat(result.getFreigegebenVonName()).isEqualTo("Max Mustermann");
        }

        @Test
        void mapptSteuerberaterNameKorrekt() {
            BwaUpload upload = erstelleBwaUpload(5L, 2025, 6);
            SteuerberaterKontakt steuerberater = new SteuerberaterKontakt();
            steuerberater.setName("Müller & Partner");
            upload.setSteuerberater(steuerberater);

            when(bwaUploadRepository.findById(5L)).thenReturn(Optional.of(upload));

            BwaUploadDto result = service.findById(5L);

            assertThat(result.getSteuerberaterName()).isEqualTo("Müller & Partner");
        }

        @Test
        void mapptPositionenMitKostenstelle() {
            BwaUpload upload = erstelleBwaUpload(5L, 2025, 6);

            Kostenstelle kostenstelle = new Kostenstelle();
            kostenstelle.setId(10L);
            kostenstelle.setBezeichnung("Verwaltung");

            BwaPosition position = new BwaPosition();
            position.setId(100L);
            position.setBezeichnung("Miete");
            position.setBetragMonat(new BigDecimal("1500.00"));
            position.setKontonummer("4210");
            position.setKategorie("Raumkosten");
            position.setInRechnungenGefunden(true);
            position.setManuellKorrigiert(false);
            position.setKostenstelle(kostenstelle);

            upload.setPositionen(List.of(position));

            when(bwaUploadRepository.findById(5L)).thenReturn(Optional.of(upload));

            BwaUploadDto result = service.findById(5L);

            assertThat(result.getPositionen()).hasSize(1);
            assertThat(result.getPositionen().get(0).getBezeichnung()).isEqualTo("Miete");
            assertThat(result.getPositionen().get(0).getKostenstelleId()).isEqualTo(10L);
            assertThat(result.getPositionen().get(0).getKostenstelleBezeichnung()).isEqualTo("Verwaltung");
        }
    }

    @Nested
    class Delete {

        @Test
        void loeschtBwaUpload() {
            service.delete(5L);

            verify(bwaUploadRepository).deleteById(5L);
        }
    }

    @Nested
    class FindAvailableYears {

        @Test
        void gibtVerfuegbareJahreSortiertZurueck() {
            when(bwaUploadRepository.findDistinctJahre()).thenReturn(List.of(2025, 2024, 2023));

            List<Integer> result = service.findAvailableYears();

            assertThat(result).containsExactly(2025, 2024, 2023);
        }

        @Test
        void gibtLeereListeZurueckBeiKeinenUploads() {
            when(bwaUploadRepository.findDistinctJahre()).thenReturn(List.of());

            List<Integer> result = service.findAvailableYears();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class FindStoredFilename {

        @Test
        void gibtDateinameZurueck() {
            BwaUpload upload = new BwaUpload();
            upload.setGespeicherterDateiname("stored_bwa.pdf");

            when(bwaUploadRepository.findById(1L)).thenReturn(Optional.of(upload));

            Optional<String> result = service.findStoredFilename(1L);

            assertThat(result).isPresent().contains("stored_bwa.pdf");
        }

        @Test
        void gibtEmptyZurueckBeiUnbekannterID() {
            when(bwaUploadRepository.findById(99L)).thenReturn(Optional.empty());

            Optional<String> result = service.findStoredFilename(99L);

            assertThat(result).isEmpty();
        }
    }
}
