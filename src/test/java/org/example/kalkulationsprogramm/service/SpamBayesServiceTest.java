package org.example.kalkulationsprogramm.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.SpamModelStats;
import org.example.kalkulationsprogramm.domain.SpamTokenCount;
import org.example.kalkulationsprogramm.repository.SpamModelStatsRepository;
import org.example.kalkulationsprogramm.repository.SpamTokenCountRepository;
import org.example.kalkulationsprogramm.service.spam.SpamSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpamBayesServiceTest {

    @Mock private SpamTokenCountRepository tokenRepo;
    @Mock private SpamModelStatsRepository statsRepo;

    private SpamBayesService service;

    @BeforeEach
    void setUp() {
        service = new SpamBayesService(tokenRepo, statsRepo);
        // loadModel() wird bei @PostConstruct aufgerufen, hier manuell:
        when(statsRepo.findByStatKey("total_spam")).thenReturn(Optional.empty());
        when(statsRepo.findByStatKey("total_ham")).thenReturn(Optional.empty());
        when(tokenRepo.findAll()).thenReturn(Collections.emptyList());
        service.refreshModel();
    }

    // ═══════════════════════════════════════════════════════════════
    // TOKENISIERUNG
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class Tokenisierung {

        @Test
        void tokenisiertEinfachenText() {
            Set<String> tokens = service.tokenize("Test Betreff", "Dies ist ein Body Text");

            assertThat(tokens).contains("test", "betreff", "dies", "body", "text");
        }

        @Test
        void entferntHtmlTags() {
            Set<String> tokens = service.tokenize(null, "<p>Hallo <b>Welt</b></p>");

            assertThat(tokens).contains("hallo", "welt");
            assertThat(tokens).noneMatch(t -> t.contains("<") || t.contains(">"));
        }

        @Test
        void filtertKurzeTokens() {
            Set<String> tokens = service.tokenize(null, "ab cd ef ghi jkl");

            // ab, cd, ef sind kürzer als 3 Zeichen
            assertThat(tokens).doesNotContain("ab", "cd", "ef");
            assertThat(tokens).contains("ghi", "jkl");
        }

        @Test
        void filtertReineZahlen() {
            Set<String> tokens = service.tokenize(null, "test 12345 hello 42 world");

            assertThat(tokens).doesNotContain("12345", "42");
            assertThat(tokens).contains("test", "hello", "world");
        }

        @Test
        void konvertiertZuLowerCase() {
            Set<String> tokens = service.tokenize("GROSSBUCHSTABEN", "MiXeD cAsE");

            assertThat(tokens).contains("grossbuchstaben", "mixed", "case");
        }

        @Test
        void unterstuetztUmlaute() {
            Set<String> tokens = service.tokenize(null, "Ärger Übung Öffnung Straße");

            assertThat(tokens).contains("ärger", "übung", "öffnung", "straße");
        }

        @Test
        void gibtLeeresMengeZurueckBeiNull() {
            Set<String> tokens = service.tokenize(null, null);

            assertThat(tokens).isEmpty();
        }

        @Test
        void gibtLeeresMengeZurueckBeiLeeremText() {
            Set<String> tokens = service.tokenize("", "");

            assertThat(tokens).isEmpty();
        }

        @Test
        void tokenisiertEmailEntity() {
            Email email = new Email();
            email.setSubject("Spam Betreff");
            email.setBody("Nachricht body text");

            Set<String> tokens = service.tokenize(email);

            assertThat(tokens).contains("spam", "betreff", "nachricht", "body", "text");
        }

        @Test
        void senderTokensAusFromAddress() {
            // Aus dem Sender werden Domain, TLD und ggf. Random-Marker
            // als praefixierte Tokens gewonnen, damit Bayes daraus lernen
            // kann (z.B. dass adventurecentral.com auffaellig oft Spam liefert).
            Email email = new Email();
            email.setSubject("Werbung");
            email.setBody("Klicken Sie hier");
            email.setFromAddress("jAWULxW.irYpfHK@adventurecentral.com");

            Set<String> tokens = service.tokenize(email);

            assertThat(tokens).contains(
                    "from_domain_adventurecentral.com",
                    "from_tld_com",
                    "from_random_local");
        }

        @Test
        void senderTokensOhneRandomMarkerFuerNormaleAdresse() {
            // Eine normale Adresse (lowercase, vorname.nachname) loest
            // den Random-Marker NICHT aus.
            Email email = new Email();
            email.setSubject("Anfrage");
            email.setBody("Bitte um Angebot");
            email.setFromAddress("max.mustermann@beispiel-firma.de");

            Set<String> tokens = service.tokenize(email);

            assertThat(tokens).contains("from_domain_beispiel-firma.de", "from_tld_de");
            assertThat(tokens).doesNotContain("from_random_local");
        }

        @Test
        void senderTokensExtrahiertAusBracketFormat() {
            // Format "Name <email@domain.com>" muss korrekt geparst werden.
            Email email = new Email();
            email.setSubject("Test");
            email.setBody("Body");
            email.setFromAddress("Max Mustermann <max@beispiel.de>");

            Set<String> tokens = service.tokenize(email);

            assertThat(tokens).contains("from_domain_beispiel.de", "from_tld_de");
        }

        @Test
        void senderTokensExtrahiertLetztesBracketPaarBeiDoppelBrackets() {
            // Robustheits-Test: Manche Mail-Server schreiben den Display-Name
            // mit eingeklammertem Spitznamen, was zu doppelten <...>-Paaren
            // fuehrt: "Foo <Bar> <real@x.de>". Die echte Adresse steht im
            // letzten Paar.
            Email email = new Email();
            email.setSubject("Test");
            email.setBody("Body");
            email.setFromAddress("Foo <Bar> <real@beispiel.de>");

            Set<String> tokens = service.tokenize(email);

            assertThat(tokens).contains("from_domain_beispiel.de", "from_tld_de");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MODELL-STATUS
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class ModellStatus {

        @Test
        void modellNichtBereitOhneTrainingsdaten() {
            assertThat(service.isModelReady()).isFalse();
        }

        @Test
        void modellBereitNachGenugTrainingsdaten() {
            SpamModelStats spamStat = new SpamModelStats();
            spamStat.setStatKey("total_spam");
            spamStat.setStatValue(15L);
            SpamModelStats hamStat = new SpamModelStats();
            hamStat.setStatKey("total_ham");
            hamStat.setStatValue(15L);

            when(statsRepo.findByStatKey("total_spam")).thenReturn(Optional.of(spamStat));
            when(statsRepo.findByStatKey("total_ham")).thenReturn(Optional.of(hamStat));
            when(tokenRepo.findAll()).thenReturn(Collections.emptyList());

            service.refreshModel();

            // 15 + 15 = 30 >= MIN_TRAINING_SAMPLES (20)
            assertThat(service.isModelReady()).isTrue();
        }

        @Test
        void modellNichtBereitMitZuWenigDaten() {
            SpamModelStats spamStat = new SpamModelStats();
            spamStat.setStatKey("total_spam");
            spamStat.setStatValue(5L);
            SpamModelStats hamStat = new SpamModelStats();
            hamStat.setStatKey("total_ham");
            hamStat.setStatValue(5L);

            when(statsRepo.findByStatKey("total_spam")).thenReturn(Optional.of(spamStat));
            when(statsRepo.findByStatKey("total_ham")).thenReturn(Optional.of(hamStat));
            when(tokenRepo.findAll()).thenReturn(Collections.emptyList());

            service.refreshModel();

            // 5 + 5 = 10 < MIN_TRAINING_SAMPLES (20)
            assertThat(service.isModelReady()).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PREDICTION
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class Prediction {

        @Test
        void gibtMinusEinsZurueckWennModellNichtBereit() {
            Set<String> tokens = Set.of("test", "hello");

            double result = service.predict(tokens);

            assertThat(result).isEqualTo(-1.0);
        }

        @Test
        void gibtMinusEinsZurueckBeiLeerenTokensWennModellNichtBereit() {
            double result = service.predict(Set.of());

            assertThat(result).isEqualTo(-1.0);
        }

        @Test
        void erkenntSpamTokensMitHoherWahrscheinlichkeit() {
            // Modell mit Spam-lastigen Tokens laden
            SpamModelStats spamStat = new SpamModelStats();
            spamStat.setStatKey("total_spam");
            spamStat.setStatValue(100L);
            SpamModelStats hamStat = new SpamModelStats();
            hamStat.setStatKey("total_ham");
            hamStat.setStatValue(100L);

            // "lottery" kommt 90x in Spam, 2x in Ham
            SpamTokenCount lottery = new SpamTokenCount();
            lottery.setToken("lottery");
            lottery.setSpamCount(90);
            lottery.setHamCount(2);

            // "winner" kommt 80x in Spam, 3x in Ham
            SpamTokenCount winner = new SpamTokenCount();
            winner.setToken("winner");
            winner.setSpamCount(80);
            winner.setHamCount(3);

            // "free" kommt 70x in Spam, 10x in Ham
            SpamTokenCount free = new SpamTokenCount();
            free.setToken("free");
            free.setSpamCount(70);
            free.setHamCount(10);

            when(statsRepo.findByStatKey("total_spam")).thenReturn(Optional.of(spamStat));
            when(statsRepo.findByStatKey("total_ham")).thenReturn(Optional.of(hamStat));
            when(tokenRepo.findAll()).thenReturn(java.util.List.of(lottery, winner, free));

            service.refreshModel();

            double spamProb = service.predict(Set.of("lottery", "winner", "free"));

            assertThat(spamProb).isGreaterThan(0.55); // Tendenz Richtung Spam
        }

        @Test
        void erkenntHamTokensMitNiedrigerWahrscheinlichkeit() {
            SpamModelStats spamStat = new SpamModelStats();
            spamStat.setStatKey("total_spam");
            spamStat.setStatValue(100L);
            SpamModelStats hamStat = new SpamModelStats();
            hamStat.setStatKey("total_ham");
            hamStat.setStatValue(100L);

            // "rechnung" kommt 2x in Spam, 80x in Ham
            SpamTokenCount rechnung = new SpamTokenCount();
            rechnung.setToken("rechnung");
            rechnung.setSpamCount(2);
            rechnung.setHamCount(80);

            // "angebot" kommt 1x in Spam, 70x in Ham
            SpamTokenCount angebot = new SpamTokenCount();
            angebot.setToken("angebot");
            angebot.setSpamCount(1);
            angebot.setHamCount(70);

            when(statsRepo.findByStatKey("total_spam")).thenReturn(Optional.of(spamStat));
            when(statsRepo.findByStatKey("total_ham")).thenReturn(Optional.of(hamStat));
            when(tokenRepo.findAll()).thenReturn(java.util.List.of(rechnung, angebot));

            service.refreshModel();

            double spamProb = service.predict(Set.of("rechnung", "angebot"));

            assertThat(spamProb).isLessThan(0.5); // Tendenz Richtung Ham
        }

        @Test
        void wahrscheinlichkeitZwischenNullUndEins() {
            SpamModelStats spamStat = new SpamModelStats();
            spamStat.setStatKey("total_spam");
            spamStat.setStatValue(50L);
            SpamModelStats hamStat = new SpamModelStats();
            hamStat.setStatKey("total_ham");
            hamStat.setStatValue(50L);

            SpamTokenCount token = new SpamTokenCount();
            token.setToken("test");
            token.setSpamCount(25);
            token.setHamCount(25);

            when(statsRepo.findByStatKey("total_spam")).thenReturn(Optional.of(spamStat));
            when(statsRepo.findByStatKey("total_ham")).thenReturn(Optional.of(hamStat));
            when(tokenRepo.findAll()).thenReturn(java.util.List.of(token));

            service.refreshModel();

            double prob = service.predict(Set.of("test"));

            assertThat(prob).isBetween(0.0, 1.0);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TRAINING
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class Training {

        @Test
        void trainTextRuftUpsertTokenAuf() {
            service.trainText("free lottery winner prize", true);

            verify(tokenRepo, atLeast(3)).upsertToken(anyString(), anyInt(), anyInt());
            verify(statsRepo).incrementStat("total_spam");
        }

        @Test
        void trainTextHamIncrementHamCounter() {
            service.trainText("Rechnung Angebot Dachsanierung", false);

            verify(tokenRepo, atLeast(2)).upsertToken(anyString(), eq(0), eq(1));
            verify(statsRepo).incrementStat("total_ham");
        }

        @Test
        void trainEmailNutztSubjectUndBody() {
            Email email = new Email();
            email.setSubject("Wichtige Rechnung");
            email.setBody("Anbei die Rechnung für das Projekt.");

            service.train(email, false);

            verify(tokenRepo, atLeast(3)).upsertToken(anyString(), eq(0), eq(1));
            verify(statsRepo).incrementStat("total_ham");
        }

        @Test
        void trainLeererTextKeinDbAufruf() {
            service.trainText("", true);

            verify(tokenRepo, never()).upsertToken(anyString(), anyInt(), anyInt());
            verify(statsRepo, never()).incrementStat(anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CSV/TSV BOOTSTRAP
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class Bootstrap {

        @Test
        void importiertTabSepariertesDatenset() throws IOException {
            String tsv = """
                       ham\tGo until jurong point, crazy.. Available only in bugis
                       spam\tFree entry in 2 a wkly comp to win FA Cup final
                       ham\tOk lar... Joking wif u oni...
                       spam\tWINNER!! You have been selected to receive a prize
                       """;

            InputStream stream = new ByteArrayInputStream(tsv.getBytes(StandardCharsets.UTF_8));

            int[] result = service.bootstrapFromCsv(stream);

            assertThat(result[0]).isEqualTo(2); // 2 Spam
            assertThat(result[1]).isEqualTo(2); // 2 Ham
        }

        @Test
        void importiertCsvMitHeader() throws IOException {
            String csv = """
                       v1,v2
                       spam,"Buy viagra now! Best deals"
                       ham,"Meeting tomorrow at 10am"
                       """;

            InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

            int[] result = service.bootstrapFromCsv(stream);

            assertThat(result[0]).isEqualTo(1); // 1 Spam
            assertThat(result[1]).isEqualTo(1); // 1 Ham
        }

        @Test
        void uebergehtUnbekanntesLabel() throws IOException {
            String tsv = """
                       ham\tNormale Nachricht
                       unknown\tDiese Zeile wird ignoriert
                       spam\tSpam Nachricht
                       """;

            InputStream stream = new ByteArrayInputStream(tsv.getBytes(StandardCharsets.UTF_8));

            int[] result = service.bootstrapFromCsv(stream);

            assertThat(result[0]).isEqualTo(1); // 1 Spam
            assertThat(result[1]).isEqualTo(1); // 1 Ham
        }

        @Test
        void uebergehtLeereZeilen() throws IOException {
            String tsv = """
                       ham\tErste Nachricht
                       
                         \s
                       spam\tZweite Nachricht
                       """;

            InputStream stream = new ByteArrayInputStream(tsv.getBytes(StandardCharsets.UTF_8));

            int[] result = service.bootstrapFromCsv(stream);

            assertThat(result[0]).isEqualTo(1);
            assertThat(result[1]).isEqualTo(1);
        }

        @Test
        void leererStreamGibtNullZurueck() throws IOException {
            InputStream stream = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));

            int[] result = service.bootstrapFromCsv(stream);

            assertThat(result[0]).isEqualTo(0);
            assertThat(result[1]).isEqualTo(0);
        }

        @Test
        void erkenntHuggingFaceFormat() throws IOException {
            // HuggingFace-Format: text,labels Header mit not_spam/spam
            String csv = """
                       text,labels
                       "Hallo, wie geht es dir?",not_spam
                       Kostenlos downloaden jetzt!,spam
                       Guten Morgen zusammen,not_spam
                       Schnell reich werden!,spam
                       Investiere jetzt und werde Millionär,spam
                       """;

            InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

            int[] result = service.bootstrapFromCsv(stream);

            assertThat(result[0]).as("Spam-Count").isEqualTo(3);
            assertThat(result[1]).as("Ham-Count").isEqualTo(2);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTER
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class StatusGetter {

        @Test
        void getTotalSpamGibtKorrektenWert() {
            assertThat(service.getTotalSpam()).isEqualTo(0);
        }

        @Test
        void getTotalHamGibtKorrektenWert() {
            assertThat(service.getTotalHam()).isEqualTo(0);
        }

        @Test
        void getVocabularySizeGibtKorrektenWert() {
            assertThat(service.getVocabularySize()).isEqualTo(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HILFEN FÜR ROBINSON/FISHER
    // ═══════════════════════════════════════════════════════════════

    private static SpamModelStats stat(String key, long wert) {
        SpamModelStats stat = new SpamModelStats();
        stat.setStatKey(key);
        stat.setStatValue(wert);
        return stat;
    }

    private static SpamTokenCount zaehler(String token, int spam, int ham) {
        SpamTokenCount tc = new SpamTokenCount();
        tc.setToken(token);
        tc.setSpamCount(spam);
        tc.setHamCount(ham);
        return tc;
    }

    /** Lädt ein Modell mit festen Mail-Summen und Token-Zählern in den Cache. */
    private void ladeModell(long totalSpam, long totalHam, List<SpamTokenCount> tokens) {
        when(statsRepo.findByStatKey("total_spam")).thenReturn(Optional.of(stat("total_spam", totalSpam)));
        when(statsRepo.findByStatKey("total_ham")).thenReturn(Optional.of(stat("total_ham", totalHam)));
        when(tokenRepo.findAll()).thenReturn(tokens);
        service.refreshModel();
    }

    private static EmailAttachment anhang(String dateiname, boolean inline) {
        EmailAttachment a = new EmailAttachment();
        a.setOriginalFilename(dateiname);
        a.setInlineAttachment(inline);
        return a;
    }

    // ═══════════════════════════════════════════════════════════════
    // ROBINSON/FISHER
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class RobinsonFisher {

        /*
         * Handgerechnetes Beispiel (x = 0,5, s_x = 0,45):
         *
         *   S = H = 100, Wort "gewinn": s = 90, h = 10
         *     p = (90/100) / (90/100 + 10/100) = 0,9
         *     n = 100, f = (0,45·0,5 + 100·0,9) / (0,45 + 100) = 90,225 / 100,45 = 0,898208
         *
         *   Ein Wort (k = 1): chi2Q(x, 2) = e^(−x/2)
         *     S' = 1 − e^(ln(1−f)) = f,  H' = 1 − e^(ln f) = 1 − f
         *     Ergebnis = (1 + f − (1 − f)) / 2 = f = 0,898208
         *
         *   Zwei solche Wörter (k = 2, v = 4): chi2Q(x, 4) = e^(−m)·(1 + m), m = x/2
         *     S-Seite: m = −2·ln(0,101792) = 4,56966 → chi2Q = 0,057710 → S' = 0,942290
         *     H-Seite: m = −2·ln(0,898208) = 0,21471 → chi2Q = 0,979999 → H' = 0,020001
         *     Ergebnis = (1 + 0,942290 − 0,020001) / 2 = 0,961144
         *
         *   S = 50, H = 200, Wort "aktion": s = 10, h = 10
         *     p = 0,2 / (0,2 + 0,05) = 0,8; n = 20; f = (0,225 + 16) / 20,45 = 0,793399
         */

        @Test
        void einzelnesWortErgibtSeineGeglaetteteWahrscheinlichkeit() {
            ladeModell(100, 100, List.of(zaehler("gewinn", 90, 10)));

            assertThat(service.predict(Set.of("gewinn"))).isCloseTo(0.898208, within(1e-6));
        }

        @Test
        void zweiSpamWoerterNachFisherHandgerechnet() {
            ladeModell(100, 100, List.of(zaehler("gewinn", 90, 10), zaehler("jackpot", 90, 10)));

            assertThat(service.predict(Set.of("gewinn", "jackpot"))).isCloseTo(0.961144, within(1e-6));
        }

        @Test
        void robinsonBeruecksichtigtUnterschiedlicheMailSummen() {
            assertThat(SpamBayesService.geglaetteteWahrscheinlichkeit(10, 10, 50, 200))
                    .isCloseTo(0.793399, within(1e-6));
        }

        @Test
        void gegenlaeufigeWoerterHebenSichAuf() {
            ladeModell(100, 100, List.of(zaehler("gewinn", 90, 10), zaehler("angebot", 10, 90)));

            assertThat(service.predict(Set.of("gewinn", "angebot"))).isCloseTo(0.5, within(1e-9));
        }

        @Test
        void unbekannteWoerterSindNeutral() {
            ladeModell(100, 100, List.of(zaehler("gewinn", 90, 10)));

            assertThat(service.predict(Set.of("gewinn", "unbekannt1", "unbekannt2")))
                    .isCloseTo(0.898208, within(1e-6));
            assertThat(SpamBayesService.geglaetteteWahrscheinlichkeit(0, 0, 100, 100)).isEqualTo(0.5);
        }

        @Test
        void alleWoerterNeutralErgibtEinhalb() {
            // s = 11, h = 9 → f = 0,5489, Abweichung 0,049 < 0,1 → zählt nicht
            ladeModell(100, 100, List.of(zaehler("hallo", 11, 9), zaehler("gruss", 50, 50)));

            assertThat(service.predict(Set.of("hallo", "gruss"))).isEqualTo(0.5);
        }

        @Test
        void keineTokensErgibtEinhalbWennModellBereit() {
            ladeModell(100, 100, List.of());

            assertThat(service.predict(Set.of())).isEqualTo(0.5);
            assertThat(service.predict(null)).isEqualTo(0.5);
        }

        @Test
        void nurSpamWoerterNaheEins() {
            List<SpamTokenCount> tokens = new ArrayList<>();
            Set<String> mail = new LinkedHashSet<>();
            for (int i = 0; i < 10; i++) {
                tokens.add(zaehler("spam" + i, 95, 1));
                mail.add("spam" + i);
            }
            ladeModell(100, 100, tokens);

            assertThat(service.predict(mail)).isGreaterThan(0.99);
        }

        @Test
        void nurHamWoerterNaheNull() {
            List<SpamTokenCount> tokens = new ArrayList<>();
            Set<String> mail = new LinkedHashSet<>();
            for (int i = 0; i < 10; i++) {
                tokens.add(zaehler("ham" + i, 1, 95));
                mail.add("ham" + i);
            }
            ladeModell(100, 100, tokens);

            assertThat(service.predict(mail)).isLessThan(0.01);
        }

        @Test
        void hoechstens150DeutlichsteWoerter() {
            List<SpamTokenCount> tokens = new ArrayList<>();
            Set<String> mail = new LinkedHashSet<>();
            // 150 sehr deutliche Ham-Wörter …
            for (int i = 0; i < 150; i++) {
                tokens.add(zaehler("ham" + i, 0, 100));
                mail.add("ham" + i);
            }
            // … und 50 schwächere Spam-Wörter, die hinter der Grenze landen
            for (int i = 0; i < 50; i++) {
                tokens.add(zaehler("spam" + i, 8, 2));
                mail.add("spam" + i);
            }
            ladeModell(100, 100, tokens);

            List<Double> gewaehlt = service.deutlichsteWahrscheinlichkeiten(mail);

            assertThat(gewaehlt).hasSize(SpamBayesService.MAX_BEWERTETE_TOKENS);
            assertThat(gewaehlt).allMatch(f -> f < 0.01);
            assertThat(service.predict(mail)).isLessThan(0.01);
        }

        @Test
        void keineSpamMailsGelerntOhneFehler() {
            // S = 0: Spam-Anteil zählt als 0 → reine Ham-Wörter, Spam-Zähler ohne Bezug neutral
            ladeModell(0, 30, List.of(zaehler("rechnung", 0, 20), zaehler("komisch", 5, 0)));

            double ergebnis = service.predict(Set.of("rechnung", "komisch"));

            assertThat(ergebnis).isBetween(0.0, 0.1);
            assertThat(SpamBayesService.geglaetteteWahrscheinlichkeit(5, 0, 0, 30)).isEqualTo(0.5);
        }

        @Test
        void keineHamMailsGelerntOhneFehler() {
            ladeModell(30, 0, List.of(zaehler("gewinn", 20, 0)));

            double ergebnis = service.predict(Set.of("gewinn"));

            assertThat(ergebnis).isBetween(0.9, 1.0);
        }

        @Test
        void chi2QBekannteWerte() {
            // v = 2: e^(−x/2); v = 4: e^(−1)·(1 + 1) bei x = 2
            assertThat(SpamBayesService.chi2Q(2.0, 2)).isCloseTo(Math.exp(-1), within(1e-12));
            assertThat(SpamBayesService.chi2Q(2.0, 4)).isCloseTo(2 * Math.exp(-1), within(1e-12));
            assertThat(SpamBayesService.chi2Q(0.0, 4)).isEqualTo(1.0);
            assertThat(SpamBayesService.chi2Q(Double.NaN, 4)).isEqualTo(1.0);
        }

        @Test
        void chi2QBleibtBeiGrossenWertenStabil() {
            // e^(−m) wäre bei m = 1000 schon 0; die Summe im Logarithmus bleibt endlich und in [0, 1]
            double klein = SpamBayesService.chi2Q(2000.0, 300);
            double gross = SpamBayesService.chi2Q(1.0, 300);

            assertThat(klein).isBetween(0.0, 1e-100);
            assertThat(gross).isCloseTo(1.0, within(1e-12)).isLessThanOrEqualTo(1.0);
        }

        @Test
        void chi2QVerlangtGeradeFreiheitsgrade() {
            assertThatThrownBy(() -> SpamBayesService.chi2Q(1.0, 3))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SpamBayesService.chi2Q(1.0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ZERLEGER VERSION 2
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class ZerlegerVersion2 {

        private Email beispielMail() {
            Email email = new Email();
            email.setSubject("Angebot");
            email.setBody("Bitte um Angebot");
            email.setFromAddress("Max Mustermann <max.mustermann@gmx.de>");
            email.setReplyToAddress("kasse@abzocke-beispiel.com");
            email.addAttachment(anhang("Rechnung.PDF", false));
            email.addAttachment(anhang("rechnung.exe", false));
            email.addAttachment(anhang("logo.png", true));
            return email;
        }

        @Test
        void version2EnthaeltKopfAnhangUndAlteTokens() {
            Set<String> tokens = service.tokenize(beispielMail());

            assertThat(tokens).contains(
                    "angebot", "from_domain_gmx.de", "from_tld_de",
                    "from_freemail", "replyto_domain_abzocke-beispiel.com", "replyto_abweichend",
                    "anhang_pdf", "anhang_exe", "anhang_gefaehrlich");
            // eingebettete Bilder sind kein Anhang
            assertThat(tokens).doesNotContain("anhang_png");
        }

        @Test
        void version1EnthaeltKeineNeuenTokens() {
            Email email = beispielMail();

            Set<String> v1 = service.tokenize(email, 1);
            Set<String> v2 = service.tokenize(email, 2);

            assertThat(v1).noneMatch(t -> t.startsWith("replyto_") || t.startsWith("anhang_")
                    || t.startsWith("signal_") || t.equals("from_freemail"));
            assertThat(v2).containsAll(v1);
        }

        @Test
        void gleicheHauptDomainIstKeineAbweichung() {
            Email email = new Email();
            email.setFromAddress("info@beispiel-firma.de");
            email.setReplyToAddress("Buchhaltung <buero@mail.beispiel-firma.de>");

            Set<String> tokens = service.tokenize(email);

            assertThat(tokens).contains("replyto_domain_mail.beispiel-firma.de");
            assertThat(tokens).doesNotContain("replyto_abweichend", "from_freemail");
        }

        @Test
        void ohneReplyToKeineReplyToTokens() {
            Email email = new Email();
            email.setFromAddress("info@beispiel-firma.de");

            assertThat(service.tokenize(email)).noneMatch(t -> t.startsWith("replyto_"));
        }

        @Test
        void ohneAbsenderKeineAbweichung() {
            Email email = new Email();
            email.setReplyToAddress("kunde@beispiel.de");

            Set<String> tokens = service.tokenize(email);

            assertThat(tokens).contains("replyto_domain_beispiel.de");
            assertThat(tokens).doesNotContain("replyto_abweichend");
        }

        @Test
        void hoechstensFuenfAnhangEndungenAlphabetisch() {
            Email email = new Email();
            for (String name : List.of("a.zip", "b.xls", "c.doc", "d.pdf", "e.jpg", "f.csv", "g.txt")) {
                email.addAttachment(anhang(name, false));
            }

            Set<String> tokens = service.tokenize(email);

            assertThat(tokens).filteredOn(t -> t.startsWith("anhang_"))
                    .containsExactlyInAnyOrder("anhang_csv", "anhang_doc", "anhang_jpg", "anhang_pdf", "anhang_txt");
        }

        @Test
        void ungueltigeEndungenWerdenUebergangen() {
            Email email = new Email();
            email.addAttachment(anhang("ohne_endung", false));
            email.addAttachment(anhang("punkt_am_ende.", false));
            email.addAttachment(anhang("sehr.langeendungxyz", false));
            email.addAttachment(anhang("sonder.p-f", false));
            email.addAttachment(anhang(null, false));
            email.getAttachments().add(null);

            assertThat(service.tokenize(email)).noneMatch(t -> t.startsWith("anhang_"));
        }

        @Test
        void gefaehrlichAuchBeiEingebettetemAnhang() {
            Email email = new Email();
            email.addAttachment(anhang("bild.js", true));

            Set<String> tokens = service.tokenize(email);

            assertThat(tokens).contains("anhang_gefaehrlich");
            assertThat(tokens).doesNotContain("anhang_js");
        }

        @Test
        void signalTokensAusSpamSignalen() {
            List<String> tokens = SpamBayesService.signalTokens(
                    EnumSet.of(SpamSignal.ANTWORT_UMGELEITET, SpamSignal.ABSENDER_GEFAELSCHT));

            assertThat(tokens).containsExactly("signal_absender_gefaelscht", "signal_antwort_umgeleitet");
        }

        @Test
        void keineSignaleKeineTokens() {
            assertThat(SpamBayesService.signalTokens(Set.of())).isEmpty();
            assertThat(SpamBayesService.signalTokens(null)).isEmpty();
        }

        @Test
        void hoehereVersionWirdWieAktuelleBehandelt() {
            Email email = beispielMail();

            assertThat(service.tokenize(email, 99)).isEqualTo(service.tokenize(email, 2));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VERSIONIERTES TRAINING
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class VersioniertesTraining {

        private Email mailMitReplyTo() {
            Email email = new Email();
            email.setSubject("Gewinn");
            email.setBody("Sie haben gewonnen");
            email.setFromAddress("max.mustermann@gmx.de");
            email.setReplyToAddress("kasse@abzocke-beispiel.com");
            return email;
        }

        @Test
        void trainSetztVersion2() {
            Email email = mailMitReplyTo();

            service.train(email, true);

            assertThat(email.getSpamTokenVersion()).isEqualTo((short) 2);
            verify(tokenRepo).upsertToken("replyto_abweichend", 1, 0);
            verify(tokenRepo).upsertToken("from_freemail", 1, 0);
        }

        @Test
        void untrainOhneVersionNimmtNurVersion1TokensZurueck() {
            Email email = mailMitReplyTo(); // spamTokenVersion = null → Version 1

            service.untrain(email, true);

            verify(tokenRepo).decrementToken("gewinn", 1, 0);
            verify(tokenRepo).decrementToken("from_domain_gmx.de", 1, 0);
            verify(tokenRepo, never()).decrementToken(eq("replyto_abweichend"), anyInt(), anyInt());
            verify(tokenRepo, never()).decrementToken(eq("from_freemail"), anyInt(), anyInt());
            verify(tokenRepo, never()).decrementToken(eq("replyto_domain_abzocke-beispiel.com"), anyInt(), anyInt());
            verify(statsRepo).decrementStat("total_spam");
            assertThat(email.getSpamTokenVersion()).isNull();
        }

        @Test
        void untrainMitVersion2NimmtNeueTokensZurueck() {
            Email email = mailMitReplyTo();
            email.setSpamTokenVersion((short) 2);

            service.untrain(email, false);

            verify(tokenRepo).decrementToken("replyto_abweichend", 0, 1);
            verify(tokenRepo).decrementToken("from_freemail", 0, 1);
            verify(statsRepo).decrementStat("total_ham");
        }

        @Test
        void untrainDannTrainWechseltAufVersion2() {
            Email email = mailMitReplyTo();

            service.untrain(email, false);
            service.train(email, true);

            verify(tokenRepo, never()).decrementToken(eq("replyto_abweichend"), anyInt(), anyInt());
            verify(tokenRepo).upsertToken("replyto_abweichend", 1, 0);
            assertThat(email.getSpamTokenVersion()).isEqualTo((short) 2);
        }

        @Test
        void trainAktualisiertCacheFuerSofortigeVorhersage() {
            ladeModell(10, 10, List.of());

            Email email = mailMitReplyTo();
            service.train(email, true);

            assertThat(service.getTotalSpam()).isEqualTo(11);
            assertThat(service.predict(Set.of("replyto_abweichend"))).isGreaterThan(0.5);
        }
    }
}
