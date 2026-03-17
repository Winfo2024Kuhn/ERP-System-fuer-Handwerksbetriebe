package org.example.kalkulationsprogramm.mapper;

import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungCreateDto;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieResponseDto;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeistungMapperTest {

    @Mock
    private ProduktkategorieRepository produktkategorieRepository;

    @Mock
    private ProduktkategorieMapper produktkategorieMapper;

    @InjectMocks
    private LeistungMapper mapper;

    @Nested
    class ToDto {

        @Test
        void mapptAlleFelder() {
            Produktkategorie kategorie = new Produktkategorie();
            kategorie.setId(5L);

            ProduktkategorieResponseDto kategorieDto = new ProduktkategorieResponseDto();
            kategorieDto.setPfad("Kategorie / Unterkategorie");
            when(produktkategorieMapper.toProduktkategorieResponseDto(kategorie)).thenReturn(kategorieDto);

            Leistung leistung = new Leistung();
            leistung.setId(1L);
            leistung.setBezeichnung("Rohrleitungsbau");
            leistung.setBeschreibung("Verlegung von Rohrleitungen");
            leistung.setPreis(new BigDecimal("85.50"));
            leistung.setEinheit(Verrechnungseinheit.LAUFENDE_METER);
            leistung.setKategorie(kategorie);

            LeistungDto dto = mapper.toDto(leistung);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Rohrleitungsbau");
            assertThat(dto.getDescription()).isEqualTo("Verlegung von Rohrleitungen");
            assertThat(dto.getPrice()).isEqualByComparingTo(new BigDecimal("85.50"));
            assertThat(dto.getUnit()).isEqualTo(Verrechnungseinheit.LAUFENDE_METER);
            assertThat(dto.getFolderId()).isEqualTo(5L);
        }

        @Test
        void gibtNullZurueckBeiNullLeistung() {
            assertThat(mapper.toDto(null)).isNull();
        }

        @Test
        void mapptLeistungOhneKategorie() {
            Leistung leistung = new Leistung();
            leistung.setId(2L);
            leistung.setBezeichnung("Test");
            leistung.setKategorie(null);

            LeistungDto dto = mapper.toDto(leistung);

            assertThat(dto.getFolderId()).isNull();
        }
    }

    @Nested
    class ToEntity {

        @Test
        void mapptCreateDtoZuEntity() {
            Produktkategorie kategorie = new Produktkategorie();
            kategorie.setId(3L);

            when(produktkategorieRepository.findById(3L)).thenReturn(Optional.of(kategorie));

            LeistungCreateDto dto = new LeistungCreateDto();
            dto.setName("Fliesenlegen");
            dto.setDescription("Fliesen verlegen inkl. Fugenmaterial");
            dto.setPrice(new BigDecimal("45.00"));
            dto.setUnit(Verrechnungseinheit.QUADRATMETER);
            dto.setFolderId(3L);

            Leistung entity = mapper.toEntity(dto);

            assertThat(entity.getBezeichnung()).isEqualTo("Fliesenlegen");
            assertThat(entity.getBeschreibung()).isEqualTo("Fliesen verlegen inkl. Fugenmaterial");
            assertThat(entity.getPreis()).isEqualByComparingTo(new BigDecimal("45.00"));
            assertThat(entity.getEinheit()).isEqualTo(Verrechnungseinheit.QUADRATMETER);
            assertThat(entity.getKategorie()).isEqualTo(kategorie);
        }

        @Test
        void gibtNullZurueckBeiNullDto() {
            assertThat(mapper.toEntity(null)).isNull();
        }

        @Test
        void setztKategorieAufNullBeiNullFolderId() {
            LeistungCreateDto dto = new LeistungCreateDto();
            dto.setName("Ohne Ordner");
            dto.setFolderId(null);

            Leistung entity = mapper.toEntity(dto);

            assertThat(entity.getKategorie()).isNull();
        }

        @Test
        void setztKategorieAufNullBeiUnbekannterFolderId() {
            when(produktkategorieRepository.findById(999L)).thenReturn(Optional.empty());

            LeistungCreateDto dto = new LeistungCreateDto();
            dto.setName("Unbekannte Kategorie");
            dto.setFolderId(999L);

            Leistung entity = mapper.toEntity(dto);

            assertThat(entity.getKategorie()).isNull();
        }
    }

    @Nested
    class UpdateEntity {

        @Test
        void aktualisiertExistierendeEntity() {
            Produktkategorie kategorie = new Produktkategorie();
            kategorie.setId(7L);

            when(produktkategorieRepository.findById(7L)).thenReturn(Optional.of(kategorie));

            Leistung leistung = new Leistung();
            leistung.setId(1L);
            leistung.setBezeichnung("Alt");

            LeistungCreateDto dto = new LeistungCreateDto();
            dto.setName("Neu");
            dto.setDescription("Neue Beschreibung");
            dto.setPrice(new BigDecimal("100.00"));
            dto.setUnit(Verrechnungseinheit.STUECK);
            dto.setFolderId(7L);

            mapper.updateEntity(leistung, dto);

            assertThat(leistung.getBezeichnung()).isEqualTo("Neu");
            assertThat(leistung.getBeschreibung()).isEqualTo("Neue Beschreibung");
            assertThat(leistung.getPreis()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(leistung.getEinheit()).isEqualTo(Verrechnungseinheit.STUECK);
            assertThat(leistung.getKategorie()).isEqualTo(kategorie);
        }

        @Test
        void entferntKategorieBeiNullFolderId() {
            Produktkategorie alteKategorie = new Produktkategorie();
            alteKategorie.setId(7L);

            Leistung leistung = new Leistung();
            leistung.setId(1L);
            leistung.setKategorie(alteKategorie);

            LeistungCreateDto dto = new LeistungCreateDto();
            dto.setName("Ohne Ordner");
            dto.setFolderId(null);

            mapper.updateEntity(leistung, dto);

            assertThat(leistung.getKategorie()).isNull();
        }
    }
}
