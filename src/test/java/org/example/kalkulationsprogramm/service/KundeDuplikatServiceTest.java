package org.example.kalkulationsprogramm.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatGrund;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatResponseDto;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class KundeDuplikatServiceTest {

    private final KundeRepository repo = mock(KundeRepository.class);
    private final KundeDuplikatService service = new KundeDuplikatService(repo);

    @Test
    @DisplayName("Telefonnummer wird auf Ziffern reduziert; +49 / 0049 → 0")
    void normalisiereTelefonNormalizesGermanPrefixes() {
        assertThat(KundeDuplikatService.normalisiereTelefon("+49 30 1234567")).isEqualTo("0301234567");
        assertThat(KundeDuplikatService.normalisiereTelefon("0049 30 1234567")).isEqualTo("0301234567");
        assertThat(KundeDuplikatService.normalisiereTelefon("030/123-4567")).isEqualTo("0301234567");
        assertThat(KundeDuplikatService.normalisiereTelefon("030 123 4567")).isEqualTo("0301234567");
        assertThat(KundeDuplikatService.normalisiereTelefon("")).isNull();
        assertThat(KundeDuplikatService.normalisiereTelefon(null)).isNull();
    }

    @Test
    @DisplayName("Nur-Vorwahl (< 7 Ziffern) liefert null – verhindert Massen-Duplikate durch auto-befüllte Vorwahl")
    void nurVorwahlWirdIgnoriert() {
        // Typischer Fall: Frontend befüllt "0931 " automatisch aus der PLZ
        assertThat(KundeDuplikatService.normalisiereTelefon("0931 ")).isNull();
        assertThat(KundeDuplikatService.normalisiereTelefon("0931")).isNull();
        assertThat(KundeDuplikatService.normalisiereTelefon("030")).isNull();
        assertThat(KundeDuplikatService.normalisiereTelefon("02")).isNull();
        // Vollständige Nummer bleibt gültig
        assertThat(KundeDuplikatService.normalisiereTelefon("0931 12345")).isEqualTo("093112345");
    }

    @Test
    @DisplayName("Nur-Vorwahl im Telefon-Feld löst keinen Duplikat-Alarm aus")
    void nurVorwahlLoestKeinenDuplikatAlarmAus() {
        Kunde k = kunde(11L, "Mustermann");
        k.setTelefon("0931 ");
        given(repo.findePotenzielleDuplikate(any(), any(), any(), any(), any(), any())).willReturn(List.of(k));

        // Neuer Kunde hat ebenfalls nur die Vorwahl (auto-befüllt)
        KundeDuplikatResponseDto resp = service.findeDuplikate(
                null, "0931 ", null, null, null, null);

        assertThat(resp.getDuplikate()).isEmpty();
        assertThat(resp.isHarterTreffer()).isFalse();
    }

    @Test
    @DisplayName("Straße: 'straße' und 'strasse' werden zu 'str.' normalisiert")
    void normalisiereStrasseUnifiesVariants() {
        assertThat(KundeDuplikatService.normalisiereStrasse("Hauptstraße 12")).isEqualTo("hauptstr. 12");
        assertThat(KundeDuplikatService.normalisiereStrasse("Hauptstrasse 12")).isEqualTo("hauptstr. 12");
        assertThat(KundeDuplikatService.normalisiereStrasse("  HAUPTSTR.  12 ")).isEqualTo("hauptstr. 12");
    }

    @Test
    @DisplayName("Email-Match liefert harten Treffer mit Score 100")
    void emailMatchYieldsHartenTreffer() {
        Kunde k = kunde(7L, "Müller GmbH");
        k.setKundenEmails(List.of("info@mueller.de"));
        given(repo.findePotenzielleDuplikate(any(), any(), any(), any(), any(), any())).willReturn(List.of(k));

        KundeDuplikatResponseDto resp = service.findeDuplikate(
                "INFO@Mueller.de", null, null, null, null, null);

        assertThat(resp.isHarterTreffer()).isTrue();
        assertThat(resp.getDuplikate()).hasSize(1);
        assertThat(resp.getDuplikate().get(0).getGruende()).containsExactly(KundeDuplikatGrund.EMAIL_GLEICH);
        assertThat(resp.getDuplikate().get(0).getScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("Telefon-Match: +49-Variante matcht 030-Variante")
    void telefonMatchAcrossPrefixVariants() {
        Kunde k = kunde(8L, "Schmidt KG");
        k.setTelefon("+49 30 1234567");
        given(repo.findePotenzielleDuplikate(any(), any(), any(), any(), any(), any())).willReturn(List.of(k));

        KundeDuplikatResponseDto resp = service.findeDuplikate(
                null, "030/123-4567", null, null, null, null);

        assertThat(resp.isHarterTreffer()).isTrue();
        assertThat(resp.getDuplikate().get(0).getGruende()).containsExactly(KundeDuplikatGrund.TELEFON_GLEICH);
    }

    @Test
    @DisplayName("Name + PLZ ist weicher Treffer (kein harterTreffer-Flag)")
    void namePlzIsSoftMatch() {
        Kunde k = kunde(9L, "Familie Meier");
        k.setPlz("10115");
        given(repo.findePotenzielleDuplikate(any(), any(), any(), any(), any(), any())).willReturn(List.of(k));

        KundeDuplikatResponseDto resp = service.findeDuplikate(
                null, null, null, "Familie Meier", "10115", null);

        assertThat(resp.isHarterTreffer()).isFalse();
        assertThat(resp.getDuplikate()).hasSize(1);
        assertThat(resp.getDuplikate().get(0).getGruende()).containsExactly(KundeDuplikatGrund.NAME_PLZ_GLEICH);
    }

    @Test
    @DisplayName("Ohne relevante Eingabe wird das Repository nicht angefragt")
    void leereEingabeUmgehtRepository() {
        KundeDuplikatResponseDto resp = service.findeDuplikate(null, null, null, null, null, null);
        assertThat(resp.getDuplikate()).isEmpty();
        assertThat(resp.isHarterTreffer()).isFalse();
    }

    @Test
    @DisplayName("Mehrere Match-Gründe pro Kunde werden kombiniert und Score addiert")
    void mehrfachTreffer() {
        Kunde k = kunde(10L, "Bau AG");
        k.setKundenEmails(List.of("kontakt@bau.de"));
        k.setTelefon("030 555 1");
        given(repo.findePotenzielleDuplikate(any(), any(), any(), any(), any(), any())).willReturn(List.of(k));

        KundeDuplikatResponseDto resp = service.findeDuplikate(
                "kontakt@bau.de", "0305551", null, null, null, null);

        assertThat(resp.getDuplikate()).hasSize(1);
        assertThat(resp.getDuplikate().get(0).getGruende())
                .containsExactlyInAnyOrder(KundeDuplikatGrund.EMAIL_GLEICH, KundeDuplikatGrund.TELEFON_GLEICH);
        assertThat(resp.getDuplikate().get(0).getScore()).isEqualTo(190);
    }

    private Kunde kunde(Long id, String name) {
        Kunde k = new Kunde();
        k.setId(id);
        k.setName(name);
        return k;
    }
}
