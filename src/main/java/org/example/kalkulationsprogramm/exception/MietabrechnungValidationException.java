package org.example.kalkulationsprogramm.exception;

import org.springframework.http.HttpStatus;

/**
 * Signals that the annual rent accounting cannot be completed because of a
 * missing or invalid configuration. The {@code userMessage} is shown to the
 * end user, while {@code detail} remains available for technical logs.
 */
public class MietabrechnungValidationException extends RuntimeException {

    private final HttpStatus status;
    private final String userMessage;
    private final String detail;

    public MietabrechnungValidationException(String userMessage, String detail) {
        this(HttpStatus.BAD_REQUEST, userMessage, detail);
    }

    public MietabrechnungValidationException(HttpStatus status, String userMessage, String detail) {
        super(detail != null ? detail : userMessage);
        this.status = status;
        this.userMessage = userMessage;
        this.detail = detail;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getDetail() {
        return detail;
    }
}
