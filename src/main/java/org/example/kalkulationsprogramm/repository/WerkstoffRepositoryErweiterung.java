package org.example.kalkulationsprogramm.repository;

public interface WerkstoffRepositoryErweiterung {

    /**
     * Legt den Werkstoff an, wenn es den Namen noch nicht gibt - ohne Fehler,
     * falls ein paralleler Import ihn gleichzeitig anlegt. Eine Dubletten-
     * Exception wuerde die Import-Transaktion unbrauchbar machen.
     */
    void legeAnFallsNeu(String name);
}
