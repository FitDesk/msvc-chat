package com.msvcchat.exceptions;

public class ParticipantNotFoundException extends RuntimeException {
    public ParticipantNotFoundException(String conversationId) {
        super("No se encontró participante en la conversación: " + conversationId);
    }
}
