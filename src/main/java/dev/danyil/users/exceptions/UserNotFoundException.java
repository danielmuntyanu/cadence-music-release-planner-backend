package dev.danyil.users.exceptions;

import lombok.Getter;

@Getter 
public class UserNotFoundException extends RuntimeException {
    private final String username;

    public UserNotFoundException(String username) {
        super("User with username '" + username + "' not found");
        this.username = username;
    }
}
