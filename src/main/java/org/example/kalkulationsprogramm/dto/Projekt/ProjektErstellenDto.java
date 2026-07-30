package org.example.kalkulationsprogramm.dto.Projekt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.dto.Materialkosten.MaterialkostenErfassenDto;
import org.example.kalkulationsprogramm.dto.ProjektProduktkategorie.ProjektProduktkategorieErfassenDto;
import org.example.kalkulationsprogramm.dto.ProjektZeit.ZeitErfassenDto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class ProjektErstellenDto {
    // Attribute
    private String bauvorhaben;
    private String kunde;
    private String kundennummer;
    private Long kundenId;
    private String strasse;
    private String plz;
    private String ort;
    private List<String> kundenEmails;
    private String kurzbeschreibung;
    private String auftragsnummer;
    private LocalDate anlegedatum;
    private LocalDate abschlussdatum;
    /**
     * Auftragspreis – nur lesend. Er ergibt sich ausschließlich aus den Dokumenten
     * (Angebot/Auftragsbestätigung/Nachtragsangebot, ersatzweise die Rechnungssumme),
     * siehe {@code AusgangsGeschaeftsDokumentService#aktualisiereProjektPreisAusDokumenten}.
     * {@code READ_ONLY} sorgt dafür, dass ein mitgeschickter Wert nicht stillschweigend
     * verschluckt wird, sondern gar nicht erst ankommt – die Projektvorlage aus einer
     * Anfrage kann ihn aber weiterhin ausliefern.
     */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private BigDecimal bruttoPreis;
    private List<MaterialkostenErfassenDto> materialkosten;
    private boolean bezahlt;
    /**
     * Haken "Beendet". Bewusst {@code Boolean}: Fehlt das Feld im Request, bleibt der
     * gespeicherte Stand unverändert – ein Teil-Update darf ein beendetes Projekt nicht
     * versehentlich wieder aufreißen.
     */
    private Boolean abgeschlossen;
    private String projektArt;

    // Fremdschlüssel
    private List<Long> anfrageIds;
    private List<Long> angebotIds;
    private List<ZeitErfassenDto> zeitPositionen;
    private List<ProjektProduktkategorieErfassenDto> produktkategorien;

}
