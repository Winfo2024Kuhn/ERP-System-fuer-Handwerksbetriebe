package org.example.kalkulationsprogramm.dto.Freigabe;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FreigabeAkzeptierenRequest
{
    /** Vom Kunden bestätigte E-Mail (optional, kommt aus dem Akzeptanz-Formular). */
    private String email;
    /** Pflicht-Bestätigung "Ich habe das Dokument geprüft und nehme es verbindlich an". */
    private boolean bestaetigung;
    /** Vom Astro-Layer aus Request-Headern extrahierte echte Client-IP. */
    private String clientIp;
    /** Vom Astro-Layer extrahierter User-Agent. */
    private String userAgent;
}
