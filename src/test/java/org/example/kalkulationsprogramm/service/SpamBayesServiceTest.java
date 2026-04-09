package org.example.kalkulationsprogramm.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.SpamModelStats;
import org.example.kalkulationsprogramm.domain.SpamTokenCount;
import org.example.kalkulationsprogramm.repository.SpamModelStatsRepository;
import org.example.kalkulationsprogramm.repository.SpamTokenCountRepository;
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
        void gibtMinusEinsZurueckBeiLeerenTokens() {
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

            // Multinomial NB mit diesen Token-Verteilungen ergibt ~0.62
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

            // Multinomial NB mit diesen Token-Verteilungen ergibt ~0.49
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
            String tsv = "ham\tGo until jurong point, crazy.. Available only in bugis\n"
                       + "spam\tFree entry in 2 a wkly comp to win FA Cup final\n"
                       + "ham\tOk lar... Joking wif u oni...\n"
                       + "spam\tWINNER!! You have been selected to receive a prize\n";

            InputStream stream = new ByteArrayInputStream(tsv.getBytes(StandardCharsets.UTF_8));

            int[] result = service.bootstrapFromCsv(stream);

            assertThat(result[0]).isEqualTo(2); // 2 Spam
            assertThat(result[1]).isEqualTo(2); // 2 Ham
        }

        @Test
        void importiertCsvMitHeader() throws IOException {
            String csv = "v1,v2\n"
                       + "spam,\"Buy viagra now! Best deals\"\n"
                       + "ham,\"Meeting tomorrow at 10am\"\n";

            InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

            int[] result = service.bootstrapFromCsv(stream);

            assertThat(result[0]).isEqualTo(1); // 1 Spam
            assertThat(result[1]).isEqualTo(1); // 1 Ham
        }

        @Test
        void uebergehtUnbekanntesLabel() throws IOException {
            String tsv = "ham\tNormale Nachricht\n"
                       + "unknown\tDiese Zeile wird ignoriert\n"
                       + "spam\tSpam Nachricht\n";

            InputStream stream = new ByteArrayInputStream(tsv.getBytes(StandardCharsets.UTF_8));

            int[] result = service.bootstrapFromCsv(stream);

            assertThat(result[0]).isEqualTo(1); // 1 Spam
            assertThat(result[1]).isEqualTo(1); // 1 Ham
        }

        @Test
        void uebergehtLeereZeilen() throws IOException {
            String tsv = "ham\tErste Nachricht\n"
                       + "\n"
                       + "   \n"
                       + "spam\tZweite Nachricht\n";

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
            String csv = "text,labels\n"
                       + "\"Hallo, wie geht es dir?\",not_spam\n"
                       + "Kostenlos downloaden jetzt!,spam\n"
                       + "Guten Morgen zusammen,not_spam\n"
                       + "Schnell reich werden!,spam\n"
                       + "Investiere jetzt und werde Millionär,spam\n";

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
}
