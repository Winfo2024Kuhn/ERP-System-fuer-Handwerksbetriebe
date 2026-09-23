package org.example.kalkulationsprogramm.dto.Einkauf;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class EinkaufKontaktDto {
    private EinkaufKontaktDto() {}

    public record Kontakt(Long id, long version,
            @Size(max = 160) String name,
            @Size(max = 40) String anrede,
            @NotBlank @Email @Size(max = 254) String email,
            boolean standardAnfrage, boolean standardBestellung, boolean aktiv) {}

    public enum KontaktZweck { ANFRAGE, BESTELLUNG }

    public record Snapshot(Long lieferantId, Long kontaktId, String lieferantenname,
            String email, String name, String anrede, String eigeneKundennummer) {}
}
