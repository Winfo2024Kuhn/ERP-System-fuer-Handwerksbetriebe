package org.example.kalkulationsprogramm.dto.Email;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReklamationEmailGenerateRequest {

    @NotBlank(message = "Beschreibung darf nicht leer sein")
    @Size(max = 10000, message = "Beschreibung zu lang")
    private String beschreibung;

    @Size(max = 200, message = "Lieferantenname zu lang")
    private String lieferantName;

    /** URLs der Reklamationsbilder (relativ, z.B. /api/lieferanten/bilder/file/...) */
    private List<String> bildUrls;
}
