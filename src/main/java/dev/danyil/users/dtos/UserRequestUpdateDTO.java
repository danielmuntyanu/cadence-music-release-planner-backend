package dev.danyil.users.dtos;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserRequestUpdateDTO(
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$")
    String username,

    @Email 
    @Size(max = 255)
    String email,

    @Size(max = 100) 
    String displayName,
    
    @Size(max = 500) 
    String bio,

    // null keeps the avatar; "" removes it; a URL replaces an uploaded file
    @URL(message = "Avatar must be a valid URL")
    @Size(max = 512)
    String avatarUrl
    
) {

}
