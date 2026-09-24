package org.example.kalkulationsprogramm.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * /api/frontend-users liefert die Profile direkt als JSON an die Benutzerverwaltung.
 * Der verknüpfte Mitarbeiter darf nur id, vorname und nachname enthalten: Die
 * Lazy-Relation krankenkasse hat als Hibernate-Proxy die ganze Liste mit einem
 * 500er abgebrochen, Lohn- und Personaldaten gehören ohnehin nicht in diese API.
 */
class FrontendUserProfileSerialisierungTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    @DisplayName("Mitarbeiter im Benutzerprofil-JSON enthält nur id, vorname und nachname")
    void mitarbeiterJsonEnthaeltNurFreigegebeneFelder() {
        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(7L);
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");
        mitarbeiter.setStundenlohn(new BigDecimal("25.50"));
        mitarbeiter.setGeburtstag(LocalDate.of(1990, 1, 1));
        mitarbeiter.setLoginToken("geheimer-token");
        mitarbeiter.setKrankenkasse(new Krankenkasse());

        FrontendUserProfile profil = new FrontendUserProfile();
        profil.setId(1L);
        profil.setDisplayName("Max Mustermann");
        profil.setUsername("max.mustermann");
        profil.setMitarbeiter(mitarbeiter);

        JsonNode json = objectMapper.valueToTree(profil);
        JsonNode mitarbeiterJson = json.get("mitarbeiter");

        List<String> felder = new java.util.ArrayList<>();
        mitarbeiterJson.fieldNames().forEachRemaining(felder::add);
        assertThat(felder).containsExactlyInAnyOrder("id", "vorname", "nachname");
        assertThat(mitarbeiterJson.get("nachname").asText()).isEqualTo("Mustermann");
        assertThat(json.has("passwordHash")).isFalse();
    }

    @Test
    @DisplayName("Benutzerprofil ohne Mitarbeiter wird mit mitarbeiter = null serialisiert")
    void profilOhneMitarbeiter() {
        FrontendUserProfile profil = new FrontendUserProfile();
        profil.setId(2L);
        profil.setDisplayName("Erika Musterfrau");

        JsonNode json = objectMapper.valueToTree(profil);

        assertThat(json.get("mitarbeiter").isNull()).isTrue();
        assertThat(json.get("roles").isArray()).isTrue();
    }
}
