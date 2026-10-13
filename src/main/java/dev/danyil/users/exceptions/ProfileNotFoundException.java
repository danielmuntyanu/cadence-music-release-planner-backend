package dev.danyil.users.exceptions;

import lombok.Getter;

@Getter 
public class ProfileNotFoundException extends RuntimeException {
    private final Long id;

    public ProfileNotFoundException(Long id) {
        super("UserProfile with id '" + id + "' not found");
        this.id = id;
    }
}