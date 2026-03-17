package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class EmailAiServiceTest {

    private EmailAiService createDisabledService() throws Exception {
        EmailAiService service = new EmailAiService(new ObjectMapper());
        Field enabledField = EmailAiService.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.set(service, false);
        return service;
    }

    @Nested
    @DisplayName("generateReklamationEmail")
    class GenerateReklamation {

        @Test
        @DisplayName("Null Beschreibung wirft IllegalArgumentException")
        void nullBeschreibungWirft() throws Exception {
            EmailAiService service = createDisabledService();
            // Re-enable to hit validation before API call
            Field enabledField = EmailAiService.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(service, true);

            assertThrows(IllegalArgumentException.class,
                    () -> service.generateReklamationEmail(null, "Lieferant", Collections.emptyList()));
        }

        @Test
        @DisplayName("Leere Beschreibung wirft IllegalArgumentException")
        void leereBeschreibungWirft() throws Exception {
            EmailAiService service = createDisabledService();
            Field enabledField = EmailAiService.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(service, true);

            assertThrows(IllegalArgumentException.class,
                    () -> service.generateReklamationEmail("   ", "Lieferant", Collections.emptyList()));
        }

        @Test
        @DisplayName("Deaktivierter Service wirft IOException")
        void deaktiviertWirftIOException() throws Exception {
            EmailAiService service = createDisabledService();

            assertThrows(IOException.class,
                    () -> service.generateReklamationEmail("Ware beschädigt", "Test", Collections.emptyList()));
        }

        @Test
        @DisplayName("Null Bildliste wird toleriert")
        void nullBildlisteWirdToleriert() throws Exception {
            EmailAiService service = createDisabledService();

            // Should throw IOException (disabled) not NPE
            assertThrows(IOException.class,
                    () -> service.generateReklamationEmail("Ware beschädigt", "Test", null));
        }

        @Test
        @DisplayName("Leerer Lieferantname wird toleriert")
        void leererLieferantnameWirdToleriert() throws Exception {
            EmailAiService service = createDisabledService();

            // Should throw IOException (disabled) not NPE
            assertThrows(IOException.class,
                    () -> service.generateReklamationEmail("Ware beschädigt", null, Collections.emptyList()));
        }
    }

    @Nested
    @DisplayName("beautify")
    class Beautify {

        @Test
        @DisplayName("Deaktiviert: gibt Originaltext zurück")
        void deaktiviertGibtOriginalZurueck() throws Exception {
            EmailAiService service = createDisabledService();
            String result = service.beautify("Hallo Welt");
            assertEquals("Hallo Welt", result);
        }

        @Test
        @DisplayName("Null-Text gibt null zurück")
        void nullTextGibtNullZurueck() throws Exception {
            EmailAiService service = createDisabledService();
            String result = service.beautify(null);
            assertNull(result);
        }

        @Test
        @DisplayName("Leerer Text gibt Originaltext zurück")
        void leererTextGibtOriginalZurueck() throws Exception {
            EmailAiService service = createDisabledService();
            String result = service.beautify("   ");
            assertEquals("   ", result);
        }
    }
}

