package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.springframework.stereotype.Service;

/**
 * Simple service to build {@link ZugferdDaten} for a project invoice.
 * Kundennummer and Kundenname are taken from the project data.
 */
@Service
public class ZugferdConverterService {

    /**
     * Creates a {@link ZugferdDaten} instance for the given invoice document.
     * Customer number and name are taken from the associated project.
     *
     * @param projekt   the project containing customer data
     * @param rechnung  the invoice document
     * @return populated {@link ZugferdDaten}
     */
    public ZugferdDaten convertRechnung(Projekt projekt, ProjektGeschaeftsdokument rechnung) {
        ZugferdDaten daten = new ZugferdDaten();
        if (projekt != null) {
            daten.setKundenName(projekt.getKunde());
            daten.setKundennummer(projekt.getKundennummer());
        }
        if (rechnung != null) {
            daten.setRechnungsnummer(rechnung.getDokumentid());
            daten.setGeschaeftsdokumentart(rechnung.getGeschaeftsdokumentart());
            daten.setRechnungsdatum(rechnung.getRechnungsdatum());
            daten.setFaelligkeitsdatum(rechnung.getFaelligkeitsdatum());
            daten.setBetrag(rechnung.getBruttoBetrag());
        }
        return daten;
    }
}

