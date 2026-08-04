package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.Kassenzaehlung;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.KassenzaehlungDto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.KassenbuchMonatsabschlussRepository;
import org.example.kalkulationsprogramm.repository.KassenzaehlungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests fuer den Kassensturz.
 *
 * <p>Kernpunkt: Eine Differenz zwischen gezaehltem Bargeld und Kassenbuch
 * ist erlaubt -- eine <em>unerklaerte</em> Differenz nicht. Genau das
 * erzwingt der Service, und genau das wird hier geprueft.</p>
 *
 * <p>DSGVO: nur Dummy-Daten (Max Mustermann).</p>
 */
@ExtendWith(MockitoExtension.class)
class KassenzaehlungServiceTest {

    @Mock private KassenzaehlungRepository zaehlungRepository;
    @Mock private KassenbuchMonatsabschlussRepository abschlussRepository;
    @Mock private BelegRepository belegRepository;
    @Mock private KasseSaldoService kasseSaldoService;
    @Mock private BelegAuditService auditService;

    @InjectMocks private KassenzaehlungService service;

    private Mitarbeiter maxMustermann;

    @BeforeEach
    void setUp() {
        maxMustermann = new Mitarbeiter();
        maxMustermann.setId(1L);
        maxMustermann.setVorname("Max");
        maxMustermann.setNachname("Mustermann");
        lenient().when(zaehlungRepository.saveAndFlush(any())).thenAnswer(i -> {
            Kassenzaehlung z = i.getArgument(0);
            z.setId(1L);
            return z;
        });
    }

    @Test
    @DisplayName("Passt das gezählte Geld zum Kassenbuch, ist die Differenz 0")
    void keineDifferenz() {
        offenerMonat();
        given(kasseSaldoService.berechneSaldoBis(any())).willReturn(new BigDecimal("250.00"));

        KassenzaehlungDto.Response r = service.zaehle(
                request("250.00", null, null, false), maxMustermann, null);

        assertThat(r.getDifferenz()).isEqualByComparingTo("0.00");
        assertThat(r.getAusgleichBelegId()).isNull();
        verify(belegRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Eine Differenz ohne Erklärung wird abgelehnt")
    void differenzOhneBemerkungWirdAbgelehnt() {
        offenerMonat();
        given(kasseSaldoService.berechneSaldoBis(any())).willReturn(new BigDecimal("250.00"));

        assertThatThrownBy(() -> service.zaehle(
                request("230.00", null, null, false), maxMustermann, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("woran das liegt");

        verify(zaehlungRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Eine Differenz mit Erklärung wird gespeichert")
    void differenzMitBemerkungWirdGespeichert() {
        offenerMonat();
        given(kasseSaldoService.berechneSaldoBis(any())).willReturn(new BigDecimal("250.00"));

        KassenzaehlungDto.Response r = service.zaehle(
                request("230.00", null, "Trinkgeld nicht erfasst", false), maxMustermann, null);

        assertThat(r.getDifferenz()).isEqualByComparingTo("-20.00");
        assertThat(r.getBemerkung()).isEqualTo("Trinkgeld nicht erfasst");
        assertThat(r.getAusgleichBelegId()).isNull();
    }

    @Test
    @DisplayName("Fehlbetrag wird auf Wunsch als Kassenausgabe ausgebucht")
    void fehlbetragWirdAusgebucht() {
        offenerMonat();
        given(kasseSaldoService.berechneSaldoBis(any())).willReturn(new BigDecimal("250.00"));
        given(belegRepository.saveAndFlush(any())).willAnswer(i -> {
            Beleg b = i.getArgument(0);
            b.setId(55L);
            return b;
        });

        KassenzaehlungDto.Response r = service.zaehle(
                request("230.00", null, "Fehlbetrag, Ursache unklar", true), maxMustermann, null);

        ArgumentCaptor<Beleg> captor = ArgumentCaptor.forClass(Beleg.class);
        verify(belegRepository).saveAndFlush(captor.capture());
        Beleg ausgleich = captor.getValue();

        assertThat(ausgleich.getBelegKategorie()).isEqualTo(BelegKategorie.KASSE_AUSGABE);
        assertThat(ausgleich.getBetragBrutto()).isEqualByComparingTo("20.00");
        assertThat(ausgleich.getBeschreibung()).contains("Kassenfehlbetrag");
        assertThat(Boolean.TRUE.equals(ausgleich.getIstUmbuchung())).isTrue();
        assertThat(r.getAusgleichBelegId()).isEqualTo(55L);
    }

    @Test
    @DisplayName("Überschuss wird auf Wunsch als Kasseneinnahme ausgebucht")
    void ueberschussWirdAusgebucht() {
        offenerMonat();
        given(kasseSaldoService.berechneSaldoBis(any())).willReturn(new BigDecimal("250.00"));
        given(belegRepository.saveAndFlush(any())).willAnswer(i -> {
            Beleg b = i.getArgument(0);
            b.setId(56L);
            return b;
        });

        service.zaehle(request("265.50", null, "Zu viel Geld in der Lade", true), maxMustermann, null);

        ArgumentCaptor<Beleg> captor = ArgumentCaptor.forClass(Beleg.class);
        verify(belegRepository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getBelegKategorie()).isEqualTo(BelegKategorie.KASSE_EINNAHME);
        assertThat(captor.getValue().getBetragBrutto()).isEqualByComparingTo("15.50");
        assertThat(captor.getValue().getBeschreibung()).contains("Kassenüberschuss");
    }

    @Test
    @DisplayName("Zählzettel wird summiert und schlägt die eingetippte Endsumme")
    void zaehlzettelGewinnt() {
        offenerMonat();
        given(kasseSaldoService.berechneSaldoBis(any())).willReturn(new BigDecimal("143.50"));

        Map<String, Integer> stueckelung = new LinkedHashMap<>();
        stueckelung.put("50", 2);      // 100,00
        stueckelung.put("20", 2);      //  40,00
        stueckelung.put("2", 1);       //   2,00
        stueckelung.put("0.50", 3);    //   1,50
        stueckelung.put("5", 0);       // faellt raus

        // Absichtlich eine falsche Endsumme daneben: gezaehlt ist gezaehlt.
        KassenzaehlungDto.Response r = service.zaehle(
                request("999.00", stueckelung, null, false), maxMustermann, null);

        assertThat(r.getGezaehlterBestand()).isEqualByComparingTo("143.50");
        assertThat(r.getDifferenz()).isEqualByComparingTo("0.00");
        assertThat(r.getStueckelungJson()).isEqualTo("{\"50\":2,\"20\":2,\"2\":1,\"0.5\":3}");
    }

    @Test
    @DisplayName("Unsinnige Werte im Zählzettel werden abgelehnt")
    void ungueltigerZaehlzettel() {
        offenerMonat();

        assertThatThrownBy(() -> service.zaehle(
                request(null, Map.of("Schein", 3), null, false), maxMustermann, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gültiger Geldschein");

        assertThatThrownBy(() -> service.zaehle(
                request(null, Map.of("20", -1), null, false), maxMustermann, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativ");
    }

    @Test
    @DisplayName("Ein negativer Kassenbestand ist unmöglich")
    void negativerBestandWirdAbgelehnt() {
        offenerMonat();

        assertThatThrownBy(() -> service.zaehle(
                request("-5.00", null, "Test", false), maxMustermann, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativer Betrag");
    }

    @Test
    @DisplayName("Für die Zukunft kann kein Bargeld gezählt werden")
    void zukunftWirdAbgelehnt() {
        KassenzaehlungDto.ZaehlRequest req = request("100.00", null, null, false);
        req.setStichtag(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> service.zaehle(req, maxMustermann, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Zukunft");
    }

    @Test
    @DisplayName("In einem abgeschlossenen Monat kann nicht mehr gezählt werden")
    void abgeschlossenerMonatWirdAbgelehnt() {
        given(abschlussRepository.existsByJahrAndMonat(any(), any())).willReturn(true);

        assertThatThrownBy(() -> service.zaehle(
                request("100.00", null, null, false), maxMustermann, null))
                .isInstanceOf(KassenbuchGesperrtException.class)
                .hasMessageContaining("bereits abgeschlossen");
    }

    @Test
    @DisplayName("Ohne Betrag und ohne Zählzettel geht nichts")
    void ohneAngabenWirdAbgelehnt() {
        offenerMonat();

        assertThatThrownBy(() -> service.zaehle(
                request(null, null, null, false), maxMustermann, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gezählten Betrag");
    }

    @Test
    @DisplayName("Jede Zählung landet im Protokoll")
    void zaehlungWirdProtokolliert() {
        offenerMonat();
        given(kasseSaldoService.berechneSaldoBis(any())).willReturn(new BigDecimal("100.00"));

        service.zaehle(request("100.00", null, null, false), maxMustermann, null);

        verify(auditService).protokolliereKassenzaehlung(any(), any(), any());
    }

    // ===================== Hilfen =====================

    private void offenerMonat() {
        lenient().when(abschlussRepository.existsByJahrAndMonat(any(), any())).thenReturn(false);
    }

    private KassenzaehlungDto.ZaehlRequest request(String betrag, Map<String, Integer> stueckelung,
                                                   String bemerkung, boolean differenzBuchen) {
        KassenzaehlungDto.ZaehlRequest r = new KassenzaehlungDto.ZaehlRequest();
        r.setStichtag(LocalDate.now());
        r.setGezaehlterBestand(betrag == null ? null : new BigDecimal(betrag));
        r.setStueckelung(stueckelung);
        r.setBemerkung(bemerkung);
        r.setDifferenzBuchen(differenzBuchen);
        return r;
    }
}
