package org.example.kalkulationsprogramm.domain;

/**
 * Bundeseinheitliche Sozialversicherungs-Satzarten. Werte exakt wie in der
 * Spalte sv_satz.satz_typ (siehe V295__sv_satz.sql).
 */
public enum SvSatzTyp {
    KV_GESAMT,
    PV_GESAMT,
    PV_KINDERLOS_AN_ZUSCHLAG,
    RV_GESAMT,
    AV_GESAMT,
    MINIJOB_AG_KV,
    MINIJOB_AG_RV,
    MINIJOB_AG_PAUSCHALSTEUER,
    U1_UMLAGE,
    U2_UMLAGE,
    INSOLVENZGELDUMLAGE
}
