package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.domain.AusgelesenePosition;
import org.example.kalkulationsprogramm.domain.LieferantDokumentPosition;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.PositionsArt;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition;
import org.example.kalkulationsprogramm.repository.LieferantDokumentPositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class LieferantDokumentPositionServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private LieferantDokumentPositionRepository repository;

    private LieferantDokumentPositionService service;

    @BeforeEach
    void setUp() {
        service = new LieferantDokumentPositionService(repository);
    }

    private static LieferantGeschaeftsdokument gd() {
        LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
        gd.setId(42L);
        return gd;
    }

    private static AusgelesenePosition ware(String text, String gesamt) {
        return new AusgelesenePosition(PositionsArt.WARE, null, text, BigDecimal.ONE, "Stück", null, null,
                gesamt != null ? new BigDecimal(gesamt) : null);
    }

    @Nested
    class Speichern {

        @SuppressWarnings("unchecked")
        @Test
        void ersetztPositionenInBelegreihenfolge() {
            int anzahl = service.ersetzePositionen(gd(), LieferantDokumentTyp.RECHNUNG,
                    List.of(ware("Flachstahl 50x5", "12.50"), ware("Rundrohr", "8")));

            assertThat(anzahl).isEqualTo(2);
            verify(repository).deleteByGeschaeftsdokumentId(42L);
            ArgumentCaptor<List<LieferantDokumentPosition>> captor = ArgumentCaptor.forClass(List.class);
            verify(repository).saveAll(captor.capture());
            assertThat(captor.getValue()).extracting(LieferantDokumentPosition::getPositionNr).containsExactly(1, 2);
            assertThat(captor.getValue().getFirst().getBezeichnung()).isEqualTo("Flachstahl 50x5");
        }

        @Test
        void ohneAusleseBleibtAllesWieEsIst() {
            assertThat(service.ersetzePositionen(gd(), LieferantDokumentTyp.RECHNUNG, null)).isZero();
            verifyNoInteractions(repository);
        }

        @ParameterizedTest
        @EnumSource(value = LieferantDokumentTyp.class, names = { "SONSTIG", "BELEG" })
        void formulareBekommenKeinePositionen(LieferantDokumentTyp typ) {
            assertThat(service.ersetzePositionen(gd(), typ, List.of(ware("Formularfeld", "1")))).isZero();
            verify(repository).deleteByGeschaeftsdokumentId(42L);
            verify(repository, never()).saveAll(anyList());
        }

        @ParameterizedTest
        @EnumSource(value = LieferantDokumentTyp.class,
                names = { "ANGEBOT", "AUFTRAGSBESTAETIGUNG", "LIEFERSCHEIN", "RECHNUNG", "GUTSCHRIFT" })
        void geschaeftsdokumenteHabenPositionen(LieferantDokumentTyp typ) {
            assertThat(LieferantDokumentPositionService.hatPositionen(typ)).isTrue();
        }

        @SuppressWarnings("unchecked")
        @Test
        void ersatzbezeichnungUndSpaltengrenzen() {
            var ohneText = new AusgelesenePosition(PositionsArt.WARE, "MAT-001", null,
                    new BigDecimal("1E20"), "x".repeat(50), new BigDecimal("1.23456"), null,
                    new BigDecimal("99.999"));
            var langerText = ware("A".repeat(600), null);

            service.ersetzePositionen(gd(), LieferantDokumentTyp.ANGEBOT, List.of(ohneText, langerText));

            ArgumentCaptor<List<LieferantDokumentPosition>> captor = ArgumentCaptor.forClass(List.class);
            verify(repository).saveAll(captor.capture());
            LieferantDokumentPosition erste = captor.getValue().getFirst();
            assertThat(erste.getBezeichnung()).isEqualTo("Artikel MAT-001");
            assertThat(erste.getMenge()).isNull(); // passt nicht in DECIMAL(15,3)
            assertThat(erste.getMengeneinheit()).hasSize(20);
            assertThat(erste.getEinzelpreis()).isEqualByComparingTo("1.2346");
            assertThat(erste.getGesamtpreisNetto()).isEqualByComparingTo("100.00");
            assertThat(captor.getValue().get(1).getBezeichnung()).hasSize(500);
        }
    }

    @Nested
    class KiAntwortLesen {

        @Test
        void liestAlleFelder() throws Exception {
            var json = mapper.readTree("""
                    {"artikelPositionen":[
                      {"positionNr":1,"positionsArt":"WARE","externeArtikelnummer":"S235-100",
                       "bezeichnung":"Flachstahl 50x5","menge":12,"mengeneinheit":"m",
                       "einzelpreis":"4,50","preiseinheit":"€/m","gesamtpreisNetto":"1.054,00"},
                      {"positionsArt":"NEBENKOSTEN","bezeichnung":"Fracht","gesamtpreisNetto":45},
                      {"bezeichnung":"Sonderrabatt","gesamtpreisNetto":-20}
                    ]}""");

            List<AusgelesenePosition> positionen = LieferantDokumentPositionService.ausKiAntwort(json);

            assertThat(positionen).hasSize(3);
            assertThat(positionen.get(0).einzelpreis()).isEqualByComparingTo("4.50");
            assertThat(positionen.get(0).gesamtpreisNetto()).isEqualByComparingTo("1054.00");
            assertThat(positionen.get(1).positionsArt()).isEqualTo(PositionsArt.NEBENKOSTEN);
            // Ohne Art, aber negativ: Rabatt
            assertThat(positionen.get(2).positionsArt()).isEqualTo(PositionsArt.RABATT);
        }

        @Test
        void altesFormatNurMitArtikelnummer() throws Exception {
            var json = mapper.readTree("""
                    {"artikelPositionen":[{"externeArtikelnummer":"100-200","einzelpreis":12.34}]}""");

            var positionen = LieferantDokumentPositionService.ausKiAntwort(json);

            assertThat(positionen).singleElement()
                    .satisfies(p -> assertThat(p.externeArtikelnummer()).isEqualTo("100-200"));
        }

        @Test
        void fehlendeListeIstNichtAusgelesen() throws Exception {
            assertThat(LieferantDokumentPositionService.ausKiAntwort(mapper.readTree("{}"))).isNull();
            assertThat(LieferantDokumentPositionService.ausKiAntwort(null)).isNull();
            assertThat(LieferantDokumentPositionService.ausKiAntwort(
                    mapper.readTree("{\"artikelPositionen\":[]}"))).isEmpty();
        }

        @Test
        void leereZeilenUndUnsinnWerdenUebersprungen() throws Exception {
            var json = mapper.readTree("""
                    {"artikelPositionen":[{}, "Text", {"menge":"viel","bezeichnung":"Blech"}]}""");

            var positionen = LieferantDokumentPositionService.ausKiAntwort(json);

            assertThat(positionen).singleElement().satisfies(p -> {
                assertThat(p.bezeichnung()).isEqualTo("Blech");
                assertThat(p.menge()).isNull();
            });
        }
    }

    @Test
    void zugferdPositionen() {
        var pos = new ZugferdArtikelPosition("A-1", "Glasklemme", new BigDecimal("4"), "C62",
                new BigDecimal("12.00"), "1 C62", new BigDecimal("48.00"));
        var rabatt = new ZugferdArtikelPosition(null, "Rabatt", null, null, null, null, new BigDecimal("-5"));

        var positionen = LieferantDokumentPositionService.ausZugferd(List.of(pos, rabatt));

        assertThat(positionen.get(0).gesamtpreisNetto()).isEqualByComparingTo("48.00");
        assertThat(positionen.get(1).positionsArt()).isEqualTo(PositionsArt.RABATT);
        assertThat(LieferantDokumentPositionService.ausZugferd(null)).isNull();
    }

    @Test
    void positionenInKiAntwortZurueckschreiben() throws Exception {
        String neu = LieferantDokumentPositionService.mitPositionen(
                "{\"dokumentNummer\":\"RE-1\",\"artikelPositionen\":[]}",
                List.of(ware("Flachstahl", "10")), mapper);

        var json = mapper.readTree(neu);
        assertThat(json.get("dokumentNummer").asText()).isEqualTo("RE-1");
        assertThat(LieferantDokumentPositionService.ausKiAntwort(json)).hasSize(1);
        assertThat(LieferantDokumentPositionService.mitPositionen(null, List.of(), mapper)).isNull();
        assertThat(LieferantDokumentPositionService.mitPositionen("kein json", List.of(), mapper)).isNull();
    }

    @Test
    void betragImDeutschenFormat() {
        var text = mapper.getNodeFactory();
        assertThat(LieferantDokumentPositionService.betrag(text.textNode("1.234,56 €"))).isEqualByComparingTo("1234.56");
        assertThat(LieferantDokumentPositionService.betrag(text.textNode("12.5"))).isEqualByComparingTo("12.5");
        assertThat(LieferantDokumentPositionService.betrag(text.textNode(""))).isNull();
        assertThat(LieferantDokumentPositionService.betrag(text.textNode("9".repeat(40)))).isNull();
        assertThat(LieferantDokumentPositionService.betrag(text.nullNode())).isNull();
    }

    @Test
    void ausreisserAusDerKiWerdenVerworfen() throws Exception {
        var text = mapper.getNodeFactory();
        // Riesiger Exponent: darf nie gerundet werden (Milliarde Stellen)
        assertThat(LieferantDokumentPositionService.betrag(text.textNode("1e999999999"))).isNull();
        assertThat(LieferantDokumentPositionService.betrag(text.textNode("1e-999999999"))).isNull();
        // Viele Nachkommastellen: gerundet, nicht verworfen
        assertThat(LieferantDokumentPositionService.betrag(text.numberNode(0.8333333333333334)))
                .isEqualByComparingTo("0.8333333333");
        assertThat(LieferantDokumentPositionService.vernuenftig(new BigDecimal("1." + "3".repeat(60)))).isNull();
        assertThat(LieferantDokumentPositionService.betrag(text.numberNode(Double.POSITIVE_INFINITY))).isNull();
        assertThat(LieferantDokumentPositionService.betrag(text.numberNode(Double.NaN))).isNull();
        assertThat(LieferantDokumentPositionService.passend(new BigDecimal("1e999999999"), 15, 2)).isNull();
        // JSON-Literal 1e400 wird zu Infinity – die Liste bleibt trotzdem lesbar
        var json = mapper.readTree("{\"artikelPositionen\":[{\"bezeichnung\":\"Blech\",\"menge\":1e400}]}");
        assertThat(LieferantDokumentPositionService.ausKiAntwort(json))
                .singleElement().satisfies(p -> assertThat(p.menge()).isNull());
    }
}
