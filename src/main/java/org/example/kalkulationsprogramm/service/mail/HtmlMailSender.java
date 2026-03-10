package org.example.kalkulationsprogramm.service.mail;

import jakarta.mail.MessagingException;

import java.io.File;
import java.util.Map;

public interface HtmlMailSender {
    void send(String fromAddress,
              String toAddress,
              String subject,
              String htmlBody,
              Map<String, File> inlineAttachments) throws MessagingException;
}
