package dev.danyil.contracts;

import java.util.Set;

import dev.danyil.auth.dtos.CredentialsDTO;
import dev.danyil.security.dtos.JwtAuthenticationDTO;
import dev.danyil.users.dtos.UserCurrentResponseDTO;

public interface AuthService {

    UserCurrentResponseDTO login(CredentialsDTO credentials);
    JwtAuthenticationDTO getAuth(String username, Set<String> roles);
    JwtAuthenticationDTO updateAuth(String oldRefreshToken);

}
