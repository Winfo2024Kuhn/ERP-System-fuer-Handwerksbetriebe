package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.Version;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sichert das optimistische Sperren (@Version) auf allen 14 Aggregate-Root-
 * Entities ab, die ein Editor tatsaechlich schreibt (siehe Implementierungsplan,
 * Abschnitt "Aggregate-Roots fuer @Version - verifizierte Liste"). Ein
 * Reflection-Test ueber eine Liste statt 14 Einzeltests, damit eine vergessene
 * Klasse sofort auffaellt und nicht erst beim naechsten produktiven
 * Versionskonflikt zwischen zwei gleichzeitigen Bearbeitern.
 *
 * <p>Negativ abgesichert: die JOINED-Subklassen von {@link Artikel} und die
 * Kind-Entitaeten (Positionen/Bloecke) duerfen KEIN eigenes @Version tragen -
 * gespeichert wird immer ueber den Wurzel-Aggregat, dessen Version als
 * Waechter reicht (siehe Spec, Abschnitt "Optimistisches Sperren").
 */
class AggregatVersionTest {

    /**
     * Die 14 verifizierten Aggregate-Roots (Klasse -&gt; Tabelle laut Plan):
     * Projekt/projekt, Anfrage/anfrage, Kunde/kunde, Lieferanten/lieferanten,
     * Artikel/artikel (Wurzel der JOINED-Vererbung), Mitarbeiter/mitarbeiter,
     * AusgangsGeschaeftsDokument/ausgangs_geschaeftsdokument,
     * LieferantDokument/lieferant_dokument, Beleg/beleg,
     * Textbaustein/textbaustein, Produktkategorie/produktkategorie,
     * Arbeitsgang/arbeitsgang, Firmeninformation/firmeninformation,
     * LieferantReklamation/lieferant_reklamation.
     */
    @ParameterizedTest(name = "{0} hat genau ein @Version-Feld vom Typ Long namens \"version\"")
    @ValueSource(classes = {
            Projekt.class,
            Anfrage.class,
            Kunde.class,
            Lieferanten.class,
            Artikel.class,
            Mitarbeiter.class,
            AusgangsGeschaeftsDokument.class,
            LieferantDokument.class,
            Beleg.class,
            Textbaustein.class,
            Produktkategorie.class,
            Arbeitsgang.class,
            Firmeninformation.class,
            LieferantReklamation.class,
    })
    void aggregatRootHatVersionsfeld(Class<?> aggregatRoot) {
        List<Field> versionierteFelder = Arrays.stream(aggregatRoot.getDeclaredFields())
                .filter(feld -> feld.isAnnotationPresent(Version.class))
                .toList();

        assertThat(versionierteFelder)
                .as("%s soll genau ein @Version-Feld deklarieren", aggregatRoot.getSimpleName())
                .hasSize(1);

        Field versionsfeld = versionierteFelder.get(0);
        assertThat(versionsfeld.getName())
                .as("%s: das @Version-Feld soll \"version\" heissen", aggregatRoot.getSimpleName())
                .isEqualTo("version");
        assertThat(versionsfeld.getType())
                .as("%s: das @Version-Feld soll vom Typ Long sein", aggregatRoot.getSimpleName())
                .isEqualTo(Long.class);
    }

    /**
     * {@link Artikel} nutzt {@code @Inheritance(strategy = InheritanceType.JOINED)}.
     * Die Version-Spalte gehoert nur auf die Wurzeltabelle "artikel" - eine
     * zusaetzliche @Version-Deklaration auf einer Subklasse wuerde Hibernate
     * dazu bringen, eine zweite (nicht vorhandene) Spalte auf der jeweiligen
     * Kindtabelle zu erwarten.
     */
    @ParameterizedTest(name = "{0} (JOINED-Subklasse von Artikel) hat KEIN eigenes @Version")
    @ValueSource(classes = { ArtikelWerkstoffe.class, ArtikelHilfsstoffe.class })
    void artikelSubklassenHabenKeinEigenesVersionsfeld(Class<?> subklasse) {
        boolean hatEigenesVersionsfeld = Arrays.stream(subklasse.getDeclaredFields())
                .anyMatch(feld -> feld.isAnnotationPresent(Version.class));

        assertThat(hatEigenesVersionsfeld)
                .as("%s erbt @Version von Artikel und darf keines redeklarieren", subklasse.getSimpleName())
                .isFalse();
    }

    /**
     * Kind-Entitaeten (Positionen/Bloecke eines Aggregats) bekommen kein
     * eigenes @Version: gespeichert wird immer ueber den Wurzel-Service,
     * dessen Version als Waechter fuer den gesamten Aggregatsbaum reicht.
     */
    @ParameterizedTest(name = "Kind-Entitaet {0} bekommt KEIN eigenes @Version")
    @ValueSource(classes = { ArtikelInProjekt.class, BelegPosition.class, LieferantDokumentProjektAnteil.class })
    void kindEntitaetenHabenKeinVersionsfeld(Class<?> kindEntitaet) {
        boolean hatVersionsfeld = Arrays.stream(kindEntitaet.getDeclaredFields())
                .anyMatch(feld -> feld.isAnnotationPresent(Version.class));

        assertThat(hatVersionsfeld)
                .as("%s ist eine Kind-Entitaet - der Wurzel-Aggregat traegt die Version", kindEntitaet.getSimpleName())
                .isFalse();
    }
}
