package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;

/** Fachliche Vorzeichen unabhängig von der Darstellung auf dem eingelesenen Beleg. */
final class DokumentBetragsvorzeichen {

    private DokumentBetragsvorzeichen() {
    }

    static BigDecimal alsMinderung(BigDecimal betrag) {
        return betrag == null ? null : betrag.abs().negate();
    }

    static void normalisiereGutschrift(LieferantGeschaeftsdokument daten, LieferantDokumentTyp typ) {
        if (daten != null && typ == LieferantDokumentTyp.GUTSCHRIFT) {
            daten.setBetragNetto(alsMinderung(daten.getBetragNetto()));
            daten.setBetragBrutto(alsMinderung(daten.getBetragBrutto()));
        }
    }
}
