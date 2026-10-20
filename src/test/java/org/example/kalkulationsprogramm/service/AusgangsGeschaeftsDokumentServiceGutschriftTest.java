package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentCounter;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentErstellenDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentUpdateDto;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentCounterRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Kunden-Gutschrift als Folgedokument einer gebuchten Rechnung (Issue #223):
 * Vorzeichen, Höchstbetrag, Offener Posten, Storno.
 */
@ExtendWith(MockitoExtension.class)
class AusgangsGeschaeftsDokumentServiceGutschriftTest {

    @Mock private AusgangsGeschaeftsDokumentRepository dokumentRepository;
    @Mock private AusgangsGeschaeftsDokumentCounterRepository counterRepository;
    @Mock private ProjektRepository projektRepository;
    @Mock private AnfrageRepository anfrageRepository;
    @Mock private KundeRepository kundeRepository;
    @Mock private FrontendUserProfileRepository frontendUserProfileRepository;
    @Mock private LeistungRepository leistungRepository;
    @Mock private ProduktkategorieRepository produktkategorieRepository;
    @Mock private ProjektDokumentRepository projektDokumentRepository;
    @Mock private ZeitbuchungRepository zeitbuchungRepository;
    @Mock private AusgangsGeschaeftsDokumentAuditService auditService;

    private AusgangsGeschaeftsDokumentService service;

    private static final String TEILBETRAG_100 = """
            {"blocks":[{"id":"g1","type":"SERVICE","title":"Gutschrift zu Rechnung RE-2026/10/00001: Kratzer am Geländer","quantity":1,"price":100,"unit":"psch"}]}
            """;

    @BeforeEach
    void setUp() {
        service = new AusgangsGeschaeftsDokumentService("uploads", dokumentRepository, counterRepository,
                projektRepository, anfrageRepository, kundeRepository, frontendUserProfileRepository,
                leistungRepository, produktkategorieRepository, projektDokumentRepository, zeitbuchungRepository,
                auditService);
    }

    private void nummernkreis() {
        AusgangsGeschaeftsDokumentCounter counter = new AusgangsGeschaeftsDokumentCounter();
        counter.setZaehler(0L);
        when(counterRepository.findByMonatKeyForUpdate(any())).thenReturn(Optional.of(counter));
        when(counterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static AusgangsGeschaeftsDokument rechnung() {
        AusgangsGeschaeftsDokument r = new AusgangsGeschaeftsDokument();
        r.setId(1L);
        r.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
        r.setDokumentNummer("RE-2026/10/00001");
        r.setBetragNetto(new BigDecimal("1000.00"));
        r.setBetragBrutto(new BigDecimal("1190.00"));
        r.setMwstSatz(new BigDecimal("0.19"));
        r.setPositionenJson("{\"blocks\":[{\"id\":\"s1\",\"type\":\"SERVICE\",\"title\":\"Geländer\",\"quantity\":1,\"price\":1000}]}");
        r.setHtmlInhalt("<p>Geländer</p>");
        r.setGebucht(true);
        return r;
    }

    private static AusgangsGeschaeftsDokument gutschrift(long id, AusgangsGeschaeftsDokument rechnung, String netto,
            String brutto, boolean gebucht) {
        AusgangsGeschaeftsDokument g = new AusgangsGeschaeftsDokument();
        g.setId(id);
        g.setTyp(AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT);
        g.setDokumentNummer("GU-2026/10/0000" + id);
        g.setVorgaenger(rechnung);
        g.setBetragNetto(new BigDecimal(netto));
        g.setBetragBrutto(new BigDecimal(brutto));
        g.setMwstSatz(new BigDecimal("0.19"));
        g.setGebucht(gebucht);
        return g;
    }

    private static AusgangsGeschaeftsDokumentErstellenDto neueGutschrift(Long rechnungId) {
        AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
        dto.setTyp(AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT);
        dto.setVorgaengerId(rechnungId);
        return dto;
    }

    private static ProjektGeschaeftsdokument offenerPosten(String brutto, String gutgeschrieben, boolean bezahlt) {
        ProjektGeschaeftsdokument op = new ProjektGeschaeftsdokument();
        op.setDokumentid("RE-2026/10/00001");
        op.setGeschaeftsdokumentart("Rechnung");
        op.setBruttoBetrag(new BigDecimal(brutto));
        op.setGutgeschrieben(new BigDecimal(gutgeschrieben));
        op.setBezahlt(bezahlt);
        return op;
    }

    @Nested
    class Anlegen {

        @Test
        void ohneRechnung_gibt400() {
            AusgangsGeschaeftsDokumentErstellenDto dto = neueGutschrift(null);

            assertThatThrownBy(() -> service.erstellen(dto))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Eine Gutschrift gehört immer zu einer Rechnung");
            verify(dokumentRepository, never()).save(any());
        }

        @Test
        void unbekannteRechnung_gibt404() {
            when(dokumentRepository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.erstellen(neueGutschrift(Long.MAX_VALUE)))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        }

        @Test
        void zuEntwurfStornoOderAngebot_gibt400() {
            AusgangsGeschaeftsDokument entwurf = rechnung();
            entwurf.setGebucht(false);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(entwurf));
            assertThatThrownBy(() -> service.erstellen(neueGutschrift(1L)))
                    .hasMessageContaining("nur zu einer gebuchten Rechnung");

            AusgangsGeschaeftsDokument storniert = rechnung();
            storniert.setStorniert(true);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(storniert));
            assertThatThrownBy(() -> service.erstellen(neueGutschrift(1L)))
                    .hasMessageContaining("Die Rechnung ist storniert.");

            AusgangsGeschaeftsDokument angebot = rechnung();
            angebot.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(angebot));
            assertThatThrownBy(() -> service.erstellen(neueGutschrift(1L)))
                    .hasMessageContaining("nur zu einer gebuchten Rechnung");

            verify(dokumentRepository, never()).save(any());
        }

        @Test
        void ganzeRechnung_uebernimmtBetragUndSpeichertMinus() {
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(rechnung()));
            nummernkreis();
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AusgangsGeschaeftsDokument g = service.erstellen(neueGutschrift(1L));

            assertThat(g.getBetragNetto()).isEqualByComparingTo("-1000.00");
            assertThat(g.getBetragBrutto()).isEqualByComparingTo("-1190.00");
            assertThat(g.getMwstSatz()).isEqualByComparingTo("0.19");
            assertThat(g.getBetreff()).isEqualTo("Gutschrift zu Rechnung RE-2026/10/00001");
            assertThat(g.getDokumentNummer()).startsWith("GU-");
            assertThat(g.getPositionenJson()).contains("Geländer");
            assertThat(g.getVorgaenger().getId()).isEqualTo(1L);
        }

        @Test
        void teilbetrag_ausEigenenPositionen() {
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(rechnung()));
            nummernkreis();
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            AusgangsGeschaeftsDokumentErstellenDto dto = neueGutschrift(1L);
            dto.setPositionenJson(TEILBETRAG_100);
            dto.setBetreff("Preisnachlass Reklamation");

            AusgangsGeschaeftsDokument g = service.erstellen(dto);

            assertThat(g.getBetragNetto()).isEqualByComparingTo("-100.00");
            assertThat(g.getBetragBrutto()).isEqualByComparingTo("-119.00");
            assertThat(g.getBetreff()).isEqualTo("Preisnachlass Reklamation");
            assertThat(g.getHtmlInhalt()).as("kein Rechnungstext zu eigenen Positionen").isNull();
        }

        @Test
        void betragOhnePositionen_gibt400() {
            AusgangsGeschaeftsDokumentErstellenDto dto = neueGutschrift(1L);
            dto.setBetragNetto(new BigDecimal("100.00"));

            assertThatThrownBy(() -> service.erstellen(dto))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("mit eigenen Positionen anlegen");
            verify(dokumentRepository, never()).save(any());
        }

        @Test
        void kundeAnschriftUndSteuersatzKommenImmerVonDerRechnung() {
            AusgangsGeschaeftsDokument r = rechnung();
            org.example.kalkulationsprogramm.domain.Kunde kunde = new org.example.kalkulationsprogramm.domain.Kunde();
            kunde.setId(7L);
            kunde.setName("Max Mustermann");
            r.setKunde(kunde);
            r.setMwstSatz(new BigDecimal("0.07"));
            r.setRechnungsadresseOverride("Max Mustermann\nMusterweg 1\n97259 Musterstadt");
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(r));
            org.example.kalkulationsprogramm.domain.Kunde fremd = new org.example.kalkulationsprogramm.domain.Kunde();
            fremd.setId(8L);
            when(kundeRepository.findById(8L)).thenReturn(Optional.of(fremd));
            nummernkreis();
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            AusgangsGeschaeftsDokumentErstellenDto dto = neueGutschrift(1L);
            dto.setKundeId(8L);
            dto.setMwstSatz(new BigDecimal("0.19"));
            dto.setPositionenJson(TEILBETRAG_100);

            AusgangsGeschaeftsDokument g = service.erstellen(dto);

            assertThat(g.getKunde()).isSameAs(kunde);
            assertThat(g.getMwstSatz()).isEqualByComparingTo("0.07");
            assertThat(g.getBetragBrutto()).isEqualByComparingTo("-107.00");
            assertThat(g.getRechnungsadresseOverride()).contains("Musterweg 1");
            verify(dokumentRepository).sperreZumGutschreiben(1L);
        }

        @Test
        void hoeherAlsDerRest_gibt400MitHoechstbetrag() {
            AusgangsGeschaeftsDokument r = rechnung();
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(r));
            AusgangsGeschaeftsDokument storniert = gutschrift(8, r, "-500.00", "-595.00", true);
            storniert.setStorniert(true);
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L)).thenReturn(List.of(
                    gutschrift(7, r, "-950.00", "-1130.50", true), storniert));
            AusgangsGeschaeftsDokumentErstellenDto dto = neueGutschrift(1L);
            dto.setPositionenJson(TEILBETRAG_100);

            assertThatThrownBy(() -> service.erstellen(dto))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode().value()).isEqualTo(400);
                        assertThat(e.getReason()).isEqualTo("Die Gutschrift ist höher als der noch offene "
                                + "Rechnungsbetrag. Höchstens 50,00 € netto sind noch möglich.");
                    });
            verify(dokumentRepository, never()).save(any());
        }
    }

    @Nested
    class Bearbeiten {

        @Test
        void positiverBetragAusDemEditor_wirdMinusUndGegenDenRestGeprueft() {
            AusgangsGeschaeftsDokument r = rechnung();
            AusgangsGeschaeftsDokument g = gutschrift(5, r, "-100.00", "-119.00", false);
            when(dokumentRepository.findById(5L)).thenReturn(Optional.of(g));
            // die eigene Gutschrift zählt nicht gegen sich selbst
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L)).thenReturn(List.of(g));
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            AusgangsGeschaeftsDokumentUpdateDto dto = new AusgangsGeschaeftsDokumentUpdateDto();
            dto.setBetragNetto(new BigDecimal("1000.00"));

            AusgangsGeschaeftsDokument gespeichert = service.aktualisieren(5L, dto);

            assertThat(gespeichert.getBetragNetto()).isEqualByComparingTo("-1000.00");
            assertThat(gespeichert.getBetragBrutto()).isEqualByComparingTo("-1190.00");
        }

        @Test
        void betragFolgtDenPositionen_undSteuersatzBleibtDerDerRechnung() {
            AusgangsGeschaeftsDokument r = rechnung();
            AusgangsGeschaeftsDokument g = gutschrift(5, r, "-100.00", "-119.00", false);
            g.setPositionenJson(TEILBETRAG_100);
            when(dokumentRepository.findById(5L)).thenReturn(Optional.of(g));
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            AusgangsGeschaeftsDokumentUpdateDto dto = new AusgangsGeschaeftsDokumentUpdateDto();
            dto.setBetragNetto(new BigDecimal("999.00"));
            dto.setMwstSatz(new BigDecimal("0.07"));

            AusgangsGeschaeftsDokument gespeichert = service.aktualisieren(5L, dto);

            assertThat(gespeichert.getBetragNetto()).isEqualByComparingTo("-100.00");
            assertThat(gespeichert.getMwstSatz()).isEqualByComparingTo("0.19");
            assertThat(gespeichert.getBetragBrutto()).isEqualByComparingTo("-119.00");
        }

        @Test
        void mehrAlsDieRechnung_gibt400() {
            AusgangsGeschaeftsDokument r = rechnung();
            AusgangsGeschaeftsDokument g = gutschrift(5, r, "-100.00", "-119.00", false);
            when(dokumentRepository.findById(5L)).thenReturn(Optional.of(g));
            AusgangsGeschaeftsDokumentUpdateDto dto = new AusgangsGeschaeftsDokumentUpdateDto();
            dto.setBetragNetto(new BigDecimal("1000.02"));

            assertThatThrownBy(() -> service.aktualisieren(5L, dto))
                    .hasMessageContaining("Höchstens 1.000,00");
            verify(dokumentRepository, never()).save(any());
        }
    }

    @Nested
    class Buchen {

        @Test
        void teilgutschrift_mindertDenOffenenPosten() {
            AusgangsGeschaeftsDokument g = gutschrift(5, rechnung(), "-100.00", "-119.00", false);
            when(dokumentRepository.findById(5L)).thenReturn(Optional.of(g));
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            ProjektGeschaeftsdokument op = offenerPosten("1190.00", "0", false);
            when(projektDokumentRepository.findGeschaeftsdokumenteByDokumentid("RE-2026/10/00001")).thenReturn(List.of(op));

            service.buchen(5L);

            assertThat(g.isGebucht()).isTrue();
            assertThat(op.getGutgeschrieben()).isEqualByComparingTo("119.00");
            assertThat(op.getOffenerBetrag()).isEqualByComparingTo("1071.00");
            assertThat(op.isBezahlt()).isFalse();
            verify(projektDokumentRepository).save(op);
            verify(projektDokumentRepository, never()).existsByDokumentid(any());
        }

        @Test
        void ganzeRechnung_erledigtDenOffenenPosten() {
            AusgangsGeschaeftsDokument g = gutschrift(5, rechnung(), "-1000.00", "-1190.00", false);
            when(dokumentRepository.findById(5L)).thenReturn(Optional.of(g));
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            ProjektGeschaeftsdokument op = offenerPosten("1190.00", "0", false);
            when(projektDokumentRepository.findGeschaeftsdokumenteByDokumentid("RE-2026/10/00001")).thenReturn(List.of(op));

            service.buchen(5L);

            assertThat(op.isBezahlt()).isTrue();
            assertThat(op.getOffenerBetrag()).isEqualByComparingTo("0.00");
            verify(projektDokumentRepository).saveAndFlush(op);
        }

        @Test
        void ohneRechnung_gibt400() {
            AusgangsGeschaeftsDokument alt = gutschrift(5, null, "-100.00", "-119.00", false);
            when(dokumentRepository.findById(5L)).thenReturn(Optional.of(alt));

            assertThatThrownBy(() -> service.buchen(5L)).hasMessageContaining("gehört immer zu einer Rechnung");
            assertThat(alt.isGebucht()).isFalse();
        }

        @Test
        void rechnungInzwischenStorniert_gibt400() {
            AusgangsGeschaeftsDokument r = rechnung();
            r.setStorniert(true);
            AusgangsGeschaeftsDokument g = gutschrift(5, r, "-100.00", "-119.00", false);
            when(dokumentRepository.findById(5L)).thenReturn(Optional.of(g));

            assertThatThrownBy(() -> service.buchen(5L)).hasMessageContaining("Die Rechnung ist storniert.");
        }
    }

    @Nested
    class Stornieren {

        @Test
        void gutschrift_stornoStehtPositivUndRechnungIstWiederOffen() {
            AusgangsGeschaeftsDokument g = gutschrift(5, rechnung(), "-1000.00", "-1190.00", true);
            when(dokumentRepository.findById(5L)).thenReturn(Optional.of(g));
            nummernkreis();
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            ProjektGeschaeftsdokument op = offenerPosten("1190.00", "1190.00", true);
            when(projektDokumentRepository.findGeschaeftsdokumenteByDokumentid("RE-2026/10/00001")).thenReturn(List.of(op));

            AusgangsGeschaeftsDokument storno = service.stornieren(5L);

            assertThat(g.isStorniert()).isTrue();
            assertThat(storno.getTyp()).isEqualTo(AusgangsGeschaeftsDokumentTyp.STORNO);
            assertThat(storno.getBetragNetto()).isEqualByComparingTo("1000.00");
            assertThat(storno.getBetragBrutto()).isEqualByComparingTo("1190.00");
            assertThat(storno.getBetreff()).contains("zu Gutschrift GU-2026/10/00005");
            assertThat(op.getGutgeschrieben()).isEqualByComparingTo("0");
            assertThat(op.isBezahlt()).isFalse();
            verify(projektDokumentRepository, never()).findAllGeschaeftsdokumente();
        }

        @Test
        void teilgutschrift_vonHandBezahlteRechnungBleibtBezahlt() {
            AusgangsGeschaeftsDokument g = gutschrift(5, rechnung(), "-100.00", "-119.00", true);
            when(dokumentRepository.findById(5L)).thenReturn(Optional.of(g));
            nummernkreis();
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            ProjektGeschaeftsdokument op = offenerPosten("1190.00", "119.00", true);
            when(projektDokumentRepository.findGeschaeftsdokumenteByDokumentid("RE-2026/10/00001")).thenReturn(List.of(op));

            service.stornieren(5L);

            assertThat(op.getGutgeschrieben()).isEqualByComparingTo("0");
            assertThat(op.isBezahlt()).isTrue();
        }

        @Test
        void gutschriftEntwurf_gibt400() {
            AusgangsGeschaeftsDokument g = gutschrift(5, rechnung(), "-100.00", "-119.00", false);
            when(dokumentRepository.findById(5L)).thenReturn(Optional.of(g));

            assertThatThrownBy(() -> service.stornieren(5L))
                    .hasMessageContaining("Nur gebuchte Gutschriften können storniert werden");
        }

        @Test
        void rechnungMitGutschrift_mussZuerstDieGutschriftStornieren() {
            AusgangsGeschaeftsDokument r = rechnung();
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(r));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L))
                    .thenReturn(List.of(gutschrift(5, r, "-100.00", "-119.00", true)));

            assertThatThrownBy(() -> service.stornieren(1L))
                    .hasMessageContaining("Bitte zuerst die Gutschrift stornieren");
            assertThat(r.isStorniert()).isFalse();

            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L))
                    .thenReturn(List.of(gutschrift(6, r, "-100.00", "-119.00", false)));
            assertThatThrownBy(() -> service.stornieren(1L))
                    .hasMessageContaining("Bitte zuerst den Entwurf löschen");
        }

        @Test
        void stornoEinerStornorechnung_bleibtVerboten() {
            AusgangsGeschaeftsDokument storno = rechnung();
            storno.setTyp(AusgangsGeschaeftsDokumentTyp.STORNO);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(storno));

            assertThatThrownBy(() -> service.stornieren(1L))
                    .hasMessageContaining("Nur Rechnungen und Gutschriften können storniert werden.");
        }
    }

    @Test
    void rahmen_zeigtRechnungBereitsUndHoechstens() {
        AusgangsGeschaeftsDokument r = rechnung();
        when(dokumentRepository.findById(1L)).thenReturn(Optional.of(r));
        List<AusgangsGeschaeftsDokument> folge = new ArrayList<>(List.of(
                gutschrift(5, r, "-100.00", "-119.00", true),
                gutschrift(6, r, "-50.00", "-59.50", false)));
        AusgangsGeschaeftsDokument storniert = gutschrift(7, r, "-300.00", "-357.00", true);
        storniert.setStorniert(true);
        folge.add(storniert);
        when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L)).thenReturn(folge);

        AusgangsGeschaeftsDokumentService.GutschriftRahmenDto rahmen = service.gutschriftRahmen(1L);

        assertThat(rahmen.rechnungNummer()).isEqualTo("RE-2026/10/00001");
        assertThat(rahmen.rechnungNetto()).isEqualByComparingTo("1000.00");
        assertThat(rahmen.rechnungBrutto()).isEqualByComparingTo("1190.00");
        assertThat(rahmen.bereitsGutgeschriebenNetto()).isEqualByComparingTo("150.00");
        assertThat(rahmen.bereitsGutgeschriebenBrutto()).isEqualByComparingTo("178.50");
        assertThat(rahmen.hoechstensNetto()).isEqualByComparingTo("850.00");
        assertThat(rahmen.hoechstensBrutto()).isEqualByComparingTo("1011.50");
        assertThat(rahmen.mwstSatz()).isEqualByComparingTo("0.19");
    }

    @Test
    void rahmen_unbekannteOderUngueltigeRechnung() {
        when(dokumentRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.gutschriftRahmen(9L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));

        AusgangsGeschaeftsDokument entwurf = rechnung();
        entwurf.setGebucht(false);
        when(dokumentRepository.findById(1L)).thenReturn(Optional.of(entwurf));
        assertThatThrownBy(() -> service.gutschriftRahmen(1L))
                .hasMessageContaining("nur zu einer gebuchten Rechnung");
    }

    @Test
    void offenerBetrag_ohneBruttoIstNull() {
        ProjektGeschaeftsdokument op = new ProjektGeschaeftsdokument();
        assertThat(op.getOffenerBetrag()).isNull();
        op.setBruttoBetrag(new BigDecimal("100.00"));
        op.setGutgeschrieben(null);
        assertThat(op.getOffenerBetrag()).isEqualByComparingTo("100.00");
    }

    @Test
    void buchenEinerRechnung_ruehrtKeineGutschriftLogikAn() {
        AusgangsGeschaeftsDokument r = rechnung();
        r.setGebucht(false);
        when(dokumentRepository.findById(1L)).thenReturn(Optional.of(r));
        when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.buchen(1L);

        ArgumentCaptor<String> nummer = ArgumentCaptor.forClass(String.class);
        verify(projektDokumentRepository, never()).findGeschaeftsdokumenteByDokumentid(nummer.capture());
    }
}
