package org.example.kalkulationsprogramm.service.telefon;

import org.example.kalkulationsprogramm.dto.Telefon.AnrufbeantworterDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TelefonBenachrichtigungServiceTest {

    @Test
    @DisplayName("AB-Name aus den Einstellungen, sonst 'AB n'; Dauer m:ss")
    void hilfen() {
        List<AnrufbeantworterDto> abs = List.of(new AnrufbeantworterDto(0, "AB Nacht"));
        assertThat(TelefonBenachrichtigungService.abName(abs, 0)).isEqualTo("AB Nacht");
        assertThat(TelefonBenachrichtigungService.abName(abs, 1)).isEqualTo("AB 2");
        assertThat(TelefonBenachrichtigungService.dauer(42)).isEqualTo("0:42");
        assertThat(TelefonBenachrichtigungService.dauer(125)).isEqualTo("2:05");
        assertThat(TelefonBenachrichtigungService.dauer(-3)).isEqualTo("0:00");
    }
}
