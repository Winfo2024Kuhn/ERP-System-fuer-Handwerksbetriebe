package org.example.kalkulationsprogramm.domain;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Der Urlaubsantrag wird von den /api/urlaub-Endpoints direkt als JSON
 * ausgeliefert (PWA-Zeiterfassung + PC-Urlaubsverwaltung). Der eingebettete
 * Mitarbeiter darf dabei nur die Felder enthalten, die die Frontends nutzen
 * (id, vorname, nachname, jahresUrlaub) – sensible Daten (Krankenkasse,
 * Stundenlohn, Lohnabrechnungen, Notizen, Login-Token) dürfen nicht ins JSON.
 * Lazy-Relationen wie krankenkasse würden als Hibernate-Proxy außerdem die
 * Serialisierung mit einem 500er crashen (Bug: Urlaubsantrag stellen → 500,
 * Antrag gespeichert, aber nirgends sichtbar).
 */
class UrlaubsantragSerialisierungTest {

    // Wie Spring Boot: JavaTime-Modul + ISO-Datumsformat statt Timestamps
    private final ObjectMapper objectMapper = JsonMapper.builderWithJackson2Defaults()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private Urlaubsantrag antrag;

    @BeforeEach
    void setUp() {
        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(1L);
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");
        mitarbeiter.setJahresUrlaub(30);
        // Sensible Daten, die niemals in der Urlaubs-API landen dürfen:
        mitarbeiter.setStundenlohn(new BigDecimal("25.50"));
        mitarbeiter.setGeburtstag(LocalDate.of(1990, 1, 1));
        mitarbeiter.setLoginToken("geheimer-token");
        mitarbeiter.setKrankenkasse(new Krankenkasse());
        mitarbeiter.getNotizen().add(new MitarbeiterNotiz());

        antrag = new Urlaubsantrag();
        antrag.setId(42L);
        antrag.setMitarbeiter(mitarbeiter);
        antrag.setVonDatum(LocalDate.of(2026, 8, 3));
        antrag.setBisDatum(LocalDate.of(2026, 8, 14));
        antrag.setBemerkung("Sommerurlaub");
        antrag.setTyp(Urlaubsantrag.Typ.URLAUB);
        antrag.setStatus(Urlaubsantrag.Status.OFFEN);
    }

    @Test
    @DisplayName("Mitarbeiter im Urlaubsantrag-JSON enthält nur die von den Frontends genutzten Felder")
    void mitarbeiterJsonEnthaeltNurFreigegebeneFelder() {
        JsonNode json = objectMapper.valueToTree(antrag);
        JsonNode mitarbeiterJson = json.get("mitarbeiter");

        assertThat(mitarbeiterJson).isNotNull();
        assertThat(mitarbeiterJson.get("id").asLong(0L)).isEqualTo(1L);
        assertThat(mitarbeiterJson.get("vorname").asString("")).isEqualTo("Max");
        assertThat(mitarbeiterJson.get("nachname").asString("")).isEqualTo("Mustermann");
        assertThat(mitarbeiterJson.get("jahresUrlaub").asInt(0)).isEqualTo(30);

        assertThat(mitarbeiterJson.has("krankenkasse"))
                .as("krankenkasse muss ausgefiltert sein – in Produktion ist sie ein Hibernate-Proxy, "
                        + "dessen Serialisierung den 500er auslöste (End-to-End abgedeckt im UrlaubsantragControllerTest)")
                .isFalse();
        assertThat(mitarbeiterJson.has("stundenlohn")).as("DSGVO: kein Lohn in der Urlaubs-API").isFalse();
        assertThat(mitarbeiterJson.has("geburtstag")).as("DSGVO: kein Geburtstag in der Urlaubs-API").isFalse();
        assertThat(mitarbeiterJson.has("loginToken")).as("Security: kein Login-Token im JSON").isFalse();
        assertThat(mitarbeiterJson.has("notizen")).as("DSGVO: keine Personalnotizen in der Urlaubs-API").isFalse();
        assertThat(mitarbeiterJson.has("lohnabrechnungen")).as("DSGVO: keine Lohnabrechnungen in der Urlaubs-API").isFalse();
    }

    @Test
    @DisplayName("Antrags-Felder selbst bleiben vollständig im JSON erhalten")
    void antragsFelderBleibenErhalten() {
        JsonNode json = objectMapper.valueToTree(antrag);

        assertThat(json.get("id").asLong(0L)).isEqualTo(42L);
        assertThat(json.get("bemerkung").asString("")).isEqualTo("Sommerurlaub");
        assertThat(json.get("status").asString("")).isEqualTo("OFFEN");
        assertThat(json.get("typ").asString("")).isEqualTo("URLAUB");
        assertThat(json.get("vonDatum").asString("")).isEqualTo("2026-08-03");
        assertThat(json.get("bisDatum").asString("")).isEqualTo("2026-08-14");
    }
}
