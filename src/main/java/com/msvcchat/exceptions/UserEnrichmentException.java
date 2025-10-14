package com.msvcchat.exceptions;

public class UserEnrichmentException extends RuntimeException {
    public UserEnrichmentException(String message, Throwable cause) {
        super(message, cause);
    }
}