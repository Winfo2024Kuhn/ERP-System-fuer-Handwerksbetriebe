package org.example.kalkulationsprogramm.dto.Lieferant;

import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.dto.ProjektEmail.ProjektEmailDto;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class LieferantDetailDto {
    private Long id;
    private String lieferantenname;
    private String eigeneKundennummer;
    private String lieferantenTyp;
    private String vertreter;
    private String strasse;
    private String plz;
    private String ort;
    private String telefon;
    private String mobiltelefon;
    private Boolean istAktiv;
    private LocalDate startZusammenarbeit;
    private List<String> kundenEmails;
    private LieferantStatistikDto statistik;
    private List<LieferantArtikelpreisDto> artikelpreise;
    private List<LieferantKommunikationDto> kommunikation;
    private List<LieferantDokumentDto.Response> dokumente;
    private List<ProjektEmailDto> emails; // Unified email structure for EmailsTab
    private List<LieferantNotizDto> notizen;
}
