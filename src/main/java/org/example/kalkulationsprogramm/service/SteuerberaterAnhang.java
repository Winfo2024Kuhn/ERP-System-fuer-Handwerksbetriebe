package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;

/**
 * Ein PDF-Anhang einer Steuerberater-Mail samt Mail und erkanntem Steuerberater –
 * das, was Lohnabrechnungs- und BWA-Ablage zum Speichern brauchen.
 */
record SteuerberaterAnhang(EmailAttachment anhang, Email email, SteuerberaterKontakt steuerberater) {

    String originalDateiname() {
        return anhang.getOriginalFilename();
    }
}
