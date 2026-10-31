package org.example.kalkulationsprogramm.service.ki;

import java.util.ArrayList;
import java.util.List;

/**
 * Eine Runde im Gespräch mit der KI: wer spricht, und was.
 *
 * @param roh optional der Zug der KI im Format ihres Anbieters (siehe {@link KiRohZug}), sonst {@code null}
 */
public record KiNachricht(Rolle rolle, List<KiTeil> teile, KiRohZug roh) {

    public enum Rolle {
        NUTZER,
        KI,
        /** Ergebnisse der Werkzeug-Aufrufe aus der vorigen Nachricht der KI. */
        WERKZEUG
    }

    public KiNachricht {
        rolle = rolle == null ? Rolle.NUTZER : rolle;
        teile = teile == null ? List.of() : List.copyOf(teile);
    }

    public KiNachricht(Rolle rolle, List<KiTeil> teile) {
        this(rolle, teile, null);
    }

    public static KiNachricht nutzer(List<KiTeil> teile) {
        return new KiNachricht(Rolle.NUTZER, teile);
    }

    public static KiNachricht nutzer(String text) {
        return nutzer(List.of(KiTeil.text(text)));
    }

    public static KiNachricht ki(String text) {
        return new KiNachricht(Rolle.KI, List.of(KiTeil.text(text)));
    }

    /** Die Antworten auf alle Werkzeug-Aufrufe einer Nachricht der KI, in einer Nachricht. */
    public static KiNachricht werkzeugErgebnisse(List<KiTeil.WerkzeugErgebnis> ergebnisse) {
        return new KiNachricht(Rolle.WERKZEUG, List.copyOf(ergebnisse));
    }

    /** Der Text der Nachricht ohne Dateien und Werkzeug-Teile. */
    public String text() {
        StringBuilder text = new StringBuilder();
        for (KiTeil teil : teile) {
            if (teil instanceof KiTeil.Text t) {
                text.append(t.text());
            }
        }
        return text.toString();
    }

    public List<KiTeil.WerkzeugAufruf> werkzeugAufrufe() {
        List<KiTeil.WerkzeugAufruf> aufrufe = new ArrayList<>();
        for (KiTeil teil : teile) {
            if (teil instanceof KiTeil.WerkzeugAufruf aufruf) {
                aufrufe.add(aufruf);
            }
        }
        return aufrufe;
    }

    /** Der Rohzug, wenn er von diesem Anbieter stammt; sonst {@code null}. */
    public KiRohZug rohVon(KiAnbieterTyp anbieter) {
        return roh != null && roh.anbieter() == anbieter ? roh : null;
    }
}
