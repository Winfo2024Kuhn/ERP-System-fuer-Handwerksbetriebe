package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelStammdatenRequest;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ArtikelStammdatenServiceTest {

    @Mock ArtikelRepository artikelRepository;
    @Mock KategorieRepository kategorieRepository;
    @Mock WerkstoffRepository werkstoffRepository;
    @InjectMocks ArtikelService service;

    private ArtikelWerkstoffe artikel;

    @BeforeEach
    void setUp() {
        artikel = new ArtikelWerkstoffe();
        ReflectionTestUtils.setField(artikel, "id", 7L);
        ReflectionTestUtils.setField(artikel, "version", 3L);
        artikel.setProduktname("Alt");
        artikel.setBeschreibung("<p>Kundentext</p>");
    }

    private void gefunden() {
        when(artikelRepository.findById(7L)).thenReturn(Optional.of(artikel));
    }

    private void speichernAktiv() {
        when(artikelRepository.save(any(Artikel.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static ArtikelStammdatenRequest anfrage() {
        ArtikelStammdatenRequest r = new ArtikelStammdatenRequest();
        r.setVersion(3L);
        r.setProduktname("Rundrohr 42,4 x 2");
        return r;
    }

    private static void abgelehnt(Runnable aufruf, HttpStatus status, String teil) {
        assertThatThrownBy(aufruf::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(status);
                    assertThat(e.getReason()).contains(teil);
                });
    }

    @Test
    void ersetztAlleStammdatenUndLaesstTexteUnangetastet() {
        gefunden();
        speichernAktiv();
        Kategorie kategorie = new Kategorie();
        Werkstoff werkstoff = new Werkstoff();
        when(kategorieRepository.findById(5)).thenReturn(Optional.of(kategorie));
        when(werkstoffRepository.findById(2L)).thenReturn(Optional.of(werkstoff));
        ArtikelStammdatenRequest r = anfrage();
        r.setProduktlinie("Geländer");
        r.setArtikelnummer("  ST-RR-042 ");
        r.setEan("4006381333931");
        r.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);
        r.setKategorieId(5L);
        r.setWerkstoffId(2L);
        r.setProfilform(Profilform.RUNDROHR);
        r.setDurchmesser(new BigDecimal("42.40"));
        r.setWandstaerke(new BigDecimal("2.00"));
        r.setStandardlaenge(6000);

        Artikel e = service.aktualisiereStammdaten(7L, r);

        assertThat(e.getProduktname()).isEqualTo("Rundrohr 42,4 x 2");
        assertThat(e.getProduktlinie()).isEqualTo("Geländer");
        assertThat(e.getArtikelnummer()).isEqualTo("ST-RR-042");
        assertThat(e.getEan()).isEqualTo("4006381333931");
        assertThat(e.getVerrechnungseinheit()).isEqualTo(Verrechnungseinheit.LAUFENDE_METER);
        assertThat(e.getKategorie()).isSameAs(kategorie);
        assertThat(e.getWerkstoff()).isSameAs(werkstoff);
        assertThat(((ArtikelWerkstoffe) e).getDurchmesser()).isEqualByComparingTo("42.4");
        assertThat(((ArtikelWerkstoffe) e).getStandardlaenge()).isEqualTo(6000);
        assertThat(e.getBeschreibung()).isEqualTo("<p>Kundentext</p>");
    }

    @Test
    void wasFehltWirdGeleert() {
        artikel.setProduktlinie("Alt");
        artikel.setArtikelnummer("A-1");
        artikel.setEan("4006381333931");
        artikel.setDurchmesser(new BigDecimal("10"));
        gefunden();
        speichernAktiv();

        Artikel e = service.aktualisiereStammdaten(7L, anfrage());

        assertThat(e.getProduktlinie()).isNull();
        assertThat(e.getArtikelnummer()).isNull();
        assertThat(e.getEan()).isNull();
        assertThat(((ArtikelWerkstoffe) e).getDurchmesser()).isNull();
        assertThat(e.getKategorie()).isNull();
        assertThat(e.getWerkstoff()).isNull();
    }

    @Test
    void baueSuchtextNeuAuf() {
        gefunden();
        speichernAktiv();
        ArtikelStammdatenRequest r = anfrage();
        r.setProfilform(Profilform.RUNDROHR);
        r.setArtikelnummer("ST-RR-042");
        r.setEan("4006381333931");
        r.setDurchmesser(new BigDecimal("42.40"));

        Artikel e = service.aktualisiereStammdaten(7L, r);

        assertThat(e.getSuchtext())
                .contains("rundrohr", "st-rr-042", "4006381333931", "42.4", "42,4");
    }

    @Test
    void ohneProduktnameWirdAbgelehnt() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setProduktname("   ");

        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Produktnamen");
        verify(artikelRepository, never()).save(any());
    }

    @Test
    void ueberlangeTexteWerdenAbgelehnt() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setProduktname("x".repeat(256));

        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Der Produktname darf höchstens 255");

        // So lang ist die Spalte in der Datenbank: mehr als 255 Zeichen müssen vorher mit Klartext abgelehnt werden
        r.setProduktname("ok");
        r.setProdukttext("y".repeat(256));
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Die Beschreibung darf höchstens 255");
    }

    @Test
    void produkttextMitGenau255ZeichenIstErlaubt() {
        gefunden();
        speichernAktiv();
        ArtikelStammdatenRequest r = anfrage();
        r.setProdukttext("y".repeat(255));

        assertThat(service.aktualisiereStammdaten(7L, r).getProdukttext()).hasSize(255);
    }

    @Test
    void ohneStandWirdAbgelehntDamitNichtStillUeberschriebenWird() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setVersion(null);

        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.CONFLICT, "neu laden");
        verify(artikelRepository, never()).save(any());
    }

    @Test
    void zuGrosseOderNegativeKategorieIdGibt400StattServerfehler() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setKategorieId(Integer.MAX_VALUE + 1L);
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Kategorie");

        r.setKategorieId(-1L);
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Kategorie");
        r.setKategorieId(Long.MAX_VALUE);
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Kategorie");
    }

    @Test
    void sonderzeichenWerdenAlsTextGespeichertNichtAusgefuehrt() {
        gefunden();
        speichernAktiv();
        ArtikelStammdatenRequest r = anfrage();
        r.setProduktname("<script>alert(1)</script>");
        r.setProduktlinie("'; DROP TABLE artikel; --");

        Artikel e = service.aktualisiereStammdaten(7L, r);

        // Der Wert bleibt Text; Ausgabe und Abfragen sind parametrisiert bzw. von React escaped
        assertThat(e.getProduktname()).isEqualTo("<script>alert(1)</script>");
        assertThat(e.getProduktlinie()).isEqualTo("'; DROP TABLE artikel; --");
    }

    @Test
    void doppelteArtikelnummerGibt409() {
        gefunden();
        // Ein anderer Artikel hat "a-1" - Gross-/Kleinschreibung zaehlt nicht.
        when(artikelRepository.existsByArtikelnummerIgnoreCaseAndIdNot("A-1", 7L)).thenReturn(true);
        ArtikelStammdatenRequest r = anfrage();
        r.setArtikelnummer("A-1");

        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.CONFLICT, "Artikelnummer A-1");
    }

    @Test
    void eigeneArtikelnummerUndEanDuerfenStehenBleiben() {
        gefunden();
        speichernAktiv();
        when(artikelRepository.existsByArtikelnummerIgnoreCaseAndIdNot("A-1", 7L)).thenReturn(false);
        when(artikelRepository.findByEan("4006381333931")).thenReturn(Optional.of(artikel));
        ArtikelStammdatenRequest r = anfrage();
        r.setArtikelnummer("A-1");
        r.setEan("4006381333931");

        Artikel e = service.aktualisiereStammdaten(7L, r);

        assertThat(e.getArtikelnummer()).isEqualTo("A-1");
        assertThat(e.getEan()).isEqualTo("4006381333931");
    }

    @Test
    void eanMitFalscherPruefzifferGibt400() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setEan("4006381333932");

        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Prüfziffer");
    }

    @Test
    void doppelteEanGibt409() {
        gefunden();
        Artikel anderer = new Artikel();
        anderer.setId(8L);
        when(artikelRepository.findByEan("4006381333931")).thenReturn(Optional.of(anderer));
        ArtikelStammdatenRequest r = anfrage();
        r.setEan("4006381333931");

        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.CONFLICT, "EAN 4006381333931");
    }

    @Test
    void veralteterStandGibt409() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setVersion(2L);

        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.CONFLICT, "neu laden");
        verify(artikelRepository, never()).save(any());
    }

    @Test
    void unbekannteKategorieUndWerkstoffGeben400() {
        gefunden();
        when(kategorieRepository.findById(99)).thenReturn(Optional.empty());
        ArtikelStammdatenRequest r = anfrage();
        r.setKategorieId(99L);
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Kategorie");

        r.setKategorieId(null);
        r.setWerkstoffId(99L);
        when(werkstoffRepository.findById(99L)).thenReturn(Optional.empty());
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Werkstoff");
    }

    @Test
    void masseUndLaengeHabenGrenzen() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setDurchmesser(new BigDecimal("-1"));
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Der Durchmesser");

        r.setDurchmesser(null);
        r.setHoehe(new BigDecimal("100001"));
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Die Höhe");

        r.setHoehe(null);
        r.setStandardlaenge(0);
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Standardlänge");
        r.setStandardlaenge(30_001);
        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Standardlänge");
    }

    @Test
    void negativeVerpackungseinheitWirdAbgelehnt() {
        gefunden();
        ArtikelStammdatenRequest r = anfrage();
        r.setVerpackungseinheit(-1L);

        abgelehnt(() -> service.aktualisiereStammdaten(7L, r), HttpStatus.BAD_REQUEST, "Verpackungseinheit");
    }

    @Test
    void artikelOhneHalbzeugDatenIgnorierenMasse() {
        Artikel einfach = new Artikel();
        ReflectionTestUtils.setField(einfach, "id", 9L);
        einfach.setProduktname("Schraube");
        when(artikelRepository.findById(9L)).thenReturn(Optional.of(einfach));
        speichernAktiv();
        ArtikelStammdatenRequest r = anfrage();
        r.setVersion(null);
        r.setDurchmesser(new BigDecimal("8"));

        Artikel e = service.aktualisiereStammdaten(9L, r);

        assertThat(e).isNotInstanceOf(ArtikelWerkstoffe.class);
        assertThat(e.getProduktname()).isEqualTo("Rundrohr 42,4 x 2");
    }

    @Test
    void unbekannteIdGibt404() {
        when(artikelRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.aktualisiereStammdaten(999L, anfrage())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void eignungFuerVerzinkenUndPulvernLaesstSichProArtikelFestlegenOderVomWerkstoffUebernehmen() {
        gefunden();
        speichernAktiv();
        Werkstoff stahl = new Werkstoff();
        stahl.setVerzinkungsgeeignet(true);
        stahl.setPulverbeschichtungsgeeignet(false);
        when(werkstoffRepository.findById(2L)).thenReturn(Optional.of(stahl));
        ArtikelStammdatenRequest r = anfrage();
        r.setWerkstoffId(2L);

        // Ohne Angabe gilt der Werkstoff (Verzinken ja, Pulvern nein)
        Artikel e = service.aktualisiereStammdaten(7L, r);
        assertThat(e.getVerzinkungsgeeignet()).isNull();
        assertThat(e.getPulverbeschichtungsgeeignet()).isNull();
        assertThat(e.istVerzinkungsgeeignet()).isTrue();
        assertThat(e.istPulverbeschichtungsgeeignet()).isFalse();

        // Für diesen Artikel festgelegt: überstimmt den Werkstoff, in beide Richtungen
        r.setVerzinkungsgeeignet(false);
        r.setPulverbeschichtungsgeeignet(true);
        e = service.aktualisiereStammdaten(7L, r);
        assertThat(e.istVerzinkungsgeeignet()).isFalse();
        assertThat(e.istPulverbeschichtungsgeeignet()).isTrue();

        // Wieder leer = zurück zum Werkstoff
        r.setVerzinkungsgeeignet(null);
        r.setPulverbeschichtungsgeeignet(null);
        e = service.aktualisiereStammdaten(7L, r);
        assertThat(e.istVerzinkungsgeeignet()).isTrue();
        assertThat(e.istPulverbeschichtungsgeeignet()).isFalse();
    }
}
