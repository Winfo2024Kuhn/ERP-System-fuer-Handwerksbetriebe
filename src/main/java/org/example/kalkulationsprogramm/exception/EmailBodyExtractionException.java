package org.example.kalkulationsprogramm.exception;

/**
 * Exception indicating that an email body or attachment could not be
 * extracted successfully.  Used to wrap lower level exceptions during
 * mail parsing so that callers can react accordingly.
 */
public class EmailBodyExtractionException extends Exception {
    public EmailBodyExtractionException(String message) {
        super(message);
    }

    public EmailBodyExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
