package org.example.kalkulationsprogramm.dto.Abteilung;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Anfrage zum Anlegen oder Umbenennen einer Abteilung: nur der Name. Geprüft wird im {@code AbteilungService}. */
@Getter
@Setter
@NoArgsConstructor
public class AbteilungNameDto {
    private String name;
}
