package org.example.kalkulationsprogramm.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Sichert den JSON-Vertrag von {@link EmailThreadEntryDto} ab.
 *
 * <p>Anlass: Beim Ergaenzen der Zustellfelder rutschten diese versehentlich
 * zwischen {@code @JsonProperty("isDraft")} und das zugehoerige Feld. Java
 * bindet eine Annotation immer an das naechste Feld — Kommentare trennen nicht.
 * Dadurch trug {@code zustellStatus} den Namen {@code isDraft}, das JSON lieferte
 * {@code "isDraft": "OFFEN"} (ein String!), und das Frontend hielt jede Mail im
 * Thread fuer einen Entwurf. Jackson meldet so etwas nicht, der Build blieb
 * gruen — nur dieser Test faellt darauf.</p>
 */
class EmailThreadEntryDtoSerialisierungTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode serialisiere() throws Exception
    {
        EmailThreadEntryDto dto = new EmailThreadEntryDto();
        dto.setId(1L);
        dto.setSubject("Angebot AG-2026/07/00005");
        dto.setDirection("OUT");
        dto.setDraft(false);
        dto.setZustellStatus("UNZUSTELLBAR");
        dto.setZustellFehler("unknown user");
        return MAPPER.readTree(MAPPER.writeValueAsString(dto));
    }

    @Test
    void isDraftBleibtEinBooleanUndTraegtSeinenNamen() throws Exception
    {
        JsonNode json = serialisiere();

        assertThat(json.has("isDraft")).as("Feld 'isDraft' muss im JSON existieren").isTrue();
        assertThat(json.get("isDraft").isBoolean())
                .as("'isDraft' muss ein Boolean sein — ein String bricht die Thread-Ansicht")
                .isTrue();
        assertThat(json.get("isDraft").asBoolean()).isFalse();
    }

    @Test
    void zustellfelderErscheinenUnterEigenemNamen() throws Exception
    {
        JsonNode json = serialisiere();

        assertThat(json.get("zustellStatus").asText()).isEqualTo("UNZUSTELLBAR");
        assertThat(json.get("zustellFehler").asText()).isEqualTo("unknown user");
    }
}
