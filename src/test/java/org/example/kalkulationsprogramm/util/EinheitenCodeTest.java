package org.example.kalkulationsprogramm.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EinheitenCodeTest {

    @ParameterizedTest
    @CsvSource({
            "Stk, H87", "Stk., H87", "Stück, H87", "St, H87",
            "m, MTR", "lfm, MTR", "lfd. m, MTR",
            "m², MTK", "qm, MTK", "m³, MTQ",
            "h, HUR", "Std., HUR", "Stunden, HUR", "min, MIN", "Tag, DAY",
            "kg, KGM", "t, TNE", "Liter, LTR", "km, KMT", "mm, MMT",
            "psch, LS", "Pauschal, LS", "Satz, SET", "Paar, PR"
    })
    void uebersetztUeblicheHandwerkereinheiten(String einheit, String code) {
        assertThat(EinheitenCode.unEceCode(einheit)).isEqualTo(code);
    }

    @ParameterizedTest
    @ValueSource(strings = { "H87", "mtk", " C62 " })
    void uebernimmtSchonGueltigeCodes(String code) {
        assertThat(EinheitenCode.unEceCode(code)).isEqualTo(code.trim().toUpperCase());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "Pal.", "Rolle", "<script>alert(1)</script>", "'; DROP TABLE x; --" })
    void unbekanntesWirdZurNeutralenEinheit(String einheit) {
        assertThat(EinheitenCode.unEceCode(einheit)).isEqualTo(EinheitenCode.EINHEIT);
    }
}
