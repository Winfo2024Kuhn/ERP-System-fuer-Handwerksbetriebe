package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungsUebersichtDto;
import org.example.kalkulationsprogramm.dto.Bestellung.DokumentRef;
import org.example.kalkulationsprogramm.dto.Bestellung.DokumentenKette;
import org.example.kalkulationsprogramm.dto.Bestellung.RechnungsVorschlagDto;
import org.example.kalkulationsprogramm.dto.Bestellung.Verbindung;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentVerknuepfungSperreRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit-Tests für {@link BestellungsUebersichtService}: Ketten bauen, Status-Regel,
 * Kandidatenauswahl für Rechnungsvorschläge und Abbildung auf die DTOs.
 *
 * <p>Die eigentliche Bewertung (Trefferquote) liegt im {@link RechnungsVorschlagService}
 * und ist hier gemockt – geprüft wird nur, welche Rechnungen der Service ihm vorlegt
 * und was er aus den Ergebnissen macht. Die Suche „Dokument zur Kette hinzufügen“
 * ({@code KettenVorschlaege}) läuft dagegen mit dem echten {@link KettenVorschlagService}.
 * Alle Namen und Nummern sind erfunden.
 */
@ExtendWith(MockitoExtension.class)
class BestellungsUebersichtServiceTest {

    @Mock private LieferantDokumentRepository dokumentRepository;
    @Mock private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    @Mock private LieferantDokumentProjektAnteilRepository projektAnteilRepository;
    @Mock private RechnungsVorschlagService rechnungsVorschlagService;
    @Mock private LieferantDokumentVerknuepfungSperreRepository sperreRepository;

    private static final Set<LieferantDokumentTyp> ALLE_TYPEN = EnumSet.allOf(LieferantDokumentTyp.class);

    @Captor private ArgumentCaptor<Collection<LieferantDokument>> kandidatenCaptor;

    private BestellungsUebersichtService service;
    private Lieferanten lieferant;
    private Lieferanten andererLieferant;

    @BeforeEach
    void setUp() {
        service = new BestellungsUebersichtService(dokumentRepository, geschaeftsdokumentRepository,
                projektAnteilRepository, rechnungsVorschlagService, new KettenVorschlagService(
                        new LieferantDokumentAbgleich(new ObjectMapper()), dokumentRepository, sperreRepository));
        lieferant = lieferant(1L, "Max Mustermann GmbH");
        andererLieferant = lieferant(2L, "Erika Musterfrau KG");
        lenient().when(projektAnteilRepository.findAll()).thenReturn(List.of());
        lenient().when(geschaeftsdokumentRepository.findAll()).thenReturn(List.of());
        lenient().when(rechnungsVorschlagService.besterVorschlag(anyCollection(), anyCollection(), anyCollection(), any()))
                .thenReturn(Optional.empty());
    }

    @Nested
    class Dokumentrechte {

        @Test
        void nichtSichtbareTypenTauchenInKettenNichtAuf() {
            LieferantDokument ls = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, null);
            LieferantDokument re = dokument(2L, LieferantDokumentTyp.RECHNUNG, null);
            verknuepfe(re, ls);
            when(dokumentRepository.findAll()).thenReturn(List.of(ls, re));

            BestellungsUebersichtDto dto = service.ladeUebersicht(EnumSet.of(LieferantDokumentTyp.LIEFERSCHEIN));

            // Ohne Rechnungs-Recht ist die Rechnung weg: die Kette steht als laufende Bestellung da
            assertThat(dto.abgeschlossen()).isEmpty();
            assertThat(dto.laufendeBestellungen()).hasSize(1);
            assertThat(dto.laufendeBestellungen().get(0).dokumente())
                    .extracting(ref -> ref.id).containsExactly(1L);
        }

        @Test
        void ohneSichtbareTypenIstDieUebersichtLeer() {
            when(dokumentRepository.findAll()).thenReturn(List.of(
                    dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, null),
                    dokument(2L, LieferantDokumentTyp.RECHNUNG, null)));

            BestellungsUebersichtDto dto = service.ladeUebersicht(EnumSet.noneOf(LieferantDokumentTyp.class));

            assertThat(dto.offeneAnfragen()).isEmpty();
            assertThat(dto.laufendeBestellungen()).isEmpty();
            assertThat(dto.abgeschlossen()).isEmpty();
            assertThat(dto.zugeordnet()).isEmpty();
            assertThat(dto.ausgeblendet()).isEmpty();
        }
    }

    @Nested
    class StatusRegel {

        @Test
        void eingeblendeteUnzugeordneteRechnungLandetBeiRechnungZuordnen() {
            LieferantDokument ls = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, null);
            LieferantDokument re = dokument(2L, LieferantDokumentTyp.RECHNUNG, null);
            verknuepfe(re, ls);
            when(dokumentRepository.findAll()).thenReturn(List.of(ls, re));

            BestellungsUebersichtDto dto = service.ladeUebersicht(ALLE_TYPEN);

            assertThat(dto.abgeschlossen()).hasSize(1);
            assertThat(dto.laufendeBestellungen()).isEmpty();
            assertThat(dto.zugeordnet()).isEmpty();
        }

        @Test
        void projektenZugeordneteRechnungIstZugeordnet() {
            LieferantDokument re = dokument(2L, LieferantDokumentTyp.RECHNUNG, null);
            LieferantDokumentProjektAnteil anteil = new LieferantDokumentProjektAnteil();
            anteil.setDokument(re);
            when(projektAnteilRepository.findAll()).thenReturn(List.of(anteil));
            when(dokumentRepository.findAll()).thenReturn(List.of(re));

            BestellungsUebersichtDto dto = service.ladeUebersicht(ALLE_TYPEN);

            assertThat(dto.zugeordnet()).hasSize(1);
            assertThat(dto.abgeschlossen()).isEmpty();
        }

        @Test
        void lagerbestellungZaehltAlsZugeordnet() {
            LieferantDokument re = dokument(2L, LieferantDokumentTyp.RECHNUNG, null);
            LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
            gd.setDokument(re);
            gd.setLagerbestellung(true);
            when(geschaeftsdokumentRepository.findAll()).thenReturn(List.of(gd));
            when(dokumentRepository.findAll()).thenReturn(List.of(re));

            assertThat(service.ladeUebersicht(ALLE_TYPEN).zugeordnet()).hasSize(1);
        }

        @Test
        void ausgeblendeteRechnungMachtDieKetteErledigt() {
            LieferantDokument ls = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, null);
            LieferantDokument re = dokument(2L, LieferantDokumentTyp.RECHNUNG, null);
            re.setAusgeblendet(true);
            verknuepfe(re, ls);
            when(dokumentRepository.findAll()).thenReturn(List.of(ls, re));

            BestellungsUebersichtDto dto = service.ladeUebersicht(ALLE_TYPEN);

            assertThat(dto.ausgeblendet()).hasSize(1);
            assertThat(dto.laufendeBestellungen()).isEmpty();
        }

        @Test
        void eingeblendeteOffeneRechnungGehtVorAusgeblendeterRechnung() {
            LieferantDokument ab = dokument(1L, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, null);
            LieferantDokument bezahlt = dokument(2L, LieferantDokumentTyp.RECHNUNG, null);
            bezahlt.setAusgeblendet(true);
            LieferantDokument offen = dokument(3L, LieferantDokumentTyp.RECHNUNG, null);
            verknuepfe(bezahlt, ab);
            verknuepfe(offen, ab);
            when(dokumentRepository.findAll()).thenReturn(List.of(ab, bezahlt, offen));

            BestellungsUebersichtDto dto = service.ladeUebersicht(ALLE_TYPEN);

            assertThat(dto.abgeschlossen()).hasSize(1);
            assertThat(dto.ausgeblendet()).isEmpty();
        }

        @Test
        void allesAusgeblendetOhneRechnungIstErledigt() {
            LieferantDokument ls = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, null);
            ls.setAusgeblendet(true);
            when(dokumentRepository.findAll()).thenReturn(List.of(ls));

            BestellungsUebersichtDto dto = service.ladeUebersicht(ALLE_TYPEN);

            assertThat(dto.ausgeblendet()).hasSize(1);
            assertThat(dto.laufendeBestellungen()).isEmpty();
        }

        @Test
        void nurSonstigeDokumenteErscheinenNirgends() {
            when(dokumentRepository.findAll()).thenReturn(List.of(dokument(1L, LieferantDokumentTyp.SONSTIG, null)));

            BestellungsUebersichtDto dto = service.ladeUebersicht(ALLE_TYPEN);

            assertThat(dto.offeneAnfragen()).isEmpty();
            assertThat(dto.laufendeBestellungen()).isEmpty();
            assertThat(dto.abgeschlossen()).isEmpty();
            assertThat(dto.zugeordnet()).isEmpty();
            assertThat(dto.ausgeblendet()).isEmpty();
        }

        @Test
        void angebotIstAnfrageUndNeuesteKetteStehtVorne() {
            LieferantDokument alt = dokument(1L, LieferantDokumentTyp.ANGEBOT, LocalDate.of(2026, 1, 5));
            LieferantDokument neu = dokument(2L, LieferantDokumentTyp.ANGEBOT, LocalDate.of(2026, 6, 5));
            when(dokumentRepository.findAll()).thenReturn(List.of(alt, neu));

            BestellungsUebersichtDto dto = service.ladeUebersicht(ALLE_TYPEN);

            assertThat(dto.offeneAnfragen()).extracting(k -> k.dokumente().get(0).id).containsExactly(2L, 1L);
        }

        @Test
        void ohneBelegdatumSortiertDerEingang() {
            LieferantDokument frueh = dokument(1L, LieferantDokumentTyp.ANGEBOT, null);
            frueh.setUploadDatum(LocalDateTime.of(2026, 2, 1, 8, 0));
            LieferantDokument spaet = dokument(2L, LieferantDokumentTyp.ANGEBOT, null);
            spaet.setUploadDatum(LocalDateTime.of(2026, 8, 1, 8, 0));
            LieferantDokument mittel = dokument(3L, LieferantDokumentTyp.ANGEBOT, LocalDate.of(2026, 5, 1));
            when(dokumentRepository.findAll()).thenReturn(List.of(frueh, spaet, mittel));

            BestellungsUebersichtDto dto = service.ladeUebersicht(ALLE_TYPEN);

            assertThat(dto.offeneAnfragen()).extracting(k -> k.dokumente().get(0).id).containsExactly(2L, 3L, 1L);
        }
    }

    @Nested
    class Kartenvorschlag {

        @Test
        void bewertetNurRechnungenDesLieferantenImZeitfenster() {
            LieferantDokument ls = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, LocalDate.of(2026, 3, 1));
            LieferantDokument passend = dokument(2L, LieferantDokumentTyp.RECHNUNG, LocalDate.of(2026, 4, 1));
            LieferantDokument zuFrueh = dokument(3L, LieferantDokumentTyp.RECHNUNG, LocalDate.of(2026, 1, 15));
            LieferantDokument fremd = dokument(4L, LieferantDokumentTyp.RECHNUNG, LocalDate.of(2026, 4, 1));
            fremd.setLieferant(andererLieferant);
            when(dokumentRepository.findAll()).thenReturn(List.of(ls, passend, zuFrueh, fremd));

            service.ladeUebersicht(ALLE_TYPEN);

            verify(rechnungsVorschlagService).besterVorschlag(anyCollection(), kandidatenCaptor.capture(),
                    anyCollection(), any());
            assertThat(kandidatenCaptor.getValue()).containsExactly(passend);
        }

        @Test
        void haengtDenBestenVorschlagAnDieLaufendeKette() {
            LieferantDokument ls = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, LocalDate.of(2026, 3, 1));
            ls.getGeschaeftsdaten().setDokumentNummer("LS-100");
            LieferantDokument re = dokument(2L, LieferantDokumentTyp.RECHNUNG, LocalDate.of(2026, 3, 10));
            when(dokumentRepository.findAll()).thenReturn(List.of(ls, re));
            var vorschlag = new RechnungsVorschlagService.Vorschlag(re, ls,
                    new LieferantDokumentAbgleich.Einschaetzung(85, true, List.of("Bestellnummer stimmt")));
            when(rechnungsVorschlagService.besterVorschlag(anyCollection(), anyCollection(), anyCollection(), any()))
                    .thenReturn(Optional.of(new RechnungsVorschlagService.BesterVorschlag(vorschlag, false)));

            BestellungsUebersichtDto dto = service.ladeUebersicht(ALLE_TYPEN);

            RechnungsVorschlagDto karte = dto.laufendeBestellungen().get(0).rechnungsVorschlag();
            assertThat(karte.rechnung().id).isEqualTo(2L);
            assertThat(karte.lieferantName()).isEqualTo("Max Mustermann GmbH");
            assertThat(karte.bestellDokumentId()).isEqualTo(1L);
            assertThat(karte.bestellDokumentTyp()).isEqualTo(LieferantDokumentTyp.LIEFERSCHEIN);
            assertThat(karte.bestellDokumentNummer()).isEqualTo("LS-100");
            assertThat(karte.trefferquote()).isEqualTo(85);
            assertThat(karte.sicher()).isTrue();
            assertThat(karte.eindeutig()).isFalse();
            assertThat(karte.gruende()).containsExactly("Bestellnummer stimmt");
            assertThat(karte.gehoertSchonZu()).isNull();
            // Die (offene) Rechnung selbst bekommt keinen Vorschlag
            assertThat(dto.abgeschlossen().get(0).rechnungsVorschlag()).isNull();
        }

        @Test
        void ohneLieferantGibtEsKeinenVorschlag() {
            LieferantDokument ls = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, LocalDate.of(2026, 3, 1));
            ls.setLieferant(null);
            when(dokumentRepository.findAll()).thenReturn(List.of(ls));

            BestellungsUebersichtDto dto = service.ladeUebersicht(ALLE_TYPEN);

            assertThat(dto.laufendeBestellungen().get(0).rechnungsVorschlag()).isNull();
            verify(rechnungsVorschlagService, never()).besterVorschlag(anyCollection(), anyCollection(),
                    anyCollection(), any());
        }
    }

    @Nested
    class Kartenfenster {

        private final List<LocalDate> bestellung = List.of(LocalDate.of(2026, 3, 1));

        @Test
        void grenzenSindEingeschlossen() {
            assertThat(imFenster(LocalDate.of(2026, 3, 1).minusDays(30))).isTrue();
            assertThat(imFenster(LocalDate.of(2026, 3, 1).minusDays(31))).isFalse();
            assertThat(imFenster(LocalDate.of(2026, 3, 1).plusDays(180))).isTrue();
            assertThat(imFenster(LocalDate.of(2026, 3, 1).plusDays(181))).isFalse();
        }

        @Test
        void ohneDatumBleibtDieRechnungImRennen() {
            assertThat(imFenster(null)).isTrue();
            LieferantDokument re = dokument(2L, LieferantDokumentTyp.RECHNUNG, LocalDate.of(2010, 1, 1));
            assertThat(BestellungsUebersichtService.imKartenFenster(re, List.of())).isTrue();
        }

        private boolean imFenster(LocalDate rechnungsdatum) {
            return BestellungsUebersichtService.imKartenFenster(
                    dokument(2L, LieferantDokumentTyp.RECHNUNG, rechnungsdatum), bestellung);
        }
    }

    @Nested
    class KettenBauen {

        @Test
        void sortiertNachTypUndListetJedeVerbindungEinmal() {
            LieferantDokument ab = dokument(1L, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, null);
            LieferantDokument ls = dokument(2L, LieferantDokumentTyp.LIEFERSCHEIN, null);
            LieferantDokument re = dokument(3L, LieferantDokumentTyp.RECHNUNG, null);
            verknuepfe(ls, ab);
            verknuepfe(re, ls);
            verknuepfe(re, ab);

            List<DokumentenKette> ketten = service.buildKetten(List.of(re, ls, ab));

            assertThat(ketten).hasSize(1);
            DokumentenKette kette = ketten.get(0);
            assertThat(kette.dokumente()).extracting(d -> d.typ).containsExactly(
                    LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, LieferantDokumentTyp.LIEFERSCHEIN,
                    LieferantDokumentTyp.RECHNUNG);
            assertThat(kette.verbindungen()).containsExactly(
                    new Verbindung(2L, 1L), new Verbindung(3L, 1L), new Verbindung(3L, 2L));
            assertThat(kette.lieferantId()).isEqualTo(1L);
            assertThat(kette.rechnungsVorschlag()).isNull();
        }

        @Test
        void unverknuepfteDokumenteSindEigeneKetten() {
            List<DokumentenKette> ketten = service.buildKetten(List.of(
                    dokument(1L, LieferantDokumentTyp.ANGEBOT, null),
                    dokument(2L, LieferantDokumentTyp.LIEFERSCHEIN, null)));

            assertThat(ketten).hasSize(2);
            assertThat(ketten).allSatisfy(k -> assertThat(k.verbindungen()).isEmpty());
        }
    }

    @Nested
    class DokumentRefAbbilden {

        @Test
        void mailAnhangHatVorrang() {
            LieferantDokument d = dokument(5L, LieferantDokumentTyp.RECHNUNG, LocalDate.of(2026, 2, 3));
            Email email = new Email();
            email.setId(40L);
            EmailAttachment anhang = new EmailAttachment();
            anhang.setId(41L);
            anhang.setEmail(email);
            d.setAttachment(anhang);
            d.setGespeicherterDateiname("x.pdf");

            DokumentRef ref = BestellungsUebersichtService.toDokumentRef(d);

            assertThat(ref.pdfUrl).isEqualTo("/api/emails/40/attachments/41");
            assertThat(ref.dokumentDatum).isEqualTo(LocalDate.of(2026, 2, 3));
            assertThat(ref.eingangsDatum).isEqualTo(LocalDate.of(2026, 9, 1));
        }

        @Test
        void gespeicherteDateiUeberDenLieferanten() {
            LieferantDokument d = dokument(5L, LieferantDokumentTyp.RECHNUNG, null);
            d.setGespeicherterDateiname("x.pdf");

            assertThat(BestellungsUebersichtService.toDokumentRef(d).pdfUrl)
                    .isEqualTo("/api/lieferanten/1/dokumente/5/download");
        }

        @Test
        void sonstDerAllgemeineDokumentLink() {
            LieferantDokument d = dokument(5L, LieferantDokumentTyp.RECHNUNG, null);
            d.setUploadDatum(null);

            DokumentRef ref = BestellungsUebersichtService.toDokumentRef(d);

            assertThat(ref.pdfUrl).isEqualTo("/api/lieferant-dokumente/5/download");
            assertThat(ref.eingangsDatum).isNull();
            assertThat(ref.betragBrutto).isNull();
        }
    }

    /** „Dokument zur Kette hinzufügen“: Vorschläge aller Arten. */
    @Nested
    class KettenVorschlaege {

        private final LocalDate lieferung = LocalDate.of(2026, 9, 23);

        @Test
        void zeugnisZumLieferscheinStehtVornUndDieKetteFehlt() {
            LieferantDokument ab = dokument(1L, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, lieferung.minusDays(5));
            LieferantDokument ls = dokument(2L, LieferantDokumentTyp.LIEFERSCHEIN, lieferung);
            ls.getGeschaeftsdaten().setDokumentNummer("90445744/01");
            verknuepfe(ls, ab);
            LieferantDokument zeugnis = dokument(3L, LieferantDokumentTyp.WERKSTOFFZEUGNIS, lieferung.minusDays(1));
            zeugnis.getGeschaeftsdaten().setReferenzNummer("90445744/01");
            LieferantDokument sonstiges = dokument(4L, LieferantDokumentTyp.SONSTIG, lieferung);
            when(dokumentRepository.findAll()).thenReturn(List.of(ab, ls, zeugnis, sonstiges));

            var liste = service.kettenVorschlaege(List.of(1L), false, null, ALLE_TYPEN).orElseThrow();

            assertThat(liste).extracting(v -> v.dokument().id).containsExactly(3L);
            assertThat(liste.get(0).kettenDokumentId()).isEqualTo(2L);
            assertThat(liste.get(0).kettenDokumentNummer()).isEqualTo("90445744/01");
            assertThat(liste.get(0).sicher()).isTrue();
            assertThat(liste.get(0).eindeutig()).isTrue();
            assertThat(liste.get(0).lieferantName()).isEqualTo("Max Mustermann GmbH");
        }

        @Test
        void filtertNachArtUndLieferant() {
            LieferantDokument ls = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, lieferung);
            LieferantDokument zeugnis = dokument(2L, LieferantDokumentTyp.WERKSTOFFZEUGNIS, lieferung);
            LieferantDokument rechnung = dokument(3L, LieferantDokumentTyp.RECHNUNG, lieferung);
            LieferantDokument fremdeRechnung = dokument(4L, LieferantDokumentTyp.RECHNUNG, lieferung);
            fremdeRechnung.setLieferant(andererLieferant);
            when(dokumentRepository.findAll()).thenReturn(List.of(ls, zeugnis, rechnung, fremdeRechnung));

            assertThat(service.kettenVorschlaege(List.of(1L), false, LieferantDokumentTyp.RECHNUNG, ALLE_TYPEN)
                    .orElseThrow()).extracting(v -> v.dokument().id).containsExactly(3L);
            assertThat(service.kettenVorschlaege(List.of(1L), true, LieferantDokumentTyp.RECHNUNG, ALLE_TYPEN)
                    .orElseThrow()).extracting(v -> v.dokument().id).containsExactlyInAnyOrder(3L, 4L);
        }

        @Test
        void einzeldokumentFindetSeineKette() {
            // Ein Zeugnis ohne Kette: Vorschläge sind die Dokumente, an die es passt.
            LieferantDokument zeugnis = dokument(1L, LieferantDokumentTyp.WERKSTOFFZEUGNIS, lieferung);
            LieferantDokument ls = dokument(2L, LieferantDokumentTyp.LIEFERSCHEIN, lieferung);
            LieferantDokument angebot = dokument(3L, LieferantDokumentTyp.ANGEBOT, lieferung);
            when(dokumentRepository.findAll()).thenReturn(List.of(zeugnis, ls, angebot));

            assertThat(service.kettenVorschlaege(List.of(1L), false, null, ALLE_TYPEN).orElseThrow())
                    .extracting(v -> v.dokument().id).containsExactly(2L);
        }

        @Test
        void alleLieferantenZeigtKeineUnsichtbarenArtenUndNenntKeineUnsichtbarenPartner() {
            LieferantDokument ls = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, lieferung);
            LieferantDokument fremdeRechnung = dokument(2L, LieferantDokumentTyp.RECHNUNG, lieferung);
            fremdeRechnung.setLieferant(andererLieferant);
            LieferantDokument fremdesZeugnis = dokument(3L, LieferantDokumentTyp.WERKSTOFFZEUGNIS, lieferung);
            fremdesZeugnis.setLieferant(andererLieferant);
            LieferantDokument fremderLs = dokument(4L, LieferantDokumentTyp.LIEFERSCHEIN, lieferung);
            fremderLs.setLieferant(andererLieferant);
            // Das fremde Zeugnis hängt an einer Rechnung, die der Aufrufer nicht sehen darf
            verknuepfe(fremdesZeugnis, fremderLs);
            verknuepfe(fremdeRechnung, fremderLs);
            when(dokumentRepository.findAll()).thenReturn(List.of(ls, fremdeRechnung, fremdesZeugnis, fremderLs));
            Set<LieferantDokumentTyp> ohneRechnung = EnumSet.of(LieferantDokumentTyp.LIEFERSCHEIN,
                    LieferantDokumentTyp.WERKSTOFFZEUGNIS);

            var liste = service.kettenVorschlaege(List.of(1L), true, null, ohneRechnung).orElseThrow();

            assertThat(liste).extracting(v -> v.dokument().id).doesNotContain(2L);
            assertThat(liste).filteredOn(v -> v.dokument().id == 3L).singleElement()
                    .satisfies(v -> assertThat(v.gehoertSchonZu()).isEqualTo("Lieferschein"));
        }

        @Test
        void dokumenteAusserhalbDesZeitfenstersFehlen() {
            LieferantDokument ls = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, lieferung);
            LieferantDokument alt = dokument(2L, LieferantDokumentTyp.WERKSTOFFZEUGNIS,
                    lieferung.minusDays(BestellungsUebersichtService.SUCHE_TAGE + 1));
            LieferantDokument ohneDatum = dokument(3L, LieferantDokumentTyp.WERKSTOFFZEUGNIS, lieferung);
            ohneDatum.getGeschaeftsdaten().setDokumentDatum(null);
            when(dokumentRepository.findAll()).thenReturn(List.of(ls, alt, ohneDatum));

            assertThat(service.kettenVorschlaege(List.of(1L), false, null, ALLE_TYPEN).orElseThrow())
                    .extracting(v -> v.dokument().id).containsExactly(3L);
        }

        @Test
        void nichtSichtbareDokumenteFehlen() {
            LieferantDokument ls = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, lieferung);
            LieferantDokument rechnung = dokument(2L, LieferantDokumentTyp.RECHNUNG, lieferung);
            when(dokumentRepository.findAll()).thenReturn(List.of(ls, rechnung));

            assertThat(service.kettenVorschlaege(List.of(1L), false, null, EnumSet.of(LieferantDokumentTyp.LIEFERSCHEIN))
                    .orElseThrow()).isEmpty();
            assertThat(service.kettenVorschlaege(List.of(1L), false, null, EnumSet.of(LieferantDokumentTyp.RECHNUNG)))
                    .isEmpty();
        }

        @Test
        void unbekannteIdsGebenKeinErgebnis() {
            LieferantDokument ls = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, lieferung);
            when(dokumentRepository.findAll()).thenReturn(List.of(ls));

            assertThat(service.kettenVorschlaege(List.of(98L, 99L), false, null, ALLE_TYPEN)).isEmpty();
        }

        @Test
        void ohneLieferantGibtEsNurMitAlleLieferantenKandidaten() {
            LieferantDokument ls = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, lieferung);
            ls.setLieferant(null);
            LieferantDokument zeugnis = dokument(2L, LieferantDokumentTyp.WERKSTOFFZEUGNIS, lieferung);
            when(dokumentRepository.findAll()).thenReturn(List.of(ls, zeugnis));

            assertThat(service.kettenVorschlaege(List.of(1L), false, null, ALLE_TYPEN).orElseThrow()).isEmpty();
            assertThat(service.kettenVorschlaege(List.of(1L), true, null, ALLE_TYPEN).orElseThrow())
                    .extracting(v -> v.dokument().id).containsExactly(2L);
        }

        @Test
        void gleichstandMitDemNachbarnIstNichtEindeutig() {
            LieferantDokument ls = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, lieferung);
            LieferantDokument a = dokument(2L, LieferantDokumentTyp.WERKSTOFFZEUGNIS, lieferung);
            LieferantDokument b = dokument(3L, LieferantDokumentTyp.WERKSTOFFZEUGNIS, lieferung);
            when(dokumentRepository.findAll()).thenReturn(List.of(ls, a, b));

            var liste = service.kettenVorschlaege(List.of(1L), false, null, ALLE_TYPEN).orElseThrow();

            assertThat(liste).hasSize(2);
            assertThat(liste).extracting(v -> v.trefferquote()).containsOnly(liste.get(0).trefferquote());
            assertThat(liste).extracting(v -> v.eindeutig()).containsOnly(false);
        }

        @Test
        void zeigtHoechstensDieMaximaleAnzahl() {
            List<LieferantDokument> alle = new ArrayList<>();
            alle.add(dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, lieferung));
            for (int i = 0; i < BestellungsUebersichtService.MAX_VORSCHLAEGE + 5; i++) {
                alle.add(dokument(10L + i, LieferantDokumentTyp.WERKSTOFFZEUGNIS, lieferung));
            }
            when(dokumentRepository.findAll()).thenReturn(alle);

            assertThat(service.kettenVorschlaege(List.of(1L), false, null, ALLE_TYPEN).orElseThrow())
                    .hasSize(BestellungsUebersichtService.MAX_VORSCHLAEGE);
        }
    }

    private static Lieferanten lieferant(long id, String name) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        l.setLieferantenname(name);
        return l;
    }

    private LieferantDokument dokument(long id, LieferantDokumentTyp typ, LocalDate belegdatum) {
        LieferantDokument d = new LieferantDokument();
        d.setId(id);
        d.setLieferant(lieferant);
        d.setTyp(typ);
        d.setUploadDatum(LocalDateTime.of(2026, 9, 1, 8, 0));
        if (belegdatum != null) {
            LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
            gd.setDokument(d);
            gd.setDokumentDatum(belegdatum);
            d.setGeschaeftsdaten(gd);
        }
        return d;
    }

    /** Wie JPA: beide Seiten der Verknüpfung (Nachfolger -> Vorgänger). */
    private static void verknuepfe(LieferantDokument nachfolger, LieferantDokument vorgaenger) {
        nachfolger.getVerknuepfteDokumente().add(vorgaenger);
        vorgaenger.getVerknuepftVon().add(nachfolger);
    }
}
