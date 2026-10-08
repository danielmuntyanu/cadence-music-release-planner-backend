package dev.danyil.users.exceptions;

import lombok.Getter;

@Getter 
public class UserNotFoundException extends RuntimeException {
    private final String username;
    private final Long id;

    public UserNotFoundException(String username) {
        super("User with username '" + username + "' not found");
        this.username = username;
        this.id = null;
    }

    public UserNotFoundException(Long id) {
        super("User with id '" + id + "' not found");
        this.id = id;
        this.username = null;
    }
}
