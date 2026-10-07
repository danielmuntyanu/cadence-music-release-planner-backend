package dev.danyil.security.exceptions;

public class JwtNoExistException extends RuntimeException {

    public JwtNoExistException(String message) {
        super(message);
    }

}