package org.example.kalkulationsprogramm.dto.Anfrage;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Eingehende Funnel-Anfrage von der öffentlichen Marketing-Webseite.
 * Wird per Server-zu-Server-Call (Cloudflare-Tunnel + Access Service Token)
 * an das ERP übergeben.
 */
@Getter
@Setter
public class AnfrageFunnelRequestDto {

    @NotBlank
    @Size(max = 50)
    private String serviceTyp;

    @Size(max = 20)
    private List<@Size(max = 100) String> projektarten;

    @NotBlank
    @Size(max = 5000)
    private String nachricht;

    @NotBlank
    @Size(max = 100)
    private String vorname;

    @NotBlank
    @Size(max = 100)
    private String nachname;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 50)
    private String telefon;

    @Size(max = 500)
    private String projektAnschrift;

    private boolean datenschutzAkzeptiert;

    @Size(max = 50)
    private String consentIp;

    @Size(max = 50)
    private String datenschutzVersion;

    @AssertTrue(message = "Datenschutz muss akzeptiert werden")
    public boolean isDatenschutzAkzeptiert() {
        return datenschutzAkzeptiert;
    }
}
