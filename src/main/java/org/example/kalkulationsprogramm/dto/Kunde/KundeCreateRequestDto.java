package org.example.kalkulationsprogramm.dto.Kunde;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KundeCreateRequestDto {

    @Size(max = 64, message = "Kundennummer ist zu lang.")
    private String kundennummer;

    @NotBlank(message = "Name darf nicht leer sein.")
    @Size(max = 255, message = "Name ist zu lang.")
    private String name;

    @Size(max = 20, message = "Anrede ist zu lang.")
    private String anrede;

    @Size(max = 255, message = "Ansprechpartner ist zu lang.")
    private String ansprechspartner;

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

    private Integer zahlungsziel;

    private List<@Email(message = "Ungültige E-Mail-Adresse.") @Size(max = 255, message = "E-Mail ist zu lang.") String> kundenEmails;

    public void setKundenEmails(List<String> kundenEmails) {
        if (kundenEmails == null) {
            this.kundenEmails = null;
            return;
        }
        this.kundenEmails = kundenEmails.stream()
                .map(email -> {
                    if (email == null) {
                        return null;
                    }
                    String trimmed = email.trim();
                    return trimmed.isEmpty() ? null : trimmed;
                })
                .toList();
    }
}
