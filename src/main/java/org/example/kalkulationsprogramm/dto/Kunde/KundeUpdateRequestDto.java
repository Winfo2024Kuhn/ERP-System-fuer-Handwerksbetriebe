package org.example.kalkulationsprogramm.dto.Kunde;

import lombok.Getter;
import lombok.Setter;

/**
 * Update-Request entspricht aktuell den gleichen Validierungsregeln wie der Create-Request,
 * wird aber bewusst getrennt gehalten, damit sich zukünftige Unterschiede leichter abbilden lassen.
 */
@Getter
@Setter
public class KundeUpdateRequestDto extends KundeCreateRequestDto {
}

