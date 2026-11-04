package org.example.kalkulationsprogramm.dto.Lieferant;

import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.domain.LieferantRolle;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class LieferantDetailDto {
    private Long id;
    private String lieferantenname;
    private String aliasName;
    private String eigeneKundennummer;
    private String lieferantenTyp;
    private Set<LieferantRolle> rollen;
    private String vertreter;
    private String strasse;
    private String plz;
    private String ort;
    private String telefon;
    private String mobiltelefon;
    private Boolean istAktiv;
    private LocalDate startZusammenarbeit;
    private List<String> kundenEmails;
    private Long standardKostenstelleId;
    private String standardKostenstelleName;
    /** true = Rechnungen dieses Lieferanten gelten beim Import sofort als bezahlt. */
    private Boolean vorauskasse;
    private LieferantStatistikDto statistik;
    private List<LieferantArtikelpreisDto> artikelpreise;
    private List<LieferantDokumentDto.Response> dokumente;
    /** Anzahl der Dokumente, auch wenn die Liste selbst (noch) nicht mitgeladen wurde. */
    private Long dokumenteAnzahl;
    /**
     * Zähler für die Reiter, damit sie stimmen, bevor die Listen nachgeladen sind. {@code emailAnzahl}
     * zählt E-Mail-Verläufe (wie der Reiter {@code GET /api/emails/reiter/lieferant/{id}}), nicht Mails.
     */
    private Long emailAnzahl;
    private Long notizenAnzahl;
    private List<LieferantNotizDto> notizen;
}
