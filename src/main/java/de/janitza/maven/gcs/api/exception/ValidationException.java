package de.janitza.maven.gcs.api.exception;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 10.02.15
 * Time: 14:06
 */
public class ValidationException extends Exception {
    public ValidationException(final String message) {
        super(message);
    }

    public ValidationException(
            final String message,
            final Throwable cause
    ) {
        super(message, cause);
    }

    public ValidationException(final Throwable cause) {
        super(cause);
    }

    public ValidationException(
            final String message,
            final Throwable cause,
            final boolean enableSuppression,
            final boolean writableStackTrace
    ) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
