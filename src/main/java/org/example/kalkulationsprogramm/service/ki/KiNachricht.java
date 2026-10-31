package org.example.kalkulationsprogramm.service.ki;

import java.util.List;

/** Eine Runde im Gespräch mit der KI: wer spricht, und was. */
public record KiNachricht(Rolle rolle, List<KiTeil> teile) {

    public enum Rolle {
        NUTZER,
        KI
    }

    public KiNachricht {
        rolle = rolle == null ? Rolle.NUTZER : rolle;
        teile = teile == null ? List.of() : List.copyOf(teile);
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
}
