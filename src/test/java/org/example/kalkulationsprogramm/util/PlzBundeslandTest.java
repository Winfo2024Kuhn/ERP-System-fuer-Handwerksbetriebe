package org.example.kalkulationsprogramm.util;

import org.example.kalkulationsprogramm.domain.Bundesland;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Bundesland-Vorschlag aus der Postleitzahl (Issue #222). */
class PlzBundeslandTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            // Landeshauptstädte und große Städte
            "80331, BY", "97259, BY", "70173, BW", "10115, BE", "14467, BB", "28195, HB", "20095, HH",
            "65183, HE", "19053, MV", "30159, NI", "40213, NW", "55116, RP", "66111, SL", "01067, SN",
            "39104, ST", "24103, SH", "99084, TH", "04109, SN", "06108, ST", "07743, TH", "68159, BW",
            "86150, BY", "60311, HE", "50667, NW",
            // Grenzgebiete
            "01968, BB", "04600, TH", "04910, BB", "06567, TH", "07952, SN", "14193, BE", "17291, BB",
            "19348, BB", "21073, HH", "21502, SH", "21522, NI", "21335, NI", "22880, SH", "23966, MV",
            "27568, HB", "28832, NI", "34346, NI", "34414, NW", "36433, TH", "37269, HE", "37308, TH",
            "37671, NW", "38855, ST", "48529, NI", "49477, NW", "53474, RP", "57610, RP", "63739, BY",
            "66482, RP", "66822, SL", "66953, RP", "68519, HE", "68623, HE", "69239, HE", "69483, HE",
            "76829, RP", "76703, BW", "88131, BY", "88212, BW", "89231, BY", "89073, BW", "89518, BW",
            "96515, TH", "97941, BW", "97877, BW", "98527, TH"})
    void vorschlag(String plz, Bundesland erwartet) {
        assertEquals(Optional.of(erwartet), PlzBundesland.vorschlag(plz));
    }

    @Test
    void vorschlag_verzeihtLeerzeichenUndLaenderkennung() {
        assertEquals(Optional.of(Bundesland.BY), PlzBundesland.vorschlag(" 97259 "));
        assertEquals(Optional.of(Bundesland.BY), PlzBundesland.vorschlag("D-97259"));
        assertEquals(Optional.of(Bundesland.BY), PlzBundesland.vorschlag("d- 97259"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"9725", "972590", "97a59", "00999", "'; DROP TABLE x; --", "<script>alert(1)</script>",
            "A-1010", "-9725"})
    void vorschlag_ungueltig_istLeer(String plz) {
        assertTrue(PlzBundesland.vorschlag(plz).isEmpty());
    }

    @Test
    void istAugsburg() {
        assertTrue(PlzBundesland.istAugsburg("86150"));
        assertTrue(PlzBundesland.istAugsburg("D-86199"));
        assertFalse(PlzBundesland.istAugsburg("86200"));
        assertFalse(PlzBundesland.istAugsburg("86149"));
        assertFalse(PlzBundesland.istAugsburg(null));
        assertFalse(PlzBundesland.istAugsburg("Augsburg"));
    }
}
