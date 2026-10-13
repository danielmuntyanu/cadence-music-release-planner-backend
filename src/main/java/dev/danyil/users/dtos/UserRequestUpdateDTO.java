package dev.danyil.users.dtos;

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
    String bio
) {

}
