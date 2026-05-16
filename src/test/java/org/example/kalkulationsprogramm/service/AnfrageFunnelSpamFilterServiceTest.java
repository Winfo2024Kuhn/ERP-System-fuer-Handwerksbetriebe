package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageFunnelRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnfrageFunnelSpamFilterServiceTest {

    private SystemSettingsService settings;

    @BeforeEach
    void setUp() {
        settings = mock(SystemSettingsService.class);
    }

    private void aktivieren() {
        when(settings.isAnfrageFunnelSpamFilterAktiv()).thenReturn(true);
    }

    private AnfrageFunnelRequestDto dummyDto() {
        AnfrageFunnelRequestDto dto = new AnfrageFunnelRequestDto();
        dto.setVorname("Max");
        dto.setNachname("Mustermann");
        dto.setEmail("max@example.test");
        dto.setTelefon("0123 456789");
        dto.setProjektAnschrift("Musterstr. 1, 12345 Musterstadt");
        dto.setServiceTyp("treppe");
        dto.setProjektarten(List.of("Innentreppe"));
        dto.setNachricht("Bitte Rückruf, Termin nächste Woche.");
        return dto;
    }

    /** Test-Stub eines Backends. */
    private static class StubBackend implements SpamFilterChatBackend {
        private final String id;
        private final boolean enabled;
        private final String responseOrNull;
        private final RuntimeException toThrow;
        boolean wasCalled = false;

        StubBackend(String id, boolean enabled, String responseOrNull) {
            this(id, enabled, responseOrNull, null);
        }
        StubBackend(String id, boolean enabled, RuntimeException toThrow) {
            this(id, enabled, null, toThrow);
        }
        private StubBackend(String id, boolean enabled, String responseOrNull, RuntimeException toThrow) {
            this.id = id;
            this.enabled = enabled;
            this.responseOrNull = responseOrNull;
            this.toThrow = toThrow;
        }
        @Override public String identifier() { return id; }
        @Override public boolean isEnabled() { return enabled; }
        @Override public String chat(String s, String u) {
            wasCalled = true;
            if (toThrow != null) throw toThrow;
            return responseOrNull;
        }
    }

    @Test
    void pruefe_returnsOk_whenDtoNull() {
        var service = new AnfrageFunnelSpamFilterService(List.of(), new ObjectMapper(), settings);
        assertThat(service.pruefe(null).spam()).isFalse();
    }

    @Test
    void pruefe_returnsOk_whenFilterDeaktiviert() {
        when(settings.isAnfrageFunnelSpamFilterAktiv()).thenReturn(false);
        var backend = new StubBackend("lokal", true, "{\"spam\":true,\"grund\":\"x\"}");
        var service = new AnfrageFunnelSpamFilterService(List.of(backend), new ObjectMapper(), settings);

        var result = service.pruefe(dummyDto());

        assertThat(result.spam()).isFalse();
        assertThat(backend.wasCalled).isFalse();
    }

    @Test
    void pruefe_returnsOk_whenProviderIstAus() {
        aktivieren();
        when(settings.getAnfrageFunnelSpamFilterProvider()).thenReturn("aus");
        var backend = new StubBackend("lokal", true, "{\"spam\":true,\"grund\":\"x\"}");
        var service = new AnfrageFunnelSpamFilterService(List.of(backend), new ObjectMapper(), settings);

        var result = service.pruefe(dummyDto());

        assertThat(result.spam()).isFalse();
        assertThat(backend.wasCalled).isFalse();
    }

    @Test
    void pruefe_returnsOk_whenKeinBackendEnabled() {
        aktivieren();
        when(settings.getAnfrageFunnelSpamFilterProvider()).thenReturn("lokal");
        var lokal = new StubBackend("lokal", false, "{}");
        var extern = new StubBackend("extern", false, "{}");
        var service = new AnfrageFunnelSpamFilterService(List.of(lokal, extern), new ObjectMapper(), settings);

        var result = service.pruefe(dummyDto());

        assertThat(result.spam()).isFalse();
        assertThat(lokal.wasCalled).isFalse();
        assertThat(extern.wasCalled).isFalse();
    }

    @Test
    void pruefe_nutztExpliziterWunschBackend_wennEnabled() {
        aktivieren();
        when(settings.getAnfrageFunnelSpamFilterProvider()).thenReturn("extern");
        var lokal = new StubBackend("lokal", true, "{\"spam\":true,\"grund\":\"vom lokalen\"}");
        var extern = new StubBackend("extern", true, "{\"spam\":true,\"grund\":\"vom externen\"}");
        var service = new AnfrageFunnelSpamFilterService(List.of(lokal, extern), new ObjectMapper(), settings);

        var result = service.pruefe(dummyDto());

        assertThat(result.spam()).isTrue();
        assertThat(result.grund()).isEqualTo("vom externen");
        assertThat(extern.wasCalled).isTrue();
        assertThat(lokal.wasCalled).isFalse();
    }

    @Test
    void pruefe_faelltAufLokalZurueck_wennExternNichtEnabled() {
        aktivieren();
        when(settings.getAnfrageFunnelSpamFilterProvider()).thenReturn("extern");
        var lokal = new StubBackend("lokal", true, "{\"spam\":false}");
        var extern = new StubBackend("extern", false, "{}");
        var service = new AnfrageFunnelSpamFilterService(List.of(lokal, extern), new ObjectMapper(), settings);

        var result = service.pruefe(dummyDto());

        assertThat(result.spam()).isFalse();
        assertThat(lokal.wasCalled).isTrue();
        assertThat(extern.wasCalled).isFalse();
    }

    @Test
    void pruefe_defaultProviderIstLokal() {
        aktivieren();
        when(settings.getAnfrageFunnelSpamFilterProvider()).thenReturn("lokal");
        var lokal = new StubBackend("lokal", true, "{\"spam\":false}");
        var extern = new StubBackend("extern", true, "{\"spam\":true,\"grund\":\"falsch\"}");
        var service = new AnfrageFunnelSpamFilterService(List.of(lokal, extern), new ObjectMapper(), settings);

        var result = service.pruefe(dummyDto());

        assertThat(result.spam()).isFalse();
        assertThat(lokal.wasCalled).isTrue();
        assertThat(extern.wasCalled).isFalse();
    }

    @Test
    void pruefe_passiertAnfrageBeiBackendFehler_durch() {
        aktivieren();
        when(settings.getAnfrageFunnelSpamFilterProvider()).thenReturn("lokal");
        var lokal = new StubBackend("lokal", true, new RuntimeException("Netzwerk weg"));
        var service = new AnfrageFunnelSpamFilterService(List.of(lokal), new ObjectMapper(), settings);

        var result = service.pruefe(dummyDto());

        assertThat(result.spam()).isFalse();
    }

    @Test
    void pruefe_parsterSpamJson_korrekt() {
        aktivieren();
        when(settings.getAnfrageFunnelSpamFilterProvider()).thenReturn("lokal");
        var lokal = new StubBackend("lokal", true,
                "{\"spam\":true,\"grund\":\"E-Mail wirkt unsinnig\"}");
        var service = new AnfrageFunnelSpamFilterService(List.of(lokal), new ObjectMapper(), settings);

        var result = service.pruefe(dummyDto());

        assertThat(result.spam()).isTrue();
        assertThat(result.grund()).isEqualTo("E-Mail wirkt unsinnig");
    }

    @Test
    void pruefe_passiertAnfrageBeiUnparsbarerAntwort_durch() {
        aktivieren();
        when(settings.getAnfrageFunnelSpamFilterProvider()).thenReturn("lokal");
        var lokal = new StubBackend("lokal", true, "kein json sondern müll");
        var service = new AnfrageFunnelSpamFilterService(List.of(lokal), new ObjectMapper(), settings);

        var result = service.pruefe(dummyDto());

        assertThat(result.spam()).isFalse();
    }

    @Test
    void pruefe_nimmtErstesEnabledBackend_alsLetzteOption() {
        aktivieren();
        when(settings.getAnfrageFunnelSpamFilterProvider()).thenReturn("unbekannt");
        var lokal = new StubBackend("lokal", false, "{}");
        var extern = new StubBackend("extern", true, "{\"spam\":false}");
        var service = new AnfrageFunnelSpamFilterService(List.of(lokal, extern), new ObjectMapper(), settings);

        var result = service.pruefe(dummyDto());

        assertThat(result.spam()).isFalse();
        assertThat(extern.wasCalled).isTrue();
    }

    @Test
    void pruefe_pruefSettingAktiv_nichtNoetigWennKeinDto() {
        var service = new AnfrageFunnelSpamFilterService(List.of(), new ObjectMapper(), settings);
        service.pruefe(null);
        verify(settings, never()).isAnfrageFunnelSpamFilterAktiv();
    }
}
