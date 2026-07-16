package com.biopet.exception;

public class EmailDuplicadoException extends RuntimeException {
    public EmailDuplicadoException(String email) {
        super("El email ya se encuentra registrado: " + email);
    }
}
