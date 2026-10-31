package org.example.kalkulationsprogramm.service.ids;

import java.util.Map;

import org.example.kalkulationsprogramm.domain.ids.IdsProtokoll;
import org.springframework.stereotype.Component;

/**
 * IDS-Connect ohne {@code action} und {@code version} beim Öffnen des Shops: nur die vier Felder, die das
 * Würth-Zugangsblatt nennt. Manche Shops lehnen unbekannte Felder ab („IDS-Parameter … ist nicht gültig“).
 * Der Rückweg ist derselbe IDS-Warenkorb wie beim Standard.
 */
@Component
public class IdsNurZugangsfelderAdapter extends IdsConnect25Adapter {

    @Override
    public IdsProtokoll protokoll() {
        return IdsProtokoll.IDS_NUR_ZUGANGSFELDER;
    }

    @Override
    public ShopFormular anmeldeFormular(IdsZugangsdaten zugang, String ruecksprung) {
        ShopFormular standard = super.anmeldeFormular(zugang, ruecksprung);
        Map<String, String> felder = new java.util.LinkedHashMap<>(standard.felder());
        felder.remove("action");
        felder.remove("version");
        return new ShopFormular(standard.action(), standard.enctype(), felder);
    }
}
