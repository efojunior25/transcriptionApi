package com.xunim.transcriptionapi.exception;

public class WhisperApiException extends RuntimeException {
    private final int statusCode;

    public WhisperApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public WhisperApiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
