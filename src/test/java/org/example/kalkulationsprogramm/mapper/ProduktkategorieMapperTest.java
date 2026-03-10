package org.example.kalkulationsprogramm.mapper;

import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieResponseDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProduktkategorieMapperTest {

    private final ProduktkategorieMapper mapper = new ProduktkategorieMapper();

    @Test
    void mapptAlleFelder() {
        Produktkategorie kategorie = new Produktkategorie();
        kategorie.setId(1L);
        kategorie.setBezeichnung("Stahl");
        kategorie.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);
        kategorie.setBeschreibung("Stahlprodukte");
        kategorie.setBildUrl("stahl.png");

        ProduktkategorieResponseDto dto = mapper.toProduktkategorieResponseDto(kategorie);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getBezeichnung()).isEqualTo("Stahl");
        assertThat(dto.getVerrechnungseinheit()).isEqualTo(Verrechnungseinheit.KILOGRAMM);
        assertThat(dto.getBeschreibung()).isEqualTo("Stahlprodukte");
        assertThat(dto.getBildUrl()).isEqualTo("/api/images/stahl.png");
        assertThat(dto.isLeaf()).isTrue();
    }

    @Test
    void gibtNullZurueckBeiNullKategorie() {
        assertThat(mapper.toProduktkategorieResponseDto(null)).isNull();
    }

    @Test
    void setzLeafAufFalseBeiVorhandenenUnterkategorien() {
        Produktkategorie kind = new Produktkategorie();
        kind.setId(2L);
        kind.setBezeichnung("Unterkategorie");

        Produktkategorie kategorie = new Produktkategorie();
        kategorie.setId(1L);
        kategorie.setBezeichnung("Eltern");
        kategorie.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        kategorie.setUnterkategorien(List.of(kind));

        ProduktkategorieResponseDto dto = mapper.toProduktkategorieResponseDto(kategorie);

        assertThat(dto.isLeaf()).isFalse();
    }

    @Test
    void bauePfadMitHierarchie() {
        Produktkategorie opa = new Produktkategorie();
        opa.setId(1L);
        opa.setBezeichnung("Werkstoffe");
        opa.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);

        Produktkategorie vater = new Produktkategorie();
        vater.setId(2L);
        vater.setBezeichnung("Metalle");
        vater.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);
        vater.setUebergeordneteKategorie(opa);

        Produktkategorie kind = new Produktkategorie();
        kind.setId(3L);
        kind.setBezeichnung("Stahl");
        kind.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);
        kind.setUebergeordneteKategorie(vater);

        ProduktkategorieResponseDto dto = mapper.toProduktkategorieResponseDto(kind);

        assertThat(dto.getPfad()).isEqualTo("Werkstoffe > Metalle > Stahl");
    }

    @Test
    void bauePfadOhneElternkategorie() {
        Produktkategorie kategorie = new Produktkategorie();
        kategorie.setId(1L);
        kategorie.setBezeichnung("Einzelne Kategorie");
        kategorie.setVerrechnungseinheit(Verrechnungseinheit.STUECK);

        ProduktkategorieResponseDto dto = mapper.toProduktkategorieResponseDto(kategorie);

        assertThat(dto.getPfad()).isEqualTo("Einzelne Kategorie");
    }

    @Test
    void laeBildUrlMitSchraegstrichUnveraendert() {
        Produktkategorie kategorie = new Produktkategorie();
        kategorie.setId(1L);
        kategorie.setBezeichnung("Test");
        kategorie.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        kategorie.setBildUrl("/api/images/vorhandenes-bild.png");

        ProduktkategorieResponseDto dto = mapper.toProduktkategorieResponseDto(kategorie);

        assertThat(dto.getBildUrl()).isEqualTo("/api/images/vorhandenes-bild.png");
    }

    @Test
    void behandeltNullBildUrl() {
        Produktkategorie kategorie = new Produktkategorie();
        kategorie.setId(1L);
        kategorie.setBezeichnung("Test");
        kategorie.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        kategorie.setBildUrl(null);

        ProduktkategorieResponseDto dto = mapper.toProduktkategorieResponseDto(kategorie);

        assertThat(dto.getBildUrl()).isNull();
    }

    @Test
    void behandeltLeereBildUrl() {
        Produktkategorie kategorie = new Produktkategorie();
        kategorie.setId(1L);
        kategorie.setBezeichnung("Test");
        kategorie.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        kategorie.setBildUrl("   ");

        ProduktkategorieResponseDto dto = mapper.toProduktkategorieResponseDto(kategorie);

        assertThat(dto.getBildUrl()).isEqualTo("   ");
    }
}
