package org.example.kalkulationsprogramm.dto.Lieferant;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class LieferantCreateRequestDto {

    @NotBlank(message = "Lieferantenname darf nicht leer sein.")
    @Size(max = 255, message = "Lieferantenname ist zu lang.")
    private String lieferantenname;

    @Size(max = 50, message = "Kundennummer ist zu lang.")
    private String eigeneKundennummer;

    @Size(max = 100, message = "Lieferantentyp ist zu lang.")
    private String lieferantenTyp;

    @Size(max = 255, message = "Vertreter ist zu lang.")
    private String vertreter;

    @Size(max = 255, message = "Straße ist zu lang.")
    private String strasse;

    @Size(max = 20, message = "PLZ ist zu lang.")
    private String plz;

    @Size(max = 255, message = "Ort ist zu lang.")
    private String ort;

    @Size(max = 50, message = "Telefonnummer ist zu lang.")
    private String telefon;

    @Size(max = 50, message = "Mobiltelefon ist zu lang.")
    private String mobiltelefon;

    private Boolean istAktiv = Boolean.TRUE;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startZusammenarbeit;

    private List<@Email(message = "Ungültige E-Mail-Adresse.") @Size(max = 255, message = "E-Mail ist zu lang.") String> kundenEmails;

    /**
     * Optionale Standard-Kostenstelle. Wird bei späteren Zuordnungen automatisch vorgeschlagen.
     */
    private Long standardKostenstelleId;
}
