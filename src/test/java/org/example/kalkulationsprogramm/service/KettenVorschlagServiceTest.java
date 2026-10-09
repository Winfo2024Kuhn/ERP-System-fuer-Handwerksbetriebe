package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentVerknuepfungSperreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class KettenVorschlagServiceTest {

    private static final LocalDate LIEFERUNG = LocalDate.of(2026, 9, 23);
    private static final java.util.Set<LieferantDokumentTyp> ALLE = java.util.EnumSet.allOf(LieferantDokumentTyp.class);

    @Mock
    private LieferantDokumentRepository dokumentRepository;
    @Mock
    private LieferantDokumentVerknuepfungSperreRepository sperreRepository;

    private KettenVorschlagService service;
    private Lieferanten lieferant;
    private long naechsteId = 1;

    @BeforeEach
    void setUp() {
        service = new KettenVorschlagService(new LieferantDokumentAbgleich(new ObjectMapper()), dokumentRepository,
                sperreRepository);
        lieferant = new Lieferanten();
        lieferant.setId(1L);
        lieferant.setLieferantenname("Max Mustermann GmbH");
    }

    @Nested
    class Richtung {

        @Test
        void zeugnisFolgtLieferschein() {
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-1", LIEFERUNG);
            LieferantDokument zeugnis = dokument(LieferantDokumentTyp.WERKSTOFFZEUGNIS, null, LIEFERUNG);

            var paar = KettenVorschlagService.paar(ls, zeugnis).orElseThrow();

            assertThat(paar.nachfolger()).isSameAs(zeugnis);
            assertThat(paar.vorgaenger()).isSameAs(ls);
        }

        @Test
        void lieferscheinIstVorgaengerDerRechnung() {
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LIEFERUNG);
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-1", LIEFERUNG);

            var paar = KettenVorschlagService.paar(ls, rechnung).orElseThrow();

            assertThat(paar.nachfolger()).isSameAs(rechnung);
            assertThat(paar.vorgaenger()).isSameAs(ls);
        }

        @Test
        void aelteresAngebotIstVorgaenger() {
            LieferantDokument neu = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1-2", LIEFERUNG);
            LieferantDokument alt = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LIEFERUNG.minusDays(10));

            var paar = KettenVorschlagService.paar(neu, alt).orElseThrow();

            assertThat(paar.vorgaenger()).isSameAs(alt);
        }

        @Test
        void angebotUndRechnungPassenNichtDirekt() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LIEFERUNG);
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LIEFERUNG);

            assertThat(KettenVorschlagService.paar(angebot, rechnung)).isEmpty();
            assertThat(KettenVorschlagService.paar(null, rechnung)).isEmpty();
        }
    }

    @Nested
    class Bewerten {

        @Test
        void zeugnisMitLieferscheinnummerStehtVorn() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LIEFERUNG.minusDays(5));
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "90445744/01", LIEFERUNG);
            LieferantDokument passend = dokument(LieferantDokumentTyp.WERKSTOFFZEUGNIS, null, LIEFERUNG.minusDays(1));
            passend.getGeschaeftsdaten().setReferenzNummer("90445744/01");
            LieferantDokument fremd = dokument(LieferantDokumentTyp.WERKSTOFFZEUGNIS, null, LIEFERUNG.minusDays(1));
            fremd.getGeschaeftsdaten().setReferenzNummer("70000001/01");

            var vorschlaege = service.bewerte(List.of(ab, ls), List.of(fremd, passend), null, service.neuerSpeicher());

            assertThat(vorschlaege).extracting(KettenVorschlagService.Vorschlag::dokument).startsWith(passend);
            var erster = vorschlaege.get(0);
            assertThat(erster.kettenDokument()).isSameAs(ls);
            assertThat(erster.einschaetzung().sicher()).isTrue();
            assertThat(erster.einschaetzung().gruende()).contains("Belegnummer wird genannt");
        }

        @Test
        void gleicheBestellnummerInDerEigenenKetteBleibtTrennscharf() {
            // AB, Lieferschein und Rechnung tragen dieselbe Bestellnummer – das macht sie
            // nicht zur Kundennummer. Das Zeugnis mit derselben Nummer bleibt sicher.
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LIEFERUNG.minusDays(5));
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-1", LIEFERUNG);
            LieferantDokument re = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LIEFERUNG.plusDays(3));
            LieferantDokument zeugnis = dokument(LieferantDokumentTyp.WERKSTOFFZEUGNIS, null, LIEFERUNG);
            for (LieferantDokument d : List.of(ab, ls, re, zeugnis)) {
                d.getGeschaeftsdaten().setBestellnummer("BV-4711");
            }

            var vorschlaege = service.bewerte(List.of(ab, ls, re), List.of(zeugnis), List.of(ab, ls, re, zeugnis),
                    service.neuerSpeicher());

            assertThat(vorschlaege.get(0).einschaetzung().sicher()).isTrue();
        }

        @Test
        void unpassendeArtenUndSonstigesFehlen() {
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-1", LIEFERUNG);
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LIEFERUNG);
            LieferantDokument sonstiges = dokument(LieferantDokumentTyp.SONSTIG, "X-1", LIEFERUNG);
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LIEFERUNG);

            var vorschlaege = service.bewerte(List.of(ls), List.of(angebot, sonstiges, rechnung), null,
                    service.neuerSpeicher());

            assertThat(vorschlaege).extracting(KettenVorschlagService.Vorschlag::dokument).containsExactly(rechnung);
        }

        @Test
        void ohneKetteKeineVorschlaege() {
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LIEFERUNG);

            assertThat(service.bewerte(List.of(), List.of(rechnung), null, service.neuerSpeicher())).isEmpty();
        }
    }

    @Nested
    class Verknuepfen {

        @Test
        void haengtZeugnisAnLieferschein() {
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-1", LIEFERUNG);
            LieferantDokument zeugnis = dokument(LieferantDokumentTyp.WERKSTOFFZEUGNIS, null, LIEFERUNG);
            when(dokumentRepository.findById(ls.getId())).thenReturn(Optional.of(ls));
            when(dokumentRepository.findById(zeugnis.getId())).thenReturn(Optional.of(zeugnis));

            service.verknuepfe(ls.getId(), zeugnis.getId(), 7L);

            assertThat(zeugnis.getVerknuepfteDokumente()).containsExactly(ls);
            assertThat(ls.getVerknuepftVon()).containsExactly(zeugnis);
            verify(dokumentRepository).save(zeugnis);
            verify(sperreRepository).loeschePaar(zeugnis.getId(), ls.getId());
        }

        @Test
        void haengtAngebotAlsVorgaengerAnDieAb() {
            // Richtung andersherum: Das gewählte Dokument ist der Vorgänger.
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LIEFERUNG);
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LIEFERUNG.minusDays(20));
            when(dokumentRepository.findById(ab.getId())).thenReturn(Optional.of(ab));
            when(dokumentRepository.findById(angebot.getId())).thenReturn(Optional.of(angebot));

            service.verknuepfe(ab.getId(), angebot.getId(), 7L);

            assertThat(ab.getVerknuepfteDokumente()).containsExactly(angebot);
            verify(dokumentRepository).save(ab);
        }

        @Test
        void schonVerknuepftBleibtUnveraendert() {
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-1", LIEFERUNG);
            LieferantDokument zeugnis = dokument(LieferantDokumentTyp.WERKSTOFFZEUGNIS, null, LIEFERUNG);
            zeugnis.getVerknuepfteDokumente().add(ls);
            when(dokumentRepository.findById(ls.getId())).thenReturn(Optional.of(ls));
            when(dokumentRepository.findById(zeugnis.getId())).thenReturn(Optional.of(zeugnis));

            service.verknuepfe(ls.getId(), zeugnis.getId(), 7L);

            verify(dokumentRepository, never()).save(any());
        }

        @Test
        void verbindungInGegenrichtungBleibtUnveraendert() {
            // Angebots-Fassungen: Ein geändertes Datum darf keinen Kreis A↔B erzeugen.
            LieferantDokument alt = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LIEFERUNG);
            LieferantDokument neu = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1-2", LIEFERUNG.minusDays(3));
            alt.getVerknuepfteDokumente().add(neu);
            when(dokumentRepository.findById(alt.getId())).thenReturn(Optional.of(alt));
            when(dokumentRepository.findById(neu.getId())).thenReturn(Optional.of(neu));

            service.verknuepfe(alt.getId(), neu.getId(), 7L);

            assertThat(neu.getVerknuepfteDokumente()).isEmpty();
            verify(dokumentRepository, never()).save(any());
        }

        @Test
        void zeugnisOhneLieferscheinHaengtAnDerAb() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LIEFERUNG);
            LieferantDokument zeugnis = dokument(LieferantDokumentTyp.WERKSTOFFZEUGNIS, null, LIEFERUNG);
            when(dokumentRepository.findById(ab.getId())).thenReturn(Optional.of(ab));
            when(dokumentRepository.findById(zeugnis.getId())).thenReturn(Optional.of(zeugnis));

            service.verknuepfe(ab.getId(), zeugnis.getId(), 7L);

            assertThat(zeugnis.getVerknuepfteDokumente()).containsExactly(ab);
        }

        @Test
        void lehntUnpassendeArtenAb() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LIEFERUNG);
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LIEFERUNG);
            LieferantDokument sonstiges = dokument(LieferantDokumentTyp.SONSTIG, "X-1", LIEFERUNG);
            when(dokumentRepository.findById(angebot.getId())).thenReturn(Optional.of(angebot));
            when(dokumentRepository.findById(rechnung.getId())).thenReturn(Optional.of(rechnung));
            when(dokumentRepository.findById(sonstiges.getId())).thenReturn(Optional.of(sonstiges));

            assertThatThrownBy(() -> service.verknuepfe(angebot.getId(), rechnung.getId(), 7L))
                    .isInstanceOf(BelegAbgelehntException.class)
                    .hasMessage("Rechnung und Angebot lassen sich nicht direkt verbinden.");
            assertThatThrownBy(() -> service.verknuepfe(rechnung.getId(), sonstiges.getId(), 7L))
                    .isInstanceOf(BelegAbgelehntException.class);
            verify(dokumentRepository, never()).save(any());
        }

        @Test
        void lehntGleicheOderFehlendeIdsAb() {
            assertThatThrownBy(() -> service.verknuepfe(5L, 5L, 7L)).isInstanceOf(BelegAbgelehntException.class);
            assertThatThrownBy(() -> service.verknuepfe(null, 5L, 7L)).isInstanceOf(BelegAbgelehntException.class);
        }
    }

    @Nested
    class SchonZugeordnet {

        @Test
        void nenntDasVerbundeneDokument() {
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, " LS-4711 ", LIEFERUNG);
            LieferantDokument zeugnis = dokument(LieferantDokumentTyp.WERKSTOFFZEUGNIS, null, LIEFERUNG);
            zeugnis.getVerknuepfteDokumente().add(ls);
            ls.getVerknuepftVon().add(zeugnis);

            assertThat(KettenVorschlagService.gehoertSchonZu(zeugnis, ALLE)).isEqualTo("Lieferschein LS-4711");
            assertThat(KettenVorschlagService.gehoertSchonZu(ls, ALLE)).isEqualTo("Werkstoffzeugnis");
        }

        @Test
        void unsichtbarerPartnerBleibtUngenannt() {
            // Wer keine Rechnungen sehen darf, erfährt auch nicht deren Nummer.
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-1", LIEFERUNG);
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-2026-0815", LIEFERUNG);
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-7", LIEFERUNG);
            rechnung.getVerknuepfteDokumente().add(ls);
            ls.getVerknuepftVon().add(rechnung);

            assertThat(KettenVorschlagService.gehoertSchonZu(ls,
                    java.util.EnumSet.of(LieferantDokumentTyp.LIEFERSCHEIN, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG)))
                    .isNull();
            ls.getVerknuepfteDokumente().add(ab);
            assertThat(KettenVorschlagService.gehoertSchonZu(ls,
                    java.util.EnumSet.of(LieferantDokumentTyp.LIEFERSCHEIN, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG)))
                    .isEqualTo("Auftragsbestätigung AB-7");
        }

        @Test
        void freiesDokumentGehoertNirgendwohin() {
            assertThat(KettenVorschlagService.gehoertSchonZu(dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LIEFERUNG), ALLE))
                    .isNull();
            assertThat(KettenVorschlagService.gehoertSchonZu(null, ALLE)).isNull();
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
}
