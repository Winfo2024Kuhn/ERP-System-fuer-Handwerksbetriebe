package org.example.kalkulationsprogramm.domain;

import java.time.LocalDate;

public interface Dokument {
    Long getId();
    String getOriginalDateiname();
    String getGespeicherterDateiname();
    String getDateityp();
    Long getDateigroesse();
    LocalDate getUploadDatum();
    LocalDate getEmailVersandDatum();
    DokumentGruppe getDokumentGruppe();
}
