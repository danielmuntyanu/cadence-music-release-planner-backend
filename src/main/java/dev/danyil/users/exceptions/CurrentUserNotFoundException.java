package dev.danyil.users.exceptions;

import lombok.Getter;

@Getter 
public class CurrentUserNotFoundException extends RuntimeException  {
    private final String username;

    public CurrentUserNotFoundException(String username) {
        super("User with username '" + username + "' not found");
        this.username = username;
    }

}
