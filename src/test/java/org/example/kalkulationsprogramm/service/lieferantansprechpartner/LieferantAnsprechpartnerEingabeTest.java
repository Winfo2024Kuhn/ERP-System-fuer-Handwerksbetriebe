package org.example.kalkulationsprogramm.service.lieferantansprechpartner;

import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantAnsprechpartnerRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LieferantAnsprechpartnerEingabeTest {

    private static LieferantAnsprechpartnerRequestDto anfrage(String nachname) {
        LieferantAnsprechpartnerRequestDto r = new LieferantAnsprechpartnerRequestDto();
        r.setNachname(nachname);
        return r;
    }

    private static void erwarte400(LieferantAnsprechpartnerRequestDto r, String meldung) {
        assertThatThrownBy(() -> LieferantAnsprechpartnerEingabe.pruefe(r))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo(meldung);
                });
    }

    @Test
    @DisplayName("Trimmt alle Felder, leere Texte werden null, Anrede wird gelesen")
    void trimmtUndLeertAus() {
        LieferantAnsprechpartnerRequestDto r = anfrage("  Mustermann ");
        r.setAnrede("HERR");
        r.setVorname(" Max ");
        r.setAufgabe("   ");
        r.setTelefon(" 0931 4444-12 ");
        r.setMobiltelefon("");
        r.setEmail(" max.mustermann@example.com ");
        r.setNotiz(" kommt dienstags ");

        LieferantAnsprechpartnerEingabe.Werte w = LieferantAnsprechpartnerEingabe.pruefe(r);

        assertThat(w.anrede()).isEqualTo(Anrede.HERR);
        assertThat(w.vorname()).isEqualTo("Max");
        assertThat(w.nachname()).isEqualTo("Mustermann");
        assertThat(w.aufgabe()).isNull();
        assertThat(w.telefon()).isEqualTo("0931 4444-12");
        assertThat(w.mobiltelefon()).isNull();
        assertThat(w.email()).isEqualTo("max.mustermann@example.com");
        assertThat(w.notiz()).isEqualTo("kommt dienstags");
    }

    @Test
    @DisplayName("Ohne Anrede bleibt sie leer; leere Anrede ebenso")
    void anredeLeer() {
        LieferantAnsprechpartnerRequestDto r = anfrage("Mustermann");
        assertThat(LieferantAnsprechpartnerEingabe.pruefe(r).anrede()).isNull();
        r.setAnrede(" ");
        assertThat(LieferantAnsprechpartnerEingabe.pruefe(r).anrede()).isNull();
    }

    @Test
    @DisplayName("Unbekannte Anrede → 400")
    void anredeUngueltig() {
        LieferantAnsprechpartnerRequestDto r = anfrage("Mustermann");
        r.setAnrede("'; DROP TABLE lieferanten; --");
        erwarte400(r, "Die Anrede ist ungültig.");
    }

    @Test
    @DisplayName("Ohne Nachname → 400 mit Handwerker-Satz")
    void nachnamePflicht() {
        erwarte400(anfrage(null), "Bitte einen Nachnamen eintragen.");
        erwarte400(anfrage("   "), "Bitte einen Nachnamen eintragen.");
    }

    @Test
    @DisplayName("Ohne Anfrage-Körper → 400")
    void ohneKoerper() {
        erwarte400(null, "Bitte einen Nachnamen eintragen.");
    }

    @Test
    @DisplayName("Ungültige E-Mail → 400")
    void emailUngueltig() {
        LieferantAnsprechpartnerRequestDto r = anfrage("Mustermann");
        r.setEmail("max at example");
        erwarte400(r, "Die E-Mail-Adresse ist ungültig.");
        r.setEmail("a?cc=chef@example.com");
        erwarte400(r, "Die E-Mail-Adresse ist ungültig.");
        r.setEmail("max&body=x@example.com");
        erwarte400(r, "Die E-Mail-Adresse ist ungültig.");
    }

    @ParameterizedTest(name = "{0} länger als {1}")
    @CsvSource({
            "Vorname, 100", "Nachname, 255", "Aufgabe, 100", "Telefon, 50",
            "Mobiltelefon, 50", "E-Mail, 255", "Notiz, 1000"})
    @DisplayName("Überlänge je Feld → „<Feld> ist zu lang.“")
    void ueberlaenge(String feld, int max) {
        BiConsumer<LieferantAnsprechpartnerRequestDto, String> setzer = switch (feld) {
            case "Vorname" -> LieferantAnsprechpartnerRequestDto::setVorname;
            case "Nachname" -> LieferantAnsprechpartnerRequestDto::setNachname;
            case "Aufgabe" -> LieferantAnsprechpartnerRequestDto::setAufgabe;
            case "Telefon" -> LieferantAnsprechpartnerRequestDto::setTelefon;
            case "Mobiltelefon" -> LieferantAnsprechpartnerRequestDto::setMobiltelefon;
            case "E-Mail" -> LieferantAnsprechpartnerRequestDto::setEmail;
            default -> LieferantAnsprechpartnerRequestDto::setNotiz;
        };
        LieferantAnsprechpartnerRequestDto genau = anfrage("Mustermann");
        String passend = feld.equals("E-Mail") ? "m".repeat(max - 12) + "@example.com" : "x".repeat(max);
        setzer.accept(genau, passend);
        assertThat(LieferantAnsprechpartnerEingabe.pruefe(genau)).isNotNull();

        LieferantAnsprechpartnerRequestDto zuLang = anfrage("Mustermann");
        setzer.accept(zuLang, "x".repeat(10_001));
        erwarte400(zuLang, feld + " ist zu lang.");
    }

    @Test
    @DisplayName("HTML wird als Text übernommen, nicht verändert")
    void htmlBleibtText() {
        LieferantAnsprechpartnerRequestDto r = anfrage("<script>alert(1)</script>");
        r.setNotiz("<b>fett</b>");
        LieferantAnsprechpartnerEingabe.Werte w = LieferantAnsprechpartnerEingabe.pruefe(r);
        assertThat(w.nachname()).isEqualTo("<script>alert(1)</script>");
        assertThat(w.notiz()).isEqualTo("<b>fett</b>");
    }
}
