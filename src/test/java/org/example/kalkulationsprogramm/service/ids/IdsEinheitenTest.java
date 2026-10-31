package org.example.kalkulationsprogramm.service.ids;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.junit.jupiter.api.Test;

class IdsEinheitenTest {

    @Test
    void kurzform() {
        assertThat(IdsEinheiten.kurzform("PCE")).isEqualTo("Stk");
        assertThat(IdsEinheiten.kurzform(" mtr ")).isEqualTo("m");
        assertThat(IdsEinheiten.kurzform("MTK")).isEqualTo("m²");
        assertThat(IdsEinheiten.kurzform("KGM")).isEqualTo("kg");
        assertThat(IdsEinheiten.kurzform("XYZ")).isEqualTo("XYZ");
        assertThat(IdsEinheiten.kurzform(null)).isEqualTo("Stk");
        assertThat(IdsEinheiten.kurzform("")).isEqualTo("Stk");
    }

    @Test
    void verrechnungseinheit() {
        assertThat(IdsEinheiten.verrechnungseinheit("Stk")).isEqualTo(Verrechnungseinheit.STUECK);
        assertThat(IdsEinheiten.verrechnungseinheit("m")).isEqualTo(Verrechnungseinheit.LAUFENDE_METER);
        assertThat(IdsEinheiten.verrechnungseinheit("m²")).isEqualTo(Verrechnungseinheit.QUADRATMETER);
        assertThat(IdsEinheiten.verrechnungseinheit("kg")).isEqualTo(Verrechnungseinheit.KILOGRAMM);
        assertThat(IdsEinheiten.verrechnungseinheit("Kartusche")).isNull();
        assertThat(IdsEinheiten.verrechnungseinheit(null)).isNull();
    }
}
