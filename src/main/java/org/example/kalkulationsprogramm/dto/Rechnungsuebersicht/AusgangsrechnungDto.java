package org.example.kalkulationsprogramm.dto.Rechnungsuebersicht;

import java.time.LocalDate;

public class AusgangsrechnungDto {
    public Long id;
    public boolean storniert;
    public boolean storno;
    public String editorUrl;
    public String dokumentid;
    public String geschaeftsdokumentart;
    public LocalDate rechnungsdatum;
    public LocalDate faelligkeitsdatum;
    public Double bruttoBetrag;
    public Boolean bezahlt;
    public String originalDateiname;
    public String pdfUrl;
    public Long projektId;
    public String projektAuftragsnummer;
    public String projektKunde;
}
