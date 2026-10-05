package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RechnungsVorschlagServiceTest {

    @Mock
    private LieferantDokumentRepository dokumentRepository;

    private RechnungsVorschlagService service;
    private Lieferanten lieferant;
    private Lieferanten andererLieferant;
    private long naechsteId = 1;

    @BeforeEach
    void setUp() {
        service = new RechnungsVorschlagService(new LieferantDokumentAbgleich(new ObjectMapper()), dokumentRepository);
        lieferant = lieferant(1L, "Max Mustermann GmbH");
        andererLieferant = lieferant(2L, "Erika Musterfrau KG");
    }

    @Nested
    class Einschaetzung {

        @Test
        void belegnummerAufDerRechnungIstSicher() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-77001", LocalDate.of(2026, 4, 1));
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-9", LocalDate.of(2026, 4, 20));
            rechnung.getGeschaeftsdaten().setReferenzNummer("AB 77001");

            var vorschlag = service.bewerte(List.of(ab), List.of(rechnung), service.neuerSpeicher()).get(0);

            assertThat(vorschlag.trefferquote()).isGreaterThanOrEqualTo(LieferantDokumentAbgleich.QUOTE_SICHER);
            assertThat(vorschlag.einschaetzung().sicher()).isTrue();
            assertThat(vorschlag.einschaetzung().gruende())
                    .contains("Gleicher Lieferant", "Belegnummer wird genannt", "19 Tage danach");
        }

        @Test
        void gleicheBestellnummerIstSicher() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 4, 1));
            ab.getGeschaeftsdaten().setBestellnummer("B-4711");
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 4, 10));
            rechnung.getGeschaeftsdaten().setBestellnummer("b 4711");

            var vorschlag = service.bewerte(List.of(ab), List.of(rechnung), service.neuerSpeicher()).get(0);

            assertThat(vorschlag.trefferquote()).isGreaterThanOrEqualTo(LieferantDokumentAbgleich.QUOTE_SICHER);
            assertThat(vorschlag.einschaetzung().gruende()).contains("Gleiche Bestellnummer");
        }

        @Test
        void bestellnummerBeiZuVielenRechnungenNurHinweis() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 4, 1));
            ab.getGeschaeftsdaten().setBestellnummer("KD-12345");
            List<LieferantDokument> rechnungen = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                LieferantDokument r = dokument(LieferantDokumentTyp.RECHNUNG, "RE-" + i, LocalDate.of(2026, 4, 10 + i));
                r.getGeschaeftsdaten().setBestellnummer("KD-12345");
                rechnungen.add(r);
            }

            var vorschlaege = service.bewerte(List.of(ab), rechnungen, service.neuerSpeicher());

            assertThat(vorschlaege).hasSize(4).allSatisfy(v -> {
                assertThat(v.einschaetzung().sicher()).isFalse();
                assertThat(v.trefferquote()).isLessThan(LieferantDokumentAbgleich.QUOTE_SICHER);
                assertThat(v.einschaetzung().gruende()).contains("Gleiche Bestellnummer (bei mehreren Belegen)");
            });
        }

        @Test
        void gleicherBetragMitPassendemDatumIstWahrscheinlich() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 4, 1));
            ab.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("1234.50"));
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 4, 15));
            rechnung.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("1234.50"));

            var vorschlag = service.bewerte(List.of(ab), List.of(rechnung), service.neuerSpeicher()).get(0);

            assertThat(vorschlag.trefferquote()).isBetween(RechnungsVorschlagService.MIN_QUOTE_KARTE, 94);
            assertThat(vorschlag.einschaetzung().sicher()).isFalse();
            assertThat(vorschlag.einschaetzung().gruende()).contains("Gleicher Betrag");
        }

        @Test
        void ohneDatumZaehltEinHinweisWeniger() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 4, 1));
            ab.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("99.00"));
            LieferantDokument mitDatum = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 4, 15));
            mitDatum.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("99.00"));
            LieferantDokument ohneDatum = dokument(LieferantDokumentTyp.RECHNUNG, "RE-2", null);
            ohneDatum.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("99.00"));

            var vorschlaege = service.bewerte(List.of(ab), List.of(ohneDatum, mitDatum), service.neuerSpeicher());

            assertThat(vorschlaege.get(0).rechnung()).isSameAs(mitDatum);
            assertThat(vorschlaege.get(1).trefferquote()).isLessThan(vorschlaege.get(0).trefferquote());
            assertThat(vorschlaege.get(1).einschaetzung().gruende()).contains("Datum fehlt");
        }

        @Test
        void datumAusserhalbDerKetteZaehltKeineHinweise() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 4, 1));
            ab.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("50.00"));
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2025, 1, 1));
            rechnung.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("50.00"));

            var vorschlag = service.bewerte(List.of(ab), List.of(rechnung), service.neuerSpeicher()).get(0);

            assertThat(vorschlag.trefferquote()).isZero();
            assertThat(vorschlag.einschaetzung().gruende()).contains("Datum passt nicht zur Bestellung");
        }

        @Test
        void andererLieferantBekommtAbzug() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-77001", LocalDate.of(2026, 4, 1));
            LieferantDokument fremd = dokument(LieferantDokumentTyp.RECHNUNG, "RE-9", LocalDate.of(2026, 4, 20));
            fremd.setLieferant(andererLieferant);
            fremd.getGeschaeftsdaten().setReferenzNummer("AB-77001");

            var vorschlag = service.bewerte(List.of(ab), List.of(fremd), service.neuerSpeicher()).get(0);

            assertThat(vorschlag.trefferquote()).isLessThan(LieferantDokumentAbgleich.QUOTE_SICHER);
            assertThat(vorschlag.einschaetzung().sicher()).isFalse();
            assertThat(vorschlag.einschaetzung().gruende()).contains("Anderer Lieferant");
        }
    }

    @Nested
    class Auswahl {

        @Test
        void besteRechnungZuerstUndJeRechnungDasPassendsteBestelldokument() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 4, 1));
            LieferantDokument lieferschein = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-5", LocalDate.of(2026, 4, 8));
            LieferantDokument passend = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 4, 20));
            passend.getGeschaeftsdaten().setReferenzNummer("LS-5");
            LieferantDokument unpassend = dokument(LieferantDokumentTyp.RECHNUNG, "RE-2", LocalDate.of(2026, 4, 21));

            var vorschlaege = service.bewerte(List.of(ab, lieferschein), List.of(unpassend, passend), service.neuerSpeicher());

            assertThat(vorschlaege).extracting(RechnungsVorschlagService.Vorschlag::rechnung)
                    .containsExactly(passend, unpassend);
            assertThat(vorschlaege.get(0).bestellDokument()).isSameAs(lieferschein);
        }

        @Test
        void ohneBestelldokumentKeineVorschlaege() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LocalDate.of(2026, 4, 1));
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 4, 2));

            assertThat(service.bewerte(List.of(angebot), List.of(rechnung), service.neuerSpeicher())).isEmpty();
        }

        @Test
        void nurRechnungenWerdenBewertet() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 4, 1));
            LieferantDokument gutschrift = dokument(LieferantDokumentTyp.GUTSCHRIFT, "GS-1", LocalDate.of(2026, 4, 2));

            assertThat(service.bewerte(List.of(ab), List.of(gutschrift), service.neuerSpeicher())).isEmpty();
        }

        @Test
        void besterVorschlagErstAbMindestquote() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 4, 1));
            LieferantDokument nurZeitlichNah = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 4, 5));

            assertThat(service.besterVorschlag(List.of(ab), List.of(nurZeitlichNah), service.neuerSpeicher())).isEmpty();
        }

        @Test
        void gleichstandIstNichtEindeutig() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 4, 1));
            ab.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("10.00"));
            LieferantDokument a = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 4, 5));
            a.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("10.00"));
            LieferantDokument b = dokument(LieferantDokumentTyp.RECHNUNG, "RE-2", LocalDate.of(2026, 4, 6));
            b.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("10.00"));

            var bester = service.besterVorschlag(List.of(ab), List.of(a, b), service.neuerSpeicher());

            assertThat(bester).isPresent();
            assertThat(bester.get().eindeutig()).isFalse();
        }

        @Test
        void klarerTrefferIstEindeutig() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-77001", LocalDate.of(2026, 4, 1));
            LieferantDokument treffer = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 4, 5));
            treffer.getGeschaeftsdaten().setReferenzNummer("AB-77001");
            LieferantDokument anderer = dokument(LieferantDokumentTyp.RECHNUNG, "RE-2", LocalDate.of(2026, 4, 6));

            var bester = service.besterVorschlag(List.of(ab), List.of(anderer, treffer), service.neuerSpeicher());

            assertThat(bester).isPresent();
            assertThat(bester.get().vorschlag().rechnung()).isSameAs(treffer);
            assertThat(bester.get().eindeutig()).isTrue();
        }
    }

    @Nested
    class Verknuepfen {

        @Test
        void haengtRechnungAnBestellung() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 4, 1));
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 4, 5));
            when(dokumentRepository.findById(ab.getId())).thenReturn(Optional.of(ab));
            when(dokumentRepository.findById(rechnung.getId())).thenReturn(Optional.of(rechnung));

            service.verknuepfe(ab.getId(), rechnung.getId(), 7L);

            assertThat(rechnung.getVerknuepfteDokumente()).containsExactly(ab);
            assertThat(ab.getVerknuepftVon()).containsExactly(rechnung);
            verify(dokumentRepository).save(rechnung);
        }

        @Test
        void lehntFalscheTypenAb() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LocalDate.of(2026, 4, 1));
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 4, 1));
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 4, 5));
            when(dokumentRepository.findById(angebot.getId())).thenReturn(Optional.of(angebot));
            when(dokumentRepository.findById(ab.getId())).thenReturn(Optional.of(ab));
            when(dokumentRepository.findById(rechnung.getId())).thenReturn(Optional.of(rechnung));

            assertThatThrownBy(() -> service.verknuepfe(angebot.getId(), rechnung.getId(), 7L))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.verknuepfe(rechnung.getId(), ab.getId(), 7L))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(dokumentRepository, never()).save(any());
        }

        @Test
        void lehntGleicheOderFehlendeIdsAb() {
            assertThatThrownBy(() -> service.verknuepfe(5L, 5L, 7L)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.verknuepfe(null, 5L, 7L)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void lehntSchonZugeordneteRechnungAb() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 4, 1));
            LieferantDokument andereAb = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-2", LocalDate.of(2026, 4, 2));
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 4, 5));
            rechnung.getVerknuepfteDokumente().add(andereAb);
            when(dokumentRepository.findById(ab.getId())).thenReturn(Optional.of(ab));
            when(dokumentRepository.findById(rechnung.getId())).thenReturn(Optional.of(rechnung));

            assertThatThrownBy(() -> service.verknuepfe(ab.getId(), rechnung.getId(), 7L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(RechnungsVorschlagService.SCHON_ZUGEORDNET);
            verify(dokumentRepository, never()).save(any());
        }

        @Test
        void erkenntBestellungAuchUeberUmwegInDerKette() {
            // Rechnung -> Angebot <- Lieferschein: Der Lieferschein gehört schon zur Kette.
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 4, 1));
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LocalDate.of(2026, 3, 1));
            LieferantDokument lieferschein = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-1", LocalDate.of(2026, 3, 20));
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 4, 5));
            rechnung.getVerknuepfteDokumente().add(angebot);
            angebot.getVerknuepftVon().add(lieferschein);
            when(dokumentRepository.findById(ab.getId())).thenReturn(Optional.of(ab));
            when(dokumentRepository.findById(rechnung.getId())).thenReturn(Optional.of(rechnung));

            assertThatThrownBy(() -> service.verknuepfe(ab.getId(), rechnung.getId(), 7L))
                    .hasMessage(RechnungsVorschlagService.SCHON_ZUGEORDNET);
        }

        @Test
        void erneutesVerknuepfenMitDerselbenBestellungIstErlaubt() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 4, 1));
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 4, 5));
            rechnung.getVerknuepfteDokumente().add(ab);
            when(dokumentRepository.findById(ab.getId())).thenReturn(Optional.of(ab));
            when(dokumentRepository.findById(rechnung.getId())).thenReturn(Optional.of(rechnung));

            service.verknuepfe(ab.getId(), rechnung.getId(), null);

            assertThat(rechnung.getVerknuepfteDokumente()).containsExactly(ab);
        }

        @Test
        void lehntAusgeblendeteDokumenteAb() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 4, 1));
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 4, 5));
            rechnung.setAusgeblendet(true);
            when(dokumentRepository.findById(ab.getId())).thenReturn(Optional.of(ab));
            when(dokumentRepository.findById(rechnung.getId())).thenReturn(Optional.of(rechnung));

            assertThatThrownBy(() -> service.verknuepfe(ab.getId(), rechnung.getId(), 7L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void andererLieferantIstBewusstErlaubt() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 4, 1));
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 4, 5));
            rechnung.setLieferant(andererLieferant);
            when(dokumentRepository.findById(ab.getId())).thenReturn(Optional.of(ab));
            when(dokumentRepository.findById(rechnung.getId())).thenReturn(Optional.of(rechnung));

            service.verknuepfe(ab.getId(), rechnung.getId(), 7L);

            assertThat(rechnung.getVerknuepfteDokumente()).containsExactly(ab);
        }

        @Test
        void unbekanntesDokument() {
            when(dokumentRepository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.verknuepfe(Long.MAX_VALUE, 1L, 7L)).isInstanceOf(NoSuchElementException.class);
        }
    }

    private LieferantDokument dokument(LieferantDokumentTyp typ, String nummer, LocalDate datum) {
        LieferantDokument dok = new LieferantDokument();
        dok.setId(naechsteId++);
        dok.setTyp(typ);
        dok.setLieferant(lieferant);
        LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
        gd.setDokumentNummer(nummer);
        gd.setDokumentDatum(datum);
        dok.setGeschaeftsdaten(gd);
        return dok;
    }

    private static Lieferanten lieferant(long id, String name) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        l.setLieferantenname(name);
        return l;
    }
}
