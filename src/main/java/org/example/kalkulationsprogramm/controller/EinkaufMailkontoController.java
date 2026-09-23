package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.dto.Einkauf.MailkontoDto.Response;
import org.example.kalkulationsprogramm.dto.Einkauf.MailkontoDto.Update;
import org.example.kalkulationsprogramm.service.mail.MailkontoService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/settings/einkauf-mail")
@RequiredArgsConstructor
public class EinkaufMailkontoController {
    private final MailkontoService mailkontoService;

    @GetMapping
    public Response lesen(Authentication authentication) {
        return mailkontoService.lesen(authentication);
    }

    @PutMapping
    public Response speichern(@RequestBody Update update, Authentication authentication) {
        return mailkontoService.speichern(authentication, update);
    }
}
