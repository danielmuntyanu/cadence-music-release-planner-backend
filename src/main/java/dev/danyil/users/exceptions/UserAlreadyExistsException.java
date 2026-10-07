package dev.danyil.users.exceptions;

import lombok.Getter;

@Getter
public class UserAlreadyExistsException extends RuntimeException {
    private final String field; // "username" или "email"

    public UserAlreadyExistsException(String field) {
        super(field + " already in use");
        this.field = field;
    }
}
