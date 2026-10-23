package org.example.kalkulationsprogramm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Die Benutzerverwaltung lädt die Profile als JSON. Vom Mitarbeiter gehören nur
 * ID und Name hinein: Mit dem ganzen Mitarbeiter brach die Liste, sobald eine
 * Krankenkasse (lazy geladen) eingetragen war, und sie verriet Login-Code und Lohndaten.
 */
class FrontendUserProfileJsonTest {

    @Test
    void mitarbeiterNurMitIdUndName() throws Exception {
        var kasse = new Krankenkasse();
        kasse.setName("Test-Kasse");
        var mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(7L);
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");
        mitarbeiter.setLoginToken("dummy-login-token");
        mitarbeiter.setKrankenkasse(kasse);
        var profil = new FrontendUserProfile();
        profil.setId(1L);
        profil.setDisplayName("Max Mustermann");
        profil.setMitarbeiter(mitarbeiter);

        JsonNode json = JsonMapper.builderWithJackson2Defaults()
                .findAndAddModules()
                .build().valueToTree(profil).get("mitarbeiter");

        assertThat(json.get("id").asLong(0L)).isEqualTo(7L);
        assertThat(json.get("vorname").asString("")).isEqualTo("Max");
        assertThat(json.get("nachname").asString("")).isEqualTo("Mustermann");
        assertThat(json.size()).as("nur id, vorname, nachname").isEqualTo(3);
        assertThat(json.has("krankenkasse")).isFalse();
        assertThat(json.has("loginToken")).isFalse();
    }
}
