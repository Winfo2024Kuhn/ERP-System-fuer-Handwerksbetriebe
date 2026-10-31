package org.example.kalkulationsprogramm.service.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.example.kalkulationsprogramm.service.mail.SpamOrdnerRegeln.ServerOrdner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SpamOrdnerRegelnTest {

    private static ServerOrdner ordner(String name) {
        return new ServerOrdner(name, false, false);
    }

    @Test
    void gekennzeichneterOrdnerGewinntVorDemNamen() {
        List<ServerOrdner> liste = List.of(ordner("Spam"), new ServerOrdner("Bulk", true, false));

        assertThat(SpamOrdnerRegeln.erkenneSpamOrdner(liste)).contains("Bulk");
    }

    @Test
    void ohneKennzeichnungZaehltDerUeblicheNameAuchAlsUnterordner() {
        List<ServerOrdner> liste = List.of(ordner("INBOX"), ordner("INBOX.Junk"), ordner("INBOX.Spam"));

        // „Spam“ steht in der Liste vor „Junk“.
        assertThat(SpamOrdnerRegeln.erkenneSpamOrdner(liste)).contains("INBOX.Spam");
    }

    @ParameterizedTest
    @ValueSource(strings = { "Junk-E-Mail", "Junk E-Mail", "Bulk Mail", "Unerwünscht", "INBOX/JUNK" })
    void erkenntUeblicheNamenVerschiedenerAnbieter(String name) {
        assertThat(SpamOrdnerRegeln.erkenneSpamOrdner(List.of(ordner(name)))).contains(name);
    }

    @Test
    void aehnlicheNamenZaehlenNicht() {
        List<ServerOrdner> liste = List.of(ordner("INBOX"), ordner("Spamfilter-Regeln"),
                ordner("'; DROP TABLE email; --"));

        assertThat(SpamOrdnerRegeln.erkenneSpamOrdner(liste)).isEmpty();
    }

    @Test
    void gekennzeichneterOrdnerDerZugleichPapierkorbIstWirdNieGenommen() {
        List<ServerOrdner> liste = List.of(new ServerOrdner("Trash", true, true));

        assertThat(SpamOrdnerRegeln.erkenneSpamOrdner(liste)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "INBOX", "inbox", "Trash", "INBOX.Trash", "Papierkorb", "Gelöschte Elemente",
            "Sent", "INBOX.Sent", "Gesendete Objekte", "Drafts", "Entwürfe", "Postausgang", "Spam\nINBOX",
            "Junk\u0000" })
    void ungeeigneteOrdnerWerdenAbgelehnt(String name) {
        assertThat(SpamOrdnerRegeln.eignetSichDemNamenNach(name)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   " })
    void leererNameIstKeinOrdner(String name) {
        assertThat(SpamOrdnerRegeln.eignetSichDemNamenNach(name)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "Junk", "INBOX.Spam", "[Gmail]/Spam", "Werbung", "<script>alert(1)</script>" })
    void geeigneteOrdnerSindErlaubt(String name) {
        assertThat(SpamOrdnerRegeln.eignetSichDemNamenNach(name)).isTrue();
    }

    @Test
    void sonderordnerVomServerIstNieGeeignet() {
        assertThat(SpamOrdnerRegeln.eignetSich(new ServerOrdner("Werbung", false, true))).isFalse();
        assertThat(SpamOrdnerRegeln.eignetSich(new ServerOrdner("Werbung", false, false))).isTrue();
    }

    @Test
    void letzterTeilKenntPunktUndSchraegstrich() {
        assertThat(SpamOrdnerRegeln.letzterTeil("INBOX.Spam")).isEqualTo("Spam");
        assertThat(SpamOrdnerRegeln.letzterTeil("[Gmail]/Spam")).isEqualTo("Spam");
        assertThat(SpamOrdnerRegeln.letzterTeil("Junk")).isEqualTo("Junk");
    }
}
