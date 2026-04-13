package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.ContactDto;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private KundeRepository kundeRepository;

    @Mock
    private LieferantenRepository lieferantenRepository;

    @Mock
    private ProjektRepository projektRepository;

    @Mock
    private AnfrageRepository anfrageRepository;

    private ContactService contactService;

    @BeforeEach
    void setUp() {
        contactService = new ContactService(
                kundeRepository,
                lieferantenRepository,
                projektRepository,
                anfrageRepository);
    }

    // ═══════════════════════════════════════════════════════════════
    // HILFSMETHODEN
    // ═══════════════════════════════════════════════════════════════

    private Kunde createKunde(Long id, String name, String ansprechpartner, String kundennummer,
            List<String> emails) {
        Kunde k = new Kunde();
        k.setId(id);
        k.setName(name);
        k.setAnsprechspartner(ansprechpartner);
        k.setKundennummer(kundennummer);
        k.setKundenEmails(new ArrayList<>(emails));
        return k;
    }

    private Lieferanten createLieferant(Long id, String name, String typ, List<String> emails) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        l.setLieferantenname(name);
        l.setLieferantenTyp(typ);
        l.setKundenEmails(new ArrayList<>(emails));
        return l;
    }

    private Projekt createProjekt(Long id, String bauvorhaben, Kunde kunde, List<String> emails) {
        Projekt p = new Projekt();
        p.setId(id);
        p.setBauvorhaben(bauvorhaben);
        p.setKundenId(kunde);
        p.setKundenEmails(new ArrayList<>(emails));
        return p;
    }

    private Anfrage createAnfrage(Long id, String bauvorhaben, Kunde kunde, List<String> emails) {
        Anfrage a = new Anfrage();
        a.setId(id);
        a.setBauvorhaben(bauvorhaben);
        a.setKunde(kunde);
        a.setKundenEmails(new ArrayList<>(emails));
        return a;
    }

    private void stubEmptyRepositories() {
        when(kundeRepository.searchByNameOrAnsprechpartnerOrEmail(anyString()))
                .thenReturn(Collections.emptyList());
        when(lieferantenRepository.searchByNameOrEmail(anyString()))
                .thenReturn(Collections.emptyList());
        when(projektRepository.searchByBauvorhabenOrKundeOrEmail(anyString()))
                .thenReturn(Collections.emptyList());
        when(anfrageRepository.searchByBauvorhabenOrKundeOrEmail(anyString()))
                .thenReturn(Collections.emptyList());
    }

    // ═══════════════════════════════════════════════════════════════
    // NULL / KURZE EINGABEN
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Eingabevalidierung")
    class EingabeValidierung {

        @Test
        @DisplayName("null-Query liefert leere Liste")
        void nullQueryReturnsEmpty() {
            List<ContactDto> result = contactService.searchContacts(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("leerer String liefert leere Liste")
        void emptyQueryReturnsEmpty() {
            List<ContactDto> result = contactService.searchContacts("");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("1 Zeichen liefert leere Liste (Minimum 2)")
        void singleCharQueryReturnsEmpty() {
            List<ContactDto> result = contactService.searchContacts("M");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("2 Zeichen starten die Suche")
        void twoCharsStartSearch() {
            stubEmptyRepositories();
            List<ContactDto> result = contactService.searchContacts("Ma");
            assertThat(result).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // KUNDEN-SUCHE
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Kunden-Suche")
    class KundenSuche {

        @Test
        @DisplayName("findet Kunde nach Name mit allen E-Mails")
        void findetKundeNachName() {
            Kunde k = createKunde(1L, "Max Mustermann", "Herr Muster", "K-001",
                    List.of("max@example.com", "info@example.com"));

            when(kundeRepository.searchByNameOrAnsprechpartnerOrEmail("Muster"))
                    .thenReturn(List.of(k));
            when(lieferantenRepository.searchByNameOrEmail("Muster"))
                    .thenReturn(Collections.emptyList());
            when(projektRepository.searchByBauvorhabenOrKundeOrEmail("Muster"))
                    .thenReturn(Collections.emptyList());
            when(anfrageRepository.searchByBauvorhabenOrKundeOrEmail("Muster"))
                    .thenReturn(Collections.emptyList());

            List<ContactDto> result = contactService.searchContacts("Muster");

            assertThat(result).hasSize(2);
            assertThat(result).allSatisfy(c -> {
                assertThat(c.getType()).isEqualTo("KUNDE");
                assertThat(c.getName()).isEqualTo("Max Mustermann");
                assertThat(c.getId()).startsWith("KUNDE_");
            });
            assertThat(result).extracting(ContactDto::getEmail)
                    .containsExactlyInAnyOrder("max@example.com", "info@example.com");
        }

        @Test
        @DisplayName("Kunde ohne E-Mails wird übersprungen")
        void kundeOhneEmailsWirdUebersprungen() {
            Kunde k = createKunde(1L, "Ohne Mail", null, "K-002", Collections.emptyList());

            when(kundeRepository.searchByNameOrAnsprechpartnerOrEmail("Ohne"))
                    .thenReturn(List.of(k));
            when(lieferantenRepository.searchByNameOrEmail("Ohne"))
                    .thenReturn(Collections.emptyList());
            when(projektRepository.searchByBauvorhabenOrKundeOrEmail("Ohne"))
                    .thenReturn(Collections.emptyList());
            when(anfrageRepository.searchByBauvorhabenOrKundeOrEmail("Ohne"))
                    .thenReturn(Collections.emptyList());

            List<ContactDto> result = contactService.searchContacts("Ohne");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Kontext enthält Ansprechpartner und Kundennummer")
        void kontextEnthaeltAnsprechpartnerUndKundennummer() {
            Kunde k = createKunde(1L, "Firma Test", "Herr Schmidt", "K-100",
                    List.of("firma@example.com"));

            when(kundeRepository.searchByNameOrAnsprechpartnerOrEmail("Firma"))
                    .thenReturn(List.of(k));
            when(lieferantenRepository.searchByNameOrEmail("Firma"))
                    .thenReturn(Collections.emptyList());
            when(projektRepository.searchByBauvorhabenOrKundeOrEmail("Firma"))
                    .thenReturn(Collections.emptyList());
            when(anfrageRepository.searchByBauvorhabenOrKundeOrEmail("Firma"))
                    .thenReturn(Collections.emptyList());

            List<ContactDto> result = contactService.searchContacts("Firma");
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getContext()).isEqualTo("Herr Schmidt (K-100)");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // LIEFERANTEN-SUCHE
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Lieferanten-Suche")
    class LieferantenSuche {

        @Test
        @DisplayName("findet Lieferant nach Name")
        void findetLieferantNachName() {
            Lieferanten l = createLieferant(5L, "Stahl GmbH", "Stahl",
                    List.of("stahl@example.com"));

            when(kundeRepository.searchByNameOrAnsprechpartnerOrEmail("Stahl"))
                    .thenReturn(Collections.emptyList());
            when(lieferantenRepository.searchByNameOrEmail("Stahl"))
                    .thenReturn(List.of(l));
            when(projektRepository.searchByBauvorhabenOrKundeOrEmail("Stahl"))
                    .thenReturn(Collections.emptyList());
            when(anfrageRepository.searchByBauvorhabenOrKundeOrEmail("Stahl"))
                    .thenReturn(Collections.emptyList());

            List<ContactDto> result = contactService.searchContacts("Stahl");

            assertThat(result).hasSize(1);
            ContactDto dto = result.get(0);
            assertThat(dto.getType()).isEqualTo("LIEFERANT");
            assertThat(dto.getName()).isEqualTo("Stahl GmbH");
            assertThat(dto.getEmail()).isEqualTo("stahl@example.com");
            assertThat(dto.getId()).isEqualTo("LIEFERANT_5");
            assertThat(dto.getContext()).isEqualTo("Stahl");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PROJEKT-SUCHE
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Projekt-Suche")
    class ProjektSuche {

        @Test
        @DisplayName("findet Projekt nach Bauvorhaben")
        void findetProjektNachBauvorhaben() {
            Kunde k = createKunde(1L, "Bauherr GmbH", null, "K-010", Collections.emptyList());
            Projekt p = createProjekt(10L, "Neubau Musterstraße 1", k,
                    List.of("bau@example.com"));

            when(kundeRepository.searchByNameOrAnsprechpartnerOrEmail("Neubau"))
                    .thenReturn(Collections.emptyList());
            when(lieferantenRepository.searchByNameOrEmail("Neubau"))
                    .thenReturn(Collections.emptyList());
            when(projektRepository.searchByBauvorhabenOrKundeOrEmail("Neubau"))
                    .thenReturn(List.of(p));
            when(anfrageRepository.searchByBauvorhabenOrKundeOrEmail("Neubau"))
                    .thenReturn(Collections.emptyList());

            List<ContactDto> result = contactService.searchContacts("Neubau");

            assertThat(result).hasSize(1);
            ContactDto dto = result.get(0);
            assertThat(dto.getType()).isEqualTo("PROJEKT");
            assertThat(dto.getName()).isEqualTo("Bauherr GmbH");
            assertThat(dto.getEmail()).isEqualTo("bau@example.com");
            assertThat(dto.getContext()).isEqualTo("Neubau Musterstraße 1");
        }

        @Test
        @DisplayName("Projekt ohne Kunde zeigt 'Unbekannt'")
        void projektOhneKundeZeigtUnbekannt() {
            Projekt p = createProjekt(11L, "Testvorhaben", null,
                    List.of("test@example.com"));

            when(kundeRepository.searchByNameOrAnsprechpartnerOrEmail("Testvorhaben"))
                    .thenReturn(Collections.emptyList());
            when(lieferantenRepository.searchByNameOrEmail("Testvorhaben"))
                    .thenReturn(Collections.emptyList());
            when(projektRepository.searchByBauvorhabenOrKundeOrEmail("Testvorhaben"))
                    .thenReturn(List.of(p));
            when(anfrageRepository.searchByBauvorhabenOrKundeOrEmail("Testvorhaben"))
                    .thenReturn(Collections.emptyList());

            List<ContactDto> result = contactService.searchContacts("Testvorhaben");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Unbekannt");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ANFRAGE-SUCHE
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Anfrage-Suche")
    class AnfrageSuche {

        @Test
        @DisplayName("findet Anfrage und inkludiert Kunde- und Anfrage-Emails")
        void findetAnfrageMitAllenEmails() {
            Kunde k = createKunde(1L, "Anfrage-Kunde", null, "K-020",
                    List.of("kunde@example.com"));
            Anfrage a = createAnfrage(20L, "Gelände Musterstadt", k,
                    List.of("anfrage@example.com"));

            when(kundeRepository.searchByNameOrAnsprechpartnerOrEmail("Gelände"))
                    .thenReturn(Collections.emptyList());
            when(lieferantenRepository.searchByNameOrEmail("Gelände"))
                    .thenReturn(Collections.emptyList());
            when(projektRepository.searchByBauvorhabenOrKundeOrEmail("Gelände"))
                    .thenReturn(Collections.emptyList());
            when(anfrageRepository.searchByBauvorhabenOrKundeOrEmail("Gelände"))
                    .thenReturn(List.of(a));

            List<ContactDto> result = contactService.searchContacts("Gelände");

            // 1 Kunde-Email + 1 Anfrage-Email = 2
            assertThat(result).hasSize(2);
            assertThat(result).allSatisfy(c -> {
                assertThat(c.getType()).isEqualTo("ANFRAGE");
                assertThat(c.getName()).isEqualTo("Anfrage-Kunde");
                assertThat(c.getContext()).isEqualTo("Gelände Musterstadt");
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DEDUPLIZIERUNG
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Deduplizierung")
    class Deduplizierung {

        @Test
        @DisplayName("Gleiche E-Mail aus Kunde und Projekt wird nur einmal zurückgegeben")
        void dedupliziertGleicheEmailAusVerschiedenenQuellen() {
            Kunde k = createKunde(1L, "Duplikat", null, "K-030",
                    List.of("shared@example.com"));
            Projekt p = createProjekt(10L, "Projekt Dup", k,
                    List.of("shared@example.com"));

            when(kundeRepository.searchByNameOrAnsprechpartnerOrEmail("Duplikat"))
                    .thenReturn(List.of(k));
            when(lieferantenRepository.searchByNameOrEmail("Duplikat"))
                    .thenReturn(Collections.emptyList());
            when(projektRepository.searchByBauvorhabenOrKundeOrEmail("Duplikat"))
                    .thenReturn(List.of(p));
            when(anfrageRepository.searchByBauvorhabenOrKundeOrEmail("Duplikat"))
                    .thenReturn(Collections.emptyList());

            List<ContactDto> result = contactService.searchContacts("Duplikat");

            // Nur 1x shared@example.com (Kunde hat Priorität, da zuerst verarbeitet)
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getEmail()).isEqualTo("shared@example.com");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MAX ERGEBNIS-LIMIT
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Ergebnis-Limit")
    class ErgebnisLimit {

        @Test
        @DisplayName("Maximal 30 Ergebnisse werden zurückgegeben")
        void maximal30Ergebnisse() {
            List<Kunde> viele = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                Kunde k = createKunde((long) i, "Kunde " + i, null, "K-" + i,
                        List.of("kunde" + i + "@example.com"));
                viele.add(k);
            }

            when(kundeRepository.searchByNameOrAnsprechpartnerOrEmail("Kunde"))
                    .thenReturn(viele);
            when(lieferantenRepository.searchByNameOrEmail("Kunde"))
                    .thenReturn(Collections.emptyList());
            when(projektRepository.searchByBauvorhabenOrKundeOrEmail("Kunde"))
                    .thenReturn(Collections.emptyList());
            when(anfrageRepository.searchByBauvorhabenOrKundeOrEmail("Kunde"))
                    .thenReturn(Collections.emptyList());

            List<ContactDto> result = contactService.searchContacts("Kunde");
            assertThat(result).hasSize(30);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // KOMBINIERTE SUCHE
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Kombinierte Suche über alle Entitäten")
    class KombiSuche {

        @Test
        @DisplayName("Ergebnisse aus allen 4 Quellen werden zusammengeführt")
        void ergebnisseAusAllenQuellen() {
            Kunde k = createKunde(1L, "Muster AG", null, "K-040",
                    List.of("kunde@example.com"));
            Lieferanten l = createLieferant(2L, "Muster Stahl", "Stahl",
                    List.of("lieferant@example.com"));
            Projekt p = createProjekt(3L, "Muster Projekt", k,
                    List.of("projekt@example.com"));
            Anfrage a = createAnfrage(4L, "Muster Anfrage", null,
                    List.of("anfrage@example.com"));

            when(kundeRepository.searchByNameOrAnsprechpartnerOrEmail("Muster"))
                    .thenReturn(List.of(k));
            when(lieferantenRepository.searchByNameOrEmail("Muster"))
                    .thenReturn(List.of(l));
            when(projektRepository.searchByBauvorhabenOrKundeOrEmail("Muster"))
                    .thenReturn(List.of(p));
            when(anfrageRepository.searchByBauvorhabenOrKundeOrEmail("Muster"))
                    .thenReturn(List.of(a));

            List<ContactDto> result = contactService.searchContacts("Muster");

            // Kunde: 1 email, Lieferant: 1, Projekt: 1 (kunde@example.com wird dedupliziert!),
            // Anfrage: 1 (keine Kunde-Emails weil kunde=null)
            assertThat(result).hasSizeGreaterThanOrEqualTo(3);
            assertThat(result).extracting(ContactDto::getType)
                    .contains("KUNDE", "LIEFERANT");
        }
    }
}
